import { useCallback, useEffect, useState } from 'react';
import type { SpringPage } from '../../../../types/page';
import { listPostsPage, type PostDTO } from '../../../../services/postService';
import PostFeed from '../components/PostFeed';
import HotSidebar from '../components/HotSidebar';

export default function DiscoverHomePage() {
  const [page, setPage] = useState<number>(1);
  const [data, setData] = useState<SpringPage<PostDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number) => {
      setLoading(true);
      setError(null);
      try {
        const resp = await listPostsPage({
          page: nextPage,
          pageSize: 20,
          status: 'PUBLISHED',
          sortBy: 'createdAt',
          sortOrderDirection: 'DESC',
        });
        setData(resp);
        setPage(nextPage);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setLoading(false);
      }
    },
    [setData, setPage]
  );

  useEffect(() => {
    load(1);
  }, [load]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <div className="lg:col-span-2 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">首页</h3>
            <p className="text-gray-600 text-sm">最新帖子信息流（来自后端 /api/posts）</p>
          </div>
          <button
            type="button"
            className="px-3 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
            disabled={loading}
            onClick={() => load(1)}
          >
            刷新
          </button>
        </div>

        <PostFeed
          page={data}
          loading={loading}
          error={error}
          onRetry={() => load(page)}
          onPrev={() => load(Math.max(1, page - 1))}
          onNext={() => load(page + 1)}
        />
      </div>

      <div className="lg:col-span-1">
        <HotSidebar />
      </div>
    </div>
  );
}
