import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { LoadBalanceChart } from './LoadBalanceChart';
import { RoutingStatusCard } from './RoutingStatusCard';
import { adminGetLlmRoutingConfig, type AdminLlmRoutingConfigDTO } from '../../../../services/llmRoutingAdminService';
import {
  adminGetLlmRoutingDecisions,
  adminOpenLlmRoutingEventSource,
  type AdminLlmRoutingDecisionEventDTO,
} from '../../../../services/llmRoutingMonitorAdminService';
import { getBackendMessage } from '../../../../services/serviceErrorUtils';
import { serviceApiUrl } from '../../../../services/serviceUrlUtils';
import { clampMetricInt, formatMmddHms } from './metricsTimeUtils';

const apiUrl = serviceApiUrl;

function normTaskType(s: string | null | undefined): string {
  return String(s || '').trim().toUpperCase();
}

type RoutingState = {
  strategy?: string;
  maxAttempts?: number;
  failureThreshold?: number;
  cooldownMs?: number;
} | null;

type RoutingDecisionEvent = AdminLlmRoutingDecisionEventDTO;

type LlmRoutingStateResponse = {
  checkedAtMs: number;
  taskType: string;
  strategy: string;
  maxAttempts: number;
  failureThreshold: number;
  cooldownMs: number;
};

const ROUTING_RUNTIME_STATE_POLL_INTERVAL_MS = 5000;
const ROUTING_EVENTS_KEEP = (() => {
  try {
    const raw = window.localStorage.getItem('admin.metrics.routingEventsKeep');
    return clampMetricInt(raw, 200, 10_000, 2000);
  } catch {
    return 2000;
  }
})();

type RoutingEventsCacheV1 = {
  v: 1;
  savedAtMs: number;
  taskType: string;
  resetAtMs: number;
  lastAtMs: number | null;
  pageSize: number;
  events: RoutingDecisionEvent[];
};

function routingEventsCacheKey(taskType: string): string {
  const tt = taskType ? normTaskType(taskType) : 'ALL';
  return `admin.metrics.routingEvents.${tt}`;
}

function loadRoutingEventsCache(taskType: string): RoutingEventsCacheV1 | null {
  try {
    const raw = window.localStorage.getItem(routingEventsCacheKey(taskType));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as RoutingEventsCacheV1;
    if (!parsed || typeof parsed !== 'object' || parsed.v !== 1) return null;
    if (normTaskType(parsed.taskType) !== normTaskType(taskType || 'ALL')) return null;
    const events = Array.isArray(parsed.events) ? (parsed.events as RoutingDecisionEvent[]) : [];
    return {
      v: 1,
      savedAtMs: typeof parsed.savedAtMs === 'number' ? parsed.savedAtMs : Date.now(),
      taskType: parsed.taskType,
      resetAtMs: typeof parsed.resetAtMs === 'number' ? parsed.resetAtMs : Date.now(),
      lastAtMs: typeof parsed.lastAtMs === 'number' ? parsed.lastAtMs : null,
      pageSize: clampMetricInt(parsed.pageSize, 1, 1000, 10),
      events: events.slice(0, ROUTING_EVENTS_KEEP),
    };
  } catch {
    return null;
  }
}

function saveRoutingEventsCache(taskType: string, cache: RoutingEventsCacheV1): void {
  try {
    window.localStorage.setItem(routingEventsCacheKey(taskType), JSON.stringify(cache));
  } catch {
  }
}

const LlmRoutingMonitorForm: React.FC = () => {
  const [cfg, setCfg] = useState<AdminLlmRoutingConfigDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const scenarioMetadata = useMemo(() => {
    const map = new Map<string, { label: string; category: string }>();
    (cfg?.scenarios ?? []).forEach((s) => {
      map.set(normTaskType(s.taskType), { label: s.label, category: s.category });
    });
    return map;
  }, [cfg?.scenarios]);

  const formatTaskTypeLabel = useCallback(
    (tt: string) => {
      const up = normTaskType(tt);
      const meta = scenarioMetadata.get(up);
      return meta ? meta.label : up;
    },
    [scenarioMetadata],
  );

  const taskTypes = useMemo(() => {
    const set = new Set<string>();
    (cfg?.scenarios ?? []).forEach((s) => set.add(normTaskType(s.taskType)));
    return Array.from(set).filter((x) => x && x !== 'UNKNOWN');
  }, [cfg?.scenarios]);

  const [monitorTaskType, setMonitorTaskType] = useState<string>('ALL');
  useEffect(() => {
    if (monitorTaskType === 'ALL') return;
    if (!taskTypes.length) return;
    const tt = normTaskType(monitorTaskType);
    if (taskTypes.includes(tt)) return;
    setMonitorTaskType(taskTypes[0]);
  }, [taskTypes, monitorTaskType]);

  useEffect(() => {
    let disposed = false;
    setLoading(true);
    setError(null);
    adminGetLlmRoutingConfig()
      .then((res) => {
        if (disposed) return;
        setCfg(res);
      })
      .catch((e) => {
        if (disposed) return;
        setError(e instanceof Error ? e.message : String(e));
        setCfg(null);
      })
      .finally(() => {
        if (disposed) return;
        setLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, []);

  const routingMonitorReady = !!cfg;
  const [routingState, setRoutingState] = useState<RoutingState>(null);
  const [routingEvents, setRoutingEvents] = useState<RoutingDecisionEvent[]>([]);
  const [routingEventsConnected, setRoutingEventsConnected] = useState(false);
  const [routingEventsResetAtMs, setRoutingEventsResetAtMs] = useState<number>(() => Date.now());
  const [routingEventsLastAtMs, setRoutingEventsLastAtMs] = useState<number | null>(null);
  const [routingEventsReplayedCount, setRoutingEventsReplayedCount] = useState<number>(0);
  const [routingEventsPageSize, setRoutingEventsPageSize] = useState<number>(10);
  const [routingEventsPageIndex, setRoutingEventsPageIndex] = useState<number>(0);

  useEffect(() => {
    if (!routingMonitorReady) return;
    const tt = monitorTaskType === 'ALL' ? '' : normTaskType(monitorTaskType);
    const timer = window.setTimeout(() => {
      saveRoutingEventsCache(tt, {
        v: 1,
        savedAtMs: Date.now(),
        taskType: tt ? normTaskType(tt) : 'ALL',
        resetAtMs: routingEventsResetAtMs,
        lastAtMs: routingEventsLastAtMs,
        pageSize: clampMetricInt(routingEventsPageSize, 1, 1000, 10),
        events: routingEvents.slice(0, ROUTING_EVENTS_KEEP),
      });
    }, 350);
    return () => window.clearTimeout(timer);
  }, [
    routingMonitorReady,
    monitorTaskType,
    routingEvents,
    routingEventsLastAtMs,
    routingEventsResetAtMs,
    routingEventsPageSize,
  ]);

  useEffect(() => {
    setRoutingEventsPageIndex(0);
  }, [routingEventsResetAtMs]);

  useEffect(() => {
    const maxIndex = Math.max(0, Math.ceil(routingEvents.length / Math.max(1, routingEventsPageSize)) - 1);
    if (routingEventsPageIndex > maxIndex) setRoutingEventsPageIndex(maxIndex);
  }, [routingEvents.length, routingEventsPageIndex, routingEventsPageSize]);

  useEffect(() => {
    if (!routingMonitorReady) return;
    if (monitorTaskType === 'ALL') {
      setRoutingState(null);
      return;
    }
    let disposed = false;
    const controller = new AbortController();
    let inFlight = false;

    const pollOnce = async () => {
      if (disposed || inFlight) return;
      inFlight = true;
      const tt = normTaskType(monitorTaskType);
      try {
        const res = await fetch(apiUrl(`/api/admin/metrics/llm-routing/state?taskType=${encodeURIComponent(tt)}`), {
          method: 'GET',
          credentials: 'include',
          signal: controller.signal,
        });
        const data: unknown = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(getBackendMessage(data) || '获取路由运行时状态失败');
        const dto = data as LlmRoutingStateResponse;
        if (!disposed) {
          setRoutingState({
            strategy: String((dto as any)?.strategy ?? '').trim() || undefined,
            maxAttempts: typeof (dto as any)?.maxAttempts === 'number' ? (dto as any).maxAttempts : undefined,
            failureThreshold: typeof (dto as any)?.failureThreshold === 'number' ? (dto as any).failureThreshold : undefined,
            cooldownMs: typeof (dto as any)?.cooldownMs === 'number' ? (dto as any).cooldownMs : undefined,
          });
        }
      } catch {
      } finally {
        inFlight = false;
      }
    };

    pollOnce();
    const timer = window.setInterval(pollOnce, ROUTING_RUNTIME_STATE_POLL_INTERVAL_MS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      controller.abort();
    };
  }, [routingMonitorReady, monitorTaskType]);

  useEffect(() => {
    if (!routingMonitorReady) return;
    const tt = monitorTaskType === 'ALL' ? '' : normTaskType(monitorTaskType);
    let disposed = false;

    const cached = loadRoutingEventsCache(tt);
    setRoutingEvents(cached?.events ?? []);
    setRoutingEventsConnected(false);
    setRoutingEventsResetAtMs(cached?.resetAtMs ?? Date.now());
    setRoutingEventsLastAtMs(cached?.lastAtMs ?? null);
    setRoutingEventsReplayedCount(0);
    setRoutingEventsPageIndex(0);
    if (cached?.pageSize) setRoutingEventsPageSize(cached.pageSize);

    const eventKey = (e: RoutingDecisionEvent): string => {
      const tsMs = typeof e.tsMs === 'number' && Number.isFinite(e.tsMs) ? e.tsMs : 0;
      const kind = String(e.kind ?? '');
      const taskType = String(e.taskType ?? '');
      const attempt = e.attempt == null ? '' : String(e.attempt);
      const taskId = String(e.taskId ?? '');
      const providerId = String(e.providerId ?? '');
      const modelName = String(e.modelName ?? '');
      const ok = e.ok == null ? '' : String(e.ok);
      return `${tsMs}|${kind}|${taskType}|${attempt}|${taskId}|${providerId}|${modelName}|${ok}`;
    };

    const normalizeEvent = (item: RoutingDecisionEvent): RoutingDecisionEvent => {
      const tsMs = typeof item.tsMs === 'number' && Number.isFinite(item.tsMs) ? item.tsMs : Date.now();
      return { ...item, tsMs };
    };

    const mergeAndTrim = (incoming: RoutingDecisionEvent[], prev: RoutingDecisionEvent[]): RoutingDecisionEvent[] => {
      const seen = new Set<string>();
      const out: RoutingDecisionEvent[] = [];
      for (const e of incoming) {
        const k = eventKey(e);
        if (seen.has(k)) continue;
        seen.add(k);
        out.push(e);
        if (out.length >= ROUTING_EVENTS_KEEP) return out;
      }
      for (const e of prev) {
        const k = eventKey(e);
        if (seen.has(k)) continue;
        seen.add(k);
        out.push(e);
        if (out.length >= ROUTING_EVENTS_KEEP) break;
      }
      return out;
    };

    let es: EventSource | null = null;

    const connect = () => {
      if (disposed) return;
      es = adminOpenLlmRoutingEventSource(tt ? { taskType: tt } : undefined);

      const onConnected = () => setRoutingEventsConnected(true);
      const onRouting = (ev: MessageEvent) => {
        try {
          const raw = JSON.parse(String(ev.data ?? '{}')) as RoutingDecisionEvent;
          const item = normalizeEvent(raw);
          setRoutingEventsLastAtMs(item.tsMs);
          setRoutingEvents((prev) => mergeAndTrim([item], prev));
        } catch {
        }
      };
      const onError = () => setRoutingEventsConnected(false);

      es.addEventListener('connected', onConnected as any);
      es.addEventListener('routing', onRouting as any);
      es.onerror = onError;
    };

    adminGetLlmRoutingDecisions({ taskType: tt || undefined, limit: ROUTING_EVENTS_KEEP })
      .then((res) => {
        if (disposed) return;
        const items = (res.items ?? []).map(normalizeEvent);
        setRoutingEventsReplayedCount(items.length);
        setRoutingEvents((prev) => mergeAndTrim(items, prev));
        if (items.length) setRoutingEventsLastAtMs(items[0].tsMs);
      })
      .catch(() => {
      })
      .finally(() => connect());

    return () => {
      disposed = true;
      try {
        es?.close();
      } catch {
      }
    };
  }, [routingMonitorReady, monitorTaskType]);

  const [autoRefreshIntervalMs, setAutoRefreshIntervalMs] = useState<number>(2000);
  const autoRefreshIntervalMsSafe = useMemo(() => clampMetricInt(autoRefreshIntervalMs, 1000, 60_000, 2000), [autoRefreshIntervalMs]);

  const intervalInputRef = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (!intervalInputRef.current) return;
    const v = String(autoRefreshIntervalMsSafe);
    if (intervalInputRef.current.value !== v) intervalInputRef.current.value = v;
  }, [autoRefreshIntervalMsSafe]);

  if (loading) {
    return <div className="p-4 text-sm text-gray-600">加载路由监控中…</div>;
  }

  if (error) {
    return <div className="p-4 text-sm text-red-700">加载失败：{error}</div>;
  }

  return (
    <div className="space-y-4">
      <div className="rounded border bg-white p-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-gray-700">
          <div className="font-semibold">路由与负载均衡</div>
          <div className="text-xs text-gray-500">监控范围：实时事件 + 负载均衡曲线</div>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-gray-700">
          <div className="text-gray-500">图表刷新</div>
          <input
            ref={intervalInputRef}
            type="number"
            className="w-[90px] rounded border px-2 py-1 text-xs"
            min={1000}
            max={60000}
            step={500}
            defaultValue={String(autoRefreshIntervalMsSafe)}
            onChange={(e) => setAutoRefreshIntervalMs(clampMetricInt(e.target.value, 1000, 60_000, 2000))}
          />
          <div className="text-gray-500">ms</div>
        </div>
      </div>

      <LoadBalanceChart autoRefreshIntervalMs={autoRefreshIntervalMsSafe} suspended={false} />

      <RoutingStatusCard
        taskTypes={taskTypes}
        monitorTaskType={monitorTaskType}
        setMonitorTaskType={setMonitorTaskType}
        formatTaskTypeLabel={formatTaskTypeLabel}
        routingState={routingState}
        routingEventsConnected={routingEventsConnected}
        routingEvents={routingEvents}
        routingEventsResetAtMs={routingEventsResetAtMs}
        routingEventsLastAtMs={routingEventsLastAtMs}
        routingEventsReplayedCount={routingEventsReplayedCount}
        routingEventsPageSize={routingEventsPageSize}
        routingEventsPageIndex={routingEventsPageIndex}
        setRoutingEvents={setRoutingEvents}
        setRoutingEventsResetAtMs={setRoutingEventsResetAtMs}
        setRoutingEventsLastAtMs={setRoutingEventsLastAtMs}
        setRoutingEventsPageSize={setRoutingEventsPageSize}
        setRoutingEventsPageIndex={setRoutingEventsPageIndex}
        clampInt={clampMetricInt}
        formatMmddHms={formatMmddHms}
        draft={{}}
      />
    </div>
  );
};

export default LlmRoutingMonitorForm;
