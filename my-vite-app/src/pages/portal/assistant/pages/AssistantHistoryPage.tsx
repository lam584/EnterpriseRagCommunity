import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  deleteQaSession,
  listQaSessions,
  searchQaHistory,
  type QaSearchHitDTO,
  type QaSessionDTO
} from '../../../../services/qaHistoryService';

export default function AssistantHistoryPage() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [sessions, setSessions] = useState<QaSessionDTO[]>([]);
  const [hits, setHits] = useState<QaSearchHitDTO[]>([]);

  const [totalElements, setTotalElements] = useState(0);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const isSearchMode = useMemo(() => q.trim().length > 0, [q]);

  const showPagination = useMemo(() => totalElements > size, [totalElements, size]);
  const hasNextPage = useMemo(() => (page + 1) * size < totalElements, [page, size, totalElements]);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    void (async () => {
      try {
        if (isSearchMode) {
          const res = await searchQaHistory(q.trim(), page, size);
          if (cancelled) return;
          setHits(res.content || []);
          setSessions([]);
          setTotalElements(res.totalElements ?? 0);
        } else {
          const res = await listQaSessions(page, size);
          if (cancelled) return;
          setSessions(res.content || []);
          setHits([]);
          setTotalElements(res.totalElements ?? 0);
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!cancelled) setError(msg || '加载失败');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isSearchMode, q, page, size]);

  function openSession(sessionId: number) {
    navigate(`/portal/assistant/chat?sessionId=${sessionId}`);
  }

  async function onDeleteSession(sessionId: number) {
    if (!window.confirm('确定删除该历史会话吗？删除后不可恢复。')) return;

    try {
      setIsLoading(true);
      setError(null);
      await deleteQaSession(sessionId);

      // 删除后刷新当前页；若当前页被删空，则回退一页再拉。
      const res = await listQaSessions(page, size);
      if ((res.content || []).length === 0 && page > 0) {
        setPage((p) => Math.max(0, p - 1));
        return;
      }
      setSessions(res.content || []);
      setHits([]);
      setTotalElements(res.totalElements ?? 0);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg || '删除失败');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="space-y-3">
      <div>
        <h3 className="text-lg font-semibold">历史</h3>
        <p className="text-gray-600">支持搜索标题与全文，点击即可继续对话。</p>
      </div>

      <div className="flex flex-col md:flex-row md:items-center gap-2">
        <div className="flex items-center gap-2 flex-1">
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder="搜索：标题 / 对话内容..."
            className="w-full border border-gray-300 rounded-md px-3 py-2"
          />
          {q && (
            <button
              type="button"
              className="px-3 py-2 rounded-md bg-gray-100 hover:bg-gray-200 text-sm"
              onClick={() => {
                setQ('');
                setPage(0);
              }}
            >
              清空
            </button>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className="text-sm text-gray-600">每页</div>
          <select
            className="border border-gray-300 rounded-md px-2 py-2 text-sm bg-white"
            value={String(size)}
            disabled={isLoading}
            onChange={(e) => {
              const n = Number(e.target.value);
              setSize(Number.isFinite(n) && n > 0 ? n : 20);
              setPage(0);
            }}
          >
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </div>
      </div>

      {error && <div className="border border-red-200 bg-red-50 text-red-700 rounded-md px-3 py-2 text-sm">{error}</div>}

      <div className="border border-gray-200 rounded-md bg-white">
        {isLoading ? (
          <div className="p-4 text-sm text-gray-500">加载中...</div>
        ) : isSearchMode ? (
          hits.length === 0 ? (
            <div className="p-4 text-sm text-gray-500">没有搜索结果</div>
          ) : (
            <ul className="divide-y">
              {hits.map((h) => (
                <li key={`${h.type}-${h.sessionId}-${h.messageId ?? ''}`} className="p-4 hover:bg-gray-50">
                  <button type="button" className="text-left w-full" onClick={() => openSession(h.sessionId)}>
                    <div className="text-sm font-medium text-gray-900">
                      {h.type === 'SESSION_TITLE' ? '标题命中' : '内容命中'} · Session #{h.sessionId}
                    </div>
                    <div className="mt-1 text-sm text-gray-700 line-clamp-2">{h.snippet}</div>
                    <div className="mt-1 text-xs text-gray-500">{new Date(h.createdAt).toLocaleString()}</div>
                  </button>
                </li>
              ))}
            </ul>
          )
        ) : sessions.length === 0 ? (
          <div className="p-4 text-sm text-gray-500">暂无历史会话</div>
        ) : (
          <ul className="divide-y">
            {sessions.map((s) => (
              <li key={s.id} className="p-4 hover:bg-gray-50">
                <div className="flex items-start justify-between gap-3">
                  <button type="button" className="text-left w-full" onClick={() => openSession(s.id)}>
                    <div className="text-sm font-medium text-gray-900">{s.title || `会话 #${s.id}`}</div>
                    <div className="mt-1 text-xs text-gray-500">{new Date(s.createdAt).toLocaleString()}</div>
                  </button>

                  <button
                    type="button"
                    className="shrink-0 px-3 py-2 rounded-md bg-red-50 hover:bg-red-100 text-red-700 text-sm"
                    onClick={() => void onDeleteSession(s.id)}
                  >
                    删除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {!isLoading && showPagination && (
        <div className="flex items-center justify-between">
          <button
            type="button"
            className="px-3 py-2 rounded-md bg-gray-100 hover:bg-gray-200 text-sm disabled:opacity-50"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            上一页
          </button>
          <div className="text-xs text-gray-500">第 {page + 1} 页</div>
          <button
            type="button"
            className="px-3 py-2 rounded-md bg-gray-100 hover:bg-gray-200 text-sm disabled:opacity-50"
            disabled={!hasNextPage}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </button>
        </div>
      )}
    </div>
  );
}
