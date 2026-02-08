import React, { useCallback, useEffect, useMemo, useState } from 'react';
import DatePicker from 'react-datepicker';
import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';
import {
  adminGetTokenMetrics,
  adminGetTokenTimeline,
  type TokenMetricsModelItemDTO,
  type TokenMetricsResponseDTO,
  type TokenTimelineResponseDTO,
} from '../../../../services/tokenMetricsAdminService';
import { TokenTimelineChart } from './TokenTimelineChart';
import { ModelTokenCostChart } from './ModelTokenCostChart';

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function formatLocalDateTime(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(
    d.getSeconds(),
  )}`;
}

function dayStart(d: Date): Date {
  const x = new Date(d.getTime());
  x.setHours(0, 0, 0, 0);
  return x;
}

function dayEnd(d: Date): Date {
  const x = new Date(d.getTime());
  x.setHours(23, 59, 59, 0);
  return x;
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

function fmtCost(v: unknown): string {
  const n = toNumber(v);
  if (n === null) return '0';
  return n.toFixed(6).replace(/\.?0+$/, '');
}

function fmtInt(v: unknown): string {
  const n = toNumber(v);
  if (n === null) return '0';
  return String(Math.round(n));
}

function trimStr(v: unknown): string {
  return String(v ?? '').trim();
}

function formatModelWithProvider(model: unknown, providerId: unknown, providerNameById: Record<string, string>): string {
  const m = trimStr(model);
  const pid = trimStr(providerId);
  if (!pid) return m || '—';
  const providerName = providerNameById[pid] || pid;
  if (providerName && m) return `${providerName}：${m}`;
  return providerName || m || '—';
}

function modelKey(model: unknown, providerId: unknown): string {
  const m = trimStr(model);
  const pid = trimStr(providerId);
  return pid ? `${pid}|${m}` : m;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

type ModerationCostRecordDTO = {
  id: number;
  contentType?: string | null;
  contentId?: number | null;
  verdict?: string | null;
  model: string;
  providerId?: string | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  totalTokens?: number | null;
  cost?: string | number | null;
  priceMissing?: boolean | null;
  decidedAt?: string | null;
};

type ModerationCostRecordsResponseDTO = {
  start: string;
  end: string;
  currency?: string | null;
  totalTokens?: number | null;
  totalCost?: string | number | null;
  content: ModerationCostRecordDTO[];
  totalPages: number;
  totalElements: number;
  page: number;
  pageSize: number;
};

async function adminListModerationCostRecords(params: { start?: string; end?: string; page?: number; pageSize?: number }): Promise<ModerationCostRecordsResponseDTO> {
  const qs = buildQuery({ start: params.start, end: params.end, page: params.page, pageSize: params.pageSize });
  const res = await fetch(apiUrl(`/api/admin/metrics/cost/moderation${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核调用明细失败');
  return data as ModerationCostRecordsResponseDTO;
}

const CostForm: React.FC = () => {
  const [startDate, setStartDate] = useState<Date>(() => dayStart(new Date(Date.now() - 7 * 24 * 3600 * 1000)));
  const [endDate, setEndDate] = useState<Date>(() => dayEnd(new Date()));

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resp, setResp] = useState<TokenMetricsResponseDTO | null>(null);
  const [timeline, setTimeline] = useState<TokenTimelineResponseDTO | null>(null);
  const [timelineError, setTimelineError] = useState<string | null>(null);

  const [chartBy, setChartBy] = useState<'cost' | 'tokens'>('cost');

  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState<string | null>(null);
  const [details, setDetails] = useState<ModerationCostRecordsResponseDTO | null>(null);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [providerNameById, setProviderNameById] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const cfg = await adminGetAiProvidersConfig();
        if (cancelled) return;
        const map: Record<string, string> = {};
        for (const p of cfg.providers ?? []) {
          const id = trimStr(p?.id);
          if (!id) continue;
          const name = trimStr(p?.name);
          map[id] = name || id;
        }
        setProviderNameById(map);
      } catch {
        if (!cancelled) setProviderNameById({});
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const items: TokenMetricsModelItemDTO[] = useMemo(() => resp?.items ?? [], [resp]);
  const getModelText = useCallback((it: TokenMetricsModelItemDTO) => formatModelWithProvider(it.model, it.providerId, providerNameById), [providerNameById]);
  const getModelKey = useCallback((it: TokenMetricsModelItemDTO) => modelKey(it.model, it.providerId), []);
  const getDetailsModelText = useCallback(
    (r: ModerationCostRecordDTO) => formatModelWithProvider(r.model, r.providerId, providerNameById),
    [providerNameById],
  );

  const sortedItems = useMemo(() => {
    const arr = [...items];
    arr.sort((a, b) => {
      const ca = toNumber(a.cost) ?? 0;
      const cb = toNumber(b.cost) ?? 0;
      if (ca !== cb) return cb - ca;
      return Number(b.totalTokens || 0) - Number(a.totalTokens || 0);
    });
    return arr;
  }, [items]);

  const maxChartValue = useMemo(() => {
    let max = 0;
    for (const it of sortedItems) {
      const v = chartBy === 'tokens' ? Number(it.totalTokens || 0) : toNumber(it.cost) ?? 0;
      if (v > max) max = v;
    }
    return max <= 0 ? 1 : max;
  }, [sortedItems, chartBy]);

  const load = useCallback(
    async (p?: number) => {
      setLoading(true);
      setError(null);
      setTimelineError(null);
      setDetailsLoading(true);
      setDetailsError(null);
      try {
        const start = formatLocalDateTime(dayStart(startDate));
        const end = formatLocalDateTime(dayEnd(endDate));
        const nextPage = p ?? page;
        const [r, t, d] = await Promise.all([
          adminGetTokenMetrics({ start, end, source: 'MODERATION' }),
          adminGetTokenTimeline({ start, end, source: 'MODERATION' }),
          adminListModerationCostRecords({ start, end, page: nextPage, pageSize }),
        ]);
        setResp(r);
        setTimeline(t);
        setDetails(d);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        setError(msg);
        setTimelineError(msg);
        setDetailsError(msg);
        setResp(null);
        setTimeline(null);
        setDetails(null);
      } finally {
        setLoading(false);
        setDetailsLoading(false);
      }
    },
    [startDate, endDate, page, pageSize],
  );

  useEffect(() => {
    load(1);
    setPage(1);
  }, [startDate, endDate]);

  const gotoPage = useCallback(
    async (p: number) => {
      const next = Math.max(1, p);
      setPage(next);
      await load(next);
    },
    [load],
  );

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h3 className="text-lg font-semibold">审核成本分析</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm text-gray-600">统计来源：</span>
          <span className="text-sm font-medium">文本审核、图片审核、相似检测向量化</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div className="flex flex-col gap-1">
          <div className="text-sm text-gray-600">开始时间</div>
          <DatePicker
            selected={startDate}
            onChange={(d) => d && setStartDate(dayStart(d))}
            showTimeSelect
            timeFormat="HH:mm"
            timeIntervals={30}
            dateFormat="yyyy-MM-dd HH:mm"
            className="rounded border px-3 py-2 w-full"
          />
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-sm text-gray-600">结束时间</div>
          <DatePicker
            selected={endDate}
            onChange={(d) => d && setEndDate(dayEnd(d))}
            showTimeSelect
            timeFormat="HH:mm"
            timeIntervals={30}
            dateFormat="yyyy-MM-dd HH:mm"
            className="rounded border px-3 py-2 w-full"
          />
        </div>
        <div className="flex items-end gap-2">
          <button
            className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-50"
            onClick={() => load(1)}
            disabled={loading || detailsLoading}
          >
            {loading || detailsLoading ? '计算中...' : '计算'}
          </button>
          <div className="flex items-center gap-2">
            <button
              className={`rounded px-3 py-2 border ${chartBy === 'cost' ? 'bg-gray-900 text-white border-gray-900' : 'bg-white text-gray-700'}`}
              onClick={() => setChartBy('cost')}
            >
              费用
            </button>
            <button
              className={`rounded px-3 py-2 border ${chartBy === 'tokens' ? 'bg-gray-900 text-white border-gray-900' : 'bg-white text-gray-700'}`}
              onClick={() => setChartBy('tokens')}
            >
              Token
            </button>
          </div>
        </div>
      </div>

      {(error || timelineError || detailsError) && (
        <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error || timelineError || detailsError}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div className="rounded border bg-gray-50 px-3 py-2">
          <div className="text-xs text-gray-600">总 Token</div>
          <div className="text-lg font-semibold">{fmtInt(resp?.totalTokens ?? 0)}</div>
        </div>
        <div className="rounded border bg-gray-50 px-3 py-2">
          <div className="text-xs text-gray-600">总费用</div>
          <div className="text-lg font-semibold">
            {fmtCost(resp?.totalCost ?? 0)} {resp?.currency ? String(resp.currency) : ''}
          </div>
        </div>
        <div className="rounded border bg-gray-50 px-3 py-2">
          <div className="text-xs text-gray-600">模型数</div>
          <div className="text-lg font-semibold">{items.length}</div>
        </div>
      </div>

      <TokenTimelineChart title="Token 消耗趋势（文本审核/图片审核/相似检测向量化）" bucket={timeline?.bucket} points={timeline?.points ?? []} />

      <div className="space-y-2">
        <div className="text-sm font-medium">按模型聚合</div>
        <ModelTokenCostChart title="按模型 Token/费用对比" items={sortedItems.slice(0, 30)} currency={resp?.currency} providerNameById={providerNameById} />
        <div className="space-y-2">
          {sortedItems.map((it) => {
            const v = chartBy === 'tokens' ? Number(it.totalTokens || 0) : toNumber(it.cost) ?? 0;
            const pct = Math.max(0, Math.min(1, v / maxChartValue));
            return (
              <div key={getModelKey(it)} className="flex items-center gap-3">
                <div className="w-56 text-sm truncate" title={getModelText(it)}>
                  {getModelText(it)}
                </div>
                <div className="flex-1">
                  <div className="h-3 bg-gray-100 rounded">
                    <div className="h-3 bg-blue-600 rounded" style={{ width: `${pct * 100}%` }} />
                  </div>
                </div>
                <div className="w-44 text-right text-sm tabular-nums">
                  {chartBy === 'tokens' ? fmtInt(it.totalTokens) : fmtCost(it.cost)}
                  {chartBy === 'cost' && resp?.currency ? ` ${String(resp.currency)}` : ''}
                </div>
              </div>
            );
          })}
          {sortedItems.length === 0 && <div className="text-sm text-gray-500">暂无数据</div>}
        </div>
      </div>

      <div className="space-y-2">
        <div className="text-sm font-medium">模型明细</div>
        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2">模型</th>
                <th className="text-right px-3 py-2">Input</th>
                <th className="text-right px-3 py-2">Output</th>
                <th className="text-right px-3 py-2">Total</th>
                <th className="text-right px-3 py-2">费用</th>
                <th className="text-left px-3 py-2">价格</th>
              </tr>
            </thead>
            <tbody>
              {sortedItems.map((it) => (
                <tr key={getModelKey(it)} className="border-t">
                  <td className="px-3 py-2" title={getModelText(it)}>
                    {getModelText(it)}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(it.tokensIn)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(it.tokensOut)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(it.totalTokens)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {fmtCost(it.cost)} {resp?.currency ? String(resp.currency) : ''}
                  </td>
                  <td className="px-3 py-2">{it.priceMissing ? <span className="text-amber-700">缺失</span> : <span className="text-green-700">OK</span>}</td>
                </tr>
              ))}
              {sortedItems.length === 0 && (
                <tr>
                  <td className="px-3 py-3 text-gray-500" colSpan={6}>
                    暂无数据
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="text-sm font-medium">审核调用明细</div>
          <div className="text-xs text-gray-600">
            {details ? `共 ${details.totalElements} 条，页码 ${details.page}/${details.totalPages}` : ''}
          </div>
        </div>
        <div className="text-xs text-gray-500">说明：此处为审核 LLM 调用明细，不包含相似检测向量化调用。</div>
        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2">时间</th>
                <th className="text-left px-3 py-2">内容</th>
                <th className="text-left px-3 py-2">模型</th>
                <th className="text-right px-3 py-2">Total</th>
                <th className="text-right px-3 py-2">费用</th>
                <th className="text-left px-3 py-2">结论</th>
              </tr>
            </thead>
            <tbody>
              {(details?.content ?? []).map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="px-3 py-2 whitespace-nowrap">{r.decidedAt ? String(r.decidedAt).replace('T', ' ') : '-'}</td>
                  <td className="px-3 py-2 whitespace-nowrap">
                    {r.contentType ?? '-'}#{r.contentId ?? '-'}
                  </td>
                  <td className="px-3 py-2" title={getDetailsModelText(r)}>
                    {getDetailsModelText(r)}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(r.totalTokens)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {fmtCost(r.cost)} {details?.currency ? String(details.currency) : ''}
                  </td>
                  <td className="px-3 py-2">{r.verdict ?? '-'}</td>
                </tr>
              ))}
              {(details?.content ?? []).length === 0 && (
                <tr>
                  <td className="px-3 py-3 text-gray-500" colSpan={6}>
                    {detailsLoading ? '加载中...' : '暂无数据'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-end gap-2">
          <button
            className="rounded border px-3 py-2 text-sm disabled:opacity-50"
            onClick={() => gotoPage((details?.page ?? page) - 1)}
            disabled={detailsLoading || (details?.page ?? page) <= 1}
          >
            上一页
          </button>
          <button
            className="rounded border px-3 py-2 text-sm disabled:opacity-50"
            onClick={() => gotoPage((details?.page ?? page) + 1)}
            disabled={detailsLoading || (details?.totalPages ?? 1) <= (details?.page ?? page)}
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
};

export default CostForm;
