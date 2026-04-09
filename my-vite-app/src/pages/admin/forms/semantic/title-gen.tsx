import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminGetPostTitleGenConfig,
  adminListPostTitleGenHistory,
  adminUpsertPostTitleGenConfig,
  type Page,
  type PostTitleGenConfigDTO,
  type PostTitleGenHistoryDTO,
} from '../../../../services/titleGenAdminService';
import { suggestPostTitles, type AiPostTitleSuggestResponse } from '../../../../services/aiTitleService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import { EditToggleButton } from '../../../../components/admin/EditToggleButton';
import PromptContentCard from '../../../../components/admin/PromptContentCard';
import {
  applySavedConfigState,
  applyUnavailableFallback,
  buildSuggestionPayload,
  loadPromptDraftState,
  useAiProviderOptions,
  usePromptConfigEditorState,
  validateSuggestionConfigForm,
} from './semanticConfigShared';

type FormState = {
  enabled: boolean;
  promptCode: string;
  model: string;
  providerId: string;
  temperature: string;
  topP: string;
  enableThinking: boolean;
  defaultCount: string;
  maxCount: string;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): PostTitleGenConfigDTO {
  return {
    enabled: true,
    promptCode: 'TITLE_GEN',
    temperature: 0.4,
    topP: 0.9,
    enableThinking: false,
    defaultCount: 5,
    maxCount: 10,
    maxContentChars: 4000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
  };
}

function toFormState(cfg?: PostTitleGenConfigDTO | null): FormState {
  const base = defaultConfig();
  return {
    enabled: cfg?.enabled === null || cfg?.enabled === undefined ? base.enabled : Boolean(cfg.enabled),
    promptCode: cfg?.promptCode ?? base.promptCode,
    model: cfg?.model ?? base.model ?? '',
    providerId: cfg?.providerId ?? base.providerId ?? '',
    temperature:
      cfg?.temperature === null || cfg?.temperature === undefined ? String(base.temperature) : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? String(base.topP) : String(cfg.topP),
    enableThinking:
      cfg?.enableThinking === null || cfg?.enableThinking === undefined
        ? Boolean(base.enableThinking)
        : Boolean(cfg.enableThinking),
    defaultCount:
      cfg?.defaultCount === null || cfg?.defaultCount === undefined ? String(base.defaultCount) : String(cfg.defaultCount),
    maxCount: cfg?.maxCount === null || cfg?.maxCount === undefined ? String(base.maxCount) : String(cfg.maxCount),
    maxContentChars:
      cfg?.maxContentChars === null || cfg?.maxContentChars === undefined
        ? String(base.maxContentChars)
        : String(cfg.maxContentChars),
    historyEnabled:
      cfg?.historyEnabled === null || cfg?.historyEnabled === undefined
        ? Boolean(base.historyEnabled)
        : Boolean(cfg.historyEnabled),
    historyKeepDays:
      cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined
        ? String(base.historyKeepDays)
        : String(cfg.historyKeepDays),
    historyKeepRows:
      cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined
        ? String(base.historyKeepRows)
        : String(cfg.historyKeepRows),
  };
}

function validateForm(s: FormState): string[] {
  return validateSuggestionConfigForm(s);
}

function buildPayload(s: FormState) {
  return buildSuggestionPayload(s);
}

const TitleGenForm: React.FC = () => {
  const {
    loading,
    setLoading,
    saving,
    setSaving,
    testing,
    setTesting,
    error,
    setError,
    savedHint,
    setSavedHint,
    editing,
    setEditing,
    promptLoadError,
    setPromptLoadError,
    committedPromptDraft,
    setCommittedPromptDraft,
    promptDraft,
    setPromptDraft,
  } = usePromptConfigEditorState();
  const { providers, activeProviderId, chatProviders } = useAiProviderOptions();

  const [form, setForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(defaultConfig()));

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !saving && !loading;

  const formHasUnsavedChanges = useMemo(() => {
    return (
      form.enabled !== committedForm.enabled ||
      form.promptCode !== committedForm.promptCode ||
      form.model !== committedForm.model ||
      form.providerId !== committedForm.providerId ||
      form.temperature !== committedForm.temperature ||
      form.topP !== committedForm.topP ||
      form.enableThinking !== committedForm.enableThinking ||
      form.defaultCount !== committedForm.defaultCount ||
      form.maxCount !== committedForm.maxCount ||
      form.maxContentChars !== committedForm.maxContentChars ||
      form.historyEnabled !== committedForm.historyEnabled ||
      form.historyKeepDays !== committedForm.historyKeepDays ||
      form.historyKeepRows !== committedForm.historyKeepRows
    );
  }, [form, committedForm]);
  const promptHasUnsavedChanges = useMemo(
    () => JSON.stringify(promptDraft) !== JSON.stringify(committedPromptDraft),
    [promptDraft, committedPromptDraft],
  );
  const hasUnsavedChanges = formHasUnsavedChanges || promptHasUnsavedChanges;

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    setPromptLoadError(null);
    try {
      const cfg = await adminGetPostTitleGenConfig();
      const next = cfg.promptCode ? toFormState(cfg) : toFormState({ ...defaultConfig(), ...cfg, promptCode: defaultConfig().promptCode });
      setForm(next);
      setCommittedForm(next);
      setEditing(false);
      if (!cfg.promptCode) setSavedHint('后端配置为空，已加载内置默认值（可编辑后保存写入数据库）');

      await loadPromptDraftState(next.promptCode, setPromptLoadError, setCommittedPromptDraft, setPromptDraft);
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
  }, [setCommittedPromptDraft, setEditing, setError, setLoading, setPromptDraft, setPromptLoadError, setSavedHint]);

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
      await applySavedConfigState({
        saveConfig: () => adminUpsertPostTitleGenConfig(buildPayload(form)),
        toFormState,
        promptCode: form.promptCode,
        promptDraft,
        promptHasUnsavedChanges,
        setCommittedPromptDraft,
        setForm,
        setCommittedForm,
        setEditing,
        setError,
        setSavedHint,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [canSave, form, promptDraft, promptHasUnsavedChanges, saving, setCommittedPromptDraft, setEditing, setError, setSavedHint, setSaving]);

  const [testContent, setTestContent] = useState('');
  const [testCount, setTestCount] = useState('');
  const [testResp, setTestResp] = useState<AiPostTitleSuggestResponse | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  const onTest = useCallback(async () => {
    const content = testContent.trim();
    if (testing) return;
    if (content.length < 10) {
      setTestError('内容太短了，至少输入 10 个字符用于试运行。');
      return;
    }
    setTesting(true);
    setTestError(null);
    setTestResp(null);
    try {
      const n = parseOptionalNumber(testCount);
      const resp = await suggestPostTitles({
        content,
        count: n === undefined ? undefined : Math.trunc(n),
      });
      setTestResp(resp);
    } catch (e) {
      setTestError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [setTesting, testContent, testCount, testing]);

  const [historyPage, setHistoryPage] = useState<Page<PostTitleGenHistoryDTO> | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);

  const loadHistory = useCallback(async (pageNo: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const p = await adminListPostTitleGenHistory({ page: pageNo, size: 20 });
      setHistoryPage(p);
    } catch (e) {
      setHistoryError(e instanceof Error ? e.message : String(e));
      setHistoryPage(null);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHistory(historyPageNo);
  }, [loadHistory, historyPageNo]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">标题生成</h3>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">启用标题生成：</span>
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
                  开启
                </option>
                <option value="false" className="text-red-600">
                  关闭
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
            {editing && (
              <button
                type="button"
                onClick={() => void onSave()}
                disabled={loading || saving || !canSave || !hasUnsavedChanges}
                className="px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 text-sm"
              >
                {saving ? '保存中...' : '保存配置'}
              </button>
            )}
          </div>
        </div>

        {savedHint && <div className="text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-md px-3 py-2">{savedHint}</div>}
        {error && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{error}</div>}

        {editing && formErrors.length > 0 && (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2 space-y-1">
            {formErrors.map((m) => (
              <div key={m}>{m}</div>
            ))}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <div className="text-xs text-gray-600 mb-1">默认生成数量 defaultCount</div>
            <input
              value={form.defaultCount}
              onChange={(e) => setForm((p) => ({ ...p, defaultCount: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 5"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">最大生成数量 maxCount</div>
            <input
              value={form.maxCount}
              onChange={(e) => setForm((p) => ({ ...p, maxCount: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 10"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">上下文长度 maxContentChars</div>
            <input
              value={form.maxContentChars}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 8000"
            />
          </div>
        </div>

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
            <div className="text-xs text-gray-600 mb-1">温度 temperature（可选）</div>
            <input
              value={form.temperature}
              onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.4"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">TOP-P topP（可选）</div>
            <input
              value={form.topP}
              onChange={(e) => setForm((p) => ({ ...p, topP: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.9"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">历史保留天数 historyKeepDays（可选）</div>
            <input
              value={form.historyKeepDays}
              onChange={(e) => setForm((p) => ({ ...p, historyKeepDays: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 30"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <PromptContentCard
            title="标题生成提示词"
            draft={promptDraft}
            editing={editing}
            onChange={(next) => {
              setPromptDraft(next);
              setSavedHint(null);
            }}
            hint={promptLoadError ?? '引用 prompts 表中的 prompt_code'}
          />
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">试运行</h4>
          <button
            type="button"
            onClick={() => void onTest()}
            disabled={testing}
            className="px-3 py-1.5 rounded-md bg-gray-100 hover:bg-gray-200 disabled:opacity-60 text-sm"
          >
            {testing ? '生成中...' : '用当前配置生成标题'}
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="md:col-span-2">
            <textarea
              value={testContent}
              onChange={(e) => setTestContent(e.target.value)}
              className="w-full rounded border px-3 py-2 text-sm min-h-[90px]"
              placeholder="输入一段帖子正文用于试运行"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">count（可选，留空用默认）</div>
            <input
              value={testCount}
              onChange={(e) => setTestCount(e.target.value)}
              className="w-full rounded border px-3 py-2 text-sm"
              placeholder="例如 5"
            />
            <div className="text-xs text-gray-500 mt-2">
              说明：试运行也会走真实生成接口；如开启历史记录，会产生一条历史。
            </div>
          </div>
        </div>

        {testError && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{testError}</div>}

        {testResp && (
          <div className="space-y-2">
            <div className="text-xs text-gray-600">
              model: {testResp.model ?? '-'}，latency: {testResp.latencyMs ?? '-'}ms
            </div>
            <div className="flex flex-wrap gap-2">
              {testResp.titles?.map((t, idx) => (
                <span key={`${idx}-${t}`} className="px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm">
                  {t}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">生成历史</h4>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void loadHistory(historyPageNo)}
              disabled={historyLoading}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              {historyLoading ? '刷新中...' : '刷新'}
            </button>
            <button
              type="button"
              onClick={() => setHistoryPageNo((p) => Math.max(0, p - 1))}
              disabled={historyLoading || historyPageNo <= 0}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              上一页
            </button>
            <button
              type="button"
              onClick={() => setHistoryPageNo((p) => p + 1)}
              disabled={historyLoading || (historyPage?.content?.length ?? 0) === 0}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              下一页
            </button>
          </div>
        </div>

        {historyError && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{historyError}</div>}

        {historyPage?.content?.length ? (
          <div className="space-y-2">
            {historyPage.content.map((row) => (
              <div key={row.id} className="border border-gray-200 rounded-md p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="text-xs text-gray-600">
                    #{row.id} · {row.createdAt} · userId={row.userId} · count={row.requestedCount} · latency={row.latencyMs ?? '-'}ms
                  </div>
                  <div className="text-xs text-gray-600">
                    model={row.model ?? '-'} · temp={row.temperature ?? '-'} · promptV={row.promptVersion ?? '-'}
                  </div>
                </div>
                {row.contentExcerpt && <div className="text-xs text-gray-600 mt-2">excerpt: {row.contentExcerpt}</div>}
                <div className="flex flex-wrap gap-2 mt-2">
                  {row.titles?.map((t, idx) => (
                    <span key={`${row.id}-${idx}`} className="px-2 py-1 rounded-full border border-gray-300 bg-white text-xs">
                      {t}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-gray-600">{historyLoading ? '加载中...' : '暂无历史记录'}</div>
        )}
      </div>
    </div>
  );
};

export default TitleGenForm;
