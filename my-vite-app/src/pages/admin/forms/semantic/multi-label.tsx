import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminGetPostTagGenConfig,
  adminListPostTagGenHistory,
  adminUpsertPostTagGenConfig,
  type Page,
  type PostTagGenConfigDTO,
  type PostTagGenHistoryDTO,
} from '../../../../services/tagGenAdminService';
import {
  adminGetPostLangLabelGenConfig,
  adminUpsertPostLangLabelGenConfig,
  type PostLangLabelGenConfigDTO,
} from '../../../../services/langLabelAdminService';
import { suggestPostTags } from '../../../../services/aiTagService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import { suggestPostLangLabels } from '../../../../services/aiLangLabelService';
import PromptContentCard, { type PromptContentDraft } from '../../../../components/admin/PromptContentCard';
import SemanticEditActionBar from './SemanticEditActionBar';
import {
  applySavedConfigState,
  applyUnavailableFallback,
  buildSuggestionPayload,
  loadPromptDraftState,
  useAiProviderOptions,
  usePromptConfigEditorState,
  validatePromptRangeFields,
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

type LangFormState = {
  enabled: boolean;
  promptCode: string;
  model: string;
  providerId: string;
  temperature: string;
  topP: string;
  enableThinking: boolean;
  maxContentChars: string;
};

type TestKind = 'TOPIC' | 'LANG';
type TestResult = {
  kind: TestKind;
  items: string[];
  model?: string;
  latencyMs?: number;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): PostTagGenConfigDTO {
  return {
    enabled: true,
    promptCode: 'TAG_GEN',
    temperature: 0.4,
    topP: 0.8,
    enableThinking: false,
    defaultCount: 5,
    maxCount: 10,
    maxContentChars: 4000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
  };
}

const DEFAULT_LANG_MAX_CONTENT_CHARS = 8000;

function toFormState(cfg?: PostTagGenConfigDTO | null): FormState {
  return {
    enabled: Boolean(cfg?.enabled),
    promptCode: cfg?.promptCode ?? 'TAG_GEN',
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    enableThinking: Boolean(cfg?.enableThinking),
    defaultCount: cfg?.defaultCount === null || cfg?.defaultCount === undefined ? '' : String(cfg.defaultCount),
    maxCount: cfg?.maxCount === null || cfg?.maxCount === undefined ? '' : String(cfg.maxCount),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    historyEnabled: Boolean(cfg?.historyEnabled),
    historyKeepDays: cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined ? '' : String(cfg.historyKeepDays),
    historyKeepRows: cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined ? '' : String(cfg.historyKeepRows),
  };
}

function defaultLangConfig(): PostLangLabelGenConfigDTO {
  return {
    enabled: true,
    promptCode: 'LANG_DETECT',
    temperature: 0.0,
    topP: 0.2,
    enableThinking: false,
    maxContentChars: 8000,
  };
}

function toLangFormState(cfg?: PostLangLabelGenConfigDTO | null): LangFormState {
  return {
    enabled: Boolean(cfg?.enabled),
    promptCode: cfg?.promptCode ?? 'LANG_DETECT',
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    enableThinking: Boolean(cfg?.enableThinking),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
  };
}

function validateForm(s: FormState): string[] {
  return validateSuggestionConfigForm(s);
}

function validateLangForm(s: LangFormState): string[] {
  return validatePromptRangeFields(s, 100000);
}

function buildPayload(s: FormState) {
  return buildSuggestionPayload(s);
}

function buildLangPayload(s: LangFormState) {
  const maxContentChars = parseOptionalNumber(s.maxContentChars);

  return {
    enabled: s.enabled,
    promptCode: s.promptCode,
    maxContentChars: maxContentChars === undefined ? DEFAULT_LANG_MAX_CONTENT_CHARS : Math.trunc(maxContentChars),
  };
}

const MultiLabelForm: React.FC = () => {
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

  const [form, setForm] = useState<FormState>(() => toFormState(null));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(null));

  const [langLoading, setLangLoading] = useState(false);
  const [langSaving, setLangSaving] = useState(false);
  const [langError, setLangError] = useState<string | null>(null);
  const [langSavedHint, setLangSavedHint] = useState<string | null>(null);
  const [langEditing, setLangEditing] = useState(false);

  const [langPromptLoadError, setLangPromptLoadError] = useState<string | null>(null);
  const [langCommittedPromptDraft, setLangCommittedPromptDraft] = useState<PromptContentDraft | null>(null);
  const [langPromptDraft, setLangPromptDraft] = useState<PromptContentDraft | null>(null);

  const [langForm, setLangForm] = useState<LangFormState>(() => toLangFormState(null));
  const [langCommittedForm, setLangCommittedForm] = useState<LangFormState>(() => toLangFormState(null));

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

  const langFormErrors = useMemo(() => validateLangForm(langForm), [langForm]);
  const langFormWarnings = useMemo(() => {
    const warnings: string[] = [];
    return warnings;
  }, []);
  const langCanSave = langFormErrors.length === 0 && !langSaving && !langLoading;
  const langFormHasUnsavedChanges = useMemo(() => {
    return (
      langForm.enabled !== langCommittedForm.enabled ||
      langForm.promptCode !== langCommittedForm.promptCode ||
      langForm.model !== langCommittedForm.model ||
      langForm.providerId !== langCommittedForm.providerId ||
      langForm.temperature !== langCommittedForm.temperature ||
      langForm.topP !== langCommittedForm.topP ||
      langForm.enableThinking !== langCommittedForm.enableThinking ||
      langForm.maxContentChars !== langCommittedForm.maxContentChars
    );
  }, [langForm, langCommittedForm]);
  const langPromptHasUnsavedChanges = useMemo(
    () => JSON.stringify(langPromptDraft) !== JSON.stringify(langCommittedPromptDraft),
    [langPromptDraft, langCommittedPromptDraft],
  );
  const langHasUnsavedChanges = langFormHasUnsavedChanges || langPromptHasUnsavedChanges;

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    setPromptLoadError(null);
    try {
      const cfg = await adminGetPostTagGenConfig();
      const base = defaultConfig();
      const next = cfg.promptCode ? toFormState(cfg) : toFormState({ ...base, ...cfg, promptCode: base.promptCode });
      setForm(next);
      setCommittedForm(next);
      setEditing(false);

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

  const loadLangConfig = useCallback(async () => {
    setLangLoading(true);
    setLangError(null);
    setLangSavedHint(null);
    setLangPromptLoadError(null);
    try {
      const cfg = await adminGetPostLangLabelGenConfig();
      const base = defaultLangConfig();
      const next = cfg.promptCode ? toLangFormState(cfg) : toLangFormState({ ...base, ...cfg, promptCode: base.promptCode });
      setLangForm(next);
      setLangCommittedForm(next);
      setLangEditing(false);

      await loadPromptDraftState(next.promptCode, setLangPromptLoadError, setLangCommittedPromptDraft, setLangPromptDraft);
    } catch (e) {
      applyUnavailableFallback(
        toLangFormState(defaultLangConfig()),
        e,
        setLangForm,
        setLangCommittedForm,
        setLangEditing,
        setLangError,
        setLangSavedHint,
        setLangCommittedPromptDraft,
        setLangPromptDraft,
      );
    } finally {
      setLangLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLangConfig();
  }, [loadLangConfig]);

  const onSave = useCallback(async () => {
    if (!canSave) return;
    if (saving) return;
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      await applySavedConfigState({
        saveConfig: () => adminUpsertPostTagGenConfig(buildPayload(form)),
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

  const onSaveLang = useCallback(async () => {
    if (!langCanSave) return;
    if (langSaving) return;
    setLangSaving(true);
    setLangError(null);
    setLangSavedHint(null);
    try {
      await applySavedConfigState({
        saveConfig: () => adminUpsertPostLangLabelGenConfig(buildLangPayload(langForm)),
        toFormState: toLangFormState,
        promptCode: langForm.promptCode,
        promptDraft: langPromptDraft,
        promptHasUnsavedChanges: langPromptHasUnsavedChanges,
        setCommittedPromptDraft: setLangCommittedPromptDraft,
        setForm: setLangForm,
        setCommittedForm: setLangCommittedForm,
        setEditing: setLangEditing,
        setError: setLangError,
        setSavedHint: setLangSavedHint,
      });
    } catch (e) {
      setLangError(e instanceof Error ? e.message : String(e));
    } finally {
      setLangSaving(false);
    }
  }, [langCanSave, langForm, langSaving, langPromptDraft, langPromptHasUnsavedChanges]);

  const [testKind, setTestKind] = useState<TestKind>('TOPIC');
  const [testTitle, setTestTitle] = useState('');
  const [testContent, setTestContent] = useState('');
  const [testCount, setTestCount] = useState('');
  const [testResult, setTestResult] = useState<TestResult | null>(null);
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
    setTestResult(null);
    try {
      const n = parseOptionalNumber(testCount);
      const title = testTitle.trim() || undefined;
      if (testKind === 'TOPIC') {
        const resp = await suggestPostTags({
          title,
          content,
          count: n === undefined ? undefined : Math.trunc(n),
        });
        setTestResult({ kind: 'TOPIC', items: resp.tags ?? [], model: resp.model, latencyMs: resp.latencyMs });
      } else {
        const resp = await suggestPostLangLabels({ title, content });
        setTestResult({ kind: 'LANG', items: resp.languages ?? [], model: resp.model, latencyMs: resp.latencyMs });
      }
    } catch (e) {
      setTestError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [setTesting, testContent, testCount, testKind, testTitle, testing]);

  const [historyPage, setHistoryPage] = useState<Page<PostTagGenHistoryDTO> | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);

  const loadHistory = useCallback(async (pageNo: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const p = await adminListPostTagGenHistory({ page: pageNo, size: 20 });
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
          <h3 className="text-lg font-semibold">主题标签生成</h3>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">主题标签生成：</span>
              <select
                value={form.enabled ? 'true' : 'false'}
                disabled={!editing || loading || saving}
                onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.value === 'true' }))}
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
                disabled={!editing || loading || saving}
                onChange={(e) => setForm((p) => ({ ...p, enableThinking: e.target.value === 'true' }))}
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
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={loading || saving}
              onClick={() => void loadConfig()}
            >
              刷新
            </button>
            <SemanticEditActionBar
              editing={editing}
              loading={loading}
              saving={saving}
              canSave={canSave}
              hasUnsavedChanges={hasUnsavedChanges}
              onStartEditing={() => setEditing(true)}
              onCancel={() => {
                setForm(committedForm);
                setPromptDraft(committedPromptDraft);
                setEditing(false);
                setError(null);
                setSavedHint(null);
              }}
              onSave={() => void onSave()}
            />
          </div>
        </div>

        {savedHint ? <div className="text-sm text-green-700">{savedHint}</div> : null}
        {error ? <div className="text-sm text-red-700">{error}</div> : null}
        {editing && formErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {formErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-4 gap-3">
          <div className="md:col-span-2">
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={form.providerId}
              model={form.model}
              disabled={!editing}
              selectClassName="w-full rounded border px-3 py-2 border-gray-300 text-sm bg-white disabled:bg-gray-50"
              onChange={(next) => setForm((p) => ({ ...p, providerId: next.providerId, model: next.model }))}
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">温度（0~2，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.temperature}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
              placeholder="例如 0.4"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">TOP-P（0~1，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.topP}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, topP: e.target.value }))}
              placeholder="例如 0.8"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">默认生成数量</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.defaultCount}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, defaultCount: e.target.value }))}
              placeholder="默认 5"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">最大生成数量</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.maxCount}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, maxCount: e.target.value }))}
              placeholder="默认 10"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.maxContentChars}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 4000"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 xl:col-span-2">
            <div>
              <div className="text-sm font-medium text-gray-700 mb-1">历史保留天数</div>
              <input
                className="w-full rounded border px-3 py-2 border-gray-300"
                value={form.historyKeepDays}
                disabled={!editing}
                onChange={(e) => setForm((p) => ({ ...p, historyKeepDays: e.target.value }))}
                placeholder="例如 30"
              />
            </div>
            <div>
              <div className="text-sm font-medium text-gray-700 mb-1">历史保留条数</div>
              <input
                className="w-full rounded border px-3 py-2 border-gray-300"
                value={form.historyKeepRows}
                disabled={!editing}
                onChange={(e) => setForm((p) => ({ ...p, historyKeepRows: e.target.value }))}
                placeholder="例如 5000"
              />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <PromptContentCard
            title="主题标签提示词"
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
          <h3 className="text-lg font-semibold">语言标签生成</h3>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={langLoading || langSaving}
              onClick={() => void loadLangConfig()}
            >
              刷新
            </button>
            {!langEditing ? (
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setLangEditing(true)}
                disabled={langLoading || langSaving}
              >
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={() => {
                    setLangForm(langCommittedForm);
                    setLangPromptDraft(langCommittedPromptDraft);
                    setLangEditing(false);
                    setLangError(null);
                    setLangSavedHint(null);
                  }}
                  disabled={langSaving || langLoading}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
                  onClick={() => void onSaveLang()}
                  disabled={!langCanSave || !langHasUnsavedChanges}
                >
                  {langSaving ? '保存中...' : '保存'}
                </button>
              </>
            )}
          </div>
        </div>

        {langSavedHint ? <div className="text-sm text-green-700">{langSavedHint}</div> : null}
        {langError ? <div className="text-sm text-red-700">{langError}</div> : null}
        {langEditing && langFormWarnings.length ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800 space-y-1">
            {langFormWarnings.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}
        {langEditing && langFormErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {langFormErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={langForm.enabled}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, enabled: e.target.checked }))}
            />
            启用语言标签生成
          </label>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={langForm.enableThinking}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, enableThinking: e.target.checked }))}
            />
            启用深度思考
          </label>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-4 gap-3">
          <div className="md:col-span-2">
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={langForm.providerId}
              model={langForm.model}
              disabled={!langEditing}
              selectClassName="w-full rounded border px-3 py-2 border-gray-300 text-sm bg-white disabled:bg-gray-50"
              onChange={(next) => setLangForm((p) => ({ ...p, providerId: next.providerId, model: next.model }))}
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">温度（0~2，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.temperature}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, temperature: e.target.value }))}
              placeholder="例如 0"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">TOP-P（0~1，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.topP}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, topP: e.target.value }))}
              placeholder="例如 0.2"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.maxContentChars}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 8000"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <PromptContentCard
            title="语言标签提示词"
            draft={langPromptDraft}
            editing={langEditing}
            onChange={(next) => {
              setLangPromptDraft(next);
              setLangSavedHint(null);
            }}
            hint={langPromptLoadError ?? '引用 prompts 表中的 prompt_code'}
          />
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <h4 className="text-base font-semibold">
          试运行（调用{' '}
          {testKind === 'TOPIC' ? '/api/ai/posts/tag-suggestions' : '/api/ai/posts/lang-label-suggestions'}
          ）
        </h4>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <select
            className="rounded border px-3 py-2 border-gray-300 bg-white"
            value={testKind}
            onChange={(e) => {
              const next = e.target.value as TestKind;
              setTestKind(next);
              setTestError(null);
              setTestResult(null);
            }}
          >
            <option value="TOPIC">主题标签生成</option>
            <option value="LANG">语言标签生成</option>
          </select>
          <input
            className={`rounded border px-3 py-2 border-gray-300 ${testKind === 'LANG' ? 'md:col-span-3' : 'md:col-span-2'}`}
            placeholder="标题（可选）"
            value={testTitle}
            onChange={(e) => setTestTitle(e.target.value)}
          />
          {testKind !== 'LANG' ? (
            <input
              className="rounded border px-3 py-2 border-gray-300"
              placeholder="生成数量（可选）"
              value={testCount}
              onChange={(e) => setTestCount(e.target.value)}
            />
          ) : null}
        </div>
        <textarea
          className="w-full rounded border px-3 py-2 border-gray-300 min-h-[120px]"
          placeholder="输入正文内容（至少 10 个字符）"
          value={testContent}
          onChange={(e) => setTestContent(e.target.value)}
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={testing}
            onClick={() => void onTest()}
            className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
          >
            {testing ? (testKind === 'LANG' ? '识别中...' : '生成中...') : testKind === 'LANG' ? '识别语言' : '生成主题标签'}
          </button>
          {testResult?.model ? <div className="text-xs text-gray-500">model: {testResult.model}</div> : null}
          {typeof testResult?.latencyMs === 'number' ? <div className="text-xs text-gray-500">latency: {testResult.latencyMs}ms</div> : null}
        </div>
        {testError ? <div className="text-sm text-red-700">{testError}</div> : null}
        {testResult?.items?.length ? (
          <div className="flex flex-wrap gap-2">
            {testResult.items.map((t, i) => (
              <span key={`${t}-${i}`} className="px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm">
                {t}
              </span>
            ))}
          </div>
        ) : null}
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">生成历史</h4>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading}
              onClick={() => void loadHistory(historyPageNo)}
            >
              刷新
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading || historyPageNo <= 0}
              onClick={() => setHistoryPageNo((p) => Math.max(0, p - 1))}
            >
              上一页
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading || (historyPage?.content?.length ?? 0) === 0}
              onClick={() => setHistoryPageNo((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </div>

        {historyError ? <div className="text-sm text-red-700">{historyError}</div> : null}
        {historyLoading ? <div className="text-sm text-gray-600">加载中...</div> : null}

        {!historyLoading && !historyError && (historyPage?.content?.length ?? 0) === 0 ? (
          <div className="text-sm text-gray-500">暂无历史记录。</div>
        ) : null}

        {(historyPage?.content?.length ?? 0) > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2 pr-4">时间</th>
                  <th className="py-2 pr-4">用户</th>
                  <th className="py-2 pr-4">标题</th>
                  <th className="py-2 pr-4">标签</th>
                  <th className="py-2 pr-4">模型</th>
                  <th className="py-2 pr-4">耗时</th>
                </tr>
              </thead>
              <tbody>
                {historyPage?.content?.map((h) => (
                  <tr key={h.id} className="border-b">
                    <td className="py-2 pr-4">{new Date(h.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4">#{h.userId}</td>
                    <td className="py-2 pr-4 max-w-[280px]">
                      <span className="truncate block" title={h.titleExcerpt ?? ''}>
                        {h.titleExcerpt ?? '-'}
                      </span>
                    </td>
                    <td className="py-2 pr-4 max-w-[520px]">
                      <span className="truncate block" title={(h.tags ?? []).join('、')}>
                        {(h.tags ?? []).join('、')}
                      </span>
                    </td>
                    <td className="py-2 pr-4">{h.model ?? '-'}</td>
                    <td className="py-2 pr-4">{typeof h.latencyMs === 'number' ? `${h.latencyMs}ms` : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default MultiLabelForm;
