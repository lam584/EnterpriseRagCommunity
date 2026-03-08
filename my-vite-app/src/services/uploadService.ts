// src/services/uploadService.ts

import { getCsrfToken } from '../utils/csrfUtils';

export interface UploadResult {
  id: number; // file_assets.id
  fileName: string;
  fileUrl: string;
  fileSize: number;
  mimeType: string;
}

export type UploadProgress = {
  loaded: number;
  total: number;
  percent: number | null;
};

export async function uploadFile(file: File): Promise<UploadResult> {
  const csrfToken = await getCsrfToken();
  const form = new FormData();
  form.append('file', file);

  const res = await fetch('/api/uploads', {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: form,
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || '上传失败');
  }

  // backend returns UploadResultDTO: { id, fileName, fileUrl, fileSize, mimeType }
  return {
    id: Number(data.id),
    fileName: String(data.fileName ?? file.name),
    fileUrl: String(data.fileUrl),
    fileSize: Number(data.fileSize ?? file.size),
    mimeType: String(data.mimeType ?? (file.type || 'application/octet-stream')),
  };
}

export function uploadFileWithProgress(
  file: File,
  options?: {
    onProgress?: (p: UploadProgress) => void;
  },
): { promise: Promise<UploadResult>; cancel: () => void } {
  let xhr: XMLHttpRequest | null = null;
  let canceledBeforeStart = false;
  let settled = false;

  const cancel = () => {
    if (settled) return;
    if (xhr) {
      try {
        xhr.abort();
      } catch {
      }
      return;
    }
    canceledBeforeStart = true;
  };

  const promise = (async () => {
    const csrfToken = await getCsrfToken();
    if (canceledBeforeStart) {
      settled = true;
      throw new Error('已取消上传');
    }

    const form = new FormData();
    form.append('file', file);

    return await new Promise<UploadResult>((resolve, reject) => {
      xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/uploads', true);
      xhr.withCredentials = true;
      xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);

      xhr.upload.onprogress = (evt) => {
        if (!evt) return;
        const total = evt.lengthComputable ? evt.total : file.size;
        const loaded = evt.loaded;
        const percent = total > 0 ? Math.max(0, Math.min(1, loaded / total)) : null;
        options?.onProgress?.({ loaded, total, percent });
      };

      xhr.onerror = () => {
        settled = true;
        reject(new Error('上传失败'));
      };

      xhr.onabort = () => {
        settled = true;
        reject(new Error('已取消上传'));
      };

      xhr.onload = () => {
        const status = xhr?.status ?? 0;
        const text = xhr?.responseText ?? '';
        let data: any = {};
        try {
          data = text ? JSON.parse(text) : {};
        } catch {
          data = {};
        }

        if (status < 200 || status >= 300) {
          settled = true;
          reject(new Error(data?.message || '上传失败'));
          return;
        }

        const out: UploadResult = {
          id: Number(data.id),
          fileName: String(data.fileName ?? file.name),
          fileUrl: String(data.fileUrl),
          fileSize: Number(data.fileSize ?? file.size),
          mimeType: String(data.mimeType ?? (file.type || 'application/octet-stream')),
        };

        options?.onProgress?.({ loaded: out.fileSize, total: out.fileSize, percent: 1 });

        settled = true;
        resolve(out);
      };

      xhr.send(form);
    });
  })();

  return { promise, cancel };
}

export type ResumableUploadInitResponse = {
  uploadId: string;
  chunkSizeBytes: number;
  uploadedBytes: number;
};

export type ResumableUploadStatus = {
  uploadId: string;
  fileName: string;
  fileSize: number;
  uploadedBytes: number;
  chunkSizeBytes: number;
  status: string;
  verifyBytes?: number;
  verifyTotalBytes?: number;
  updatedAtEpochMs?: number;
  errorMessage?: string;
};

export type ResumableUploadHandle = {
  promise: Promise<UploadResult>;
  pause: () => void;
  resume: () => void;
  cancel: () => void;
};

export function uploadFileResumable(
  file: File,
  options?: {
    onProgress?: (p: UploadProgress) => void;
    onInit?: (p: ResumableUploadInitResponse) => void;
    onRetry?: (p: { attempt: number; maxAttempts: number; delayMs: number; status: number | null; message: string; requestId: string | null }) => void;
    resumeUploadId?: string;
  },
): ResumableUploadHandle {
  let currentXhr: XMLHttpRequest | null = null;
  let paused = false;
  let canceled = false;
  let resumeWaiter: (() => void) | null = null;
  let csrfToken: string | null = null;
  let uploadId: string | null = null;

  const pause = () => {
    if (canceled) return;
    paused = true;
    if (currentXhr) {
      try {
        currentXhr.abort();
      } catch {
      }
    }
  };

  const resume = () => {
    if (canceled) return;
    if (!paused) return;
    paused = false;
    if (resumeWaiter) {
      const r = resumeWaiter;
      resumeWaiter = null;
      r();
    }
  };

  const cancel = () => {
    if (canceled) return;
    canceled = true;
    paused = false;
    if (resumeWaiter) {
      const r = resumeWaiter;
      resumeWaiter = null;
      r();
    }
    if (currentXhr) {
      try {
        currentXhr.abort();
      } catch {
      }
    }
    const id = uploadId;
    const token = csrfToken;
    if (id && token) {
      void fetch(`/api/uploads/resumable/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: { 'X-XSRF-TOKEN': token },
        credentials: 'include',
      }).catch(() => {});
    }
  };

  const waitIfPaused = async () => {
    if (!paused) return;
    await new Promise<void>((resolve) => {
      resumeWaiter = resolve;
    });
  };

  const parseJson = (text: string): any => {
    try {
      return text ? JSON.parse(text) : {};
    } catch {
      return {};
    }
  };

  const sleep = async (ms: number) => {
    const t = Math.max(0, Math.min(60_000, Math.trunc(ms)));
    if (t <= 0) return;
    await new Promise<void>((resolve) => window.setTimeout(resolve, t));
  };

  const safeTextSnippet = (text: string): string => {
    const t = String(text || '').trim();
    if (!t) return '';
    const normalized = t.replace(/\s+/g, ' ');
    return normalized.length > 240 ? normalized.slice(0, 240) + '…' : normalized;
  };

  const buildHttpErrorMessage = (status: number, bodyText: string, requestId: string | null): string => {
    const data = parseJson(bodyText);
    const backendMsg = typeof data?.message === 'string' ? data.message.trim() : '';
    const rid = requestId ? ` requestId=${requestId}` : '';
    if (backendMsg) return `${backendMsg} (HTTP ${status}${rid})`;
    const snippet = safeTextSnippet(bodyText);
    if (snippet) return `上传失败 (HTTP ${status}${rid}): ${snippet}`;
    return `上传失败 (HTTP ${status}${rid})`;
  };

  const isRetryableStatus = (status: number): boolean => {
    if (status === 0) return true;
    if (status === 502 || status === 503 || status === 504) return true;
    return status >= 500 && status <= 599;
  };

  const uploadChunk = async (id: string, offset: number, total: number, blob: Blob): Promise<number | null> => {
    const token = csrfToken;
    if (!token) throw new Error('无法获取安全令牌，请刷新页面重试');
    return await new Promise<number | null>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      currentXhr = xhr;
      xhr.open('PUT', `/api/uploads/resumable/${encodeURIComponent(id)}/chunk`, true);
      xhr.withCredentials = true;
      xhr.setRequestHeader('X-XSRF-TOKEN', token);
      xhr.setRequestHeader('X-Upload-Offset', String(offset));
      xhr.setRequestHeader('X-Upload-Total', String(total));
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');

      xhr.upload.onprogress = (evt) => {
        if (!evt) return;
        const loaded = offset + evt.loaded;
        const percent = total > 0 ? Math.max(0, Math.min(1, loaded / total)) : null;
        options?.onProgress?.({ loaded, total, percent });
      };

      xhr.onerror = () => {
        currentXhr = null;
        reject(new Error('上传失败'));
      };

      xhr.onabort = () => {
        currentXhr = null;
        if (canceled) {
          reject(new Error('已取消上传'));
          return;
        }
        if (paused) {
          resolve(null);
          return;
        }
        reject(new Error('已取消上传'));
      };

      xhr.onload = () => {
        currentXhr = null;
        const status = xhr.status ?? 0;
        const responseText = xhr.responseText ?? '';
        const requestId = xhr.getResponseHeader('X-Request-Id');
        if (status < 200 || status >= 300) {
          reject(new Error(buildHttpErrorMessage(status, responseText, requestId)));
          return;
        }
        const data = parseJson(responseText);
        const next = Number(data?.uploadedBytes);
        resolve(Number.isFinite(next) ? next : offset + blob.size);
      };

      xhr.send(blob);
    });
  };

  const uploadChunkWithRetry = async (
    id: string,
    offset: number,
    total: number,
    blob: Blob,
  ): Promise<number | null> => {
    const maxAttempts = 6;
    let attempt = 0;
    let lastStatus: number | null = null;
    let lastRequestId: string | null = null;
    let lastMessage = '';

    while (true) {
      await waitIfPaused();
      if (canceled) throw new Error('已取消上传');
      if (paused) return null;

      try {
        const next = await uploadChunk(id, offset, total, blob);
        return next;
      } catch (e: unknown) {
        if (paused) return null;
        const msg = e instanceof Error ? e.message : String(e);
        lastMessage = msg;

        const m = msg.match(/\(HTTP\s+(\d+)/);
        const status = m ? Number(m[1]) : NaN;
        lastStatus = Number.isFinite(status) ? status : null;
        const rid = msg.match(/requestId=([a-zA-Z0-9-]+)/);
        lastRequestId = rid ? rid[1] : null;

        if (msg.includes('偏移量不匹配')) {
          throw e instanceof Error ? e : new Error(msg);
        }

        if (lastStatus != null && !isRetryableStatus(lastStatus)) {
          throw e instanceof Error ? e : new Error(msg);
        }
        if (lastStatus == null && msg !== '上传失败') {
          const maybeRetryable = msg.includes('上传失败');
          if (!maybeRetryable) {
            throw e instanceof Error ? e : new Error(msg);
          }
        }

        attempt += 1;
        if (attempt >= maxAttempts) {
          const statusStr = lastStatus == null ? '' : `HTTP ${lastStatus}`;
          const ridStr = lastRequestId ? ` requestId=${lastRequestId}` : '';
          const prefix = statusStr || ridStr ? `分片上传多次失败（${attempt}/${maxAttempts} ${statusStr}${ridStr}）` : `分片上传多次失败（${attempt}/${maxAttempts}）`;
          throw new Error(`${prefix}：${lastMessage || '上传失败'}`);
        }

        try {
          const s = await syncOffset(id);
          const serverOffset = Number.isFinite(s.uploadedBytes) ? Math.max(0, s.uploadedBytes) : null;
          if (serverOffset != null && serverOffset > offset) {
            return serverOffset;
          }
        } catch {
        }

        const delayMs = Math.min(30_000, Math.trunc(500 * Math.pow(2, attempt - 1)));
        options?.onRetry?.({
          attempt,
          maxAttempts,
          delayMs,
          status: lastStatus,
          message: lastMessage || '上传失败',
          requestId: lastRequestId,
        });
        await sleep(delayMs);
      }
    }
  };

  const syncOffset = async (id: string): Promise<ResumableUploadStatus> => {
    const token = csrfToken;
    if (!token) throw new Error('无法获取安全令牌，请刷新页面重试');
    return await fetchResumableStatus(id, token, { fileName: file.name, fileSize: file.size });
  };

  const promise = (async () => {
    csrfToken = await getCsrfToken();
    if (canceled) throw new Error('已取消上传');

    const requestedResumeUploadId = typeof options?.resumeUploadId === 'string' ? options.resumeUploadId.trim() : '';
    const init: ResumableUploadInitResponse = requestedResumeUploadId
      ? (() => {
          if (!/^[a-zA-Z0-9]{1,64}$/.test(requestedResumeUploadId)) {
            throw new Error('uploadId 非法');
          }
          return {
            uploadId: requestedResumeUploadId,
            chunkSizeBytes: 5 * 1024 * 1024,
            uploadedBytes: 0,
          };
        })()
      : { uploadId: '', chunkSizeBytes: 5 * 1024 * 1024, uploadedBytes: 0 };

    if (requestedResumeUploadId) {
      const status = await fetchResumableStatus(requestedResumeUploadId, csrfToken, { fileName: file.name, fileSize: file.size });
      const serverSize = Number.isFinite(status.fileSize) ? status.fileSize : 0;
      if (serverSize > 0 && file.size > 0 && serverSize !== file.size) {
        throw new Error('所选文件大小与待续传任务不一致');
      }
      init.uploadId = status.uploadId;
      init.chunkSizeBytes = Number.isFinite(status.chunkSizeBytes) ? status.chunkSizeBytes : 5 * 1024 * 1024;
      init.uploadedBytes = Number.isFinite(status.uploadedBytes) ? status.uploadedBytes : 0;
    } else {
      const initRes = await fetch('/api/uploads/resumable/init', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrfToken,
        },
        credentials: 'include',
        body: JSON.stringify({
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type,
        }),
      });
      const initData = await initRes.json().catch(() => ({}));
      if (!initRes.ok) {
        throw new Error(initData.message || '上传初始化失败');
      }

      init.uploadId = String(initData.uploadId);
      init.chunkSizeBytes = Number(initData.chunkSizeBytes ?? 5 * 1024 * 1024);
      init.uploadedBytes = Number(initData.uploadedBytes ?? 0);
    }
    uploadId = init.uploadId;
    options?.onInit?.(init);

    let offset = Math.max(0, init.uploadedBytes);
    const total = typeof file.size === 'number' && Number.isFinite(file.size) ? file.size : 0;
    const chunkSize = Math.max(256 * 1024, init.chunkSizeBytes || 5 * 1024 * 1024);

    options?.onProgress?.({
      loaded: offset,
      total,
      percent: total > 0 ? Math.max(0, Math.min(1, offset / total)) : null,
    });

    while (offset < total) {
      await waitIfPaused();
      if (canceled) throw new Error('已取消上传');

      if (paused) continue;

      const end = Math.min(total, offset + chunkSize);
      const blob = file.slice(offset, end);

      try {
        const nextOffset = await uploadChunkWithRetry(init.uploadId, offset, total, blob);
        if (nextOffset == null) {
          continue;
        }
        offset = Math.max(offset, nextOffset);
      } catch (e: unknown) {
        if (paused) {
          continue;
        }
        const msg = e instanceof Error ? e.message : String(e);
        if (msg.includes('偏移量不匹配')) {
          const s = await syncOffset(init.uploadId);
          offset = Math.max(0, s.uploadedBytes);
          options?.onProgress?.({
            loaded: offset,
            total,
            percent: total > 0 ? Math.max(0, Math.min(1, offset / total)) : null,
          });
          continue;
        }
        throw e instanceof Error ? e : new Error(msg);
      }
    }

    await waitIfPaused();
    if (canceled) throw new Error('已取消上传');

    const completeRes = await fetch(`/api/uploads/resumable/${encodeURIComponent(init.uploadId)}/complete`, {
      method: 'POST',
      headers: { 'X-XSRF-TOKEN': csrfToken },
      credentials: 'include',
    });
    const data = await completeRes.json().catch(() => ({}));
    if (!completeRes.ok) {
      throw new Error(data.message || '上传失败');
    }

    return {
      id: Number(data.id),
      fileName: String(data.fileName ?? file.name),
      fileUrl: String(data.fileUrl),
      fileSize: Number(data.fileSize ?? file.size),
      mimeType: String(data.mimeType ?? (file.type || 'application/octet-stream')),
    };
  })();

  return { promise, pause, resume, cancel };
}

async function fetchResumableStatus(
  id: string,
  csrfToken: string,
  fallback?: { fileName?: string; fileSize?: number },
): Promise<ResumableUploadStatus> {
  const res = await fetch(`/api/uploads/resumable/${encodeURIComponent(id)}`, {
    method: 'GET',
    headers: { 'X-XSRF-TOKEN': csrfToken },
    credentials: 'include',
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || '获取上传状态失败');
  }
  const fallbackName = String(fallback?.fileName || 'file');
  const fallbackSize = typeof fallback?.fileSize === 'number' && Number.isFinite(fallback.fileSize) ? fallback.fileSize : 0;
  return {
    uploadId: String(data.uploadId),
    fileName: String(data.fileName ?? fallbackName),
    fileSize: Number(data.fileSize ?? fallbackSize),
    uploadedBytes: Number(data.uploadedBytes ?? 0),
    chunkSizeBytes: Number(data.chunkSizeBytes ?? 5 * 1024 * 1024),
    status: String(data.status ?? ''),
    verifyBytes: typeof data.verifyBytes === 'number' ? data.verifyBytes : undefined,
    verifyTotalBytes: typeof data.verifyTotalBytes === 'number' ? data.verifyTotalBytes : undefined,
    updatedAtEpochMs: typeof data.updatedAtEpochMs === 'number' ? data.updatedAtEpochMs : undefined,
    errorMessage: typeof data.errorMessage === 'string' ? data.errorMessage : undefined,
  };
}

export async function getResumableUploadStatus(
  uploadId: string,
  fallback?: { fileName?: string; fileSize?: number },
): Promise<ResumableUploadStatus> {
  const csrfToken = await getCsrfToken();
  return await fetchResumableStatus(uploadId, csrfToken, fallback);
}

export async function cancelResumableUpload(uploadId: string): Promise<void> {
  const id = typeof uploadId === 'string' ? uploadId.trim() : '';
  if (!id) return;
  const csrfToken = await getCsrfToken();
  await fetch(`/api/uploads/resumable/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { 'X-XSRF-TOKEN': csrfToken },
    credentials: 'include',
  }).catch(() => {});
}

export async function findUploadBySha256(sha256: string, fileName?: string): Promise<UploadResult | null> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams();
  qs.set('sha256', sha256);
  if (fileName) qs.set('fileName', fileName);

  const res = await fetch(`/api/uploads/by-sha256?${qs.toString()}`, {
    method: 'GET',
    headers: { 'X-XSRF-TOKEN': csrfToken },
    credentials: 'include',
  });

  if (res.status === 404) return null;

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || '查重失败');
  }

  return {
    id: Number(data.id),
    fileName: String(data.fileName ?? fileName ?? 'file'),
    fileUrl: String(data.fileUrl),
    fileSize: Number(data.fileSize ?? 0),
    mimeType: String(data.mimeType ?? 'application/octet-stream'),
  };
}

export async function listUploads(): Promise<UploadResult[]> {
  // 后端暂未提供列表接口；保留函数以兼容现有调用。
  return [];
}
