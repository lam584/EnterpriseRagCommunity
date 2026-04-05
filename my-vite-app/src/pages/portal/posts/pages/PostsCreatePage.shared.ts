import { slugify, type TagDTO } from '../../../../services/tagService';

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

export function formatUploadRetryMessage(params: {
    attempt: number;
    maxAttempts: number;
    delayMs: number;
    status?: number | null;
    requestId?: string | null;
}): string {
    const seconds = Math.max(1, Math.ceil(params.delayMs / 1000));
    const statusText = params.status != null ? `HTTP ${params.status}` : '网络错误';
    const requestIdSuffix = params.requestId ? ` requestId=${params.requestId}` : '';
    return `重试中（${params.attempt}/${params.maxAttempts}，${seconds}s 后）${statusText}${requestIdSuffix}`;
}

export function shouldReportThrottledProgress(params: {
    loaded: number;
    total: number;
    nowMs: number;
    lastReportAtMs: number;
    minIntervalMs?: number;
}): boolean {
    const isFinal = params.total > 0 && params.loaded >= params.total;
    if (isFinal) return true;
    const minIntervalMs = params.minIntervalMs ?? 250;
    return params.nowMs - params.lastReportAtMs >= minIntervalMs;
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

export function normalizeRequestedTagNames(names: string[], limit?: number): string[] {
    const requested = names.map((x) => String(x || '').trim()).filter((x) => x.length > 0);
    if (limit == null) return requested;
    return requested.slice(0, Math.max(1, Math.trunc(limit)));
}

export function normalizeSuggestedLanguages(input: unknown, limit = 3): string[] {
    const max = Math.max(1, Math.trunc(limit));
    if (!Array.isArray(input)) return [];
    return input
        .map((x) => String(x || '').trim())
        .filter((x) => x.length > 0)
        .slice(0, max);
}

export async function suggestLanguagesToPublish(opts: {
    enabled: boolean;
    title: string | null | undefined;
    content: string | null | undefined;
    suggest: (payload: {title?: string; content: string}) => Promise<{languages?: unknown}>;
}): Promise<string[]> {
    if (!opts.enabled) return [];
    try {
        const resp = await opts.suggest({
            title: opts.title?.trim() || undefined,
            content: (opts.content ?? '').trim(),
        });
        return normalizeSuggestedLanguages(resp.languages);
    } catch {
        return [];
    }
}

export async function ensureTagSlugsWithAvailableTags(opts: {
    names: string[];
    availableTags: TagDTO[];
    limit?: number;
    createTag: (name: string, slug: string) => Promise<TagDTO>;
    onCreateError?: (e: unknown) => void;
}): Promise<{slugs: string[]; availableTags: TagDTO[]}> {
    const requested = normalizeRequestedTagNames(opts.names, opts.limit);
    if (requested.length === 0) return {slugs: [], availableTags: opts.availableTags};

    let localAvailable = opts.availableTags;
    const slugs: string[] = [];
    for (const name of requested) {
        const targetSlug = slugify(name);
        const exists = localAvailable.find((t) => t.slug === targetSlug) ?? localAvailable.find((t) => t.name === name);
        if (exists) {
            slugs.push(exists.slug);
            continue;
        }
        try {
            const created = await opts.createTag(name, targetSlug);
            localAvailable = [created, ...localAvailable.filter((x) => x.id !== created.id)];
            slugs.push(created.slug);
        } catch (e: unknown) {
            opts.onCreateError?.(e);
        }
    }
    return {slugs, availableTags: localAvailable};
}
