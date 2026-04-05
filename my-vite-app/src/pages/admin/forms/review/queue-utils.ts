import type {
    ContentType,
    ModerationCaseType,
    ModerationQueueDetail,
    ModerationQueueItem,
} from '../../../../services/moderationQueueService';
import { toInt } from '../../../../utils/numberParsers';

export function formatDateTime(s?: string | null): string {
    if (!s) return '—';
    const d = new Date(s);
    if (Number.isNaN(d.getTime())) return String(s);
    return d.toLocaleString();
}

export function excerptText(text?: string | null, maxChars: number = 240): string {
    const t = String(text ?? '').trim();
    if (!t) return '';
    if (t.length <= maxChars) return t;
    return `${t.substring(0, maxChars)}…`;
}

export function isImageFileName(name?: string | null): boolean {
    const n = String(name ?? '').trim().toLowerCase();
    if (!n) return false;
    const dot = n.lastIndexOf('.');
    if (dot < 0) return false;
    const ext = n.substring(dot + 1);
    return ext === 'png' || ext === 'jpg' || ext === 'jpeg' || ext === 'webp' || ext === 'gif' || ext === 'bmp' || ext === 'tif' || ext === 'tiff';
}

export function toRecord(v: unknown): Record<string, unknown> | null {
    if (typeof v === 'string') {
        const t = v.trim();
        if (!t) return null;
        try {
            const parsed = JSON.parse(t) as unknown;
            if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed as Record<string, unknown>;
        } catch {
        }
        return null;
    }
    if (!v || typeof v !== 'object' || Array.isArray(v)) return null;
    return v as Record<string, unknown>;
}

export function toStringDict(v: unknown): Record<string, string> {
    const out: Record<string, string> = {};
    const o = toRecord(v);
    if (!o) return out;
    for (const [k0, v0] of Object.entries(o)) {
        const k = String(k0).trim();
        const t = v0 == null ? '' : String(v0).trim();
        if (!k || !t) continue;
        out[k] = t;
    }
    return out;
}

export function toStringListDict(v: unknown): Record<string, string[]> {
    const out: Record<string, string[]> = {};
    const o = toRecord(v);
    if (!o) return out;
    for (const [k0, v0] of Object.entries(o)) {
        const k = String(k0).trim();
        if (!k) continue;
        if (!Array.isArray(v0)) continue;
        const list = v0
            .map((x) => (x == null ? '' : String(x).trim()))
            .filter(Boolean);
        if (!list.length) continue;
        out[k] = list;
    }
    return out;
}

export function riskSlugsFromDetail(d: ModerationQueueDetail | null): string[] {
    if (!d) return [];
    if (Array.isArray(d.riskTags) && d.riskTags.length) return d.riskTags;
    if (Array.isArray(d.riskTagItems) && d.riskTagItems.length) {
        const out: string[] = [];
        for (const it of d.riskTagItems) {
            const s = String(it?.slug ?? '').trim();
            if (!s) continue;
            if (!out.includes(s)) out.push(s);
        }
        return out;
    }
    return [];
}

export const DETAIL_CHUNK_COLLAPSE_THRESHOLD = 5;
export const POST_MARKDOWN_PREVIEW_STORAGE_KEY = 'admin.review.queue.postMarkdownPreviewEnabled';

export const statusBadgeClass = (status?: string | null) => {
    switch (status) {
        case 'PENDING':
            return 'bg-yellow-100 text-yellow-800';
        case 'REVIEWING':
            return 'bg-blue-100 text-blue-800';
        // HUMAN is treated as "waiting for human". If it's claimed, we will render a second badge.
        case 'HUMAN':
            return 'bg-indigo-100 text-indigo-800';
        case 'APPROVED':
            return 'bg-green-100 text-green-800';
        case 'REJECTED':
            return 'bg-red-100 text-red-800';
        default:
            return 'bg-gray-100 text-gray-700';
    }
};

export const isTerminal = (s?: string | null) => s === 'APPROVED' || s === 'REJECTED';

export const statusLabel = (status?: string | null, caseType?: ModerationCaseType | null) => {
    switch (status) {
        case 'PENDING':
            return '待自动审核';
        case 'REVIEWING':
            return '审核中';
        case 'HUMAN':
            return '待人工审核';
        case 'APPROVED':
            return caseType === 'REPORT' ? '举报未核实' : '已通过';
        case 'REJECTED':
            return caseType === 'REPORT' ? '举报已核实' : '已驳回';
        default:
            return '—';
    }
};

export const typeLabel = (caseType?: ModerationCaseType | null, contentType?: ContentType | null) => {
    if (caseType === 'REPORT') {
        if (contentType === 'POST') return '举报-帖子';
        if (contentType === 'COMMENT') return '举报-评论';
        if (contentType === 'PROFILE') return '举报-资料';
        return '举报';
    }
    if (contentType === 'POST') return '帖子';
    if (contentType === 'COMMENT') return '评论';
    if (contentType === 'PROFILE') return '资料';
    return '—';
};

export const stageLabel = (stage?: string | null) => {
    switch (stage) {
        case 'RULE':
            return '规则';
        case 'VEC':
            return '向量';
        case 'LLM':
            return '大模型';
        case 'HUMAN':
            return '人工';
        default:
            return '—';
    }
};

export const PAGE_SIZE_OPTIONS = [10, 30, 100, 200, 500] as const;

export function fingerprintQueueList(items: ModerationQueueItem[], totalPages: number, totalElements: number): string {
    const parts: string[] = [`tp:${totalPages}`, `te:${totalElements}`];
    for (const it of items) {
        const cp = it.chunkProgress;
        const tags = it.riskTags;
        const tagsKey = tags && tags.length ? [...tags].sort().join(',') : '';
        parts.push(
            [
                it.id,
                it.caseType,
                it.contentType,
                it.contentId,
                it.status,
                it.currentStage,
                it.priority,
                it.assignedToId ?? '',
                it.createdAt,
                it.updatedAt,
                tagsKey,
                cp?.status ?? '',
                cp?.totalChunks ?? '',
                cp?.completedChunks ?? '',
                cp?.failedChunks ?? '',
                cp?.updatedAt ?? '',
            ].join('|'),
        );
    }
    return parts.join('~');
}
