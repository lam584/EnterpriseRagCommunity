// embed.tsx
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getCsrfToken } from '../../../../utils/csrfUtils';
import { useSearchParams } from 'react-router-dom';
import {
  createSample,
  deleteSample,
  listSamples,
  syncSample,
  updateSample,
  type ModerationSample,
  type ModerationSampleCreateRequest,
  type ModerationSamplesSyncResult,
} from '../../../../services/moderationEmbedSamplesService';
import {
  triggerReindexSamples,
  getSamplesIndexStatus,
  type ModerationSamplesIndexStatusResponse
} from '../../../../services/moderationService';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';

// ===== 后端/运行时配置 =====
// 保持兼容：本页允许通过 Vite 环境变量配置 API_BASE；未配置时使用同域相对路径
const API_BASE: string = ((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE as string) ?? '';

type ContentType = 'POST' | 'COMMENT';

// ===== 后端返回类型（最小集：只覆盖本页使用到的字段） =====
interface SimilarityConfig {
  enabled: boolean;
  embeddingModel?: string | null;
  embeddingDims?: number | null;
  maxInputChars?: number | null;
  defaultTopK?: number | null;
  defaultThreshold?: number | null;
  defaultNumCandidates?: number | null;
  updatedAt?: string | null;
}

type SimilarityHit = {
  sampleId?: number | null;
  distance?: number | null;
  category?: ModerationSampleCreateRequest['category'] | string | null;
  riskLevel?: number | null;
  rawTextPreview?: string | null;
};

interface SimilarityCheckResponse {
  hit: boolean;
  bestDistance?: number | null;
  threshold?: number | null;
  topK?: number | null;
  numCandidates?: number | null;
  embeddingDims?: number | null;
  embeddingModel?: string | null;
  maxInputChars?: number | null;
  hits?: SimilarityHit[];
}

// NOTE: “相似命中记录（moderation_similar_hits）” 功能已移除；保留审核历史记录即可。

// ====== UI 展示用中文映射（注意：value 仍然必须是后端枚举，不要改） ======
const CONTENT_TYPE_LABEL = {
  POST: '帖子',
  COMMENT: '评论',
} satisfies Record<ContentType, string>;

const CATEGORY_LABEL = {
  AD_SAMPLE: '广告样本',
  HISTORY_VIOLATION: '历史违规',
} satisfies Record<ModerationSampleCreateRequest['category'], string>;

type SampleSource = NonNullable<ModerationSampleCreateRequest['source']>;
const SOURCE_LABEL = {
  HUMAN: '人工',
  RULE: '规则',
  LLM: '大模型',
  IMPORT: '导入',
} satisfies Record<SampleSource, string>;

const CATEGORY_VALUES = Object.keys(CATEGORY_LABEL) as Array<ModerationSampleCreateRequest['category']>;

function labelWithEnum(label: string, enumValue: string): string {
  // 展示时保留英文枚举，方便和后端/日志对齐
  return `${label}（${enumValue}）`;
}

function categoryDisplay(v?: string | null): string {
  if (!v) return '—';
  const key = v as ModerationSampleCreateRequest['category'];
  if (CATEGORY_VALUES.includes(key)) return labelWithEnum(CATEGORY_LABEL[key], v);
  return labelWithEnum('未知', v);
}

function sourceDisplay(v?: string | null): string {
  if (!v) return '—';
  const key = v as SampleSource;
  if (key in SOURCE_LABEL) return labelWithEnum(SOURCE_LABEL[key], v);
  return labelWithEnum('未知', v);
}

function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return String(s);
  return d.toLocaleString();
}

function clampInt(n: number, min: number, max: number): number {
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, Math.trunc(n)));
}

type SimilarityConfigForm = {
  enabled: boolean;
  embeddingModel: string;
  embeddingDims: string;
  maxInputChars: string;
  defaultTopK: string;
  defaultThreshold: string;
  defaultNumCandidates: string;
};

type SamplesAutoSyncConfig = {
  enabled: boolean;
  intervalSeconds: number;
};

type SamplesAutoSyncConfigForm = {
  enabled: boolean;
  intervalSeconds: string;
};

function toAutoSyncFormState(c?: SamplesAutoSyncConfig | null): SamplesAutoSyncConfigForm {
  return {
    enabled: c?.enabled ?? true,
    intervalSeconds: c?.intervalSeconds == null ? '' : String(c.intervalSeconds),
  };
}

function toCfgFormState(c?: SimilarityConfig | null): SimilarityConfigForm {
  return {
    enabled: c?.enabled ?? true,
    embeddingModel: c?.embeddingModel ? String(c.embeddingModel) : '',
    embeddingDims: c?.embeddingDims == null ? '' : String(c.embeddingDims),
    maxInputChars: c?.maxInputChars == null ? '' : String(c.maxInputChars),
    defaultTopK: c?.defaultTopK == null ? '' : String(c.defaultTopK),
    defaultThreshold: c?.defaultThreshold == null ? '' : String(c.defaultThreshold),
    defaultNumCandidates: c?.defaultNumCandidates == null ? '' : String(c.defaultNumCandidates),
  };
}

const EmbedForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  const qidForHistory = useMemo(() => {
    const raw = searchParams.get('queueId') ?? '';
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [searchParams]);

  // ===== Global toggle =====
  const [cfgLoading, setCfgLoading] = useState(false);
  const [cfgSaving, setCfgSaving] = useState(false);
  const [cfg, setCfg] = useState<SimilarityConfig | null>(null);
  const [cfgForm, setCfgForm] = useState<SimilarityConfigForm>(() => toCfgFormState(null));
  const [cfgEditing, setCfgEditing] = useState(false);
  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');

  const [autoSyncLoading, setAutoSyncLoading] = useState(false);
  const [autoSyncCfg, setAutoSyncCfg] = useState<SamplesAutoSyncConfig | null>(null);
  const [autoSyncForm, setAutoSyncForm] = useState<SamplesAutoSyncConfigForm>(() => toAutoSyncFormState(null));
  const autoSyncEnableEnsureInFlightRef = useRef(false);

  const [samplesIndexLoading, setSamplesIndexLoading] = useState(false);
  const [samplesIndexError, setSamplesIndexError] = useState<string | null>(null);
  const [samplesIndexStatus, setSamplesIndexStatus] = useState<ModerationSamplesIndexStatusResponse | null>(null);

  // ===== Reindex (MySQL -> ES) =====
  const [reindexing, setReindexing] = useState(false);

  // ===== Manual check =====
  const [text, setText] = useState('');
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<SimilarityCheckResponse | null>(null);

  // ===== Samples =====
  const [samplesLoading, setSamplesLoading] = useState(false);
  const [samplesPage, setSamplesPage] = useState(1);
  const [samplesPageSize, setSamplesPageSize] = useState(20);
  const [samplesCategory, setSamplesCategory] = useState<ModerationSampleCreateRequest['category'] | ''>('');
  const [samplesEnabled, setSamplesEnabled] = useState<'true' | 'false' | ''>('');
  const [samples, setSamples] = useState<ModerationSample[]>([]);
  const [samplesTotalPages, setSamplesTotalPages] = useState(1);
  const [samplesTotalElements, setSamplesTotalElements] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const cfg = await adminGetAiProvidersConfig();
        if (cancelled) return;
        setProviders((cfg.providers ?? []).filter(Boolean) as AiProviderDTO[]);
        setActiveProviderId(cfg.activeProviderId ?? '');
      } catch {
        if (cancelled) return;
        setProviders([]);
        setActiveProviderId('');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const loadSamplesIndexStatus = useCallback(async () => {
    setSamplesIndexLoading(true);
    setSamplesIndexError(null);
    try {
      const data = await getSamplesIndexStatus();
      setSamplesIndexStatus(data);
    } catch (e) {
      setSamplesIndexError(e instanceof Error ? e.message : String(e));
      setSamplesIndexStatus(null);
    } finally {
      setSamplesIndexLoading(false);
    }
  }, []);

  // ===== Samples CRUD UI =====
  const [sampleModalOpen, setSampleModalOpen] = useState(false);
  const [sampleSaving, setSampleSaving] = useState(false);
  const [editingSampleId, setEditingSampleId] = useState<number | null>(null);
  type SampleRefContentType = NonNullable<ModerationSampleCreateRequest['refContentType']>;

  const [sampleForm, setSampleForm] = useState<ModerationSampleCreateRequest>({
    category: 'AD_SAMPLE',
    enabled: true,
    riskLevel: 0,
    source: 'HUMAN',
    rawText: '',
    refContentType: null,
    refContentId: null,
    labels: null,
  });

  const manualParams = useMemo(() => {
    const topK = cfgForm.defaultTopK.trim() ? clampInt(Number(cfgForm.defaultTopK), 1, 50) : 5;

    const thresholdText = cfgForm.defaultThreshold.trim();
    let threshold = 0.15;
    if (thresholdText) {
      const n = Number(thresholdText);
      if (Number.isFinite(n)) threshold = Math.max(0, Math.min(1, n));
    }

    const numCandidates = cfgForm.defaultNumCandidates.trim() ? clampInt(Number(cfgForm.defaultNumCandidates), 0, 10_000) : 0;
    const embeddingModel = cfgForm.embeddingModel.trim() ? cfgForm.embeddingModel.trim() : undefined;
    const embeddingDims = cfgForm.embeddingDims.trim() ? clampInt(Number(cfgForm.embeddingDims), 0, 100_000) : undefined;
    const maxInputChars = cfgForm.maxInputChars.trim() ? clampInt(Number(cfgForm.maxInputChars), 0, 200_000) : undefined;
    return { topK, threshold, numCandidates, embeddingModel, embeddingDims, maxInputChars };
  }, [cfgForm.defaultNumCandidates, cfgForm.defaultThreshold, cfgForm.defaultTopK, cfgForm.embeddingDims, cfgForm.embeddingModel, cfgForm.maxInputChars]);


  function openCreateSample() {
    setEditingSampleId(null);
    setSampleForm({
      category: 'AD_SAMPLE',
      enabled: true,
      riskLevel: 0,
      source: 'HUMAN',
      rawText: '',
      refContentType: null,
      refContentId: null,
      labels: null,
    });
    setSampleModalOpen(true);
  }

  function openEditSample(s: ModerationSample) {
    setEditingSampleId(s.id);
    setSampleForm({
      category: s.category,
      enabled: s.enabled ?? true,
      riskLevel: s.riskLevel ?? 0,
      source: (s.source ?? 'HUMAN') as SampleSource,
      rawText: s.rawText ?? '',
      refContentType: (s.refContentType ?? null) as SampleRefContentType | null,
      refContentId: s.refContentId ?? null,
      labels: s.labels ?? null,
    });
    setSampleModalOpen(true);
  }

  const submitSample = useCallback(async () => {
    setSampleSaving(true);
    setError(null);
    try {
      const payload: ModerationSampleCreateRequest = {
        ...sampleForm,
        rawText: (sampleForm.rawText ?? '').trim(),
      };
      if (!payload.rawText) {
        setError('rawText 不能为空');
        return;
      }

      let saved: ModerationSample;
      if (editingSampleId == null) {
        saved = await createSample(payload);
      } else {
        saved = await updateSample(editingSampleId, payload);
      }

      if (saved?.esSynced === false) {
        setError(`样本已保存，但同步到 ES 索引失败：${saved.esSyncMessage || '未知错误'}。你可以在列表里点击“同步索引”。`);
      }

      setSampleModalOpen(false);
      // refresh list with current filters
      const pageData = await listSamples({
        page: samplesPage,
        pageSize: samplesPageSize,
        category: samplesCategory,
        enabled: samplesEnabled,
      });
      setSamples(pageData.content ?? []);
      setSamplesTotalPages(pageData.totalPages ?? 1);
      setSamplesTotalElements(pageData.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSampleSaving(false);
    }
  }, [editingSampleId, sampleForm, samplesCategory, samplesEnabled, samplesPage, samplesPageSize]);

  const loadConfig = useCallback(async () => {
    setCfgLoading(true);
    setError(null);
    try {
      const res = await fetch(apiUrl('/api/admin/moderation/embed/config'), {
        method: 'GET',
        credentials: 'include',
      });
      const data: unknown = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(getBackendMessage(data) || '获取配置失败');
        return;
      }
      const c = data as SimilarityConfig;
      setCfg(c);
      setCfgForm(toCfgFormState(c));
      setCfgEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCfgLoading(false);
    }
  }, []);

  const loadAutoSyncConfig = useCallback(async () => {
    setAutoSyncLoading(true);
    setError(null);
    try {
      const res = await fetch(apiUrl('/api/admin/moderation/embed/samples/auto-sync/config'), {
        method: 'GET',
        credentials: 'include',
      });
      const data: unknown = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(getBackendMessage(data) || '获取自动增量同步配置失败');
        return;
      }
      const c = data as SamplesAutoSyncConfig;
      setAutoSyncCfg(c);
      setAutoSyncForm(toAutoSyncFormState(c));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setAutoSyncLoading(false);
    }
  }, []);

  const saveConfig = useCallback(async () => {
    setCfgSaving(true);
    setError(null);
    try {
      const csrf = await getCsrfToken();

      // 1. Save SimilarityConfig
      const payload1 = {
        enabled: cfgForm.enabled,
        embeddingModel: cfgForm.embeddingModel.trim() ? cfgForm.embeddingModel.trim() : null,
        embeddingDims: cfgForm.embeddingDims.trim() ? clampInt(Number(cfgForm.embeddingDims), 0, 100_000) : 0,
        maxInputChars: cfgForm.maxInputChars.trim() ? clampInt(Number(cfgForm.maxInputChars), 0, 200_000) : 0,
        defaultTopK: cfgForm.defaultTopK.trim() ? clampInt(Number(cfgForm.defaultTopK), 1, 50) : 5,
        defaultThreshold: cfgForm.defaultThreshold.trim() ? Math.max(0, Math.min(1, Number(cfgForm.defaultThreshold))) : 0.15,
        defaultNumCandidates: cfgForm.defaultNumCandidates.trim() ? clampInt(Number(cfgForm.defaultNumCandidates), 0, 10_000) : 0,
      };

      // 2. Save AutoSyncConfig
      const intervalSeconds = autoSyncForm.intervalSeconds.trim()
        ? clampInt(Number(autoSyncForm.intervalSeconds.trim()), 5, 3600)
        : 60;
      const payload2 = {
        enabled: autoSyncForm.enabled,
        intervalSeconds,
      };

      // Execute both requests
      const [res1, res2] = await Promise.all([
        fetch(apiUrl('/api/admin/moderation/embed/config'), {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrf,
          },
          credentials: 'include',
          body: JSON.stringify(payload1),
        }),
        fetch(apiUrl('/api/admin/moderation/embed/samples/auto-sync/config'), {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrf,
          },
          credentials: 'include',
          body: JSON.stringify(payload2),
        })
      ]);

      const data1: unknown = await res1.json().catch(() => ({}));
      if (!res1.ok) {
        throw new Error(getBackendMessage(data1) || '更新运行时配置失败');
      }

      const data2: unknown = await res2.json().catch(() => ({}));
      if (!res2.ok) {
        throw new Error(getBackendMessage(data2) || '更新自动增量同步配置失败');
      }

      const c1 = data1 as SimilarityConfig;
      setCfg(c1);
      setCfgForm(toCfgFormState(c1));

      const c2 = data2 as SamplesAutoSyncConfig;
      setAutoSyncCfg(c2);
      setAutoSyncForm(toAutoSyncFormState(c2));
      
      setCfgEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCfgSaving(false);
    }
  }, [cfgForm, autoSyncForm]);

  const manualCheck = useCallback(async () => {
    setChecking(true);
    setError(null);
    setCheckResult(null);
    try {
      const csrf = await getCsrfToken();
      const res = await fetch(apiUrl('/api/admin/moderation/embed/check'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrf,
        },
        credentials: 'include',
        body: JSON.stringify({
          text,
          topK: manualParams.topK,
          threshold: manualParams.threshold,
          numCandidates: manualParams.numCandidates,
          embeddingModel: manualParams.embeddingModel,
          embeddingDims: manualParams.embeddingDims,
          maxInputChars: manualParams.maxInputChars,
        }),
      });
      const data: unknown = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(getBackendMessage(data) || '检测失败');
        return;
      }
      setCheckResult(data as SimilarityCheckResponse);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setChecking(false);
    }
  }, [manualParams, text]);

  const loadSamples = useCallback(async () => {
    setSamplesLoading(true);
    setError(null);
    try {
      const pageData = await listSamples({
        page: samplesPage,
        pageSize: samplesPageSize,
        category: samplesCategory,
        enabled: samplesEnabled,
      });
      setSamples(pageData.content ?? []);
      setSamplesTotalPages(pageData.totalPages ?? 1);
      setSamplesTotalElements(pageData.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSamplesLoading(false);
    }
  }, [samplesCategory, samplesEnabled, samplesPage, samplesPageSize]);

  const removeSample = useCallback(
    async (id: number) => {
      const ok = window.confirm(`确认删除样本 ${id} ?`);
      if (!ok) return;
      setError(null);
      try {
        await deleteSample(id);
        await loadSamples();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    },
    [loadSamples]
  );

  const reindexSamples = useCallback(async () => {
    const ok = window.confirm('确认执行：重建 ES 索引？\n\n将先清空 ES 索引，然后把 moderation_samples(启用=true) 全量写入 ES，并清理 MySQL 已删除的 ES 孤儿文档。\n\n会调用 embedding，可能耗时与产生费用。');
    if (!ok) return;

    setReindexing(true);
    setError(null);
    try {
      await triggerReindexSamples({ onlyEnabled: true, batchSize: 200 });

      // Optional UX: refresh sample list after reindex
      void loadSamples();
      void loadSamplesIndexStatus();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setReindexing(false);
    }
  }, [loadSamples, loadSamplesIndexStatus]);

  // ===== ES Sync (one) =====
  const [syncingId, setSyncingId] = useState<number | null>(null);

  const syncOneSample = useCallback(
    async (id: number) => {
      setSyncingId(id);
      setError(null);
      try {
        const r: ModerationSamplesSyncResult = await syncSample(id);
        if (!r.success) {
          setError(r.message || '同步失败');
          return;
        }
        // refresh list
        await loadSamples();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setSyncingId(null);
      }
    },
    [loadSamples]
  );

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  useEffect(() => {
    void loadAutoSyncConfig();
  }, [loadAutoSyncConfig]);

  useEffect(() => {
    if (autoSyncEnableEnsureInFlightRef.current) return;
    if (cfgEditing || cfgSaving || cfgLoading || autoSyncLoading) return;
    if (cfg?.enabled !== true) return;
    if (!autoSyncCfg) return;
    if (autoSyncCfg.enabled === true) return;

    autoSyncEnableEnsureInFlightRef.current = true;
    (async () => {
      try {
        const csrf = await getCsrfToken();
        const intervalSeconds = autoSyncCfg.intervalSeconds == null ? 60 : clampInt(Number(autoSyncCfg.intervalSeconds), 5, 3600);
        const res = await fetch(apiUrl('/api/admin/moderation/embed/samples/auto-sync/config'), {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrf,
          },
          credentials: 'include',
          body: JSON.stringify({
            enabled: true,
            intervalSeconds,
          }),
        });
        const data: unknown = await res.json().catch(() => ({}));
        if (!res.ok) {
          setError(getBackendMessage(data) || '启用自动增量同步失败');
          return;
        }
        const c2 = data as SamplesAutoSyncConfig;
        setAutoSyncCfg(c2);
        setAutoSyncForm(toAutoSyncFormState(c2));
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        autoSyncEnableEnsureInFlightRef.current = false;
      }
    })();
  }, [autoSyncCfg, autoSyncLoading, cfg?.enabled, cfgEditing, cfgLoading, cfgSaving]);

  useEffect(() => {
    void loadSamples();
  }, [loadSamples]);

  useEffect(() => {
    void loadSamplesIndexStatus();
  }, [loadSamplesIndexStatus]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">嵌入相似检测（VEC）控制台</h3>
          <button
            type="button"
            className="rounded border px-3 py-2 disabled:opacity-60"
            onClick={() => {
              void loadConfig();
              void loadAutoSyncConfig();
              void loadSamples();
              void loadSamplesIndexStatus();
            }}
            disabled={cfgLoading || autoSyncLoading || samplesLoading || samplesIndexLoading}
          >
            刷新
          </button>
        </div>

        {error ? (
          <div className="rounded border border-red-200 bg-red-50 text-red-800 px-3 py-2 text-sm flex items-center justify-between gap-3">
            <span>错误：{error}</span>
            <button type="button" className="rounded bg-red-600 text-white px-3 py-1.5" onClick={() => setError(null)}>
              关闭
            </button>
          </div>
        ) : null}

        <div className="bg-white rounded-lg shadow p-4 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-lg font-semibold">样本库索引状态</div>
            </div>
            {samplesIndexStatus?.exists === false && (
              <button
                type="button"
                className="rounded bg-green-600 text-white px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                disabled={reindexing}
                onClick={() => void reindexSamples()}
              >
                {reindexing ? '创建中...' : '创建索引'}
              </button>
            )}
          </div>

          {samplesIndexError ? (
            <div className="rounded border border-red-200 bg-red-50 text-red-800 px-3 py-2 text-sm">错误：{samplesIndexError}</div>
          ) : null}

          {!samplesIndexStatus ? (
            <div className="text-sm text-gray-500 py-4 text-center bg-gray-50 rounded border border-dashed">
              {samplesIndexLoading ? '加载中…' : '暂无状态数据。'}
            </div>
          ) : (
            <div className="overflow-x-auto border rounded-md">
              <table className="min-w-full text-sm divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr className="text-left text-gray-500 font-medium text-sm uppercase tracking-wider">
                    <th className="py-2 px-3">索引名</th>
                    <th className="py-2 px-3">文档数</th>
                    <th className="py-2 px-3">维度（配置/映射）</th>
                    <th className="py-2 px-3">状态</th>
                    <th className="py-2 px-3">上次增量同步</th>
                    <th className="py-2 px-3 text-right">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  <tr className="hover:bg-gray-50 transition-colors">
                    <td className="py-2 px-3 font-mono text-gray-900 break-all text-sm font-medium">{samplesIndexStatus.indexName ?? '—'}</td>
                    <td className="py-2 px-3 text-gray-500 text-sm">{samplesIndexStatus.docCount ?? '—'}</td>
                    <td className="py-2 px-3 text-gray-500 text-sm">
                      {(samplesIndexStatus.embeddingDimsConfigured ?? '—') + ' / ' + (samplesIndexStatus.embeddingDimsInMapping ?? '—')}
                    </td>
                    <td className="py-2 px-3">
                      <div className="flex flex-col gap-1 items-start">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide ${
                              samplesIndexStatus.exists === true ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {samplesIndexStatus.exists === true ? '存在' : '不存在'}
                          </span>

                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide ${
                              samplesIndexStatus.available === true
                                ? 'bg-green-100 text-green-800'
                                : samplesIndexStatus.available === false
                                  ? 'bg-red-100 text-red-800'
                                  : 'bg-gray-100 text-gray-700'
                            }`}
                          >
                            {samplesIndexStatus.available === true ? '可用' : samplesIndexStatus.available === false ? '不可用' : '未知'}
                          </span>

                          {samplesIndexStatus.available === true && samplesIndexStatus.docCount === 0 ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-yellow-100 text-yellow-800">
                              无样本
                            </span>
                          ) : null}
                        </div>

                        {samplesIndexStatus.available === false && samplesIndexStatus.availabilityMessage ? (
                          <div className="text-xs text-gray-500">{samplesIndexStatus.availabilityMessage}</div>
                        ) : null}
                      </div>
                    </td>
                    <td className="py-2 px-3 text-gray-500 text-sm">{formatDateTime(samplesIndexStatus.lastIncrementalSyncAt)}</td>
                    <td className="py-2 px-3 text-right whitespace-nowrap">
                      <button
                        type="button"
                        className="rounded bg-blue-600 text-white px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                        disabled={reindexing}
                        onClick={() => void reindexSamples()}
                        title="全量重建：先清空 ES，再全量写入，并清理孤儿文档（会调用 embedding）。"
                      >
                        {reindexing ? '重建中…' : '重建索引'}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="rounded border bg-gray-50 p-3 space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="font-semibold">运行时配置</div>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-700">运行时配置：</span>
                  <select
                    value={cfgForm.enabled ? 'true' : 'false'}
                    disabled={!cfgEditing || cfgLoading || cfgSaving}
                    onChange={(e) => {
                      const nextEnabled = e.target.value === 'true';
                      setCfgForm((p) => ({ ...p, enabled: nextEnabled }));
                    }}
                    className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                      cfgForm.enabled ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                    } disabled:opacity-60 disabled:bg-gray-100`}
                    title={!cfgEditing ? '只读（点击右侧「编辑配置」后可修改）' : '修改开关（需保存生效）'}
                  >
                    <option value="true" className="text-green-600">
                      开启
                    </option>
                    <option value="false" className="text-red-600">
                      关闭
                    </option>
                  </select>
                </div>

                {!cfgEditing ? (
                  <button
                    type="button"
                    className="rounded bg-blue-600 text-white px-4 py-2 text-sm disabled:opacity-60"
                    disabled={cfgLoading || cfgSaving}
                    onClick={() => {
                      setCfgEditing(true);
                      setError(null);
                    }}
                    title="进入编辑模式"
                  >
                    编辑
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                      disabled={cfgLoading || cfgSaving}
                      onClick={() => {
                        setCfgForm(toCfgFormState(cfg));
                        setAutoSyncForm(toAutoSyncFormState(autoSyncCfg));
                        setCfgEditing(false);
                        setError(null);
                      }}
                      title="放弃未保存的修改，并恢复到最近一次加载/保存的配置"
                    >
                      放弃
                    </button>
                    <button
                      type="button"
                      className="rounded bg-blue-600 text-white px-4 py-2 text-sm disabled:opacity-60"
                      disabled={cfgLoading || cfgSaving}
                      onClick={() => void saveConfig()}
                      title="保存并生效"
                    >
                      {cfgSaving ? '保存中…' : '保存'}
                    </button>
                  </>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div>
                <ProviderModelSelect
                  providers={providers}
                  activeProviderId={activeProviderId}
                  mode="embedding"
                  includeProviderOnlyOptions={false}
                  providerId=""
                  model={cfgForm.embeddingModel}
                  disabled={!cfgEditing || cfgLoading || cfgSaving}
                  selectClassName="w-full rounded border px-3 py-2 text-sm bg-white disabled:bg-gray-50"
                  onChange={(next) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, embeddingModel: next.model }));
                  }}
                />
              </div>
              <div>
                <div className="text-sm font-medium mb-1">维度（0=自动）</div>
                <input
                  className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                  placeholder="0"
                  value={cfgForm.embeddingDims}
                  readOnly={!cfgEditing}
                  onChange={(e) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, embeddingDims: e.target.value }));
                  }}
                />
              </div>
              <div>
                <div className="text-sm font-medium mb-1">最大输入长度（0=不截断）</div>
                <input
                  className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                  placeholder="0"
                  value={cfgForm.maxInputChars}
                  readOnly={!cfgEditing}
                  onChange={(e) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, maxInputChars: e.target.value }));
                  }}
                />
              </div>
              <div>
                <div className="text-sm font-medium mb-1">TopK（1~50）</div>
                <input
                  className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                  placeholder="例如：5"
                  value={cfgForm.defaultTopK}
                  readOnly={!cfgEditing}
                  onChange={(e) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, defaultTopK: e.target.value }));
                  }}
                />
              </div>
              <div>
                <div className="text-sm font-medium mb-1">阈值（0~1）</div>
                <input
                  className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                  placeholder="例如：0.15"
                  value={cfgForm.defaultThreshold}
                  readOnly={!cfgEditing}
                  onChange={(e) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, defaultThreshold: e.target.value }));
                  }}
                />
              </div>
              <div>
                <div className="text-sm font-medium mb-1">候选数（0=自动）</div>
                <input
                  className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                  placeholder="0"
                  value={cfgForm.defaultNumCandidates}
                  readOnly={!cfgEditing}
                  onChange={(e) => {
                    if (!cfgEditing) return;
                    setCfgForm((p) => ({ ...p, defaultNumCandidates: e.target.value }));
                  }}
                />
              </div>
            </div>

            <div className="rounded border bg-white p-3 space-y-3">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <div className="text-sm font-medium mb-1">自动增量同步</div>
                  <select
                    value={autoSyncForm.enabled ? 'true' : 'false'}
                    disabled={!cfgEditing || autoSyncLoading || cfgSaving}
                    onChange={(e) => {
                      if (!cfgEditing) return;
                      const nextEnabled = e.target.value === 'true';
                      setAutoSyncForm((p) => ({ ...p, enabled: nextEnabled }));
                    }}
                    className={`w-full rounded border px-3 py-2 text-sm font-semibold focus:outline-none ${
                      autoSyncForm.enabled ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                    } disabled:opacity-60 disabled:bg-gray-100`}
                    title={!cfgEditing ? '只读（点击右侧「编辑配置」后可修改）' : '修改开关（需保存生效）'}
                  >
                    <option value="true" className="text-green-600">
                      开启
                    </option>
                    <option value="false" className="text-red-600">
                      关闭
                    </option>
                  </select>
                </div>
                <div>
                  <div className="text-sm font-medium mb-1">自动增量同步时间间隔</div>
                  <select
                    value={autoSyncForm.intervalSeconds.trim() ? autoSyncForm.intervalSeconds : '60'}
                    disabled={!cfgEditing || autoSyncLoading || cfgSaving}
                    onChange={(e) => {
                      if (!cfgEditing) return;
                      setAutoSyncForm((p) => ({ ...p, intervalSeconds: e.target.value }));
                    }}
                    className="w-full rounded border px-3 py-2 text-sm bg-white disabled:bg-gray-50 disabled:opacity-60"
                  >
                    <option value="5">5 秒</option>
                    <option value="10">10 秒</option>
                    <option value="30">30 秒</option>
                    <option value="60">1 分钟</option>
                    <option value="120">2 分钟</option>
                    <option value="300">5 分钟</option>
                    <option value="600">10 分钟</option>
                    <option value="1800">30 分钟</option>
                    <option value="3600">60 分钟</option>
                  </select>
                </div>
              </div>
            </div>
            <div className="text-xs text-gray-500">更新时间：{formatDateTime(cfg?.updatedAt)}</div>
          </div>

          <div className="rounded border bg-gray-50 p-3 space-y-3">
            <div className="font-semibold">手动检测（用于验证 ES/Embedding 是否通）</div>
            <textarea
              className="w-full rounded border px-3 py-2 min-h-[90px]"
              placeholder="输入要检测的文本（贴标题+正文或评论内容）"
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                disabled={checking || !text.trim()}
                onClick={() => void manualCheck()}
              >
                {checking ? '检测中…' : '检测'}
              </button>
            </div>

            {checkResult ? (
              <div className="rounded border bg-white p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="font-semibold">检测结果</div>
                  <span className={`text-sm font-semibold ${checkResult.hit ? 'text-red-700' : 'text-green-700'}`}>
                    {checkResult.hit ? '命中（HIT，建议转人工）' : '未命中（MISS）'}
                  </span>
                </div>
                <div className="text-sm text-gray-700">
                  最佳距离（bestDistance） <b>{checkResult.bestDistance ?? '—'}</b>；阈值（threshold） <b>{checkResult.threshold ?? '—'}</b>；取前K个（topK）{' '}
                  <b>{checkResult.topK ?? '—'}</b>；候选数（numCandidates） <b>{checkResult.numCandidates ?? '—'}</b>；向量维度（dims）{' '}
                  <b>{checkResult.embeddingDims ?? '—'}</b>；向量模型（model） <b>{checkResult.embeddingModel ?? '—'}</b>；最大输入字符数（maxInputChars）{' '}
                  <b>{checkResult.maxInputChars ?? '—'}</b>
                </div>
                <div className="overflow-auto">
                  <table className="min-w-full text-sm">
                    <thead>
                      <tr className="text-left text-gray-600">
                        <th className="py-2 pr-2">样本ID</th>
                        <th className="py-2 pr-2">距离</th>
                        <th className="py-2 pr-2">分类</th>
                        <th className="py-2 pr-2">风险等级</th>
                        <th className="py-2 pr-2">文本预览</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(checkResult.hits ?? []).map((h: SimilarityHit, idx: number) => (
                        <tr key={idx} className="border-t">
                          <td className="py-2 pr-2">{h.sampleId ?? '—'}</td>
                          <td className="py-2 pr-2">{h.distance ?? '—'}</td>
                          <td className="py-2 pr-2">{categoryDisplay(h.category ? String(h.category) : null)}</td>
                          <td className="py-2 pr-2">{h.riskLevel ?? '—'}</td>
                          <td className="py-2 pr-2 text-gray-700">{h.rawTextPreview ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : null}
          </div>
        </div>
      </div>

      {/* Samples */}
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-lg font-semibold">样本库数据（moderation_samples）</div>
            <div className="text-sm text-gray-600">支持新增/编辑/删除；textHash/normalizedText 由后端自动生成</div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-4 py-2 disabled:opacity-60"
              onClick={() => openCreateSample()}
              disabled={samplesLoading}
            >
              新增样本
            </button>
            <button type="button" className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60" onClick={() => void loadSamples()} disabled={samplesLoading}>
              {samplesLoading ? '加载中…' : '刷新样本库'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <select
            className="rounded border px-3 py-2"
            value={samplesCategory}
            onChange={(e) => {
              const v = e.target.value;
              setSamplesCategory(v === '' ? '' : (v as ModerationSampleCreateRequest['category']));
              setSamplesPage(1);
            }}
          >
            <option value="">全部分类</option>
            <option value="AD_SAMPLE">{labelWithEnum(CATEGORY_LABEL.AD_SAMPLE, 'AD_SAMPLE')}</option>
            <option value="HISTORY_VIOLATION">{labelWithEnum(CATEGORY_LABEL.HISTORY_VIOLATION, 'HISTORY_VIOLATION')}</option>
          </select>

          <select
            className="rounded border px-3 py-2"
            value={samplesEnabled}
            onChange={(e) => {
              const v = e.target.value;
              setSamplesEnabled(v === '' ? '' : (v as 'true' | 'false'));
              setSamplesPage(1);
            }}
          >
            <option value="">全部状态</option>
            <option value="true">启用（true）</option>
            <option value="false">禁用（false）</option>
          </select>

          <select
            className="rounded border px-3 py-2"
            value={samplesPageSize}
            onChange={(e) => {
              setSamplesPageSize(Number(e.target.value));
              setSamplesPage(1);
            }}
          >
            <option value={10}>10 /页</option>
            <option value={20}>20 /页</option>
            <option value={50}>50 /页</option>
            <option value={100}>100 /页</option>
          </select>

          <div className="text-sm text-gray-600 flex items-center">
            总数：<b className="ml-1">{samplesTotalElements}</b>
          </div>
        </div>

        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-600 bg-gray-50">
                <th className="py-2 px-2">ID</th>
                <th className="py-2 px-2">分类</th>
                <th className="py-2 px-2">启用</th>
                <th className="py-2 px-2">风险等级</th>
                <th className="py-2 px-2">来源</th>
                <th className="py-2 px-2">原始文本</th>
                <th className="py-2 px-2">更新时间</th>
                <th className="py-2 px-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {samples.map((s) => (
                <tr key={s.id} className="border-t">
                  <td className="py-2 px-2">{s.id}</td>
                  <td className="py-2 px-2">{labelWithEnum(CATEGORY_LABEL[s.category], s.category)}</td>
                  <td className="py-2 px-2">{s.enabled ? '启用（true）' : '禁用（false）'}</td>
                  <td className="py-2 px-2">{s.riskLevel ?? '—'}</td>
                  <td className="py-2 px-2">{sourceDisplay(s.source ? String(s.source) : null)}</td>
                  <td className="py-2 px-2 text-gray-700 max-w-[520px]">
                    <div className="line-clamp-2">{s.rawText ?? '—'}</div>
                  </td>
                  <td className="py-2 px-2">{formatDateTime(s.updatedAt)}</td>
                  <td className="py-2 px-2">
                    <div className="flex items-center gap-2">
                      <button type="button" className="rounded border px-2 py-1" onClick={() => openEditSample(s)}>
                        编辑
                      </button>
                      <button
                        type="button"
                        className="rounded border px-2 py-1 disabled:opacity-60"
                        disabled={syncingId === s.id}
                        onClick={() => void syncOneSample(s.id)}
                        title="把该样本重新写入 ES（会调用 embedding）"
                      >
                        {syncingId === s.id ? '同步中…' : '同步索引'}
                      </button>
                      <button type="button" className="rounded border border-red-300 text-red-700 px-2 py-1" onClick={() => void removeSample(s.id)}>
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {samples.length === 0 ? (
                <tr>
                  <td className="py-4 px-2 text-gray-500" colSpan={8}>
                    暂无数据
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between">
          <div className="text-sm text-gray-600">
            页码 {samplesPage} / {samplesTotalPages}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 disabled:opacity-60"
              disabled={samplesLoading || samplesPage <= 1}
              onClick={() => setSamplesPage((p) => Math.max(1, p - 1))}
            >
              上一页
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1.5 disabled:opacity-60"
              disabled={samplesLoading || samplesPage >= samplesTotalPages}
              onClick={() => setSamplesPage((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </div>

        {sampleModalOpen ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onMouseDown={() => setSampleModalOpen(false)}>
            <div className="w-full max-w-2xl rounded bg-white shadow p-4 space-y-3" onMouseDown={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-between">
                <div className="text-lg font-semibold">{editingSampleId == null ? '新增样本' : `编辑样本 #${editingSampleId}`}</div>
                <button type="button" className="rounded border px-3 py-1.5" onClick={() => setSampleModalOpen(false)}>
                  关闭
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <label className="text-sm space-y-1">
                  <div className="text-gray-600">分类</div>
                  <select
                    className="w-full rounded border px-3 py-2"
                    value={sampleForm.category}
                    onChange={(e) => {
                      const v = e.target.value as ModerationSampleCreateRequest['category'];
                      setSampleForm((p) => ({ ...p, category: v }));
                    }}
                  >
                    <option value="AD_SAMPLE">{labelWithEnum(CATEGORY_LABEL.AD_SAMPLE, 'AD_SAMPLE')}</option>
                    <option value="HISTORY_VIOLATION">{labelWithEnum(CATEGORY_LABEL.HISTORY_VIOLATION, 'HISTORY_VIOLATION')}</option>
                  </select>
                </label>

                <label className="text-sm space-y-1">
                  <div className="text-gray-600">是否启用</div>
                  <select
                    className="w-full rounded border px-3 py-2"
                    value={String(sampleForm.enabled ?? true)}
                    onChange={(e) => setSampleForm((p) => ({ ...p, enabled: e.target.value === 'true' }))}
                  >
                    <option value="true">启用（true）</option>
                    <option value="false">禁用（false）</option>
                  </select>
                </label>

                <label className="text-sm space-y-1">
                  <div className="text-gray-600">来源</div>
                  <select
                    className="w-full rounded border px-3 py-2"
                    value={sampleForm.source ?? 'HUMAN'}
                    onChange={(e) => {
                      const v = e.target.value as SampleSource;
                      setSampleForm((p) => ({ ...p, source: v }));
                    }}
                  >
                    <option value="HUMAN">{labelWithEnum(SOURCE_LABEL.HUMAN, 'HUMAN')}</option>
                    <option value="RULE">{labelWithEnum(SOURCE_LABEL.RULE, 'RULE')}</option>
                    <option value="LLM">{labelWithEnum(SOURCE_LABEL.LLM, 'LLM')}</option>
                    <option value="IMPORT">{labelWithEnum(SOURCE_LABEL.IMPORT, 'IMPORT')}</option>
                  </select>
                </label>

                <label className="text-sm space-y-1">
                  <div className="text-gray-600">风险等级</div>
                  <input
                    className="w-full rounded border px-3 py-2"
                    type="number"
                    value={String(sampleForm.riskLevel ?? 0)}
                    onChange={(e) => setSampleForm((p) => ({ ...p, riskLevel: Number(e.target.value) }))}
                  />
                </label>

                <label className="text-sm space-y-1">
                  <div className="text-gray-600">关联内容类型（可选）</div>
                  <select
                    className="w-full rounded border px-3 py-2"
                    value={sampleForm.refContentType ?? ''}
                    onChange={(e) => {
                      const v = e.target.value;
                      setSampleForm((p) => ({ ...p, refContentType: v === '' ? null : (v as SampleRefContentType) }));
                    }}
                  >
                    <option value="">无</option>
                    <option value="POST">{labelWithEnum(CONTENT_TYPE_LABEL.POST, 'POST')}</option>
                    <option value="COMMENT">{labelWithEnum(CONTENT_TYPE_LABEL.COMMENT, 'COMMENT')}</option>
                  </select>
                </label>

                <label className="text-sm space-y-1">
                  <div className="text-gray-600">关联内容ID（可选）</div>
                  <input
                    className="w-full rounded border px-3 py-2"
                    type="number"
                    value={sampleForm.refContentId ?? ''}
                    onChange={(e) => {
                      const t = e.target.value;
                      setSampleForm((p) => ({ ...p, refContentId: t === '' ? null : Number(t) }));
                    }}
                  />
                </label>
              </div>

              <label className="text-sm space-y-1 block">
                <div className="text-gray-600">标签 labels（MySQL JSON，可选）</div>
                <input
                  className="w-full rounded border px-3 py-2"
                  placeholder='例如：["ad","spam"]（JSON 数组）'
                  value={sampleForm.labels ?? ''}
                  onChange={(e) => setSampleForm((p) => ({ ...p, labels: e.target.value || null }))}
                />
              </label>

              <label className="text-sm space-y-1 block">
                <div className="text-gray-600">原始文本 rawText</div>
                <textarea
                  className="w-full rounded border px-3 py-2 min-h-[110px]"
                  value={sampleForm.rawText}
                  onChange={(e) => setSampleForm((p) => ({ ...p, rawText: e.target.value }))}
                />
              </label>

              <div className="flex items-center justify-end gap-2">
                <button type="button" className="rounded border px-4 py-2" onClick={() => setSampleModalOpen(false)} disabled={sampleSaving}>
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                  onClick={() => void submitSample()}
                  disabled={sampleSaving}
                >
                  {sampleSaving ? '保存中…' : '保存'}
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>

      <ModerationPipelineHistoryPanel
        title="嵌入相似检测 · 审核历史记录"
        initialMode={qidForHistory ? { kind: 'queue', queueId: qidForHistory } : undefined}
        stageFilter={['VEC']}
      />
    </div>
  );
};

export default EmbedForm;
