import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchHotPosts, type HotPostDTO, type HotWindow } from '../../../../services/hotService';


export default function HotSidebar({ initialWindow = '24h', limit = 8 }: { initialWindow?: HotWindow; limit?: number }) {
  const [window] = useState<HotWindow>(initialWindow);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<HotPostDTO[]>([]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const page = await fetchHotPosts({ window, page: 1, pageSize: limit });
        setItems(page.content ?? []);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
        setItems([]);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [window, limit]);

  return (
    <div className="bg-white rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">今日热门</h3>
        <Link to="/portal/discover/hot" className="text-sm text-blue-600 hover:underline">
          更多
        </Link>
      </div>

      <div className="mt-3">
        {loading ? <div className="text-sm text-gray-500">加载中...</div> : null}
        {error ? <div className="text-sm text-red-600">{error}</div> : null}
        {!loading && !error && items.length === 0 ? <div className="text-sm text-gray-500">暂无数据</div> : null}

        <ol className="mt-2 space-y-2">
          {items.map((it, idx) => (
            <li key={it.post.id} className="flex gap-2">
              <span className="w-6 text-right text-sm text-gray-500">{idx + 1}.</span>
              <div className="min-w-0 flex-1">
                <Link to={`/portal/posts/detail/${it.post.id}`} className="text-sm hover:text-blue-600 line-clamp-2">
                  {it.post.title}
                </Link>
                <div className="text-xs text-gray-500 mt-0.5">分数：{(it.score ?? 0).toFixed(2)}</div>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
