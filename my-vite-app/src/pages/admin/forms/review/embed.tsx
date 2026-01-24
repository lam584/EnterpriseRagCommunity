// embed.tsx
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { getCsrfToken } from '../../../../utils/csrfUtils';
import { useSearchParams } from 'react-router-dom';
import {
  createSample,
  deleteSample,
  listSamples,
  syncSample,
  syncSamplesIncremental,
  updateSample,
  type ModerationSample,
  type ModerationSampleCreateRequest,
  type ModerationSamplesSyncResult,
} from '../../../../services/moderationEmbedSamplesService';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';

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

  // ===== Reindex (MySQL -> ES) =====
  const [reindexing, setReindexing] = useState(false);
  const [reindexResult, setReindexResult] = useState<null | {
    total?: number;
    success?: number;
    failed?: number;
    failedIds?: number[];
    cleared?: boolean | null;
    clearError?: string | null;
    orphanDeleted?: number | null;
    orphanFailed?: number | null;
    orphanFailedIds?: number[];
  }>(null);

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

  const saveConfig = useCallback(async () => {
    setCfgSaving(true);
    setError(null);
    try {
      const csrf = await getCsrfToken();
      const payload = {
        enabled: cfgForm.enabled,
        embeddingModel: cfgForm.embeddingModel.trim() ? cfgForm.embeddingModel.trim() : null,
        embeddingDims: cfgForm.embeddingDims.trim() ? clampInt(Number(cfgForm.embeddingDims), 0, 100_000) : 0,
        maxInputChars: cfgForm.maxInputChars.trim() ? clampInt(Number(cfgForm.maxInputChars), 0, 200_000) : 0,
        defaultTopK: cfgForm.defaultTopK.trim() ? clampInt(Number(cfgForm.defaultTopK), 1, 50) : 5,
        defaultThreshold: cfgForm.defaultThreshold.trim() ? Math.max(0, Math.min(1, Number(cfgForm.defaultThreshold))) : 0.15,
        defaultNumCandidates: cfgForm.defaultNumCandidates.trim() ? clampInt(Number(cfgForm.defaultNumCandidates), 0, 10_000) : 0,
      };
      const res = await fetch(apiUrl('/api/admin/moderation/embed/config'), {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrf,
        },
        credentials: 'include',
        body: JSON.stringify(payload),
      });
      const data: unknown = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(getBackendMessage(data) || '更新配置失败');
        return;
      }
      const c = data as SimilarityConfig;
      setCfg(c);
      setCfgForm(toCfgFormState(c));
      setCfgEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCfgSaving(false);
    }
  }, [cfgForm]);

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
    setReindexResult(null);
    try {
      const csrf = await getCsrfToken();
      const res = await fetch(apiUrl('/api/admin/moderation/embed/reindex?onlyEnabled=true&batchSize=200'), {
        method: 'POST',
        headers: {
          'X-XSRF-TOKEN': csrf,
        },
        credentials: 'include',
      });
      const data: unknown = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(getBackendMessage(data) || '重建索引失败');
        return;
      }
      setReindexResult(data as {
        total?: number;
        success?: number;
        failed?: number;
        failedIds?: number[];
        cleared?: boolean | null;
        clearError?: string | null;
        orphanDeleted?: number | null;
        orphanFailed?: number | null;
        orphanFailedIds?: number[];
      });

      // Optional UX: refresh sample list after reindex
      void loadSamples();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setReindexing(false);
    }
  }, [loadSamples]);

  // ===== ES Sync (incremental / one) =====
  const [syncFromId, setSyncFromId] = useState<string>('');
  const [syncBatching, setSyncBatching] = useState(false);
  const [syncBatchResult, setSyncBatchResult] = useState<{ total?: number; success?: number; failed?: number; failedIds?: number[] } | null>(null);
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

  const syncBatch = useCallback(async () => {
    const from = syncFromId.trim() ? Number(syncFromId.trim()) : undefined;
    if (from !== undefined && (!Number.isFinite(from) || from <= 0)) {
      setError('fromId 必须是正整数');
      return;
    }
    setSyncBatching(true);
    setError(null);
    setSyncBatchResult(null);
    try {
      const r = await syncSamplesIncremental({ onlyEnabled: true, batchSize: 200, fromId: from });
      setSyncBatchResult({ total: r.total, success: r.success, failed: r.failed, failedIds: r.failedIds });
      await loadSamples();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSyncBatching(false);
    }
  }, [loadSamples, syncFromId]);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  useEffect(() => {
    void loadSamples();
  }, [loadSamples]);

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
              void loadSamples();
            }}
            disabled={cfgLoading || samplesLoading}
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

        {/* Reindex */}
        <div className="rounded border bg-gray-50 p-3 space-y-2">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="font-semibold">样本库 → ES 同步</div>
              <div className="text-sm text-gray-600">全量重建：先清空 ES，再全量写入，并清理孤儿文档（会调用 embedding）。</div>
            </div>
            <button
              type="button"
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
              disabled={reindexing}
              onClick={() => void reindexSamples()}
            >
              {reindexing ? '重建中…' : '重建 ES 索引'}
            </button>
          </div>
          {reindexResult ? (
            <div className="text-sm text-gray-700">
              本次结果：total <b>{reindexResult.total ?? '—'}</b>，success <b>{reindexResult.success ?? '—'}</b>，failed <b>{reindexResult.failed ?? '—'}</b>
              <div className="text-xs text-gray-500 mt-1">
                cleared: <b>{String(reindexResult.cleared ?? '—')}</b>
                {reindexResult.clearError ? <span className="text-red-700">（clearError: {reindexResult.clearError}）</span> : null}
              </div>
              <div className="text-xs text-gray-500">
                orphanDeleted <b>{reindexResult.orphanDeleted ?? '—'}</b>，orphanFailed <b>{reindexResult.orphanFailed ?? '—'}</b>
                {reindexResult.orphanFailedIds && reindexResult.orphanFailedIds.length ? (
                  <span>（orphanFailedIds: {reindexResult.orphanFailedIds.join(', ')}）</span>
                ) : null}
              </div>
              {reindexResult.failedIds && reindexResult.failedIds.length ? (
                <div className="text-xs text-gray-500 mt-1">失败样本ID（最多50个）：{reindexResult.failedIds.join(', ')}</div>
              ) : null}
            </div>
          ) : null}

          <div className="flex items-center gap-2 pt-2">
            <input
              className="rounded border px-3 py-2 text-sm"
              placeholder="增量同步 fromId（可选）"
              value={syncFromId}
              onChange={(e) => setSyncFromId(e.target.value)}
            />
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm disabled:opacity-60"
              disabled={syncBatching}
              onClick={() => void syncBatch()}
            >
              {syncBatching ? '同步中…' : '增量同步到 ES'}
            </button>
            {syncBatchResult ? (
              <div className="text-xs text-gray-600">
                增量结果：total {syncBatchResult.total ?? '—'}，success {syncBatchResult.success ?? '—'}，failed {syncBatchResult.failed ?? '—'}
              </div>
            ) : null}
          </div>
        </div>

        <div className="rounded border bg-gray-50 p-3 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="font-semibold">运行时配置</div>
              <div className="text-sm text-gray-600">用于控制 VEC 阶段使用的 embedding 模型/维度/最大输入长度，以及手动测试的默认参数。</div>
            </div>
            <div className="flex items-center gap-2">
              <select
                className={`rounded border px-3 py-2 text-sm disabled:opacity-100 disabled:bg-gray-50 ${cfgForm.enabled ? 'text-green-700' : 'text-red-700'}`}
                value={cfgForm.enabled ? 'on' : 'off'}
                disabled={!cfgEditing || cfgLoading || cfgSaving}
                onChange={(e) => setCfgForm((p) => ({ ...p, enabled: e.target.value === 'on' }))}
                title={!cfgEditing ? '只读（点击右侧「编辑配置」后可修改）' : '修改开关（需保存生效）'}
              >
                <option value="on">开启功能</option>
                <option value="off">关闭功能</option>
              </select>

              {!cfgEditing ? (
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                  disabled={cfgLoading || cfgSaving}
                  onClick={() => {
                    setCfgEditing(true);
                    setError(null);
                  }}
                  title="进入编辑模式"
                >
                  编辑配置
                </button>
              ) : (
                <>
                  <button
                    type="button"
                    className="rounded border px-3 py-2 disabled:opacity-60"
                    disabled={cfgLoading || cfgSaving}
                    onClick={() => {
                      setCfgForm(toCfgFormState(cfg));
                      setCfgEditing(false);
                      setError(null);
                    }}
                    title="放弃未保存的修改，并恢复到最近一次加载/保存的配置"
                  >
                    放弃修改
                  </button>
                  <button
                    type="button"
                    className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                    disabled={cfgLoading || cfgSaving}
                    onClick={() => void saveConfig()}
                    title="保存并生效"
                  >
                    {cfgSaving ? '保存中…' : '保存配置'}
                  </button>
                </>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <div className="text-sm font-medium mb-1">
                Embedding 模型名（可选）
                {!cfgEditing ? <span className="text-xs text-gray-500 ml-2">（只读，点击右上角「编辑配置」修改）</span> : null}
              </div>
              <input
                className={`w-full rounded border px-3 py-2 ${!cfgEditing ? 'bg-gray-50' : ''}`}
                placeholder="留空使用后端默认"
                value={cfgForm.embeddingModel}
                readOnly={!cfgEditing}
                onChange={(e) => {
                  if (!cfgEditing) return;
                  setCfgForm((p) => ({ ...p, embeddingModel: e.target.value }));
                }}
              />
            </div>
            <div>
              <div className="text-sm font-medium mb-1">Embedding 维度 dims（0=自动）</div>
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
              <div className="text-sm font-medium mb-1">最大输入长度 maxInputChars（0=不截断）</div>
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
              <div className="text-sm font-medium mb-1">默认 TopK（1~50）</div>
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
              <div className="text-sm font-medium mb-1">默认阈值 threshold（0~1）</div>
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
              <div className="text-sm font-medium mb-1">默认 numCandidates（0=自动）</div>
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
          <div className="text-xs text-gray-500">更新时间：{formatDateTime(cfg?.updatedAt)}</div>
        </div>

        {/* Manual check */}
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
                bestDistance <b>{checkResult.bestDistance ?? '—'}</b>；threshold <b>{checkResult.threshold ?? '—'}</b>；topK <b>{checkResult.topK ?? '—'}</b>；numCandidates{' '}
                <b>{checkResult.numCandidates ?? '—'}</b>；dims <b>{checkResult.embeddingDims ?? '—'}</b>；model <b>{checkResult.embeddingModel ?? '—'}</b>；maxInputChars{' '}
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
