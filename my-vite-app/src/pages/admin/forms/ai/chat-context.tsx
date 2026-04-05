import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import Modal from '../users/roles/Modal';
import { AdminLoadingCard, computeLogsTotalPages, copyTextWithFeedback } from '../shared/adminFormUiShared';
import {
  adminGetChatContextConfig,
  adminGetChatContextLog,
  adminListChatContextLogs,
  adminUpdateChatContextConfig,
  type AdminChatContextEventDetailDTO,
  type AdminChatContextEventLogDTO,
  type ChatContextGovernanceConfigDTO,
} from '../../../../services/chatContextGovernanceService';

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

function toInt(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'string' && v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function toFloat(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'string' && v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

const DEFAULT_CFG: ChatContextGovernanceConfigDTO = {
  enabled: true,

  maxPromptTokens: 24000,
  reserveAnswerTokens: 2000,
  maxPromptChars: 200000,
  perMessageMaxTokens: 6000,
  keepLastMessages: 20,
  allowDropRagContext: true,

  compressionEnabled: true,
  compressionTriggerTokens: 18000,
  compressionKeepLastMessages: 8,
  compressionPerMessageSnippetChars: 300,
  compressionMaxChars: 12000,

  maxFiles: 10,
  perFileMaxChars: 6000,
  totalFilesMaxChars: 24000,

  logEnabled: true,
  logSampleRate: 1,
  logMaxDays: 30,
};

const ChatContextGovernanceForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_ai_chat_context', 'access');
  const canWrite = hasPerm('admin_ai_chat_context', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<ChatContextGovernanceConfigDTO>({ ...DEFAULT_CFG });
  const [committedConfig, setCommittedConfig] = useState<ChatContextGovernanceConfigDTO>({ ...DEFAULT_CFG });
  const [configLoaded, setConfigLoaded] = useState(false);
  const [editing, setEditing] = useState(false);

  const hasUnsavedChanges = useMemo(() => JSON.stringify(config) !== JSON.stringify(committedConfig), [config, committedConfig]);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetChatContextConfig();
      const next = { ...DEFAULT_CFG, ...(cfg ?? {}) };
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
      const saved = await adminUpdateChatContextConfig(config);
      const next = { ...DEFAULT_CFG, ...(saved ?? {}) };
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

  const onReset = useCallback(() => {
    if (!editing) return;
    setConfig(committedConfig);
    setMessage('已恢复为已保存版本');
  }, [committedConfig, editing]);

  const [logsPage, setLogsPage] = useState(0);
  const [logs, setLogs] = useState<AdminChatContextEventLogDTO[]>([]);
  const [logsTotal, setLogsTotal] = useState(0);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [selectedLog, setSelectedLog] = useState<AdminChatContextEventDetailDTO | null>(null);

  const loadLogs = useCallback(async () => {
    if (!canAccess) return;
    setLoading(true);
    try {
      const page = await adminListChatContextLogs({ page: logsPage, size: 20 });
      setLogs(page.content ?? []);
      setLogsTotal(page.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载日志失败');
    } finally {
      setLoading(false);
    }
  }, [canAccess, logsPage]);

  useEffect(() => {
    if (!canAccess) return;
    loadLogs();
  }, [canAccess, loadLogs]);

  const onSelectLog = useCallback(async (id: number) => {
    setSelectedLogId(id);
    setSelectedLog(null);
    setLoading(true);
    try {
      const detail = await adminGetChatContextLog(id);
      setSelectedLog(detail);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载日志详情失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const logsTotalPages = useMemo(() => computeLogsTotalPages(logsTotal), [logsTotal]);

  const copyText = useCallback(async (text: string) => {
    await copyTextWithFeedback(text, setMessage, setError);
  }, []);

  if (accessLoading || !configLoaded) {
    return <AdminLoadingCard />;
  }

  if (!canAccess) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="text-red-600 font-medium">无权限访问：对话上下文治理</div>
        <div className="text-gray-600 text-sm mt-1">需要 admin_ai_chat_context:access</div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">对话上下文治理</h3>
          <div className="text-xs text-gray-500">动态裁剪、历史压缩、文件注入与日志</div>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <button type="button" className={btnSecondaryClass} onClick={loadConfig} disabled={loading}>
            刷新
          </button>
          <button
            type="button"
            className={btnSecondaryClass}
            onClick={() => setEditing(true)}
            disabled={loading || !canWrite || editing}
          >
            编辑
          </button>
          <button
            type="button"
            className={btnSecondaryClass}
            onClick={() => {
              setEditing(false);
              setConfig(committedConfig);
              setMessage('已取消编辑');
            }}
            disabled={loading || !editing}
          >
            取消
          </button>
          <button type="button" className={btnSecondaryClass} onClick={onReset} disabled={loading || !editing || !hasUnsavedChanges}>
            恢复
          </button>
          <button type="button" className={btnPrimaryClass} onClick={onSave} disabled={loading || !canWrite || !editing}>
            保存
          </button>
        </div>
      </div>

      {error && <div className="text-sm text-red-600">{error}</div>}
      {message && <div className="text-sm text-green-700">{message}</div>}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-3">
          <div className="text-sm font-semibold text-gray-800">裁剪（Clip）</div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.enabled)}
              onChange={(e) => setConfig((v) => ({ ...v, enabled: e.target.checked }))}
              disabled={!editing || !canWrite || loading}
            />
            <span>启用上下文治理</span>
          </label>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">提示词最大 Token 数</div>
              <input
                className={inputClass}
                value={config.maxPromptTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxPromptTokens: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">回答预留 Token 数</div>
              <input
                className={inputClass}
                value={config.reserveAnswerTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, reserveAnswerTokens: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">提示词最大字符数</div>
              <input
                className={inputClass}
                value={config.maxPromptChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxPromptChars: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">单条消息最大 Token 数</div>
              <input
                className={inputClass}
                value={config.perMessageMaxTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, perMessageMaxTokens: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">保留最近消息数</div>
              <input
                className={inputClass}
                value={config.keepLastMessages ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, keepLastMessages: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={config.allowDropRagContext !== false}
                  onChange={(e) => setConfig((v) => ({ ...v, allowDropRagContext: e.target.checked }))}
                  disabled={!editing || !canWrite || loading}
                />
                <span>允许丢弃 RAG 上下文</span>
              </label>
            </div>
          </div>
        </div>

        <div className="space-y-3">
          <div className="text-sm font-semibold text-gray-800">压缩（Compress）</div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={config.compressionEnabled !== false}
              onChange={(e) => setConfig((v) => ({ ...v, compressionEnabled: e.target.checked }))}
              disabled={!editing || !canWrite || loading}
            />
            <span>启用历史压缩</span>
          </label>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">触发压缩 Token 阈值</div>
              <input
                className={inputClass}
                value={config.compressionTriggerTokens ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, compressionTriggerTokens: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">压缩后保留最近消息数</div>
              <input
                className={inputClass}
                value={config.compressionKeepLastMessages ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, compressionKeepLastMessages: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">单条消息压缩摘要字符数</div>
              <input
                className={inputClass}
                value={config.compressionPerMessageSnippetChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, compressionPerMessageSnippetChars: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">压缩内容最大字符数</div>
              <input
                className={inputClass}
                value={config.compressionMaxChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, compressionMaxChars: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>

          <div className="text-sm font-semibold text-gray-800 pt-2">文件注入（Files）</div>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <div className="text-xs text-gray-600 mb-1">最大文件数</div>
              <input
                className={inputClass}
                value={config.maxFiles ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, maxFiles: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">单文件最大字符数</div>
              <input
                className={inputClass}
                value={config.perFileMaxChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, perFileMaxChars: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">文件总最大字符数</div>
              <input
                className={inputClass}
                value={config.totalFilesMaxChars ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, totalFilesMaxChars: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>

          <div className="text-sm font-semibold text-gray-800 pt-2">日志（Logs）</div>
          <div className="grid grid-cols-3 gap-2">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={config.logEnabled !== false}
                onChange={(e) => setConfig((v) => ({ ...v, logEnabled: e.target.checked }))}
                disabled={!editing || !canWrite || loading}
              />
              <span>启用</span>
            </label>
            <div>
              <div className="text-xs text-gray-600 mb-1">日志采样率</div>
              <input
                className={inputClass}
                value={config.logSampleRate ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, logSampleRate: toFloat(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
            <div>
              <div className="text-xs text-gray-600 mb-1">日志保留天数</div>
              <input
                className={inputClass}
                value={config.logMaxDays ?? ''}
                onChange={(e) => setConfig((v) => ({ ...v, logMaxDays: toInt(e.target.value) }))}
                disabled={!editing || !canWrite || loading}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="border-t pt-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-sm font-semibold text-gray-800">裁剪/压缩日志</div>
            <div className="text-xs text-gray-500">仅记录发生变更的请求（按采样率）</div>
          </div>
          <button type="button" className={btnSecondaryClass} onClick={loadLogs} disabled={loading}>
            刷新日志
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-600">
                <th className="py-2 pr-3">时间</th>
                <th className="py-2 pr-3">原因</th>
                <th className="py-2 pr-3">Token 变化</th>
                <th className="py-2 pr-3">字符变化</th>
                <th className="py-2 pr-3">会话</th>
                <th className="py-2 pr-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {(logs ?? []).map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="py-2 pr-3 text-gray-700">{fmtDateTime(r.createdAt)}</td>
                  <td className="py-2 pr-3 text-gray-700">{r.reason ?? '—'}</td>
                  <td className="py-2 pr-3 text-gray-700">
                    {(r.beforeTokens ?? '—') as unknown as string} → {(r.afterTokens ?? '—') as unknown as string}
                  </td>
                  <td className="py-2 pr-3 text-gray-700">
                    {(r.beforeChars ?? '—') as unknown as string} → {(r.afterChars ?? '—') as unknown as string}
                  </td>
                  <td className="py-2 pr-3 text-gray-700">{r.sessionId ?? '—'}</td>
                  <td className="py-2 pr-3">
                    <button
                      type="button"
                      className="text-blue-600 hover:text-blue-800 font-medium"
                      onClick={() => void onSelectLog(r.id)}
                      disabled={loading}
                    >
                      详情
                    </button>
                  </td>
                </tr>
              ))}
              {!logs?.length && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={6}>
                    暂无日志
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between">
          <div className="text-xs text-gray-500">
            {logsTotal ? `共 ${logsTotal} 条` : '—'}，第 {logsPage + 1} / {logsTotalPages} 页
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className={btnSecondaryClass}
              onClick={() => setLogsPage((p) => Math.max(0, p - 1))}
              disabled={loading || logsPage <= 0}
            >
              上一页
            </button>
            <button
              type="button"
              className={btnSecondaryClass}
              onClick={() => setLogsPage((p) => Math.min(logsTotalPages - 1, p + 1))}
              disabled={loading || logsPage >= logsTotalPages - 1}
            >
              下一页
            </button>
          </div>
        </div>
      </div>

      <Modal isOpen={selectedLogId != null} onClose={() => setSelectedLogId(null)} title="上下文治理日志详情">
        {!selectedLog ? (
          <div className="text-sm text-gray-500">加载中…</div>
        ) : (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <div className="text-xs text-gray-500">ID</div>
                <div className="text-gray-800">{selectedLog.id}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">时间</div>
                <div className="text-gray-800">{fmtDateTime(selectedLog.createdAt)}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">原因</div>
                <div className="text-gray-800">{selectedLog.reason ?? '—'}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500">会话</div>
                <div className="text-gray-800">{selectedLog.sessionId ?? '—'}</div>
              </div>
            </div>

            <div className="flex items-center justify-between gap-2">
              <div className="text-sm font-semibold text-gray-800">详情 JSON</div>
              <button
                type="button"
                className={btnSecondaryClass}
                onClick={() => void copyText(JSON.stringify(selectedLog.detailJson ?? {}, null, 2))}
              >
                复制 JSON
              </button>
            </div>
            <pre className="bg-gray-50 border rounded p-3 text-xs overflow-auto max-h-[50vh]">
              {JSON.stringify(selectedLog.detailJson ?? {}, null, 2)}
            </pre>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ChatContextGovernanceForm;
