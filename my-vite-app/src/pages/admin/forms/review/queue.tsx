// queue.tsx
import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {
    adminApproveModerationQueue,
    adminBackfillModerationQueue,
    adminGetModerationQueueDetail,
    adminSetModerationQueueRiskTags,
    adminListModerationQueue,
    adminRejectModerationQueue,
    adminOverrideApproveModerationQueue,
    adminOverrideRejectModerationQueue,
    adminClaimModerationQueue,
    adminReleaseModerationQueue,
    adminRequeueModerationQueue,
    adminToHumanModerationQueue,
    type ContentType,
    type ModerationCaseType,
    type ModerationQueueBackfillResponse,
    type ModerationQueueDetail,
    type ModerationQueueItem,
    type QueueStatus,
} from '../../../../services/moderationQueueService';
import { getCurrentAdmin } from '../../../../services/authService';
import { ModerationPipelineTracePanel } from '../../../../components/admin/ModerationPipelineTracePanel';
import { createRiskTag, listRiskTagsPage, type RiskTagDTO } from '../../../../services/riskTagService';
import { getPostAiSummary, type PostAiSummaryDTO } from '../../../../services/aiPostSummaryService';
import { getPost } from '../../../../services/postService';
import { adminListPostSummaryHistory, type PostSummaryGenHistoryDTO } from '../../../../services/postSummaryAdminService';

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
    if (caseType === 'REPORT') return contentType === 'POST' ? '举报-帖子' : '举报-评论';
    return contentType === 'POST' ? '帖子' : '评论';
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
        const n = Number(searchParams.get('pageSize') ?? '20');
        return Number.isFinite(n) && n > 0 ? n : 20;
    });

    const [items, setItems] = useState<ModerationQueueItem[]>([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);

    const [detailOpen, setDetailOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detail, setDetail] = useState<ModerationQueueDetail | null>(null);

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

    const [myUserId, setMyUserId] = useState<number | undefined>(undefined);
    const [onlyMine] = useState(() => searchParams.get('onlyMine') === '1');

    const loadSeqRef = useRef(0);

    const parsedTaskId = useMemo(() => {
        const n = Number(taskId);
        if (!taskId.trim()) return undefined;
        return Number.isFinite(n) && n > 0 ? n : undefined;
    }, [taskId]);

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

    const load = useCallback(async () => {
        const seq = ++loadSeqRef.current;
        setLoading(true);
        setError(null);
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
            if (seq !== loadSeqRef.current) return;

            setItems(res.content ?? []);
            setTotalPages(res.totalPages ?? 1);
            setTotalElements(res.totalElements ?? 0);
        } catch (e) {
            if (seq !== loadSeqRef.current) return;
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            if (seq === loadSeqRef.current) setLoading(false);
        }
    }, [page, pageSize, orderBy, sortDir, parsedTaskId, contentType, status, onlyMine, myUserId]);

    useEffect(() => {
        const next = new URLSearchParams();
        const trimmedTaskId = taskId.trim();
        if (trimmedTaskId) next.set('taskId', trimmedTaskId);
        if (contentType) next.set('contentType', contentType);
        if (status) next.set('status', status);
        if (orderBy && orderBy !== 'createdAt') next.set('orderBy', orderBy);
        if (sortDir !== 'desc') next.set('sort', sortDir);
        if (onlyMine) next.set('onlyMine', '1');
        if (page !== 1) next.set('page', String(page));
        if (pageSize !== 20) next.set('pageSize', String(pageSize));
        setSearchParams(next, { replace: true });
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
        setDetailLoading(true);
        setDetail(null);
        try {
            const d = await adminGetModerationQueueDetail(id);
            setDetail(d);
        } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        } finally {
            setDetailLoading(false);
        }
    }, []);

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
        setRiskSelected(detail.riskTags ?? []);
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
            const isOverride = it.status === 'REJECTED';
            const ok = window.confirm(
                it.caseType === 'REPORT'
                    ? (isOverride ? '确认覆核通过（驳回举报并恢复内容，如已被下架）？' : '确认驳回该举报（内容正常）？')
                    : (isOverride ? '确认覆核通过（恢复内容可见）？' : '确认内容审核通过？')
            );
            if (!ok) return;
            setActionLoadingId(it.id);
            setError(null);
            try {
                if (isOverride) {
                    await adminOverrideApproveModerationQueue(it.id);
                } else {
                    await adminApproveModerationQueue(it.id);
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
            const isOverride = it.status === 'APPROVED';
            const reason = window.prompt(it.caseType === 'REPORT' ? '请输入核实举报原因（可选）：' : '请输入驳回原因（可选）：') ?? undefined;
            const ok = window.confirm(
                it.caseType === 'REPORT'
                    ? (isOverride ? '确认覆核驳回（核实举报并下架内容）？' : '确认核实该举报并下架内容？')
                    : (isOverride ? '确认覆核驳回（下架内容）？' : '确认驳回？')
            );
            if (!ok) return;
            setActionLoadingId(it.id);
            setError(null);
            try {
                if (isOverride) {
                    await adminOverrideRejectModerationQueue(it.id, reason);
                } else {
                    await adminRejectModerationQueue(it.id, reason);
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
        const reason = window.prompt('备注（可选）：') ?? undefined;
        setActionLoadingId(id);
        setError(null);
        try {
            await adminRequeueModerationQueue(id, reason);
            // 后端会同步 best-effort 推进 RULE/VEC/LLM；这里稍微延迟刷新一次，提升“立刻推进”的可见性
            await new Promise((r) => setTimeout(r, 300));
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

    const toHuman = useCallback(async (id: number) => {
        const ok = window.confirm('确认将该任务进入人工审核？');
        if (!ok) return;
        const reason = window.prompt('备注（可选）：') ?? undefined;
        setActionLoadingId(id);
        setError(null);
        try {
            await adminToHumanModerationQueue(id, reason);
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
                        onClick={load}
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
                    <option value={30}>每页 35</option>
                    <option value={100}>每页 100</option>
                    <option value={200}>每页 200</option>
                    <option value={500}>每页 500</option>
                </select>
            </div>

            {error ? (
                <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>
            ) : null}

            {/* 列表 */}
            <div className="overflow-auto border rounded">
                <table className="min-w-full text-sm">
                    <thead className="bg-gray-50">
                    <tr>
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
                            <td className="px-3 py-4 text-gray-500" colSpan={8}>
                                {loading ? '加载中…' : '暂无数据'}
                            </td>
                        </tr>
                    ) : (
                        items.map((it) => {
                            const isHumanClaimed = it.status === 'HUMAN' && it.assignedToId != null;
                            const primaryStatus = isHumanClaimed ? 'REVIEWING' : it.status;
                            return (
                                <tr key={it.id} className="border-t">
                                    <td className="px-3 py-2">{it.id}</td>
                                    <td className="px-3 py-2">{typeLabel(it.caseType, it.contentType)}</td>
                                    <td className="px-3 py-2">{it.contentId}</td>
                                    <td className="px-3 py-2">
                                        <div className="font-medium text-gray-900 truncate max-w-[520px]">
                                            {it.summary?.title || '—'}
                                        </div>
                                        <div className="text-gray-500 truncate max-w-[520px]">{it.summary?.snippet || '—'}</div>
                                        {it.riskTags && it.riskTags.length ? (
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
                                        <button
                                            type="button"
                                            className="rounded bg-green-600 text-white px-3 py-1 mr-2 disabled:opacity-60"
                                            onClick={() => approve(it)}
                                            disabled={actionLoadingId === it.id || it.status === 'APPROVED'}
                                        >
                                            {it.caseType === 'REPORT' ? (it.status === 'REJECTED' ? '覆核驳回举报' : '驳回举报') : (it.status === 'REJECTED' ? '覆核通过' : '通过')}
                                        </button>
                                        <button
                                            type="button"
                                            className="rounded bg-red-600 text-white px-3 py-1 mr-2 disabled:opacity-60"
                                            onClick={() => reject(it)}
                                            disabled={actionLoadingId === it.id || it.status === 'REJECTED'}
                                        >
                                            {it.caseType === 'REPORT' ? (it.status === 'APPROVED' ? '覆核核实举报' : '核实举报') : (it.status === 'APPROVED' ? '覆核驳回' : '驳回')}
                                        </button>
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
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] overflow-auto">
                        <div className="flex items-center justify-between px-4 py-3 border-b">
                            <div className="font-semibold">任务详情</div>
                            <button
                                type="button"
                                className="rounded border px-3 py-1 hover:bg-gray-50"
                                onClick={() => setDetailOpen(false)}
                            >
                                关闭
                            </button>
                        </div>

                        <div className="p-4 space-y-3">
                            {detailLoading ? (
                                <div className="text-gray-600">加载中…</div>
                            ) : detail ? (
                                <>
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

                                    <div className="border rounded p-3">
                                        <div className="font-medium mb-1">摘要</div>
                                        <div className="text-sm text-gray-700">标题：{detail.summary?.title || '—'}</div>
                                        <div
                                            className="text-sm text-gray-700">内容：{detail.summary?.snippet || '—'}</div>
                                    </div>

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
                                        {detail.riskTags && detail.riskTags.length ? (
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
                                            <div className="font-medium mb-1">帖子内容</div>
                                            <div
                                                className="text-sm text-gray-700">标题：{detail.post?.title || '—'}</div>
                                            <div
                                                className="text-sm text-gray-500 mb-2">状态：{detail.post?.status || '—'}</div>
                                            <pre
                                                className="whitespace-pre-wrap text-sm bg-gray-50 rounded p-2 overflow-auto max-h-[300px]">{detail.post?.content || ''}</pre>
                                        </div>
                                    ) : (
                                        <div className="border rounded p-3">
                                            <div className="font-medium mb-1">评论内容</div>
                                            <div
                                                className="text-sm text-gray-500 mb-2">所属帖子ID：{detail.comment?.postId ?? '—'}</div>
                                            <pre
                                                className="whitespace-pre-wrap text-sm bg-gray-50 rounded p-2 overflow-auto max-h-[300px]">{detail.comment?.content || ''}</pre>
                                        </div>
                                    )}

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
                                            className="rounded bg-green-600 text-white px-4 py-2 disabled:opacity-60"
                                            onClick={() => approve(detail)}
                                            disabled={actionLoadingId === detail.id || detail.status === 'APPROVED'}
                                        >
                                            {detail.caseType === 'REPORT'
                                                ? (detail.status === 'REJECTED' ? '覆核驳回举报' : '驳回举报')
                                                : (detail.status === 'REJECTED' ? '覆核通过' : '通过')}
                                        </button>
                                        <button
                                            type="button"
                                            className="rounded bg-red-600 text-white px-4 py-2 disabled:opacity-60"
                                            onClick={() => reject(detail)}
                                            disabled={actionLoadingId === detail.id || detail.status === 'REJECTED'}
                                        >
                                            {detail.caseType === 'REPORT'
                                                ? (detail.status === 'APPROVED' ? '覆核核实举报' : '核实举报')
                                                : (detail.status === 'APPROVED' ? '覆核驳回' : '驳回')}
                                        </button>
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
                            ) : (
                                <div className="text-gray-600">无详情数据</div>
                            )}
                        </div>
                    </div>
                </div>
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
        </div>
    );
};

export default QueueForm;
