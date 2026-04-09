/* eslint-disable react-refresh/only-export-components */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { LlmQueueSampleDTO, LlmQueueTaskType } from '../../../../services/llmQueueAdminService';
import { formatDurationMs, formatHmsTime } from './metricsTimeUtils';

export function fmtMs(v: unknown): string {
  return formatDurationMs(v);
}

export function fmtNum(v: unknown): string {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '—';
  if (Math.abs(n) >= 1000) return String(Math.round(n));
  return n.toFixed(2).replace(/\.?0+$/, '');
}

export function fmtTs(v: string): string {
  return formatHmsTime(v, v);
}

export function fmtHmsTs(v: string | null | undefined): string {
  return formatHmsTime(v);
}

export function fmtType(t: LlmQueueTaskType | string | undefined | null): string {
  return (t || 'UNKNOWN').toString().trim().toUpperCase();
}

export function normalizeSamples(samples: LlmQueueSampleDTO[] | null | undefined): LlmQueueSampleDTO[] {
  if (!samples || samples.length === 0) return [];
  return samples
    .filter((s) => s && typeof s === 'object' && typeof s.ts === 'string')
    .slice(-600);
}

export function sliceSamplesByWindow(samples: LlmQueueSampleDTO[], windowSec: number): LlmQueueSampleDTO[] {
  const ws = Math.max(1, Math.floor(Number(windowSec) || 0));
  if (samples.length === 0) return samples;
  const last = samples[samples.length - 1];
  const endMs = Number.isFinite(Date.parse(last.ts)) ? Date.parse(last.ts) : Date.now();
  const startMs = endMs - ws * 1000;
  return samples.filter((s) => {
    const t = Date.parse(s.ts);
    return Number.isFinite(t) && t >= startMs;
  });
}

export const SparkLine: React.FC<{
  samples: LlmQueueSampleDTO[];
  value: (s: LlmQueueSampleDTO) => number;
  height?: number;
  color?: string;
  lineWidth?: number;
  axisFontSize?: number;
  unitFontSize?: number;
  xUnit?: string;
  yUnit?: string;
  formatX?: (s: LlmQueueSampleDTO) => string;
  formatY?: (v: number) => string;
}> = ({
  samples,
  value,
  height = 56,
  color = '#2563eb',
  lineWidth = 1.25,
  axisFontSize = 14,
  unitFontSize = 14,
  xUnit = '时间',
  yUnit,
  formatX = (s) => fmtTs(s.ts),
  formatY = (v) => fmtNum(v),
}) => {
  const h = Math.max(24, height);
  const margins = { left: 36, right: 8, top: 10, bottom: 22 };
  const [w, setW] = useState(320);
  const iw = Math.max(1, w - margins.left - margins.right);
  const ih = Math.max(1, h - margins.top - margins.bottom);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const [hover, setHover] = useState<{
    i: number;
    x: number;
    y: number;
    rx: number;
    ry: number;
  } | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const update = () => {
      const next = Math.max(180, Math.floor(el.clientWidth || 0));
      setW((prev) => (prev === next ? prev : next));
    };
    update();
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(() => update());
      ro.observe(el);
      return () => ro.disconnect();
    }
    const onResize = () => update();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const vs = samples.map(value).map((x) => (Number.isFinite(x) ? x : 0));
  const max = vs.length ? Math.max(...vs, 1) : 1;
  const min = 0;

  const plot = useMemo(() => {
    if (!samples.length) return { dx: 0, pts: [] as { x: number; y: number; v: number }[], points: '' };
    const dx = iw / Math.max(1, samples.length - 1);
    const toY = (v: number) => {
      const t = (v - min) / Math.max(1e-9, max - min);
      const y = margins.top + (1 - t) * ih;
      return Math.max(margins.top, Math.min(margins.top + ih, y));
    };
    const pts = vs.map((v, i) => {
      const x = margins.left + i * dx;
      return { x, y: toY(v), v };
    });
    const points = pts.map((p) => `${Math.round(p.x)},${Math.round(p.y)}`).join(' ');
    return { dx, pts, points };
  }, [samples.length, vs, max, min, iw, ih, margins.left, margins.top]);

  const x0 = margins.left;
  const x1 = w - margins.right;
  const y0 = margins.top;
  const y1 = margins.top + ih;

  const onPointerMove = useCallback(
    (e: React.PointerEvent<SVGSVGElement>) => {
      if (!containerRef.current || plot.pts.length === 0) return;
      const rect = containerRef.current.getBoundingClientRect();
      const rx = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
      const ry = Math.max(0, Math.min(rect.height, e.clientY - rect.top));
      const x = (rx / Math.max(1, rect.width)) * w;
      const raw = plot.dx <= 0 ? 0 : (x - margins.left) / plot.dx;
      const i = Math.max(0, Math.min(plot.pts.length - 1, Math.round(raw)));
      const p = plot.pts[i];
      setHover({ i, x: p.x, y: p.y, rx, ry });
    },
    [plot.dx, plot.pts, margins.left, w],
  );

  const onPointerLeave = useCallback(() => setHover(null), []);

  return (
    <div ref={containerRef} className="relative w-full">
      <svg viewBox={`0 0 ${w} ${h}`} className="w-full block" onPointerMove={onPointerMove} onPointerLeave={onPointerLeave}>
        <line x1={x0} y1={y1} x2={x1} y2={y1} stroke="#e5e7eb" strokeWidth="1" />
        <line x1={x0} y1={y0} x2={x0} y2={y1} stroke="#e5e7eb" strokeWidth="1" />

        <line x1={x0} y1={y0} x2={x1} y2={y0} stroke="#f3f4f6" strokeWidth="1" />
        <line x1={x0} y1={margins.top + ih / 2} x2={x1} y2={margins.top + ih / 2} stroke="#f3f4f6" strokeWidth="1" />

        <text x={x0 - 4} y={y1} textAnchor="end" dominantBaseline="middle" fontSize={axisFontSize} fill="#6b7280">
          0
        </text>
        <text x={x0 - 4} y={y0} textAnchor="end" dominantBaseline="middle" fontSize={axisFontSize} fill="#6b7280">
          {formatY(max)}
        </text>
        {yUnit ? (
          <text x={2} y={y0} textAnchor="start" dominantBaseline="hanging" fontSize={unitFontSize} fill="#6b7280">
            {yUnit}
          </text>
        ) : null}

        {samples.length ? (
          <>
            <text x={x0} y={h - 12} textAnchor="start" dominantBaseline="middle" fontSize={axisFontSize} fill="#6b7280">
              {formatX(samples[0])}
            </text>
            <text x={x1} y={h - 12} textAnchor="end" dominantBaseline="middle" fontSize={axisFontSize} fill="#6b7280">
              {formatX(samples[samples.length - 1])}
            </text>
          </>
        ) : null}
        <text x={(x0 + x1) / 2} y={h - 2} textAnchor="middle" dominantBaseline="ideographic" fontSize={unitFontSize} fill="#6b7280">
          {xUnit}
        </text>

        <polyline fill="none" stroke={color} strokeWidth={String(lineWidth)} strokeLinejoin="round" strokeLinecap="round" points={plot.points} />

        {hover ? (
          <>
            <line x1={hover.x} y1={y0} x2={hover.x} y2={y1} stroke="#d1d5db" strokeWidth="1" />
            <circle cx={hover.x} cy={hover.y} r="2.5" fill={color} stroke="#ffffff" strokeWidth="1" />
          </>
        ) : null}
      </svg>

      {hover ? (
        <div
          className="absolute z-10 pointer-events-none rounded border bg-white shadow-sm px-2 py-1 text-xs"
          style={{
            left: Math.min(hover.rx + 10, Math.max(0, (containerRef.current?.clientWidth ?? 0) - 140)),
            top: Math.max(0, hover.ry - 34),
            width: 140,
          }}
        >
          <div className="text-gray-600">{samples[hover.i] ? formatX(samples[hover.i]) : '—'}</div>
          <div className="font-mono">
            {formatY(plot.pts[hover.i]?.v ?? 0)}
            {yUnit ? ` ${yUnit}` : ''}
          </div>
        </div>
      ) : null}
    </div>
  );
};
