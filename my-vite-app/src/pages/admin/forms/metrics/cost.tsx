import React, { useCallback, useEffect, useMemo, useState } from 'react';
import DatePicker from 'react-datepicker';
import {
  adminGetTokenMetrics,
  adminGetTokenTimeline,
  type TokenMetricsModelItemDTO,
  type TokenMetricsResponseDTO,
  type TokenTimelineResponseDTO,
} from '../../../../services/tokenMetricsAdminService';
import {
  computeMaxMetricChartValue,
  type MetricsRangePreset,
  fmtCost,
  fmtInt,
  formatLocalDateTime,
  formatModelWithProvider,
  modelKey,
  resolveRangePresetDates,
  toNumber,
  useAiProviderNameMap,
  useMetricsRangeState,
  useMetricsRequestState,
} from './metricsTimeUtils';
import { TokenTimelineChart } from './TokenTimelineChart';
import { ModelTokenCostChart } from './ModelTokenCostChart';

const CostForm: React.FC = () => {
  const { startDate, setStartDate, endDate, setEndDate, rangePreset, setRangePreset } = useMetricsRangeState();
  const { loading, setLoading, error, setError, resp, setResp, timeline, setTimeline, timelineError, setTimelineError } =
    useMetricsRequestState<TokenMetricsResponseDTO, TokenTimelineResponseDTO>();

  const [chartBy, setChartBy] = useState<'cost' | 'tokens'>('cost');

  const providerNameById = useAiProviderNameMap();

  const items: TokenMetricsModelItemDTO[] = useMemo(() => resp?.items ?? [], [resp]);
  const getModelText = useCallback((it: TokenMetricsModelItemDTO) => formatModelWithProvider(it.model, it.providerId, providerNameById), [providerNameById]);
  const getModelKey = useCallback((it: TokenMetricsModelItemDTO) => modelKey(it.model, it.providerId), []);

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
    return computeMaxMetricChartValue(
      sortedItems,
      chartBy,
      (it) => Number(it.totalTokens || 0),
      (it) => toNumber(it.cost) ?? 0,
    );
  }, [sortedItems, chartBy]);

  const applyRangePreset = useCallback((preset: MetricsRangePreset) => {
    setRangePreset(preset);
    const next = resolveRangePresetDates(preset);
    if (!next) return;
    setStartDate(next.startDate);
    setEndDate(next.endDate);
  }, [setEndDate, setRangePreset, setStartDate]);

  const load = useCallback(
    async () => {
      setLoading(true);
      setError(null);
      setTimelineError(null);
      try {
        const start = formatLocalDateTime(startDate);
        const end = formatLocalDateTime(endDate);
        const [r, t] = await Promise.all([
          adminGetTokenMetrics({ start, end, source: 'MODERATION' }),
          adminGetTokenTimeline({ start, end, source: 'MODERATION' }),
        ]);
        setResp(r);
        setTimeline(t);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        setError(msg);
        setTimelineError(msg);
        setResp(null);
        setTimeline(null);
      } finally {
        setLoading(false);
      }
    },
    [endDate, setError, setLoading, setResp, setTimeline, setTimelineError, startDate],
  );

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h3 className="text-lg font-semibold">审核成本分析</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm text-gray-600">统计来源：</span>
          <span className="text-sm font-medium">文本审核、图片审核、相似检测向量化</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div className="flex flex-col gap-1">
          <div className="text-sm text-gray-600">常用范围</div>
          <select
            className="rounded border px-3 py-2 w-full"
            value={rangePreset}
            onChange={(e) => applyRangePreset(e.target.value as MetricsRangePreset)}
          >
            <option value="CUSTOM">自定义</option>
            <option value="LAST_30M">近 30 分钟</option>
            <option value="LAST_1H">近 1 小时</option>
            <option value="LAST_6H">近 6 小时</option>
            <option value="LAST_12H">近 12 小时</option>
            <option value="LAST_24H">近 24 小时</option>
            <option value="TODAY">今天</option>
            <option value="YESTERDAY">昨天</option>
            <option value="LAST_7D">近 7 天</option>
            <option value="LAST_30D">近 30 天</option>
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-sm text-gray-600">开始时间</div>
          <DatePicker
            selected={startDate}
            onChange={(d) => {
              if (!d) return;
              setRangePreset('CUSTOM');
              setStartDate(d);
              if (d.getTime() > endDate.getTime()) setEndDate(d);
            }}
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
            onChange={(d) => {
              if (!d) return;
              setRangePreset('CUSTOM');
              setEndDate(d);
              if (d.getTime() < startDate.getTime()) setStartDate(d);
            }}
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
            onClick={() => load()}
            disabled={loading}
          >
            {loading ? '计算中...' : '计算'}
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

      {(error || timelineError) && (
        <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error || timelineError}</div>
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
        <div className="space-y-2 w-full max-w-5xl mx-auto">
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
    </div>
  );
};

export default CostForm;
