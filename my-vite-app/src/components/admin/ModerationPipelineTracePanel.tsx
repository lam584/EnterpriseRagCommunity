import React, { useEffect, useState } from 'react';
import { adminGetLatestPipelineByQueueId, type AdminModerationPipelineRunDetailDTO } from '../../services/moderationPipelineService';

function formatMs(ms?: number | null): string {
  if (ms == null) return '—';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function safeJson(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}

export const ModerationPipelineTracePanel: React.FC<{ queueId: number }> = ({ queueId }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminModerationPipelineRunDetailDTO | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await adminGetLatestPipelineByQueueId(queueId);
      setData(d);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError(null);

    (async () => {
      try {
        const d = await adminGetLatestPipelineByQueueId(queueId);
        if (cancelled) return;
        setData(d);
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [queueId]);

  return (
    <div className="border rounded p-3 space-y-2">
      <div className="flex items-center justify-between">
        <div className="font-medium">审核流水线追溯</div>
        <button type="button" className="rounded border px-3 py-1 text-sm hover:bg-gray-50" onClick={load} disabled={loading}>
          刷新
        </button>
      </div>

      {loading ? <div className="text-sm text-gray-600">加载中…</div> : null}
      {error ? <div className="text-sm text-red-700">{error}</div> : null}

      {!loading && !error && !data?.run ? <div className="text-sm text-gray-600">暂无流水线记录（可能尚未自动运行）</div> : null}

      {data?.run ? (
        <div className="text-sm grid grid-cols-1 md:grid-cols-2 gap-2">
          <div>
            <span className="text-gray-500">runId：</span>
            {data.run.id}
          </div>
          <div>
            <span className="text-gray-500">traceId：</span>
            <span className="font-mono">{data.run.traceId || '—'}</span>
          </div>
          <div>
            <span className="text-gray-500">状态：</span>
            {data.run.status || '—'}
          </div>
          <div>
            <span className="text-gray-500">最终结论：</span>
            {data.run.finalDecision || '—'}
          </div>
          <div>
            <span className="text-gray-500">总耗时：</span>
            {formatMs(data.run.totalMs)}
          </div>
          <div>
            <span className="text-gray-500">错误：</span>
            {data.run.errorMessage || '—'}
          </div>
        </div>
      ) : null}

      {data?.steps?.length ? (
        <div className="space-y-2">
          {data.steps
            .slice()
            .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
            .map((s) => (
              <details key={s.id} className="border rounded p-2">
                <summary className="cursor-pointer select-none text-sm flex items-center justify-between gap-3">
                  <span>
                    <b>{s.stage}</b> · {s.decision || '—'} · {formatMs(s.costMs)}
                  </span>
                  <span className="text-gray-500">score={s.score ?? '—'} threshold={s.threshold ?? '—'}</span>
                </summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[240px]">{safeJson(s.details)}</pre>
              </details>
            ))}
        </div>
      ) : null}
    </div>
  );
};
