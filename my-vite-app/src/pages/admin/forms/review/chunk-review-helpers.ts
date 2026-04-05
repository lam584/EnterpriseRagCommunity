export function clampInt(n: number, min: number, max: number) {
    if (!Number.isFinite(n)) return min;
    return Math.min(Math.max(Math.trunc(n), min), max);
}

export function formatDateTime(s?: string | null): string {
    if (!s) return '—';
    const d = new Date(s);
    if (Number.isNaN(d.getTime())) return String(s);
    return d.toLocaleString();
}

export function safeJson(v: unknown): string {
    try {
        return JSON.stringify(v, null, 2);
    } catch {
        return String(v);
    }
}

export const SOURCE_TYPE_ZH: Record<string, string> = {
    POST_TEXT: '帖子文本',
    FILE_TEXT: '文件文本',
};

export const STATUS_ZH: Record<string, string> = {
    PENDING: '待处理',
    RUNNING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
};

export const VERDICT_ZH: Record<string, string> = {
    APPROVE: '通过',
    REJECT: '拒绝',
    REVIEW: '需人工复核',
};

export function formatEnumZh(code: string | null | undefined, mapping: Record<string, string>): string {
    if (!code) return '—';
    const zh = mapping[code];
    return zh ? `${zh}（${code}）` : code;
}

export function parseOptionalPositiveInt(s: string): number | undefined {
    const raw = s.trim();
    if (!raw) return undefined;
    const n = Number(raw);
    if (!Number.isFinite(n) || n <= 0) return undefined;
    return Math.floor(n);
}

export type BudgetConvergenceView = {
    baseChunkSizeChars: number;
    effectiveChunkSizeChars: number;
    baseTextTokenBudget: number;
    effectiveTextTokenBudget: number;
    estimatedImageTokens: number;
    imageTokenBudget: number;
    totalBudgetTokens: number;
    triggeredResharding: boolean;
    rounds: Array<{
        round: number;
        textTokenBudget: number;
        chunkSizeChars: number;
        estimatedTotalRequestTokens: number
    }>;
};

export type TokenDiagnosticsView = {
    promptChars?: number;
    promptTokens?: number;
    promptTokensPerPromptChar?: number;
    promptTokensPerImage?: number;
    imagesSent?: number;
    visionMaxImagesPerRequest?: number;
    visionImageTokenBudget?: number;
    visionHighResolutionImages?: boolean;
    visionMaxPixels?: number;
    estimatedImageBatchesByCount?: number;
    imageUrlKinds?: {
        localUpload?: number;
        dataUrl?: number;
        remoteUrl?: number;
        other?: number;
    };
    hypotheses?: string[];
};

export function toFiniteInt(v: unknown): number | null {
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n)) return null;
    return Math.trunc(n);
}

export function parseBudgetConvergenceLog(v: unknown): BudgetConvergenceView | null {
    if (!v || typeof v !== 'object') return null;
    const o = v as Record<string, unknown>;
    const baseChunkSizeChars = toFiniteInt(o.baseChunkSizeChars);
    const effectiveChunkSizeChars = toFiniteInt(o.effectiveChunkSizeChars);
    const baseTextTokenBudget = toFiniteInt(o.baseTextTokenBudget);
    const effectiveTextTokenBudget = toFiniteInt(o.effectiveTextTokenBudget);
    const estimatedImageTokens = toFiniteInt(o.estimatedImageTokens);
    const imageTokenBudget = toFiniteInt(o.imageTokenBudget);
    const totalBudgetTokens = toFiniteInt(o.totalBudgetTokens);
    if (
        baseChunkSizeChars == null ||
        effectiveChunkSizeChars == null ||
        baseTextTokenBudget == null ||
        effectiveTextTokenBudget == null ||
        estimatedImageTokens == null ||
        imageTokenBudget == null ||
        totalBudgetTokens == null
    ) {
        return null;
    }
    const roundsRaw = Array.isArray(o.rounds) ? o.rounds : [];
    const rounds = roundsRaw
        .map((it) => {
            if (!it || typeof it !== 'object') return null;
            const x = it as Record<string, unknown>;
            const round = toFiniteInt(x.round);
            const textTokenBudget = toFiniteInt(x.textTokenBudget);
            const chunkSizeChars = toFiniteInt(x.chunkSizeChars);
            const estimatedTotalRequestTokens = toFiniteInt(x.estimatedTotalRequestTokens);
            if (round == null || textTokenBudget == null || chunkSizeChars == null || estimatedTotalRequestTokens == null) return null;
            return {round, textTokenBudget, chunkSizeChars, estimatedTotalRequestTokens};
        })
        .filter((it): it is NonNullable<typeof it> => Boolean(it));

    return {
        baseChunkSizeChars,
        effectiveChunkSizeChars,
        baseTextTokenBudget,
        effectiveTextTokenBudget,
        estimatedImageTokens,
        imageTokenBudget,
        totalBudgetTokens,
        triggeredResharding: Boolean(o.triggeredResharding),
        rounds,
    };
}

export function formatBudgetConvergenceSummary(v: unknown): string {
    const p = parseBudgetConvergenceLog(v);
    if (!p) return '—';
    return `预算 ${p.baseTextTokenBudget}→${p.effectiveTextTokenBudget}；Chunk ${p.baseChunkSizeChars}→${p.effectiveChunkSizeChars}`;
}

export function parseTokenDiagnostics(v: unknown): TokenDiagnosticsView | null {
    if (!v || typeof v !== 'object') return null;
    const o = v as Record<string, unknown>;
    const imageUrlKindsRaw = o.imageUrlKinds && typeof o.imageUrlKinds === 'object' ? (o.imageUrlKinds as Record<string, unknown>) : null;
    const hypothesesRaw = Array.isArray(o.hypotheses) ? o.hypotheses.filter((x): x is string => typeof x === 'string' && x.trim().length > 0) : [];
    return {
        promptChars: toFiniteInt(o.promptChars) ?? undefined,
        promptTokens: toFiniteInt(o.promptTokens) ?? undefined,
        promptTokensPerPromptChar:
            typeof o.promptTokensPerPromptChar === 'number'
                ? o.promptTokensPerPromptChar
                : Number.isFinite(Number(o.promptTokensPerPromptChar))
                    ? Number(o.promptTokensPerPromptChar)
                    : undefined,
        promptTokensPerImage:
            typeof o.promptTokensPerImage === 'number'
                ? o.promptTokensPerImage
                : Number.isFinite(Number(o.promptTokensPerImage))
                    ? Number(o.promptTokensPerImage)
                    : undefined,
        imagesSent: toFiniteInt(o.imagesSent) ?? undefined,
        visionMaxImagesPerRequest: toFiniteInt(o.visionMaxImagesPerRequest) ?? undefined,
        visionImageTokenBudget: toFiniteInt(o.visionImageTokenBudget) ?? undefined,
        visionHighResolutionImages: typeof o.visionHighResolutionImages === 'boolean' ? o.visionHighResolutionImages : undefined,
        visionMaxPixels: toFiniteInt(o.visionMaxPixels) ?? undefined,
        estimatedImageBatchesByCount: toFiniteInt(o.estimatedImageBatchesByCount) ?? undefined,
        imageUrlKinds: imageUrlKindsRaw
            ? {
                localUpload: toFiniteInt(imageUrlKindsRaw.localUpload) ?? undefined,
                dataUrl: toFiniteInt(imageUrlKindsRaw.dataUrl) ?? undefined,
                remoteUrl: toFiniteInt(imageUrlKindsRaw.remoteUrl) ?? undefined,
                other: toFiniteInt(imageUrlKindsRaw.other) ?? undefined,
            }
            : undefined,
        hypotheses: hypothesesRaw,
    };
}

export const VISION_TOKEN_GRID_SIDE = 32 as const;
export const VISION_TOKEN_PIXELS = VISION_TOKEN_GRID_SIDE * VISION_TOKEN_GRID_SIDE;
export const VISION_TOKEN_LIMIT = 16_384;
export const DEFAULT_DASHSCOPE_MAX_PIXELS = 2_621_440;

export type ImageNaturalSize = { width: number; height: number };

function abortError(): Error {
    const e = new Error('Aborted');
    (e as { name?: string }).name = 'AbortError';
    return e;
}

export function isAbortError(e: unknown): boolean {
    if (!e) return false;
    if (e instanceof DOMException) return e.name === 'AbortError';
    return (e as { name?: string }).name === 'AbortError';
}

export function toUrlString(v: unknown): string | null {
    const s = typeof v === 'string' ? v.trim() : '';
    return s ? s : null;
}

export type ImageSizeLike = {
    url?: unknown;
    width?: number | null;
    height?: number | null;
};

export function hasResolvedImageSize(img: ImageSizeLike): boolean {
    const w = img.width;
    const h = img.height;
    return typeof w === 'number' && Number.isFinite(w) && w > 0 && typeof h === 'number' && Number.isFinite(h) && h > 0;
}

export function collectMissingImageSizeEntries<T extends ImageSizeLike>(imgs: T[]) {
    return imgs
        .map((img, idx) => ({img, idx, url: toUrlString(img.url)}))
        .filter(({img, url}) => Boolean(url) && !hasResolvedImageSize(img));
}

export function createImageSizeProbe(opts: { concurrency: number }) {
    const cache = new Map<string, ImageNaturalSize | null>();
    const inflight = new Map<string, Promise<ImageNaturalSize | null>>();
    const queue: Array<{
        url: string;
        signal: AbortSignal;
        resolve: (v: ImageNaturalSize | null) => void;
        reject: (e: unknown) => void;
    }> = [];
    let active = 0;

    const load = (url: string, signal: AbortSignal): Promise<ImageNaturalSize | null> =>
        new Promise((resolve, reject) => {
            if (signal.aborted) {
                reject(abortError());
                return;
            }

            const img = new Image();
            img.decoding = 'async';

            const cleanup = () => {
                img.onload = null;
                img.onerror = null;
            };

            const onAbort = () => {
                cleanup();
                img.src = '';
                reject(abortError());
            };

            signal.addEventListener('abort', onAbort, {once: true});

            img.onload = () => {
                signal.removeEventListener('abort', onAbort);
                cleanup();
                const w = img.naturalWidth;
                const h = img.naturalHeight;
                if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) {
                    resolve(null);
                    return;
                }
                resolve({width: Math.trunc(w), height: Math.trunc(h)});
            };

            img.onerror = () => {
                signal.removeEventListener('abort', onAbort);
                cleanup();
                resolve(null);
            };

            img.src = url;
        });

    const pump = () => {
        while (active < Math.max(1, Math.trunc(opts.concurrency)) && queue.length > 0) {
            const job = queue.shift();
            if (!job) return;
            if (job.signal.aborted) {
                job.reject(abortError());
                inflight.delete(job.url);
                continue;
            }
            active += 1;
            load(job.url, job.signal)
                .then((res) => {
                    cache.set(job.url, res);
                    job.resolve(res);
                })
                .catch((e) => {
                    if (isAbortError(e)) {
                        job.reject(e);
                        return;
                    }
                    cache.set(job.url, null);
                    job.resolve(null);
                })
                .finally(() => {
                    active -= 1;
                    inflight.delete(job.url);
                    pump();
                });
        }
    };

    const get = (url: string, signal: AbortSignal): Promise<ImageNaturalSize | null> => {
        const u = toUrlString(url);
        if (!u) return Promise.resolve(null);
        const cached = cache.get(u);
        if (cached !== undefined) return Promise.resolve(cached);
        const existing = inflight.get(u);
        if (existing) {
            if (signal.aborted) return Promise.reject(abortError());
            return Promise.race([
                existing,
                new Promise<ImageNaturalSize | null>((_, reject) => {
                    signal.addEventListener('abort', () => reject(abortError()), {once: true});
                }),
            ]);
        }

        const p = new Promise<ImageNaturalSize | null>((resolve, reject) => {
            queue.push({url: u, signal, resolve, reject});
            pump();
        });
        inflight.set(u, p);
        return p;
    };

    return {
        get,
        peek: (url: string) => {
            const u = toUrlString(url);
            if (!u) return undefined;
            return cache.get(u);
        },
    };
}

export const sharedImageSizeProbe = createImageSizeProbe({concurrency: 6});

export async function copyText(text: string): Promise<void> {
    const t = text ?? '';
    if (!t) return;
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(t);
        return;
    }
    const ta = document.createElement('textarea');
    ta.value = t;
    ta.style.position = 'fixed';
    ta.style.left = '-10000px';
    ta.style.top = '-10000px';
    document.body.appendChild(ta);
    try {
        ta.select();
        document.execCommand('copy');
    } finally {
        ta.remove();
    }
}
