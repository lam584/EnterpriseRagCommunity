// post.tsx - 管理员帖子管理（仅搜索与状态变更）
import React, { useEffect, useMemo, useState, lazy, Suspense } from 'react';
import { listBoards, type BoardDTO } from '../../../../services/boardService';
import { type PostDTO, type PostStatus, searchAdminPosts, updatePostStatus } from '../../../../services/postService';
import {batchPostIndexSyncStatus, type IndexSyncStatus} from '../../../../services/retrievalIndexSyncAdminService';
import { useAccess } from '../../../../contexts/AccessContext';
import { renderIndexStatus } from './indexSyncStatusView';

// Lazy-load heavy markdown renderer to avoid blocking the main thread during route/menu switches.
const MarkdownPreview = lazy(() => import('../../../../components/ui/MarkdownPreview'));

// 状态选项（与DB一致）
const STATUS_OPTIONS: Array<{ value: PostStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'ARCHIVED', label: '已归档' },
];

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;

const PostAdminManage: React.FC = () => {
  const { hasPerm, loading: accessLoading } = useAccess();
  const canRead = hasPerm('admin_posts', 'read');
  const canUpdate = hasPerm('admin_moderation_queue', 'action');

  // 基础数据
  const [boards, setBoards] = useState<BoardDTO[]>([]);

  // 过滤条件
  const [keyword, setKeyword] = useState('');
  const [boardId, setBoardId] = useState<number | ''>('');
  const [status, setStatus] = useState<PostStatus | 'ALL'>('ALL');
  const [authorId, setAuthorId] = useState<number | ''>('');
  const [createdFrom, setCreatedFrom] = useState(''); // yyyy-mm-dd
  const [createdTo, setCreatedTo] = useState('');

  // 结果集
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<PostDTO[]>([]);
    const [indexStatusByPostId, setIndexStatusByPostId] = useState<Record<number, {
        post?: IndexSyncStatus;
        attachment?: IndexSyncStatus
    }>>({});

  // 分页（后端 page 从 1 开始）
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(25);

  // 预览弹窗
  const [previewing, setPreviewing] = useState<PostDTO | null>(null);

  // 用于取消上一次查询请求（避免快速切换/重复触发导致请求堆积）
  const [queryAbortController, setQueryAbortController] = useState<AbortController | null>(null);

  // 加载板块
  useEffect(() => {
    (async () => {
      try {
        const [b] = await Promise.all([listBoards()]);
        setBoards(b);
      } finally {
        // do nothing
      }
    })();

    // 组件卸载时取消仍在进行的请求
    return () => {
      queryAbortController?.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const boardOptions = useMemo(() => boards.map(b => ({ value: b.id, label: `${b.name} (#${b.id})` })), [boards]);

  const getErrorMessage = (e: unknown, fallback: string) => {
    if (e instanceof Error) return e.message || fallback;
    if (typeof e === 'object' && e && 'message' in e) {
      const m = (e as { message?: unknown }).message;
      if (typeof m === 'string' && m.trim()) return m;
    }
    return fallback;
  };

  const runQuery = async (nextPage: number, nextPageSize: number = pageSize) => {
    // 取消上一请求
    queryAbortController?.abort();
    const ctrl = new AbortController();
    setQueryAbortController(ctrl);

    setLoading(true);
    try {
      const rs = await searchAdminPosts(
        {
          keyword: keyword || undefined,
          boardId: boardId === '' ? undefined : Number(boardId),
          status,
          authorId: authorId === '' ? undefined : Number(authorId),
          createdFrom: createdFrom || undefined,
          createdTo: createdTo || undefined,
          page: nextPage,
          pageSize: nextPageSize,
        },
        { signal: ctrl.signal },
      );
      setItems(rs);
      setPage(nextPage);
    } catch (e: unknown) {
      // 如果是主动取消，不提示
      if (e instanceof DOMException && e.name === 'AbortError') return;
      alert(getErrorMessage(e, '查询失败（请确认已登录且后端服务可用）'));
    } finally {
      setLoading(false);
    }
  };

  const onQuery = async () => {
    await runQuery(1);
  };

  const onReset = async () => {
    setKeyword('');
    setBoardId('');
    setStatus('ALL');
    setAuthorId('');
    setCreatedFrom('');
    setCreatedTo('');
    // 注意：setState 是异步的，这里直接用“重置后的默认值”重新查询
    queryAbortController?.abort();
    const ctrl = new AbortController();
    setQueryAbortController(ctrl);

    setLoading(true);
    try {
      const rs = await searchAdminPosts({ status: 'ALL', page: 1, pageSize }, { signal: ctrl.signal });
      setItems(rs);
      setPage(1);
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
      alert(getErrorMessage(e, '查询失败（请确认已登录且后端服务可用）'));
    } finally {
      setLoading(false);
    }
  };

  // 首次进入页面加载默认数据（只有具备 read 权限时才触发）
  useEffect(() => {
    if (accessLoading) return;
    if (!canRead) return;
    void runQuery(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessLoading, canRead]);

    useEffect(() => {
        if (!canRead || items.length === 0) {
            setIndexStatusByPostId({});
            return;
        }
        let alive = true;
        (async () => {
            try {
                const postIds = items.map((x) => x.id).filter((x): x is number => typeof x === 'number');
                if (postIds.length === 0) {
                    if (alive) setIndexStatusByPostId({});
                    return;
                }
                const rows = await batchPostIndexSyncStatus(postIds);
                if (!alive) return;
                const next: Record<number, { post?: IndexSyncStatus; attachment?: IndexSyncStatus }> = {};
                for (const r of rows) {
                    next[r.postId] = {post: r.postIndex, attachment: r.attachmentIndex};
                }
                setIndexStatusByPostId(next);
            } catch {
                if (alive) setIndexStatusByPostId({});
            }
        })();
        return () => {
            alive = false;
        };
    }, [items, canRead]);

  const confirmAndGetReason = (next: PostStatus): string | undefined => {
    let needReason = false;
    let actionLabel = '';
    switch (next) {
      case 'REJECTED': actionLabel = '驳回'; needReason = true; break;
      case 'ARCHIVED': actionLabel = '归档'; break;
      case 'PUBLISHED': actionLabel = '发布'; break;
      case 'PENDING': actionLabel = '标记为待审核'; break;
      case 'DRAFT': actionLabel = '标记为草稿'; break;
    }
    const ok = window.confirm(`确定要${actionLabel}该帖子吗？`);
    if (!ok) return undefined;
    if (needReason) {
      const reason = window.prompt('请输入原因（可选）：') ?? '';
      return reason.trim();
    }
    return '';
  };

  const changeStatus = async (id: number, next: PostStatus) => {
    if (!canUpdate) {
      alert('无权限：需要 admin_moderation_queue:action');
      return;
    }
    const reason = confirmAndGetReason(next);
    if (reason === undefined) return; // 用户取消
    // 说明：后端当前状态更新接口只接收 status 字段；reason 作为后续可扩展字段，暂不上传。
    try {
      const updated = await updatePostStatus(id, next);
      setItems(prev => prev.map(p => (p.id === id ? updated : p)));
    } catch (e: unknown) {
      alert(getErrorMessage(e, '更新状态失败'));
    }
  };

  const formatDateTime = (iso?: string) => {
    if (!iso) return '-';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
  };

  const toSummary = (post: PostDTO, maxLen = 160) => {
    const raw = (post.content ?? '').trim();
    if (!raw) return '-';

    // Keep it simple: collapse whitespace; for markdown it still reads fine as a snippet.
    const compact = raw
      .replace(/\r\n/g, '\n')
      .replace(/\s+/g, ' ')
      .trim();

    if (compact.length <= maxLen) return compact;
    return `${compact.slice(0, maxLen)}…`;
  };

  const PreviewModal: React.FC<{ post: PostDTO; onClose: () => void }> = ({ post, onClose }) => {
    const content = post.content ?? '';
    const fmt = post.contentFormat ?? 'MARKDOWN';

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
        <div className="bg-white rounded-lg shadow-lg w-full max-w-4xl max-h-[85vh] flex flex-col">
          <div className="px-5 py-4 border-b flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="text-lg font-semibold truncate" title={post.title}>
                {post.title}
              </h3>
              <div className="mt-1 text-xs text-gray-500 space-x-2">
                <span>#{post.id}</span>
                <span>{post.authorName ?? (post.authorId ? `作者#${post.authorId}` : '未知作者')}</span>
                <span>{post.boardName ?? (post.boardId ? `板块#${post.boardId}` : '')}</span>
                <span>{formatDateTime(post.createdAt)}</span>
              </div>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700 px-2 py-1"
              aria-label="关闭预览"
            >
              ×
            </button>
          </div>

          <div className="p-5 overflow-auto">
            <Suspense fallback={<div className="text-sm text-gray-600">正在加载预览组件…</div>}>
              {fmt === 'PLAIN' ? (
                <pre className="text-sm whitespace-pre-wrap break-words">{content || ''}</pre>
              ) : (
                // For safety, render as markdown (HTML inside will be sanitized by MarkdownPreview).
                <MarkdownPreview markdown={content || ''} />
              )}
            </Suspense>
          </div>

          <div className="px-5 py-3 border-t flex justify-end">
            <button type="button" className="rounded border px-4 py-2" onClick={onClose}>
              关闭
            </button>
          </div>
        </div>
      </div>
    );
  };

  if (accessLoading) {
    return <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600">加载中…</div>;
  }

  // 页面权限与接口权限统一：该页面直接以 admin_posts:* 为准。
  if (!canRead) {
    return (
      <div className="bg-white rounded-lg shadow p-6 space-y-2">
        <div className="text-lg font-semibold">403 无权限</div>
        <div className="text-sm text-gray-600">需要权限：admin_posts:read</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 搜索条件 */}
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">帖子管理 - 搜索与状态管理</h3>
        <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
          <input
            className="rounded border px-3 py-2 border-gray-300 md:col-span-2"
            placeholder="按标题或内容关键字"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={boardId}
            onChange={(e) => setBoardId(e.target.value ? Number(e.target.value) : '')}
          >
            <option value="">全部板块</option>
            {boardOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={status}
            onChange={(e) => setStatus((e.target.value || 'ALL') as PostStatus | 'ALL')}
          >
            <option value="ALL">全部状态</option>
            {STATUS_OPTIONS.map(s => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="作者ID（可选）"
            type="number"
            value={authorId}
            onChange={(e) => setAuthorId(e.target.value ? Number(e.target.value) : '')}
          />
          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="date"
            value={createdFrom}
            onChange={(e) => setCreatedFrom(e.target.value)}
          />
          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="date"
            value={createdTo}
            onChange={(e) => setCreatedTo(e.target.value)}
          />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <button
            type="button"
            className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-50"
            onClick={onQuery}
            disabled={loading}
          >
            查询
          </button>
          <button
            type="button"
            className="rounded border px-4 py-2 disabled:opacity-50"
            onClick={onReset}
            disabled={loading}
          >
            重置
          </button>
        </div>
      </div>

      {/* 结果列表 */}
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">帖子列表</h3>
        {items.length === 0 ? (
          <p className="text-sm text-gray-500">暂无数据。可调整条件后点击“查询”。</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2 pr-4">ID</th>
                  <th className="py-2 pr-4">标题</th>
                  <th className="py-2 pr-4">摘要</th>
                  <th className="py-2 pr-4">板块</th>
                  <th className="py-2 pr-4">作者</th>
                  <th className="py-2 pr-4">状态</th>
                    <th className="py-2 pr-4">帖子索引</th>
                  <th className="py-2 pr-4">创建时间</th>
                  <th className="py-2 pr-4">操作</th>
                </tr>
              </thead>
              <tbody>
                {items.map(p => (
                  <tr key={p.id} className="border-b hover:bg-gray-50">
                    <td className="py-2 pr-4">{p.id}</td>
                    <td className="py-2 pr-4 max-w-[260px] truncate" title={p.title}>{p.title}</td>
                    <td className="py-2 pr-4 max-w-[420px]">
                      <div className="truncate" title={(p.content ?? '').trim() || ''}>
                        {toSummary(p)}
                      </div>
                    </td>
                    <td className="py-2 pr-4">{boards.find(b => b.id === p.boardId)?.name ?? p.boardName ?? p.boardId}</td>
                    <td className="py-2 pr-4">{p.authorName ?? (p.authorId ? `#${p.authorId}` : '-')}</td>
                    <td className="py-2 pr-4">
                      <select
                        className="rounded border px-2 py-1 border-gray-300 disabled:bg-gray-100 disabled:text-gray-500"
                        value={p.status ?? 'DRAFT'}
                        disabled={!canUpdate}
                        title={!canUpdate ? '无权限：admin_posts:update' : undefined}
                        onChange={(e) => changeStatus(p.id, e.target.value as PostStatus)}
                      >
                        {STATUS_OPTIONS.map(s => (
                          <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                      </select>
                    </td>
                      <td className="py-2 pr-4">{renderIndexStatus(indexStatusByPostId[p.id]?.post)}</td>
                    <td className="py-2 pr-4">{formatDateTime(p.createdAt)}</td>
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        className="text-blue-600 hover:underline"
                        onClick={() => setPreviewing(p)}
                        disabled={!p.content}
                        title={!p.content ? '无正文内容' : '查看全文'}
                      >
                        查看全文
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="mt-4 flex w-full items-center justify-end gap-2 text-sm">
            <span className="text-gray-500">每页</span>
            <select
              className="rounded border px-2 py-1 border-gray-300 disabled:bg-gray-100 disabled:text-gray-500"
              value={pageSize}
              disabled={loading}
              onChange={(e) => {
                const next = Number(e.target.value) as (typeof PAGE_SIZE_OPTIONS)[number];
                setPageSize(next);
                void runQuery(1, next);
              }}
            >
              {PAGE_SIZE_OPTIONS.map(n => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
            <button
              type="button"
              className="rounded border px-3 py-1 disabled:opacity-50"
              onClick={() => runQuery(Math.max(1, page - 1))}
              disabled={loading || page <= 1}
            >
              上一页
            </button>
            <span className="text-gray-600">第 {page} 页</span>
            <button
              type="button"
              className="rounded border px-3 py-1 disabled:opacity-50"
              onClick={() => runQuery(page + 1)}
              disabled={loading || items.length < pageSize}
              title={items.length < pageSize ? '当前页数量不足，可能没有下一页' : undefined}
            >
              下一页
            </button>
          </div>
      </div>

      {previewing ? <PreviewModal post={previewing} onClose={() => setPreviewing(null)} /> : null}
    </div>
  );
};

export default PostAdminManage;
