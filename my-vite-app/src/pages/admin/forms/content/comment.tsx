import React, { useEffect, useMemo, useState } from 'react';
import { searchPosts, type PostDTO } from '../../../../services/postService';
import {
  adminListComments,
  adminSetCommentDeleted,
  adminUpdateCommentStatus,
  type CommentAdminDTO,
  type CommentAdminQuery,
  type CommentStatus,
} from '../../../../services/commentService';
import {batchCommentIndexSyncStatus, type IndexSyncStatus} from '../../../../services/retrievalIndexSyncAdminService';
import { useAccess } from '../../../../contexts/AccessContext';
import { renderIndexStatus } from './indexSyncStatusView';

const statuses: { value: CommentStatus; label: string }[] = [
  { value: 'PENDING', label: '待审核' },
  { value: 'VISIBLE', label: '已发布' },
  { value: 'HIDDEN', label: '隐藏' },
  { value: 'REJECTED', label: '已拒绝' },
];

const CommentForm: React.FC = () => {
  const { hasPerm, loading: accessLoading } = useAccess();
  const canRead = hasPerm('admin_comments', 'read');
  const canUpdate = hasPerm('admin_comments', 'update');

  // fuzzy post search (only search results; do NOT preload all posts)
  const [postKw, setPostKw] = useState('');
  const [postCandidates, setPostCandidates] = useState<PostDTO[]>([]);
  const [loadingPostCandidates, setLoadingPostCandidates] = useState(false);
  const [selectedPost, setSelectedPost] = useState<PostDTO | null>(null);

  // server data
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [resp, setResp] = useState<{ content: CommentAdminDTO[]; totalPages: number; totalElements: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [kw, setKw] = useState('');
  const [filterPostId, setFilterPostId] = useState<number | ''>('');
  const [filterAuthorId, setFilterAuthorId] = useState<number | ''>('');
  const [filterAuthorName, setFilterAuthorName] = useState('');
  const [filterStatus, setFilterStatus] = useState<CommentStatus | ''>('');
  const [filterCreatedFrom, setFilterCreatedFrom] = useState('');
  const [filterCreatedTo, setFilterCreatedTo] = useState('');
  const [showDeleted, setShowDeleted] = useState(false);
    const [indexStatusByCommentId, setIndexStatusByCommentId] = useState<Record<number, IndexSyncStatus>>({});
    const [fullContentRow, setFullContentRow] = useState<CommentAdminDTO | null>(null);

  const effectivePostOptions = useMemo(() => {
    if (postKw.trim().length < 2) return [];
    return postCandidates.map(p => ({ value: p.id, label: `${p.title} (#${p.id})` }));
  }, [postCandidates, postKw]);

  useEffect(() => {
    const keyword = postKw.trim();
    // 只在输入>=2时触发搜索；否则清空候选
    if (keyword.length < 2) {
      setPostCandidates([]);
      setLoadingPostCandidates(false);
      return;
    }

    let alive = true;
    const timer = window.setTimeout(async () => {
      setLoadingPostCandidates(true);
      try {
        const res = await searchPosts({ keyword, page: 1, pageSize: 20 });
        if (!alive) return;
        setPostCandidates(res);
      } finally {
        if (alive) setLoadingPostCandidates(false);
      }
    }, 300);

    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [postKw]);
  useEffect(() => {
    if (filterPostId === '') {
      setSelectedPost(null);
    }
  }, [filterPostId]);

  const query: CommentAdminQuery = useMemo(() => {
    const fromIso = filterCreatedFrom ? new Date(filterCreatedFrom).toISOString() : undefined;
    const toIso = filterCreatedTo ? new Date(filterCreatedTo).toISOString() : undefined;

    return {
      page,
      pageSize,
      keyword: kw || undefined,
      postId: filterPostId === '' ? undefined : Number(filterPostId),
      authorId: filterAuthorId === '' ? undefined : Number(filterAuthorId),
      authorName: filterAuthorName.trim() ? filterAuthorName.trim() : undefined,
      createdFrom: fromIso,
      createdTo: toIso,
      status: filterStatus === '' ? undefined : filterStatus,
      isDeleted: showDeleted ? undefined : false,
    };
  }, [page, pageSize, kw, filterPostId, filterAuthorId, filterAuthorName, filterCreatedFrom, filterCreatedTo, filterStatus, showDeleted]);

  const fetchList = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminListComments(query);
      setResp({ content: data.content, totalPages: data.totalPages, totalElements: data.totalElements });
    } catch (e) {
      setResp(null);
      setError(e instanceof Error ? e.message : '获取评论列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (accessLoading) return;
    if (!canRead) return;
    fetchList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessLoading, canRead, query]);

    const items = resp?.content ?? [];

    useEffect(() => {
        if (!canRead || items.length === 0) {
            setIndexStatusByCommentId({});
            return;
        }
        let alive = true;
        (async () => {
            try {
                const ids = items.map((x) => x.id).filter((x): x is number => typeof x === 'number');
                if (ids.length === 0) {
                    if (alive) setIndexStatusByCommentId({});
                    return;
                }
                const rows = await batchCommentIndexSyncStatus(ids);
                if (!alive) return;
                const next: Record<number, IndexSyncStatus> = {};
                for (const r of rows) {
                    next[r.commentId] = r.commentIndex;
                }
                setIndexStatusByCommentId(next);
            } catch {
                if (alive) setIndexStatusByCommentId({});
            }
        })();
        return () => {
            alive = false;
        };
    }, [items, canRead]);

  const changeStatus = async (id: number, next: CommentStatus) => {
    if (!canUpdate) {
      setError('无权限：admin_comments:update');
      return;
    }
    try {
      await adminUpdateCommentStatus(id, next);
      await fetchList();
    } catch (e) {
      setError(e instanceof Error ? e.message : '更新状态失败');
    }
  };

  const toggleDelete = async (id: number, isDeleted: boolean) => {
    if (!canUpdate) {
      setError('无权限：admin_comments:update');
      return;
    }
    try {
      await adminSetCommentDeleted(id, !isDeleted);
      await fetchList();
    } catch (e) {
      setError(e instanceof Error ? e.message : '更新删除状态失败');
    }
  };

  // 用搜索结果/已选中帖子来解析标题（不会触发全量拉取）
  const postTitleById = useMemo(() => {
    const m = new Map<number, string>();
    for (const p of postCandidates) m.set(p.id, p.title);
    if (selectedPost) m.set(selectedPost.id, selectedPost.title);
    return m;
  }, [postCandidates, selectedPost]);

  const postTitleForRow = (c: CommentAdminDTO): string | undefined => {
    return (c.postTitle ?? undefined) || postTitleById.get(c.postId);
  };

  if (accessLoading) {
    return <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600">加载中…</div>;
  }

  if (!canRead) {
    return (
      <div className="bg-white rounded-lg shadow p-6 space-y-2">
        <div className="text-lg font-semibold">403 无权限</div>
        <div className="text-sm text-gray-600">需要权限：admin_comments:read</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Search & filters */}
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">评论管理 - 搜索与审核</h3>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="综合搜索（内容/评论ID/帖子ID/作者ID）"
            value={kw}
            onChange={(e) => { setKw(e.target.value); setPage(1); }}
          />

          {/* 帖子模糊搜索 + 选择 */}
          <div className="md:col-span-2 grid grid-cols-1 md:grid-cols-2 gap-3">
            <input
              className="rounded border px-3 py-2 border-gray-300"
              placeholder="模糊搜索帖子（标题/内容，输入≥2字）"
              value={postKw}
              onChange={(e) => {
                setPostKw(e.target.value);
                setPage(1);
              }}
            />

            <select
              className="rounded border px-3 py-2 border-gray-300"
              value={filterPostId}
              onChange={(e) => {
                const nextId = e.target.value ? Number(e.target.value) : '';
                setFilterPostId(nextId);
                const next = typeof nextId === 'number'
                  ? postCandidates.find(p => p.id === nextId) ?? null
                  : null;
                setSelectedPost(next);
                setPage(1);
              }}
            >
              <option value="">
                {postKw.trim().length >= 2 ? '从搜索结果中选择帖子' : '请输入关键词搜索帖子'}
              </option>
              {(loadingPostCandidates && postKw.trim().length >= 2) ? (
                <option value="">搜索中…</option>
              ) : (
                effectivePostOptions.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))
              )}
            </select>
          </div>

          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="帖子ID（精确）"
            type="number"
            value={filterPostId}
            onChange={(e) => {
              const nextId = e.target.value ? Number(e.target.value) : '';
              setFilterPostId(nextId);
              setSelectedPost(null);
              setPage(1);
            }}
          />

          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="作者ID（可选）"
            type="number"
            value={filterAuthorId}
            onChange={(e) => { setFilterAuthorId(e.target.value ? Number(e.target.value) : ''); setPage(1); }}
          />

          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="作者名（模糊）"
            value={filterAuthorName}
            onChange={(e) => { setFilterAuthorName(e.target.value); setPage(1); }}
          />

          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="datetime-local"
            value={filterCreatedFrom}
            onChange={(e) => { setFilterCreatedFrom(e.target.value); setPage(1); }}
            title="评论创建时间-起"
          />
          <input
            className="rounded border px-3 py-2 border-gray-300"
            type="datetime-local"
            value={filterCreatedTo}
            onChange={(e) => { setFilterCreatedTo(e.target.value); setPage(1); }}
            title="评论创建时间-止"
          />

          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={filterStatus}
            onChange={(e) => { setFilterStatus((e.target.value || '') as CommentStatus | ''); setPage(1); }}
          >
            <option value="">全部状态</option>
            {statuses.map(s => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={showDeleted} onChange={(e) => { setShowDeleted(e.target.checked); setPage(1); }} />
              显示已删除（勾选=查看全部）
            </label>
            <div className="flex gap-2 ml-auto">
              <button
                type="button"
                className="rounded bg-blue-600 text-white px-4 py-2"
                onClick={() => fetchList()}
                disabled={loading}
              >查询</button>
              <button
                type="button"
                className="rounded border px-4 py-2"
                onClick={() => {
                  setKw('');
                  setPostKw('');
                  setPostCandidates([]);
                  setSelectedPost(null);
                  setFilterPostId('');
                  setFilterAuthorId('');
                  setFilterAuthorName('');
                  setFilterCreatedFrom('');
                  setFilterCreatedTo('');
                  setFilterStatus('');
                  setShowDeleted(false);
                  setPage(1);
                }}
                disabled={loading}
              >重置</button>
            </div>
          </div>
        </div>

        {selectedPost ? (
          <p className="text-xs text-gray-500 mt-2">已选择帖子：{selectedPost.title} (#{selectedPost.id})</p>
        ) : null}
        {(postKw.trim().length >= 2 && !loadingPostCandidates && postCandidates.length === 0) ? (
          <p className="text-xs text-gray-500 mt-2">未搜索到匹配的帖子</p>
        ) : null}

        {error ? <p className="text-sm text-red-600 mt-3">{error}</p> : null}
      </div>

      {/* List & moderation */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center gap-3 mb-4">
          <h3 className="text-lg font-semibold">评论列表</h3>
          {loading ? <span className="text-sm text-gray-500">加载中…</span> : null}
          {resp ? <span className="text-sm text-gray-500 ml-auto">共 {resp.totalElements} 条</span> : null}
        </div>

        {!loading && items.length === 0 ? (
          <p className="text-sm text-gray-500">暂无数据</p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="text-left border-b">
                    <th className="py-2 pr-4">ID</th>
                    <th className="py-2 pr-4">帖子</th>
                    <th className="py-2 pr-4">帖子摘要</th>
                    <th className="py-2 pr-4">作者</th>
                    <th className="py-2 pr-4">父评论</th>
                    <th className="py-2 pr-4">评论内容</th>
                      <th className="py-2 pr-4">评论索引</th>
                    <th className="py-2 pr-4">状态</th>
                    <th className="py-2 pr-4">删除</th>
                    <th className="py-2 pr-4">创建时间</th>
                    <th className="py-2 pr-4">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(c => (
                    <tr key={c.id} className="border-b hover:bg-gray-50">
                      <td className="py-2 pr-4">{c.id}</td>
                      <td className="py-2 pr-4 max-w-[260px] truncate" title={postTitleForRow(c) ? `#${c.postId} ${postTitleForRow(c)}` : `#${c.postId}`}>
                        <span className="text-gray-500">#{c.postId}</span>
                        {postTitleForRow(c) ? <span> {postTitleForRow(c)}</span> : null}
                      </td>

                      {/* 新增：帖子摘要 */}
                      <td className="py-2 pr-4 max-w-[360px] truncate" title={c.postExcerpt ?? undefined}>
                        {c.postExcerpt ? <span className="text-gray-600">{c.postExcerpt}</span> : <span className="text-gray-400">-</span>}
                      </td>

                      <td className="py-2 pr-4">
                        <span className="text-gray-500">#{c.authorId}</span>
                        {c.authorName ? <span> {c.authorName}</span> : null}
                      </td>
                      <td className="py-2 pr-4">{c.parentId ? `#${c.parentId}` : '-'}</td>
                      <td className="py-2 pr-4 max-w-[360px] truncate" title={c.content}>{c.content}</td>
                        <td className="py-2 pr-4">{renderIndexStatus(indexStatusByCommentId[c.id])}</td>
                      <td className="py-2 pr-4">
                        <select
                          className="rounded border px-2 py-1 border-gray-300 disabled:bg-gray-100 disabled:text-gray-500"
                          value={c.status}
                          onChange={(e) => changeStatus(c.id, e.target.value as CommentStatus)}
                          disabled={loading || !canUpdate}
                          title={!canUpdate ? '无权限：admin_comments:update' : undefined}
                        >
                          {statuses.map(s => (
                            <option key={s.value} value={s.value}>{s.label}</option>
                          ))}
                        </select>
                      </td>
                      <td className="py-2 pr-4">{c.isDeleted ? '是' : '否'}</td>
                      <td className="py-2 pr-4">{new Date(c.createdAt).toLocaleString()}</td>
                      <td className="py-2 pr-4">
                        <button
                            className="text-blue-600 hover:underline mr-3"
                            onClick={() => setFullContentRow(c)}
                        >查看全文
                        </button>
                          <button
                          className={c.isDeleted ? 'text-green-600 hover:underline' : 'text-red-600 hover:underline'}
                          onClick={() => toggleDelete(c.id, c.isDeleted)}
                          disabled={loading || !canUpdate}
                          title={!canUpdate ? '无权限：admin_comments:update' : undefined}
                        >{c.isDeleted ? '恢复' : '删除'}</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {resp && resp.totalPages > 1 ? (
              <div className="flex items-center justify-between mt-4 text-sm">
                <button
                  className="rounded border px-3 py-1 disabled:opacity-50"
                  disabled={loading || page <= 1}
                  onClick={() => setPage(p => Math.max(1, p - 1))}
                >上一页</button>
                <span>第 {page} / {resp.totalPages} 页</span>
                <button
                  className="rounded border px-3 py-1 disabled:opacity-50"
                  disabled={loading || page >= resp.totalPages}
                  onClick={() => setPage(p => p + 1)}
                >下一页</button>
              </div>
            ) : null}
          </>
        )}
      </div>

        {fullContentRow ? (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
                <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] flex flex-col">
                    <div className="px-5 py-4 border-b flex items-center justify-between">
                        <h3 className="text-lg font-semibold">评论全文 #{fullContentRow.id}</h3>
                        <button type="button" className="text-gray-500 hover:text-gray-700 px-2 py-1"
                                onClick={() => setFullContentRow(null)}>×
                        </button>
                    </div>
                    <div className="p-5 overflow-auto">
                        <pre className="text-sm whitespace-pre-wrap break-words">{fullContentRow.content}</pre>
                    </div>
                    <div className="px-5 py-3 border-t flex justify-end">
                        <button type="button" className="rounded border px-4 py-2"
                                onClick={() => setFullContentRow(null)}>关闭
                        </button>
                    </div>
                </div>
            </div>
        ) : null}
    </div>
  );
};

export default CommentForm;
