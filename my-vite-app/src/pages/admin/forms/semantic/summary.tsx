import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { HistoryPagination } from '../../../../components/admin/HistoryPagination';
import {
  adminGetPostSummaryConfig,
  adminListPostSummaryHistory,
  adminRegeneratePostSummary,
  adminUpsertPostSummaryConfig,
  type Page,
  type PostSummaryGenConfigDTO,
  type PostSummaryGenHistoryDTO,
} from '../../../../services/postSummaryAdminService';
import { adminBatchGetPrompts, adminUpdatePromptContent, type PromptContentDTO } from '../../../../services/promptsAdminService';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import { EditToggleButton } from '../../../../components/admin/EditToggleButton';
import PromptContentCard, { type PromptContentDraft } from '../../../../components/admin/PromptContentCard';
import { applyUnavailableFallback } from './semanticConfigShared';

type FormState = {
  enabled: boolean;
  model: string;
  providerId: string;
  temperature: string;
  topP: string;
  enableThinking: boolean;
  maxContentChars: string;
  promptCode: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): PostSummaryGenConfigDTO {
  return {
    enabled: true,
    model: null,
    temperature: 0.3,
    topP: 0.7,
    enableThinking: false,
    maxContentChars: 8000,
    promptCode: 'SUMMARY_GEN',
  };
}

function toPromptDraft(dto?: PromptContentDTO | null): PromptContentDraft {
  return {
    name: dto?.name ?? '',
    systemPrompt: dto?.systemPrompt ?? '',
    userPromptTemplate: dto?.userPromptTemplate ?? '',
  };
}

function toFormState(cfg?: PostSummaryGenConfigDTO | null): FormState {
  return {
    enabled: Boolean(cfg?.enabled),
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    enableThinking: Boolean(cfg?.enableThinking),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    promptCode: cfg?.promptCode ?? '',
  };
}

function validateForm(s: FormState): string[] {
  const errors: string[] = [];
  if (!s.promptCode.trim()) errors.push('提示词编码不能为空');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 1)) errors.push('温度参数需在 [0, 1] 范围内');

  const topP = parseOptionalNumber(s.topP);
  if (topP !== undefined && (topP < 0 || topP > 1)) errors.push('TOP-P 需在 [0, 1] 范围内');

  const mcc = parseOptionalNumber(s.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 50000)) errors.push('上下文长度需为 200~50000 的整数');

  return errors;
}

function buildPayload(s: FormState) {
  const maxContentChars = parseOptionalNumber(s.maxContentChars);
  return {
    enabled: s.enabled,
    maxContentChars: maxContentChars === undefined ? 4000 : Math.trunc(maxContentChars),
    promptCode: s.promptCode,
  };
}

const SummaryForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);
  const [promptLoadError, setPromptLoadError] = useState<string | null>(null);
  const [committedPromptDraft, setCommittedPromptDraft] = useState<PromptContentDraft | null>(null);
  const [promptDraft, setPromptDraft] = useState<PromptContentDraft | null>(null);

  const [form, setForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(defaultConfig()));

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !loading && !saving;
  const formHasUnsavedChanges = useMemo(() => JSON.stringify(form) !== JSON.stringify(committedForm), [form, committedForm]);
  const promptHasUnsavedChanges = useMemo(
    () => JSON.stringify(promptDraft) !== JSON.stringify(committedPromptDraft),
    [promptDraft, committedPromptDraft]
  );
  const hasUnsavedChanges = formHasUnsavedChanges || promptHasUnsavedChanges;

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

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const opts = await getAiChatOptions();
        if (cancelled) return;
        setChatProviders((opts.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[]);
      } catch {
        if (cancelled) return;
        setChatProviders([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    setPromptLoadError(null);
    try {
      const cfg = await adminGetPostSummaryConfig();
      const prompt = cfg?.promptCode?.trim();
      const next = toFormState(prompt ? cfg : { ...defaultConfig(), ...cfg });
      setForm(next);
      setCommittedForm(next);
      setEditing(false);
      if (!prompt) setSavedHint('后端配置为空，已加载内置默认值（可编辑后保存写入数据库）');

      try {
        const resp = await adminBatchGetPrompts([next.promptCode]);
        const dto = resp.prompts?.[0];
        if (!dto || (resp.missingCodes ?? []).length) {
          setCommittedPromptDraft(null);
          setPromptDraft(null);
        } else {
          const draft = toPromptDraft(dto);
          setCommittedPromptDraft(draft);
          setPromptDraft(draft);
        }
      } catch (e) {
        setPromptLoadError(e instanceof Error ? e.message : String(e));
        setCommittedPromptDraft(null);
        setPromptDraft(null);
      }
    } catch (e) {
      applyUnavailableFallback(
        toFormState(defaultConfig()),
        e,
        setForm,
        setCommittedForm,
        setEditing,
        setError,
        setSavedHint,
        setCommittedPromptDraft,
        setPromptDraft,
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  const onSave = useCallback(async () => {
    if (!canSave) return;
    if (saving) return;
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertPostSummaryConfig(payload);

      let promptUpdateErr: string | null = null;
      if (promptDraft && promptHasUnsavedChanges) {
        try {
          await adminUpdatePromptContent(form.promptCode, {
            systemPrompt: promptDraft.systemPrompt,
            userPromptTemplate: promptDraft.userPromptTemplate,
          });
          setCommittedPromptDraft(promptDraft);
        } catch (e) {
          promptUpdateErr = e instanceof Error ? e.message : String(e);
        }
      }

      const next = toFormState(saved);
      setForm(next);
      setCommittedForm(next);
      setEditing(false);
      if (promptUpdateErr) {
        setError(`配置已保存，但提示词保存失败：${promptUpdateErr}`);
      } else {
        setSavedHint('保存成功');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [canSave, form, saving, promptDraft, promptHasUnsavedChanges]);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);
  const [historyPageSize, setHistoryPageSize] = useState(20);
  const [historyPage, setHistoryPage] = useState<Page<PostSummaryGenHistoryDTO> | null>(null);
  const [historyHint, setHistoryHint] = useState<string | null>(null);
  const [regeneratingPostId, setRegeneratingPostId] = useState<number | null>(null);

  const loadHistory = useCallback(async (pageNo: number, pageSize?: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const size = pageSize ?? historyPageSize;
      const p = await adminListPostSummaryHistory({ page: pageNo, size });
      setHistoryPage(p);
      setHistoryPageNo(pageNo);
    } catch (e) {
      setHistoryError(e instanceof Error ? e.message : String(e));
      setHistoryPage(null);
    } finally {
      setHistoryLoading(false);
    }
  }, [historyPageSize]);

  useEffect(() => {
    void loadHistory(0);
  }, [loadHistory]);

  const totalPages = useMemo(() => {
    const total = historyPage?.totalElements ?? 0;
    const size = historyPage?.size ?? historyPageSize;
    if (!size) return 0;
    return Math.ceil(total / size);
  }, [historyPage?.size, historyPage?.totalElements, historyPageSize]);

  const [errorOpen, setErrorOpen] = useState(false);
  const [errorText, setErrorText] = useState<string>('');

  const onRegenerate = useCallback(
    async (postId: number) => {
      if (!postId) return;
      if (regeneratingPostId !== null) return;
      setRegeneratingPostId(postId);
      setHistoryError(null);
      try {
        await adminRegeneratePostSummary(postId);
        setHistoryHint(`已提交重新生成（帖子ID=${postId}）。请稍后刷新查看结果。`);
        setTimeout(() => {
          void loadHistory(historyPageNo);
        }, 1500);
      } catch (e) {
        setHistoryError(e instanceof Error ? e.message : String(e));
      } finally {
        setRegeneratingPostId(null);
      }
    },
    [historyPageNo, loadHistory, regeneratingPostId]
  );

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">帖子摘要</h3>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">功能开关：</span>
              <select
                value={form.enabled ? 'true' : 'false'}
                onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.value === 'true' }))}
                disabled={!editing || loading || saving}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.enabled ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
                title={!editing ? '只读（点击右侧「编辑」后可修改）' : '修改开关（需保存生效）'}
              >
                <option value="true" className="text-green-600">
                  启用
                </option>
                <option value="false" className="text-red-600">
                  禁用
                </option>
              </select>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">启用深度思考：</span>
              <select
                value={form.enableThinking ? 'true' : 'false'}
                onChange={(e) => setForm((p) => ({ ...p, enableThinking: e.target.value === 'true' }))}
                disabled={!editing || loading || saving}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.enableThinking ? 'text-purple-700 border-purple-200 bg-white' : 'text-gray-700 border-gray-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
                title={!editing ? '只读（点击右侧「编辑」后可修改）' : '修改开关（需保存生效）'}
              >
                <option value="false" className="text-gray-700">
                  关闭
                </option>
                <option value="true" className="text-purple-700">
                  开启
                </option>
              </select>
            </div>
            <button
              type="button"
              onClick={() => void loadConfig()}
              disabled={loading}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              {loading ? '刷新中...' : '刷新配置'}
            </button>
            <EditToggleButton
              editing={editing}
              loading={loading}
              saving={saving}
              onEdit={() => setEditing(true)}
              onCancel={() => {
                setForm(committedForm);
                setPromptDraft(committedPromptDraft);
                setEditing(false);
                setError(null);
                setSavedHint(null);
              }}
            />
            {editing ? (
              <button
                type="button"
                onClick={() => void onSave()}
                disabled={loading || saving || !canSave || !hasUnsavedChanges}
                className="px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 text-sm"
              >
                {saving ? '保存中...' : '保存配置'}
              </button>
            ) : null}
          </div>
        </div>

        {savedHint ? <div className="text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-md px-3 py-2">{savedHint}</div> : null}
        {error ? <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{error}</div> : null}

        {formErrors.length > 0 ? (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2 space-y-1">
            {formErrors.map((m) => (
              <div key={m}>{m}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="md:col-span-2">
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={form.providerId}
              model={form.model}
              disabled={!editing || loading || saving}
              selectClassName="w-full rounded border px-3 py-2 text-sm bg-white disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              onChange={(next) => setForm((p) => ({ ...p, providerId: next.providerId, model: next.model }))}
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">温度参数（0~1）</div>
            <input
              value={form.temperature}
              onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.3"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">TOP-P（0~1）</div>
            <input
              value={form.topP}
              onChange={(e) => setForm((p) => ({ ...p, topP: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.7"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">上下文长度</div>
            <input
              value={form.maxContentChars}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 8000"
            />
          </div>
        </div>

        <PromptContentCard
          title="摘要生成提示词"
          draft={promptDraft}
          editing={editing}
          onChange={(next) => {
            setPromptDraft(next);
            setSavedHint(null);
          }}
          hint={promptLoadError ?? '引用当前配置的提示词'}
        />
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">摘要生成日志</h4>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
            disabled={historyLoading}
            onClick={() => void loadHistory(historyPageNo)}
          >
            刷新
          </button>
        </div>

        {historyHint ? (
          <div className="text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-md px-3 py-2">{historyHint}</div>
        ) : null}
        {historyError ? <div className="text-sm text-red-700">{historyError}</div> : null}
        {historyLoading ? <div className="text-sm text-gray-600">加载中...</div> : null}

        {!historyLoading && (historyPage?.content?.length ?? 0) === 0 ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600">暂无日志</div>
        ) : null}

        {(historyPage?.content?.length ?? 0) > 0 ? (
          <div className="overflow-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b">
                  <th className="py-2 pr-4">任务ID</th>
                  <th className="py-2 pr-4">关联帖子ID</th>
                  <th className="py-2 pr-4">生成状态</th>
                  <th className="py-2 pr-4">使用的模型</th>
                  <th className="py-2 pr-4">生成时间</th>
                  <th className="py-2 pr-4">耗时</th>
                  <th className="py-2 pr-4">操作</th>
                </tr>
              </thead>
              <tbody>
                {(historyPage?.content ?? []).map((h) => {
                  const isSuccess = String(h.status || '').toUpperCase() === 'SUCCESS';
                  return (
                    <tr key={h.id} className="border-b last:border-b-0 align-top">
                      <td className="py-2 pr-4 whitespace-nowrap text-gray-700">#{h.id}</td>
                      <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.postId}</td>
                      <td className="py-2 pr-4 whitespace-nowrap">
                        <span
                          className={`inline-flex px-2 py-1 rounded text-xs ${
                            isSuccess ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {isSuccess ? '成功' : '失败'}
                        </span>
                      </td>
                      <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.model || '（默认）'}</td>
                      <td className="py-2 pr-4 whitespace-nowrap text-gray-600">{new Date(h.createdAt).toLocaleString()}</td>
                      <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.latencyMs ? `${h.latencyMs}ms` : '-'}</td>
                      <td className="py-2 pr-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            className="rounded border px-3 py-1 text-xs hover:bg-gray-50 disabled:opacity-50"
                            disabled={isSuccess || !h.errorMessage}
                            onClick={() => {
                              setErrorText(String(h.errorMessage || ''));
                              setErrorOpen(true);
                            }}
                          >
                            查看错误
                          </button>
                          {!isSuccess ? (
                            <button
                              type="button"
                              className="rounded border px-3 py-1 text-xs hover:bg-gray-50 disabled:opacity-50"
                              disabled={historyLoading || regeneratingPostId === h.postId}
                              onClick={() => void onRegenerate(h.postId)}
                            >
                              {regeneratingPostId === h.postId ? '重新生成中...' : '重新生成'}
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}

        <HistoryPagination
          pageNo={historyPageNo}
          pageSize={historyPageSize}
          totalPages={totalPages}
          loading={historyLoading}
          onPageSizeChange={(nextSize) => {
            setHistoryPageSize(nextSize);
            void loadHistory(0, nextSize);
          }}
          onPrevPage={() => void loadHistory(Math.max(0, historyPageNo - 1))}
          onNextPage={() => void loadHistory(historyPageNo + 1)}
        />
      </div>

      {errorOpen ? (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] overflow-auto">
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <div className="font-semibold">错误详情</div>
              <button
                type="button"
                className="rounded border px-3 py-1 hover:bg-gray-50"
                onClick={() => setErrorOpen(false)}
              >
                关闭
              </button>
            </div>
            <div className="p-4">
              <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[70vh]">
                {errorText || '—'}
              </pre>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default SummaryForm;
