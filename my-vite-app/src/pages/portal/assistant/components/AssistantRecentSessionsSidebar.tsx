import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import { listQaSessions, type QaSessionDTO } from '../../../../services/qaHistoryService';

export default function AssistantRecentSessionsSidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const activeSessionId = useMemo(() => {
    const raw = searchParams.get('sessionId');
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? n : undefined;
  }, [searchParams]);

  const [recentSessions, setRecentSessions] = useState<QaSessionDTO[]>([]);
  const [recentSessionsLoading, setRecentSessionsLoading] = useState(false);
  const [recentSessionsError, setRecentSessionsError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setRecentSessionsError(null);
    setRecentSessionsLoading(true);
    void (async () => {
      try {
        const res = await listQaSessions(0, 10);
        if (cancelled) return;
        setRecentSessions(res.content || []);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!cancelled) setRecentSessionsError(msg || '加载最近会话失败');
      } finally {
        if (!cancelled) setRecentSessionsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search]);

  return (
    <div className="bg-white rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">最近会话</h3>
        <button
          type="button"
          className="text-sm text-blue-600 hover:underline"
          onClick={() => navigate('/portal/assistant/history')}
        >
          查看全部
        </button>
      </div>

      <div className="mt-3">
        {recentSessionsLoading ? <div className="text-sm text-gray-500">加载中...</div> : null}
        {recentSessionsError ? <div className="text-sm text-red-600">{recentSessionsError}</div> : null}
        {!recentSessionsLoading && !recentSessionsError && recentSessions.length === 0 ? (
          <div className="text-sm text-gray-500">暂无数据</div>
        ) : null}

        <ol className="mt-2 space-y-2">
          {recentSessions.map((s, idx) => {
            const isActive = activeSessionId !== undefined && Number(s.id) === activeSessionId;
            return (
              <li key={s.id} className="flex gap-2">
                <span className="w-6 text-right text-sm text-gray-500">{idx + 1}.</span>
                <button
                  type="button"
                  className="min-w-0 flex-1 text-left"
                  onClick={() => navigate(`/portal/assistant/chat?sessionId=${s.id}`)}
                >
                  <div className={`text-sm line-clamp-2 ${isActive ? 'text-blue-700 font-medium' : 'hover:text-blue-600'}`}>
                    {s.title || `会话 #${s.id}`}
                  </div>
                  {s.lastMessagePreview ? (
                    <div className="text-xs text-gray-500 mt-0.5 line-clamp-2">{s.lastMessagePreview}</div>
                  ) : null}
                  <div className="text-xs text-gray-500 mt-0.5">{new Date(s.lastMessageAt || s.createdAt).toLocaleString()}</div>
                </button>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}

