import { useEffect, useState } from 'react';
import PostFeed from '../../discover/components/PostFeed';
import type { SpringPage } from '../../../../types/page';
import { deletePostFavorite, listBookmarkedPostsPage, type PostDTO } from '../../../../services/postService';

export default function PostsBookmarksPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<SpringPage<PostDTO> | null>(null);
  const [pageNum, setPageNum] = useState<number>(1); // backend expects 1-based
  const [pageSize] = useState<number>(15);

  const load = async (nextPageNum = pageNum) => {
    setLoading(true);
    setError(null);
    try {
      const rs = await listBookmarkedPostsPage({
        page: nextPageNum,
        pageSize,
      });
      setPage(rs);
      setPageNum(nextPageNum);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setError(msg || '加载失败');
      setPage(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onCancelBookmark = async (post: PostDTO) => {
    // 这里是“收藏管理”页面：点一次就是取消收藏
    try {
      // 乐观更新：先从列表移除，失败则回滚
      const prev = page;
      if (prev) {
        setPage({
          ...prev,
          content: prev.content.filter((x) => x.id !== post.id),
          totalElements: Math.max(0, (prev.totalElements ?? prev.content.length) - 1),
        });
      }

      const resp = await deletePostFavorite(post.id);
      if (resp === null) {
        throw new Error('取消收藏失败');
      }

      // 若本页被清空且不是第一页，自动回退一页重载
      const afterCount = (page?.content?.length ?? 0) - 1;
      if (afterCount <= 0 && pageNum > 1) {
        void load(pageNum - 1);
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '取消收藏失败';
      setError(msg || '取消收藏失败');
      // 回滚：重新加载当前页更简单可靠
      void load(pageNum);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold">收藏</h3>
          <p className="text-gray-600">这里展示你收藏过的帖子，可在列表中一键取消收藏。</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => load(1)}
            className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50"
            disabled={loading}
          >
            刷新
          </button>
        </div>
      </div>

      {/* List */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between gap-2 mb-4">
          <h4 className="text-base font-semibold">收藏的帖子</h4>
          <div className="text-xs text-gray-500">{page ? `共 ${page.totalElements ?? 0} 条` : ''}</div>
        </div>

        <PostFeed
          page={page}
          loading={loading}
          error={error}
          onRetry={() => load(pageNum)}
          onPrev={() => {
            if (loading) return;
            const prev = Math.max(1, pageNum - 1);
            void load(prev);
          }}
          onNext={() => {
            if (loading) return;
            void load(pageNum + 1);
          }}
          renderActions={(p) => (
            <button
              type="button"
              className="text-red-600 hover:underline"
              onClick={async () => {
                const ok = window.confirm(`确定取消收藏《${p.title}》吗？`);
                if (!ok) return;
                await onCancelBookmark(p);
              }}
            >
              取消收藏
            </button>
          )}
        />

        {!loading && !error && (page?.content?.length ?? 0) === 0 ? (
          <p className="mt-3 text-sm text-gray-500">暂无收藏。你可以在帖子卡片右下角点“收藏”把喜欢的内容收进来。</p>
        ) : null}
      </div>
    </div>
  );
}
