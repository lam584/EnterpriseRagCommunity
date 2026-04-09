import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ModerationChunkReviewConfig } from '../../../../services/moderationChunkReviewConfigService';
import { getModerationChunkReviewConfig, updateModerationChunkReviewConfig } from '../../../../services/moderationChunkReviewConfigService';
import type { LlmModerationConfigDTO } from '../../../../services/moderationLlmService';
import { adminGetLlmModerationConfig, adminUpsertLlmModerationConfig } from '../../../../services/moderationLlmService';
import type { ModerationChunkContentPreview, ModerationChunkLogDetail, ModerationChunkLogItem } from '../../../../services/moderationChunkReviewLogsService';
import { adminGetModerationChunkLogContent, adminGetModerationChunkLogDetail, adminListModerationChunkLogs } from '../../../../services/moderationChunkReviewLogsService';
import type { ModerationPolicyConfigDTO, ModerationPolicyContentType } from '../../../../services/moderationPolicyService';
import { adminGetModerationPolicyConfig } from '../../../../services/moderationPolicyService';
import { downloadBlob } from '../../../../utils/download';
import { estimateVisionImageTokens } from '../../../../utils/visionImageTokens';
import {
    DEFAULT_DASHSCOPE_MAX_PIXELS,
    SOURCE_TYPE_ZH,
    STATUS_ZH,
    VERDICT_ZH,
    VISION_TOKEN_GRID_SIDE,
    VISION_TOKEN_LIMIT,
    VISION_TOKEN_PIXELS,
    clampInt,
    copyText,
    formatBudgetConvergenceSummary,
    formatDateTime,
    formatEnumZh,
    collectMissingImageSizeEntries,
    hasResolvedImageSize,
    isAbortError,
    parseBudgetConvergenceLog,
    parseOptionalPositiveInt,
    parseTokenDiagnostics,
    safeJson,
    sharedImageSizeProbe,
    toUrlString,
} from './chunk-review-helpers';

const Section: React.FC<React.PropsWithChildren<{ title: string; desc?: string }>> = ({ title, desc, children }) => (
  <div className="bg-white rounded-lg shadow p-4 space-y-3">
    <div>
      <div className="text-lg font-semibold text-gray-900">{title}</div>
      {desc ? <div className="text-sm text-gray-600 mt-1">{desc}</div> : null}
    </div>
    {children}
  </div>
);

const Switch: React.FC<{ checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }> = ({
  checked,
  onChange,
  label,
  disabled,
}) => (
  <label className="inline-flex items-center gap-2 text-sm text-gray-700">
    <input
      type="checkbox"
      className="h-4 w-4"
      checked={checked}
      disabled={disabled}
      onChange={(e) => onChange(e.target.checked)}
    />
    <span>{label}</span>
  </label>
);

const NumberInput: React.FC<{
  value: number;
  onChange: (v: number) => void;
  min: number;
  max: number;
  step?: number;
  disabled?: boolean;
}> = ({ value, onChange, min, max, step = 1, disabled }) => (
  <input
    type="number"
    className="rounded border px-3 py-2 w-full"
    value={value}
    min={min}
    max={max}
    step={step}
    disabled={disabled}
    onChange={(e) => onChange(Number(e.target.value))}
  />
);

const ChunkReviewConfigForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  const [cfg, setCfg] = useState<ModerationChunkReviewConfig | null>(null);
  const [committedCfg, setCommittedCfg] = useState<ModerationChunkReviewConfig | null>(null);
  const [llmCfg, setLlmCfg] = useState<LlmModerationConfigDTO | null>(null);
  const [committedLlmCfg, setCommittedLlmCfg] = useState<LlmModerationConfigDTO | null>(null);
  const [llmLoadError, setLlmLoadError] = useState<string | null>(null);
  const [policyContentType, setPolicyContentType] = useState<ModerationPolicyContentType>('POST');
  const [policyCfg, setPolicyCfg] = useState<ModerationPolicyConfigDTO | null>(null);
  const [policyLoadError, setPolicyLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const hasUnsavedChanges = useMemo(
    () => JSON.stringify({ cfg, llmCfg }) !== JSON.stringify({ cfg: committedCfg, llmCfg: committedLlmCfg }),
    [cfg, committedCfg, llmCfg, committedLlmCfg]
  );

  const [logsLoading, setLogsLoading] = useState(false);
  const [logsError, setLogsError] = useState<string | null>(null);
  const [logs, setLogs] = useState<ModerationChunkLogItem[]>([]);

  const [logLimit, setLogLimit] = useState(50);
  const [qQueueId, setQQueueId] = useState('');
  const [qFileAssetId, setQFileAssetId] = useState('');
  const [qStatus, setQStatus] = useState('');
  const [qVerdict, setQVerdict] = useState('');
  const [qSourceType, setQSourceType] = useState('');
  const [qKeyword, setQKeyword] = useState('');
  const [qOnlyResharded, setQOnlyResharded] = useState(false);

  const visibleLogs = useMemo(() => {
    if (!qOnlyResharded) return logs;
    return logs.filter((it) => parseBudgetConvergenceLog(it.budgetConvergenceLog)?.triggeredResharding === true);
  }, [logs, qOnlyResharded]);

  type ImageSizeStatus = 'idle' | 'loading' | 'done' | 'failed';
  type LogImageInfo = {
    index?: number | null;
    label: string;
    url?: string | null;
    width?: number | null;
    height?: number | null;
    sizeStatus: ImageSizeStatus;
  };

  const toLogImageInfo = useCallback((it: {
    index?: number | null;
    placeholder?: string | null;
    fileName?: string | null;
    url?: string | null;
    width?: number | null;
    height?: number | null;
  }): LogImageInfo => {
    const sizeStatus: ImageSizeStatus = hasResolvedImageSize(it) ? 'done' : 'idle';
    return {
      index: it.index,
      label: String(it.placeholder || it.fileName || it.url || (it.index != null ? `图片#${it.index}` : '图片')),
      url: it.url,
      width: it.width,
      height: it.height,
      sizeStatus,
    };
  }, []);
  const [logImages, setLogImages] = useState<Record<number, { loading: boolean; error: string | null; images: LogImageInfo[] }>>({});
  const logImagesRef = useRef(logImages);
  useEffect(() => {
    logImagesRef.current = logImages;
  }, [logImages]);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detail, setDetail] = useState<ModerationChunkLogDetail | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<'overview' | 'content' | 'debug'>('overview');

  const [contentLoading, setContentLoading] = useState(false);
  const [contentError, setContentError] = useState<string | null>(null);
  const [content, setContent] = useState<ModerationChunkContentPreview | null>(null);
  const [contentImages, setContentImages] = useState<LogImageInfo[] | null>(null);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setOk(null);
    setLlmLoadError(null);
    setPolicyLoadError(null);
    try {
      const data = await getModerationChunkReviewConfig();
      setCfg(data);
      setCommittedCfg(data);
      try {
        const l = await adminGetLlmModerationConfig();
        setLlmCfg(l);
        setCommittedLlmCfg(l);
      } catch (e) {
        setLlmLoadError(e instanceof Error ? e.message : '加载 LLM 审核配置失败');
        setLlmCfg(null);
        setCommittedLlmCfg(null);
      }
      try {
        const p = await adminGetModerationPolicyConfig(policyContentType);
        setPolicyCfg(p);
      } catch (e) {
        setPolicyLoadError(e instanceof Error ? e.message : '加载审核策略配置失败');
        setPolicyCfg(null);
      }
      setEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [policyContentType]);

  useEffect(() => {
    void load();
  }, [load]);

  const canSave = useMemo(() => !!cfg && !saving && editing && hasUnsavedChanges, [cfg, editing, hasUnsavedChanges, saving]);

  const save = useCallback(async () => {
    if (!cfg) return;
    if (!editing) return;
    setSaving(true);
    setError(null);
    setOk(null);
    try {
      const errs: string[] = [];
      const payload: Partial<ModerationChunkReviewConfig> = {
        enabled: !!cfg.enabled,
        chunkMode: 'SEMANTIC',
        chunkThresholdChars: clampInt(cfg.chunkThresholdChars, 1000, 5_000_000),
        chunkSizeChars: clampInt(cfg.chunkSizeChars, 500, 10_000),
        overlapChars: clampInt(cfg.overlapChars, 0, 2000),
        maxChunksTotal: clampInt(cfg.maxChunksTotal, 1, 2000),
        chunksPerRun: clampInt(cfg.chunksPerRun, 1, 50),
        maxConcurrentWorkers: clampInt(cfg.maxConcurrentWorkers, 1, 64),
        maxAttempts: clampInt(cfg.maxAttempts, 1, 20),

        enableTempIndexHints: !!cfg.enableTempIndexHints,
        enableContextCompress: !!cfg.enableContextCompress,
        enableGlobalMemory: !!cfg.enableGlobalMemory,
        sendImagesOnlyWhenInEvidence: cfg.sendImagesOnlyWhenInEvidence !== false,
        includeImagesBlockOnlyForEvidenceMatches: cfg.includeImagesBlockOnlyForEvidenceMatches !== false,

        queueAutoRefreshEnabled: !!cfg.queueAutoRefreshEnabled,
        queuePollIntervalMs: clampInt(cfg.queuePollIntervalMs, 1000, 60_000),
      };
      if ((payload.overlapChars ?? 0) >= (payload.chunkSizeChars ?? 0)) {
        payload.overlapChars = Math.max(0, Math.floor((payload.chunkSizeChars ?? 0) / 10));
      }
      try {
        const saved = await updateModerationChunkReviewConfig(payload);
        setCfg(saved);
        setCommittedCfg(saved);
      } catch (e) {
        errs.push(e instanceof Error ? e.message : '保存分片审核配置失败');
      }

      if (llmCfg) {
        try {
          const llmPayload = {
            ...llmCfg,
          };
          const savedLlm = await adminUpsertLlmModerationConfig(llmPayload);
          setLlmCfg(savedLlm);
          setCommittedLlmCfg(savedLlm);
        } catch (e) {
          errs.push(e instanceof Error ? e.message : '保存 LLM 审核配置失败');
        }
      }

      if (errs.length > 0) throw new Error(errs.join('；'));
      setEditing(false);
      setOk('已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }, [cfg, editing, llmCfg]);

  const exportLogs = useCallback(() => {
    if (visibleLogs.length === 0) return;
    const header = [
      'ID',
      'Queue ID',
      'Content Type',
      'Content ID',
      'Source Type',
      'File Name/Key',
      'Chunk Index',
      'Start Offset',
      'End Offset',
      'Status',
      'Verdict',
      'Confidence',
      'Attempts',
      'Tokens In',
      'Tokens Out',
      'Budget/ChunkSize',
      'Last Error',
      'Created At',
      'Decided At',
    ].join(',');

    const rows = visibleLogs.map((it) => {
      return [
        it.id,
        it.queueId,
        it.contentType || '',
        it.contentId || '',
        it.sourceType || '',
        (it.fileName || it.sourceKey || '').replace(/"/g, '""'),
        it.chunkIndex || '',
        it.startOffset || '',
        it.endOffset || '',
        it.status || '',
        it.verdict || '',
        it.confidence || '',
        it.attempts || '',
        it.tokensIn || '',
        it.tokensOut || '',
        formatBudgetConvergenceSummary(it.budgetConvergenceLog),
        (it.lastError || '').replace(/"/g, '""').replace(/\n/g, ' '),
        it.createdAt || '',
        it.decidedAt || '',
      ]
        .map((v) => `"${v}"`)
        .join(',');
    });

    const csv = [header, ...rows].join('\n');
    // Add BOM for Excel compatibility
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    downloadBlob(blob, `chunk-review-logs-${new Date().toISOString().slice(0, 10)}.csv`);
  }, [visibleLogs]);

  const loadLogs = useCallback(async () => {
    setLogsLoading(true);
    setLogsError(null);
    try {
      const lim = clampInt(logLimit, 1, 1000);
      const queueId = parseOptionalPositiveInt(qQueueId);
      const fileAssetId = parseOptionalPositiveInt(qFileAssetId);
      const keyword = qKeyword.trim();
      const items = await adminListModerationChunkLogs({
        limit: lim,
        queueId,
        fileAssetId,
        status: qStatus.trim() || undefined,
        verdict: qVerdict.trim() || undefined,
        sourceType: qSourceType.trim() || undefined,
        keyword: keyword ? keyword : undefined,
      });
      setLogs(Array.isArray(items) ? items : []);
    } catch (e) {
      setLogsError(e instanceof Error ? e.message : '加载日志失败');
      setLogs([]);
    } finally {
      setLogsLoading(false);
    }
  }, [logLimit, qFileAssetId, qKeyword, qQueueId, qSourceType, qStatus, qVerdict]);

  const probeLogImageSizes = useCallback(
    (logId: number, imgs: LogImageInfo[], signal: AbortSignal) => {
      const missing = collectMissingImageSizeEntries(imgs);

      if (missing.length === 0) return;

      const missingIndex = new Set(missing.map((it) => it.idx));
      setLogImages((prev) => {
        const cur = prev[logId];
        if (!cur) return prev;
        const nextImages: LogImageInfo[] = imgs.map((img, idx) => (missingIndex.has(idx) ? { ...img, sizeStatus: 'loading' as ImageSizeStatus } : img));
        return {
          ...prev,
          [logId]: { ...cur, loading: false, error: null, images: nextImages },
        };
      });

      void Promise.all(
        missing.map(async ({ idx, url }) => {
          if (!url) return;
          try {
            const res = await sharedImageSizeProbe.get(url, signal);
            if (signal.aborted) return;
            setLogImages((prev) => {
              const cur = prev[logId];
              if (!cur) return prev;
              if (!cur.images[idx] || toUrlString(cur.images[idx].url) !== url) return prev;
              const nextImages: LogImageInfo[] = cur.images.map((img, i) => {
                if (i !== idx) return img;
                if (res) return { ...img, width: res.width, height: res.height, sizeStatus: 'done' as ImageSizeStatus };
                return { ...img, sizeStatus: 'failed' as ImageSizeStatus };
              });
              return { ...prev, [logId]: { ...cur, images: nextImages } };
            });
          } catch (e) {
            if (isAbortError(e) || signal.aborted) return;
            setLogImages((prev) => {
              const cur = prev[logId];
              if (!cur) return prev;
              if (!cur.images[idx] || toUrlString(cur.images[idx].url) !== url) return prev;
              const nextImages: LogImageInfo[] = cur.images.map((img, i) => (i === idx ? { ...img, sizeStatus: 'failed' as ImageSizeStatus } : img));
              return { ...prev, [logId]: { ...cur, images: nextImages } };
            });
          }
        })
      );
    },
    [setLogImages]
  );

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError(null);
    setDetail(null);
    setDetailId(id);
    setDetailTab('overview');
    setContentLoading(false);
    setContentError(null);
    setContent(null);
    setContentImages(null);
    try {
      const d = await adminGetModerationChunkLogDetail(id);
      setDetail(d);
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : '加载详情失败');
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const loadContent = useCallback(async () => {
    if (detailId == null) return;
    setContentLoading(true);
    setContentError(null);
    try {
      const d = await adminGetModerationChunkLogContent(detailId);
      setContent(d);
    } catch (e) {
      setContentError(e instanceof Error ? e.message : '加载分片内容失败');
      setContent(null);
      setContentImages(null);
    } finally {
      setContentLoading(false);
    }
  }, [detailId]);

  useEffect(() => {
    if (!detailOpen) return;
    if (detailId == null) return;
    if (contentLoading) return;
    if (content !== null) return;
    if (contentError) return;
    void loadContent();
  }, [content, contentError, contentLoading, detailId, detailOpen, loadContent]);

  useEffect(() => {
    if (!detailOpen) {
      setContentImages(null);
      return;
    }
    if (!content) {
      setContentImages(null);
      return;
    }
    const controller = new AbortController();
    const imgs: LogImageInfo[] = (content.images ?? [])
      .filter((it) => Boolean(it?.url))
      .map(toLogImageInfo);
    setContentImages(imgs);

    const missing = collectMissingImageSizeEntries(imgs);

    if (missing.length === 0) return () => controller.abort();

    const missingIndex = new Set(missing.map((it) => it.idx));
    setContentImages((prev) => {
      if (!prev) return prev;
      const next: LogImageInfo[] = prev.map((img, idx) => (missingIndex.has(idx) ? { ...img, sizeStatus: 'loading' as ImageSizeStatus } : img));
      return next;
    });

    void Promise.all(
      missing.map(async ({ idx, url }) => {
        if (!url) return;
        try {
          const res = await sharedImageSizeProbe.get(url, controller.signal);
          if (controller.signal.aborted) return;
          setContentImages((prev) => {
            if (!prev || !prev[idx] || toUrlString(prev[idx].url) !== url) return prev;
            const next: LogImageInfo[] = prev.map((img, i) => {
              if (i !== idx) return img;
              if (res) return { ...img, width: res.width, height: res.height, sizeStatus: 'done' as ImageSizeStatus };
              return { ...img, sizeStatus: 'failed' as ImageSizeStatus };
            });
            return next;
          });
        } catch (e) {
          if (isAbortError(e) || controller.signal.aborted) return;
          setContentImages((prev) => {
            if (!prev || !prev[idx] || toUrlString(prev[idx].url) !== url) return prev;
            const next: LogImageInfo[] = prev.map((img, i) => (i === idx ? { ...img, sizeStatus: 'failed' as ImageSizeStatus } : img));
            return next;
          });
        }
      })
    );

    return () => controller.abort();
  }, [content, detailOpen, toLogImageInfo]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  useEffect(() => {
    if (logsLoading) return;
    if (visibleLogs.length === 0) {
      setLogImages({});
      return;
    }

    const controller = new AbortController();
    const ids = visibleLogs.map((it) => it.id);
    const snapshot = logImagesRef.current;
    const toFetch = ids.filter((id) => !snapshot[id] && Number.isFinite(id));

    setLogImages((prev) => {
      const next: Record<number, { loading: boolean; error: string | null; images: LogImageInfo[] }> = {};
      for (const id of ids) {
        next[id] = prev[id] ?? { loading: toFetch.includes(id), error: null, images: [] };
      }
      return next;
    });

    if (toFetch.length === 0) return () => controller.abort();

    const concurrency = Math.min(4, toFetch.length);
    const queue = [...toFetch];

    const worker = async () => {
      while (queue.length > 0 && !controller.signal.aborted) {
        const id = queue.shift();
        if (id == null) return;
        setLogImages((prev) => ({ ...prev, [id]: { ...(prev[id] ?? { loading: false, error: null, images: [] }), loading: true, error: null } }));
        try {
          const preview = await adminGetModerationChunkLogContent(id, controller.signal);
          const imgs: LogImageInfo[] = (preview.images ?? [])
            .filter((it) => Boolean(it?.url))
            .map(toLogImageInfo);
          setLogImages((prev) => ({ ...prev, [id]: { loading: false, error: null, images: imgs } }));
          probeLogImageSizes(id, imgs, controller.signal);
        } catch (e) {
          if (controller.signal.aborted) return;
          setLogImages((prev) => ({
            ...prev,
            [id]: { loading: false, error: e instanceof Error ? e.message : '加载图片信息失败', images: [] },
          }));
        }
      }
    };

    void Promise.all(Array.from({ length: concurrency }, () => worker()));
    return () => controller.abort();
  }, [visibleLogs, logsLoading, probeLogImageSizes, toLogImageInfo]);

  useEffect(() => {
    if (!detailOpen) return;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeDetail();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = prevOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [closeDetail, detailOpen]);

  const pillClass = (tone: 'gray' | 'green' | 'red' | 'amber' | 'blue') => {
    switch (tone) {
      case 'green':
        return 'inline-flex items-center rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-200';
      case 'red':
        return 'inline-flex items-center rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-200';
      case 'amber':
        return 'inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-200';
      case 'blue':
        return 'inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-200';
      default:
        return 'inline-flex items-center rounded-full bg-gray-50 px-2 py-0.5 text-xs font-medium text-gray-700 ring-1 ring-inset ring-gray-200';
    }
  };

  const statusTone = (s: string | null | undefined) => {
    switch (s) {
      case 'SUCCESS':
        return 'green';
      case 'FAILED':
        return 'red';
      case 'RUNNING':
        return 'blue';
      case 'PENDING':
        return 'amber';
      default:
        return 'gray';
    }
  };

  const verdictTone = (v: string | null | undefined) => {
    switch (v) {
      case 'APPROVE':
        return 'green';
      case 'REJECT':
        return 'red';
      case 'REVIEW':
        return 'amber';
      default:
        return 'gray';
    }
  };

  const tabBtnClass = (active: boolean) =>
    `rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${active ? 'bg-white text-gray-900 shadow-sm ring-1 ring-black/5' : 'text-gray-600 hover:text-gray-900'}`;

  if (loading) {
    return <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600">正在加载…</div>;
  }

  if (!cfg) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        {error ? <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">{error}</div> : <div className="text-sm text-gray-600">无数据</div>}
        <div className="mt-3">
          <button type="button" onClick={() => void load()} className="rounded border px-3 py-2 text-sm">
            重试
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 flex items-center justify-between gap-3">
        <div>
          <div className="text-lg font-semibold">分片审核配置</div>
          <div className="text-sm text-gray-600 mt-1">
            超长帖子/超长文件解析文本会进入分片审核。队列面板可展示分片进度，并按配置间隔刷新。
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => void load()} className="rounded border px-3 py-2 text-sm" disabled={loading || saving}>
            刷新
          </button>
          {!editing ? (
            <button
              type="button"
              onClick={() => {
                setEditing(true);
                setError(null);
                setOk(null);
              }}
              className="rounded border px-3 py-2 text-sm disabled:opacity-60"
              disabled={loading || saving}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  setCfg(committedCfg);
                  setLlmCfg(committedLlmCfg);
                  setEditing(false);
                  setError(null);
                  setOk(null);
                }}
                className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                disabled={loading || saving}
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void save()}
                className="rounded bg-blue-600 text-white px-4 py-2 text-sm disabled:opacity-60"
                disabled={!canSave}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </>
          )}
        </div>
      </div>

      {error ? <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">{error}</div> : null}
      {ok ? <div className="bg-green-50 border border-green-200 text-green-700 rounded p-3 text-sm">{ok}</div> : null}

      <Section title="策略联动（Policy）" desc="展示 token/Top-K/预算字段（来源：审核策略配置）。">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <div className="text-sm text-gray-700 font-medium">内容类型</div>
            <select
              className="rounded border px-3 py-2 w-full"
              value={policyContentType}
              onChange={(e) => setPolicyContentType(e.target.value as ModerationPolicyContentType)}
              disabled={loading || saving}
            >
              <option value="POST">帖子（POST）</option>
              <option value="COMMENT">评论（COMMENT）</option>
              <option value="PROFILE">个人简介（PROFILE）</option>
            </select>
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">策略版本</div>
            <div className="rounded border px-3 py-2 bg-gray-50 text-sm">{policyCfg?.policyVersion || '—'}</div>
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">状态</div>
            <div className="rounded border px-3 py-2 bg-gray-50 text-sm">{policyLoadError ? `加载失败：${policyLoadError}` : policyCfg ? '已加载' : '—'}</div>
          </div>
        </div>

        {policyCfg ? (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
            <div className="rounded border bg-gray-50 p-3">
              <div className="text-xs text-gray-600">目标分片 Token 数</div>
              <div className="text-sm font-semibold text-gray-900">{String((policyCfg.config as any)?.chunking?.chunk_target_tokens ?? '—')}</div>
            </div>
            <div className="rounded border bg-gray-50 p-3">
              <div className="text-xs text-gray-600">分片重叠比例</div>
              <div className="text-sm font-semibold text-gray-900">{String((policyCfg.config as any)?.chunking?.chunk_overlap_ratio ?? '—')}</div>
            </div>
            <div className="rounded border bg-gray-50 p-3">
              <div className="text-xs text-gray-600">最大分片数</div>
              <div className="text-sm font-semibold text-gray-900">{String((policyCfg.config as any)?.chunking?.max_chunks ?? '—')}</div>
            </div>
            <div className="rounded border bg-gray-50 p-3">
              <div className="text-xs text-gray-600">Top-K 分片上限</div>
              <div className="text-sm font-semibold text-gray-900">{String((policyCfg.config as any)?.judge_upgrade?.top_k_total_chunks_cap ?? '—')}</div>
            </div>
          </div>
        ) : null}
      </Section>

      <Section title="开关与工程参数" desc="该页仅配置分片切分与吞吐相关参数；阈值/升级/路由请到“审核策略配置（Policy）”页面配置。">
        <div className="flex items-center gap-4 flex-wrap">
          <Switch checked={cfg.enabled} onChange={(v) => setCfg({ ...cfg, enabled: v })} label="启用分片审核" disabled={!editing} />
          <Switch
            checked={cfg.sendImagesOnlyWhenInEvidence !== false}
            onChange={(v) => setCfg({ ...cfg, sendImagesOnlyWhenInEvidence: v })}
            label="仅当 evidence 命中图片时上传"
            disabled={!editing}
          />
          <Switch
            checked={cfg.includeImagesBlockOnlyForEvidenceMatches !== false}
            onChange={(v) => setCfg({ ...cfg, includeImagesBlockOnlyForEvidenceMatches: v })}
            label="IMAGES 区块仅输出 evidence 命中图片"
            disabled={!editing}
          />
        </div>
        <div className="text-xs text-gray-500">说明：memory 中历史图片仅用于页面展示，不会发送给 LLM。</div>

        <div>
          <div className="text-sm text-gray-700 font-medium mb-1">分片模式</div>
          <div className="flex items-center gap-2 text-sm text-gray-700">
            <span className="font-semibold text-blue-600">语义分片 (SEMANTIC)</span>
          </div>
          <div className="mt-1 text-xs text-gray-500">
            语义分片会优先在段落和句子边界切分，避免截断语义；分片大小将作为软上限（Budget）。
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <div className="text-sm text-gray-700 font-medium">超长触发阈值（chars）</div>
            <NumberInput
              value={cfg.chunkThresholdChars}
              onChange={(v) => setCfg({ ...cfg, chunkThresholdChars: v })}
              min={1000}
              max={5_000_000}
              disabled={!editing}
            />
            <div className="text-xs text-gray-500 mt-1">仅当文本长度超过该阈值时才进入分片审核。</div>
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">分片大小（chars）</div>
            <NumberInput
              value={cfg.chunkSizeChars}
              onChange={(v) => setCfg({ ...cfg, chunkSizeChars: v })}
              min={500}
              max={10_000}
              disabled={!editing}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">分片重叠（chars）</div>
            <NumberInput
              value={cfg.overlapChars}
              onChange={(v) => setCfg({ ...cfg, overlapChars: v })}
              min={0}
              max={2000}
              disabled={!editing}
            />
            <div className="text-xs text-gray-500 mt-1">重叠用于提升召回；证据汇总阶段会做跨分片去重，避免同一违规文本重复展示。</div>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div>
            <div className="text-sm text-gray-700 font-medium">最大分片数</div>
            <NumberInput
              value={cfg.maxChunksTotal}
              onChange={(v) => setCfg({ ...cfg, maxChunksTotal: v })}
              min={1}
              max={2000}
              disabled={!editing}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">单次处理分片数</div>
            <NumberInput
              value={cfg.chunksPerRun}
              onChange={(v) => setCfg({ ...cfg, chunksPerRun: v })}
              min={1}
              max={50}
              disabled={!editing}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">分片并发工人上限</div>
            <NumberInput
              value={cfg.maxConcurrentWorkers}
              onChange={(v) => setCfg({ ...cfg, maxConcurrentWorkers: v })}
              min={1}
              max={64}
              disabled={!editing}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 font-medium">最大重试次数</div>
            <NumberInput value={cfg.maxAttempts} onChange={(v) => setCfg({ ...cfg, maxAttempts: v })} min={1} max={20} disabled={!editing} />
          </div>
        </div>
      </Section>

      <Section
        title="图片分片配置（视觉）"
        desc="图片分批与高分辨率策略现已由 prompt_code 对应的提示词配置统一管理，此处仅展示派生信息。"
      >
        {llmLoadError ? <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">{llmLoadError}</div> : null}

        {llmCfg ? (
          <>
            {(() => {
              const maxPixelsUpper = VISION_TOKEN_LIMIT * VISION_TOKEN_PIXELS;
              const effectiveMaxPixels = DEFAULT_DASHSCOPE_MAX_PIXELS;
              const maxImageTokens = Math.floor(effectiveMaxPixels / VISION_TOKEN_PIXELS) + 2;
              const maxImageTokensUpper = VISION_TOKEN_LIMIT + 2;

              return (
                <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-2 text-sm text-gray-700">
                  <div className="font-medium">派生信息（图片 Token 与像素上限）</div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    <div>Token 网格：{VISION_TOKEN_GRID_SIDE}×{VISION_TOKEN_GRID_SIDE}</div>
                    <div>每 Token 像素数：{VISION_TOKEN_PIXELS}（{VISION_TOKEN_GRID_SIDE}×{VISION_TOKEN_GRID_SIDE}）</div>
                    <div>Token 上限：{VISION_TOKEN_LIMIT}</div>
                    <div>像素上限上界：{maxPixelsUpper}（{VISION_TOKEN_LIMIT}×{VISION_TOKEN_PIXELS}）</div>
                    <div>
                      当前像素上限：{String(effectiveMaxPixels)}（默认）
                    </div>
                    <div>单图理论 Token 上限：{Math.min(maxImageTokens, maxImageTokensUpper)}（+2 规则）</div>
                  </div>
                  <div className="text-xs text-gray-600">
                    DashScope OpenAI 兼容模式下：高分辨率开关为顶层参数；像素上限为每张图片 part 参数，且仅在高分辨率关闭时有效。
                  </div>
                </div>
              );
            })()}
          </>
        ) : (
          <div className="text-sm text-gray-600">LLM 审核配置未加载，无法展示图片分片配置。</div>
        )}
      </Section>

      <Section title="最近分片结果（日志）" desc="用于快速排障与观察调参效果。仅展示分片元数据与结果，不包含分片原始文本。">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="text-sm text-gray-700">数量</div>
            <select
              className="rounded border px-2 py-2 text-sm"
              value={String(logLimit)}
              onChange={(e) => setLogLimit(Number(e.target.value))}
              disabled={logsLoading}
            >
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100</option>
              <option value="200">200</option>
              <option value="500">500</option>
              <option value="1000">1000</option>
            </select>

            <div className="text-sm text-gray-700">队列编号</div>
            <input
              className="rounded border px-2 py-2 text-sm w-[120px]"
              value={qQueueId}
              onChange={(e) => setQQueueId(e.target.value)}
              placeholder="可选"
              disabled={logsLoading}
            />

            <div className="text-sm text-gray-700">文件资源编号</div>
            <input
              className="rounded border px-2 py-2 text-sm w-[130px]"
              value={qFileAssetId}
              onChange={(e) => setQFileAssetId(e.target.value)}
              placeholder="可选"
              disabled={logsLoading}
            />

            <div className="text-sm text-gray-700">状态</div>
            <select className="rounded border px-2 py-2 text-sm" value={qStatus} onChange={(e) => setQStatus(e.target.value)} disabled={logsLoading}>
              <option value="">全部</option>
              <option value="PENDING">{formatEnumZh('PENDING', STATUS_ZH)}</option>
              <option value="RUNNING">{formatEnumZh('RUNNING', STATUS_ZH)}</option>
              <option value="SUCCESS">{formatEnumZh('SUCCESS', STATUS_ZH)}</option>
              <option value="FAILED">{formatEnumZh('FAILED', STATUS_ZH)}</option>
              <option value="CANCELLED">{formatEnumZh('CANCELLED', STATUS_ZH)}</option>
            </select>

            <div className="text-sm text-gray-700">结论</div>
            <select className="rounded border px-2 py-2 text-sm" value={qVerdict} onChange={(e) => setQVerdict(e.target.value)} disabled={logsLoading}>
              <option value="">全部</option>
              <option value="APPROVE">{formatEnumZh('APPROVE', VERDICT_ZH)}</option>
              <option value="REJECT">{formatEnumZh('REJECT', VERDICT_ZH)}</option>
              <option value="REVIEW">{formatEnumZh('REVIEW', VERDICT_ZH)}</option>
            </select>

            <div className="text-sm text-gray-700">来源</div>
            <select
              className="rounded border px-2 py-2 text-sm"
              value={qSourceType}
              onChange={(e) => setQSourceType(e.target.value)}
              disabled={logsLoading}
            >
              <option value="">全部</option>
              <option value="POST_TEXT">{formatEnumZh('POST_TEXT', SOURCE_TYPE_ZH)}</option>
              <option value="FILE_TEXT">{formatEnumZh('FILE_TEXT', SOURCE_TYPE_ZH)}</option>
            </select>

            <div className="text-sm text-gray-700">关键字</div>
            <input
              className="rounded border px-2 py-2 text-sm w-[220px]"
              value={qKeyword}
              onChange={(e) => setQKeyword(e.target.value)}
              placeholder="文件名 / 模型 / 最后错误 / 来源键"
              disabled={logsLoading}
            />

            <label className="inline-flex items-center gap-2 text-sm text-gray-700 select-none">
              <input
                type="checkbox"
                className="h-4 w-4"
                checked={qOnlyResharded}
                onChange={(e) => setQOnlyResharded(e.target.checked)}
                disabled={logsLoading}
              />
              <span>仅看发生重分片</span>
            </label>
          </div>

          <div className="flex items-center gap-2">
            <button type="button" onClick={() => void loadLogs()} className="rounded border px-3 py-2 text-sm" disabled={logsLoading}>
              {logsLoading ? '加载中…' : '刷新'}
            </button>
            <button
              type="button"
              onClick={exportLogs}
              className="rounded border px-3 py-2 text-sm disabled:opacity-60"
              disabled={logsLoading || visibleLogs.length === 0}
            >
              导出 CSV
            </button>
          </div>
        </div>

        {logsError ? <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">{logsError}</div> : null}

        <div className="border rounded overflow-auto">
          <table className="min-w-[1440px] w-full text-sm">
            <thead className="bg-gray-50 text-gray-700">
              <tr>
                <th className="text-left font-medium p-2">时间</th>
                <th className="text-left font-medium p-2">队列编号</th>
                <th className="text-left font-medium p-2">内容</th>
                <th className="text-left font-medium p-2">来源</th>
                <th className="text-left font-medium p-2">分片</th>
                <th className="text-left font-medium p-2">状态</th>
                <th className="text-left font-medium p-2">结论</th>
                <th className="text-left font-medium p-2">尝试次数</th>
                <th className="text-left font-medium p-2">Token（入/出，最终阶段）</th>
                <th className="text-left font-medium p-2">图片 Token</th>
                <th className="text-left font-medium p-2">预算收敛</th>
                <th className="text-left font-medium p-2">最后错误</th>
                <th className="text-left font-medium p-2">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {visibleLogs.map((it) => (
                <tr key={it.id} className="hover:bg-gray-50">
                  <td className="p-2 text-gray-700 whitespace-nowrap">{formatDateTime(it.decidedAt || it.updatedAt)}</td>
                  <td className="p-2">
                    {it.queueId ? (
                      <a className="text-blue-700 hover:underline" href={`/admin/review?active=queue&taskId=${it.queueId}`} title="在审核队列中查看该队列编号">
                        {it.queueId}
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">
                    {(it.contentType || '—') + (it.contentId != null ? `#${it.contentId}` : '')}
                  </td>
                  <td className="p-2 text-gray-700">
                    <div className="whitespace-nowrap">{formatEnumZh(it.sourceType, SOURCE_TYPE_ZH)}</div>
                    <div className="text-xs text-gray-500 truncate max-w-[260px]" title={it.fileName ?? ''}>
                      {it.fileName || it.sourceKey || ''}
                    </div>
                  </td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">
                    {it.chunkIndex != null ? `#${it.chunkIndex}` : '—'}{' '}
                    {it.startOffset != null && it.endOffset != null ? (
                      <span className="text-xs text-gray-500">
                        [{it.startOffset},{it.endOffset})
                      </span>
                    ) : null}
                  </td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">{formatEnumZh(it.status, STATUS_ZH)}</td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">
                    {formatEnumZh(it.verdict, VERDICT_ZH)}
                    {it.confidence != null ? <span className="text-xs text-gray-500"> {it.confidence.toFixed(4)}</span> : null}
                  </td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">{it.attempts ?? '—'}</td>
                  <td className="p-2 text-gray-700 whitespace-nowrap">
                    {it.tokensIn != null || it.tokensOut != null ? `${it.tokensIn ?? 0}/${it.tokensOut ?? 0}` : '—'}
                  </td>
                  <td className="p-2 text-gray-700">
                    {(() => {
                      const st = logImages[it.id];
                      if (!st) return <span className="text-gray-500">—</span>;
                      if (st.loading) return <span className="text-gray-500">计算中…</span>;
                      if (st.error) return <span className="text-gray-500">—</span>;
                      if (!st.images || st.images.length === 0) return <span className="text-gray-500">无图片</span>;

                      const tokens = st.images.map((img) =>
                        estimateVisionImageTokens({
                          model: it.model,
                          width: img.width,
                          height: img.height,
                          vlHighResolutionImages: null,
                          maxPixelsOverride: null,
                        })
                      );
                      const known = tokens.filter((t): t is number => typeof t === 'number' && Number.isFinite(t));
                      const total = known.reduce((a, b) => a + b, 0);
                      const probingCount = st.images.filter((img) => img.sizeStatus === 'loading' || img.sizeStatus === 'idle').length;
                      const failedCount = st.images.filter((img) => img.sizeStatus === 'failed').length;
                      const title = st.images
                        .map((img, idx) => {
                          const t = tokens[idx];
                          const dim = img.width && img.height ? `${img.width}×${img.height}` : '';
                          const status = dim ? '' : img.sizeStatus === 'loading' ? '（计算中）' : img.sizeStatus === 'failed' ? '（失败）' : '';
                          return `${img.label}${dim ? `（${dim}）` : status}: ${t ?? '—'}`;
                        })
                        .join('\n');

                      const summary = (() => {
                        const n = st.images.length;
                        if (known.length === n) return `总计 ${total}（${n} 张）`;
                        if (known.length > 0) {
                          const parts = [`已知 ${known.length}/${n}`];
                          if (probingCount > 0) parts.push(`计算中 ${probingCount}`);
                          if (failedCount > 0) parts.push(`失败 ${failedCount}`);
                          return `总计 ${total}（${parts.join('，')}）`;
                        }
                        if (probingCount > 0) return `计算中…（已知 0/${n}）`;
                        if (failedCount > 0) return `—（已知 0/${n}，失败 ${failedCount}）`;
                        return `—（已知 0/${n}）`;
                      })();

                      return (
                        <details className="max-w-[260px]">
                          <summary className="cursor-pointer select-none whitespace-nowrap" title={title}>
                            {summary}
                          </summary>
                          <div className="mt-1 space-y-0.5 text-xs text-gray-600">
                            {st.images.map((img, idx) => {
                              const t = tokens[idx];
                              const dim = img.width && img.height ? `${img.width}×${img.height}` : '';
                              const status = dim ? '' : img.sizeStatus === 'loading' ? '（计算中）' : img.sizeStatus === 'failed' ? '（失败）' : '';
                              const line = `#${img.index ?? idx + 1} Token:${t ?? '—'}${dim ? `（${dim}）` : status} ${img.label}`;
                              return (
                                <div key={String(img.index ?? img.url ?? idx)} className="truncate" title={line}>
                                  {line}
                                </div>
                              );
                            })}
                          </div>
                        </details>
                      );
                    })()}
                  </td>
                  <td className="p-2 text-gray-700">
                    {(() => {
                      const b = parseBudgetConvergenceLog(it.budgetConvergenceLog);
                      if (!b) return <span className="text-gray-500">—</span>;
                      const title = [
                        `文本预算: ${b.baseTextTokenBudget} -> ${b.effectiveTextTokenBudget}`,
                        `ChunkSize: ${b.baseChunkSizeChars} -> ${b.effectiveChunkSizeChars}`,
                        `总预算: ${b.totalBudgetTokens}`,
                        `图片预算/估算: ${b.imageTokenBudget}/${b.estimatedImageTokens}`,
                        `是否重分片: ${b.triggeredResharding ? '是' : '否'}`,
                        ...b.rounds.map((r) => `R${r.round}: token=${r.textTokenBudget}, chunk=${r.chunkSizeChars}, total=${r.estimatedTotalRequestTokens}`),
                      ].join('\n');
                      return (
                        <details className="max-w-[320px]">
                          <summary className="cursor-pointer select-none whitespace-nowrap" title={title}>
                            {`预算 ${b.baseTextTokenBudget}→${b.effectiveTextTokenBudget}，Chunk ${b.baseChunkSizeChars}→${b.effectiveChunkSizeChars}`}
                          </summary>
                          <div className="mt-1 space-y-0.5 text-xs text-gray-600">
                            <div>{`总预算 ${b.totalBudgetTokens}，图片预算/估算 ${b.imageTokenBudget}/${b.estimatedImageTokens}`}</div>
                            <div>{`重分片触发：${b.triggeredResharding ? '是' : '否'}`}</div>
                            {b.rounds.map((r) => (
                              <div key={`r-${r.round}`}>{`R${r.round} token ${r.textTokenBudget}，chunk ${r.chunkSizeChars}`}</div>
                            ))}
                          </div>
                        </details>
                      );
                    })()}
                  </td>
                  <td className="p-2 text-gray-700">
                    <div className="truncate max-w-[320px]" title={it.lastError ?? ''}>
                      {it.lastError || '—'}
                    </div>
                  </td>
                  <td className="p-2 whitespace-nowrap">
                    <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => void openDetail(it.id)} disabled={logsLoading}>
                      详情
                    </button>
                  </td>
                </tr>
              ))}
              {!logsLoading && visibleLogs.length === 0 ? (
                <tr>
                  <td className="p-3 text-gray-500" colSpan={13}>
                    {qOnlyResharded ? '暂无发生重分片的日志' : '暂无数据'}
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Section>

      {detailOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 sm:p-6 animate-in fade-in duration-200"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) closeDetail();
          }}
          role="dialog"
          aria-modal="true"
          aria-labelledby="chunk-detail-title"
        >
          <div className="w-full max-w-6xl max-h-[calc(100vh-3rem)] flex flex-col overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-black/10 animate-in zoom-in-95 duration-200">
            <div className="sticky top-0 z-10 border-b bg-white/90 backdrop-blur">
              <div className="flex items-start justify-between gap-3 px-5 pt-4 pb-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <div id="chunk-detail-title" className="truncate text-base font-semibold text-gray-900">
                      分片详情 {detailId != null ? `#${detailId}` : ''}
                    </div>
                    {detail?.chunk?.status ? (
                      <span className={pillClass(statusTone(detail.chunk.status))}>{formatEnumZh(detail.chunk.status, STATUS_ZH)}</span>
                    ) : null}
                    {detail?.chunk?.verdict ? (
                      <span className={pillClass(verdictTone(detail.chunk.verdict))}>
                        {formatEnumZh(detail.chunk.verdict, VERDICT_ZH)}
                        {detail.chunk.confidence != null ? <span className="ml-1 text-[11px] text-gray-500">{detail.chunk.confidence.toFixed(4)}</span> : null}
                      </span>
                    ) : null}
                    {detail?.chunk?.model ? <span className={pillClass('gray')}>{detail.chunk.model}</span> : null}
                    {detail?.chunk?.tokensIn != null || detail?.chunk?.tokensOut != null ? (
                      <span className={pillClass('gray')}>Token（最终阶段）{detail.chunk?.tokensIn ?? 0}/{detail.chunk?.tokensOut ?? 0}</span>
                    ) : null}
                  </div>
                  <div className="mt-1 text-xs text-gray-500">Esc / 点击遮罩 关闭</div>
                </div>
                <button
                  type="button"
                  className="inline-flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-700 shadow-sm hover:bg-gray-50 active:bg-gray-100"
                  onClick={closeDetail}
                >
                  <svg viewBox="0 0 20 20" fill="currentColor" className="h-4 w-4 text-gray-500" aria-hidden="true">
                    <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                  </svg>
                  关闭
                </button>
              </div>
              <div className="px-5 pb-4">
                <div className="inline-flex rounded-lg bg-gray-100 p-1">
                  <button type="button" className={tabBtnClass(detailTab === 'overview')} onClick={() => setDetailTab('overview')}>
                    概览
                  </button>
                  <button type="button" className={tabBtnClass(detailTab === 'content')} onClick={() => setDetailTab('content')}>
                    内容
                  </button>
                  <button type="button" className={tabBtnClass(detailTab === 'debug')} onClick={() => setDetailTab('debug')}>
                    错误 / JSON
                  </button>
                </div>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {detailLoading ? (
                <div className="rounded-lg border border-gray-200 bg-white p-4">
                  <div className="h-4 w-40 rounded bg-gray-200 animate-pulse" />
                  <div className="mt-3 space-y-2">
                    <div className="h-3 w-full rounded bg-gray-100 animate-pulse" />
                    <div className="h-3 w-5/6 rounded bg-gray-100 animate-pulse" />
                    <div className="h-3 w-2/3 rounded bg-gray-100 animate-pulse" />
                  </div>
                </div>
              ) : null}

              {detailError ? <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{detailError}</div> : null}

              {detail && !detailLoading ? (
                <>
                  {detailTab === 'overview' ? (
                    <div className="space-y-4">
                      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                          <div className="px-4 py-3 border-b bg-gray-50">
                            <div className="text-sm font-medium text-gray-900">分片</div>
                          </div>
                          <div className="p-4">
                            <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-2 text-xs text-gray-700">
                              <dt className="text-gray-500">编号</dt>
                              <dd className="font-mono break-all">{detail.chunk?.id ?? '—'}</dd>
                              <dt className="text-gray-500">分片集合编号</dt>
                              <dd className="font-mono break-all">{detail.chunk?.chunkSetId ?? '—'}</dd>
                              <dt className="text-gray-500">队列编号</dt>
                              <dd className="font-mono break-all">{detail.chunk?.queueId ?? '—'}</dd>
                              <dt className="text-gray-500">案件类型</dt>
                              <dd className="font-mono break-all">{detail.chunk?.caseType ?? '—'}</dd>
                              <dt className="text-gray-500">内容</dt>
                              <dd className="font-mono break-all">
                                {(detail.chunk?.contentType ?? '—') + (detail.chunk?.contentId != null ? `#${detail.chunk?.contentId}` : '')}
                              </dd>
                              <dt className="text-gray-500">来源类型</dt>
                              <dd className="font-mono break-all">{formatEnumZh(detail.chunk?.sourceType, SOURCE_TYPE_ZH)}</dd>
                              <dt className="text-gray-500">来源键</dt>
                              <dd className="font-mono break-all">{detail.chunk?.sourceKey ?? '—'}</dd>
                              <dt className="text-gray-500">文件资源编号</dt>
                              <dd className="font-mono break-all">{detail.chunk?.fileAssetId ?? '—'}</dd>
                              <dt className="text-gray-500">文件名</dt>
                              <dd className="font-mono break-all">{detail.chunk?.fileName ?? '—'}</dd>
                              <dt className="text-gray-500">分片序号</dt>
                              <dd className="font-mono break-all">{detail.chunk?.chunkIndex ?? '—'}</dd>
                              <dt className="text-gray-500">偏移范围</dt>
                              <dd className="font-mono break-all">
                                {detail.chunk?.startOffset ?? '—'} → {detail.chunk?.endOffset ?? '—'}
                              </dd>
                              <dt className="text-gray-500">尝试次数</dt>
                              <dd className="font-mono break-all">{detail.chunk?.attempts ?? '—'}</dd>
                              <dt className="text-gray-500">Token 口径</dt>
                              <dd className="break-all">最终阶段 usage（非全阶段总和）</dd>
                              <dt className="text-gray-500">Token 诊断</dt>
                              <dd className="break-all">
                                {(() => {
                                  const labels = detail.chunk?.labels && typeof detail.chunk.labels === 'object'
                                    ? (detail.chunk.labels as Record<string, unknown>)
                                    : null;
                                  const diag = parseTokenDiagnostics(labels?.tokenDiagnostics);
                                  if (!diag) return '—';
                                  const kinds = diag.imageUrlKinds;
                                  const summary = [
                                    diag.promptChars != null ? `promptChars=${diag.promptChars}` : null,
                                    diag.imagesSent != null ? `images=${diag.imagesSent}` : null,
                                    diag.estimatedImageBatchesByCount != null ? `estimatedBatches=${diag.estimatedImageBatchesByCount}` : null,
                                    kinds
                                      ? `urlKinds(local/data/http/other)=(${kinds.localUpload ?? 0}/${kinds.dataUrl ?? 0}/${kinds.remoteUrl ?? 0}/${kinds.other ?? 0})`
                                      : null,
                                  ].filter((x): x is string => Boolean(x));
                                  return (
                                    <div className="space-y-1">
                                      <div className="font-mono text-[11px]">{summary.length > 0 ? summary.join('，') : '—'}</div>
                                      {diag.hypotheses && diag.hypotheses.length > 0 ? (
                                        <div className="text-[11px] text-amber-700">
                                          {diag.hypotheses.join('；')}
                                        </div>
                                      ) : null}
                                    </div>
                                  );
                                })()}
                              </dd>
                              <dt className="text-gray-500">裁决时间</dt>
                              <dd className="font-mono break-all">{formatDateTime(detail.chunk?.decidedAt)}</dd>
                              <dt className="text-gray-500">创建时间</dt>
                              <dd className="font-mono break-all">{formatDateTime(detail.chunk?.createdAt)}</dd>
                              <dt className="text-gray-500">更新时间</dt>
                              <dd className="font-mono break-all">{formatDateTime(detail.chunk?.updatedAt)}</dd>
                            </dl>
                          </div>
                        </div>

                        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                          <div className="px-4 py-3 border-b bg-gray-50">
                            <div className="text-sm font-medium text-gray-900">分片集合</div>
                          </div>
                          <div className="p-4">
                            <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-2 text-xs text-gray-700">
                              <dt className="text-gray-500">编号</dt>
                              <dd className="font-mono break-all">{detail.chunkSet?.id ?? '—'}</dd>
                              <dt className="text-gray-500">队列编号</dt>
                              <dd className="font-mono break-all">{detail.chunkSet?.queueId ?? '—'}</dd>
                              <dt className="text-gray-500">案件类型</dt>
                              <dd className="font-mono break-all">{detail.chunkSet?.caseType ?? '—'}</dd>
                              <dt className="text-gray-500">内容</dt>
                              <dd className="font-mono break-all">
                                {(detail.chunkSet?.contentType ?? '—') + (detail.chunkSet?.contentId != null ? `#${detail.chunkSet?.contentId}` : '')}
                              </dd>
                              <dt className="text-gray-500">状态</dt>
                              <dd className="font-mono break-all">{formatEnumZh(detail.chunkSet?.status, STATUS_ZH)}</dd>
                              <dt className="text-gray-500">分片参数</dt>
                              <dd className="font-mono break-all">
                                阈值={detail.chunkSet?.chunkThresholdChars ?? '—'} 大小={detail.chunkSet?.chunkSizeChars ?? '—'} 重叠={detail.chunkSet?.overlapChars ?? '—'}
                              </dd>
                              <dt className="text-gray-500">统计</dt>
                              <dd className="font-mono break-all">
                                总数={detail.chunkSet?.totalChunks ?? '—'} 完成={detail.chunkSet?.completedChunks ?? '—'} 失败={detail.chunkSet?.failedChunks ?? '—'}
                              </dd>
                              <dt className="text-gray-500">创建时间</dt>
                              <dd className="font-mono break-all">{formatDateTime(detail.chunkSet?.createdAt)}</dd>
                              <dt className="text-gray-500">更新时间</dt>
                              <dd className="font-mono break-all">{formatDateTime(detail.chunkSet?.updatedAt)}</dd>
                            </dl>
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : null}

                  {detailTab === 'content' ? (
                    <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                      <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between gap-3">
                        <div className="text-sm font-medium text-gray-900">分片内容（预览）</div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100 disabled:opacity-60"
                            onClick={() => void loadContent()}
                            disabled={contentLoading || detailId == null}
                          >
                            {contentLoading ? '加载中…' : content ? '刷新' : '加载'}
                          </button>
                          <button
                            type="button"
                            className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100 disabled:opacity-60"
                            onClick={() => void copyText(String(content?.text ?? ''))}
                            disabled={!content?.text}
                          >
                            复制文本
                          </button>
                          <button
                            type="button"
                            className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100 disabled:opacity-60"
                            onClick={() => downloadBlob(new Blob([String(content?.text ?? '')], { type: 'text/plain' }), `chunk-${detailId ?? 'content'}.txt`)}
                            disabled={!content?.text}
                          >
                            下载文本
                          </button>
                        </div>
                      </div>
                      <div className="p-4 space-y-3">
                        {contentError ? <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{contentError}</div> : null}
                        {content?.reason ? <div className="text-sm text-gray-700">原因：{content.reason}</div> : null}
                        {contentImages && contentImages.length > 0 ? (
                          <div className="text-xs text-gray-600">
                            {(() => {
                              const tokens = contentImages.map((img) =>
                                estimateVisionImageTokens({
                                  model: detail.chunk?.model ?? null,
                                  width: img.width,
                                  height: img.height,
                                  vlHighResolutionImages: null,
                                  maxPixelsOverride: null,
                                })
                              );
                              const known = tokens.filter((t): t is number => typeof t === 'number' && Number.isFinite(t));
                              const total = known.reduce((a, b) => a + b, 0);
                              const probingCount = contentImages.filter((img) => img.sizeStatus === 'loading' || img.sizeStatus === 'idle').length;
                              const failedCount = contentImages.filter((img) => img.sizeStatus === 'failed').length;
                              const n = contentImages.length;
                              if (known.length === n) return `图片 Token：总计 ${total}（${n} 张）`;
                              if (known.length > 0) {
                                const parts = [`已知 ${known.length}/${n}`];
                                if (probingCount > 0) parts.push(`计算中 ${probingCount}`);
                                if (failedCount > 0) parts.push(`失败 ${failedCount}`);
                                return `图片 Token：总计 ${total}（${parts.join('，')}）`;
                              }
                              if (probingCount > 0) return `图片 Token：计算中…（已知 0/${n}）`;
                              if (failedCount > 0) return `图片 Token：—（已知 0/${n}，失败 ${failedCount}）`;
                              return `图片 Token：—（已知 0/${n}）`;
                            })()}
                          </div>
                        ) : null}
                        {content ? (
                          <pre className="whitespace-pre-wrap text-[11px] leading-5 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 overflow-auto max-h-[360px] font-mono text-gray-800">
                            {content.text || '—'}
                          </pre>
                        ) : null}
                        {contentImages && contentImages.length > 0 ? (
                          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                            {contentImages
                              .filter((it) => !!it?.url)
                              .map((it, idx) => (
                                <a
                                  key={String(it.index ?? it.url ?? idx)}
                                  href={String(it.url)}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="group block rounded-lg border border-gray-200 overflow-hidden bg-white hover:shadow-md transition-shadow"
                                  title={it.label}
                                >
                                  <div className="aspect-square bg-gray-50 flex items-center justify-center overflow-hidden">
                                    <img
                                      src={String(it.url)}
                                      alt={it.label}
                                      className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-[1.02]"
                                      loading="lazy"
                                    />
                                  </div>
                                  <div className="px-2 py-1 text-[11px] text-gray-600">
                                    <div className="truncate">{it.label}</div>
                                    <div className="text-[10px] text-gray-500 truncate">
                                      {(() => {
                                        const t = estimateVisionImageTokens({
                                          model: detail.chunk?.model ?? null,
                                          width: it.width,
                                          height: it.height,
                                          vlHighResolutionImages: null,
                                          maxPixelsOverride: null,
                                        });
                                        const dim = it.width && it.height ? `${it.width}×${it.height}` : '';
                                        const status = dim ? '' : it.sizeStatus === 'loading' ? '（计算中）' : it.sizeStatus === 'failed' ? '（失败）' : '';
                                        return `Token：${t ?? '—'}${dim ? `（${dim}）` : status}`;
                                      })()}
                                    </div>
                                  </div>
                                </a>
                              ))}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  ) : null}

                  {detailTab === 'debug' ? (
                    <div className="space-y-4">
                      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                        <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between gap-3">
                          <div className="text-sm font-medium text-gray-900">最后错误</div>
                          <button
                            type="button"
                            className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                            onClick={() => void copyText(detail.chunk?.lastError || '')}
                            disabled={!detail.chunk?.lastError}
                          >
                            复制
                          </button>
                        </div>
                        <pre className="whitespace-pre-wrap text-[11px] leading-5 rounded-b-xl bg-gray-50 px-4 py-3 overflow-auto max-h-[280px] font-mono text-gray-800">
                          {detail.chunk?.lastError || '—'}
                        </pre>
                      </div>

                      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                          <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between gap-2">
                            <div className="text-sm font-medium text-gray-900">标签</div>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() => void copyText(safeJson(detail.chunk?.labels ?? {}))}
                              >
                                复制
                              </button>
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() =>
                                  downloadBlob(
                                    new Blob([safeJson(detail.chunk?.labels ?? {})], { type: 'application/json' }),
                                    `chunk-${detail.chunk?.id ?? 'labels'}.labels.json`
                                  )
                                }
                              >
                                下载
                              </button>
                            </div>
                          </div>
                          <pre className="whitespace-pre-wrap text-[11px] leading-5 bg-gray-50 px-4 py-3 overflow-auto max-h-[360px] font-mono text-gray-800">
                            {safeJson(detail.chunk?.labels ?? {})}
                          </pre>
                        </div>

                        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                          <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between gap-2">
                            <div className="text-sm font-medium text-gray-900">配置 JSON</div>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() => void copyText(safeJson(detail.chunkSet?.configJson ?? {}))}
                              >
                                复制
                              </button>
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() =>
                                  downloadBlob(
                                    new Blob([safeJson(detail.chunkSet?.configJson ?? {})], { type: 'application/json' }),
                                    `chunkset-${detail.chunkSet?.id ?? 'config'}.config.json`
                                  )
                                }
                              >
                                下载
                              </button>
                            </div>
                          </div>
                          <pre className="whitespace-pre-wrap text-[11px] leading-5 bg-gray-50 px-4 py-3 overflow-auto max-h-[360px] font-mono text-gray-800">
                            {safeJson(detail.chunkSet?.configJson ?? {})}
                          </pre>
                        </div>

                        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                          <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between gap-2">
                            <div className="text-sm font-medium text-gray-900">记忆 JSON</div>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() => void copyText(safeJson(detail.chunkSet?.memoryJson ?? {}))}
                              >
                                复制
                              </button>
                              <button
                                type="button"
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 active:bg-gray-100"
                                onClick={() =>
                                  downloadBlob(
                                    new Blob([safeJson(detail.chunkSet?.memoryJson ?? {})], { type: 'application/json' }),
                                    `chunkset-${detail.chunkSet?.id ?? 'memory'}.memory.json`
                                  )
                                }
                              >
                                下载
                              </button>
                            </div>
                          </div>
                          <pre className="whitespace-pre-wrap text-[11px] leading-5 bg-gray-50 px-4 py-3 overflow-auto max-h-[360px] font-mono text-gray-800">
                            {safeJson(detail.chunkSet?.memoryJson ?? {})}
                          </pre>
                        </div>
                      </div>
                    </div>
                  ) : null}
                </>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default ChunkReviewConfigForm;
