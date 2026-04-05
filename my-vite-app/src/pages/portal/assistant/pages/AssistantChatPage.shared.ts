import type { AiCitationSource } from '../../../../services/aiChatService';
import type { AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import type { QaMessageDTO } from '../../../../services/qaHistoryService';

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

export type AssistantSessionMessagesState = {
    messages: ChatMsg[];
    sourcesByMsgId: Record<string, AiCitationSource[]>;
};

export type FlatProviderModelOption = {
    providerId: string;
    providerLabel: string;
    model: string;
    value: string;
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

export function flattenProviderModelOptions(providerOptions: AiChatProviderOptionDTO[]): FlatProviderModelOption[] {
    const uniq: FlatProviderModelOption[] = [];
    const seen = new Set<string>();
    for (const provider of providerOptions) {
        const providerId = String(provider.id ?? '').trim();
        if (!providerId) continue;
        const providerName = String(provider.name ?? '').trim();
        const providerLabel = providerName || providerId;
        const rows = Array.isArray(provider.chatModels) ? provider.chatModels.filter(Boolean) : [];
        for (const model of rows) {
            const modelName = String((model as { name?: unknown }).name ?? '').trim();
            if (!modelName) continue;
            const key = `${providerId}::${modelName}`;
            if (seen.has(key)) continue;
            seen.add(key);
            uniq.push({
                providerId,
                providerLabel,
                model: modelName,
                value: buildProviderModelValue(providerId, modelName)
            });
        }
    }
    uniq.sort((a, b) => {
        const pa = `${a.providerLabel} (${a.providerId})`;
        const pb = `${b.providerLabel} (${b.providerId})`;
        const pCmp = pa.localeCompare(pb, 'zh-Hans-CN');
        if (pCmp !== 0) return pCmp;
        return a.model.localeCompare(b.model, 'zh-Hans-CN');
    });
    return uniq;
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

function toChatRole(role: QaMessageDTO['role']): ChatMsg['role'] | null {
    if (role === 'USER') return 'user';
    if (role === 'SYSTEM') return 'system';
    if (role === 'ASSISTANT') return 'assistant';
    return null;
}

export function mapQaMessagesToChatState(list: QaMessageDTO[]): AssistantSessionMessagesState {
    const messages: ChatMsg[] = [];
    const sourcesByMsgId: Record<string, AiCitationSource[]> = {};
    for (const message of list) {
        const role = toChatRole(message.role);
        if (!role) continue;
        messages.push({
            id: String(message.id),
            role,
            content: message.content,
            createdAt: message.createdAt,
            model: message.model ?? null,
            tokensIn: toNullableNumber(message.tokensIn),
            tokensOut: toNullableNumber(message.tokensOut),
            latencyMs: toNullableNumber(message.latencyMs),
            isFavorite: message.isFavorite,
            firstTokenLatencyMs: toNullableNumber(message.firstTokenLatencyMs)
        });
        if (role === 'assistant' && Array.isArray(message.sources) && message.sources.length > 0) {
            sourcesByMsgId[String(message.id)] = message.sources;
        }
    }
    return {messages, sourcesByMsgId};
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
