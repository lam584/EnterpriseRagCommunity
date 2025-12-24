import { useCallback, useEffect, useMemo, useState } from 'react';
import type { SpringPage } from '../../../../types/page';
import { listPostsPage, type PostDTO } from '../../../../services/postService';
import PostFeed from '../components/PostFeed';

export default function DiscoverSearchPage() {
  const [q, setQ] = useState('');
  const [submittedQ, setSubmittedQ] = useState('');
  const [page, setPage] = useState<number>(1);
  const [data, setData] = useState<SpringPage<PostDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSearch = useMemo(() => submittedQ.trim().length > 0, [submittedQ]);

  const load = useCallback(
    async (nextPage: number, keyword: string) => {
      const kw = keyword.trim();
      if (!kw) {
        setData(null);
        setError(null);
        setLoading(false);
        setPage(1);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const resp = await listPostsPage({
          keyword: kw,
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
    if (!canSearch) return;
    load(1, submittedQ);
  }, [canSearch, submittedQ, load]);

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">搜索</h3>
        <p className="text-gray-600 text-sm">搜索帖子标题/内容（来自后端 /api/posts?keyword=...）</p>
      </div>

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setSubmittedQ(q);
          setPage(1);
        }}
      >
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="搜索帖子..."
          className="flex-1 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          disabled={loading}
        >
          搜索
        </button>
      </form>

      {!submittedQ.trim() ? <div className="text-sm text-gray-500">输入关键词并点击搜索</div> : null}

      <PostFeed
        page={data}
        loading={loading}
        error={error}
        onRetry={() => load(page, submittedQ)}
        onPrev={() => load(Math.max(1, page - 1), submittedQ)}
        onNext={() => load(page + 1, submittedQ)}
      />
    </div>
  );
}
