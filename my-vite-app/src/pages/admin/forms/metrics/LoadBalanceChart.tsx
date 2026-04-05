import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts';
import { parseTimestampMs } from './metricsTimeUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

type LoadBalancePoint = { tsMs: number | null; qps: number; avgMs: number; errorRate: number; throttled429Rate: number; p95Ms: number };
type LoadBalanceRow = {
  key: string;
  label: string;
  qps: number;
  avgMs: number;
  errorRate: number;
  throttled429Rate: number;
  p95Ms: number;
  points: LoadBalancePoint[];
};

type RangeKey = '15m' | '30m' | '1h' | '2h' | '6h' | '12h' | '24h' | '7d';

const RANGE_OPTIONS: { key: RangeKey; label: string; seconds: number }[] = [
  { key: '15m', label: '近 15 分钟', seconds: 15 * 60 },
  { key: '30m', label: '近 30 分钟', seconds: 30 * 60 },
  { key: '1h', label: '近 1 小时', seconds: 1 * 3600 },
  { key: '2h', label: '近 2 小时', seconds: 2 * 3600 },
  { key: '6h', label: '近 6 小时', seconds: 6 * 3600 },
  { key: '12h', label: '近 12 小时', seconds: 12 * 3600 },
  { key: '24h', label: '近 24 小时', seconds: 24 * 3600 },
  { key: '7d', label: '近 7 天', seconds: 7 * 24 * 3600 },
];

const LOAD_BALANCE_CACHE_TTL_MS = 24 * 3600_000;
type LoadBalanceCacheV1 = { v: 1; savedAtMs: number; range: RangeKey; rows: LoadBalanceRow[] };

function loadBalanceCacheKey(range: RangeKey): string {
  return `admin.metrics.loadBalance.${range}`;
}

function loadLoadBalanceCache(range: RangeKey): LoadBalanceRow[] | null {
  try {
    const raw = window.localStorage.getItem(loadBalanceCacheKey(range));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LoadBalanceCacheV1;
    if (!parsed || typeof parsed !== 'object' || parsed.v !== 1) return null;
    if (parsed.range !== range) return null;
    if (typeof parsed.savedAtMs === 'number' && Date.now() - parsed.savedAtMs > LOAD_BALANCE_CACHE_TTL_MS) return null;
    if (!Array.isArray(parsed.rows)) return null;
    return parsed.rows as LoadBalanceRow[];
  } catch {
    return null;
  }
}

function saveLoadBalanceCache(range: RangeKey, rows: LoadBalanceRow[]): void {
  try {
    const data: LoadBalanceCacheV1 = { v: 1, savedAtMs: Date.now(), range, rows };
    window.localStorage.setItem(loadBalanceCacheKey(range), JSON.stringify(data));
  } catch {
  }
}

function median(values: number[]): number {
  const list = values.filter((x) => Number.isFinite(x)).slice();
  if (!list.length) return 0;
  list.sort((a, b) => a - b);
  const mid = Math.floor(list.length / 2);
  if (list.length % 2 === 0) return (list[mid - 1] + list[mid]) / 2;
  return list[mid];
}

function stddev(values: number[]): number {
  const list = values.filter((x) => Number.isFinite(x));
  if (list.length <= 1) return 0;
  const mean = list.reduce((a, b) => a + b, 0) / list.length;
  const v = list.reduce((a, x) => a + (x - mean) * (x - mean), 0) / list.length;
  return Math.sqrt(v);
}

function formatPercent(x: number, digits = 1): string {
  if (!Number.isFinite(x)) return '-';
  return `${(x * 100).toFixed(digits)}%`;
}

function normalizeLoadBalanceRows(input: unknown, rangeSecondsIn: number): LoadBalanceRow[] {
  const list: any[] = Array.isArray(input)
    ? (input as any[])
    : (input && typeof input === 'object' && 'models' in (input as any) && Array.isArray((input as any).models)
        ? ((input as any).models as any[])
        : (input && typeof input === 'object' && 'items' in (input as any) && Array.isArray((input as any).items)
            ? ((input as any).items as any[])
            : []));

  const rangeSeconds = Math.max(1, Math.floor(rangeSecondsIn));
  const rows: LoadBalanceRow[] = [];

  for (const item of list) {
    const providerId = String(item?.providerId ?? item?.provider ?? '').trim();
    const modelName = String(item?.modelName ?? item?.model ?? item?.name ?? '').trim();
    const key = providerId ? `${providerId}|${modelName}` : modelName;
    if (!modelName) continue;

    let qps = Number(item?.qps ?? item?.requestsPerSec ?? item?.qpsAvg ?? NaN);
    if (!Number.isFinite(qps)) {
      const count = Number(item?.count ?? item?.calls ?? item?.totalCalls ?? item?.requests ?? NaN);
      if (Number.isFinite(count)) qps = count / rangeSeconds;
    }
    if (!Number.isFinite(qps)) qps = 0;

    let avgMs = Number(item?.avgResponseMs ?? item?.avgLatencyMs ?? item?.avgRtMs ?? item?.avgMs ?? NaN);
    if (!Number.isFinite(avgMs)) avgMs = 0;

    const totalCount = Number(item?.count ?? item?.calls ?? item?.totalCalls ?? item?.requests ?? NaN);
    const errCount = Number(item?.errorCount ?? item?.failCount ?? NaN);
    const throttled429Count = Number(item?.throttled429Count ?? item?.rateLimitedCount ?? NaN);

    let errorRate = Number(item?.errorRate ?? item?.failRate ?? NaN);
    if (!Number.isFinite(errorRate) && Number.isFinite(totalCount) && totalCount > 0 && Number.isFinite(errCount)) errorRate = errCount / totalCount;
    if (!Number.isFinite(errorRate)) errorRate = 0;

    let throttled429Rate = Number(item?.throttled429Rate ?? item?.rateLimitedRate ?? NaN);
    if (!Number.isFinite(throttled429Rate) && Number.isFinite(totalCount) && totalCount > 0 && Number.isFinite(throttled429Count)) throttled429Rate = throttled429Count / totalCount;
    if (!Number.isFinite(throttled429Rate)) throttled429Rate = 0;

    let p95Ms = Number(item?.p95ResponseMs ?? item?.p95LatencyMs ?? item?.p95Ms ?? NaN);
    if (!Number.isFinite(p95Ms)) p95Ms = 0;

    const pointsIn: any[] = Array.isArray(item?.points) ? item.points : Array.isArray(item?.samples) ? item.samples : Array.isArray(item?.series) ? item.series : [];
    const points: LoadBalancePoint[] = pointsIn
      .map((p) => {
        const tsMs = parseTimestampMs(p?.timestamp ?? p?.time ?? p?.ts);
        let pqps = Number(p?.qps ?? p?.requestsPerSec ?? NaN);
        if (!Number.isFinite(pqps)) {
          const c = Number(p?.count ?? p?.calls ?? NaN);
          if (Number.isFinite(c)) pqps = c;
        }
        if (!Number.isFinite(pqps)) pqps = 0;
        let pms = Number(p?.avgResponseMs ?? p?.avgLatencyMs ?? p?.avgRtMs ?? p?.avgMs ?? NaN);
        if (!Number.isFinite(pms)) pms = 0;
        const pCount = Number(p?.count ?? p?.calls ?? NaN);
        const pErrCount = Number(p?.errorCount ?? p?.failCount ?? NaN);
        const pThrottled = Number(p?.throttled429Count ?? p?.rateLimitedCount ?? NaN);
        let pErrorRate = Number(p?.errorRate ?? NaN);
        if (!Number.isFinite(pErrorRate) && Number.isFinite(pCount) && pCount > 0 && Number.isFinite(pErrCount)) pErrorRate = pErrCount / pCount;
        if (!Number.isFinite(pErrorRate)) pErrorRate = 0;
        let pThrottledRate = Number(p?.throttled429Rate ?? NaN);
        if (!Number.isFinite(pThrottledRate) && Number.isFinite(pCount) && pCount > 0 && Number.isFinite(pThrottled)) pThrottledRate = pThrottled / pCount;
        if (!Number.isFinite(pThrottledRate)) pThrottledRate = 0;
        let p95 = Number(p?.p95ResponseMs ?? p?.p95LatencyMs ?? p?.p95Ms ?? NaN);
        if (!Number.isFinite(p95)) p95 = 0;
        return { tsMs, qps: pqps, avgMs: pms, errorRate: pErrorRate, throttled429Rate: pThrottledRate, p95Ms: p95 };
      })
      .sort((a, b) => {
        if (a.tsMs == null && b.tsMs == null) return 0;
        if (a.tsMs == null) return -1;
        if (b.tsMs == null) return 1;
        return a.tsMs - b.tsMs;
      });

    rows.push({
      key,
      label: providerId ? `${modelName} (${providerId})` : modelName,
      qps,
      avgMs,
      errorRate,
      throttled429Rate,
      p95Ms,
      points,
    });
  }

  rows.sort((a, b) => b.qps - a.qps);
  return rows;
}

function computeSlowStreak(points: LoadBalancePoint[]): number {
  if (!points.length) return 0;
  let streak = 0;
  for (let i = points.length - 1; i >= 0; i--) {
    const x = points[i];
    if (!Number.isFinite(x.avgMs) || x.avgMs <= 1000) break;
    streak++;
  }
  return streak;
}

const LoadBalanceControls = React.memo(function LoadBalanceControls({
  range,
  loading,
  onChangeRange,
}: {
  range: RangeKey;
  loading: boolean;
  onChangeRange: (next: RangeKey) => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div>
        <div className="text-sm font-semibold">LLM 调用负载均衡</div>
        <div className="text-xs text-gray-600">按模型展示近时段调用量（QPS）、平均响应与 P95</div>
      </div>
      <div className="flex items-center gap-2">
        <div className="flex flex-wrap rounded border overflow-hidden">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.key}
              type="button"
              className={[
                'px-3 py-2 text-xs',
                range === opt.key ? 'bg-blue-600 text-white' : 'bg-white hover:bg-gray-50 text-gray-700',
              ].join(' ')}
              onClick={() => onChangeRange(opt.key)}
              disabled={loading}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
});

export const LoadBalanceChart: React.FC<{ autoRefreshIntervalMs?: number; suspended?: boolean }> = ({ autoRefreshIntervalMs, suspended }) => {
  const [range, setRange] = useState<RangeKey>('1h');
  const rangeSeconds = useMemo(() => RANGE_OPTIONS.find((x) => x.key === range)?.seconds ?? 3600, [range]);
  const [rows, setRows] = useState<LoadBalanceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlightRef = useRef(false);

  const elRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<echarts.EChartsType | null>(null);

  useEffect(() => {
    const cached = loadLoadBalanceCache(range);
    if (cached && cached.length) {
      setRows(cached);
      setError(null);
      return;
    }
    setRows([]);
  }, [range]);

  const load = useCallback(async (opts?: { silent?: boolean }) => {
    const silent = !!opts?.silent;
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const tryFetch = async (path: string) => {
        const res = await fetch(apiUrl(path), { method: 'GET', credentials: 'include' });
        const data: unknown = await res.json().catch(() => ({}));
        return { res, data };
      };

      const primary = await tryFetch(`/api/llm/load-balance?range=${range}`);
      if (primary.res.ok) {
        const normalized = normalizeLoadBalanceRows(primary.data, rangeSeconds);
        setRows(normalized);
        saveLoadBalanceCache(range, normalized);
        setError(null);
        return;
      }

      const fallback = await tryFetch(`/api/admin/metrics/llm-load-balance?range=${range}`);
      if (!fallback.res.ok) {
        throw new Error(getBackendMessage(primary.data) || getBackendMessage(fallback.data) || '获取负载均衡数据失败');
      }
      const normalized = normalizeLoadBalanceRows(fallback.data, rangeSeconds);
      setRows(normalized);
      saveLoadBalanceCache(range, normalized);
      setError(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      if (!silent) setRows([]);
    } finally {
      inFlightRef.current = false;
      if (!silent) setLoading(false);
    }
  }, [range, rangeSeconds]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const ms = Number.isFinite(autoRefreshIntervalMs) ? Math.max(1000, Math.floor(autoRefreshIntervalMs as number)) : 0;
    if (!ms) return;
    if (suspended) return;
    const timer = window.setInterval(() => {
      load({ silent: true });
    }, ms);
    return () => window.clearInterval(timer);
  }, [autoRefreshIntervalMs, load, suspended]);

  const onChangeRange = useCallback((next: RangeKey) => setRange(next), []);
  const onManualRefresh = useCallback(() => load(), [load]);

  const { decreaseSuggest, increaseSuggest, removeSuggest, noTrafficSuggest, suggestMeta } = useMemo(() => {
    const list = rows.filter((r) => Number.isFinite(r.qps) && r.qps >= 0);
    if (list.length < 2) {
      return {
        decreaseSuggest: [],
        increaseSuggest: [],
        removeSuggest: [],
        noTrafficSuggest: [],
        suggestMeta: { totalQps: 0, cv: 0, peerMedianMs: 0, n: list.length },
      };
    }

    const totalQps = list.reduce((a, r) => a + (Number.isFinite(r.qps) ? r.qps : 0), 0);
    const n = list.length;
    const meanQps = totalQps > 0 ? totalQps / n : 0;
    const qpsValues = list.map((r) => r.qps);
    const cv = meanQps > 0 ? stddev(qpsValues) / meanQps : 0;
    const expectedShare = n > 0 ? 1 / n : 0;

    const latencyValues = list.map((r) => r.avgMs).filter((x) => Number.isFinite(x) && x > 0);
    const peerMedianMs = median(latencyValues);
    const slowThreshold = Math.max(1000, peerMedianMs > 0 ? peerMedianMs * 2 : 0);

    const recentWindow = rangeSeconds <= 3600 ? 12 : rangeSeconds <= 6 * 3600 ? 16 : rangeSeconds <= 24 * 3600 ? 20 : 24;
    const signalMinTotalQps = 0.2;

    type SuggestItem = {
      row: LoadBalanceRow;
      share: number;
      recentMedianMs: number;
      slowStreak: number;
      slowRatio: number;
      slowThreshold: number;
      recentErrorRate: number;
      recentThrottled429Rate: number;
    };

    const items: SuggestItem[] = list.map((row) => {
      const share = totalQps > 0 ? row.qps / totalQps : 0;
      const recentPoints = row.points.slice(-recentWindow).filter((p) => Number.isFinite(p.avgMs) && p.avgMs > 0);
      const recentMedianMs = median(recentPoints.map((p) => p.avgMs)) || (Number.isFinite(row.avgMs) ? row.avgMs : 0);
      const errRates = row.points.slice(-recentWindow).map((p) => p.errorRate).filter((x) => Number.isFinite(x) && x >= 0);
      const throttledRates = row.points.slice(-recentWindow).map((p) => p.throttled429Rate).filter((x) => Number.isFinite(x) && x >= 0);
      const recentErrorRate = errRates.length ? errRates.reduce((a, b) => a + b, 0) / errRates.length : (Number.isFinite(row.errorRate) ? row.errorRate : 0);
      const recentThrottled429Rate = throttledRates.length
        ? throttledRates.reduce((a, b) => a + b, 0) / throttledRates.length
        : (Number.isFinite(row.throttled429Rate) ? row.throttled429Rate : 0);
      const recentSlowRatio =
        recentPoints.length > 0 ? recentPoints.filter((p) => p.avgMs >= slowThreshold).length / recentPoints.length : 0;
      const slowStreak = (() => {
        if (!row.points.length) return 0;
        if (slowThreshold <= 1000) return computeSlowStreak(row.points);
        let streak = 0;
        for (let i = row.points.length - 1; i >= 0; i--) {
          const x = row.points[i];
          if (!Number.isFinite(x.avgMs) || x.avgMs < slowThreshold) break;
          streak++;
        }
        return streak;
      })();

      return { row, share, recentMedianMs, slowStreak, slowRatio: recentSlowRatio, slowThreshold, recentErrorRate, recentThrottled429Rate };
    });

    const removeSuggest: SuggestItem[] = [];
    const decreaseSuggest: SuggestItem[] = [];
    const increaseSuggest: SuggestItem[] = [];
    const noTrafficSuggest: SuggestItem[] = [];

    for (const it of items) {
      const overShare = expectedShare > 0 ? it.share > expectedShare * 1.35 : false;
      const underShare = expectedShare > 0 ? it.share < expectedShare * 0.65 : false;
      const slowRelative = peerMedianMs > 0 ? it.recentMedianMs > peerMedianMs * 1.8 : it.recentMedianMs >= 1200;
      const slowPersistent = it.slowStreak >= 3 || it.slowRatio >= 0.7;
      const hasSignal = totalQps >= signalMinTotalQps;
      const high429 = it.recentThrottled429Rate >= 0.05;
      const highErr = it.recentErrorRate >= 0.1;

      const shouldRemove =
        hasSignal &&
        ((slowRelative && slowPersistent) || highErr || high429) &&
        (it.share >= expectedShare * 0.5 || it.row.qps >= meanQps * 0.5);
      if (shouldRemove) {
        removeSuggest.push(it);
        continue;
      }

      const shouldDecrease =
        hasSignal &&
        ((overShare && it.row.qps >= meanQps * 1.2) ||
          (slowRelative && it.share >= expectedShare) ||
          (slowPersistent && it.share >= expectedShare * 0.8) ||
          (high429 && it.share >= expectedShare * 0.3) ||
          (highErr && it.share >= expectedShare * 0.3));
      if (shouldDecrease) {
        decreaseSuggest.push(it);
        continue;
      }

      const healthy = peerMedianMs > 0 ? it.recentMedianMs <= peerMedianMs * 1.2 : it.recentMedianMs > 0 && it.recentMedianMs < 900;
      const shouldIncrease = hasSignal && underShare && it.row.qps <= meanQps * 0.8 && healthy && it.row.qps > 0;
      if (shouldIncrease) {
        increaseSuggest.push(it);
        continue;
      }

      const hasOthers = items.some((x) => x.row.key !== it.row.key && x.row.qps >= meanQps * 0.8);
      const shouldCheckNoTraffic = hasSignal && hasOthers && it.row.qps <= Math.max(0.01, meanQps * 0.05);
      if (shouldCheckNoTraffic) {
        noTrafficSuggest.push(it);
      }
    }

    decreaseSuggest.sort((a, b) => b.share - a.share);
    increaseSuggest.sort((a, b) => a.share - b.share);
    removeSuggest.sort((a, b) => b.slowStreak - a.slowStreak || b.recentMedianMs - a.recentMedianMs);
    noTrafficSuggest.sort((a, b) => a.row.qps - b.row.qps);

    return { decreaseSuggest, increaseSuggest, removeSuggest, noTrafficSuggest, suggestMeta: { totalQps, cv, peerMedianMs, n } };
  }, [rows, rangeSeconds]);

  useEffect(() => {
    const el = elRef.current;
    if (!el) return;

    if (!chartRef.current) chartRef.current = echarts.init(el, undefined, { renderer: 'canvas' });
    const chart = chartRef.current;

    const names = rows.map((r) => r.label).reverse();
    const qps = rows.map((r) => r.qps).reverse();
    const avgMs = rows.map((r) => r.avgMs).reverse();
    const p95Ms = rows.map((r) => r.p95Ms).reverse();

    chart.setOption(
      {
        grid: { left: 60, right: 60, top: 36, bottom: 30, containLabel: true },
        legend: { top: 6, data: ['QPS', '平均响应时间(ms)', 'P95响应(ms)'] },
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        xAxis: [
          { type: 'value', name: 'QPS', axisLabel: { formatter: (v: number) => String(v) } },
          { type: 'value', name: 'ms', position: 'top', axisLabel: { formatter: (v: number) => String(v) } },
        ],
        yAxis: { type: 'category', data: names },
        series: [
          { name: 'QPS', type: 'bar', xAxisIndex: 0, data: qps, itemStyle: { color: '#3b82f6' }, barMaxWidth: 22 },
          { name: '平均响应时间(ms)', type: 'line', xAxisIndex: 1, data: avgMs, smooth: true, symbol: 'circle', symbolSize: 6, itemStyle: { color: '#f59e0b' } },
          { name: 'P95响应(ms)', type: 'line', xAxisIndex: 1, data: p95Ms, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { type: 'dashed' }, itemStyle: { color: '#ef4444' } },
        ],
      },
      { notMerge: true },
    );

    const ro = new ResizeObserver(() => chart.resize());
    ro.observe(el);
    return () => ro.disconnect();
  }, [rows]);

  useEffect(() => {
    return () => {
      if (chartRef.current) {
        chartRef.current.dispose();
        chartRef.current = null;
      }
    };
  }, []);

  return (
    <div className="rounded border p-3 space-y-3 bg-white">
      <LoadBalanceControls range={range} loading={loading} onChangeRange={onChangeRange} />

      {error ? (
        <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm flex items-center justify-between gap-2">
          <div className="min-w-0 break-words">{error}</div>
          <button type="button" className="rounded border bg-white px-3 py-1 text-xs" onClick={onManualRefresh}>
            重试
          </button>
        </div>
      ) : null}

      <div className="rounded border bg-white">
        <div className="border-b bg-gray-50 px-3 py-2 text-xs text-gray-600">模型 QPS（柱）与响应时间（平均 / P95）</div>
        <div className="p-2">
          {loading && !rows.length ? (
            <div className="h-[360px] flex items-center justify-center text-sm text-gray-500">加载中…</div>
          ) : rows.length ? (
            <div ref={elRef} className="h-[360px] w-full" />
          ) : (
            <div className="h-[360px] flex items-center justify-center text-sm text-gray-500">暂无数据</div>
          )}
        </div>
      </div>

      <div className="rounded border p-3 space-y-2">
        <div className="text-sm font-semibold">均衡建议</div>
        <div className="text-xs text-gray-600">
          {suggestMeta.n >= 2 && suggestMeta.totalQps > 0 ? (
            <>
              总 QPS {suggestMeta.totalQps.toFixed(2)}，均衡度 {(suggestMeta.cv * 100).toFixed(0)}（越低越均衡）
            </>
          ) : (
            <>暂无足够数据生成建议</>
          )}
        </div>
        {decreaseSuggest.length === 0 && increaseSuggest.length === 0 && removeSuggest.length === 0 && noTrafficSuggest.length === 0 ? (
          <div className="text-sm text-gray-600">暂无明显不均衡或异常响应</div>
        ) : (
          <div className="space-y-2">
            {increaseSuggest.length ? (
              <div className="rounded border border-emerald-200 bg-emerald-50 px-3 py-2">
                <div className="text-sm font-medium text-emerald-900">建议上调权重</div>
                <div className="text-xs text-emerald-900/80 mt-1 break-words">
                  {increaseSuggest
                    .map(
                      (it) =>
                        `${it.row.label}（占比 ${formatPercent(it.share)}，QPS ${it.row.qps.toFixed(2)}，中位响应 ${it.recentMedianMs.toFixed(0)}ms，失败率 ${formatPercent(it.recentErrorRate)}，429 ${formatPercent(it.recentThrottled429Rate)}）`,
                    )
                    .join('、')}
                </div>
              </div>
            ) : null}
            {decreaseSuggest.length ? (
              <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2">
                <div className="text-sm font-medium text-amber-900">建议下调权重</div>
                <div className="text-xs text-amber-900/80 mt-1 break-words">
                  {decreaseSuggest
                    .map(
                      (it) =>
                        `${it.row.label}（占比 ${formatPercent(it.share)}，QPS ${it.row.qps.toFixed(2)}，中位响应 ${it.recentMedianMs.toFixed(0)}ms，失败率 ${formatPercent(it.recentErrorRate)}，429 ${formatPercent(it.recentThrottled429Rate)}）`,
                    )
                    .join('、')}
                </div>
              </div>
            ) : null}
            {removeSuggest.length ? (
              <div className="rounded border border-red-200 bg-red-50 px-3 py-2">
                <div className="text-sm font-medium text-red-900">建议临时摘除</div>
                <div className="text-xs text-red-900/80 mt-1 break-words">
                  {removeSuggest
                    .map(
                      (it) =>
                        `${it.row.label}（连续 ${it.slowStreak} 个周期 ≥ ${it.slowThreshold.toFixed(0)}ms，中位响应 ${it.recentMedianMs.toFixed(0)}ms，失败率 ${formatPercent(it.recentErrorRate)}，429 ${formatPercent(it.recentThrottled429Rate)}）`,
                    )
                    .join('、')}
                </div>
              </div>
            ) : null}
            {noTrafficSuggest.length ? (
              <div className="rounded border border-slate-200 bg-slate-50 px-3 py-2">
                <div className="text-sm font-medium text-slate-900">建议检查未分配流量</div>
                <div className="text-xs text-slate-900/80 mt-1 break-words">
                  {noTrafficSuggest
                    .map((it) => `${it.row.label}（占比 ${formatPercent(it.share)}，QPS ${it.row.qps.toFixed(2)}）`)
                    .join('、')}
                </div>
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
};
