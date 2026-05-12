import React, { useEffect, useMemo, useRef } from 'react';
import { init, type AppEChartsOption, type AppEChartsType } from './echartsCore';
import type { TokenMetricsModelItemDTO } from '../../../../services/admin/ai/tokenMetricsAdminService';

const MODEL_COST_SERIES_META = [
  { label: 'Input', color: '#60A5FA', dash: 'solid' },
  { label: 'Output', color: '#2563EB', dash: 'solid' },
  { label: 'Total', color: '#111827', dash: 'dashed' },
  { label: 'Cost', color: '#F59E0B', dash: 'solid' },
] as const;

function trimStr(v: unknown): string {
  return String(v ?? '').trim();
}

function toNumber(v: unknown): number | null {
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

function fmtInt(v: unknown): string {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '—';
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function fmtCost(v: unknown): string {
  const n = toNumber(v);
  if (n === null) return '0';
  const fixed = n.toFixed(6).replace(/\.?0+$/, '');
  const parts = fixed.split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts.join('.');
}

function formatModelWithProvider(model: unknown, providerId: unknown, providerNameById: Record<string, string>): string {
  const m = trimStr(model);
  const pid = trimStr(providerId);
  if (!pid) return m || '—';
  const providerName = providerNameById[pid] || pid;
  if (providerName && m) return `${providerName}：${m}`;
  return providerName || m || '—';
}

export const ModelTokenCostChart: React.FC<{
  title: string;
  items: TokenMetricsModelItemDTO[];
  currency?: string | null | undefined;
  providerNameById?: Record<string, string>;
  height?: number;
}> = ({ title, items, currency, providerNameById = {}, height = 320 }) => {
  const elRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<AppEChartsType | null>(null);

  const normalized = useMemo(() => {
    const xs = items.map((it) => formatModelWithProvider(it.model, it.providerId, providerNameById));
    const tokensIn = items.map((it) => Number(it.tokensIn || 0));
    const tokensOut = items.map((it) => Number(it.tokensOut || 0));
    const totalTokens = items.map((it) => Number(it.totalTokens || 0));
    const costs = items.map((it) => toNumber(it.cost) ?? 0);
    return { xs, tokensIn, tokensOut, totalTokens, costs };
  }, [items, providerNameById]);

  const summary = useMemo(() => {
    const totalTokens = normalized.totalTokens.reduce((sum, value) => sum + value, 0);
    const totalCost = normalized.costs.reduce((sum, value) => sum + value, 0);
    const topIndex = normalized.totalTokens.reduce((bestIndex, value, index, list) => (value > (list[bestIndex] ?? -1) ? index : bestIndex), 0);

    return {
      modelCount: items.length,
      totalTokens,
      totalCost,
      topModel: normalized.xs[topIndex] ?? '—',
    };
  }, [items.length, normalized]);

  useEffect(() => {
    if (!elRef.current) return;
    if (!chartRef.current) chartRef.current = init(elRef.current);

    const opt: AppEChartsOption = {
      grid: { left: 56, right: 56, top: 20, bottom: 72 },
      xAxis: {
        type: 'category',
        data: normalized.xs,
        axisLabel: {
          color: '#6B7280',
          fontSize: 10,
          interval: 0,
          formatter: (v: any) => {
            const s = String(v ?? '');
            if (s.length <= 18) return s;
            return `${s.slice(0, 8)}…${s.slice(-8)}`;
          },
        },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#E5E7EB' } },
      },
      yAxis: [
        {
          type: 'value',
          name: 'tokens',
          nameTextStyle: { color: '#6B7280', fontSize: 10 },
          axisLabel: { color: '#6B7280', fontSize: 10, formatter: (v: any) => fmtInt(v) },
          splitLine: { lineStyle: { color: '#F3F4F6' } },
        },
        {
          type: 'value',
          name: currency ? String(currency) : 'cost',
          nameTextStyle: { color: '#6B7280', fontSize: 10 },
          axisLabel: { color: '#6B7280', fontSize: 10 },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: 'Input',
          type: 'bar',
          stack: 'tokens',
          barMaxWidth: 22,
          data: normalized.tokensIn,
          itemStyle: { color: '#60A5FA' },
        },
        {
          name: 'Output',
          type: 'bar',
          stack: 'tokens',
          barMaxWidth: 22,
          data: normalized.tokensOut,
          itemStyle: { color: '#2563EB' },
        },
        {
          name: 'Total',
          type: 'line',
          data: normalized.totalTokens,
          yAxisIndex: 0,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 1.5, color: '#111827', type: 'dashed' },
        },
        {
          name: 'Cost',
          type: 'line',
          data: normalized.costs,
          yAxisIndex: 1,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 2, color: '#F59E0B', type: 'solid' },
        },
      ],
    };

    chartRef.current.setOption(opt, true);

    const onResize = () => chartRef.current?.resize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [normalized, currency]);

  useEffect(() => {
    return () => {
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  if (!items.length) {
    return (
      <div className="rounded border p-3">
        <div className="text-sm font-medium">{title}</div>
        <div className="text-sm text-gray-500 mt-2">暂无数据</div>
      </div>
    );
  }

  return (
    <div className="rounded border p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-sm font-medium">{title}</div>
          <div className="mt-2 flex flex-wrap gap-3 text-xs text-gray-600">
            {MODEL_COST_SERIES_META.map((item) => (
              <div key={item.label} className="inline-flex items-center gap-1.5">
                <span
                  className="inline-block h-0.5 w-4 rounded-full"
                  style={{ backgroundColor: item.color, borderTop: item.dash === 'solid' ? undefined : `2px ${item.dash} ${item.color}` }}
                />
                <span>{item.label}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="rounded-md bg-gray-50 px-3 py-2 text-xs text-gray-600">
          <div className="font-medium text-gray-700">模型数 {summary.modelCount}</div>
          <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
            <span>Total {fmtInt(summary.totalTokens)}</span>
            <span>Cost {fmtCost(summary.totalCost)}{currency ? ` ${String(currency)}` : ''}</span>
            <span>Top {summary.topModel}</span>
          </div>
        </div>
      </div>
      <div ref={elRef} style={{ height: Math.max(188, height - 32) }} />
    </div>
  );
};
