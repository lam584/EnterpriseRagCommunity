import { useCallback, useEffect, useMemo, useState } from 'react';
import type { BoardDTO } from '../../../../services/boardService';
import { listMyModeratedBoards } from '../../../../services/moderatorBoardsService';
import {
  adminApproveModerationQueue,
  adminGetModerationQueueDetail,
  adminListModerationQueue,
  adminRejectModerationQueue,
  adminToHumanModerationQueue,
  type ModerationQueueDetail,
  type ModerationQueueItem,
  type QueueStatus,
} from '../../../../services/moderationQueueService';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';

const statusOptions: Array<{ value: QueueStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待自动审核' },
  { value: 'REVIEWING', label: '审核中' },
  { value: 'HUMAN', label: '待人工审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
];

function statusLabel(status: QueueStatus): string {
  const m: Record<QueueStatus, string> = {
    PENDING: '待自动审核',
    REVIEWING: '审核中',
    HUMAN: '待人工审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  };
  return m[status] ?? status;
}

function statusBadgeClass(status: QueueStatus): string {
  const m: Record<QueueStatus, string> = {
    PENDING: 'bg-gray-100 text-gray-800 ring-gray-200',
    REVIEWING: 'bg-blue-50 text-blue-700 ring-blue-200',
    HUMAN: 'bg-amber-50 text-amber-700 ring-amber-200',
    APPROVED: 'bg-green-50 text-green-700 ring-green-200',
    REJECTED: 'bg-red-50 text-red-700 ring-red-200',
  };
  return m[status] ?? 'bg-gray-100 text-gray-800 ring-gray-200';
}

function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return String(s);
  return d.toLocaleString();
}

export default function ModerationQueuePage() {
  const [boardsLoading, setBoardsLoading] = useState(false);
  const [boardsError, setBoardsError] = useState<string | null>(null);
  const [boards, setBoards] = useState<BoardDTO[]>([]);
  const [boardId, setBoardId] = useState<number | ''>('');

  const [status, setStatus] = useState<QueueStatus | ''>('');
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<ModerationQueueItem[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detail, setDetail] = useState<ModerationQueueDetail | null>(null);
  const [markdownPreviewEnabled, setMarkdownPreviewEnabled] = useState(true);

  const [reason, setReason] = useState('');
  const [actionLoading, setActionLoading] = useState<'approve' | 'reject' | 'toHuman' | null>(null);
  const [rowAction, setRowAction] = useState<{ id: number; type: 'approve' | 'reject' | 'toHuman' } | null>(null);

  const selectedBoard = useMemo(() => {
    if (!boardId) return null;
    return boards.find((b) => b.id === boardId) ?? null;
  }, [boardId, boards]);

  useEffect(() => {
    let mounted = true;
    setBoardsLoading(true);
    setBoardsError(null);
    listMyModeratedBoards()
      .then((list) => {
        if (!mounted) return;
        setBoards(list ?? []);
        if ((list ?? []).length > 0) setBoardId(list[0].id);
      })
      .catch((e) => {
        if (!mounted) return;
        setBoardsError(e instanceof Error ? e.message : String(e));
        setBoards([]);
      })
      .finally(() => {
        if (!mounted) return;
        setBoardsLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  const load = useCallback(async () => {
    if (!boardId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await adminListModerationQueue({
        boardId,
        page,
        pageSize,
        orderBy: 'createdAt',
        sort: 'desc',
        contentType: 'POST',
        status: status || undefined,
      });
      setItems(res.content ?? []);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [boardId, page, pageSize, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError(null);
    setDetail(null);
    setReason('');
    setActionLoading(null);
    setMarkdownPreviewEnabled(true);
    try {
      const d = await adminGetModerationQueueDetail(id);
      setDetail(d);
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setDetail(null);
    setDetailError(null);
    setReason('');
    setActionLoading(null);
    setMarkdownPreviewEnabled(true);
  }, []);

  const approve = useCallback(async () => {
    if (!detail) return;
    const r = reason.trim();
    if (!r) {
      setDetailError('请输入理由');
      return;
    }
    setActionLoading('approve');
    setDetailError(null);
    try {
      await adminApproveModerationQueue(detail.id, r);
      await load();
      closeDetail();
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setActionLoading(null);
    }
  }, [closeDetail, detail, load, reason]);

  const reject = useCallback(async () => {
    if (!detail) return;
    const r = reason.trim();
    if (!r) {
      setDetailError('请输入理由');
      return;
    }
    setActionLoading('reject');
    setDetailError(null);
    try {
      await adminRejectModerationQueue(detail.id, r);
      await load();
      closeDetail();
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setActionLoading(null);
    }
  }, [closeDetail, detail, load, reason]);

  const toHuman = useCallback(async () => {
    if (!detail) return;
    const r = reason.trim();
    if (!r) {
      setDetailError('请输入理由');
      return;
    }
    setActionLoading('toHuman');
    setDetailError(null);
    try {
      await adminToHumanModerationQueue(detail.id, r);
      const d = await adminGetModerationQueueDetail(detail.id);
      setDetail(d);
      setReason('');
      await load();
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setActionLoading(null);
    }
  }, [detail, load, reason]);

  const approveRow = useCallback(
    async (id: number) => {
      const input = window.prompt('确认通过该内容？\n请输入处理原因（必填），取消则不处理：');
      if (input === null) return;
      const r = input.trim();
      if (!r) {
        window.alert('必须填写理由');
        return;
      }
      setRowAction({ id, type: 'approve' });
      setError(null);
      try {
        await adminApproveModerationQueue(id, r);
        if (detail?.id === id) {
          const d = await adminGetModerationQueueDetail(id);
          setDetail(d);
        }
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setRowAction(null);
      }
    },
    [detail?.id, load]
  );

  const rejectRow = useCallback(
    async (id: number) => {
      const input = window.prompt('确认驳回该内容？\n请输入处理原因（必填），取消则不处理：');
      if (input === null) return;
      const r = input.trim();
      if (!r) {
        window.alert('必须填写理由');
        return;
      }
      setRowAction({ id, type: 'reject' });
      setError(null);
      try {
        await adminRejectModerationQueue(id, r);
        if (detail?.id === id) {
          const d = await adminGetModerationQueueDetail(id);
          setDetail(d);
        }
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setRowAction(null);
      }
    },
    [detail?.id, load]
  );

  const toHumanRow = useCallback(
    async (id: number) => {
      const input = window.prompt('确认将该任务进入人工审核？\n请输入处理原因（必填），取消则不处理：');
      if (input === null) return;
      const r = input.trim();
      if (!r) {
        window.alert('必须填写理由');
        return;
      }
      setRowAction({ id, type: 'toHuman' });
      setError(null);
      try {
        await adminToHumanModerationQueue(id, r);
        if (detail?.id === id) {
          const d = await adminGetModerationQueueDetail(id);
          setDetail(d);
        }
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setRowAction(null);
      }
    },
    [detail?.id, load]
  );

  const pageLabel = useMemo(() => {
    const start = totalElements === 0 ? 0 : (page - 1) * pageSize + 1;
    const end = Math.min(page * pageSize, totalElements);
    return `${start}-${end} / ${totalElements}`;
  }, [page, pageSize, totalElements]);

  return (
    <div className="space-y-4">
      <div className="rounded bg-white shadow p-4 space-y-3">
        <div className="flex flex-wrap items-center gap-3 justify-between">
          <div className="space-y-1">
            <div className="text-lg font-semibold text-gray-900">审核队列</div>
            <div className="text-xs text-gray-500">仅显示你负责版块的帖子审核队列</div>
          </div>
          <button
            type="button"
            onClick={() => void load()}
            className="rounded bg-gray-100 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-200 disabled:opacity-60"
            disabled={loading || !boardId}
          >
            刷新
          </button>
        </div>

        {boardsLoading ? <div className="text-sm text-gray-500">加载版块中…</div> : null}
        {boardsError ? <div className="text-sm text-red-600">{boardsError}</div> : null}

        {!boardsLoading && !boardsError && boards.length === 0 ? (
          <div className="text-sm text-gray-600">你目前没有被设置为任何版块的版主。</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="space-y-1">
              <div className="text-xs font-medium text-gray-700">版块</div>
              <select
                value={boardId === '' ? '' : String(boardId)}
                onChange={(e) => {
                  const v = e.target.value;
                  setPage(1);
                  setBoardId(v ? Number(v) : '');
                }}
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                disabled={boardsLoading || loading}
              >
                {boards.map((b) => (
                  <option key={b.id} value={String(b.id)}>
                    {b.name} (id={b.id})
                  </option>
                ))}
              </select>
              {selectedBoard?.description ? <div className="text-xs text-gray-500">{selectedBoard.description}</div> : null}
            </div>
            <div className="space-y-1">
              <div className="text-xs font-medium text-gray-700">状态</div>
              <select
                value={status}
                onChange={(e) => {
                  setPage(1);
                  setStatus((e.target.value as QueueStatus | '') ?? '');
                }}
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                disabled={!boardId || loading}
              >
                {statusOptions.map((o) => (
                  <option key={o.label} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </div>

      <div className="rounded bg-white shadow p-4 space-y-3">
        {error ? <div className="text-sm text-red-600">{error}</div> : null}
        {loading ? <div className="text-sm text-gray-500">加载中…</div> : null}

        {!loading && items.length === 0 ? <div className="text-sm text-gray-600">暂无数据</div> : null}

        {items.length > 0 ? (
          <div className="overflow-x-auto rounded border border-gray-200">
            <table className="w-full min-w-[760px] table-fixed divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="w-16 px-3 py-2 text-left text-xs font-medium text-gray-500 whitespace-nowrap">ID</th>
                  <th className="w-[200px] md:w-[280px] px-3 py-2 text-left text-xs font-medium text-gray-500">标题</th>
                  <th className="w-24 px-3 py-2 text-left text-xs font-medium text-gray-500 whitespace-nowrap">状态</th>
                  <th className="w-36 px-3 py-2 text-left text-xs font-medium text-gray-500 whitespace-nowrap">更新时间</th>
                  <th className="w-[240px] md:w-[300px] px-3 py-2 text-left text-xs font-medium text-gray-500 whitespace-nowrap">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {items.map((it) => (
                  <tr key={it.id}>
                    <td className="w-16 px-3 py-2 text-sm text-gray-700 whitespace-nowrap">{it.id}</td>
                    <td className="w-[200px] md:w-[280px] px-3 py-2 text-sm text-gray-700">
                      <div className="font-medium truncate">{it.summary?.title ?? `contentId=${it.contentId}`}</div>
                      {it.summary?.snippet ? <div className="text-xs text-gray-500 line-clamp-2">{it.summary.snippet}</div> : null}
                    </td>
                    <td className="px-3 py-2 text-sm text-gray-700 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${statusBadgeClass(
                          it.status
                        )}`}
                      >
                        {statusLabel(it.status)}
                      </span>
                    </td>
                    <td className="w-36 px-3 py-2 text-sm text-gray-500 truncate">{formatDateTime(it.updatedAt)}</td>
                    <td className="w-[280px] md:w-[360px] px-3 py-2 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-2">
                        {it.status === 'HUMAN' ? (
                          <>
                            <button
                              type="button"
                              onClick={() => void rejectRow(it.id)}
                              disabled={loading || rowAction?.id === it.id}
                              className="rounded bg-red-600 px-2.5 py-1 text-xs text-white disabled:bg-red-300"
                            >
                              {rowAction?.id === it.id && rowAction.type === 'reject' ? '处理中…' : '驳回'}
                            </button>
                            <button
                              type="button"
                              onClick={() => void approveRow(it.id)}
                              disabled={loading || rowAction?.id === it.id}
                              className="rounded bg-green-600 px-2.5 py-1 text-xs text-white disabled:bg-green-300"
                            >
                              {rowAction?.id === it.id && rowAction.type === 'approve' ? '处理中…' : '通过'}
                            </button>
                          </>
                        ) : null}

                        {it.status === 'APPROVED' || it.status === 'REJECTED' ? (
                          <button
                            type="button"
                            onClick={() => void toHumanRow(it.id)}
                            disabled={loading || rowAction?.id === it.id}
                            className="rounded border border-gray-300 px-2.5 py-1 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                            title="已终态任务切回人工审核（HUMAN），以便再次处理"
                          >
                            {rowAction?.id === it.id && rowAction.type === 'toHuman' ? '处理中…' : '进入人工审核'}
                          </button>
                        ) : null}

                        <button
                          type="button"
                          onClick={() => void openDetail(it.id)}
                          className="rounded border border-gray-300 px-2.5 py-1 text-xs text-gray-700 hover:bg-gray-50 whitespace-nowrap"
                        >
                          查看全文
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {items.length > 0 ? (
          <div className="flex items-center justify-between text-sm">
            <div className="text-gray-600">{pageLabel}</div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-60"
                disabled={page <= 1 || loading}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                上一页
              </button>
              <button
                type="button"
                className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-60"
                disabled={page >= totalPages || loading}
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              >
                下一页
              </button>
            </div>
          </div>
        ) : null}
      </div>

      {detailOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-3xl rounded bg-white shadow-lg">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div className="text-sm font-semibold text-gray-900">审核处理</div>
              <button type="button" onClick={closeDetail} className="text-gray-500 hover:text-gray-800">
                ×
              </button>
            </div>
            <div className="p-4 space-y-3">
              {detailLoading ? <div className="text-sm text-gray-500">加载详情中…</div> : null}
              {detailError ? <div className="text-sm text-red-600">{detailError}</div> : null}
              {detail && !detailLoading ? (
                <>
                  <div className="space-y-1">
                    <div className="text-base font-medium text-gray-900">{detail.post?.title ?? detail.summary?.title ?? '—'}</div>
                    <div className="text-xs text-gray-500">
                      状态：{detail.status} · 阶段：{detail.currentStage} · 创建：{formatDateTime(detail.createdAt)}
                    </div>
                  </div>
                  {detail.post?.content ? (
                    <div className="space-y-2">
                      <label className="inline-flex items-center gap-2 text-xs text-gray-700 select-none">
                        <input
                          type="checkbox"
                          checked={markdownPreviewEnabled}
                          onChange={(e) => setMarkdownPreviewEnabled(e.target.checked)}
                          disabled={actionLoading !== null}
                        />
                        <span>Markdown预览</span>
                      </label>
                      <div className="rounded border border-gray-200 max-h-[280px] overflow-auto">
                        {markdownPreviewEnabled ? (
                          <MarkdownPreview markdown={detail.post.content} className="p-3" />
                        ) : (
                          <div className="p-3 text-sm text-gray-700 whitespace-pre-wrap">{detail.post.content}</div>
                        )}
                      </div>
                    </div>
                  ) : null}
                  <div className="space-y-1">
                    <div className="text-xs font-medium text-gray-700">处理原因（必填）</div>
                    <textarea
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                      rows={3}
                      disabled={actionLoading !== null}
                      placeholder="例如：不符合版规/涉敏内容/已修正可通过…"
                    />
                  </div>
                  <div className="flex items-center justify-end gap-2 pt-1">
                    {detail.status === 'APPROVED' || detail.status === 'REJECTED' ? (
                      <button
                        type="button"
                        onClick={toHuman}
                        disabled={actionLoading !== null || !reason.trim()}
                        className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                        title="已终态任务切回人工审核（HUMAN），以便再次处理"
                      >
                        {actionLoading === 'toHuman' ? '处理中…' : '进入人工审核'}
                      </button>
                    ) : null}
                    <button
                      type="button"
                      onClick={reject}
                      disabled={actionLoading !== null || !reason.trim() || detail.status === 'APPROVED' || detail.status === 'REJECTED'}
                      className="rounded bg-red-600 px-4 py-2 text-sm text-white disabled:bg-red-300"
                    >
                      {actionLoading === 'reject' ? '处理中…' : '驳回'}
                    </button>
                    <button
                      type="button"
                      onClick={approve}
                      disabled={actionLoading !== null || !reason.trim() || detail.status === 'APPROVED' || detail.status === 'REJECTED'}
                      className="rounded bg-green-600 px-4 py-2 text-sm text-white disabled:bg-green-300"
                    >
                      {actionLoading === 'approve' ? '处理中…' : '通过'}
                    </button>
                  </div>
                </>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
