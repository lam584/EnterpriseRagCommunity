import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listBoards, type BoardDTO } from '../../../../services/boardService';
// NOTE: Portal pages shouldn't depend on admin AuthContext (it calls /api/auth/current-admin).
import { deletePost, type PostDTO, type PostStatus, listMyPostsPage } from '../../../../services/postService';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import { getStoredUserId, resolvePortalAuthState } from '../../../../services/portalAuthService';
import type { SpringPage } from '../../../../types/page';
import PostFeed from '../../discover/components/PostFeed';

function formatDateTime(iso?: string) {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}

const STATUS_OPTIONS: Array<{ value: PostStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'ARCHIVED', label: '已归档' },
];

export default function PostsMinePage() {
  const navigate = useNavigate();

  // Current user
  const [authorId, setAuthorId] = useState<number | undefined>(() => getStoredUserId());

  // Boards
  const [boards, setBoards] = useState<BoardDTO[]>([]);

  // Filters
  const [keyword, setKeyword] = useState('');
  const [boardId, setBoardId] = useState<number | ''>('');
  const [status, setStatus] = useState<PostStatus | 'ALL'>('ALL');
  const [createdFrom, setCreatedFrom] = useState('');
  const [createdTo, setCreatedTo] = useState('');

  // Results
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<PostDTO[]>([]);

  // Preview
  const [previewing, setPreviewing] = useState<PostDTO | null>(null);

  const boardOptions = useMemo(() => boards.map((b) => ({ value: b.id, label: `${b.name} (#${b.id})` })), [boards]);

  const loadBoards = async () => {
    try {
      const b = await listBoards();
      setBoards(b);
    } catch {
      // ignore board fetch error; still can render by id
      setBoards([]);
    }
  };

  const canQuery = Boolean(authorId);

  const onQuery = async () => {
    setLoading(true);
    setError(null);
    try {
      const pageResp = await listMyPostsPage({
        keyword: keyword || undefined,
        boardId: boardId === '' ? undefined : Number(boardId),
        status,
        createdFrom: createdFrom || undefined,
        createdTo: createdTo || undefined,
        page: 1,
        pageSize: 1000,
      });
      setItems(pageResp.content ?? []);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '查询失败';
      setError(msg || '查询失败');
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  const onReset = async () => {
    setKeyword('');
    setBoardId('');
    setStatus('ALL');
    setCreatedFrom('');
    setCreatedTo('');
    await onQuery();
  };

  useEffect(() => {
    // Sync login state across tabs; also handles late write after navigation.
    const sync = () => setAuthorId(getStoredUserId());
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);

  useEffect(() => {
    // Server-first auth resolve (shared with后台的 session 登录态)
    const load = async () => {
      const st = await resolvePortalAuthState();
      setAuthorId(st.userId);
    };
    void load();
  }, []);

  useEffect(() => {
    // init
    loadBoards();
  }, []);

  useEffect(() => {
    // enter page -> try default query
    if (!canQuery) {
      setItems([]);
      setError('未获取到当前用户信息，无法加载“我的帖子”。（请先登录或刷新页面重试）');
      return;
    }
    void onQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authorId]);

  // Sort by updatedAt/createdAt desc
  const sorted = useMemo(() => {
    return [...items].sort((a, b) => {
      const ta = new Date(a.updatedAt ?? a.createdAt ?? 0).getTime();
      const tb = new Date(b.updatedAt ?? b.createdAt ?? 0).getTime();
      return tb - ta;
    });
  }, [items]);

  const listPage: SpringPage<PostDTO> | null = useMemo(() => {
    const content = sorted;
    return {
      content,
      totalElements: content.length,
      totalPages: 1,
      size: content.length,
      number: 0,
      first: true,
      last: true,
      empty: content.length === 0,
    };
  }, [sorted]);

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
            {fmt === 'PLAIN' ? (
              <pre className="text-sm whitespace-pre-wrap break-words">{content || ''}</pre>
            ) : fmt === 'HTML' ? (
              <MarkdownPreview markdown={content || ''} />
            ) : (
              <MarkdownPreview markdown={content || ''} />
            )}
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

  const showNoUserHint = !authorId;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold">我的帖子</h3>
          <p className="text-gray-600">只展示当前账号发布/创建过的帖子。支持查询、预览、编辑、删除。</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => navigate('/portal/posts/create')}
            className="px-3 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
          >
            新建帖子
          </button>
          <button
            type="button"
            onClick={() => onQuery()}
            className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50"
            disabled={loading || showNoUserHint}
          >
            刷新
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow p-6">
        <h4 className="text-base font-semibold mb-4">筛选条件</h4>

        <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
          <input
            className="rounded border px-3 py-2 border-gray-300 md:col-span-2"
            placeholder="按标题或内容关键字"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                void onQuery();
              }
            }}
            disabled={loading}
          />

          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={boardId}
            onChange={(e) => setBoardId(e.target.value ? Number(e.target.value) : '')}
            disabled={loading}
          >
            <option value="">全部板块</option>
            {boardOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>

          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={status}
            onChange={(e) => setStatus((e.target.value || 'ALL') as PostStatus | 'ALL')}
            disabled={loading}
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>

          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="date"
            value={createdFrom}
            onChange={(e) => setCreatedFrom(e.target.value)}
            disabled={loading}
          />

          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="date"
            value={createdTo}
            onChange={(e) => setCreatedTo(e.target.value)}
            disabled={loading}
          />

          <div className="flex gap-2 md:col-span-2">
            <button
              type="button"
              className="rounded bg-blue-600 text-white px-4 py-2"
              onClick={() => onQuery()}
              disabled={loading || showNoUserHint}
              title={showNoUserHint ? '未获取到当前用户信息' : ''}
            >
              {loading ? '查询中…' : '查询'}
            </button>
            <button type="button" className="rounded border px-4 py-2" onClick={() => onReset()} disabled={loading}>
              重置
            </button>
          </div>
        </div>
      </div>

      {showNoUserHint ? (
        <div className="text-sm text-gray-600">
          未获取到当前用户信息，无法加载“我的帖子”。（请先登录或刷新页面重试）
        </div>
      ) : null}

      {error ? <div className="p-3 rounded-md border border-red-200 bg-red-50 text-red-700 text-sm">{error}</div> : null}

      {/* List */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between gap-2 mb-4">
          <h4 className="text-base font-semibold">帖子列表</h4>
        </div>

        {listPage ? (
          <PostFeed
            page={listPage}
            loading={loading}
            error={error}
            onRetry={() => onQuery()}
            renderActions={(p) => (
              <>
                <button
                  type="button"
                  className="text-blue-600 hover:underline"
                  onClick={() => setPreviewing(p)}
                  disabled={!p.content}
                  title={!p.content ? '无正文内容' : '查看全文'}
                >
                  查看全文
                </button>
                <button
                  type="button"
                  className="text-gray-700 hover:underline"
                  onClick={() => navigate(`/portal/posts/edit/${p.id}`)}
                >
                  编辑
                </button>
                <button
                  type="button"
                  className="text-red-600 hover:underline"
                  onClick={async () => {
                    const ok = window.confirm(`确定删除帖子《${p.title}》吗？此操作不可恢复。`);
                    if (!ok) return;
                    try {
                      await deletePost(p.id);
                      setItems((prev) => prev.filter((x) => x.id !== p.id));
                    } catch (e: unknown) {
                      const msg = e instanceof Error ? e.message : '删除失败';
                      setError(msg || '删除失败');
                    }
                  }}
                >
                  删除
                </button>
              </>
            )}
          />
        ) : null}

        {!loading && !error && sorted.length === 0 ? (
          <p className="text-sm text-gray-500">暂无数据。可调整条件后点击“查询”。</p>
        ) : null}
      </div>

      {previewing ? <PreviewModal post={previewing} onClose={() => setPreviewing(null)} /> : null}
    </div>
  );
}
