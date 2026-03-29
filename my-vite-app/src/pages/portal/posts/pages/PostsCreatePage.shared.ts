export function getErrorMessage(e: unknown, fallback: string) {
    if (e && typeof e === 'object' && 'message' in e) {
        const m = (e as { message?: unknown }).message;
        if (typeof m === 'string') return m;
    }
    return fallback;
}

export function getFieldErrors(e: unknown): Record<string, string> | undefined {
    if (!e || typeof e !== 'object') return undefined;
    if (!('fieldErrors' in e)) return undefined;
    const fe = (e as { fieldErrors?: unknown }).fieldErrors;
    if (!fe || typeof fe !== 'object') return undefined;
    // fieldErrors is expected like: { title: '...', content: '...' }
    return fe as Record<string, string>;
}

export type UploadItemStatus = 'uploading' | 'verifying' | 'finalizing' | 'paused' | 'done' | 'error' | 'canceled';

export type UploadItemDedupeStatus = 'hashing' | 'checking' | 'hit' | 'miss' | 'error' | null;

export type UploadItem = {
    id: string;
    kind: 'image' | 'attachment';
    fileName: string;
    fileSize: number;
    status: UploadItemStatus;
    dedupeStatus: UploadItemDedupeStatus;
    sha256: string | null;
    hashLoaded: number;
    hashTotal: number;
    loaded: number;
    total: number;
    speedBps: number | null;
    etaSeconds: number | null;
    lastTickAtMs: number | null;
    lastLoaded: number;
    speedSampleAtMs: number | null;
    speedSampleLoaded: number;
    serverUploadId: string | null;
    verifyLoaded: number;
    verifyTotal: number;
    verifySpeedBps: number | null;
    verifyEtaSeconds: number | null;
    verifyLastTickAtMs: number | null;
    verifyLastLoaded: number;
    verifySpeedSampleAtMs: number | null;
    verifySpeedSampleLoaded: number;
    errorMessage: string | null;
};

export function clampCount(n: number, maxCount?: number | null): number {
    const max = Math.max(1, Math.min(maxCount ?? 50, 50));
    const nn = Number.isFinite(n) ? Math.trunc(n) : 1;
    return Math.max(1, Math.min(max, nn));
}

export function normalizeCount(raw: unknown, fallback: number): number {
    const n = typeof raw === 'number' ? raw : raw == null ? NaN : Number(raw);
    if (!Number.isFinite(n)) return fallback;
    const nn = Math.trunc(n);
    if (nn < 1 || nn > 50) return fallback;
    return nn;
}
