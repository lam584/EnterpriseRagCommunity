import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex } from '@noble/hashes/utils';

export type FileHashProgress = {
  loaded: number;
  total: number;
  percent: number;
};

export async function computeFileSha256(
  file: Blob,
  options?: {
    chunkSizeBytes?: number;
    signal?: AbortSignal;
    onProgress?: (p: FileHashProgress) => void;
  },
): Promise<string> {
  const total = typeof (file as { size?: unknown }).size === 'number' ? (file as { size: number }).size : 0;
  const rawChunk = options?.chunkSizeBytes ?? 4 * 1024 * 1024;
  const chunkSize = Math.max(256 * 1024, Math.min(32 * 1024 * 1024, Math.trunc(rawChunk)));

  const h = sha256.create();
  let offset = 0;
  const readArrayBuffer = async (b: Blob): Promise<ArrayBuffer> => {
    const anyB = b as unknown as { arrayBuffer?: () => Promise<ArrayBuffer> };
    if (typeof anyB.arrayBuffer === 'function') return await anyB.arrayBuffer();
    if (typeof FileReader !== 'undefined') {
      return await new Promise<ArrayBuffer>((resolve, reject) => {
        const r = new FileReader();
        r.onerror = () => reject(new Error('读取文件失败'));
        r.onload = () => resolve(r.result as ArrayBuffer);
        r.readAsArrayBuffer(b);
      });
    }
    return await new Response(b).arrayBuffer();
  };
  const yieldLoop = async () => {
    await new Promise<void>((resolve) => {
      const setT = typeof window !== 'undefined' ? window.setTimeout : setTimeout;
      setT(resolve, 0);
    });
  };

  const report = (loaded: number) => {
    const percent = total > 0 ? Math.max(0, Math.min(1, loaded / total)) : 1;
    options?.onProgress?.({ loaded, total, percent });
  };

  report(0);

  while (offset < total) {
    if (options?.signal?.aborted) throw new Error('已取消');
    const end = Math.min(total, offset + chunkSize);
    const buf = await readArrayBuffer(file.slice(offset, end));
    if (options?.signal?.aborted) throw new Error('已取消');
    h.update(new Uint8Array(buf));
    offset = end;
    report(offset);
    await yieldLoop();
  }

  return bytesToHex(h.digest());
}
