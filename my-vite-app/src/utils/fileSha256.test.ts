import { afterEach, describe, expect, it, vi } from 'vitest';
import { computeFileSha256 } from './fileSha256';

describe('computeFileSha256', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    (vi as any).unstubAllGlobals?.();
  });

  it('computes sha256 for a small blob', async () => {
    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const sha = await computeFileSha256(blob, { chunkSizeBytes: 2 });
    expect(sha).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');
  });

  it('uses default chunkSizeBytes and Blob.arrayBuffer when available', async () => {
    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const orig = (Blob.prototype as any).arrayBuffer;
    try {
      if (typeof orig !== 'function') {
        (Blob.prototype as any).arrayBuffer = async function (this: Blob) {
          return await new Promise<ArrayBuffer>((resolve, reject) => {
            const r = new FileReader();
            r.onerror = () => reject(new Error('read failed'));
            r.onload = () => resolve(r.result as ArrayBuffer);
            r.readAsArrayBuffer(this);
          });
        };
      }
      const spy = vi.spyOn(Blob.prototype as any, 'arrayBuffer');
      const sha = await computeFileSha256(blob);
      expect(sha).toMatch(/^[0-9a-f]{64}$/);
      expect(spy).toHaveBeenCalled();
    } finally {
      (Blob.prototype as any).arrayBuffer = orig;
    }
  });

  it('reports progress and supports abort', async () => {
    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const onProgress = vi.fn();
    const ctrl = new AbortController();
    ctrl.abort();
    await expect(computeFileSha256(blob, { chunkSizeBytes: 2, signal: ctrl.signal, onProgress })).rejects.toThrow('已取消');
    expect(onProgress).toHaveBeenCalledTimes(1);
    expect(onProgress.mock.calls[0]?.[0]).toMatchObject({ loaded: 0, total: blob.size });
  });

  it('throws when aborted after reading a chunk', async () => {
    const blob = new Blob([new Uint8Array([1, 2, 3, 4, 5])], { type: 'application/octet-stream' });
    const ctrl = new AbortController();
    vi.spyOn(Blob.prototype as any, 'arrayBuffer').mockImplementation(async () => {
      ctrl.abort();
      return new Uint8Array([1, 2, 3]).buffer;
    });
    await expect(computeFileSha256(blob, { chunkSizeBytes: 2, signal: ctrl.signal })).rejects.toThrow('已取消');
  });

  it('reports percent=1 when total size is not a number', async () => {
    const onProgress = vi.fn();
    const fileLike: any = { size: 'nope', slice: () => new Blob([]) };
    const sha = await computeFileSha256(fileLike, { onProgress });
    expect(sha).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    expect(onProgress).toHaveBeenCalledTimes(1);
    expect(onProgress.mock.calls[0]?.[0]).toEqual({ loaded: 0, total: 0, percent: 1 });
  });

  it('uses global setTimeout when window is undefined', async () => {
    vi.stubGlobal('window', undefined as any);
    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const sha = await computeFileSha256(blob, { chunkSizeBytes: 2 });
    expect(sha).toMatch(/^[0-9a-f]{64}$/);
  });

  it('falls back to Response(arrayBuffer) when Blob.arrayBuffer and FileReader are unavailable', async () => {
    const origFileReader = (globalThis as any).FileReader;
    const origArrayBuffer = (Blob.prototype as any).arrayBuffer;

    (globalThis as any).FileReader = undefined;
    (Blob.prototype as any).arrayBuffer = undefined;

    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const sha = await computeFileSha256(blob, { chunkSizeBytes: 1024 });
    expect(sha).toMatch(/^[0-9a-f]{64}$/);

    (globalThis as any).FileReader = origFileReader;
    (Blob.prototype as any).arrayBuffer = origArrayBuffer;
  });

  it('falls back to FileReader when Blob.arrayBuffer is unavailable', async () => {
    const origFileReader = (globalThis as any).FileReader;
    const origArrayBuffer = (Blob.prototype as any).arrayBuffer;

    const readers: any[] = [];
    (globalThis as any).FileReader = class {
      onerror: null | (() => void) = null;
      onload: null | (() => void) = null;
      result: ArrayBuffer | null = null;
      readAsArrayBuffer(_b: Blob) {
        this.result = new TextEncoder().encode('hello').buffer;
        readers.push(this);
        this.onload?.();
      }
    };
    (Blob.prototype as any).arrayBuffer = undefined;

    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    const sha = await computeFileSha256(blob, { chunkSizeBytes: 1024 });
    expect(sha).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');
    expect(readers.length).toBeGreaterThan(0);

    (globalThis as any).FileReader = origFileReader;
    (Blob.prototype as any).arrayBuffer = origArrayBuffer;
  });

  it('throws a readable error when FileReader fails', async () => {
    const origFileReader = (globalThis as any).FileReader;
    const origArrayBuffer = (Blob.prototype as any).arrayBuffer;

    (globalThis as any).FileReader = class {
      onerror: null | (() => void) = null;
      onload: null | (() => void) = null;
      readAsArrayBuffer(_b: Blob) {
        this.onerror?.();
      }
    };
    (Blob.prototype as any).arrayBuffer = undefined;

    const blob = new Blob([new TextEncoder().encode('hello')], { type: 'text/plain' });
    await expect(computeFileSha256(blob, { chunkSizeBytes: 1024 })).rejects.toThrow('读取文件失败');

    (globalThis as any).FileReader = origFileReader;
    (Blob.prototype as any).arrayBuffer = origArrayBuffer;
  });
});
