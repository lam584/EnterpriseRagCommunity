// queue.tsx
import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {
    adminApproveModerationQueue,
    adminBackfillModerationQueue,
    adminBanModerationQueueUser,
    adminGetModerationQueueDetail,
    adminGetModerationQueueChunkProgress,
    adminGetModerationQueueRiskTags,
    adminSetModerationQueueRiskTags,
    adminListModerationQueue,
    adminRejectModerationQueue,
    adminOverrideApproveModerationQueue,
    adminOverrideRejectModerationQueue,
    adminClaimModerationQueue,
    adminReleaseModerationQueue,
    adminRequeueModerationQueue,
    adminToHumanModerationQueue,
    adminBatchRequeueModerationQueue,
    type ContentType,
    type ModerationCaseType,
    type ModerationQueueBackfillResponse,
    type ModerationQueueDetail,
    type ModerationChunkProgress,
    type ModerationQueueItem,
    type QueueStatus,
} from '../../../../services/moderationQueueService';
import { getCurrentAdmin } from '../../../../services/authService';
import { ModerationPipelineTracePanel } from '../../../../components/admin/ModerationPipelineTracePanel';
import { createRiskTag, listRiskTagsPage, type RiskTagDTO } from '../../../../services/riskTagService';
import { getPostAiSummary, type PostAiSummaryDTO } from '../../../../services/aiPostSummaryService';
import { getPost } from '../../../../services/postService';
import { adminListPostSummaryHistory, type PostSummaryGenHistoryDTO } from '../../../../services/postSummaryAdminService';
import { getModerationChunkReviewConfig, type ModerationChunkReviewConfig } from '../../../../services/moderationChunkReviewConfigService';
import { adminGetModerationReviewTraceTaskDetail, type ModerationReviewTraceTaskDetail } from '../../../../services/moderationReviewTraceService';
import EvidenceListView from '../../../../components/admin/EvidenceListView';
import { countUniqueEvidence, extractEvidenceFromDetails, shouldSkipStepEvidenceForChunkedReview } from '../../../../utils/evidence-utils';
import DetailDialog from '../../../../components/common/DetailDialog';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import { resolveAssetUrl } from '../../../../utils/urlUtils';
import { buildEvidenceImageUrlMap, extractLatestRunImageUrls } from '../../../../utils/evidenceImageMap';

function formatDateTime(s?: string | null): string {
    if (!s) return '—';
    const d = new Date(s);
    if (Number.isNaN(d.getTime())) return String(s);
    return d.toLocaleString();
}

function excerptText(text?: string | null, maxChars: number = 240): string {
    const t = String(text ?? '').trim();
    if (!t) return '';
    if (t.length <= maxChars) return t;
    return `${t.substring(0, maxChars)}…`;
}

function isImageFileName(name?: string | null): boolean {
    const n = String(name ?? '').trim().toLowerCase();
    if (!n) return false;
    const dot = n.lastIndexOf('.');
    if (dot < 0) return false;
    const ext = n.substring(dot + 1);
    return ext === 'png' || ext === 'jpg' || ext === 'jpeg' || ext === 'webp' || ext === 'gif' || ext === 'bmp' || ext === 'tif' || ext === 'tiff';
}

function toRecord(v: unknown): Record<string, unknown> | null {
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

function toInt(v: unknown): number | null {
    if (typeof v === 'number' && Number.isFinite(v)) return Math.floor(v);
    if (typeof v === 'string') {
        const t = v.trim();
        if (!t) return null;
        const n = Number(t);
        if (Number.isFinite(n)) return Math.floor(n);
    }
    return null;
}

function toStringDict(v: unknown): Record<string, string> {
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

function toStringListDict(v: unknown): Record<string, string[]> {
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

function riskSlugsFromDetail(d: ModerationQueueDetail | null): string[] {
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

const DETAIL_CHUNK_COLLAPSE_THRESHOLD = 5;
const POST_MARKDOWN_PREVIEW_STORAGE_KEY = 'admin.review.queue.postMarkdownPreviewEnabled';

const statusBadgeClass = (status?: string | null) => {
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

const isTerminal = (s?: string | null) => s === 'APPROVED' || s === 'REJECTED';

const statusLabel = (status?: string | null, caseType?: ModerationCaseType | null) => {
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

const typeLabel = (caseType?: ModerationCaseType | null, contentType?: ContentType | null) => {
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

const stageLabel = (stage?: string | null) => {
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

const PAGE_SIZE_OPTIONS = [10, 30, 100, 200, 500] as const;

function fingerprintQueueList(items: ModerationQueueItem[], totalPages: number, totalElements: number): string {
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

const QueueForm: React.FC = () => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // 补齐历史待审
    const [backfillLoading, setBackfillLoading] = useState(false);
    const [backfillResult, setBackfillResult] = useState<ModerationQueueBackfillResponse | null>(null);

    const [taskId, setTaskId] = useState<string>(() => searchParams.get('taskId') ?? '');
    const [contentType, setContentType] = useState<ContentType | ''>(() => (searchParams.get('contentType') as ContentType) || '');
    // status can be empty => all statuses
    const [status, setStatus] = useState<QueueStatus | ''>(() => (searchParams.get('status') as QueueStatus) || '');
    const [orderBy, setOrderBy] = useState<string>(() => searchParams.get('orderBy') ?? 'createdAt');
    const [sortDir, setSortDir] = useState<'asc' | 'desc'>(() => (searchParams.get('sort') === 'asc' ? 'asc' : 'desc'));

    const [page, setPage] = useState(() => {
        const n = Number(searchParams.get('page') ?? '1');
        return Number.isFinite(n) && n > 0 ? n : 1;
    });
    const [pageSize, setPageSize] = useState(() => {
        const n = Number(searchParams.get('pageSize') ?? '10');
        if (!Number.isFinite(n)) return 10;
        return (PAGE_SIZE_OPTIONS as readonly number[]).includes(n) ? n : 10;
    });

    const [items, setItems] = useState<ModerationQueueItem[]>([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);

    const [detailOpen, setDetailOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detail, setDetail] = useState<ModerationQueueDetail | null>(null);
    const [detailTab, setDetailTab] = useState<'overview' | 'chunks' | 'content'>('overview');
    const [postMarkdownPreviewEnabled, setPostMarkdownPreviewEnabled] = useState(() => {
        try {
            const raw = String(localStorage.getItem(POST_MARKDOWN_PREVIEW_STORAGE_KEY) ?? '').trim().toLowerCase();
            if (raw === '1' || raw === 'true') return true;
            if (raw === '0' || raw === 'false') return false;
        } catch {
        }
        return true;
    });
    const [detailTraceLoading, setDetailTraceLoading] = useState(false);
    const [detailTraceError, setDetailTraceError] = useState<string | null>(null);
    const [detailTrace, setDetailTrace] = useState<ModerationReviewTraceTaskDetail | null>(null);
    const [detailChunkIndexFilter, setDetailChunkIndexFilter] = useState('');

    const [postSummaryLoading, setPostSummaryLoading] = useState(false);
    const [postSummaryError, setPostSummaryError] = useState<string | null>(null);
    const [postSummary, setPostSummary] = useState<PostAiSummaryDTO | null>(null);
    const [relatedPostLite, setRelatedPostLite] = useState<{ id: number; title?: string | null; content?: string | null } | null>(null);
    const [postSummaryLatestHistory, setPostSummaryLatestHistory] = useState<PostSummaryGenHistoryDTO | null>(null);
    const [postSummaryErrorOpen, setPostSummaryErrorOpen] = useState(false);

    const [riskEditorOpen, setRiskEditorOpen] = useState(false);
    const [riskEditorLoading, setRiskEditorLoading] = useState(false);
    const [riskEditorSaving, setRiskEditorSaving] = useState(false);
    const [riskEditorError, setRiskEditorError] = useState<string | null>(null);
    const [riskQuery, setRiskQuery] = useState('');
    const [riskOptions, setRiskOptions] = useState<RiskTagDTO[]>([]);
    const [riskSelected, setRiskSelected] = useState<string[]>([]);
    const [riskNewName, setRiskNewName] = useState('');

    const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

    const [chunkCfg, setChunkCfg] = useState<ModerationChunkReviewConfig | null>(null);
    const [chunkCfgError, setChunkCfgError] = useState<string | null>(null);

    const [detailChunkProgressLoading, setDetailChunkProgressLoading] = useState(false);
    const [detailChunkProgress, setDetailChunkProgress] = useState<ModerationChunkProgress | null>(null);
    const [detailChunksExpanded, setDetailChunksExpanded] = useState(false);

    const [chunkErrorOpen, setChunkErrorOpen] = useState(false);
    const [chunkErrorTitle, setChunkErrorTitle] = useState<string>('');
    const [chunkErrorText, setChunkErrorText] = useState<string>('');
    const [chunkErrorLoadingQueueId, setChunkErrorLoadingQueueId] = useState<number | null>(null);

    const imageAttachmentFileAssetIds = useMemo(() => {
        const set = new Set<number>();
        const atts = detail?.contentType === 'POST' ? detail?.post?.attachments : null;
        if (!atts || !atts.length) return set;
        for (const a of atts) {
            if (!a || a.fileAssetId == null) continue;
            const mt = String(a.mimeType ?? '').trim().toLowerCase();
            if (mt.startsWith('image/')) set.add(a.fileAssetId);
        }
        return set;
    }, [detail]);

    const imageChunkSummary = useMemo(() => {
        const chunks = detailChunkProgress?.chunks;
        if (!chunks || !chunks.length) return null;
        let total = 0;
        let completed = 0;
        let failed = 0;
        let running = 0;
        for (const c of chunks) {
            if (!c || c.sourceType !== 'FILE_TEXT') continue;
            const byMime = c.fileAssetId != null && imageAttachmentFileAssetIds.has(c.fileAssetId);
            const byExt = !byMime && isImageFileName(c.fileName);
            if (!byMime && !byExt) continue;
            total += 1;
            const st = String(c.status ?? '').trim().toUpperCase();
            if (st === 'SUCCESS') completed += 1;
            else if (st === 'FAILED') failed += 1;
            else if (st === 'RUNNING') running += 1;
        }
        if (total <= 0) return null;
        return { total, completed, failed, running };
    }, [detailChunkProgress, imageAttachmentFileAssetIds]);

    const detailChunksView = useMemo(() => {
        const chunks = detailChunkProgress?.chunks;
        if (!chunks || !chunks.length) return [];
        const score = (c: (typeof chunks)[number]) => {
            const st = String(c?.status ?? '').trim().toUpperCase();
            if (st === 'FAILED') return 0;
            if (st === 'RUNNING') return 1;
            if (st === 'PENDING') return 2;
            return 3;
        };
        return [...chunks].sort((a, b) => score(a) - score(b)).slice(0, 120);
    }, [detailChunkProgress]);

    const detailChunkOutput = useMemo(() => {
        const mem = toRecord(detailTrace?.chunkSet?.memoryJson ?? null);
        const summaries = toStringDict(mem?.summaries);
        const llmEvidenceByChunk = toStringListDict(mem?.llmEvidenceByChunk);

        const filter = detailChunkIndexFilter.trim();
        const filterIdx = filter ? toInt(filter) : null;
        return { summaries, llmEvidenceByChunk, filterIdx };
    }, [detailTrace?.chunkSet?.memoryJson, detailChunkIndexFilter]);

    const detailChunkIdByChunkIndex = useMemo(() => {
        const map: Record<string, number> = {};
        const chunks = detailChunkProgress?.chunks ?? [];
        for (const c of chunks) {
            if (!c) continue;
            const idx = toInt((c as { chunkIndex?: unknown }).chunkIndex);
            const id = toInt((c as { chunkId?: unknown; id?: unknown }).chunkId ?? (c as { id?: unknown }).id);
            if (idx == null || id == null) continue;
            map[String(idx)] = id;
        }
        return map;
    }, [detailChunkProgress?.chunks]);

    const detailStepEvidenceGroups = useMemo(() => {
        const latestRun = detailTrace?.latestRun as Record<string, unknown> | null;
        if (!latestRun) return [];
        const steps = latestRun.steps;
        if (!Array.isArray(steps)) return [];
        const chunkIndexByChunkId: Record<string, number> = {};
        if (detailTrace?.chunkSet) {
            for (const [k, v] of Object.entries(detailChunkIdByChunkIndex)) {
                const ci = toInt(k);
                if (ci == null) continue;
                chunkIndexByChunkId[String(v)] = ci;
            }
        }
        const out: Array<{ key: string; title: string; evidence: unknown[]; order: number; stage: string }> = [];
        for (const s of steps) {
            const step = toRecord(s);
            if (!step) continue;
            const details = toRecord(step.details ?? null);
            const stage = typeof step.stage === 'string' && step.stage.trim() ? step.stage.trim() : 'STEP';
            const stepIdNum = toInt(step.id);
            const ci = stepIdNum == null ? undefined : chunkIndexByChunkId[String(stepIdNum)];
            if (shouldSkipStepEvidenceForChunkedReview({ stage, details, hasChunkSet: Boolean(detailTrace?.chunkSet), chunkIndex: ci })) continue;
            const evidence = extractEvidenceFromDetails(details);
            if (!evidence.length) continue;
            const order = toInt(step.stepOrder) ?? 0;
            const id = step.id == null ? '' : String(step.id);
            const decision = step.decision == null ? '' : String(step.decision);
            const costMs = toInt(step.costMs);
            const title = `${stage}${step.stepOrder == null ? '' : `#${order}`}${id ? ` · id=${id}` : ''}${decision ? ` · ${decision}` : ''}${costMs == null ? '' : ` · ${costMs}ms`}`;
            out.push({ key: `${stage}-${order}-${id}-${decision}`, title, evidence, order, stage });
        }
        out.sort((a, b) => a.order - b.order);
        return out;
    }, [detailChunkIdByChunkIndex, detailTrace?.chunkSet, detailTrace?.latestRun]);

    const detailStepEvidenceCount = useMemo(() => {
        let n = 0;
        for (const g of detailStepEvidenceGroups) n += g.evidence.length;
        return n;
    }, [detailStepEvidenceGroups]);

    const detailUniqueEvidenceCount = useMemo(() => {
        const all: unknown[] = [];
        for (const g of detailStepEvidenceGroups) {
            for (const ev of g.evidence) all.push(ev);
        }
        if (detailTrace?.chunkSet) {
            for (const [k, v] of Object.entries(detailChunkOutput.llmEvidenceByChunk)) {
                const ci = toInt(k);
                if (detailChunkOutput.filterIdx != null && ci != null && ci !== detailChunkOutput.filterIdx) continue;
                for (const ev of v) all.push(ev);
            }
        }
        return countUniqueEvidence(all);
    }, [detailChunkOutput.filterIdx, detailChunkOutput.llmEvidenceByChunk, detailStepEvidenceGroups, detailTrace?.chunkSet]);

    const detailEvidenceImageUrlById = useMemo(() => {
        const traceQueue = toRecord(detailTrace?.queue ?? null);
        const tracePost = toRecord(traceQueue?.post ?? null);
        const traceAttachments = Array.isArray(tracePost?.attachments)
            ? tracePost.attachments
            : Array.isArray(traceQueue?.attachments)
                ? traceQueue.attachments
                : Array.isArray(traceQueue?.postAttachments)
                    ? traceQueue.postAttachments
                    : null;
        return buildEvidenceImageUrlMap({
            attachments: traceAttachments ?? detail?.post?.attachments,
            extraImageUrls: [
                ...extractLatestRunImageUrls(detailTrace?.latestRun),
                ...(detail?.contentType === 'PROFILE'
                    ? [detail.profile?.pendingAvatarUrl, detail.profile?.publicAvatarUrl]
                    : []),
            ],
        });
    }, [detail, detailTrace?.latestRun, detailTrace?.queue]);

    useEffect(() => {
        setDetailChunksExpanded(false);
    }, [detail?.id]);

    useEffect(() => {
        try {
            localStorage.setItem(POST_MARKDOWN_PREVIEW_STORAGE_KEY, postMarkdownPreviewEnabled ? '1' : '0');
        } catch {
        }
    }, [postMarkdownPreviewEnabled]);

    const openChunkErrorsForQueue = useCallback(async (queueId: number) => {
        if (!queueId) return;
        setChunkErrorLoadingQueueId(queueId);
        try {
            const p = await adminGetModerationQueueChunkProgress(queueId, { includeChunks: true, limit: 300 });
            const failed = (p.chunks ?? []).filter((c) => String(c?.status ?? '').trim().toUpperCase() === 'FAILED');
            const lines: string[] = [];
            for (const c of failed) {
                const src =
                    c.sourceType === 'FILE_TEXT'
                        ? `文件：${c.fileName || c.fileAssetId || '—'}`
                        : c.sourceType === 'POST_TEXT'
                            ? '正文'
                            : c.sourceType || '—';
                const idx = c.chunkIndex ?? '—';
                const attempts = c.attempts ?? '—';
                const err = (c.lastError || '').trim();
                if (!err) continue;
                lines.push(`[${src}] 分片 ${idx} · attempts=${attempts}\n${err}`);
            }
            setChunkErrorTitle(`分片错误（任务 ${queueId}）`);
            setChunkErrorText(lines.length ? lines.join('\n\n') : '未找到 FAILED 分片的错误信息（lastError 为空或暂无失败分片）。');
            setChunkErrorOpen(true);
        } catch (e) {
            setChunkErrorTitle(`分片错误（任务 ${queueId}）`);
            setChunkErrorText(e instanceof Error ? e.message : String(e));
            setChunkErrorOpen(true);
        } finally {
            setChunkErrorLoadingQueueId(null);
        }
    }, []);

    const [selectedMap, setSelectedMap] = useState<Record<number, boolean>>({});
    const selectedIds = useMemo(() => {
        const out: number[] = [];
        for (const [k, v] of Object.entries(selectedMap)) {
            if (!v) continue;
            const n = Number(k);
            if (Number.isFinite(n) && n > 0) out.push(n);
        }
        out.sort((a, b) => a - b);
        return out;
    }, [selectedMap]);
    const [batchReason, setBatchReason] = useState('');

    const [myUserId, setMyUserId] = useState<number | undefined>(undefined);
    const [onlyMine] = useState(() => searchParams.get('onlyMine') === '1');

    const loadSeqRef = useRef(0);
    const mountedRef = useRef(true);
    const suppressChunkProgressUntilRef = useRef<Record<number, number>>({});
    const loadBusyRef = useRef(false);
    const listFingerprintRef = useRef<string>('');

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
            loadSeqRef.current += 1;
        };
    }, []);

    useEffect(() => {
        listFingerprintRef.current = fingerprintQueueList(items, totalPages, totalElements);
    }, [items, totalPages, totalElements]);

    useEffect(() => {
        let mounted = true;
        setChunkCfgError(null);
        getModerationChunkReviewConfig()
            .then((c) => {
                if (!mounted) return;
                setChunkCfg(c);
            })
            .catch((e) => {
                if (!mounted) return;
                setChunkCfg(null);
                setChunkCfgError(e instanceof Error ? e.message : String(e));
            });
        return () => {
            mounted = false;
        };
    }, []);

    const parsedTaskId = useMemo(() => {
        const n = Number(taskId);
        if (!taskId.trim()) return undefined;
        return Number.isFinite(n) && n > 0 ? n : undefined;
    }, [taskId]);

    const clearSuppressedChunkProgress = useCallback((id: number) => {
        const suppressed = suppressChunkProgressUntilRef.current;
        if (!suppressed[id]) return;
        const nextSuppressed = { ...suppressed };
        delete nextSuppressed[id];
        suppressChunkProgressUntilRef.current = nextSuppressed;
    }, []);

    const upsertQueueSnapshot = useCallback((snapshot: {
        id: number;
        status?: QueueStatus;
        currentStage?: ModerationQueueItem['currentStage'];
        assignedToId?: number | null;
        updatedAt?: string;
        chunkProgress?: ModerationQueueItem['chunkProgress'];
    }) => {
        setItems((prev) =>
            prev.map((it) => {
                if (it.id !== snapshot.id) return it;
                return {
                    ...it,
                    ...(snapshot.status ? { status: snapshot.status } : {}),
                    ...(snapshot.currentStage ? { currentStage: snapshot.currentStage } : {}),
                    ...(snapshot.assignedToId !== undefined ? { assignedToId: snapshot.assignedToId } : {}),
                    ...(snapshot.updatedAt ? { updatedAt: snapshot.updatedAt } : {}),
                    ...(snapshot.chunkProgress !== undefined ? { chunkProgress: snapshot.chunkProgress } : {}),
                };
            })
        );
        setDetail((prev) => {
            if (!prev || prev.id !== snapshot.id) return prev;
            return {
                ...prev,
                ...(snapshot.status ? { status: snapshot.status } : {}),
                ...(snapshot.currentStage ? { currentStage: snapshot.currentStage } : {}),
                ...(snapshot.assignedToId !== undefined ? { assignedToId: snapshot.assignedToId } : {}),
                ...(snapshot.updatedAt ? { updatedAt: snapshot.updatedAt } : {}),
                ...(snapshot.chunkProgress !== undefined ? { chunkProgress: snapshot.chunkProgress } : {}),
            };
        });
    }, []);

    const detailPostId = useMemo(() => {
        if (!detail) return null;
        if (detail.contentType === 'POST') return detail.contentId ?? null;
        if (detail.contentType === 'COMMENT') return detail.comment?.postId ?? detail.summary?.postId ?? null;
        return null;
    }, [detail]);

    useEffect(() => {
        if (!detailOpen || !detailPostId || !detail) {
            setPostSummary(null);
            setPostSummaryError(null);
            setPostSummaryLoading(false);
            setRelatedPostLite(null);
            setPostSummaryLatestHistory(null);
            return;
        }

        const d = detail;
        let mounted = true;

        setPostSummaryLoading(true);
        setPostSummaryError(null);
        setPostSummary(null);
        setPostSummaryLatestHistory(null);
        getPostAiSummary(detailPostId)
            .then((s) => {
                if (!mounted) return;
                setPostSummary(s);
            })
            .catch((e) => {
                if (!mounted) return;
                setPostSummaryError(e instanceof Error ? e.message : String(e));
                setPostSummary(null);
            })
            .finally(() => {
                if (!mounted) return;
                setPostSummaryLoading(false);
            });

        adminListPostSummaryHistory({ page: 0, size: 1, postId: detailPostId })
            .then((p) => {
                if (!mounted) return;
                const first = (p.content ?? [])[0] ?? null;
                setPostSummaryLatestHistory(first);
            })
            .catch(() => {
                if (!mounted) return;
                setPostSummaryLatestHistory(null);
            });

        if (d.contentType === 'POST' && d.post) {
            setRelatedPostLite({ id: d.post.id, title: d.post.title, content: d.post.content });
        } else {
            setRelatedPostLite(null);
            getPost(detailPostId)
                .then((p) => {
                    if (!mounted) return;
                    setRelatedPostLite({ id: p.id, title: p.title, content: p.content });
                })
                .catch(() => {
                    if (!mounted) return;
                    setRelatedPostLite({ id: detailPostId, title: null, content: null });
                });
        }

        return () => {
            mounted = false;
        };
    }, [detailOpen, detailPostId, detail]);

    const load = useCallback(async (opts?: { silent?: boolean }) => {
        const silent = !!opts?.silent;
        const seq = ++loadSeqRef.current;
        loadBusyRef.current = true;
        if (!silent) setLoading(true);
        if (!silent) setError(null);
        try {
            const res = await adminListModerationQueue({
                page,
                pageSize,
                orderBy,
                sort: sortDir,
                id: parsedTaskId,
                contentType: contentType || undefined,
                status: status || undefined,
                assignedToId: status === 'HUMAN' && onlyMine ? myUserId : undefined,
            });
            if (!mountedRef.current || seq !== loadSeqRef.current) return;

            const suppressed = suppressChunkProgressUntilRef.current;
            const toClearSuppressed: number[] = [];
            const nextItems = (res.content ?? []).map((it) => {
                const suppressAt = suppressed[it.id];
                if (!suppressAt) return it;
                const rawUpdatedAt = it.chunkProgress?.updatedAt;
                const progressUpdatedAt = rawUpdatedAt ? Date.parse(String(rawUpdatedAt)) : Number.NaN;
                if (Number.isFinite(progressUpdatedAt) && progressUpdatedAt >= suppressAt) {
                    toClearSuppressed.push(it.id);
                    return it;
                }
                return { ...it, chunkProgress: null };
            });
            if (toClearSuppressed.length) {
                const nextSuppressed = { ...suppressed };
                for (const id of toClearSuppressed) delete nextSuppressed[id];
                suppressChunkProgressUntilRef.current = nextSuppressed;
            }

            const nextTotalPages = res.totalPages ?? 1;
            const nextTotalElements = res.totalElements ?? 0;
            const nextFingerprint = fingerprintQueueList(nextItems, nextTotalPages, nextTotalElements);
            const changed = nextFingerprint !== listFingerprintRef.current;
            if (changed) {
                listFingerprintRef.current = nextFingerprint;
                setItems(nextItems);
                setTotalPages(nextTotalPages);
                setTotalElements(nextTotalElements);
                setError(null);
            } else if (!silent) {
                setTotalPages(nextTotalPages);
                setTotalElements(nextTotalElements);
            }
        } catch (e) {
            if (!mountedRef.current || seq !== loadSeqRef.current) return;
            if (!silent) setError(e instanceof Error ? e.message : String(e));
        } finally {
            if (mountedRef.current && seq === loadSeqRef.current) {
                loadBusyRef.current = false;
                if (!silent) setLoading(false);
            }
        }
    }, [page, pageSize, orderBy, sortDir, parsedTaskId, contentType, status, onlyMine, myUserId]);

    useEffect(() => {
        setSearchParams((prev) => {
            const sp = new URLSearchParams(prev);

            const trimmedTaskId = taskId.trim();
            if (trimmedTaskId) sp.set('taskId', trimmedTaskId);
            else sp.delete('taskId');

            if (contentType) sp.set('contentType', contentType);
            else sp.delete('contentType');

            if (status) sp.set('status', status);
            else sp.delete('status');

            if (orderBy && orderBy !== 'createdAt') sp.set('orderBy', orderBy);
            else sp.delete('orderBy');

            if (sortDir !== 'desc') sp.set('sort', sortDir);
            else sp.delete('sort');

            if (onlyMine) sp.set('onlyMine', '1');
            else sp.delete('onlyMine');

            if (page !== 1) sp.set('page', String(page));
            else sp.delete('page');

            if (pageSize !== 10) sp.set('pageSize', String(pageSize));
            else sp.delete('pageSize');

            if (sp.toString() === prev.toString()) return prev;
            return sp;
        }, { replace: true });
    }, [taskId, contentType, status, orderBy, sortDir, onlyMine, page, pageSize, setSearchParams]);

    const backfill = useCallback(async () => {
        const ok = window.confirm('将从数据库补齐历史遗漏的待审核帖子/评论（只会入队 PENDING 且未删除的内容）。确认执行？');
        if (!ok) return;

        setBackfillLoading(true);
        setError(null);
        setBackfillResult(null);
        try {
            const res = await adminBackfillModerationQueue({
                contentTypes: ['POST', 'COMMENT'],
                limit: 500,
            });
            setBackfillResult(res);
            await load();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setBackfillLoading(false);
        }
    }, [load]);

    useEffect(() => {
        // 默认自动刷新 PENDING
        load();
    }, [load]);

    useEffect(() => {
        if (!chunkCfg || !chunkCfg.queueAutoRefreshEnabled) return;
        const ms = Math.max(1000, Math.min(60_000, Number(chunkCfg.queuePollIntervalMs) || 5000));
        const t = window.setInterval(() => {
            if (!mountedRef.current) return;
            if (detailOpen) return;
            if (loadBusyRef.current) return;
            void load({ silent: true });
        }, ms);
        return () => window.clearInterval(t);
    }, [chunkCfg, detailOpen, load]);

    useEffect(() => {
        // best-effort
        getCurrentAdmin()
            .then((u: unknown) => {
                const obj = (u && typeof u === 'object') ? (u as Record<string, unknown>) : null;
                const rawId = obj ? obj['id'] : undefined;
                const id = typeof rawId === 'number' ? rawId : Number(rawId);
                if (Number.isFinite(id) && id > 0) setMyUserId(id);
            })
            .catch(() => {
            });
    }, []);

    const openDetail = useCallback(async (id: number) => {
        setDetailOpen(true);
        setDetailTab('overview');
        setDetailLoading(true);
        setDetail(null);
        setDetailChunkProgress(null);
        setDetailTrace(null);
        setDetailTraceError(null);
        setDetailTraceLoading(false);
        setDetailChunkIndexFilter('');
        try {
            const d = await adminGetModerationQueueDetail(id);
            setDetail(d);
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setDetailLoading(false);
        }
    }, []);

    const closeDetail = useCallback(() => {
        setDetailOpen(false);
        setDetailTab('overview');
    }, []);

    useEffect(() => {
        if (!detailOpen || !detail?.id) {
            setDetailChunkProgress(null);
            setDetailChunkProgressLoading(false);
            return;
        }
        let mounted = true;
        setDetailChunkProgressLoading(true);
        adminGetModerationQueueChunkProgress(detail.id, { includeChunks: true, limit: 120 })
            .then((p) => {
                if (!mounted) return;
                setDetailChunkProgress(p);
            })
            .catch(() => {
                if (!mounted) return;
                setDetailChunkProgress(null);
            })
            .finally(() => {
                if (!mounted) return;
                setDetailChunkProgressLoading(false);
            });
        return () => {
            mounted = false;
        };
    }, [detailOpen, detail?.id]);

    useEffect(() => {
        if (!detailOpen || !detail?.id) {
            setDetailTrace(null);
            setDetailTraceError(null);
            setDetailTraceLoading(false);
            setDetailChunkIndexFilter('');
            return;
        }
        let mounted = true;
        const queueId = detail.id;

        const hasRisk = (detail.riskTagItems && detail.riskTagItems.length) || (detail.riskTags && detail.riskTags.length);
        if (!hasRisk) {
            adminGetModerationQueueRiskTags(queueId)
                .then((tags) => {
                    if (!mounted) return;
                    if (!tags || tags.length === 0) return;
                    setDetail((prev) => (prev && prev.id === queueId ? { ...prev, riskTags: tags } : prev));
                })
                .catch(() => {
                });
        }

        setDetailTraceLoading(true);
        setDetailTraceError(null);
        setDetailTrace(null);
        adminGetModerationReviewTraceTaskDetail(queueId)
            .then((d) => {
                if (!mounted) return;
                setDetailTrace(d);
            })
            .catch((e) => {
                if (!mounted) return;
                setDetailTrace(null);
                setDetailTraceError(e instanceof Error ? e.message : String(e));
            })
            .finally(() => {
                if (!mounted) return;
                setDetailTraceLoading(false);
            });
        return () => {
            mounted = false;
        };
    }, [detailOpen, detail?.id]);

    useEffect(() => {
        if (!riskEditorOpen) return;
        const t = window.setTimeout(() => {
            (async () => {
                setRiskEditorLoading(true);
                setRiskEditorError(null);
                try {
                    const res = await listRiskTagsPage({ page: 1, pageSize: 30, keyword: riskQuery.trim() ? riskQuery.trim() : undefined });
                    setRiskOptions(res.content ?? []);
                } catch (e) {
                    setRiskEditorError(e instanceof Error ? e.message : String(e));
                } finally {
                    setRiskEditorLoading(false);
                }
            })();
        }, 200);
        return () => window.clearTimeout(t);
    }, [riskEditorOpen, riskQuery]);

    const openRiskEditor = useCallback(() => {
        if (!detail) return;
        setRiskEditorOpen(true);
        setRiskEditorError(null);
        setRiskQuery('');
        setRiskOptions([]);
        setRiskSelected(riskSlugsFromDetail(detail));
        setRiskNewName('');
    }, [detail]);

    const closeRiskEditor = useCallback(() => {
        setRiskEditorOpen(false);
        setRiskEditorError(null);
        setRiskQuery('');
        setRiskOptions([]);
        setRiskSelected([]);
        setRiskNewName('');
    }, []);

    const addRiskSlug = useCallback((slug: string) => {
        const s = (slug ?? '').trim();
        if (!s) return;
        setRiskSelected((prev) => (prev.includes(s) ? prev : [...prev, s]));
    }, []);

    const removeRiskSlug = useCallback((slug: string) => {
        setRiskSelected((prev) => prev.filter((x) => x !== slug));
    }, []);

    const createAndSelectRiskTag = useCallback(async () => {
        if (!riskNewName.trim()) return;
        setRiskEditorSaving(true);
        setRiskEditorError(null);
        try {
            const created = await createRiskTag({ tenantId: 1, name: riskNewName.trim(), active: true });
            addRiskSlug(created.slug);
            setRiskNewName('');
            const res = await listRiskTagsPage({ page: 1, pageSize: 30, keyword: riskQuery.trim() ? riskQuery.trim() : undefined });
            setRiskOptions(res.content ?? []);
        } catch (e) {
            setRiskEditorError(e instanceof Error ? e.message : String(e));
        } finally {
            setRiskEditorSaving(false);
        }
    }, [addRiskSlug, riskNewName, riskQuery]);

    const saveRiskTags = useCallback(async () => {
        if (!detail) return;
        setRiskEditorSaving(true);
        setRiskEditorError(null);
        try {
            const updated = await adminSetModerationQueueRiskTags(detail.id, riskSelected);
            setDetail(updated);
            await load();
            closeRiskEditor();
        } catch (e) {
            setRiskEditorError(e instanceof Error ? e.message : String(e));
        } finally {
            setRiskEditorSaving(false);
        }
    }, [closeRiskEditor, detail, load, riskSelected]);

    const approve = useCallback(
        async (it: Pick<ModerationQueueItem, 'id' | 'caseType' | 'status'>) => {
            const isHuman = it.status === 'HUMAN';
            const isOverride = it.status === 'REJECTED';
            const ok = window.confirm(
                it.caseType === 'REPORT'
                    ? (isHuman ? '确认人工处理：驳回该举报（内容正常）？' : (isOverride ? '确认通过（驳回举报并恢复内容，如已被下架）？' : '确认驳回该举报（内容正常）？'))
                    : (isHuman ? '确认覆核通过（恢复内容可见）？' : (isOverride ? '确认通过（恢复内容可见）？' : '确认内容审核通过？'))
            );
            if (!ok) return;
            const reason = window.prompt(it.caseType === 'REPORT' ? '请输入处理理由（必填）：' : '请输入通过理由（必填）：');
            if (reason === null) return;
            const reasonTrim = reason.trim();
            if (!reasonTrim) {
                window.alert('必须填写理由');
                return;
            }
            setActionLoadingId(it.id);
            setError(null);
            try {
                if (isOverride) {
                    await adminOverrideApproveModerationQueue(it.id, reasonTrim);
                } else {
                    await adminApproveModerationQueue(it.id, reasonTrim);
                }
                if (detail?.id === it.id) {
                    const d = await adminGetModerationQueueDetail(it.id);
                    setDetail(d);
                }
                await load();
            } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
            } finally {
                setActionLoadingId(null);
            }
        },
        [detail?.id, load]
    );

    const reject = useCallback(
        async (it: Pick<ModerationQueueItem, 'id' | 'caseType' | 'status'>) => {
            const isHuman = it.status === 'HUMAN';
            const isOverride = it.status === 'APPROVED';
            const reason = window.prompt(it.caseType === 'REPORT' ? '请输入核实举报原因（必填）：' : '请输入驳回原因（必填）：');
            if (reason === null) return;
            const reasonTrim = reason.trim();
            if (!reasonTrim) {
                window.alert('必须填写理由');
                return;
            }
            const ok = window.confirm(
                it.caseType === 'REPORT'
                    ? (isHuman ? '确认人工处理：核实该举报并下架内容？' : (isOverride ? '确认驳回（核实举报并下架内容）？' : '确认核实该举报并下架内容？'))
                    : (isHuman ? '确认覆核驳回（下架内容）？' : (isOverride ? '确认驳回（下架内容）？' : '确认驳回？'))
            );
            if (!ok) return;
            setActionLoadingId(it.id);
            setError(null);
            try {
                if (isOverride) {
                    await adminOverrideRejectModerationQueue(it.id, reasonTrim);
                } else {
                    await adminRejectModerationQueue(it.id, reasonTrim);
                }
                if (detail?.id === it.id) {
                    const d = await adminGetModerationQueueDetail(it.id);
                    setDetail(d);
                }
                await load();
            } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
            } finally {
                setActionLoadingId(null);
            }
        },
        [detail?.id, load]
    );

    const banUser = useCallback(
        async (it: Pick<ModerationQueueItem, 'id' | 'contentType'>) => {
            const label = it.contentType === 'COMMENT' ? '该评论作者' : it.contentType === 'POST' ? '该帖子作者' : '该资料用户';
            const ok = window.confirm(`确认封禁${label}？封禁后该用户将无法登录与发帖。`);
            if (!ok) return;
            const reason = window.prompt('请输入封禁原因（必填）：');
            if (reason === null) return;
            const reasonTrim = reason.trim();
            if (!reasonTrim) {
                window.alert('必须填写封禁原因');
                return;
            }
            setActionLoadingId(it.id);
            setError(null);
            try {
                await adminBanModerationQueueUser(it.id, reasonTrim);
                if (detail?.id === it.id) {
                    const d = await adminGetModerationQueueDetail(it.id);
                    setDetail(d);
                }
                await load();
            } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
            } finally {
                setActionLoadingId(null);
            }
        },
        [detail?.id, load]
    );

    const claim = useCallback(async (id: number) => {
        setActionLoadingId(id);
        setError(null);
        try {
            await adminClaimModerationQueue(id);
            if (detail?.id === id) {
                const d = await adminGetModerationQueueDetail(id);
                setDetail(d);
            }
            await load();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setActionLoadingId(null);
        }
    }, [detail?.id, load]);

    const release = useCallback(async (id: number) => {
        setActionLoadingId(id);
        setError(null);
        try {
            await adminReleaseModerationQueue(id);
            if (detail?.id === id) {
                const d = await adminGetModerationQueueDetail(id);
                setDetail(d);
            }
            await load();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setActionLoadingId(null);
        }
    }, [detail?.id, load]);

    const requeue = useCallback(async (id: number) => {
        const ok = window.confirm('确认将该任务重新进入自动审核（会清空认领，并从 RULE 重新跑）？');
        if (!ok) return;
        const reason = window.prompt('备注（必填）：');
        if (reason === null) return;
        const reasonTrim = reason.trim();
        if (!reasonTrim) {
            window.alert('必须填写理由');
            return;
        }
        setActionLoadingId(id);
        setError(null);
        try {
            const requeued = await adminRequeueModerationQueue(id, reasonTrim);
            const suppressAt = Date.now();
            suppressChunkProgressUntilRef.current = { ...suppressChunkProgressUntilRef.current, [id]: suppressAt };
            upsertQueueSnapshot({
                id,
                status: requeued.status,
                currentStage: requeued.currentStage,
                assignedToId: requeued.assignedToId ?? null,
                updatedAt: requeued.updatedAt,
                chunkProgress: null,
            });
            if (detail?.id === id) {
                setDetailChunkProgress(null);
                setDetailChunkProgressLoading(false);
            }
            const sleep = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms));
            const MAX_POLL = 6;
            for (let i = 0; i < MAX_POLL; i += 1) {
                await sleep(i === 0 ? 250 : 800);
                let d: ModerationQueueDetail | null = null;
                let p: ModerationChunkProgress | null = null;
                try {
                    [d, p] = await Promise.all([
                        adminGetModerationQueueDetail(id),
                        adminGetModerationQueueChunkProgress(id, { includeChunks: true, limit: 120 }),
                    ]);
                } catch {
                    continue;
                }
                const detailProgressUpdatedAt = d.chunkProgress?.updatedAt ? Date.parse(String(d.chunkProgress.updatedAt)) : Number.NaN;
                const chunkProgressUpdatedAt = p.updatedAt ? Date.parse(String(p.updatedAt)) : Number.NaN;
                const progressStarted =
                    (Number.isFinite(detailProgressUpdatedAt) && detailProgressUpdatedAt >= suppressAt) ||
                    (Number.isFinite(chunkProgressUpdatedAt) && chunkProgressUpdatedAt >= suppressAt) ||
                    (p.runningChunks ?? 0) > 0 ||
                    (p.completedChunks ?? 0) > 0 ||
                    (p.failedChunks ?? 0) > 0 ||
                    d.status === 'REVIEWING' ||
                    d.currentStage !== 'RULE';

                upsertQueueSnapshot({
                    id,
                    status: d.status,
                    currentStage: d.currentStage,
                    assignedToId: d.assignedToId ?? null,
                    updatedAt: d.updatedAt,
                    chunkProgress: progressStarted ? d.chunkProgress ?? null : null,
                });
                if (detail?.id === id) {
                    setDetail(progressStarted ? d : { ...d, chunkProgress: null });
                    setDetailChunkProgress(progressStarted ? p : null);
                    setDetailChunkProgressLoading(false);
                }
                if (progressStarted) {
                    clearSuppressedChunkProgress(id);
                    break;
                }
                if (i === MAX_POLL - 1) {
                    setDetailChunkProgress(null);
                    setDetailChunkProgressLoading(false);
                }
            }
            await load();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setActionLoadingId(null);
        }
    }, [detail?.id, load, upsertQueueSnapshot, clearSuppressedChunkProgress]);

    const toHuman = useCallback(async (id: number) => {
        const reason = window.prompt('确认将该任务进入人工审核？\n请输入备注（必填），取消则不处理：');
        if (reason === null) return;
        const reasonTrim = reason.trim();
        if (!reasonTrim) {
            window.alert('必须填写理由');
            return;
        }
        setActionLoadingId(id);
        setError(null);
        try {
            await adminToHumanModerationQueue(id, reasonTrim);
            if (detail?.id === id) {
                const d = await adminGetModerationQueueDetail(id);
                setDetail(d);
            }
            await load();
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setActionLoadingId(null);
        }
    }, [detail?.id, load]);

    const canPrev = page > 1;
    const canNext = page < totalPages;
    const allOnPageSelected = useMemo(() => {
        if (!items.length) return false;
        return items.every((it) => !!selectedMap[it.id]);
    }, [items, selectedMap]);

    return (
        <div className="bg-white rounded-lg shadow p-4 space-y-4">
            <div className="flex items-center justify-between gap-3">
                <h3 className="text-lg font-semibold">审核队列面板</h3>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={backfill}
                        className="rounded border px-4 py-2 disabled:opacity-60"
                        disabled={loading || backfillLoading}
                        title="从数据库补齐历史遗漏的待审核帖子/评论"
                    >
                        {backfillLoading ? '补齐中…' : '补齐历史待审'}
                    </button>
                    <button
                        type="button"
                        onClick={() => void load()}
                        className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                        disabled={loading}
                    >
                        {loading ? '刷新中…' : '刷新'}
                    </button>
                </div>
            </div>

            {backfillResult ? (
                <div className="rounded border border-blue-200 bg-blue-50 text-blue-800 px-3 py-2 text-sm">
                    已补齐：新增入队 <b>{backfillResult.enqueued}</b> 条；已存在 <b>{backfillResult.alreadyQueued}</b> 条；跳过 <b>{backfillResult.skipped}</b> 条。
                    （扫描：帖子 {backfillResult.scannedPosts}，评论 {backfillResult.scannedComments}）
                </div>
            ) : null}

            {/* 过滤器 */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                <input
                    className="rounded border px-3 py-2"
                    placeholder="输入任务ID（可选）"
                    value={taskId}
                    onChange={(e) => {
                        setTaskId(e.target.value);
                        setPage(1);
                    }}
                />

                <select
                    className="rounded border px-3 py-2"
                    value={contentType}
                    onChange={(e) => {
                        setContentType((e.target.value as ContentType) || '');
                        setPage(1);
                    }}
                >
                    <option value="">全部类型</option>
                    <option value="POST">帖子</option>
                    <option value="COMMENT">评论</option>
                    <option value="PROFILE">资料</option>
                </select>

                <select
                    className="rounded border px-3 py-2"
                    value={status}
                    onChange={(e) => {
                        setStatus((e.target.value as QueueStatus) || '');
                        setPage(1);
                    }}
                >
                    <option value="">全部状态</option>
                    <option value="PENDING">待自动审核</option>
                    <option value="REVIEWING">审核中</option>
                    <option value="HUMAN">待人工审核</option>
                    <option value="APPROVED">已通过</option>
                    <option value="REJECTED">已驳回</option>
                </select>

                <select
                    className="rounded border px-3 py-2"
                    value={sortDir}
                    onChange={(e) => {
                        setOrderBy('createdAt');
                        setSortDir((e.target.value as 'asc' | 'desc') || 'desc');
                        setPage(1);
                    }}
                >
                    <option value="desc">时间倒序</option>
                    <option value="asc">时间正序</option>
                </select>   
                <select
                    className="rounded border px-3 py-2"
                    value={pageSize}
                    onChange={(e) => {
                        setPageSize(Number(e.target.value));
                        setPage(1);
                    }}
                >
                    <option value={10}>每页 10</option>
                    <option value={30}>每页 30</option>
                    <option value={100}>每页 100</option>
                    <option value={200}>每页 200</option>
                    <option value={500}>每页 500</option>
                </select>
            </div>

            {error ? (
                <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>
            ) : null}

            {chunkCfgError ? (
                <div className="rounded border border-amber-200 bg-amber-50 text-amber-800 px-3 py-2 text-sm">
                    分片审核配置读取失败：{chunkCfgError}
                </div>
            ) : null}

            {selectedIds.length ? (
                <div className="rounded border bg-gray-50 px-3 py-2 text-sm flex flex-wrap items-center gap-2">
                    <div className="text-gray-700">已选 {selectedIds.length} 条</div>
                    <input
                        type="text"
                        className="rounded border px-2 py-1 flex-1 min-w-[220px]"
                        placeholder="批量进入再次审核：备注（必填）"
                        value={batchReason}
                        onChange={(e) => setBatchReason(e.target.value)}
                    />
                    <button
                        type="button"
                        className="rounded bg-blue-600 text-white px-3 py-1.5 disabled:opacity-60"
                        disabled={loading || !batchReason.trim()}
                        onClick={async () => {
                            const reason = batchReason.trim();
                            if (!reason) return;
                            const ok = window.confirm(`确认将 ${selectedIds.length} 条任务批量进入再次审核（重新自动审核）？`);
                            if (!ok) return;
                            setError(null);
                            try {
                                const res = await adminBatchRequeueModerationQueue(selectedIds, reason);
                                const successIds = (res.successIds && res.successIds.length) ? res.successIds : selectedIds;
                                const suppressAt = Date.now();
                                const suppressed = suppressChunkProgressUntilRef.current;
                                const nextSuppressed = { ...suppressed };
                                for (const id of successIds) nextSuppressed[id] = suppressAt;
                                suppressChunkProgressUntilRef.current = nextSuppressed;
                                const nowIso = new Date().toISOString();
                                setItems((prev) => prev.map((it) => (
                                    successIds.includes(it.id)
                                        ? {
                                            ...it,
                                            status: 'PENDING',
                                            currentStage: 'RULE',
                                            assignedToId: null,
                                            updatedAt: nowIso,
                                            chunkProgress: null,
                                        }
                                        : it
                                )));
                                if (detailOpen && detail?.id != null && successIds.includes(detail.id)) {
                                    setDetailChunkProgress(null);
                                    setDetailChunkProgressLoading(false);
                                    setDetail((prev) => (
                                        prev && prev.id === detail.id
                                            ? {
                                                ...prev,
                                                status: 'PENDING',
                                                currentStage: 'RULE',
                                                assignedToId: null,
                                                updatedAt: nowIso,
                                                chunkProgress: null,
                                            }
                                            : prev
                                    ));
                                }
                                setSelectedMap({});
                                setBatchReason('');
                                await load();
                            } catch (e) {
                                setError(e instanceof Error ? e.message : String(e));
                            }
                        }}
                    >
                        批量进入再次审核
                    </button>
                    <button
                        type="button"
                        className="rounded border px-3 py-1.5"
                        onClick={() => setSelectedMap({})}
                    >
                        清空选择
                    </button>
                </div>
            ) : null}

            {/* 列表 */}
            <div className="overflow-auto border rounded">
                <table className="min-w-full text-sm">
                    <thead className="bg-gray-50">
                    <tr>
                        <th className="text-left px-3 py-2">
                            <input
                                type="checkbox"
                                className="h-4 w-4"
                                checked={allOnPageSelected}
                                onChange={(e) => {
                                    const checked = e.target.checked;
                                    setSelectedMap((prev) => {
                                        const next = { ...prev };
                                        for (const it of items) next[it.id] = checked;
                                        return next;
                                    });
                                }}
                                aria-label="全选本页"
                            />
                        </th>
                        <th className="text-left px-3 py-2">任务ID</th>
                        <th className="text-left px-3 py-2">类型</th>
                        <th className="text-left px-3 py-2">内容ID</th>
                        <th className="text-left px-3 py-2">摘要</th>
                        <th className="text-left px-3 py-2">状态</th>
                        <th className="text-left px-3 py-2">阶段</th>
                        <th className="text-left px-3 py-2">创建时间</th>
                        <th className="text-right px-3 py-2">操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    {items.length === 0 ? (
                        <tr>
                            <td className="px-3 py-4 text-gray-500" colSpan={9}>
                                {loading ? '加载中…' : '暂无数据'}
                            </td>
                        </tr>
                    ) : (
                        items.map((it) => {
                            const isHumanClaimed = it.status === 'HUMAN' && it.assignedToId != null;
                            const primaryStatus = isHumanClaimed ? 'REVIEWING' : it.status;
                            return (
                                <tr key={it.id} className="border-t">
                                    <td className="px-3 py-2">
                                        <input
                                            type="checkbox"
                                            className="h-4 w-4"
                                            checked={!!selectedMap[it.id]}
                                            onChange={(e) => {
                                                const checked = e.target.checked;
                                                setSelectedMap((prev) => ({ ...prev, [it.id]: checked }));
                                            }}
                                            aria-label={`选择任务 ${it.id}`}
                                        />
                                    </td>
                                    <td className="px-3 py-2">{it.id}</td>
                                    <td className="px-3 py-2">{typeLabel(it.caseType, it.contentType)}</td>
                                    <td className="px-3 py-2">{it.contentId}</td>
                                    <td className="px-3 py-2">
                                        <div className="font-medium text-gray-900 truncate max-w-[520px]">
                                            {it.summary?.title || '—'}
                                        </div>
                                        <div className="text-gray-500 truncate max-w-[520px]">{it.summary?.snippet || '—'}</div>
                                        {it.riskTagItems && it.riskTagItems.length ? (
                                            <div className="flex flex-wrap gap-1 mt-1">
                                                {it.riskTagItems.map((t) => (
                                                    <span
                                                        key={t.slug}
                                                        className="inline-flex px-2 py-0.5 rounded-full text-xs border border-amber-200 bg-amber-50 text-amber-900"
                                                        title={t.name || t.slug}
                                                    >
                                                        {t.name || t.slug}
                                                    </span>
                                                ))}
                                            </div>
                                        ) : it.riskTags && it.riskTags.length ? (
                                            <div className="flex flex-wrap gap-1 mt-1">
                                                {it.riskTags.map((t) => (
                                                    <span
                                                        key={t}
                                                        className="inline-flex px-2 py-0.5 rounded-full text-xs border border-amber-200 bg-amber-50 text-amber-900"
                                                        title={t}
                                                    >
                                                        {t}
                                                    </span>
                                                ))}
                                            </div>
                                        ) : null}
                                        {it.contentType === 'COMMENT' && it.summary?.postId ? (
                                            <div className="text-gray-400">所属帖子ID: {it.summary.postId}</div>
                                        ) : null}
                                    </td>
                                    <td className="px-3 py-2">
                                        <div className="flex flex-wrap items-center gap-1">
                                            <span className={`inline-flex px-2 py-1 rounded text-xs ${statusBadgeClass(primaryStatus)}`}>
                                                {statusLabel(primaryStatus, it.caseType)}
                                            </span>
                                            {isHumanClaimed ? (
                                                <span className="inline-flex px-2 py-1 rounded text-xs bg-purple-100 text-purple-800">
                                                    人工审核中
                                                </span>
                                            ) : null}
                                        </div>
                                    </td>
                                    <td className="px-3 py-2">
                                        <span className="inline-flex px-2 py-1 rounded text-xs bg-gray-100 text-gray-700">
                                            {stageLabel(it.currentStage)}
                                        </span>
                                        {it.chunkProgress && (it.chunkProgress.totalChunks ?? 0) > 0 ? (
                                            <div className="text-xs text-gray-600 mt-1">
                                                分片 {it.chunkProgress.completedChunks ?? 0}/{it.chunkProgress.totalChunks ?? 0}
                                                {(it.chunkProgress.failedChunks ?? 0) > 0 ? (
                                                    <>
                                                        <span className="text-red-700">{` · 失败 ${it.chunkProgress.failedChunks}`}</span>
                                                        <button
                                                            type="button"
                                                            className="ml-2 text-xs text-blue-700 hover:underline disabled:opacity-60"
                                                            onClick={() => void openChunkErrorsForQueue(it.id)}
                                                            disabled={chunkErrorLoadingQueueId === it.id}
                                                            title="查看失败分片的错误信息"
                                                        >
                                                            {chunkErrorLoadingQueueId === it.id ? '加载中…' : '查看错误'}
                                                        </button>
                                                    </>
                                                ) : (
                                                    ''
                                                )}
                                            </div>
                                        ) : null}
                                    </td>
                                    <td className="px-3 py-2">{formatDateTime(it.createdAt)}</td>
                                    <td className="px-3 py-2 text-right whitespace-nowrap">
                                        <button
                                            type="button"
                                            className="rounded border px-3 py-1 mr-2 hover:bg-gray-50"
                                            onClick={() => openDetail(it.id)}
                                        >
                                            详情
                                        </button>
                                        {it.status !== 'APPROVED' ? (
                                            <button
                                                type="button"
                                                className="rounded bg-green-600 text-white px-3 py-1 mr-2 disabled:opacity-60"
                                                onClick={() => approve(it)}
                                                disabled={actionLoadingId === it.id}
                                            >
                                                {it.caseType === 'REPORT' ? '驳回举报' : (it.status === 'HUMAN' ? '覆核通过' : '通过')}
                                            </button>
                                        ) : null}
                                        {it.status !== 'REJECTED' && it.status !== 'APPROVED' ? (
                                            <button
                                                type="button"
                                                className="rounded bg-red-600 text-white px-3 py-1 mr-2 disabled:opacity-60"
                                                onClick={() => reject(it)}
                                                disabled={actionLoadingId === it.id}
                                            >
                                                {it.caseType === 'REPORT' ? '核实举报' : (it.status === 'HUMAN' ? '覆核驳回' : '驳回')}
                                            </button>
                                        ) : null}
                                        {isTerminal(it.status) ? (
                                            <button
                                                type="button"
                                                className="rounded border px-3 py-1 mr-2 hover:bg-gray-50 disabled:opacity-60"
                                                onClick={() => toHuman(it.id)}
                                                disabled={actionLoadingId === it.id}
                                                title="已终态任务切回人工审核（HUMAN），以便再次处理"
                                            >
                                                进入人工审核
                                            </button>
                                        ) : null}

                                        {!isTerminal(it.status) ? (
                                            <button
                                                type="button"
                                                className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
                                                disabled={actionLoadingId === it.id || it.status !== 'HUMAN' || (it.assignedToId != null && it.assignedToId !== myUserId)}
                                                onClick={() => claim(it.id)}
                                                title="认领后才能处理（通过/驳回）"
                                            >
                                                认领
                                            </button>
                                        ) : null}

                                        {it.status === 'HUMAN' ? (
                                            <button
                                                type="button"
                                                className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
                                                disabled={actionLoadingId === it.id || it.assignedToId == null || it.assignedToId !== myUserId}
                                                onClick={() => release(it.id)}
                                                title="释放自己认领的任务"
                                            >
                                                释放
                                            </button>
                                        ) : null}

                                        <button
                                            type="button"
                                            className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
                                            disabled={actionLoadingId === it.id}
                                            onClick={() => requeue(it.id)}
                                            title="重新进入自动审核（从 RULE 重新跑）"
                                        >
                                            重新自动审核
                                        </button>
                                    </td>
                                </tr>
                            );
                        })
                    )}
                    </tbody>
                </table>
            </div>

            {/* 分页 */}
            <div className="flex items-center justify-between text-sm">
                <div className="text-gray-600">
                    共 {totalElements} 条，{totalPages} 页
                </div>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        className="rounded border px-3 py-1 disabled:opacity-60"
                        disabled={!canPrev || loading}
                        onClick={() => setPage((p) => Math.max(1, p - 1))}
                    >
                        上一页
                    </button>
                    <div>
                        第 <span className="font-medium">{page}</span> 页
                    </div>
                    <button
                        type="button"
                        className="rounded border px-3 py-1 disabled:opacity-60"
                        disabled={!canNext || loading}
                        onClick={() => setPage((p) => p + 1)}
                    >
                        下一页
                    </button>
                </div>
            </div>

            {/* 详情弹窗 */}
            {detailOpen ? (
                <DetailDialog
                    open={detailOpen}
                    onClose={closeDetail}
                    title={detail?.id != null ? `任务详情 #${detail.id}` : '任务详情'}
                    tabs={[
                        { id: 'overview', label: '概览' },
                        { id: 'chunks', label: '分片' },
                        { id: 'content', label: '内容' },
                    ]}
                    activeTabId={detailTab}
                    onTabChange={(id) => setDetailTab(id as 'overview' | 'chunks' | 'content')}
                    containerClassName="max-w-[1280px] max-h-[85vh]"
                    bodyClassName="flex-1 overflow-auto p-4 space-y-3"
                >
                    {detailLoading ? (
                        <div className="text-gray-600">加载中…</div>
                    ) : detail ? (
                        <>
                            {detailTab === 'overview' ? (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                                        <div><span className="text-gray-500">任务ID：</span>{detail.id}</div>
                                        <div><span className="text-gray-500">类型：</span>{typeLabel(detail.caseType, detail.contentType)}</div>
                                        <div><span className="text-gray-500">内容ID：</span>{detail.contentId}</div>
                                        <div>
                                            <span className="text-gray-500">状态：</span>
                                            {(() => {
                                                const isHumanClaimed = detail.status === 'HUMAN' && detail.assignedToId != null;
                                                const primaryStatus = isHumanClaimed ? 'REVIEWING' : detail.status;
                                                return (
                                                    <span className="inline-flex items-center gap-1">
                                                        <span className={`inline-flex px-2 py-1 rounded text-xs ${statusBadgeClass(primaryStatus)}`}>{statusLabel(primaryStatus, detail.caseType)}</span>
                                                        {isHumanClaimed ? (
                                                            <span className="inline-flex px-2 py-1 rounded text-xs bg-purple-100 text-purple-800">人工审核中</span>
                                                        ) : null}
                                                    </span>
                                                );
                                            })()}
                                        </div>
                                        <div>
                                            <span className="text-gray-500">阶段：</span>
                                            <span className="inline-flex px-2 py-1 rounded text-xs bg-gray-100 text-gray-700">
                                                {stageLabel(detail.currentStage)}
                                            </span>
                                        </div>
                                        <div><span className="text-gray-500">创建时间：</span>{formatDateTime(detail.createdAt)}</div>
                                        <div><span className="text-gray-500">更新时间：</span>{formatDateTime(detail.updatedAt)}</div>
                                </div>
                            ) : null}

                            {detailTab === 'chunks' ? (
                                <>
                                    {detailChunkProgressLoading ? (
                                        <div className="border rounded p-3 text-sm text-gray-600">正在加载分片进度…</div>
                                    ) : detailChunkProgress && (detailChunkProgress.totalChunks ?? 0) > 0 ? (
                                        <div className="border rounded p-3 space-y-2">
                                            <div className="flex items-center justify-between gap-2">
                                                <div className="font-medium">分片进度</div>
                                                <div className="flex items-center gap-2">
                                                    {detailChunksView.length > DETAIL_CHUNK_COLLAPSE_THRESHOLD ? (
                                                        <button
                                                            type="button"
                                                            className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
                                                            onClick={() => setDetailChunksExpanded((v) => !v)}
                                                        >
                                                            {detailChunksExpanded ? '收起' : `展开（共 ${detailChunksView.length} 条）`}
                                                        </button>
                                                    ) : null}
                                                    <button
                                                        type="button"
                                                        className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
                                                        onClick={async () => {
                                                            if (!detail?.id) return;
                                                            setDetailChunkProgressLoading(true);
                                                            try {
                                                                const p = await adminGetModerationQueueChunkProgress(detail.id, { includeChunks: true, limit: 120 });
                                                                setDetailChunkProgress(p);
                                                            } finally {
                                                                setDetailChunkProgressLoading(false);
                                                            }
                                                        }}
                                                    >
                                                        刷新进度
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="text-sm text-gray-700">
                                                状态：{detailChunkProgress.status || '—'} · 已完成 {detailChunkProgress.completedChunks ?? 0}/{detailChunkProgress.totalChunks ?? 0}
                                                {(detailChunkProgress.failedChunks ?? 0) > 0 ? ` · 失败 ${detailChunkProgress.failedChunks}` : ''}
                                                {(detailChunkProgress.runningChunks ?? 0) > 0 ? ` · 运行中 ${detailChunkProgress.runningChunks}` : ''}
                                            </div>
                                            {imageChunkSummary ? (
                                                <div className="text-sm text-gray-700">
                                                    图片分片：已完成 {imageChunkSummary.completed}/{imageChunkSummary.total}
                                                    {imageChunkSummary.failed > 0 ? ` · 失败 ${imageChunkSummary.failed}` : ''}
                                                    {imageChunkSummary.running > 0 ? ` · 运行中 ${imageChunkSummary.running}` : ''}
                                                </div>
                                            ) : null}
                                            {detailChunkProgress.chunks && detailChunkProgress.chunks.length ? (
                                                <div className="overflow-auto border rounded">
                                                    <table className="min-w-full text-xs">
                                                        <thead className="bg-gray-50">
                                                            <tr>
                                                                <th className="text-left px-2 py-1">来源</th>
                                                                <th className="text-left px-2 py-1">分片</th>
                                                                <th className="text-left px-2 py-1">状态</th>
                                                                <th className="text-left px-2 py-1">结论</th>
                                                                <th className="text-left px-2 py-1">分数</th>
                                                                <th className="text-left px-2 py-1">耗时</th>
                                                                <th className="text-left px-2 py-1">重试</th>
                                                                <th className="text-left px-2 py-1">错误</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {(detailChunksExpanded || detailChunksView.length <= DETAIL_CHUNK_COLLAPSE_THRESHOLD
                                                                ? detailChunksView
                                                                : detailChunksView.slice(0, DETAIL_CHUNK_COLLAPSE_THRESHOLD)
                                                            ).map((c) => (
                                                                <tr key={c.id} className="border-t">
                                                                    <td className="px-2 py-1">
                                                                        {c.sourceType === 'FILE_TEXT'
                                                                            ? `文件：${c.fileName || c.fileAssetId || '—'}`
                                                                            : '正文'}
                                                                    </td>
                                                                    <td className="px-2 py-1">{c.chunkIndex ?? '—'}</td>
                                                                    <td className="px-2 py-1">{c.status || '—'}</td>
                                                                    <td className="px-2 py-1">{c.verdict || '—'}</td>
                                                                    <td className="px-2 py-1">
                                                                        {typeof c.score === 'number'
                                                                            ? c.score.toFixed(3)
                                                                            : typeof c.confidence === 'number'
                                                                            ? c.confidence.toFixed(3)
                                                                            : '—'}
                                                                    </td>
                                                                    <td className="px-2 py-1">
                                                                        {typeof c.elapsedMs === 'number' ? `${c.elapsedMs}ms` : '—'}
                                                                    </td>
                                                                    <td className="px-2 py-1">{typeof c.attempts === 'number' ? c.attempts : '—'}</td>
                                                                    <td className="px-2 py-1">
                                                                        {c.lastError ? (
                                                                            <button
                                                                                type="button"
                                                                                className="text-xs text-blue-700 hover:underline"
                                                                                onClick={() => {
                                                                                    setChunkErrorTitle(`分片错误（任务 ${detail?.id ?? ''}）`);
                                                                                    setChunkErrorText(String(c.lastError ?? ''));
                                                                                    setChunkErrorOpen(true);
                                                                                }}
                                                                                title={c.lastError}
                                                                            >
                                                                                查看
                                                                            </button>
                                                                        ) : (
                                                                            '—'
                                                                        )}
                                                                    </td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                    {!detailChunksExpanded && detailChunksView.length > DETAIL_CHUNK_COLLAPSE_THRESHOLD ? (
                                                        <div className="px-2 py-1 text-xs text-gray-500">
                                                            已折叠，仅展示前 {DETAIL_CHUNK_COLLAPSE_THRESHOLD} 条（失败/运行优先），点击“展开”查看全部
                                                        </div>
                                                    ) : null}
                                                    {detailChunkProgress.chunks.length > detailChunksView.length ? (
                                                        <div className="px-2 py-1 text-xs text-gray-500">
                                                            仅展示前 {detailChunksView.length} 条（失败/运行优先）
                                                        </div>
                                                    ) : null}
                                                </div>
                                            ) : null}
                                        </div>
                                    ) : null}

                                    {(detailChunkProgress && (detailChunkProgress.totalChunks ?? 0) > 0) || detailTrace?.chunkSet || detailTrace?.latestRun ? (
                                        <div className="border rounded p-3 space-y-2">
                                            <div className="flex items-center justify-between gap-3">
                                                <div className="font-medium">证据与分片审核输出汇总</div>
                                                <div className="flex items-center gap-2">
                                                    <input
                                                        className="rounded border px-2 py-1 text-sm w-[180px]"
                                                        placeholder="chunkIndex 筛选"
                                                        value={detailChunkIndexFilter}
                                                        onChange={(e) => setDetailChunkIndexFilter(e.target.value)}
                                                    />
                                                    <button
                                                        type="button"
                                                        className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
                                                        onClick={() => navigate(`/admin/review?active=logs&queueId=${detail.id}`)}
                                                        title="打开“审核日志与追溯（按任务聚合）”查看更多原始数据"
                                                    >
                                                        打开追溯
                                                    </button>
                                                </div>
                                            </div>

                                            {detailTraceLoading ? (
                                                <div className="text-sm text-gray-600">正在加载分片输出…</div>
                                            ) : detailTraceError ? (
                                                <div className="text-sm text-red-700">加载失败：{detailTraceError}</div>
                                            ) : (
                                                <>
                                                    <details className="rounded border p-2" open>
                                                        <summary className="cursor-pointer select-none text-sm">
                                                            evidence（列表）（{detailUniqueEvidenceCount}）
                                                        </summary>
                                                        <div className="mt-2">
                                                            <EvidenceListView
                                                                stepEvidenceGroups={detailStepEvidenceGroups}
                                                                chunkEvidenceByChunkIndex={detailTrace?.chunkSet ? (detailChunkOutput.llmEvidenceByChunk as unknown as Record<string, unknown[]>) : undefined}
                                                                chunkIdByChunkIndex={detailChunkIdByChunkIndex}
                                                                chunkIndexFilter={detailChunkOutput.filterIdx}
                                                                imageUrlByImageId={detailEvidenceImageUrlById}
                                                            />
                                                        </div>
                                                    </details>

                                                    {detailTrace?.chunkSet ? (
                                                        <details className="rounded border p-2">
                                                            <summary className="cursor-pointer select-none text-sm">
                                                                summaries（{Object.keys(detailChunkOutput.summaries).length}）
                                                            </summary>
                                                            <div className="mt-2 overflow-auto">
                                                                <table className="min-w-[860px] w-full text-sm">
                                                                    <thead>
                                                                        <tr className="text-left text-xs text-gray-500">
                                                                            <th className="py-2 pr-3 w-[120px]">chunkIndex</th>
                                                                            <th className="py-2 pr-3">summary</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {Object.entries(detailChunkOutput.summaries)
                                                                            .map(([k, v]) => ({ k, v, n: toInt(k) }))
                                                                            .sort((a, b) => (a.n ?? 0) - (b.n ?? 0))
                                                                            .filter((x) => (detailChunkOutput.filterIdx == null ? true : x.n === detailChunkOutput.filterIdx))
                                                                            .map((x) => (
                                                                                <tr key={x.k} className="border-t align-top">
                                                                                    <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{x.k}</td>
                                                                                    <td className="py-2 pr-3">
                                                                                        <pre className="text-xs whitespace-pre-wrap break-words max-h-[220px] overflow-auto">{x.v}</pre>
                                                                                    </td>
                                                                                </tr>
                                                                            ))}
                                                                        {Object.keys(detailChunkOutput.summaries).length === 0 ? (
                                                                            <tr>
                                                                                <td className="py-4 text-center text-gray-500" colSpan={2}>
                                                                                    暂无 summaries
                                                                                </td>
                                                                            </tr>
                                                                        ) : null}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        </details>
                                                    ) : detailStepEvidenceCount <= 0 ? (
                                                        <div className="text-sm text-gray-500">（暂无 chunkSet / 未启用分片审核输出）</div>
                                                    ) : null}
                                                </>
                                            )}
                                        </div>
                                    ) : null}
                                </>
                            ) : null}

                            {detailTab === 'content' ? (
                                <>
                                    <div className="border rounded p-3 space-y-2">
                                        <div className="font-medium">帖子摘要</div>
                                        {(() => {
                                            const enabled = postSummary?.enabled;
                                            const status = String(postSummary?.status ?? '');
                                            const normalized = status.toUpperCase();
                                            const isSuccess = normalized === 'SUCCESS' && Boolean(postSummary?.summaryText?.trim());
                                            const isDisabled = enabled === false || normalized === 'DISABLED';
                                            const isPending = normalized === 'PENDING';
                                            const isFailed = normalized === 'FAILED';

                                            const fallbackTitle = relatedPostLite?.title || (detail.contentType === 'POST' ? detail.post?.title : null) || '—';
                                            const fallbackContent = relatedPostLite?.content || (detail.contentType === 'POST' ? detail.post?.content : null) || '';
                                            const fallbackSnippet = excerptText(fallbackContent, 260);

                                            if (postSummaryLoading) {
                                                return <div className="text-gray-600 text-sm">加载中…</div>;
                                            }

                                            if (postSummaryError) {
                                                return (
                                                    <>
                                                        <div className="text-sm text-red-700">加载失败：{postSummaryError}</div>
                                                        <div className="text-sm text-gray-700">标题：{fallbackTitle}</div>
                                                        <div className="text-sm text-gray-500">内容：{fallbackSnippet || '—'}</div>
                                                    </>
                                                );
                                            }

                                            if (isSuccess) {
                                                return (
                                                    <>
                                                        <div className="text-sm text-gray-700">标题：{postSummary?.summaryTitle || fallbackTitle}</div>
                                                        <div className="text-sm text-gray-700 whitespace-pre-wrap break-words">{postSummary?.summaryText}</div>
                                                        <div className="text-xs text-gray-500">
                                                            模型：{postSummary?.model || '（默认）'} · 时间：{postSummary?.generatedAt ? formatDateTime(postSummary.generatedAt) : '—'} · 耗时：
                                                            {postSummary?.latencyMs ? `${postSummary.latencyMs}ms` : '—'}
                                                        </div>
                                                    </>
                                                );
                                            }

                                            const hint = isDisabled ? '摘要功能未启用，展示标题与正文摘录。' : isPending ? '摘要尚未生成，展示标题与正文摘录。' : isFailed ? '摘要生成失败，展示标题与正文摘录。' : '摘要不可用，展示标题与正文摘录。';

                                            return (
                                                <>
                                                    <div className="text-sm text-gray-500">{hint}</div>
                                                    {isFailed && postSummaryLatestHistory?.errorMessage ? (
                                                        <div className="flex justify-end">
                                                            <button
                                                                type="button"
                                                                className="text-xs text-blue-700 hover:underline"
                                                                onClick={() => setPostSummaryErrorOpen(true)}
                                                            >
                                                                查看错误
                                                            </button>
                                                        </div>
                                                    ) : null}
                                                    <div className="text-sm text-gray-700">标题：{fallbackTitle}</div>
                                                    <div className="text-sm text-gray-500">内容：{fallbackSnippet || '—'}</div>
                                                </>
                                            );
                                        })()}
                                    </div>

                                    {detail.reports && detail.reports.length ? (
                                        <div className="border rounded p-3 space-y-2">
                                            <div className="font-medium">举报</div>
                                            <div className="space-y-2">
                                                {detail.reports.slice(0, 3).map((r) => (
                                                    <div key={r.id} className="rounded bg-gray-50 p-2 text-sm">
                                                        <div className="flex items-center justify-between gap-2">
                                                            <div className="text-gray-800">
                                                                {r.reasonCode || '—'}
                                                                {r.reasonText ? `：${r.reasonText}` : ''}
                                                            </div>
                                                            <div className="text-xs text-gray-500">
                                                                {r.createdAt ? formatDateTime(r.createdAt) : '—'}
                                                            </div>
                                                        </div>
                                                        <div className="mt-1 text-xs text-gray-500">
                                                            举报人：{r.reporterId ?? '—'} · 状态：{r.status || '—'}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ) : null}
                                    <div className="border rounded p-3 space-y-2">
                                        <div className="flex items-center justify-between gap-3">
                                            <div className="font-medium">风险标签</div>
                                            {detail.status === 'HUMAN' ? (
                                                <button
                                                    type="button"
                                                    className="rounded border px-3 py-1 hover:bg-gray-50"
                                                    onClick={openRiskEditor}
                                                >
                                                    编辑风险标签
                                                </button>
                                            ) : null}
                                        </div>
                                        {detail.riskTagItems && detail.riskTagItems.length ? (
                                            <div className="flex flex-wrap gap-1">
                                                {detail.riskTagItems.map((t) => (
                                                    <span
                                                        key={t.slug}
                                                        className="inline-flex px-2 py-0.5 rounded-full text-xs border border-amber-200 bg-amber-50 text-amber-900"
                                                        title={t.name || t.slug}
                                                    >
                                                        {t.name || t.slug}
                                                    </span>
                                                ))}
                                            </div>
                                        ) : detail.riskTags && detail.riskTags.length ? (
                                            <div className="flex flex-wrap gap-1">
                                                {detail.riskTags.map((t) => (
                                                    <span
                                                        key={t}
                                                        className="inline-flex px-2 py-0.5 rounded-full text-xs border border-amber-200 bg-amber-50 text-amber-900"
                                                        title={t}
                                                    >
                                                        {t}
                                                    </span>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="text-sm text-gray-500">（暂无）</div>
                                        )}
                                    </div>

                                    {detail.contentType === 'POST' ? (
                                        <div className="border rounded p-3">
                                            <div className="mb-2 flex items-center justify-between gap-3">
                                                <div className="font-medium">帖子内容</div>
                                                <button
                                                    type="button"
                                                    className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
                                                    onClick={() => setPostMarkdownPreviewEnabled((v) => !v)}
                                                >
                                                    {postMarkdownPreviewEnabled ? '查看原文' : '预览 Markdown'}
                                                </button>
                                            </div>
                                            <div
                                                className="text-sm text-gray-700">标题：{detail.post?.title || '—'}</div>
                                            <div
                                                className="text-sm text-gray-500 mb-2">状态：{detail.post?.status || '—'}</div>
                                            {postMarkdownPreviewEnabled ? (
                                                <div className="rounded border bg-white p-2 overflow-auto max-h-[380px]">
                                                    <MarkdownPreview markdown={detail.post?.content || ''} />
                                                </div>
                                            ) : (
                                                <pre
                                                    className="whitespace-pre-wrap text-sm bg-gray-50 rounded p-2 overflow-auto max-h-[300px]">{detail.post?.content || ''}</pre>
                                            )}
                                            {(() => {
                                                const atts = detail.post?.attachments ?? [];
                                                const images = atts.filter((a) => {
                                                    const url = a?.url || '';
                                                    if (!url) return false;
                                                    const mt = (a?.mimeType || '').toLowerCase();
                                                    if (mt.startsWith('image/')) return true;
                                                    return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(url);
                                                });
                                                const files = atts.filter((a) => {
                                                    const url = a?.url || '';
                                                    if (!url) return false;
                                                    const mt = (a?.mimeType || '').toLowerCase();
                                                    if (mt.startsWith('image/')) return false;
                                                    if (/\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(url)) return false;
                                                    return true;
                                                });

                                                if ((!images || images.length === 0) && (!files || files.length === 0)) return null;

                                                return (
                                                    <div className="mt-3 space-y-2">
                                                        {images && images.length ? (
                                                            <div className="space-y-2">
                                                                <div className="text-sm font-medium text-gray-700">
                                                                    图片（{images.length}）
                                                                </div>
                                                                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                                                                    {images.slice(0, 9).map((img) => (
                                                                        (() => {
                                                                            const href = resolveAssetUrl(img.url) ?? img.url;
                                                                            return (
                                                                        <a
                                                                            key={img.id}
                                                                            href={href}
                                                                            target="_blank"
                                                                            rel="noreferrer"
                                                                            className="block rounded overflow-hidden border bg-white hover:shadow"
                                                                            title={img.fileName || href}
                                                                        >
                                                                            <img
                                                                                src={href}
                                                                                alt={img.fileName || 'image'}
                                                                                className="w-full h-28 object-cover"
                                                                                loading="lazy"
                                                                            />
                                                                        </a>
                                                                            );
                                                                        })()
                                                                    ))}
                                                                </div>
                                                                {images.length > 9 ? (
                                                                    <div className="text-xs text-gray-500">仅展示前 9 张</div>
                                                                ) : null}
                                                            </div>
                                                        ) : null}

                                                        {files && files.length ? (
                                                            <div className="space-y-1">
                                                                <div className="text-sm font-medium text-gray-700">
                                                                    附件（{files.length}）
                                                                </div>
                                                                <div className="space-y-1">
                                                                    {files.slice(0, 10).map((f) => (
                                                                        (() => {
                                                                            const href = resolveAssetUrl(f.url) ?? f.url;
                                                                            return (
                                                                                <div key={f.id} className="flex items-center gap-2 text-sm">
                                                                                    <a
                                                                                        href={href}
                                                                                        target="_blank"
                                                                                        rel="noreferrer"
                                                                                        className="text-blue-700 hover:underline break-all"
                                                                                    >
                                                                                        {f.fileName || href}
                                                                                    </a>
                                                                                    <a
                                                                                        href={href}
                                                                                        target="_blank"
                                                                                        rel="noreferrer"
                                                                                        className="rounded border px-2 py-0.5 text-xs text-gray-700 hover:bg-gray-50 shrink-0"
                                                                                    >
                                                                                        预览
                                                                                    </a>
                                                                                </div>
                                                                            );
                                                                        })()
                                                                    ))}
                                                                </div>
                                                                {files.length > 10 ? (
                                                                    <div className="text-xs text-gray-500">仅展示前 10 个</div>
                                                                ) : null}
                                                            </div>
                                                        ) : null}
                                                    </div>
                                                );
                                            })()}
                                        </div>
                                    ) : detail.contentType === 'COMMENT' ? (
                                        <div className="border rounded p-3">
                                            <div className="font-medium mb-1">评论内容</div>
                                            <div className="text-sm text-gray-500 mb-2">所属帖子ID：{detail.comment?.postId ?? '—'}</div>
                                            <pre className="whitespace-pre-wrap text-sm bg-gray-50 rounded p-2 overflow-auto max-h-[300px]">
                                                {detail.comment?.content || ''}
                                            </pre>
                                        </div>
                                    ) : detail.contentType === 'PROFILE' ? (
                                        <div className="border rounded p-3 space-y-2">
                                            <div className="font-medium">资料内容</div>
                                            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                                <div className="rounded border bg-gray-50 p-3 space-y-1">
                                                    <div className="text-sm font-medium text-gray-700">公开版本</div>
                                                    <div className="text-sm text-gray-700">昵称：{detail.profile?.publicUsername || '—'}</div>
                                                    <div className="text-sm text-gray-700">简介：{detail.profile?.publicBio || '—'}</div>
                                                    <div className="text-sm text-gray-700">地区：{detail.profile?.publicLocation || '—'}</div>
                                                    <div className="text-sm text-gray-700">网站：{detail.profile?.publicWebsite || '—'}</div>
                                                    <div className="text-sm text-gray-700">
                                                        头像：
                                                        {detail.profile?.publicAvatarUrl ? (
                                                            <a
                                                                href={detail.profile.publicAvatarUrl}
                                                                target="_blank"
                                                                rel="noreferrer"
                                                                className="ml-1 text-blue-700 hover:underline break-all"
                                                            >
                                                                查看
                                                            </a>
                                                        ) : (
                                                            '—'
                                                        )}
                                                    </div>
                                                </div>

                                                <div className="rounded border bg-white p-3 space-y-1">
                                                    <div className="text-sm font-medium text-gray-700">待审版本</div>
                                                    <div className="text-sm text-gray-700">昵称：{detail.profile?.pendingUsername || '—'}</div>
                                                    <div className="text-sm text-gray-700">简介：{detail.profile?.pendingBio || '—'}</div>
                                                    <div className="text-sm text-gray-700">地区：{detail.profile?.pendingLocation || '—'}</div>
                                                    <div className="text-sm text-gray-700">网站：{detail.profile?.pendingWebsite || '—'}</div>
                                                    <div className="text-sm text-gray-700">
                                                        头像：
                                                        {detail.profile?.pendingAvatarUrl ? (
                                                            <a
                                                                href={detail.profile.pendingAvatarUrl}
                                                                target="_blank"
                                                                rel="noreferrer"
                                                                className="ml-1 text-blue-700 hover:underline break-all"
                                                            >
                                                                查看
                                                            </a>
                                                        ) : (
                                                            '—'
                                                        )}
                                                    </div>
                                                    {detail.profile?.pendingSubmittedAt ? (
                                                        <div className="text-xs text-gray-500">提交时间：{detail.profile.pendingSubmittedAt}</div>
                                                    ) : null}
                                                </div>
                                            </div>
                                        </div>
                                    ) : null}

                                    <ModerationPipelineTracePanel queueId={detail.id} />

                                    <div className="flex justify-end gap-2">
                                        <button
                                            type="button"
                                            className="rounded border px-4 py-2 hover:bg-gray-50"
                                            onClick={() => navigate(`/admin/review?active=llm&queueId=${detail.id}`)}
                                            title="跳转到 LLM 审核层并自动带入 queueId 进行试运行"
                                        >
                                            LLM 试跑
                                        </button>
                                        <button
                                            type="button"
                                            className="rounded border border-red-300 text-red-700 px-4 py-2 hover:bg-red-50 disabled:opacity-60"
                                            onClick={() => banUser(detail)}
                                            disabled={actionLoadingId === detail.id}
                                            title="封禁该内容作者账号"
                                        >
                                            封禁用户
                                        </button>
                                        {detail.status !== 'APPROVED' ? (
                                            <button
                                                type="button"
                                                className="rounded bg-green-600 text-white px-4 py-2 disabled:opacity-60"
                                                onClick={() => approve(detail)}
                                                disabled={actionLoadingId === detail.id}
                                            >
                                                {detail.caseType === 'REPORT' ? '驳回举报' : (detail.status === 'HUMAN' ? '覆核通过' : '通过')}
                                            </button>
                                        ) : null}
                                        {detail.status !== 'REJECTED' && detail.status !== 'APPROVED' ? (
                                            <button
                                                type="button"
                                                className="rounded bg-red-600 text-white px-4 py-2 disabled:opacity-60"
                                                onClick={() => reject(detail)}
                                                disabled={actionLoadingId === detail.id}
                                            >
                                                {detail.caseType === 'REPORT' ? '核实举报' : (detail.status === 'HUMAN' ? '覆核驳回' : '驳回')}
                                            </button>
                                        ) : null}
                                        {isTerminal(detail.status) ? (
                                            <button
                                                type="button"
                                                className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-60"
                                                onClick={() => toHuman(detail.id)}
                                                disabled={actionLoadingId === detail.id}
                                                title="已终态任务切回人工审核（HUMAN），以便再次处理"
                                            >
                                                进入人工审核
                                            </button>
                                        ) : null}

                                        {detail.status === 'HUMAN' ? (
                                            <>
                                                <button
                                                    type="button"
                                                    className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                                                    disabled={actionLoadingId === detail.id || (detail.assignedToId != null && detail.assignedToId !== myUserId)}
                                                    onClick={() => claim(detail.id)}
                                                >
                                                    认领
                                                </button>
                                                <button
                                                    type="button"
                                                    className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                                                    disabled={actionLoadingId === detail.id || detail.assignedToId == null || detail.assignedToId !== myUserId}
                                                    onClick={() => release(detail.id)}
                                                >
                                                    释放
                                                </button>
                                            </>
                                        ) : null}

                                        <button
                                            type="button"
                                            className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                                            disabled={actionLoadingId === detail.id}
                                            onClick={() => requeue(detail.id)}
                                        >
                                            重新自动审核
                                        </button>
                                    </div>
                                </>
                            ) : null}
                        </>
                    ) : (
                        <div className="text-gray-600">无详情数据</div>
                    )}
                </DetailDialog>
            ) : null}

            {riskEditorOpen ? (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-lg w-full max-w-2xl max-h-[85vh] overflow-auto">
                        <div className="flex items-center justify-between px-4 py-3 border-b">
                            <div className="font-semibold">编辑风险标签</div>
                            <button
                                type="button"
                                className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-60"
                                onClick={closeRiskEditor}
                                disabled={riskEditorSaving}
                            >
                                关闭
                            </button>
                        </div>

                        <div className="p-4 space-y-4">
                            {riskEditorError ? <div className="text-sm text-red-700">{riskEditorError}</div> : null}

                            <div className="space-y-2">
                                <div className="text-sm font-medium text-gray-700">已选择</div>
                                {riskSelected.length ? (
                                    <div className="flex flex-wrap gap-2">
                                        {riskSelected.map((t) => (
                                            <span
                                                key={t}
                                                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-amber-200 bg-amber-50 text-sm text-amber-900"
                                                title={t}
                                            >
                                                <span>{t}</span>
                                                <button
                                                    type="button"
                                                    className="text-amber-800 hover:text-amber-950"
                                                    onClick={() => removeRiskSlug(t)}
                                                    disabled={riskEditorSaving}
                                                >
                                                    ×
                                                </button>
                                            </span>
                                        ))}
                                    </div>
                                ) : (
                                    <div className="text-sm text-gray-500">（未选择）</div>
                                )}
                            </div>

                            <div className="space-y-2">
                                <div className="text-sm font-medium text-gray-700">搜索已有风险标签</div>
                                <input
                                    className="w-full rounded border px-3 py-2 border-gray-300"
                                    placeholder="输入关键字搜索 name/slug"
                                    value={riskQuery}
                                    onChange={(e) => setRiskQuery(e.target.value)}
                                    disabled={riskEditorSaving}
                                />
                                {riskEditorLoading ? <div className="text-sm text-gray-500">加载中...</div> : null}
                                {riskOptions.length ? (
                                    <div className="max-h-[260px] overflow-auto border rounded">
                                        {riskOptions.map((t) => (
                                            <button
                                                key={t.id}
                                                type="button"
                                                className="w-full text-left px-3 py-2 hover:bg-gray-50 disabled:opacity-60"
                                                onClick={() => addRiskSlug(t.slug)}
                                                disabled={riskEditorSaving}
                                                title={t.slug}
                                            >
                                                <div className="flex items-center justify-between gap-3">
                                                    <span className="text-sm text-gray-900">{t.name}</span>
                                                    <span className="text-xs text-gray-500">{t.slug}</span>
                                                </div>
                                            </button>
                                        ))}
                                    </div>
                                ) : (
                                    <div className="text-sm text-gray-500">（无匹配结果）</div>
                                )}
                            </div>

                            <div className="space-y-2">
                                <div className="text-sm font-medium text-gray-700">快速新增</div>
                                <div className="flex gap-2">
                                    <input
                                        className="flex-1 rounded border px-3 py-2 border-gray-300"
                                        placeholder="输入新风险标签名称（将自动生成 slug）"
                                        value={riskNewName}
                                        onChange={(e) => setRiskNewName(e.target.value)}
                                        disabled={riskEditorSaving}
                                    />
                                    <button
                                        type="button"
                                        className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                                        onClick={() => void createAndSelectRiskTag()}
                                        disabled={riskEditorSaving || !riskNewName.trim()}
                                    >
                                        新增并选中
                                    </button>
                                </div>
                            </div>

                            <div className="flex justify-end gap-2">
                                <button
                                    type="button"
                                    className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-60"
                                    onClick={closeRiskEditor}
                                    disabled={riskEditorSaving}
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                                    onClick={() => void saveRiskTags()}
                                    disabled={riskEditorSaving}
                                >
                                    {riskEditorSaving ? '保存中...' : '保存'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}

            {postSummaryErrorOpen ? (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] overflow-auto">
                        <div className="flex items-center justify-between px-4 py-3 border-b">
                            <div className="font-semibold">摘要错误详情</div>
                            <button
                                type="button"
                                className="rounded border px-3 py-1 hover:bg-gray-50"
                                onClick={() => setPostSummaryErrorOpen(false)}
                            >
                                关闭
                            </button>
                        </div>
                        <div className="p-4">
                            <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[70vh]">
                                {postSummaryLatestHistory?.errorMessage || '—'}
                            </pre>
                        </div>
                    </div>
                </div>
            ) : null}

            {chunkErrorOpen ? (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] overflow-auto">
                        <div className="flex items-center justify-between px-4 py-3 border-b">
                            <div className="font-semibold">{chunkErrorTitle || '分片错误详情'}</div>
                            <button
                                type="button"
                                className="rounded border px-3 py-1 hover:bg-gray-50"
                                onClick={() => setChunkErrorOpen(false)}
                            >
                                关闭
                            </button>
                        </div>
                        <div className="p-4">
                            <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[70vh]">
                                {chunkErrorText || '—'}
                            </pre>
                        </div>
                    </div>
                </div>
            ) : null}
        </div>
    );
};

export default QueueForm;
