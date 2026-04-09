import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('uploadService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('uploadFile maps backend dto with type conversions and fallbacks', async () => {
    const { uploadFile } = await import('./uploadService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: '1', fileUrl: '/u', fileSize: '2' } });

    const file = new File(['hi'], 'a.txt', { type: 'text/plain' });
    const res = await uploadFile(file);
    expect(res).toEqual({ id: 1, fileName: 'a.txt', fileUrl: '/u', fileSize: 2, mimeType: 'text/plain' });

    expect(lastCall()?.[0]).toBe('/api/uploads');
    expect(lastCall()?.[1]?.method).toBe('POST');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
    expect(lastCall()?.[1]?.credentials).toBe('include');
    expect(lastCall()?.[1]?.body).toBeInstanceOf(FormData);
    expect((lastCall()?.[1]?.body as FormData).get('file')).toBe(file);
  });

  it('uploadFile falls back to file.size and default mimeType when backend fields missing', async () => {
    const { uploadFile } = await import('./uploadService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: 1, fileUrl: '/u' } });

    const file = new File(['hi'], 'a.txt');
    const res = await uploadFile(file);
    expect(res.fileSize).toBe(file.size);
    expect(res.mimeType).toBe('application/octet-stream');
  });

  it('uploadFile throws backend message on failure', async () => {
    const { uploadFile } = await import('./uploadService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });

    const file = new File(['hi'], 'a.txt', { type: 'text/plain' });
    await expect(uploadFile(file)).rejects.toThrow('bad');
  });

  it('uploadFile falls back to default message when backend message missing', async () => {
    const { uploadFile } = await import('./uploadService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: {} });

    const file = new File(['hi'], 'a.txt', { type: 'text/plain' });
    await expect(uploadFile(file)).rejects.toThrow('上传失败');
  });

  it('uploadFile falls back to default message when response json fails', async () => {
    const { uploadFile } = await import('./uploadService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, jsonError: new Error('boom') });

    const file = new File(['hi'], 'a.txt', { type: 'text/plain' });
    await expect(uploadFile(file)).rejects.toThrow('上传失败');
  });

  it('uploadFileWithProgress cancel before start rejects with cancel error', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hi'], 'a.txt', { type: 'text/plain' });

    const handle = uploadFileWithProgress(file);
    handle.cancel();
    await expect(handle.promise).rejects.toThrow('已取消上传');
  });

  it('uploadFileWithProgress emits progress and resolves on 2xx', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' });
    const progress: any[] = [];

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    let currentFile: File | null = file;

    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      private headers: Record<string, string> = {};
      open = vi.fn();
      setRequestHeader = vi.fn((k: string, v: string) => {
        this.headers[k] = v;
      });
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        const f = currentFile;
        if (f) {
          this.upload.onprogress?.({ lengthComputable: true, total: f.size, loaded: Math.max(0, Math.floor(f.size / 2)) });
        }
        this.status = 200;
        this.responseText = JSON.stringify({ id: 1, fileUrl: '/u', fileSize: f?.size ?? 0, mimeType: f?.type ?? '' });
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const handle = uploadFileWithProgress(file, { onProgress: (p) => progress.push(p) });
      const res = await handle.promise;
      expect(res).toMatchObject({ id: 1, fileName: 'a.txt', fileUrl: '/u', mimeType: 'text/plain' });
      expect(progress.length).toBeGreaterThan(0);
      expect(progress.at(-1)?.percent).toBe(1);
    } finally {
      currentFile = null;
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress cancel after settled is a no-op', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    let abortCalls = 0;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        abortCalls += 1;
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 200;
        this.responseText = JSON.stringify({ id: 1, fileUrl: '/u', fileSize: file.size, mimeType: file.type });
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const h = uploadFileWithProgress(file);
      await expect(h.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
      h.cancel();
      expect(abortCalls).toBe(0);
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress ignores null progress events', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.upload.onprogress?.(null);
        this.status = 200;
        this.responseText = JSON.stringify({ id: 1, fileUrl: '/u', fileSize: file.size, mimeType: file.type });
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const h = uploadFileWithProgress(file);
      await expect(h.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress rejects on non-2xx and uses backend message when present', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 500;
        this.responseText = JSON.stringify({ message: 'bad' });
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const handle = uploadFileWithProgress(file);
      await expect(handle.promise).rejects.toThrow('bad');
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress rejects on xhr error and abort', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    let mode: 'error' | 'hang' = 'error';
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        if (mode === 'error') this.onerror?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      mode = 'error';
      const h1 = uploadFileWithProgress(file);
      await expect(h1.promise).rejects.toThrow('上传失败');

      mode = 'hang';
      const h2 = uploadFileWithProgress(file);
      await Promise.resolve();
      h2.cancel();
      await expect(h2.promise).rejects.toThrow('已取消上传');
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress handles non-computable progress and empty/invalid error json', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File([], 'a.txt', { type: 'text/plain' });
    const seen: any[] = [];

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    let mode: 'empty' | 'invalid' = 'empty';
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.upload.onprogress?.({ lengthComputable: false, total: 0, loaded: 0 });
        this.status = 400;
        this.responseText = mode === 'empty' ? '' : '{';
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      mode = 'empty';
      const h1 = uploadFileWithProgress(file, { onProgress: (p) => seen.push(p) });
      await expect(h1.promise).rejects.toThrow('上传失败');
      expect(seen.some((p) => p.percent === null)).toBe(true);

      mode = 'invalid';
      const h2 = uploadFileWithProgress(file);
      await expect(h2.promise).rejects.toThrow('上传失败');
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileWithProgress falls back fileSize and mimeType when backend fields missing', async () => {
    const { uploadFileWithProgress } = await import('./uploadService');
    const file = new File(['hello'], 'a.txt');

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 200;
        this.responseText = JSON.stringify({ id: 1, fileUrl: '/u' });
        this.onload?.();
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const h = uploadFileWithProgress(file);
      await expect(h.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt', fileUrl: '/u', fileSize: file.size, mimeType: 'application/octet-stream' });
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable rejects invalid resumeUploadId', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const file = new File([], 'a.txt', { type: 'text/plain' });
    const handle = uploadFileResumable(file, { resumeUploadId: 'bad-id!' });
    await expect(handle.promise).rejects.toThrow('uploadId 非法');
  });

  it('uploadFileResumable rejects when selected file does not match resumable task size', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();
    replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 2, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });

    const file = new File(['x'], 'a.txt', { type: 'text/plain' });
    const handle = uploadFileResumable(file, { resumeUploadId: 'u1' });
    await expect(handle.promise).rejects.toThrow('所选文件大小与待续传任务不一致');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('uploadFileResumable supports resuming by resumeUploadId without calling init', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();
    replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 1, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: '' } });

    const file = new File(['x'], 'a.txt', { type: 'text/plain' });
    const handle = uploadFileResumable(file, { resumeUploadId: 'u1' });
    await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[0]?.[0] || '')).toContain('/api/uploads/resumable/u1');
  });

  it('uploadFileResumable sends init/complete requests with csrf header and credentials', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 0, mimeType: 'text/plain' } });

    const file = new File([], 'a.txt', { type: 'text/plain' });
    const handle = uploadFileResumable(file);
    await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt', fileUrl: '/u', fileSize: 0, mimeType: 'text/plain' });

    expect(fetchMock).toHaveBeenCalledTimes(2);

    const initCall = fetchMock.mock.calls[0] as any;
    const initInfo = getFetchCallInfo(initCall);
    expect(initInfo?.url).toBe('/api/uploads/resumable/init');
    expect(initInfo?.method).toBe('POST');
    expect(initInfo?.init?.credentials).toBe('include');
    expect(initInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(initInfo?.body).toBe(JSON.stringify({ fileName: 'a.txt', fileSize: 0, mimeType: 'text/plain' }));

    const completeCall = fetchMock.mock.calls[1] as any;
    const completeInfo = getFetchCallInfo(completeCall);
    expect(completeInfo?.url).toBe('/api/uploads/resumable/u1/complete');
    expect(completeInfo?.method).toBe('POST');
    expect(completeInfo?.init?.credentials).toBe('include');
    expect(completeInfo?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('uploadFileResumable supports pause/resume gate', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' } });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      private headers: Record<string, string> = {};
      open = vi.fn();
      setRequestHeader = vi.fn((k: string, v: string) => {
        this.headers[k] = v;
      });
      getResponseHeader = vi.fn(() => null);
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 200;
        this.responseText = JSON.stringify({ uploadedBytes: 1 });
        this.onload?.();
      });
    }
    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file);
      handle.pause();
      await Promise.resolve();
      handle.resume();
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable retries 5xx once, calls onRetry, then succeeds', async () => {
    vi.useFakeTimers();
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
    replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024 } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' } });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    const sendQueue: Array<(xhr: any) => void> = [
      (xhr) => {
        xhr.status = 503;
        xhr.responseText = JSON.stringify({ message: 'server down' });
        xhr.getResponseHeader = () => 'rid';
        xhr.onload?.();
      },
      (xhr) => {
        xhr.status = 200;
        xhr.responseText = JSON.stringify({ uploadedBytes: 1 });
        xhr.getResponseHeader = () => null;
        xhr.onload?.();
      },
    ];

    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      getResponseHeader = vi.fn(() => null);
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        const fn = sendQueue.shift();
        if (!fn) throw new Error('no send behavior');
        fn(this);
      });
    }

    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const onRetry = vi.fn();
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file, { onRetry });
      await vi.runAllTimersAsync();
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
      expect(onRetry).toHaveBeenCalledTimes(1);
      expect(String(onRetry.mock.calls[0]?.[0]?.message || '')).toContain('server down');
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
      vi.useRealTimers();
    }
  });

  it('uploadFileResumable treats HTTP 0 as retryable and retries', async () => {
    vi.useFakeTimers();
    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    try {
      const { uploadFileResumable } = await import('./uploadService');
      const { replyOnce } = installFetchMock();

      replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: '' } });

      const onRetry = vi.fn();
      const sendQueue: Array<(xhr: any, blob: Blob) => void> = [
        (xhr) => {
          xhr.status = 0;
          xhr.responseText = '';
          xhr.getResponseHeader = () => null;
          xhr.onload?.();
        },
        (xhr, blob) => {
          xhr.status = 200;
          xhr.responseText = JSON.stringify({ uploadedBytes: blob.size });
          xhr.getResponseHeader = () => null;
          xhr.onload?.();
        },
      ];

      class MockXhr {
        upload = { onprogress: null as any };
        onerror: null | (() => void) = null;
        onabort: null | (() => void) = null;
        onload: null | (() => void) = null;
        withCredentials = false;
        status = 0;
        responseText = '';
        open = vi.fn();
        setRequestHeader = vi.fn();
        getResponseHeader = vi.fn(() => null);
        abort = vi.fn(() => {
          this.onabort?.();
        });
        send = vi.fn((blob: Blob) => {
          const fn = sendQueue.shift();
          if (!fn) throw new Error('no send behavior');
          fn(this, blob);
        });
      }

      (globalThis as any).XMLHttpRequest = MockXhr;

      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file, { onRetry });
      await vi.runAllTimersAsync();
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
      expect(onRetry).toHaveBeenCalled();
      expect(String(onRetry.mock.calls[0]?.[0]?.status)).toBe('0');
    } finally {
      vi.useRealTimers();
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable uses blob.size when server response has no uploadedBytes', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' } });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      getResponseHeader = vi.fn(() => null);
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 200;
        this.responseText = '{}';
        this.onload?.();
      });
    }
    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file);
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable syncs offset when chunk fails with offset mismatch', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
    replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 1, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' } });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      getResponseHeader = vi.fn(() => null);
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 409;
        this.responseText = JSON.stringify({ message: '偏移量不匹配' });
        this.onload?.();
      });
    }
    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file);
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt' });
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable throws on non-retryable chunk failures', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });

    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    class MockXhr {
      upload = { onprogress: null as any };
      onerror: null | (() => void) = null;
      onabort: null | (() => void) = null;
      onload: null | (() => void) = null;
      withCredentials = false;
      status = 0;
      responseText = '';
      open = vi.fn();
      setRequestHeader = vi.fn();
      getResponseHeader = vi.fn(() => null);
      abort = vi.fn(() => {
        this.onabort?.();
      });
      send = vi.fn(() => {
        this.status = 400;
        this.responseText = JSON.stringify({ message: 'bad' });
        this.onload?.();
      });
    }
    (globalThis as any).XMLHttpRequest = MockXhr;
    try {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file);
      await expect(handle.promise).rejects.toThrow('bad');
      expect(fetchMock).toHaveBeenCalledTimes(1);
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable stops after max retry attempts', async () => {
    vi.useFakeTimers();
    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    try {
      const { uploadFileResumable } = await import('./uploadService');
      const { replyOnce } = installFetchMock();

      replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });

      class MockXhr {
        upload = { onprogress: null as any };
        onerror: null | (() => void) = null;
        onabort: null | (() => void) = null;
        onload: null | (() => void) = null;
        withCredentials = false;
        status = 0;
        responseText = '';
        open = vi.fn();
        setRequestHeader = vi.fn();
        getResponseHeader = vi.fn((k: string) => (k === 'X-Request-Id' ? 'rid1' : null));
        abort = vi.fn(() => {
          this.onabort?.();
        });
        send = vi.fn(() => {
          this.status = 503;
          this.responseText = JSON.stringify({ message: 'tmp' });
          this.onload?.();
        });
      }
      (globalThis as any).XMLHttpRequest = MockXhr;

      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file);
      const rejection = expect(handle.promise).rejects.toThrow('分片上传多次失败');
      await vi.runAllTimersAsync();
      await rejection;
    } finally {
      vi.useRealTimers();
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable throws backend init message on failure', async () => {
    const { uploadFileResumable } = await import('./uploadService');
    const { replyOnce, fetchMock } = installFetchMock();
    replyOnce({ ok: false, status: 400, json: { message: 'init bad' } });
    const file = new File([], 'a.txt', { type: 'text/plain' });
    const handle = uploadFileResumable(file);
    await expect(handle.promise).rejects.toThrow('init bad');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('getResumableUploadStatus applies fallbacks and sends csrf header and credentials', async () => {
    const { getResumableUploadStatus } = await import('./uploadService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { uploadId: 'u2', uploadedBytes: 1, status: 'OK' } });

    const res = await getResumableUploadStatus('u2', { fileName: 'fallback.txt', fileSize: 9 });
    expect(res).toMatchObject({
      uploadId: 'u2',
      fileName: 'fallback.txt',
      fileSize: 9,
      uploadedBytes: 1,
      status: 'OK',
      chunkSizeBytes: 5 * 1024 * 1024,
    });

    const call = lastCall();
    expect(call?.[0]).toBe('/api/uploads/resumable/u2');
    expect(call?.[1]).toMatchObject({
      method: 'GET',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf' },
    });
  });

  it('getResumableUploadStatus uses default fallback values and keeps typed optional fields', async () => {
    const { getResumableUploadStatus } = await import('./uploadService');
    const { replyOnce } = installFetchMock();
    replyOnce({
      ok: true,
      json: { uploadId: 'u2', uploadedBytes: 1, status: 'OK', verifyBytes: 1, verifyTotalBytes: 2, updatedAtEpochMs: 3, errorMessage: 'e' },
    });

    const res = await getResumableUploadStatus('u2');
    expect(res).toMatchObject({
      uploadId: 'u2',
      fileName: 'file',
      fileSize: 0,
      uploadedBytes: 1,
      verifyBytes: 1,
      verifyTotalBytes: 2,
      updatedAtEpochMs: 3,
      errorMessage: 'e',
    });
  });

  it('cancelResumableUpload is a no-op on blank uploadId', async () => {
    const { cancelResumableUpload } = await import('./uploadService');
    const { fetchMock } = installFetchMock();
    const csrf = await import('../utils/csrfUtils');

    await expect(cancelResumableUpload('   ')).resolves.toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
    expect((csrf as any).getCsrfToken).not.toHaveBeenCalled();
  });

  it('cancelResumableUpload is a no-op when uploadId is not a string', async () => {
    const { cancelResumableUpload } = await import('./uploadService');
    const { fetchMock } = installFetchMock();
    await expect(cancelResumableUpload(null as any)).resolves.toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('findUploadBySha256 returns null on 404 without parsing json', async () => {
    const { findUploadBySha256 } = await import('./uploadService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 404, jsonError: new Error('nope') });
    await expect(findUploadBySha256('abc')).resolves.toBeNull();
  });

  it('findUploadBySha256 falls back fileName to default when not provided', async () => {
    const { findUploadBySha256 } = await import('./uploadService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u' } });

    const res = await findUploadBySha256('abc');
    expect(res).toMatchObject({ id: 1, fileName: 'file', fileUrl: '/u' });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('sha256=abc');
    expect(info?.url).not.toContain('fileName=');
  });

  it('findUploadBySha256 sends query params, csrf header and credentials', async () => {
    const { findUploadBySha256 } = await import('./uploadService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { id: 1, fileUrl: '/u' } });

    const res = await findUploadBySha256('a b', 'x.txt');
    expect(res).toMatchObject({ id: 1, fileName: 'x.txt', fileUrl: '/u' });

    const call = lastCall();
    const info = getFetchCallInfo(call);
    expect(info?.url).toContain('/api/uploads/by-sha256?');
    expect(info?.url).toContain('sha256=a+b');
    expect(info?.url).toContain('fileName=x.txt');
    expect(info?.method).toBe('GET');
    expect(info?.init?.credentials).toBe('include');
    expect(info?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('cancelResumableUpload sends DELETE with csrf header and credentials', async () => {
    const { cancelResumableUpload } = await import('./uploadService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, status: 204, text: '' });
    await expect(cancelResumableUpload('u1')).resolves.toBeUndefined();
    expect(lastCall()?.[0]).toBe('/api/uploads/resumable/u1');
    expect(lastCall()?.[1]).toMatchObject({ method: 'DELETE', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'csrf' } });
  });

  it('cancelResumableUpload swallows delete failures', async () => {
    const { cancelResumableUpload } = await import('./uploadService');
    const { rejectOnce } = installFetchMock();
    rejectOnce(new Error('boom'));
    await expect(cancelResumableUpload('u1')).resolves.toBeUndefined();
  });

  it('getResumableUploadStatus throws backend message on failure', async () => {
    const { getResumableUploadStatus } = await import('./uploadService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getResumableUploadStatus('u1')).rejects.toThrow('bad');
  });

  it('findUploadBySha256 throws fallback message when backend message missing', async () => {
    const { findUploadBySha256 } = await import('./uploadService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, json: {} });
    await expect(findUploadBySha256('abc')).rejects.toThrow('查重失败');
  });

  it('listUploads returns empty list', async () => {
    const { listUploads } = await import('./uploadService');
    await expect(listUploads()).resolves.toEqual([]);
  });

  it('uploadFileResumable retries retryable chunk failures and reports onRetry', async () => {
    vi.useFakeTimers();
    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    try {
      const { uploadFileResumable } = await import('./uploadService');
      const { replyOnce } = installFetchMock();
      replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
      replyOnce({ ok: true, json: { uploadId: 'u1', fileName: 'a.txt', fileSize: 1, uploadedBytes: 0, chunkSizeBytes: 1024 * 1024, status: 'UPLOADING' } });
      replyOnce({ ok: true, json: { id: 1, fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' } });

      const onRetry = vi.fn();

      let attempt = 0;
      class MockXhr {
        upload = { onprogress: null as any };
        onerror: null | (() => void) = null;
        onabort: null | (() => void) = null;
        onload: null | (() => void) = null;
        withCredentials = false;
        status = 0;
        responseText = '';
        open = vi.fn();
        setRequestHeader = vi.fn();
        getResponseHeader = vi.fn((k: string) => (k === 'X-Request-Id' ? 'rid1' : null));
        abort = vi.fn(() => {
          this.onabort?.();
        });
        send = vi.fn((blob: Blob) => {
          attempt += 1;
          if (attempt === 1) {
            this.status = 503;
            this.responseText = JSON.stringify({ message: 'tmp' });
            this.onload?.();
            return;
          }
          this.status = 200;
          this.responseText = JSON.stringify({ uploadedBytes: blob.size });
          this.onload?.();
        });
      }

      (globalThis as any).XMLHttpRequest = MockXhr;

      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      const handle = uploadFileResumable(file, { onRetry });

      await vi.runAllTimersAsync();
      await expect(handle.promise).resolves.toMatchObject({ id: 1, fileName: 'a.txt', fileUrl: '/u', fileSize: 1, mimeType: 'text/plain' });
      expect(onRetry).toHaveBeenCalled();
      expect(attempt).toBeGreaterThanOrEqual(2);
    } finally {
      vi.useRealTimers();
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });

  it('uploadFileResumable cancel after init triggers cleanup request and rejects', async () => {
    const OriginalXhr = (globalThis as any).XMLHttpRequest;
    try {
      const { uploadFileResumable } = await import('./uploadService');
      const { replyOnce, fetchMock } = installFetchMock();
      replyOnce({ ok: true, json: { uploadId: 'u1', chunkSizeBytes: 1024 * 1024, uploadedBytes: 0 } });
      replyOnce({ ok: true, status: 204, text: '' });

      class MockXhr {
        upload = { onprogress: null as any };
        onerror: null | (() => void) = null;
        onabort: null | (() => void) = null;
        onload: null | (() => void) = null;
        withCredentials = false;
        status = 0;
        responseText = '';
        open = vi.fn();
        setRequestHeader = vi.fn();
        getResponseHeader = vi.fn(() => null);
        abort = vi.fn(() => {
          this.onabort?.();
        });
        send = vi.fn(() => {
        });
      }
      (globalThis as any).XMLHttpRequest = MockXhr;

      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      let initUploadId: string | null = null;
      const handle = uploadFileResumable(file, {
        onInit: (p) => {
          initUploadId = p.uploadId;
        },
      });

      await new Promise<void>((r) => setTimeout(r, 0));
      expect(initUploadId).toBe('u1');

      handle.cancel();
      await expect(handle.promise).rejects.toThrow('已取消上传');

      const delCall = fetchMock.mock.calls.find((c) => String(c?.[0]).includes('/api/uploads/resumable/u1') && (c?.[1] as any)?.method === 'DELETE');
      expect(delCall).toBeTruthy();
    } finally {
      (globalThis as any).XMLHttpRequest = OriginalXhr;
    }
  });
});
