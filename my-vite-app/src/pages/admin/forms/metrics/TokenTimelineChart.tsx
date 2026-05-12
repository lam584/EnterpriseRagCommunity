import React, { useEffect, useMemo, useRef } from 'react';
import { init, type AppEChartsOption, type AppEChartsType } from './echartsCore';
import type { TokenTimelinePointDTO } from '../../../../services/admin/ai/tokenMetricsAdminService';

const TOKEN_TIMELINE_SERIES_META = [
  { label: 'Total Token', color: '#2563EB', dash: 'solid' },
  { label: 'Input Token', color: '#60A5FA', dash: 'dashed' },
  { label: 'Output Token', color: '#16A34A', dash: 'dotted' },
] as const;

function fmtInt(v: unknown): string {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '—';
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function fmtLabel(time: string, bucket: string): string {
  const t = String(time || '').replace('T', ' ');
  if (bucket === 'DAY') return t.slice(5, 10);
  if (bucket === 'HOUR') return t.slice(5, 16);
  return t.slice(5, 16);
}

export const TokenTimelineChart: React.FC<{
  title: string;
  bucket: string | null | undefined;
  points: TokenTimelinePointDTO[];
}> = ({ title, bucket, points }) => {
  const elRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<AppEChartsType | null>(null);

  const normalized = useMemo(() => {
    const b = String(bucket || 'AUTO').toUpperCase();
    const xs = points.map((p) => fmtLabel(p.time, b));
    const inYs = points.map((p) => Number(p.tokensIn || 0));
    const outYs = points.map((p) => Number(p.tokensOut || 0));
    const totalYs = points.map((p) => Number(p.totalTokens || 0));
    return { bucket: b, xs, inYs, outYs, totalYs };
  }, [bucket, points]);

  const latestSnapshot = useMemo(() => {
    if (!normalized.xs.length) {
      return null;
    }

    const lastIndex = normalized.xs.length - 1;
    return {
      label: normalized.xs[lastIndex],
      total: normalized.totalYs[lastIndex] ?? 0,
      input: normalized.inYs[lastIndex] ?? 0,
      output: normalized.outYs[lastIndex] ?? 0,
    };
  }, [normalized]);

  useEffect(() => {
    if (!elRef.current) return;
    if (!chartRef.current) {
      chartRef.current = init(elRef.current);
    }

    const opt: AppEChartsOption = {
      grid: { left: 44, right: 18, top: 12, bottom: 32 },
      xAxis: {
        type: 'category',
        data: normalized.xs,
        axisLabel: { color: '#6B7280', fontSize: 10, interval: 'auto' },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#E5E7EB' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#6B7280', fontSize: 10 },
        splitLine: { lineStyle: { color: '#F3F4F6' } },
      },
      series: [
        {
          name: 'Total Token',
          type: 'line',
          data: normalized.totalYs,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 2, color: '#2563EB' },
          areaStyle: { color: 'rgba(37,99,235,0.12)' },
        },
        {
          name: 'Input Token',
          type: 'line',
          data: normalized.inYs,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 1.5, color: '#60A5FA', type: 'dashed' },
        },
        {
          name: 'Output Token',
          type: 'line',
          data: normalized.outYs,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 1.5, color: '#16A34A', type: 'dotted' },
        },
      ],
    };

    chartRef.current.setOption(opt, true);

    const onResize = () => chartRef.current?.resize();
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('resize', onResize);
    };
  }, [normalized]);

  useEffect(() => {
    return () => {
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  if (!points.length) {
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
            {TOKEN_TIMELINE_SERIES_META.map((item) => (
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
        {latestSnapshot ? (
          <div className="rounded-md bg-gray-50 px-3 py-2 text-xs text-gray-600">
            <div className="font-medium text-gray-700">最新桶 {latestSnapshot.label}</div>
            <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
              <span>Total {fmtInt(latestSnapshot.total)}</span>
              <span>Input {fmtInt(latestSnapshot.input)}</span>
              <span>Output {fmtInt(latestSnapshot.output)}</span>
            </div>
          </div>
        ) : null}
      </div>
      <div ref={elRef} style={{ height: 188 }} />
    </div>
  );
};
