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

function readString(v: unknown): string | null {
  return typeof v === 'string' && v.trim() ? v : null;
}

function readNumber(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
}

function isAntiSpamHit(v: unknown): boolean {
  if (typeof v === 'boolean') return v;
  return typeof v === 'string' && v.toLowerCase() === 'true';
}

function shouldHideLlmStep(steps: Array<{ stage?: string | null }>): boolean {
  const hasPromptStages = steps.some((it) => {
    const stage = String(it.stage ?? '').toUpperCase();
    return stage === 'TEXT' || stage === 'VISION' || stage === 'JUDGE';
  });
  return hasPromptStages;
}

type ModerationPipelineTracePanelDensity = 'normal' | 'compact';

type ModerationPipelineTracePanelProps = {
  queueId: number;
  density?: ModerationPipelineTracePanelDensity;
};

export const ModerationPipelineTracePanel: React.FC<ModerationPipelineTracePanelProps> = ({ queueId, density = 'normal' }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminModerationPipelineRunDetailDTO | null>(null);

  const dense = density === 'compact';

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
    <div className={dense ? 'border rounded p-2 space-y-1' : 'border rounded p-3 space-y-2'}>
      <div className="flex items-center justify-between">
        <div className={dense ? 'font-medium text-sm leading-tight' : 'font-medium'}>审核流水线追溯</div>
        <button
          type="button"
          className={dense ? 'rounded border px-2 py-0.5 text-xs hover:bg-gray-50' : 'rounded border px-3 py-1 text-sm hover:bg-gray-50'}
          onClick={load}
          disabled={loading}
        >
          刷新
        </button>
      </div>

      {loading ? <div className={dense ? 'text-xs text-gray-600' : 'text-sm text-gray-600'}>加载中…</div> : null}
      {error ? <div className={dense ? 'text-xs text-red-700' : 'text-sm text-red-700'}>{error}</div> : null}

      {!loading && !error && !data?.run ? (
        <div className={dense ? 'text-xs text-gray-600' : 'text-sm text-gray-600'}>暂无流水线记录（可能尚未自动运行）</div>
      ) : null}

      {data?.run ? (
        <div className={dense ? 'text-xs leading-snug grid grid-cols-1 md:grid-cols-2 gap-1' : 'text-sm grid grid-cols-1 md:grid-cols-2 gap-2'}>
          <div>
            <span className="text-gray-500">runId：</span>
            {data.run.id}
          </div>
          <div>
            <span className="text-gray-500">traceId：</span>
            <span className="font-mono">{data.run.traceId || '—'}</span>
          </div>
          <div>
            <span className="text-gray-500">policyVersion：</span>
            <span className="font-mono">{data.run.policyVersion || '—'}</span>
          </div>
          <div>
            <span className="text-gray-500">inputMode：</span>
            {data.run.inputMode || '—'}
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
        <div className={dense ? 'space-y-1' : 'space-y-2'}>
          {(data.steps ?? [])
            .slice()
            .filter((s) => !shouldHideLlmStep(data.steps ?? []) || String(s.stage ?? '').toUpperCase() !== 'LLM')
            .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
            .map((s) => {
              const model = typeof s.details?.model === 'string' ? s.details.model : null;
              const antiSpamHit = s.stage === 'RULE' && isAntiSpamHit(s.details?.antiSpamHit);
              const antiSpamType = readString(s.details?.antiSpamType);
              const antiSpamReason = readString(s.details?.reason);
              const antiSpamActualCount = readNumber(s.details?.actualCount);
              const antiSpamThreshold = readNumber(s.details?.threshold);
              const antiSpamWindowSeconds = readNumber(s.details?.windowSeconds);
              const antiSpamWindowMinutes = readNumber(s.details?.windowMinutes);
              const antiSpamWindowText = antiSpamWindowSeconds != null
                ? `${antiSpamWindowSeconds} 秒`
                : antiSpamWindowMinutes != null
                  ? `${antiSpamWindowMinutes} 分钟`
                  : '—';
              return (
                <details key={s.id} className={dense ? 'border rounded p-1' : 'border rounded p-2'}>
                <summary className={dense ? 'cursor-pointer select-none text-xs flex items-center justify-between gap-2' : 'cursor-pointer select-none text-sm flex items-center justify-between gap-3'}>
                  <span>
                    <b>{s.stage}</b> · {s.decision || '—'} · {formatMs(s.costMs)}
                  </span>
                  <span className="text-gray-500">
                    {model ? `model=${model} ` : ''}
                    score={s.score ?? '—'} threshold={s.threshold ?? '—'}
                  </span>
                </summary>
                {antiSpamHit ? (
                  <div className={dense ? 'mt-1 rounded border border-orange-200 bg-orange-50 p-2 text-xs' : 'mt-2 rounded border border-orange-200 bg-orange-50 p-2 text-xs'}>
                    <div className="font-medium text-orange-800">anti_spam 命中</div>
                    <div className="mt-1 grid grid-cols-1 gap-1 md:grid-cols-2">
                      <div>命中类型：{antiSpamType ?? '—'}</div>
                      <div>原因：{antiSpamReason ?? '—'}</div>
                      <div>实际计数：{antiSpamActualCount ?? '—'}</div>
                      <div>阈值：{antiSpamThreshold ?? '—'}</div>
                      <div>窗口：{antiSpamWindowText}</div>
                    </div>
                  </div>
                ) : null}
                <pre className={dense ? 'mt-1 whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[240px]' : 'mt-2 whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[240px]'}>
                  {safeJson(s.details)}
                </pre>
                </details>
              );
            })}
        </div>
      ) : null}
    </div>
  );
};
