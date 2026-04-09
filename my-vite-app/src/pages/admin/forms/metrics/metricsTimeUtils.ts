import { useCallback, useEffect, useState } from 'react';
import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';

export type MetricsRangePreset =
  | 'CUSTOM'
  | 'LAST_30M'
  | 'LAST_1H'
  | 'LAST_6H'
  | 'LAST_12H'
  | 'LAST_24H'
  | 'TODAY'
  | 'YESTERDAY'
  | 'LAST_7D'
  | 'LAST_30D';

type MetricsRangeState = {
  startDate: Date;
  endDate: Date;
  rangePreset: MetricsRangePreset;
};

type MetricsRequestState<TMetrics, TTimeline> = {
  loading: boolean;
  error: string | null;
  resp: TMetrics | null;
  timeline: TTimeline | null;
  timelineError: string | null;
};

export function parseTimestampMs(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) {
    const ms = v > 1e12 ? v : v * 1000;
    return Number.isFinite(ms) ? ms : null;
  }
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    if (Number.isFinite(n)) {
      const ms = n > 1e12 ? n : n * 1000;
      return Number.isFinite(ms) ? ms : null;
    }
    const d = new Date(t);
    const ms = d.getTime();
    return Number.isFinite(ms) ? ms : null;
  }
  return null;
}

export function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

export function formatLocalDateTime(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(
    d.getSeconds(),
  )}`;
}

export function dayStart(d: Date): Date {
  const x = new Date(d.getTime());
  x.setHours(0, 0, 0, 0);
  return x;
}

export function dayEnd(d: Date): Date {
  const x = new Date(d.getTime());
  x.setHours(23, 59, 59, 0);
  return x;
}

export function createDefaultMetricsRangeState(now = new Date()): MetricsRangeState {
  return {
    startDate: dayStart(new Date(now.getTime() - 7 * 24 * 3600 * 1000)),
    endDate: dayEnd(now),
    rangePreset: 'CUSTOM',
  };
}

export function useMetricsRangeState() {
  const [state, setState] = useState<MetricsRangeState>(() => createDefaultMetricsRangeState());
  const setStartDate = useCallback((startDate: Date) => {
    setState((prev) => ({ ...prev, startDate }));
  }, []);
  const setEndDate = useCallback((endDate: Date) => {
    setState((prev) => ({ ...prev, endDate }));
  }, []);
  const setRangePreset = useCallback((rangePreset: MetricsRangePreset) => {
    setState((prev) => ({ ...prev, rangePreset }));
  }, []);

  return {
    ...state,
    setStartDate,
    setEndDate,
    setRangePreset,
  };
}

export function useMetricsRequestState<TMetrics, TTimeline>() {
  const [state, setState] = useState<MetricsRequestState<TMetrics, TTimeline>>({
    loading: false,
    error: null,
    resp: null,
    timeline: null,
    timelineError: null,
  });
  const setLoading = useCallback((loading: boolean) => {
    setState((prev) => ({ ...prev, loading }));
  }, []);
  const setError = useCallback((error: string | null) => {
    setState((prev) => ({ ...prev, error }));
  }, []);
  const setResp = useCallback((resp: TMetrics | null) => {
    setState((prev) => ({ ...prev, resp }));
  }, []);
  const setTimeline = useCallback((timeline: TTimeline | null) => {
    setState((prev) => ({ ...prev, timeline }));
  }, []);
  const setTimelineError = useCallback((timelineError: string | null) => {
    setState((prev) => ({ ...prev, timelineError }));
  }, []);

  return {
    ...state,
    setLoading,
    setError,
    setResp,
    setTimeline,
    setTimelineError,
  };
}

export function clampMetricInt(v: unknown, min: number, max: number, def: number): number {
  if (typeof v === 'number' && Number.isFinite(v)) {
    const t = Math.trunc(v);
    return Math.max(min, Math.min(max, t));
  }
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return def;
    const n = Number(t);
    if (!Number.isFinite(n)) return def;
    const x = Math.trunc(n);
    return Math.max(min, Math.min(max, x));
  }
  return def;
}

export function toNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

export function fmtCost(v: unknown): string {
  const n = toNumber(v);
  if (n === null) return '0';
  return n.toFixed(6).replace(/\.?0+$/, '');
}

export function fmtInt(v: unknown): string {
  const n = toNumber(v);
  if (n === null) return '0';
  return String(Math.round(n));
}

export function trimStr(v: unknown): string {
  return String(v ?? '').trim();
}

export function buildProviderNameMap(providers: Array<{ id?: unknown; name?: unknown } | null | undefined>): Record<string, string> {
  const map: Record<string, string> = {};
  for (const provider of providers) {
    const id = trimStr(provider?.id);
    if (!id) continue;
    const name = trimStr(provider?.name);
    map[id] = name || id;
  }
  return map;
}

export function useAiProviderNameMap() {
  const [providerNameById, setProviderNameById] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const cfg = await adminGetAiProvidersConfig();
        if (!cancelled) {
          setProviderNameById(buildProviderNameMap((cfg.providers ?? []) as Array<{ id?: unknown; name?: unknown }>));
        }
      } catch {
        if (!cancelled) setProviderNameById({});
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return providerNameById;
}

export function formatModelWithProvider(
  model: unknown,
  providerId: unknown,
  providerNameById: Record<string, string>
): string {
  const m = trimStr(model);
  const pid = trimStr(providerId);
  if (!pid) return m || '—';
  const providerName = providerNameById[pid] || pid;
  if (providerName && m) return `${providerName}：${m}`;
  return providerName || m || '—';
}

export function modelKey(model: unknown, providerId: unknown): string {
  const m = trimStr(model);
  const pid = trimStr(providerId);
  return pid ? `${pid}|${m}` : m;
}

export function formatMmddHms(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return '';
  const d = new Date(ms);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${mm}-${dd} ${hh}:${mi}:${ss}`;
}

export function formatHmsTime(value: string | null | undefined, fallback = '—'): string {
  if (!value) return fallback;
  const d = new Date(value);
  if (!Number.isFinite(d.getTime())) return value;
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

export function formatDurationMs(value: unknown): string {
  const n = toNumber(value);
  if (n === null || n < 0) return '—';
  if (n < 1000) return `${Math.round(n)}ms`;
  const s = n / 1000;
  if (s < 60) return `${s.toFixed(2).replace(/\.?0+$/, '')}s`;
  const m = Math.floor(s / 60);
  const r = s - m * 60;
  return `${m}m${Math.round(r)}s`;
}

export function resolveRangePresetDates(preset: MetricsRangePreset, end = new Date()): { startDate: Date; endDate: Date } | null {
  if (preset === 'CUSTOM') return null;
  if (preset === 'YESTERDAY') {
    const y = new Date(end.getTime() - 24 * 3600 * 1000);
    return { startDate: dayStart(y), endDate: dayEnd(y) };
  }

  let startDate = new Date(end.getTime());
  if (preset === 'LAST_30M') startDate = new Date(end.getTime() - 30 * 60 * 1000);
  else if (preset === 'LAST_1H') startDate = new Date(end.getTime() - 1 * 3600 * 1000);
  else if (preset === 'LAST_6H') startDate = new Date(end.getTime() - 6 * 3600 * 1000);
  else if (preset === 'LAST_12H') startDate = new Date(end.getTime() - 12 * 3600 * 1000);
  else if (preset === 'LAST_24H') startDate = new Date(end.getTime() - 24 * 3600 * 1000);
  else if (preset === 'LAST_7D') startDate = new Date(end.getTime() - 7 * 24 * 3600 * 1000);
  else if (preset === 'LAST_30D') startDate = new Date(end.getTime() - 30 * 24 * 3600 * 1000);
  else if (preset === 'TODAY') startDate = dayStart(end);

  return { startDate, endDate: end };
}

export function computeMaxMetricChartValue<T>(
  items: T[],
  chartBy: 'cost' | 'tokens',
  readTokens: (item: T) => number,
  readCost: (item: T) => number
): number {
  let max = 0;
  for (const item of items) {
    const value = chartBy === 'tokens' ? readTokens(item) : readCost(item);
    if (value > max) max = value;
  }
  return max <= 0 ? 1 : max;
}
