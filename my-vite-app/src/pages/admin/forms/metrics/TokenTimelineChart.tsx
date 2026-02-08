import React, { useEffect, useMemo, useRef } from 'react';
import * as echarts from 'echarts';
import type { TokenTimelinePointDTO } from '../../../../services/tokenMetricsAdminService';

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
  const chartRef = useRef<echarts.EChartsType | null>(null);

  const normalized = useMemo(() => {
    const b = String(bucket || 'AUTO').toUpperCase();
    const xs = points.map((p) => fmtLabel(p.time, b));
    const inYs = points.map((p) => Number(p.tokensIn || 0));
    const outYs = points.map((p) => Number(p.tokensOut || 0));
    const totalYs = points.map((p) => Number(p.totalTokens || 0));
    return { bucket: b, xs, inYs, outYs, totalYs };
  }, [bucket, points]);

  useEffect(() => {
    if (!elRef.current) return;
    if (!chartRef.current) {
      chartRef.current = echarts.init(elRef.current);
    }

    const opt: echarts.EChartsOption = {
      title: { text: title, left: 'center', textStyle: { fontSize: 12, fontWeight: 600 } },
      grid: { left: 44, right: 18, top: 44, bottom: 32 },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (v: any) => fmtInt(v),
        formatter: (params: any) => {
          const list = Array.isArray(params) ? params : [];
          const axisLabel = list[0]?.axisValueLabel ?? list[0]?.axisValue ?? '';
          const byName = new Map<string, any>();
          for (const p of list) {
            if (p?.seriesName) byName.set(String(p.seriesName), p);
          }
          const total = byName.get('Total Token')?.data ?? 0;
          const inV = byName.get('Input Token')?.data ?? 0;
          const outV = byName.get('Output Token')?.data ?? 0;
          return [
            `<div style="font-size:12px;color:#374151">${axisLabel}</div>`,
            `<div style="margin-top:4px;font-size:12px;color:#111827">Total: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(total)}</span></div>`,
            `<div style="font-size:12px;color:#111827">Input: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(inV)}</span></div>`,
            `<div style="font-size:12px;color:#111827">Output: <span style="font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace">${fmtInt(outV)}</span></div>`,
          ].join('');
        },
      },
      legend: {
        top: 28,
        left: 'center',
        itemWidth: 10,
        itemHeight: 6,
        textStyle: { fontSize: 10, color: '#6B7280' },
      },
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
  }, [title, normalized]);

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

  return <div className="rounded border p-3" ref={elRef} style={{ height: 220 }} />;
};
