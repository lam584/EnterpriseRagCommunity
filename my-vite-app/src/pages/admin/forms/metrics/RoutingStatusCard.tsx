import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts';
import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';

type RoutingState = {
  strategy?: string;
  maxAttempts?: number;
  failureThreshold?: number;
  cooldownMs?: number;
} | null;

type RoutingDecisionEvent = {
  tsMs: number;
  kind: string;
  taskType: string | null;
  attempt: number | null;
  taskId: string | null;
  providerId: string | null;
  modelName: string | null;
  ok: boolean | null;
  errorCode: string | null;
  errorMessage: string | null;
  latencyMs: number | null;
  apiSource?: string | null;
};

export const RoutingStatusCard: React.FC<{
  taskTypes: string[];
  monitorTaskType: string;
  setMonitorTaskType: React.Dispatch<React.SetStateAction<string>>;
  formatTaskTypeLabel: (tt: string) => string;
  routingState: RoutingState;
  routingEventsConnected: boolean;
  routingEvents: RoutingDecisionEvent[];
  routingEventsResetAtMs: number;
  routingEventsLastAtMs: number | null;
  routingEventsReplayedCount: number;
  routingEventsPageSize: number;
  routingEventsPageIndex: number;
  setRoutingEvents: React.Dispatch<React.SetStateAction<RoutingDecisionEvent[]>>;
  setRoutingEventsResetAtMs: React.Dispatch<React.SetStateAction<number>>;
  setRoutingEventsLastAtMs: React.Dispatch<React.SetStateAction<number | null>>;
  setRoutingEventsPageSize: React.Dispatch<React.SetStateAction<number>>;
  setRoutingEventsPageIndex: React.Dispatch<React.SetStateAction<number>>;
  clampInt: (v: unknown, min: number, max: number, def: number) => number;
  formatMmddHms: (ms: number | null | undefined) => string;
  draft: unknown | null;
}> = ({
  taskTypes,
  monitorTaskType,
  setMonitorTaskType,
  formatTaskTypeLabel,
  routingState,
  routingEventsConnected,
  routingEvents,
  routingEventsResetAtMs,
  routingEventsLastAtMs,
  routingEventsReplayedCount,
  routingEventsPageSize,
  routingEventsPageIndex,
  setRoutingEvents,
  setRoutingEventsResetAtMs,
  setRoutingEventsLastAtMs,
  setRoutingEventsPageSize,
  setRoutingEventsPageIndex,
  clampInt,
  formatMmddHms,
  draft,
}) => {
  const normTaskType = (v: unknown): string => String(v ?? '').trim().toUpperCase();

  const [tickMs, setTickMs] = useState<number>(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setTickMs(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const [providerLabelById, setProviderLabelById] = useState<Record<string, string>>({});
  useEffect(() => {
    let disposed = false;
    adminGetAiProvidersConfig()
      .then((cfg) => {
        if (disposed) return;
        const map: Record<string, string> = {};
        for (const p of cfg.providers ?? []) {
          const id = String(p?.id ?? '').trim();
          if (!id) continue;
          const name = String(p?.name ?? '').trim();
          map[id] = name ? `${name} (${id})` : id;
        }
        setProviderLabelById(map);
      })
      .catch(() => {
      });
    return () => {
      disposed = true;
    };
  }, []);

  const providerSeries = useMemo(() => {
    const counts = new Map<string, number>();
    for (const e of routingEvents) {
      if (e.ok !== true) continue;
      if (e.kind !== 'ROUTE_OK' && e.kind !== 'FALLBACK_OK') continue;
      const src = String(e.providerId ?? '').trim() || '未知';
      counts.set(src, (counts.get(src) ?? 0) + 1);
    }
    const data = Array.from(counts.entries()).map(([providerId, value]) => ({
      name: providerLabelById[providerId] ?? providerId,
      value,
    }));
    data.sort((a, b) => b.value - a.value);
    const total = data.reduce((s, x) => s + x.value, 0);
    return { data, total };
  }, [providerLabelById, routingEvents]);

  const providerChartRef = useRef<HTMLDivElement | null>(null);
  const providerChartInstanceRef = useRef<echarts.EChartsType | null>(null);

  useEffect(() => {
    const el = providerChartRef.current;
    if (!el) return;
    const chart = echarts.init(el);
    providerChartInstanceRef.current = chart;
    const onResize = () => {
      try {
        chart.resize();
      } catch {
      }
    };
    window.addEventListener('resize', onResize);
    onResize();
    return () => {
      window.removeEventListener('resize', onResize);
      providerChartInstanceRef.current = null;
      try {
        chart.dispose();
      } catch {
      }
    };
  }, []);

  useEffect(() => {
    const chart = providerChartInstanceRef.current;
    if (!chart) return;
    if (!providerSeries.data.length) {
      chart.clear();
      return;
    }
    chart.setOption(
      {
        tooltip: { trigger: 'item' },
        legend: { top: 8, left: 'center' },
        series: [
          {
            name: '模型提供方',
            type: 'pie',
            radius: ['35%', '70%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, formatter: '{b}: {d}%' },
            labelLine: { show: true },
            data: providerSeries.data,
          },
        ],
      },
      { notMerge: true },
    );
  }, [providerSeries]);

  const routingLastTsMs = routingEventsLastAtMs ?? (routingEvents.length ? routingEvents[0].tsMs : null);
  const routingSilenceSec = Math.max(0, Math.floor((tickMs - (routingLastTsMs ?? routingEventsResetAtMs)) / 1000));
  const routingEventsSafePageSize = Math.max(1, clampInt(routingEventsPageSize, 1, 1000, 10));
  const routingEventsSafePageIndex = Math.max(0, routingEventsPageIndex);
  const routingEventsPageStart = routingEventsSafePageIndex * routingEventsSafePageSize;
  const routingEventsPageItems = routingEvents.slice(routingEventsPageStart, routingEventsPageStart + routingEventsSafePageSize);
  const routingEventsHasPrevPage = routingEventsSafePageIndex > 0 && routingEvents.length > 0;
  const routingEventsHasNextPage = routingEventsPageStart + routingEventsSafePageSize < routingEvents.length;

  return (
    <div className="rounded border p-3 space-y-3 bg-white">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">实时路由状态</div>
          <div className="text-xs text-gray-500">
            {monitorTaskType === 'ALL' ? (
              <>
                事件范围：全部场景
                <span className="ml-2 inline-flex items-center rounded border border-gray-200 bg-gray-50 px-1.5 py-0.5 text-[10px] text-gray-600">
                  监控全部场景：不展示单一场景的路由状态参数
                </span>
              </>
            ) : (
              <>
                事件范围：当前场景 · 当前配置：{formatTaskTypeLabel(monitorTaskType)}
              </>
            )}
            {routingEventsReplayedCount ? <> · 已回放 {routingEventsReplayedCount} 条</> : null}
          </div>
        </div>
        <div className="flex items-center gap-2 text-xs text-gray-600">
          <div className="flex items-center gap-1">
            <div>每页</div>
            <input
              type="number"
              className="w-[72px] rounded border px-2 py-1 text-xs"
              min={1}
              max={1000}
              step={1}
              value={routingEventsSafePageSize}
              onChange={(e) => {
                const v = clampInt(e.target.value, 1, 1000, 10);
                setRoutingEventsPageSize(v);
                setRoutingEventsPageIndex(0);
              }}
              disabled={!draft}
            />
            <div>行</div>
          </div>
          <div className="flex items-center gap-1">
            <div className="text-gray-500">监控</div>
            <select
              className="rounded border px-2 py-1 text-xs bg-white disabled:bg-gray-50"
              value={monitorTaskType}
              onChange={(e) => {
                const v = String(e.target.value ?? '');
                setMonitorTaskType(v === 'ALL' ? 'ALL' : normTaskType(v));
              }}
              disabled={!draft}
            >
              <option value="ALL">全部场景</option>
              {(taskTypes.length ? taskTypes : ['CHAT']).map((tt) => (
                <option key={tt} value={tt}>
                  {formatTaskTypeLabel(tt)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <span className={`inline-block h-2.5 w-2.5 rounded-full ${routingEventsConnected ? 'bg-green-500' : 'bg-gray-400'}`} />{' '}
            {routingEventsConnected ? 'SSE 已连接' : 'SSE 未连接'}
          </div>
          <button
            type="button"
            className="rounded border px-2 py-1 text-xs disabled:opacity-50"
            onClick={() => {
              setRoutingEvents([]);
              setRoutingEventsResetAtMs(Date.now());
              setRoutingEventsLastAtMs(null);
              setRoutingEventsPageIndex(0);
            }}
            disabled={!draft}
          >
            清空
          </button>
        </div>
      </div>

      {monitorTaskType === 'ALL' ? null : routingState ? (
        <div className="text-xs text-gray-700">
          策略：{routingState.strategy || '-'}，maxAttempts {routingState.maxAttempts ?? '-'}，熔断阈值 {routingState.failureThreshold ?? '-'}，冷却{' '}
          {routingState.cooldownMs ?? '-'}ms
        </div>
      ) : (
        <div className="text-xs text-gray-500">正在加载路由状态…</div>
      )}

      <div className="text-xs text-gray-500">
        {!routingEventsConnected ? (
          <>等待 SSE 连接…已等待 {routingSilenceSec}s</>
        ) : !routingEvents.length ? (
          <>暂无路由事件</>
        ) : routingSilenceSec >= 10 ? (
          <>最近 {routingSilenceSec}s 无新路由事件（最后一次 {formatMmddHms(routingLastTsMs) || '-'}）</>
        ) : (
          <>最近事件：{formatMmddHms(routingLastTsMs) || '-'}（共 {routingEvents.length} 条）</>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-gray-600">
        <div className="flex items-center gap-2">
          {routingEvents.length ? <div className="text-gray-500">共 {routingEvents.length} 条</div> : null}
        </div>
        <div className="flex items-center gap-2">
          {routingEventsHasPrevPage ? (
            <button
              type="button"
              className="rounded border px-2 py-1 text-xs"
              onClick={() => setRoutingEventsPageIndex((x) => Math.max(0, x - 1))}
            >
              上一页
            </button>
          ) : null}
          {routingEventsHasNextPage ? (
            <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => setRoutingEventsPageIndex((x) => x + 1)}>
              下一页
            </button>
          ) : null}
        </div>
      </div>

      <div className="overflow-x-auto border rounded">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-gray-50 text-gray-600 border-b">
              <th className="p-2 border-r text-left font-semibold min-w-[140px]">时间</th>
              <th className="p-2 border-r text-left font-semibold min-w-[110px]">场景</th>
              <th className="p-2 border-r text-left font-semibold min-w-[110px]">事件</th>
              <th className="p-2 border-r text-left font-semibold min-w-[260px]">模型</th>
              <th className="p-2 border-r text-left font-semibold min-w-[80px]">结果</th>
              <th className="p-2 text-left font-semibold min-w-[90px]">耗时</th>
            </tr>
          </thead>
          <tbody className="divide-y bg-white">
            {routingEventsPageItems.map((e, idx) => {
              const ok = e.ok;
              const statusText = ok === true ? '成功' : ok === false ? '失败' : '-';
              const taskTypeText = e.taskType ? formatTaskTypeLabel(e.taskType) : '-';
              const modelText = e.providerId && e.modelName ? `${e.providerId}：${e.modelName}` : '-';
              const err = e.errorCode || e.errorMessage ? `${e.errorCode ? `[${e.errorCode}] ` : ''}${e.errorMessage || ''}`.trim() : '';
              return (
                <tr key={`${e.tsMs}-${idx}`} className="hover:bg-gray-50">
                  <td className="p-2 border-r text-gray-700">{formatMmddHms(e.tsMs) || '-'}</td>
                  <td className="p-2 border-r text-gray-700">{taskTypeText}</td>
                  <td className="p-2 border-r text-gray-700">{e.kind || '-'}</td>
                  <td className="p-2 border-r text-gray-700 whitespace-pre-wrap break-words" title={err}>
                    {modelText}
                    {e.taskId ? <div className="text-[11px] text-gray-500">{e.taskId}</div> : null}
                  </td>
                  <td className={`p-2 border-r ${ok === true ? 'text-green-700' : ok === false ? 'text-red-700' : 'text-gray-500'}`}>{statusText}</td>
                  <td className="p-2 text-gray-700">{e.latencyMs != null ? `${e.latencyMs}ms` : '-'}</td>
                </tr>
              );
            })}
            {!routingEvents.length ? (
              <tr>
                <td colSpan={6} className="p-3 text-center text-gray-400 italic">
                  暂无路由事件（{routingEventsConnected ? 'SSE 已连接' : '等待 SSE 连接…'}）
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="rounded border bg-white p-3 space-y-2">
        <div className="text-sm font-semibold">路由事件 模型提供方占比</div>
        <div className="text-xs text-gray-500">
          {providerSeries.total ? <>统计范围：当前已接收 {providerSeries.total} 条成功路由事件</> : <>暂无可统计事件</>}
        </div>
        <div className="w-full h-[260px]" ref={providerChartRef} />
      </div>
    </div>
  );
};
