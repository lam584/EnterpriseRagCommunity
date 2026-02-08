import React, { useEffect, useMemo, useRef } from 'react';
import * as echarts from 'echarts';
import type { TokenMetricsModelItemDTO } from '../../../../services/tokenMetricsAdminService';

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
  const chartRef = useRef<echarts.EChartsType | null>(null);

  const normalized = useMemo(() => {
    const xs = items.map((it) => formatModelWithProvider(it.model, it.providerId, providerNameById));
    const tokensIn = items.map((it) => Number(it.tokensIn || 0));
    const tokensOut = items.map((it) => Number(it.tokensOut || 0));
    const totalTokens = items.map((it) => Number(it.totalTokens || 0));
    const costs = items.map((it) => toNumber(it.cost) ?? 0);
    return { xs, tokensIn, tokensOut, totalTokens, costs };
  }, [items, providerNameById]);

  useEffect(() => {
    if (!elRef.current) return;
    if (!chartRef.current) chartRef.current = echarts.init(elRef.current);

    const opt: echarts.EChartsOption = {
      title: { text: title, left: 'center', textStyle: { fontSize: 12, fontWeight: 600 } },
      grid: { left: 56, right: 56, top: 54, bottom: 72 },
      legend: {
        top: 28,
        left: 'center',
        itemWidth: 10,
        itemHeight: 6,
        textStyle: { fontSize: 10, color: '#6B7280' },
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const list = Array.isArray(params) ? params : [];
          const axisLabel = list[0]?.axisValueLabel ?? list[0]?.axisValue ?? '';
          const byName = new Map<string, any>();
          for (const p of list) {
            if (p?.seriesName) byName.set(String(p.seriesName), p);
          }
          const inV = byName.get('Input')?.data ?? 0;
          const outV = byName.get('Output')?.data ?? 0;
          const totalV = byName.get('Total')?.data ?? 0;
          const costV = byName.get('Cost')?.data ?? 0;
          const cur = currency ? String(currency) : '';
          return [
            `<div style="font-size:12px;color:#374151">${axisLabel}</div>`,
            `<div style="margin-top:4px;font-size:12px;color:#111827">Total: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(totalV)}</span></div>`,
            `<div style="font-size:12px;color:#111827">Input: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(inV)}</span></div>`,
            `<div style="font-size:12px;color:#111827">Output: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(outV)}</span></div>`,
            `<div style="font-size:12px;color:#111827">Cost: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtCost(costV)}</span>${cur ? ` ${cur}` : ''}</div>`,
          ].join('');
        },
      },
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
          data: normalized.tokensIn,
          itemStyle: { color: '#60A5FA' },
        },
        {
          name: 'Output',
          type: 'bar',
          stack: 'tokens',
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
  }, [title, normalized, currency]);

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

  return <div className="rounded border p-3" ref={elRef} style={{ height: Math.max(220, height) }} />;
};

