export const MAX_VISION_IMAGES = 10;
export const MAX_CHAT_FILES = 10;

export type ChatMsg = {
    id: string;
    role: 'user' | 'assistant' | 'system';
    content: string;
    createdAt?: string;
    isFavorite?: boolean;
    model?: string | null;
    tokensIn?: number | null;
    tokensOut?: number | null;
    latencyMs?: number | null;
    firstTokenLatencyMs?: number | null;
};

export type MsgPerf = {
    startAtMs: number;
    firstDeltaAtMs?: number;
    doneAtMs?: number;
    backendLatencyMs?: number;
};

export type ThinkPerf = {
    startedAtMs?: number;
    endedAtMs?: number;
};

export type ThinkUi = {
    collapsed: boolean;
};

export type BranchInfo = {
    id: string;
    label: string;
};

export type BranchAnchorState = {
    activeBranchId: string;
    branches: BranchInfo[];
};

export type BranchMembership = {
    anchorId: string;
    branchId: string;
};

export function uid(): string {
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function buildProviderModelValue(providerId: string, model: string): string {
    const p = String(providerId ?? '').trim();
    const m = String(model ?? '').trim();
    if (!p || !m) return '';
    return `${encodeURIComponent(p)}|${encodeURIComponent(m)}`;
}

export function parseProviderModelValue(value: string): { providerId: string; model: string } | null {
    const v = String(value ?? '').trim();
    if (!v) return null;
    const idx = v.indexOf('|');
    if (idx <= 0) return null;
    const p = v.slice(0, idx);
    const m = v.slice(idx + 1);
    try {
        const providerId = decodeURIComponent(p).trim();
        const model = decodeURIComponent(m).trim();
        if (!providerId || !model) return null;
        return {providerId, model};
    } catch {
        return null;
    }
}

export function isPersistedId(id: string): boolean {
    return /^\d+$/.test(id);
}

export function formatDateTime(iso?: string): string | null {
    if (!iso) return null;
    const d = new Date(iso);
    if (!Number.isFinite(d.getTime())) return null;
    return d.toLocaleString();
}

export function formatMsgTokensInfo(msg: Pick<ChatMsg, 'role' | 'tokensIn' | 'tokensOut'>): string {
    if (msg.role === 'user') {
        const inStr = typeof msg.tokensIn === 'number' ? String(msg.tokensIn) : '-';
        return `tokens in ${inStr}`;
    }
    const outStr = typeof msg.tokensOut === 'number' ? String(msg.tokensOut) : '-';
    return `tokens out ${outStr}`;
}

export function toNullableNumber(v: unknown): number | null {
    if (typeof v === 'number') return Number.isFinite(v) ? v : null;
    if (typeof v === 'string') {
        const s = v.trim();
        if (!s) return null;
        const n = Number(s);
        return Number.isFinite(n) ? n : null;
    }
    return null;
}

export function colorClassForCitationIndex(index: number): string {
    const palette = [
        'text-blue-600',
        'text-emerald-600',
        'text-purple-600',
        'text-amber-600',
        'text-rose-600',
        'text-cyan-600'
    ];
    const i = Number.isFinite(index) ? Math.abs(index) : 0;
    return palette[i % palette.length] ?? 'text-blue-600';
}

export function linkifyCitations(md: string, anchorPrefix = 'cite-'): string {
    if (!md) return md;
    const parts: string[] = [];
    const re = /```[\s\S]*?```/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = re.exec(md)) !== null) {
        const before = md.slice(lastIndex, match.index);
        parts.push(before.replace(/\[(\d{1,3})](?!\()/g, (_m, n) => `[[${n}]](#${anchorPrefix}${n})`));
        parts.push(match[0]);
        lastIndex = match.index + match[0].length;
    }
    const tail = md.slice(lastIndex);
    parts.push(tail.replace(/\[(\d{1,3})](?!\()/g, (_m, n) => `[[${n}]](#${anchorPrefix}${n})`));
    return parts.join('');
}

export function extractCitationIndexes(md: string): Set<number> {
    const out = new Set<number>();
    if (!md) return out;
    const reCode = /```[\s\S]*?```/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    const extractFromText = (txt: string) => {
        for (const m of txt.matchAll(/\[(\d{1,3})](?!\()/g)) {
            const n = Number(m[1]);
            if (Number.isFinite(n) && n > 0) out.add(n);
        }
    };
    while ((match = reCode.exec(md)) !== null) {
        extractFromText(md.slice(lastIndex, match.index));
        lastIndex = match.index + match[0].length;
    }
    extractFromText(md.slice(lastIndex));
    return out;
}

export function isThinkingOnlyModel(model: string): boolean {
    const m = String(model ?? '').trim().toLowerCase();
    if (!m) return false;
    return m.includes('-thinking') || m.includes('thinking-') || m.endsWith('thinking');
}

export function formatDurationMs(ms: number): string {
    const safe = Number.isFinite(ms) ? Math.max(0, ms) : 0;
    const totalSeconds = safe / 1000;
    if (totalSeconds < 10) return `${totalSeconds.toFixed(1)}s`;
    if (totalSeconds < 60) return `${Math.round(totalSeconds)}s`;
    const mins = Math.floor(totalSeconds / 60);
    const secs = Math.round(totalSeconds - mins * 60);
    return `${mins}m${secs}s`;
}
