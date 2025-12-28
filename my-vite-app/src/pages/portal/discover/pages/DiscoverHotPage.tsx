import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { SpringPage } from '../../../../types/page';
import { fetchHotPosts, type HotPostDTO, type HotWindow } from '../../../../services/hotService';

const tabs: HotWindow[] = ['24h', '7d', 'all'];
const labels: Record<HotWindow, string> = { '24h': '24小时', '7d': '7天', all: '全部' };

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
      const resp = await fetchHotPosts({ window: w, page: p, pageSize: 20 });
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

  const totalPages = useMemo(() => (data?.totalPages ?? 1), [data]);

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

      {loading ? <div className="text-sm text-gray-500">加载中...</div> : null}
      {error ? <div className="text-sm text-red-600">{error}</div> : null}

      <div className="bg-white border rounded-lg divide-y">
        {(data?.content ?? []).map((it, idx) => (
          <div key={it.post.id} className="p-4 flex gap-3">
            <div className="w-10 text-center text-gray-500">{(page - 1) * 20 + idx + 1}</div>
            <div className="flex-1 min-w-0">
              <Link to={`/portal/posts/detail/${it.post.id}`} className="font-medium hover:text-blue-600">
                {it.post.title}
              </Link>
              <div className="mt-1 text-xs text-gray-500 flex gap-4">
                <span>分数：{(it.score ?? 0).toFixed(2)}</span>
                <span>评论：{it.post.commentCount ?? 0}</span>
                <span>点赞：{it.post.reactionCount ?? 0}</span>
                <span>收藏：{it.post.favoriteCount ?? 0}</span>
              </div>
            </div>
          </div>
        ))}
        {!loading && !error && (data?.content?.length ?? 0) === 0 ? (
          <div className="p-6 text-sm text-gray-500">暂无数据</div>
        ) : null}
      </div>

      <div className="flex items-center justify-between">
        <button
          type="button"
          className="px-3 py-2 border rounded disabled:opacity-50"
          disabled={loading || page <= 1}
          onClick={() => load(window, Math.max(1, page - 1))}
        >
          上一页
        </button>
        <div className="text-sm text-gray-600">
          第 {page} / {totalPages} 页
        </div>
        <button
          type="button"
          className="px-3 py-2 border rounded disabled:opacity-50"
          disabled={loading || page >= totalPages}
          onClick={() => load(window, page + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  );
}

