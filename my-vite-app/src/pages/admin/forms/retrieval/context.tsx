import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import {
  adminGetContextClipConfig,
  adminGetContextWindow,
  adminListContextWindows,
  adminTestContextClip,
  adminUpdateContextClipConfig,
  type ContextClipConfigDTO,
  type ContextClipTestResponse,
  type ContextWindowDetailDTO,
  type ContextWindowLogDTO,
  type ContextWindowPolicy,
} from '../../../../services/retrievalContextService';

const inputClass =
  'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200';
const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';

function fmtDateTime(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return d.toLocaleString();
}

function safeNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'string' && v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function clampPolicy(v: unknown): ContextWindowPolicy {
  const s = String(v ?? '').trim();
  if (!s) return 'TOPK';
  return s;
}

const DEFAULT_CFG: ContextClipConfigDTO = {
  enabled: true,
  policy: 'TOPK',

  maxItems: 6,
  maxContextTokens: 12000,
  reserveAnswerTokens: 2000,
  perItemMaxTokens: 2000,
  maxPromptChars: 200000,

  minScore: null,
  maxSamePostItems: 2,
  requireTitle: false,

  dedupByPostId: true,
  dedupByTitle: false,
  dedupByContentHash: true,

  sectionTitle: '以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：',
  itemHeaderTemplate: '[{i}] post_id={postId} chunk={chunkIndex} score={score}\n标题：{title}\n',
  separator: '\n\n',

  showPostId: true,
  showChunkIndex: true,
  showScore: true,
  showTitle: true,

  extraInstruction: '回答时尽量在相关句末添加 [编号] 引用；如资料不足请明确说明。',

  logEnabled: true,
  logSampleRate: 1,
  logMaxDays: 30,
};

const POLICIES: { value: ContextWindowPolicy; label: string }[] = [
  { value: 'TOPK', label: 'TOPK（按命中顺序截断）' },
  { value: 'DEDUP', label: 'DEDUP（去重优先）' },
  { value: 'FIXED', label: 'FIXED（固定预算）' },
  { value: 'ADAPTIVE', label: 'ADAPTIVE（随 query 自适应预算）' },
  { value: 'SLIDING', label: 'SLIDING（滑窗/占位）' },
  { value: 'IMPORTANCE', label: 'IMPORTANCE（重要性/占位）' },
  { value: 'HYBRID', label: 'HYBRID（混合/占位）' },
];

const ContextClipForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_retrieval_context', 'access');
  const canWrite = hasPerm('admin_retrieval_context', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<ContextClipConfigDTO>({ ...DEFAULT_CFG });
  const [configLoaded, setConfigLoaded] = useState(false);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetContextClipConfig();
      setConfig({ ...DEFAULT_CFG, ...(cfg ?? {}), policy: clampPolicy((cfg as ContextClipConfigDTO | undefined)?.policy) });
      setConfigLoaded(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载配置失败');
      setConfigLoaded(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!canAccess) return;
    loadConfig();
  }, [canAccess, loadConfig]);

  const onSave = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const saved = await adminUpdateContextClipConfig(config);
      setConfig({ ...DEFAULT_CFG, ...(saved ?? {}), policy: clampPolicy((saved as ContextClipConfigDTO | undefined)?.policy) });
      setMessage('配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  }, [config]);

  const [testQuery, setTestQuery] = useState('');
  const [testBoardId, setTestBoardId] = useState<number | ''>('');
  const [useSavedConfig, setUseSavedConfig] = useState(false);
  const [testResult, setTestResult] = useState<ContextClipTestResponse | null>(null);

  const onTest = useCallback(async () => {
    setError(null);
    setMessage(null);
    setTestResult(null);
    setLoading(true);
    try {
      const res = await adminTestContextClip({
        queryText: testQuery,
        boardId: testBoardId === '' ? null : testBoardId,
        useSavedConfig,
        config: useSavedConfig ? null : config,
      });
      setTestResult(res);
      setMessage('测试完成');
    } catch (e) {
      setError(e instanceof Error ? e.message : '测试失败');
    } finally {
      setLoading(false);
    }
  }, [config, testBoardId, testQuery, useSavedConfig]);

  const [logsPage, setLogsPage] = useState(0);
  const [logs, setLogs] = useState<ContextWindowLogDTO[]>([]);
  const [logsTotal, setLogsTotal] = useState(0);
  const [selectedWindowId, setSelectedWindowId] = useState<number | null>(null);
  const [selectedWindow, setSelectedWindow] = useState<ContextWindowDetailDTO | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    try {
      const page = await adminListContextWindows({ page: logsPage, size: 20 });
      setLogs(page.content ?? []);
      setLogsTotal(page.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载日志失败');
    } finally {
      setLoading(false);
    }
  }, [logsPage]);

  useEffect(() => {
    if (!canAccess) return;
    loadLogs();
  }, [canAccess, loadLogs]);

  const onSelectWindow = useCallback(async (id: number) => {
    setSelectedWindowId(id);
    setSelectedWindow(null);
    setLoading(true);
    try {
      const detail = await adminGetContextWindow(id);
      setSelectedWindow(detail);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载详情失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const logsTotalPages = useMemo(() => Math.max(1, Math.ceil((logsTotal || 0) / 20)), [logsTotal]);

  const copyText = useCallback(async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setMessage('已复制到剪贴板');
    } catch {
      setError('复制失败（浏览器权限限制）');
    }
  }, []);

  if (accessLoading || !configLoaded) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="text-gray-500">加载中…</div>
      </div>
    );
  }

  if (!canAccess) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="text-red-600 font-medium">无权限访问：动态上下文裁剪</div>
        <div className="text-gray-600 text-sm mt-1">需要 admin_retrieval_context:access</div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">动态上下文裁剪</h3>
          <div className="text-xs text-gray-500">控制 Chat RAG 的上下文预算、格式、去重、日志与测试预览</div>
        </div>
        <div className="flex items-center gap-2">
          <button className={btnSecondaryClass} onClick={loadConfig} disabled={loading}>
            刷新
          </button>
          <button className={btnPrimaryClass} onClick={onSave} disabled={loading || !canWrite}>
            保存配置
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}
      {message && (
        <div className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{message}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">开关与策略</div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.enabled)}
              onChange={(e) => setConfig((v) => ({ ...v, enabled: e.target.checked }))}
              disabled={!canWrite}
            />
            启用上下文注入（Chat RAG system prompt）
          </label>
          <div>
            <div className="text-xs text-gray-500 mb-1">策略 Policy</div>
            <select
              className={inputClass}
              value={config.policy ?? 'TOPK'}
              onChange={(e) => setConfig((v) => ({ ...v, policy: e.target.value }))}
              disabled={!canWrite}
            >
              {POLICIES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">最大全局预算（tokens）</div>
              <input
                className={inputClass}
                value={config.maxContextTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxContextTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">预留回答 tokens</div>
              <input
                className={inputClass}
                value={config.reserveAnswerTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, reserveAnswerTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">单条最大 tokens</div>
              <input
                className={inputClass}
                value={config.perItemMaxTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, perItemMaxTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大条数</div>
              <input
                className={inputClass}
                value={config.maxItems ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxItems: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大 Prompt 字符数</div>
              <input
                className={inputClass}
                value={config.maxPromptChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxPromptChars: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">过滤与去重</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">minScore（小于则剔除）</div>
              <input
                className={inputClass}
                value={config.minScore ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, minScore: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">同一 post 最多入选条数</div>
              <input
                className={inputClass}
                value={config.maxSamePostItems ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxSamePostItems: safeNumber(e.target.value) }))}
                disabled={!canWrite}
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.requireTitle)}
              onChange={(e) => setConfig((v) => ({ ...v, requireTitle: e.target.checked }))}
              disabled={!canWrite}
            />
            必须有标题（无标题命中将被剔除）
          </label>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByPostId)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByPostId: e.target.checked }))}
                disabled={!canWrite}
              />
              去重：postId
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByTitle)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByTitle: e.target.checked }))}
                disabled={!canWrite}
              />
              去重：标题
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByContentHash)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByContentHash: e.target.checked }))}
                disabled={!canWrite}
              />
              去重：内容hash
            </label>
          </div>
        </div>
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="font-medium">Prompt 与展示格式</div>
        <div>
          <div className="text-xs text-gray-500 mb-1">段落标题（sectionTitle）</div>
          <textarea
            className={inputClass}
            value={config.sectionTitle ?? ''}
            onChange={(e) => setConfig((v) => ({ ...v, sectionTitle: e.target.value }))}
            disabled={!canWrite}
            rows={2}
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <div className="text-xs text-gray-500 mb-1">条目头模板（可用占位符：{`{i} {postId} {chunkIndex} {score} {title}` }）</div>
            <textarea
              className={inputClass}
              value={config.itemHeaderTemplate ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, itemHeaderTemplate: e.target.value }))}
              disabled={!canWrite}
              rows={4}
            />
          </div>
          <div className="space-y-3">
            <div>
              <div className="text-xs text-gray-500 mb-1">分隔符（separator）</div>
              <input
                className={inputClass}
                value={config.separator ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, separator: e.target.value }))}
                disabled={!canWrite}
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showPostId)}
                  onChange={(e) => setConfig((v) => ({ ...v, showPostId: e.target.checked }))}
                  disabled={!canWrite}
                />
                展示 postId
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showChunkIndex)}
                  onChange={(e) => setConfig((v) => ({ ...v, showChunkIndex: e.target.checked }))}
                  disabled={!canWrite}
                />
                展示 chunkIndex
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showScore)}
                  onChange={(e) => setConfig((v) => ({ ...v, showScore: e.target.checked }))}
                  disabled={!canWrite}
                />
                展示 score
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showTitle)}
                  onChange={(e) => setConfig((v) => ({ ...v, showTitle: e.target.checked }))}
                  disabled={!canWrite}
                />
                展示标题
              </label>
            </div>
          </div>
        </div>
        <div>
          <div className="text-xs text-gray-500 mb-1">额外指令（extraInstruction，会拼到 system prompt 末尾）</div>
          <textarea
            className={inputClass}
            value={config.extraInstruction ?? ''}
            onChange={(e) => setConfig((v) => ({ ...v, extraInstruction: e.target.value }))}
            disabled={!canWrite}
            rows={2}
          />
        </div>
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="font-medium">日志（context_windows）</div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={Boolean(config.logEnabled)}
            onChange={(e) => setConfig((v) => ({ ...v, logEnabled: e.target.checked }))}
            disabled={!canWrite}
          />
          启用裁剪日志落库
        </label>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <div>
            <div className="text-xs text-gray-500 mb-1">采样率（0~1）</div>
            <input
              className={inputClass}
              value={config.logSampleRate ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, logSampleRate: safeNumber(e.target.value) }))}
              disabled={!canWrite}
            />
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">保留天数（占位）</div>
            <input
              className={inputClass}
              value={config.logMaxDays ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, logMaxDays: safeNumber(e.target.value) }))}
              disabled={!canWrite}
            />
          </div>
          <div className="flex items-end">
            <button className={btnSecondaryClass} onClick={loadLogs} disabled={loading}>
              刷新日志
            </button>
          </div>
        </div>
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-medium">测试预览</div>
            <div className="text-xs text-gray-500">直接跑一遍“检索 → 裁剪 → 拼接 system prompt”，便于调参</div>
          </div>
          <button className={btnPrimaryClass} onClick={onTest} disabled={loading || !canWrite}>
            运行测试
          </button>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <div className="md:col-span-2">
            <div className="text-xs text-gray-500 mb-1">Query</div>
            <input className={inputClass} value={testQuery} onChange={(e) => setTestQuery(e.target.value)} />
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">boardId（可选）</div>
            <input
              className={inputClass}
              value={testBoardId}
              onChange={(e) => setTestBoardId(e.target.value === '' ? '' : Number(e.target.value))}
            />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={useSavedConfig} onChange={(e) => setUseSavedConfig(e.target.checked)} />
          使用已保存配置（忽略当前编辑）
        </label>

        {testResult && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm">
              <div className="rounded border border-gray-200 px-3 py-2">
                <div className="text-xs text-gray-500">预算 tokens</div>
                <div className="font-medium">{testResult.budgetTokens ?? '—'}</div>
              </div>
              <div className="rounded border border-gray-200 px-3 py-2">
                <div className="text-xs text-gray-500">已用 tokens</div>
                <div className="font-medium">{testResult.usedTokens ?? '—'}</div>
              </div>
              <div className="rounded border border-gray-200 px-3 py-2">
                <div className="text-xs text-gray-500">入选条数</div>
                <div className="font-medium">{testResult.itemsSelected ?? '—'}</div>
              </div>
              <div className="rounded border border-gray-200 px-3 py-2">
                <div className="text-xs text-gray-500">剔除条数</div>
                <div className="font-medium">{testResult.itemsDropped ?? '—'}</div>
              </div>
            </div>

            <div className="flex items-center justify-between">
              <div className="font-medium">生成的 system prompt</div>
              <button
                className={btnSecondaryClass}
                onClick={() => copyText(String(testResult.contextPrompt ?? ''))}
                disabled={!testResult.contextPrompt}
              >
                复制
              </button>
            </div>
            <pre className="rounded border border-gray-200 bg-gray-50 p-3 text-xs overflow-auto max-h-80">
              {String(testResult.contextPrompt ?? '')}
            </pre>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="rounded border border-gray-200 p-3">
                <div className="font-medium mb-2">入选</div>
                <div className="space-y-2">
                  {(testResult.selected ?? []).slice(0, 50).map((it, idx) => (
                    <div key={idx} className="rounded border border-gray-100 px-2 py-1 text-xs">
                      <div className="text-gray-700">
                        #{it.rank ?? idx + 1} postId={it.postId ?? '—'} chunk={it.chunkIndex ?? '—'} score={it.score ?? '—'} tokens=
                        {it.tokens ?? '—'}
                      </div>
                      {it.title && <div className="text-gray-500 mt-1">{it.title}</div>}
                    </div>
                  ))}
                </div>
              </div>
              <div className="rounded border border-gray-200 p-3">
                <div className="font-medium mb-2">剔除（原因）</div>
                <div className="space-y-2">
                  {(testResult.dropped ?? []).slice(0, 50).map((it, idx) => (
                    <div key={idx} className="rounded border border-gray-100 px-2 py-1 text-xs">
                      <div className="text-gray-700">
                        #{it.rank ?? idx + 1} postId={it.postId ?? '—'} chunk={it.chunkIndex ?? '—'} reason={it.reason ?? '—'} score=
                        {it.score ?? '—'} tokens={it.tokens ?? '—'}
                      </div>
                      {it.title && <div className="text-gray-500 mt-1">{it.title}</div>}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="flex items-center justify-between">
          <div>
            <div className="font-medium">日志回放</div>
            <div className="text-xs text-gray-500">来自 context_windows（按时间倒序）</div>
          </div>
          <div className="flex items-center gap-2">
            <button
              className={btnSecondaryClass}
              onClick={() => setLogsPage((p) => Math.max(0, p - 1))}
              disabled={loading || logsPage <= 0}
            >
              上一页
            </button>
            <div className="text-sm text-gray-600">
              {logsPage + 1} / {logsTotalPages}
            </div>
            <button
              className={btnSecondaryClass}
              onClick={() => setLogsPage((p) => Math.min(logsTotalPages - 1, p + 1))}
              disabled={loading || logsPage + 1 >= logsTotalPages}
            >
              下一页
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <div className="space-y-2">
            {(logs ?? []).map((row) => (
              <button
                key={row.id}
                className={`w-full text-left rounded border px-3 py-2 text-sm hover:bg-gray-50 ${
                  selectedWindowId === row.id ? 'border-blue-300 bg-blue-50' : 'border-gray-200 bg-white'
                }`}
                onClick={() => onSelectWindow(row.id)}
                disabled={loading}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="font-medium truncate">{row.queryText ? String(row.queryText).slice(0, 60) : '—'}</div>
                  <div className="text-xs text-gray-500">{fmtDateTime(row.createdAt)}</div>
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  id={row.id} eventId={row.eventId ?? '—'} policy={row.policy ?? '—'} items={row.items ?? '—'} tokens={row.totalTokens ?? '—'}
                </div>
              </button>
            ))}
          </div>

          <div className="rounded border border-gray-200 p-3 bg-gray-50">
            {!selectedWindow ? (
              <div className="text-sm text-gray-500">选择一条日志查看详情</div>
            ) : (
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="font-medium">窗口详情</div>
                  <button className={btnSecondaryClass} onClick={() => copyText(JSON.stringify(selectedWindow ?? {}, null, 2))}>
                    复制 JSON
                  </button>
                </div>
                <div className="text-xs text-gray-500">
                  id={selectedWindow.id} eventId={selectedWindow.eventId ?? '—'} policy={selectedWindow.policy ?? '—'} tokens=
                  {selectedWindow.totalTokens ?? '—'} createdAt={fmtDateTime(selectedWindow.createdAt)}
                </div>
                {selectedWindow.queryText && <div className="text-sm text-gray-800">{selectedWindow.queryText}</div>}
                <pre className="rounded border border-gray-200 bg-white p-3 text-xs overflow-auto max-h-96">
                  {JSON.stringify(selectedWindow.chunkIds ?? {}, null, 2)}
                </pre>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ContextClipForm;
