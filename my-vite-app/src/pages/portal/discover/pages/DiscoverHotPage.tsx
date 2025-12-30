import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { SpringPage } from '../../../../types/page';
import { fetchHotPosts, type HotPostDTO, type HotWindow } from '../../../../services/hotService';
import type { PostDTO } from '../../../../services/postService';
import PostFeed from '../components/PostFeed';

const tabs: HotWindow[] = ['24h', '7d', 'all'];
const labels: Record<HotWindow, string> = { '24h': '24小时', '7d': '7天', all: '全部' };

const PAGE_SIZE = 25;

export default function DiscoverHotPage() {
  const [window, setWindow] = useState<HotWindow>('24h');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<SpringPage<HotPostDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (w: HotWindow, p: number) => {
    setLoading(true);
    setError(null);
    try {
      const resp = await fetchHotPosts({ window: w, page: p, pageSize: PAGE_SIZE });
      setData(resp);
      setWindow(w);
      setPage(p);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load('24h', 1);
  }, [load]);

  const mappedPage: SpringPage<PostDTO> | null = useMemo(() => {
    if (!data) return null;
    return {
      ...data,
      content: (data.content ?? []).map((it) => {
        const p = it.post as PostDTO;
        if (typeof p.hotScore !== 'number' && typeof it.score === 'number') {
          return { ...p, hotScore: it.score } as PostDTO;
        }
        return p;
      }),
    } as SpringPage<PostDTO>;
  }, [data]);

  const totalPages = useMemo(() => (data?.totalPages ?? 1), [data]);
  const showPager = useMemo(() => {
    // 只要后端返回 totalPages>1 就显示分页。
    // 不要用 content.length >= PAGE_SIZE 作为条件：后端可能会过滤掉部分帖子，导致第一页不足一页但仍然存在下一页。
    return totalPages > 1;
  }, [totalPages]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold">热榜</h3>
          <p className="text-sm text-gray-600">来自后端 /api/hot?window=24h|7d|all</p>
        </div>
        <Link to="/portal/discover" className="text-sm text-blue-600 hover:underline">
          返回首页
        </Link>
      </div>

      <div className="flex gap-2">
        {tabs.map((t) => (
          <button
            key={t}
            type="button"
            className={`px-3 py-1.5 rounded border text-sm ${window === t ? 'bg-blue-600 text-white border-blue-600' : 'bg-white'}`}
            onClick={() => load(t, 1)}
            disabled={loading}
          >
            {labels[t]}
          </button>
        ))}
      </div>

      <PostFeed
        page={mappedPage}
        loading={loading}
        error={error}
        onRetry={() => load(window, page)}
        onPrev={() => load(window, Math.max(1, page - 1))}
        onNext={() => load(window, page + 1)}
      />

      {/* 兼容：如果后端没给 totalPages（或返回 1），但你仍希望保留原分页行，可改用 mappedPage 的分页条；这里额外保留原分页（showPager）以匹配旧行为 */}
      {showPager ? (
        <div className="flex items-center justify-between">
          <button
            type="button"
            className="px-3 py-2 border rounded disabled:opacity-50"
            disabled={loading || page <= 1}
            onClick={() => load(window, Math.max(1, page - 1))}
          >
            上一页
          </button>
          <div className="text-sm text-gray-600">第 {page} / {totalPages} 页</div>
          <button
            type="button"
            className="px-3 py-2 border rounded disabled:opacity-50"
            disabled={loading || page >= totalPages}
            onClick={() => load(window, page + 1)}
          >
            下一页
          </button>
        </div>
      ) : null}
    </div>
  );
}
