import React, { useCallback, useEffect, useMemo, useState } from 'react';
import DatePicker from 'react-datepicker';
import {
  adminGetTokenMetrics,
  adminGetTokenTimeline,
  adminListLlmPriceConfigs,
  adminListTokenSources,
  adminUpsertLlmPriceConfig,
  type AdminLlmPriceConfigDTO,
  type AdminTokenSourceDTO,
  type TokenMetricsModelItemDTO,
  type TokenMetricsResponseDTO,
  type TokenTimelineResponseDTO,
} from '../../../../services/tokenMetricsAdminService';
import {
  computeMaxMetricChartValue,
  fmtCost,
  fmtInt,
  formatLocalDateTime,
  formatModelWithProvider,
  type MetricsRangePreset,
  modelKey,
  resolveRangePresetDates,
  toNumber,
  useAiProviderNameMap,
  useMetricsRangeState,
  useMetricsRequestState,
} from './metricsTimeUtils';
import { TokenTimelineChart } from './TokenTimelineChart';
import { ModelTokenCostChart } from './ModelTokenCostChart';

type SortKey = 'cost' | 'totalTokens' | 'model';
type SortDir = 'asc' | 'desc';

type PricingMode = 'DEFAULT' | 'NON_THINKING' | 'THINKING';
type TokenSource = string;
type PricingUnit = 'PER_1K' | 'PER_1M';
type PricingStrategy = 'FLAT' | 'TIERED';

type PriceTierEditState = {
  upToTokens: string;
  inputCostPerUnit: string;
  outputCostPerUnit: string;
};

function updateTierField(
  setPriceEdits: React.Dispatch<React.SetStateAction<Record<string, PriceEditState>>>,
  model: string,
  fallbackState: PriceEditState | undefined,
  idx: number,
  patch: Partial<PriceTierEditState>,
) {
  setPriceEdits((prev) => {
    const cur = prev[model] || fallbackState;
    if (!cur) return prev;
    const next = [...cur.tiers];
    next[idx] = { ...next[idx], ...patch };
    return { ...prev, [model]: { ...cur, tiers: next, saving: false, ok: false } };
  });
}

type PriceEditState = {
  currency: string;
  strategy: PricingStrategy;
  unit: PricingUnit;
  defaultInputCostPerUnit: string;
  defaultOutputCostPerUnit: string;
  nonThinkingInputCostPerUnit: string;
  nonThinkingOutputCostPerUnit: string;
  thinkingInputCostPerUnit: string;
  thinkingOutputCostPerUnit: string;
  tiers: PriceTierEditState[];
  tiersOpen?: boolean;
  saving: boolean;
  error?: string | null;
  ok?: boolean;
};

const TokenForm: React.FC = () => {
  const { startDate, setStartDate, endDate, setEndDate, rangePreset, setRangePreset } = useMetricsRangeState();
  const { loading, setLoading, error, setError, resp, setResp, timeline, setTimeline, timelineError, setTimelineError } =
    useMetricsRequestState<TokenMetricsResponseDTO, TokenTimelineResponseDTO>();

  const [sortKey, setSortKey] = useState<SortKey>('cost');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [chartBy, setChartBy] = useState<'cost' | 'tokens'>('cost');
  const [pricingMode, setPricingMode] = useState<PricingMode>('DEFAULT');
  const [source, setSource] = useState<TokenSource>('ALL');

  const providerNameById = useAiProviderNameMap();
  const [tokenSources, setTokenSources] = useState<AdminTokenSourceDTO[]>([]);

  const [pricePanelOpen, setPricePanelOpen] = useState(false);
  const [pricesLoading, setPricesLoading] = useState(false);
  const [pricesError, setPricesError] = useState<string | null>(null);
  const [prices, setPrices] = useState<AdminLlmPriceConfigDTO[] | null>(null);
  const [priceEdits, setPriceEdits] = useState<Record<string, PriceEditState>>({});

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const list = await adminListTokenSources();
        if (cancelled) return;
        const arr = Array.isArray(list) ? [...list] : [];
        arr.sort((a, b) => {
          const sa = typeof a?.sortIndex === 'number' ? a.sortIndex : 0;
          const sb = typeof b?.sortIndex === 'number' ? b.sortIndex : 0;
          if (sa !== sb) return sa - sb;
          return String(a?.taskType ?? '').localeCompare(String(b?.taskType ?? ''), 'en');
        });
        setTokenSources(arr);
      } catch {
        if (!cancelled) setTokenSources([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const tokenSourceMetaByTaskType = useMemo(() => {
    const map = new Map<string, AdminTokenSourceDTO>();
    for (const s of tokenSources) {
      const tt = String(s?.taskType ?? '').trim().toUpperCase();
      if (!tt) continue;
      if (!map.has(tt)) map.set(tt, s);
    }
    return map;
  }, [tokenSources]);

  const sourceLabel = useMemo(() => {
    const tt = String(source ?? '').trim().toUpperCase();
    if (!tt || tt === 'ALL') return '全部场景';
    const meta = tokenSourceMetaByTaskType.get(tt);
    return meta?.label || tt;
  }, [source, tokenSourceMetaByTaskType]);

  const tokenSourcesByCategory = useMemo(() => {
    const out: Record<string, AdminTokenSourceDTO[]> = { TEXT_GEN: [], EMBEDDING: [], RERANK: [] };
    for (const s of tokenSources) {
      const cat = String(s?.category ?? '').trim().toUpperCase();
      if (cat === 'TEXT_GEN') out.TEXT_GEN.push(s);
      else if (cat === 'EMBEDDING') out.EMBEDDING.push(s);
      else if (cat === 'RERANK') out.RERANK.push(s);
      else out.TEXT_GEN.push(s);
    }
    return out;
  }, [tokenSources]);

  const items: TokenMetricsModelItemDTO[] = useMemo(() => resp?.items ?? [], [resp]);
  const getModelText = useCallback((it: TokenMetricsModelItemDTO) => formatModelWithProvider(it.model, it.providerId, providerNameById), [providerNameById]);
  const getModelKey = useCallback((it: TokenMetricsModelItemDTO) => modelKey(it.model, it.providerId), []);

  const sortedItems = useMemo(() => {
    const arr = [...items];
    const dir = sortDir === 'asc' ? 1 : -1;
    arr.sort((a, b) => {
      if (sortKey === 'model') {
        return dir * String(a.model || '').localeCompare(String(b.model || ''), 'zh-Hans-CN');
      }
      if (sortKey === 'totalTokens') {
        return dir * (Number(a.totalTokens || 0) - Number(b.totalTokens || 0));
      }
      const ca = toNumber(a.cost) ?? 0;
      const cb = toNumber(b.cost) ?? 0;
      if (ca !== cb) return dir * (ca - cb);
      return dir * (Number(a.totalTokens || 0) - Number(b.totalTokens || 0));
    });
    return arr;
  }, [items, sortDir, sortKey]);

  const maxChartValue = useMemo(
    () =>
      computeMaxMetricChartValue(
        sortedItems,
        chartBy,
        (it) => Number(it.totalTokens || 0),
        (it) => toNumber(it.cost) ?? 0,
      ),
    [sortedItems, chartBy],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTimelineError(null);
      const start = formatLocalDateTime(startDate);
      const end = formatLocalDateTime(endDate);
      const [r, t] = await Promise.all([
        adminGetTokenMetrics({ start, end, source, pricingMode }),
        adminGetTokenTimeline({ start, end, source }),
      ]);
      setResp(r);
      setTimeline(t);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setResp(null);
      setTimeline(null);
      setTimelineError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate, pricingMode, source]);

  const loadPrices = useCallback(async () => {
    setPricesLoading(true);
    setPricesError(null);
    try {
      const list = await adminListLlmPriceConfigs();
      setPrices(list);
    } catch (e) {
      setPricesError(e instanceof Error ? e.message : String(e));
      setPrices(null);
    } finally {
      setPricesLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const applyRangePreset = useCallback((preset: MetricsRangePreset) => {
    setRangePreset(preset);
    const next = resolveRangePresetDates(preset);
    if (!next) return;
    setStartDate(next.startDate);
    setEndDate(next.endDate);
  }, []);

  const toggleSort = useCallback(
    (k: SortKey) => {
      if (sortKey === k) {
        setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
        return;
      }
      setSortKey(k);
      setSortDir(k === 'model' ? 'asc' : 'desc');
    },
    [sortKey],
  );

  const modelsInRange = useMemo(() => {
    const ms = new Set<string>();
    for (const it of items) {
      if (it?.model) ms.add(it.model);
    }
    return Array.from(ms).sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'));
  }, [items]);

  const priceByName = useMemo(() => {
    const m = new Map<string, AdminLlmPriceConfigDTO>();
    for (const p of prices ?? []) {
      if (!p?.name) continue;
      m.set(p.name, p);
    }
    return m;
  }, [prices]);

  const mergedPriceRows = useMemo(() => {
    return modelsInRange.map((model) => {
      const p = priceByName.get(model);
      return {
        model,
        currency: p?.currency ?? 'CNY',
        inputCostPer1k: p?.inputCostPer1k ?? null,
        outputCostPer1k: p?.outputCostPer1k ?? null,
        pricing: p?.pricing ?? null,
        updatedAt: p?.updatedAt ?? null,
      };
    });
  }, [modelsInRange, priceByName]);

  const ensureEditState = useCallback((model: string, row: { currency: string; inputCostPer1k: unknown; outputCostPer1k: unknown; pricing?: any }) => {
    setPriceEdits((prev) => {
      if (prev[model]) return prev;
      const pricing = row.pricing || null;
      const unit: PricingUnit = pricing?.unit === 'PER_1M' ? 'PER_1M' : 'PER_1K';
      const strategy: PricingStrategy = pricing?.strategy === 'TIERED' ? 'TIERED' : 'FLAT';

      const legacyInPer1k = toNumber(row.inputCostPer1k);
      const legacyOutPer1k = toNumber(row.outputCostPer1k);
      const legacyInPerUnit = legacyInPer1k === null ? '' : String(unit === 'PER_1M' ? legacyInPer1k * 1000 : legacyInPer1k);
      const legacyOutPerUnit = legacyOutPer1k === null ? '' : String(unit === 'PER_1M' ? legacyOutPer1k * 1000 : legacyOutPer1k);

      const tiers: PriceTierEditState[] = Array.isArray(pricing?.tiers)
        ? pricing.tiers
            .map((t: any) => ({
              upToTokens: t?.upToTokens === null || t?.upToTokens === undefined ? '' : String(t.upToTokens),
              inputCostPerUnit: t?.inputCostPerUnit === null || t?.inputCostPerUnit === undefined ? '' : String(t.inputCostPerUnit),
              outputCostPerUnit: t?.outputCostPerUnit === null || t?.outputCostPerUnit === undefined ? '' : String(t.outputCostPerUnit),
            }))
            .filter((t: PriceTierEditState) => !!t.upToTokens)
        : [];
      const tiersOrDefault =
        strategy === 'TIERED' && tiers.length === 0
          ? [
              { upToTokens: '32000', inputCostPerUnit: '', outputCostPerUnit: '' },
              { upToTokens: '128000', inputCostPerUnit: '', outputCostPerUnit: '' },
              { upToTokens: '256000', inputCostPerUnit: '', outputCostPerUnit: '' },
              { upToTokens: '1000000', inputCostPerUnit: '', outputCostPerUnit: '' },
            ]
          : tiers;

      return {
        ...prev,
        [model]: {
          currency: row.currency || 'CNY',
          strategy,
          unit,
          defaultInputCostPerUnit:
            pricing?.defaultInputCostPerUnit === null || pricing?.defaultInputCostPerUnit === undefined
              ? legacyInPerUnit
              : String(pricing.defaultInputCostPerUnit),
          defaultOutputCostPerUnit:
            pricing?.defaultOutputCostPerUnit === null || pricing?.defaultOutputCostPerUnit === undefined
              ? legacyOutPerUnit
              : String(pricing.defaultOutputCostPerUnit),
          nonThinkingInputCostPerUnit:
            pricing?.nonThinkingInputCostPerUnit === null || pricing?.nonThinkingInputCostPerUnit === undefined
              ? ''
              : String(pricing.nonThinkingInputCostPerUnit),
          nonThinkingOutputCostPerUnit:
            pricing?.nonThinkingOutputCostPerUnit === null || pricing?.nonThinkingOutputCostPerUnit === undefined
              ? ''
              : String(pricing.nonThinkingOutputCostPerUnit),
          thinkingInputCostPerUnit:
            pricing?.thinkingInputCostPerUnit === null || pricing?.thinkingInputCostPerUnit === undefined
              ? ''
              : String(pricing.thinkingInputCostPerUnit),
          thinkingOutputCostPerUnit:
            pricing?.thinkingOutputCostPerUnit === null || pricing?.thinkingOutputCostPerUnit === undefined
              ? ''
              : String(pricing.thinkingOutputCostPerUnit),
          tiers: tiersOrDefault,
          tiersOpen: false,
          saving: false,
          error: null,
          ok: false,
        },
      };
    });
  }, []);

  useEffect(() => {
    if (!pricePanelOpen) return;
    if (prices === null && !pricesLoading && !pricesError) {
      loadPrices();
    }
  }, [pricePanelOpen, prices, pricesLoading, pricesError, loadPrices]);

  useEffect(() => {
    if (!pricePanelOpen) return;
    for (const row of mergedPriceRows) {
      ensureEditState(row.model, row);
    }
  }, [pricePanelOpen, mergedPriceRows, ensureEditState]);

  const savePrice = useCallback(async (model: string) => {
    const st = priceEdits[model];
    if (!st || st.saving) return;
    setPriceEdits((prev) => ({
      ...prev,
      [model]: { ...prev[model], saving: true, error: null, ok: false },
    }));
    try {
      const defaultIn = toNumber(st.defaultInputCostPerUnit);
      const defaultOut = toNumber(st.defaultOutputCostPerUnit);
      const nonThinkingIn = toNumber(st.nonThinkingInputCostPerUnit);
      const nonThinkingOut = toNumber(st.nonThinkingOutputCostPerUnit);
      const thinkingIn = toNumber(st.thinkingInputCostPerUnit);
      const thinkingOut = toNumber(st.thinkingOutputCostPerUnit);
      const tiers =
        st.strategy === 'TIERED'
          ? st.tiers.reduce((acc, t) => {
              const upTo = toNumber(t.upToTokens);
              if (upTo === null) return acc;
              const inCost = toNumber(t.inputCostPerUnit);
              const outCost = toNumber(t.outputCostPerUnit);
              acc.push({
                upToTokens: Math.max(1, Math.round(upTo)),
                inputCostPerUnit: inCost === null ? undefined : inCost,
                outputCostPerUnit: outCost === null ? undefined : outCost,
              });
              return acc;
            }, [] as { upToTokens: number; inputCostPerUnit?: number; outputCostPerUnit?: number }[])
          : undefined;
      const saved = await adminUpsertLlmPriceConfig({
        name: model,
        currency: st.currency || 'CNY',
        pricing: {
          strategy: st.strategy,
          unit: st.unit,
          defaultInputCostPerUnit: defaultIn === null ? undefined : defaultIn,
          defaultOutputCostPerUnit: defaultOut === null ? undefined : defaultOut,
          nonThinkingInputCostPerUnit: nonThinkingIn === null ? undefined : nonThinkingIn,
          nonThinkingOutputCostPerUnit: nonThinkingOut === null ? undefined : nonThinkingOut,
          thinkingInputCostPerUnit: thinkingIn === null ? undefined : thinkingIn,
          thinkingOutputCostPerUnit: thinkingOut === null ? undefined : thinkingOut,
          tiers,
        },
      });
      setPrices((prev) => {
        const arr = prev ? [...prev] : [];
        const idx = arr.findIndex((p) => p.name === saved.name);
        if (idx >= 0) arr[idx] = saved;
        else arr.push(saved);
        return arr;
      });
      setPriceEdits((prev) => ({
        ...prev,
        [model]: { ...prev[model], saving: false, ok: true },
      }));
      await load();
    } catch (e) {
      setPriceEdits((prev) => ({
        ...prev,
        [model]: { ...prev[model], saving: false, error: e instanceof Error ? e.message : String(e) },
      }));
    }
  }, [priceEdits, load]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">Token 成本统计</h3>
          <div className="text-xs text-gray-600">
            统计范围：LLM 队列任务历史（llm_queue_task_history），按路由场景/能力汇总
          </div>
        </div>
        <button type="button" className="rounded border px-3 py-2 text-sm" onClick={load} disabled={loading}>
          {loading ? '统计中…' : '刷新'}
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[1fr_140px] gap-3 items-end">
        <div className="grid grid-cols-1 sm:grid-cols-[180px_160px_1fr_1fr] gap-2 items-end">
          <div>
            <div className="text-xs text-gray-600 mb-1">常用范围</div>
            <select
              className="w-full rounded border px-3 py-2 text-sm"
              value={rangePreset}
              onChange={(e) => {
                  const v = e.target.value as MetricsRangePreset;
                applyRangePreset(v);
              }}
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
          <div>
            <div className="text-xs text-gray-600 mb-1">统计来源</div>
            <select
              className="w-full rounded border px-3 py-2 text-sm"
              value={source}
              onChange={(e) => {
                const v = String(e.target.value ?? '').trim();
                setSource(v || 'ALL');
              }}
            >
              <option value="ALL">全部场景</option>
              {tokenSourcesByCategory.TEXT_GEN.length ? (
                <optgroup label="文本生成">
                  {tokenSourcesByCategory.TEXT_GEN.map((s) => (
                    <option key={String(s.taskType)} value={String(s.taskType)}>
                      {s.label || s.taskType}
                    </option>
                  ))}
                </optgroup>
              ) : null}
              {tokenSourcesByCategory.EMBEDDING.length ? (
                <optgroup label="向量化">
                  {tokenSourcesByCategory.EMBEDDING.map((s) => (
                    <option key={String(s.taskType)} value={String(s.taskType)}>
                      {s.label || s.taskType}
                    </option>
                  ))}
                </optgroup>
              ) : null}
              {tokenSourcesByCategory.RERANK.length ? (
                <optgroup label="重排序">
                  {tokenSourcesByCategory.RERANK.map((s) => (
                    <option key={String(s.taskType)} value={String(s.taskType)}>
                      {s.label || s.taskType}
                    </option>
                  ))}
                </optgroup>
              ) : null}
            </select>
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">开始时间</div>
            <DatePicker
              selected={startDate}
              onChange={(d) => {
                if (!d) return;
                setRangePreset('CUSTOM');
                setStartDate(d);
                if (d.getTime() > endDate.getTime()) setEndDate(d);
              }}
              className="w-full rounded border px-3 py-2"
              showTimeSelect
              timeFormat="HH:mm"
              timeIntervals={30}
              dateFormat="yyyy-MM-dd HH:mm"
              maxDate={endDate}
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">结束时间</div>
            <DatePicker
              selected={endDate}
              onChange={(d) => {
                if (!d) return;
                setRangePreset('CUSTOM');
                setEndDate(d);
                if (d.getTime() < startDate.getTime()) setStartDate(d);
              }}
              className="w-full rounded border px-3 py-2"
              showTimeSelect
              timeFormat="HH:mm"
              timeIntervals={30}
              dateFormat="yyyy-MM-dd HH:mm"
              minDate={startDate}
            />
          </div>
        </div>
        <div className="flex">
          <button
            type="button"
            className="rounded bg-blue-600 text-white px-4 py-2 w-full"
            onClick={load}
            disabled={loading}
          >
            统计
          </button>
        </div>
      </div>

      {(error || timelineError) && (
        <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{error || timelineError}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div className="rounded border p-3">
          <div className="text-xs text-gray-600">日期范围内总 Token</div>
          <div className="text-2xl font-semibold">{resp ? fmtInt(resp.totalTokens) : '—'}</div>
        </div>
        <div className="rounded border p-3">
          <div className="text-xs text-gray-600">日期范围内总费用</div>
          <div className="text-2xl font-semibold">
            {resp ? fmtCost(resp.totalCost) : '—'} <span className="text-sm text-gray-500">{resp?.currency || ''}</span>
          </div>
        </div>
      </div>

      <TokenTimelineChart
        title={`Token 消耗趋势（${sourceLabel}）`}
        bucket={timeline?.bucket}
        points={timeline?.points ?? []}
      />

      <div className="flex items-center justify-between gap-3">
        <div className="text-sm font-semibold">按模型对比</div>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-xs text-gray-600">费用口径</span>
          <select
            className="rounded border px-2 py-1 text-sm"
            value={pricingMode}
            onChange={(e) => {
              const v = e.target.value;
              if (v === 'NON_THINKING' || v === 'THINKING') setPricingMode(v);
              else setPricingMode('DEFAULT');
            }}
          >
            <option value="DEFAULT">默认</option>
            <option value="NON_THINKING">非思考</option>
            <option value="THINKING">思考（思维链+回答）</option>
          </select>
          <span className="text-xs text-gray-600">图表指标</span>
          <select
            className="rounded border px-2 py-1 text-sm"
            value={chartBy}
            onChange={(e) => setChartBy(e.target.value === 'tokens' ? 'tokens' : 'cost')}
          >
            <option value="cost">费用</option>
            <option value="tokens">总 Token</option>
          </select>
        </div>
      </div>

      <ModelTokenCostChart title="按模型 Token/费用对比" items={sortedItems.slice(0, 30)} currency={resp?.currency} providerNameById={providerNameById} />

      <div className="rounded border p-3 space-y-2 w-full max-w-5xl mx-auto">
        {sortedItems.length === 0 ? (
          <div className="text-sm text-gray-500">该时间范围内暂无记录。</div>
        ) : (
          sortedItems.slice(0, 30).map((it) => {
            const v = chartBy === 'tokens' ? Number(it.totalTokens || 0) : toNumber(it.cost) ?? 0;
            const w = Math.max(1, Math.round((v / maxChartValue) * 100));
            return (
              <div key={getModelKey(it)} className="grid grid-cols-12 gap-2 items-center">
                <div className="col-span-3 text-xs text-gray-700 truncate" title={getModelText(it)}>
                  {getModelText(it)}
                </div>
                <div className="col-span-7">
                  <div className="h-3 bg-gray-100 rounded">
                    <div className="h-3 bg-blue-600 rounded" style={{ width: `${w}%` }} />
                  </div>
                </div>
                <div className="col-span-2 text-right text-xs text-gray-600">
                  {chartBy === 'tokens' ? fmtInt(it.totalTokens) : fmtCost(it.cost)}
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="overflow-x-auto rounded border">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-gray-700">
            <tr className="text-left">
              <th className="px-3 py-2 cursor-pointer select-none" onClick={() => toggleSort('model')}>
                模型 {sortKey === 'model' ? (sortDir === 'asc' ? '↑' : '↓') : ''}
              </th>
              <th className="px-3 py-2 text-right cursor-pointer select-none" onClick={() => toggleSort('totalTokens')}>
                总 Token {sortKey === 'totalTokens' ? (sortDir === 'asc' ? '↑' : '↓') : ''}
              </th>
              <th className="px-3 py-2 text-right">输入</th>
              <th className="px-3 py-2 text-right">输出</th>
              <th className="px-3 py-2 text-right cursor-pointer select-none" onClick={() => toggleSort('cost')}>
                费用 {sortKey === 'cost' ? (sortDir === 'asc' ? '↑' : '↓') : ''}
              </th>
              <th className="px-3 py-2 text-right">价格</th>
            </tr>
          </thead>
          <tbody>
            {sortedItems.map((it) => (
              <tr key={getModelKey(it)} className="border-t">
                <td className="px-3 py-2 font-mono text-xs" title={getModelText(it)}>
                  {getModelText(it)}
                </td>
                <td className="px-3 py-2 text-right">{fmtInt(it.totalTokens)}</td>
                <td className="px-3 py-2 text-right">{fmtInt(it.tokensIn)}</td>
                <td className="px-3 py-2 text-right">{fmtInt(it.tokensOut)}</td>
                <td className="px-3 py-2 text-right font-mono text-xs">
                  {fmtCost(it.cost)} {resp?.currency || ''}
                </td>
                <td className="px-3 py-2 text-right text-xs">
                  {it.priceMissing ? <span className="text-amber-700">未配置</span> : <span className="text-green-700">已配置</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="rounded border">
        <button
          type="button"
          className="w-full flex items-center justify-between px-3 py-2 text-sm"
          onClick={() => setPricePanelOpen((v) => !v)}
        >
          <span className="font-semibold">模型单价配置</span>
          <span className="text-gray-500">{pricePanelOpen ? '收起' : '展开'}</span>
        </button>
        {pricePanelOpen && (
          <div className="border-t p-3 space-y-3">
            <div className="flex items-center justify-between gap-2">
              <div className="text-sm text-gray-700">按模型名配置（name = model）</div>
              <button
                type="button"
                className="rounded border px-3 py-1 text-sm disabled:opacity-60"
                onClick={loadPrices}
                disabled={pricesLoading}
              >
                刷新单价
              </button>
            </div>
            {pricesError && <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{pricesError}</div>}
            {pricesLoading && <div className="text-sm text-gray-500">加载中…</div>}
            {!pricesLoading && mergedPriceRows.length === 0 && <div className="text-sm text-gray-500">当前范围内没有模型记录。</div>}
            {!pricesLoading && mergedPriceRows.length > 0 && (
              <div className="overflow-x-auto rounded border">
                <table className="min-w-full text-sm">
                  <thead className="bg-gray-50 text-gray-700">
                    <tr className="text-left">
                      <th className="px-3 py-2">模型</th>
                      <th className="px-3 py-2">货币</th>
                      <th className="px-3 py-2">单位</th>
                      <th className="px-3 py-2">计价</th>
                      <th className="px-3 py-2">默认输入</th>
                      <th className="px-3 py-2">默认输出</th>
                      <th className="px-3 py-2">非思考输入</th>
                      <th className="px-3 py-2">非思考输出</th>
                      <th className="px-3 py-2">思考输入</th>
                      <th className="px-3 py-2">思考输出</th>
                      <th className="px-3 py-2">阶梯</th>
                      <th className="px-3 py-2">状态</th>
                      <th className="px-3 py-2 text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mergedPriceRows.map((row) => {
                      const st = priceEdits[row.model];
                      const unitLabel = st?.unit === 'PER_1M' ? '/1M' : '/1k';
                      const isTiered = st?.strategy === 'TIERED';
                      const disabledPrices = !!isTiered;

                      return (
                        <React.Fragment key={row.model}>
                          <tr className="border-t">
                            <td className="px-3 py-2 font-mono text-xs">{row.model}</td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-20"
                                value={st?.currency ?? row.currency}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: { ...(prev[row.model] || st), currency: e.target.value, saving: false, ok: false },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <select
                                className="rounded border px-2 py-1 w-24"
                                value={st?.unit ?? 'PER_1K'}
                                onChange={(e) => {
                                  const nextUnit: PricingUnit = e.target.value === 'PER_1M' ? 'PER_1M' : 'PER_1K';
                                  setPriceEdits((prev) => {
                                    const cur = prev[row.model] || st;
                                    if (!cur) return prev;
                                    if (cur.unit === nextUnit) return prev;
                                    const mul = cur.unit === 'PER_1K' && nextUnit === 'PER_1M' ? 1000 : 0.001;
                                    const conv = (x: string): string => {
                                      const n = toNumber(x);
                                      if (n === null) return x;
                                      const v = n * mul;
                                      return String(Number.isFinite(v) ? v : n);
                                    };
                                    return {
                                      ...prev,
                                      [row.model]: {
                                        ...cur,
                                        unit: nextUnit,
                                        defaultInputCostPerUnit: conv(cur.defaultInputCostPerUnit),
                                        defaultOutputCostPerUnit: conv(cur.defaultOutputCostPerUnit),
                                        nonThinkingInputCostPerUnit: conv(cur.nonThinkingInputCostPerUnit),
                                        nonThinkingOutputCostPerUnit: conv(cur.nonThinkingOutputCostPerUnit),
                                        thinkingInputCostPerUnit: conv(cur.thinkingInputCostPerUnit),
                                        thinkingOutputCostPerUnit: conv(cur.thinkingOutputCostPerUnit),
                                        tiers: cur.tiers.map((t) => ({
                                          ...t,
                                          inputCostPerUnit: conv(t.inputCostPerUnit),
                                          outputCostPerUnit: conv(t.outputCostPerUnit),
                                        })),
                                        saving: false,
                                        ok: false,
                                      },
                                    };
                                  });
                                }}
                              >
                                <option value="PER_1K">/1k</option>
                                <option value="PER_1M">/1M</option>
                              </select>
                            </td>
                            <td className="px-3 py-2">
                              <select
                                className="rounded border px-2 py-1 w-24"
                                value={st?.strategy ?? 'FLAT'}
                                onChange={(e) => {
                                  const next: PricingStrategy = e.target.value === 'TIERED' ? 'TIERED' : 'FLAT';
                                  setPriceEdits((prev) => {
                                    const cur = prev[row.model] || st;
                                    if (!cur) return prev;
                                    const nextTiers =
                                      next === 'TIERED' && (!cur.tiers || cur.tiers.length === 0)
                                        ? [
                                            { upToTokens: '32000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '128000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '256000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '1000000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                          ]
                                        : cur.tiers;
                                    return {
                                      ...prev,
                                      [row.model]: { ...cur, strategy: next, tiers: nextTiers, saving: false, ok: false },
                                    };
                                  });
                                }}
                              >
                                <option value="FLAT">单价</option>
                                <option value="TIERED">阶梯</option>
                              </select>
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`例如 0.12 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.defaultInputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      defaultInputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`留空则同输入 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.defaultOutputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      defaultOutputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`可选 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.nonThinkingInputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      nonThinkingInputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`可选 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.nonThinkingOutputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      nonThinkingOutputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`可选 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.thinkingInputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      thinkingInputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              <input
                                className="rounded border px-2 py-1 w-28 disabled:bg-gray-50"
                                placeholder={`可选 ${unitLabel}`}
                                disabled={disabledPrices}
                                value={st?.thinkingOutputCostPerUnit ?? ''}
                                onChange={(e) =>
                                  setPriceEdits((prev) => ({
                                    ...prev,
                                    [row.model]: {
                                      ...(prev[row.model] || st),
                                      thinkingOutputCostPerUnit: e.target.value,
                                      saving: false,
                                      ok: false,
                                    },
                                  }))
                                }
                              />
                            </td>
                            <td className="px-3 py-2">
                              {isTiered ? (
                                <button
                                  type="button"
                                  className="rounded border px-2 py-1 text-xs"
                                  onClick={() =>
                                    setPriceEdits((prev) => ({
                                      ...prev,
                                      [row.model]: { ...(prev[row.model] || st), tiersOpen: !(prev[row.model] || st)?.tiersOpen },
                                    }))
                                  }
                                >
                                  {(st?.tiersOpen ? '收起' : '编辑') + unitLabel}
                                </button>
                              ) : (
                                <span className="text-gray-400">—</span>
                              )}
                            </td>
                            <td className="px-3 py-2 text-xs">
                              {st?.ok ? <span className="text-green-700">已保存</span> : null}
                              {st?.error ? <span className="text-red-700">{st.error}</span> : null}
                              {!st?.ok && !st?.error && row.updatedAt ? <span className="text-gray-500">已配置</span> : null}
                            </td>
                            <td className="px-3 py-2 text-right">
                              <button
                                type="button"
                                className="rounded bg-blue-600 text-white px-3 py-1 text-sm disabled:opacity-60"
                                disabled={!st || st.saving}
                                onClick={() => savePrice(row.model)}
                              >
                                {st?.saving ? '保存中…' : '保存'}
                              </button>
                            </td>
                          </tr>

                          {isTiered && st?.tiersOpen ? (
                            <tr className="border-t bg-gray-50/50">
                              <td className="px-3 py-3" colSpan={13}>
                                <div className="flex items-center justify-between gap-2 mb-2">
                                  <div className="text-xs text-gray-600">按单次请求总 Token（输入+输出）匹配阶梯，上限为包含关系（≤）。</div>
                                  <button
                                    type="button"
                                    className="rounded border px-2 py-1 text-xs"
                                    onClick={() =>
                                      setPriceEdits((prev) => ({
                                        ...prev,
                                        [row.model]: {
                                          ...(prev[row.model] || st),
                                          tiers: [
                                            { upToTokens: '32000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '128000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '256000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                            { upToTokens: '1000000', inputCostPerUnit: '', outputCostPerUnit: '' },
                                          ],
                                          saving: false,
                                          ok: false,
                                        },
                                      }))
                                    }
                                  >
                                    使用默认阶梯
                                  </button>
                                </div>
                                <div className="overflow-x-auto rounded border bg-white">
                                  <table className="min-w-full text-sm">
                                    <thead className="bg-gray-50 text-gray-700">
                                      <tr className="text-left">
                                        <th className="px-3 py-2">Token 上限（≤）</th>
                                        <th className="px-3 py-2">输入单价{unitLabel}</th>
                                        <th className="px-3 py-2">输出单价{unitLabel}</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {(st?.tiers ?? []).map((t, idx) => (
                                        <tr key={idx} className="border-t">
                                          <td className="px-3 py-2">
                                            <input
                                              className="rounded border px-2 py-1 w-32"
                                              value={t.upToTokens}
                                              onChange={(e) => updateTierField(setPriceEdits, row.model, st, idx, { upToTokens: e.target.value })}
                                            />
                                          </td>
                                          <td className="px-3 py-2">
                                            <input
                                              className="rounded border px-2 py-1 w-32"
                                              value={t.inputCostPerUnit}
                                              onChange={(e) => updateTierField(setPriceEdits, row.model, st, idx, { inputCostPerUnit: e.target.value })}
                                            />
                                          </td>
                                          <td className="px-3 py-2">
                                            <input
                                              className="rounded border px-2 py-1 w-32"
                                              placeholder="留空则同输入单价"
                                              value={t.outputCostPerUnit}
                                              onChange={(e) => updateTierField(setPriceEdits, row.model, st, idx, { outputCostPerUnit: e.target.value })}
                                            />
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              </td>
                            </tr>
                          ) : null}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            <div className="text-xs text-gray-600">
              说明：输出单价留空时，按输入单价计费；“非思考/思考”单价留空时回退到默认单价；保存后会自动刷新本页统计结果。
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default TokenForm;
