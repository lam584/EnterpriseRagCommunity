import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export type ImageStorageConfig = {
  mode?: string | null;
  localBaseUrl?: string | null;
  dashscopeModel?: string | null;
  ossEndpoint?: string | null;
  ossBucket?: string | null;
  ossAccessKeyId?: string | null;
  ossAccessKeySecret?: string | null;
  ossRegion?: string | null;
  compressionEnabled?: boolean | null;
  compressionMaxWidth?: number | null;
  compressionMaxHeight?: number | null;
  compressionQuality?: number | null;
  compressionMaxBytes?: number | null;
};

export type ImageUploadLog = {
  id: number;
  localPath: string;
  remoteUrl: string;
  storageMode: string;
  modelName?: string | null;
  fileSizeBytes?: number | null;
  uploadDurationMs?: number | null;
  uploadedAt: string;
  expiresAt?: string | null;
  status: string;
};

export type PagedUploadLogs = {
  content: ImageUploadLog[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type TestUploadResult = {
  success: boolean;
  remoteUrl?: string;
  elapsedMs?: number;
  error?: string;
};

export async function adminGetImageStorageConfig(): Promise<ImageStorageConfig> {
  const res = await fetch(apiUrl('/api/admin/ai/image-storage/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取图片存储配置失败');
  return data as ImageStorageConfig;
}

export async function adminUpdateImageStorageConfig(payload: ImageStorageConfig): Promise<ImageStorageConfig> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/image-storage/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新图片存储配置失败');
  return data as ImageStorageConfig;
}

export async function adminGetUploadLogs(page = 0, size = 20): Promise<PagedUploadLogs> {
  const res = await fetch(apiUrl(`/api/admin/ai/image-storage/upload-logs?page=${page}&size=${size}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取上传日志失败');
  return data as PagedUploadLogs;
}

export async function adminTestUpload(localPath: string, mimeType?: string, modelName?: string): Promise<TestUploadResult> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/image-storage/test-upload'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ localPath, mimeType, modelName }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '测试上传失败');
  return data as TestUploadResult;
}

export type TestCompressResult = {
  success: boolean;
  originalSize?: number;
  compressedSize?: number;
  originalWidth?: number;
  originalHeight?: number;
  compressedWidth?: number;
  compressedHeight?: number;
  compressionRatio?: string;
  format?: string;
  wasCompressed?: boolean;
  originalPreview?: string;
  compressedPreview?: string;
  error?: string;
};

export async function adminTestCompress(localPath: string): Promise<TestCompressResult> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/image-storage/test-compress'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ localPath }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '测试压缩失败');
  return data as TestCompressResult;
}

export async function adminDeleteExpiredLogs(): Promise<{ deleted: number }> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/image-storage/expired-logs'), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '清理过期日志失败');
  return data as { deleted: number };
}
