import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import Modal from '../users/roles/Modal';
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
  { value: 'SLIDING', label: 'SLIDING（滑窗截断，尽量吃满预算）' },
  { value: 'IMPORTANCE', label: 'IMPORTANCE（按重要性/信息密度挑选）' },
  { value: 'HYBRID', label: 'HYBRID（命中顺序 + 重要性混合）' },
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
  const [committedConfig, setCommittedConfig] = useState<ContextClipConfigDTO>({ ...DEFAULT_CFG });
  const [editing, setEditing] = useState(false);
  const [policyHelpOpen, setPolicyHelpOpen] = useState(false);

  const hasUnsavedChanges = useMemo(() => JSON.stringify(config) !== JSON.stringify(committedConfig), [config, committedConfig]);
  const selectedPolicy = useMemo(() => clampPolicy(config.policy), [config.policy]);
  const selectedPolicyLabel = useMemo(
    () => POLICIES.find((p) => p.value === selectedPolicy)?.label ?? selectedPolicy,
    [selectedPolicy],
  );

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetContextClipConfig();
      const next = { ...DEFAULT_CFG, ...(cfg ?? {}), policy: clampPolicy((cfg as ContextClipConfigDTO | undefined)?.policy) };
      setConfig(next);
      setCommittedConfig(next);
      setEditing(false);
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
    if (!canWrite || !editing) return;
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const saved = await adminUpdateContextClipConfig(config);
      const next = { ...DEFAULT_CFG, ...(saved ?? {}), policy: clampPolicy((saved as ContextClipConfigDTO | undefined)?.policy) };
      setConfig(next);
      setCommittedConfig(next);
      setEditing(false);
      setMessage('配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  }, [canWrite, config, editing]);

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
        <div className="flex flex-wrap items-center justify-end gap-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-700">上下文注入：</span>
            <select
              value={Boolean(config.enabled) ? 'true' : 'false'}
              onChange={(e) => setConfig((v) => ({ ...v, enabled: e.target.value === 'true' }))}
              disabled={!canWrite || !editing || loading}
              className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                Boolean(config.enabled) ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
              } disabled:opacity-60 disabled:bg-gray-100`}
              title={!editing ? '只读（点击右侧「编辑」后可修改）' : '修改开关（需保存生效）'}
            >
              <option value="true" className="text-green-600">
                开启
              </option>
              <option value="false" className="text-red-600">
                关闭
              </option>
            </select>
          </div>
          <button className={btnSecondaryClass} onClick={loadConfig} disabled={loading}>
            刷新
          </button>
          {!editing ? (
            <button
              className={btnSecondaryClass}
              onClick={() => {
                setEditing(true);
                setError(null);
                setMessage(null);
              }}
              disabled={loading || !canWrite}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                className={btnSecondaryClass}
                onClick={() => {
                  setConfig(committedConfig);
                  setEditing(false);
                  setError(null);
                  setMessage(null);
                }}
                disabled={loading}
              >
                取消
              </button>
              <button className={btnPrimaryClass} onClick={onSave} disabled={loading || !canWrite || !hasUnsavedChanges}>
                保存
              </button>
            </>
          )}
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
          <div>
            <div className="flex items-center justify-between gap-3 mb-1">
              <div className="text-xs text-gray-500">策略 Policy</div>
              <button
                type="button"
                className="text-xs text-blue-600 hover:text-blue-700 underline underline-offset-2"
                onClick={() => setPolicyHelpOpen(true)}
              >
                帮助
              </button>
            </div>
            <select
              className={inputClass}
              value={config.policy ?? 'TOPK'}
              onChange={(e) => setConfig((v) => ({ ...v, policy: e.target.value }))}
              disabled={!canWrite || !editing}
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
                disabled={!canWrite || !editing}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">预留回答 tokens</div>
              <input
                className={inputClass}
                value={config.reserveAnswerTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, reserveAnswerTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">单条最大 tokens</div>
              <input
                className={inputClass}
                value={config.perItemMaxTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, perItemMaxTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大条数</div>
              <input
                className={inputClass}
                value={config.maxItems ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxItems: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大 Prompt 字符数</div>
              <input
                className={inputClass}
                value={config.maxPromptChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxPromptChars: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
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
                disabled={!canWrite || !editing}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">同一 post 最多入选条数</div>
              <input
                className={inputClass}
                value={config.maxSamePostItems ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxSamePostItems: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.requireTitle)}
              onChange={(e) => setConfig((v) => ({ ...v, requireTitle: e.target.checked }))}
              disabled={!canWrite || !editing}
            />
            必须有标题（无标题命中将被剔除）
          </label>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByPostId)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByPostId: e.target.checked }))}
                disabled={!canWrite || !editing}
              />
              去重：postId
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByTitle)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByTitle: e.target.checked }))}
                disabled={!canWrite || !editing}
              />
              去重：标题
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={Boolean(config.dedupByContentHash)}
                onChange={(e) => setConfig((v) => ({ ...v, dedupByContentHash: e.target.checked }))}
                disabled={!canWrite || !editing}
              />
              去重：内容hash
            </label>
          </div>
        </div>
      </div>

      <Modal isOpen={policyHelpOpen} onClose={() => setPolicyHelpOpen(false)} title="策略说明">
        <div className="space-y-4 text-sm text-gray-800">
          <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2">
            <div className="font-medium">当前选择：{selectedPolicyLabel}</div>
            <div className="text-xs text-gray-600 mt-1">
              策略决定两件事：① 这次最多能塞多少“上下文”（预算怎么计算）② 在预算内怎么把命中条目塞进去（挑选与裁剪怎么做）。页面下方的“去重/过滤”开关仍然会生效。
            </div>
          </div>

          <div className="space-y-2 rounded border border-gray-200 p-3">
            <div className="font-medium">先搞懂 3 个词</div>
            <ul className="list-disc pl-5 space-y-1 text-gray-700">
              <li>tokens：可以粗略理解成“字符/单词的计量单位”。prompt 越长，tokens 越多。</li>
              <li>上下文预算：这次允许放进 prompt 的“检索结果内容”最多占多少 tokens。</li>
              <li>reserveAnswerTokens：给模型回答预留的空间；预留越多，回答越不容易被挤没。</li>
            </ul>
          </div>

          <div className="space-y-3">
            <div className="font-medium">① 预算怎么计算（能放多少内容）</div>
            <div className="rounded border border-gray-200 p-3 space-y-2">
              <div className="font-medium">FIXED（固定塞满）</div>
              <div className="text-gray-700">
                预算 = maxContextTokens（不额外给回答预留空间）。优点是更容易把检索内容塞满；缺点是 prompt 可能过长，回答空间可能不够，出现截断/回答变短。
              </div>
            </div>
            <div className="rounded border border-gray-200 p-3 space-y-2">
              <div className="font-medium">ADAPTIVE（随问题变动）</div>
              <div className="text-gray-700">
                预算 = maxContextTokens - reserveAnswerTokens - 约等于（query tokens × 2）。问题越长，给检索内容的空间越少，优先保证“问题 + 回答”不会把上下文挤爆。
              </div>
            </div>
            <div className="rounded border border-gray-200 p-3 space-y-2">
              <div className="font-medium">TOPK / DEDUP / SLIDING / IMPORTANCE / HYBRID（统一预留回答）</div>
              <div className="text-gray-700">
                预算 = maxContextTokens - reserveAnswerTokens。它们的差异不在“能放多少”，而在“怎么挑选/怎么裁剪”。
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <div className="font-medium">② 怎么挑选与裁剪（放哪些内容进来）</div>
            <div className="text-gray-700">流程可以理解成两步：</div>
            <ul className="list-disc pl-5 space-y-1 text-gray-700">
              <li>先做硬性筛选：分数太低（minScore）、必须有标题、去重、同帖最多入选条数、单条过长先截断等。</li>
              <li>再按策略把剩下的条目往预算里塞：不同策略的“塞法”不一样。</li>
            </ul>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
              <div className="rounded border border-gray-200 p-3 space-y-2">
                <div className="font-medium">TOPK（按顺序装箱）</div>
                <div className="text-gray-700">
                  从高分到低分依次尝试加入；某条放不下就先跳过它，继续尝试后面更短的条目。特点是“尽量不裁剪内容”，但可能留下一点没用满的空位。
                </div>
              </div>
              <div className="rounded border border-gray-200 p-3 space-y-2">
                <div className="font-medium">SLIDING（最后一条截到刚好放下）</div>
                <div className="text-gray-700">
                  前面类似 TOPK；遇到放不下时，会把当前这条截断到“剩余预算”并作为最后一条塞进去。特点是更容易用满预算，但最后一条可能只剩一段不完整内容。
                </div>
              </div>
              <div className="rounded border border-gray-200 p-3 space-y-2">
                <div className="font-medium">DEDUP（更分散）</div>
                <div className="text-gray-700">
                  更倾向从不同帖子/不同标题里各取一些，避免同一来源占满上下文。具体“怎么判重/怎么限制同帖条数”仍然由页面下方去重开关和同帖上限控制。
                </div>
              </div>
              <div className="rounded border border-gray-200 p-3 space-y-2">
                <div className="font-medium">IMPORTANCE（挑信息密度更高的组合）</div>
                <div className="text-gray-700">
                  候选集更大时，会优先挑“更重要/更有信息量”的片段组合，尽量用更少 tokens 放进更有用的内容。适合命中很多、但你更在乎“质量”而不是“数量”的场景。
                </div>
              </div>
              <div className="rounded border border-gray-200 p-3 space-y-2 md:col-span-2">
                <div className="font-medium">HYBRID（先保底头部，再用重要性补齐）</div>
                <div className="text-gray-700">
                  先保证少量最靠前（通常也是分数最高）的命中进来，再用 IMPORTANCE 从更多候选里补齐剩余预算；补齐阶段同样可能发生类似 SLIDING 的截断。适合既想“别漏掉高分命中”，又想“整体更有用”的平衡方案。
                </div>
              </div>
            </div>

            <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-gray-700">
              <div className="font-medium text-gray-800">新手怎么选（快速指南）</div>
              <ul className="list-disc pl-5 space-y-1">
                <li>不想纠结：用 HYBRID（相对均衡）。</li>
                <li>命中很多、内容很杂：用 IMPORTANCE 或 HYBRID。</li>
                <li>想尽量塞满上下文：用 SLIDING（但最后一条可能被截断）。</li>
                <li>想尽量不截断内容：用 TOPK。</li>
                <li>想更去重更分散：优先调去重开关 + 同一 post 最多入选条数 + 最大条数，再考虑 DEDUP。</li>
              </ul>
            </div>
          </div>
        </div>
      </Modal>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="font-medium">Prompt 与展示格式</div>
        <div>
          <div className="text-xs text-gray-500 mb-1">段落标题（sectionTitle）</div>
          <textarea
            className={inputClass}
            value={config.sectionTitle ?? ''}
            onChange={(e) => setConfig((v) => ({ ...v, sectionTitle: e.target.value }))}
            disabled={!canWrite || !editing}
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
              disabled={!canWrite || !editing}
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
                disabled={!canWrite || !editing}
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showPostId)}
                  onChange={(e) => setConfig((v) => ({ ...v, showPostId: e.target.checked }))}
                  disabled={!canWrite || !editing}
                />
                展示 postId
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showChunkIndex)}
                  onChange={(e) => setConfig((v) => ({ ...v, showChunkIndex: e.target.checked }))}
                  disabled={!canWrite || !editing}
                />
                展示 chunkIndex
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showScore)}
                  onChange={(e) => setConfig((v) => ({ ...v, showScore: e.target.checked }))}
                  disabled={!canWrite || !editing}
                />
                展示 score
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(config.showTitle)}
                  onChange={(e) => setConfig((v) => ({ ...v, showTitle: e.target.checked }))}
                  disabled={!canWrite || !editing}
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
            disabled={!canWrite || !editing}
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
            disabled={!canWrite || !editing}
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
              disabled={!canWrite || !editing}
            />
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">保留天数（占位）</div>
            <input
              className={inputClass}
              value={config.logMaxDays ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, logMaxDays: safeNumber(e.target.value) }))}
              disabled={!canWrite || !editing}
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
