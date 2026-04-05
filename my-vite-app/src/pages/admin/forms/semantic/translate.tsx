import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { HistoryPagination } from '../../../../components/admin/HistoryPagination';
import {
  adminGetTranslateConfig,
  adminListTranslateHistory,
  adminUpsertTranslateConfig,
  type Page,
  type SemanticTranslateConfigDTO,
  type SemanticTranslateHistoryDTO,
} from '../../../../services/translateAdminService';
import { adminBatchGetPrompts, adminUpdatePromptContent } from '../../../../services/promptsAdminService';
import {
  adminDeleteSupportedLanguage,
  adminUpdateSupportedLanguage,
  adminUpsertSupportedLanguage,
  listSupportedLanguages,
  normalizeSupportedLanguages,
  type SupportedLanguageDTO,
} from '../../../../services/supportedLanguagesService';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import PromptContentCard, { type PromptContentDraft } from '../../../../components/admin/PromptContentCard';
import SemanticEditActionBar from './SemanticEditActionBar';
import { loadPromptDraftState, validatePromptRangeFields } from './semanticConfigShared';

type FormState = {
  enabled: boolean;
  promptCode: string;
  model: string;
  providerId: string;
  temperature: string;
  topP: string;
  enableThinking: boolean;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
  allowedTargetLanguageCodes: string[];
};
  
function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): SemanticTranslateConfigDTO {
  return {
    enabled: true,
    promptCode: 'TRANSLATE_GEN',
    temperature: 0.2,
    topP: 0.4,
    enableThinking: false,
    maxContentChars: 8000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
    allowedTargetLanguages: [],
  };
}

function toFormState(cfg?: SemanticTranslateConfigDTO | null): FormState {
  const codes = (cfg?.allowedTargetLanguages?.length ? cfg.allowedTargetLanguages : []).filter(Boolean);
  return {
    enabled: Boolean(cfg?.enabled),
    promptCode: cfg?.promptCode ?? 'TRANSLATE_GEN',
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    enableThinking: Boolean(cfg?.enableThinking),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    historyEnabled: Boolean(cfg?.historyEnabled),
    historyKeepDays: cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined ? '' : String(cfg.historyKeepDays),
    historyKeepRows: cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined ? '' : String(cfg.historyKeepRows),
    allowedTargetLanguageCodes: Array.from(new Set(codes.map((x) => String(x ?? '').trim()).filter((x) => x.length > 0))),
  };
}

function validateForm(s: FormState): string[] {
  const errors = validatePromptRangeFields(s, 100000);
  const hkd = parseOptionalNumber(s.historyKeepDays);
  if (hkd !== undefined && (!Number.isInteger(hkd) || hkd < 1)) errors.push('historyKeepDays 必须为正整数');
  const hkr = parseOptionalNumber(s.historyKeepRows);
  if (hkr !== undefined && (!Number.isInteger(hkr) || hkr < 1)) errors.push('historyKeepRows 必须为正整数');
  return errors;
}

function buildPayload(s: FormState) {
  const maxContentChars = parseOptionalNumber(s.maxContentChars);
  const historyKeepDays = parseOptionalNumber(s.historyKeepDays);
  const historyKeepRows = parseOptionalNumber(s.historyKeepRows);

  const allowedTargetLanguages = Array.from(new Set((s.allowedTargetLanguageCodes ?? []).map((x) => String(x ?? '').trim()).filter((x) => x.length > 0)));

  return {
    enabled: s.enabled,
    promptCode: s.promptCode,
    maxContentChars: maxContentChars === undefined ? 8000 : Math.trunc(maxContentChars),
    historyEnabled: s.historyEnabled,
    historyKeepDays: historyKeepDays === undefined ? null : Math.trunc(historyKeepDays),
    historyKeepRows: historyKeepRows === undefined ? null : Math.trunc(historyKeepRows),
    allowedTargetLanguages,
  };
}

const TranslateForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

  const [promptLoadError, setPromptLoadError] = useState<string | null>(null);
  const [committedPromptDraft, setCommittedPromptDraft] = useState<PromptContentDraft | null>(null);
  const [promptDraft, setPromptDraft] = useState<PromptContentDraft | null>(null);

  const [editing, setEditing] = useState(false);
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [form, setForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [supportedLanguages, setSupportedLanguages] = useState<SupportedLanguageDTO[]>([]);
  const [focusedLanguageCode, setFocusedLanguageCode] = useState('');
  const [langPanelMode, setLangPanelMode] = useState<'idle' | 'create'>('idle');
  const [langEditor, setLangEditor] = useState<{ languageCode: string; displayName: string }>({ languageCode: '', displayName: '' });
  const [langRowDrafts, setLangRowDrafts] = useState<Record<string, { languageCode: string; displayName: string }>>({});
  const [langActionLoading, setLangActionLoading] = useState(false);
  const [langActionError, setLangActionError] = useState<string | null>(null);
  const [langActionHint, setLangActionHint] = useState<string | null>(null);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);
  const [historyPageSize, setHistoryPageSize] = useState(20);
  const [historyPage, setHistoryPage] = useState<Page<SemanticTranslateHistoryDTO> | null>(null);

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0;
  const formHasUnsavedChanges = useMemo(() => JSON.stringify(form) !== JSON.stringify(committedForm), [form, committedForm]);
  const promptHasUnsavedChanges = useMemo(
    () => JSON.stringify(promptDraft) !== JSON.stringify(committedPromptDraft),
    [promptDraft, committedPromptDraft],
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

  const loadHistory = useCallback(
    async (pageNo: number, pageSize?: number) => {
      setHistoryLoading(true);
      setHistoryError(null);
      try {
        const size = pageSize ?? historyPageSize;
        const p = await adminListTranslateHistory({ page: pageNo, size });
        setHistoryPage(p);
        setHistoryPageNo(pageNo);
      } catch (e: unknown) {
        setHistoryError(e instanceof Error ? e.message : String(e));
      } finally {
        setHistoryLoading(false);
      }
    },
    [historyPageSize],
  );

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    setPromptLoadError(null);
    try {
      const cfg = await adminGetTranslateConfig();
      const merged = cfg.promptCode ? { ...defaultConfig(), ...cfg } : { ...defaultConfig(), ...cfg, promptCode: defaultConfig().promptCode };
      const next = toFormState(merged);
      setCommittedForm(next);
      setForm(next);
      setEditing(false);

      await loadPromptDraftState(next.promptCode, setPromptLoadError, setCommittedPromptDraft, setPromptDraft);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
    void loadHistory(0);
  }, [loadConfig, loadHistory]);

  const loadSupportedLanguages = useCallback(async () => {
    try {
      const langs = await listSupportedLanguages();
      setSupportedLanguages(normalizeSupportedLanguages(langs));
    } catch {
      setSupportedLanguages([]);
    }
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const langs = await listSupportedLanguages();
        if (!mounted) return;
        setSupportedLanguages(normalizeSupportedLanguages(langs));
      } catch {
        if (!mounted) return;
        setSupportedLanguages([]);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const focusedLanguage = useMemo(() => {
    const code = String(focusedLanguageCode ?? '').trim();
    if (!code) return null;
    return supportedLanguages.find((x) => x.languageCode === code) ?? null;
  }, [focusedLanguageCode, supportedLanguages]);

  const languageListItems = useMemo(() => {
    const selected = Array.from(
      new Set((form.allowedTargetLanguageCodes ?? []).map((x) => String(x ?? '').trim()).filter((x) => x.length > 0)),
    );
    const supportedSet = new Set((supportedLanguages ?? []).map((x) => x.languageCode));
    const unknown = selected
      .filter((code) => !supportedSet.has(code))
      .map((code) => ({ languageCode: code, displayName: code, known: false }));
    const known = (supportedLanguages ?? []).map((x) => ({ languageCode: x.languageCode, displayName: x.displayName, known: true }));
    return [...unknown, ...known];
  }, [form.allowedTargetLanguageCodes, supportedLanguages]);

  useEffect(() => {
    setLangRowDrafts((prev) => {
      const next = { ...prev };
      for (const item of languageListItems) {
        if (!next[item.languageCode]) {
          next[item.languageCode] = { languageCode: item.languageCode, displayName: item.displayName };
        }
      }
      return next;
    });
  }, [languageListItems]);

  const toggleAllowedTargetLanguageCode = useCallback((languageCode: string) => {
    const code = String(languageCode ?? '').trim();
    if (!code) return;
    setForm((p) => {
      const next = new Set(p.allowedTargetLanguageCodes ?? []);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return { ...p, allowedTargetLanguageCodes: Array.from(next) };
    });
  }, []);

  const saveLanguageRow = useCallback(
    async (originalCode: string) => {
      if (!editing || langActionLoading) return;
      const draft = langRowDrafts[originalCode];
      if (!draft) return;
      const nextCode = draft.languageCode.trim();
      const nextName = draft.displayName.trim();
      if (!nextCode || !nextName) {
        setLangActionError('languageCode 与 displayName 不能为空');
        return;
      }

      setLangActionLoading(true);
      setLangActionError(null);
      setLangActionHint(null);
      try {
        const isKnown = (supportedLanguages ?? []).some((x) => x.languageCode === originalCode);
        if (isKnown) {
          await adminUpdateSupportedLanguage(originalCode, { languageCode: nextCode, displayName: nextName });
        } else {
          await adminUpsertSupportedLanguage({ languageCode: nextCode, displayName: nextName });
        }
        await loadSupportedLanguages();
        if (originalCode !== nextCode) {
          setForm((p) => ({
            ...p,
            allowedTargetLanguageCodes: Array.from(
              new Set((p.allowedTargetLanguageCodes ?? []).map((c) => (c === originalCode ? nextCode : c))),
            ),
          }));
          setLangRowDrafts((p) => {
            const { [originalCode]: _removed, ...rest } = p;
            return { ...rest, [nextCode]: { languageCode: nextCode, displayName: nextName } };
          });
        } else {
          setLangRowDrafts((p) => ({ ...p, [originalCode]: { languageCode: nextCode, displayName: nextName } }));
        }
        setFocusedLanguageCode(nextCode);
        setLangActionHint('保存成功');
      } catch (e: unknown) {
        setLangActionError(e instanceof Error ? e.message : String(e));
      } finally {
        setLangActionLoading(false);
      }
    },
    [editing, langActionLoading, langRowDrafts, supportedLanguages, loadSupportedLanguages],
  );

  const onSave = useCallback(async () => {
    if (saving || !canSave || !hasUnsavedChanges) return;
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertTranslateConfig(payload);

      let promptUpdateErr: string | null = null;
      if (promptDraft && promptHasUnsavedChanges) {
        try {
          await adminUpdatePromptContent(form.promptCode, {
            systemPrompt: promptDraft.systemPrompt,
            userPromptTemplate: promptDraft.userPromptTemplate,
          });
          setCommittedPromptDraft(promptDraft);
        } catch (e: unknown) {
          promptUpdateErr = e instanceof Error ? e.message : String(e);
        }
      }

      const merged = { ...defaultConfig(), ...saved };
      const next = toFormState(merged);
      setCommittedForm(next);
      setForm(next);
      setEditing(false);
      if (promptUpdateErr) {
        setError(`配置已保存，但提示词保存失败：${promptUpdateErr}`);
      } else {
        setSavedHint('保存成功');
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [saving, canSave, hasUnsavedChanges, form, promptDraft, promptHasUnsavedChanges]);

  const totalPages = useMemo(() => {
    const total = historyPage?.totalElements ?? 0;
    const size = historyPage?.size ?? 20;
    return size > 0 ? Math.ceil(total / size) : 0;
  }, [historyPage]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">翻译配置</h3>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-700">自动翻译：</span>
              <select
                value={form.enabled ? 'true' : 'false'}
                disabled={!editing}
                onChange={(e) => {
                  if (!editing) return;
                  setForm((p) => ({ ...p, enabled: e.target.value === 'true' }));
                  setSavedHint(null);
                }}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.enabled ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
              >
                <option value="true" className="text-green-600">
                  开启
                </option>
                <option value="false" className="text-red-600">
                  关闭
                </option>
              </select>
            <span className="text-sm font-medium text-gray-700 ml-2">启用深度思考：</span>
            <select
              value={form.enableThinking ? 'true' : 'false'}
              disabled={!editing}
              onChange={(e) => {
                if (!editing) return;
                setForm((p) => ({ ...p, enableThinking: e.target.value === 'true' }));
                setSavedHint(null);
              }}
              className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                form.enableThinking ? 'text-purple-700 border-purple-200 bg-white' : 'text-gray-700 border-gray-200 bg-white'
              } disabled:opacity-60 disabled:bg-gray-100`}
            >
              <option value="false" className="text-gray-700">
                关闭
              </option>
              <option value="true" className="text-purple-700">
                开启
              </option>
            </select>
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
        {formErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {formErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 gap-4 items-start xl:grid-cols-3 xl:gap-x-2">
          <div className="space-y-3 xl:pr-[100px]">
            <div className="space-y-3">
              <div>
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

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <div className="text-sm font-medium text-gray-700 mb-1">温度</div>
                  <input
                    className="w-full rounded border px-3 py-2 border-gray-300"
                    value={form.temperature}
                    disabled={!editing}
                    onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
                    placeholder="例如 0.2"
                  />
                </div>

                <div>
                  <div className="text-sm font-medium text-gray-700 mb-1">TOP-P</div>
                  <input
                    className="w-full rounded border px-3 py-2 border-gray-300"
                    value={form.topP}
                    disabled={!editing}
                    onChange={(e) => setForm((p) => ({ ...p, topP: e.target.value }))}
                    placeholder="例如 0.4"
                  />
                </div>

                <div>
                  <div className="text-sm font-medium text-gray-700 mb-1">上下文长度</div>
                  <input
                    className="w-full rounded border px-3 py-2 border-gray-300"
                    value={form.maxContentChars}
                    disabled={!editing}
                    onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
                    placeholder="默认 8000"
                  />
                </div>

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
          </div>
          <div className="space-y-3 xl:-ml-[100px]">
            <PromptContentCard
              title="翻译提示词"
              draft={promptDraft}
              editing={editing}
              onChange={(next) => {
                setPromptDraft(next);
                setSavedHint(null);
              }}
              hint={promptLoadError ?? '引用 prompts 表中的 prompt_code'}
            />
          </div>

          <div className="space-y-3">
            <div className="text-sm font-medium text-gray-700">语言支持列表</div>
            <div
              className="w-full rounded border border-gray-300 bg-white min-h-[160px] max-h-[340px] overflow-auto"
              role="listbox"
              aria-multiselectable="true"
            >
              {languageListItems.length === 0 ? (
                <div className="px-3 py-2 text-sm text-gray-500">暂无语言</div>
              ) : (
                languageListItems.map((item) => {
                  const isSelected = (form.allowedTargetLanguageCodes ?? []).includes(item.languageCode);
                  const isFocused = focusedLanguageCode === item.languageCode;
                  const rowClassName = [
                    'grid grid-cols-[36px_1fr] gap-2 px-3 py-2 border-b last:border-b-0',
                    editing ? 'hover:bg-gray-50' : '',
                    isFocused ? 'ring-1 ring-inset ring-blue-400' : '',
                  ]
                    .filter(Boolean)
                    .join(' ');
                  const draft = langRowDrafts[item.languageCode] ?? { languageCode: item.languageCode, displayName: item.displayName };
                  return (
                    <div
                      key={item.languageCode}
                      className={rowClassName}
                      onMouseDown={() => setFocusedLanguageCode(item.languageCode)}
                      onFocusCapture={() => setFocusedLanguageCode(item.languageCode)}
                      onBlurCapture={(e) => {
                        if (e.currentTarget.contains(e.relatedTarget as Node)) return;
                        void saveLanguageRow(item.languageCode);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') void saveLanguageRow(item.languageCode);
                        if (e.key === 'Escape') {
                          setLangRowDrafts((p) => ({
                            ...p,
                            [item.languageCode]: { languageCode: item.languageCode, displayName: item.displayName },
                          }));
                        }
                      }}
                    >
                      <div className="pt-0.5">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          disabled={!editing}
                          onChange={() => toggleAllowedTargetLanguageCode(item.languageCode)}
                        />
                      </div>
                      <div className="min-w-0 grid grid-cols-1 md:grid-cols-[2fr_0.5fr] gap-2">
                        <input
                          className="w-full min-w-0 rounded border px-2 py-1.5 border-gray-300 text-sm disabled:bg-gray-100"
                          value={draft.displayName}
                          disabled={!editing || langActionLoading}
                          onChange={(e) =>
                            setLangRowDrafts((p) => ({ ...p, [item.languageCode]: { ...draft, displayName: e.target.value } }))
                          }
                          placeholder="语言名称"
                        />
                        <input
                          className="w-full min-w-0 rounded border px-2 py-1.5 border-gray-300 text-sm disabled:bg-gray-100"
                          value={draft.languageCode}
                          disabled={!editing || langActionLoading}
                          onChange={(e) =>
                            setLangRowDrafts((p) => ({ ...p, [item.languageCode]: { ...draft, languageCode: e.target.value } }))
                          }
                          placeholder={item.known ? 'languageCode' : 'languageCode（未收录）'}
                        />
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            <div className="w-full min-w-0 rounded border border-gray-200 bg-gray-50 p-4 space-y-3">
              <div className="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    className="rounded bg-green-600 text-white px-3 py-2 text-sm disabled:bg-green-300"
                    disabled={!editing || langActionLoading}
                    onClick={() => {
                      setLangActionError(null);
                      setLangActionHint(null);
                      setLangPanelMode('create');
                      setLangEditor({ languageCode: '', displayName: '' });
                    }}
                  >
                    新增
                  </button>
                  <button
                    type="button"
                    className="rounded bg-red-600 text-white px-3 py-2 text-sm disabled:bg-red-300"
                    disabled={!editing || langActionLoading || !focusedLanguage}
                    onClick={() => {
                      if (!focusedLanguage) {
                        setLangActionError('请先在上方列表中点击要删除的语言');
                        return;
                      }
                      const ok = window.confirm(`确认删除语言：${focusedLanguage.displayName} (${focusedLanguage.languageCode}) ?`);
                      if (!ok) return;
                      setLangActionLoading(true);
                      setLangActionError(null);
                      setLangActionHint(null);
                      void (async () => {
                        try {
                          await adminDeleteSupportedLanguage(focusedLanguage.languageCode);
                          await loadSupportedLanguages();
                          setForm((p) => ({
                            ...p,
                            allowedTargetLanguageCodes: (p.allowedTargetLanguageCodes ?? []).filter((c) => c !== focusedLanguage.languageCode),
                          }));
                          setFocusedLanguageCode('');
                          setLangPanelMode('idle');
                          setLangActionHint('删除成功');
                        } catch (e: unknown) {
                          setLangActionError(e instanceof Error ? e.message : String(e));
                        } finally {
                          setLangActionLoading(false);
                        }
                      })();
                    }}
                  >
                    删除
                  </button>
                </div>
              {langActionHint ? <div className="text-sm text-green-700">{langActionHint}</div> : null}
              {langActionError ? <div className="text-sm text-red-700">{langActionError}</div> : null}

              {langPanelMode !== 'idle' ? (
                <div className="rounded border border-gray-200 bg-white p-3 space-y-3">
                  <div className="text-sm font-medium text-gray-700">新增语言</div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <div className="text-sm text-gray-600 mb-1">语言代码</div>
                      <input
                        className="w-full rounded border px-3 py-2 border-gray-300 text-sm"
                        value={langEditor.languageCode}
                        disabled={langActionLoading}
                        onChange={(e) => setLangEditor((p) => ({ ...p, languageCode: e.target.value }))}
                        placeholder="例如 en / zh-CN"
                      />
                    </div>
                    <div>
                      <div className="text-sm text-gray-600 mb-1">语言名称</div>
                      <input
                        className="w-full rounded border px-3 py-2 border-gray-300 text-sm"
                        value={langEditor.displayName}
                        disabled={langActionLoading}
                        onChange={(e) => setLangEditor((p) => ({ ...p, displayName: e.target.value }))}
                        placeholder="例如 英语（English）"
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
                      disabled={langActionLoading}
                      onClick={() => {
                        setLangPanelMode('idle');
                        setLangActionError(null);
                      }}
                    >
                      取消
                    </button>
                    <button
                      type="button"
                      className="rounded bg-gray-900 text-white px-3 py-1.5 text-sm disabled:bg-gray-400"
                      disabled={langActionLoading || !langEditor.languageCode.trim() || !langEditor.displayName.trim()}
                      onClick={() => {
                        setLangActionLoading(true);
                        setLangActionError(null);
                        setLangActionHint(null);
                        void (async () => {
                          try {
                            const nextCode = langEditor.languageCode.trim();
                            const nextName = langEditor.displayName.trim();
                            if (!nextCode || !nextName) {
                              setLangActionError('languageCode 与 displayName 不能为空');
                              return;
                            }
                            await adminUpsertSupportedLanguage({ languageCode: nextCode, displayName: nextName });
                            await loadSupportedLanguages();
                            setFocusedLanguageCode(nextCode);
                            setLangPanelMode('idle');
                            setLangActionHint('新增成功');
                          } catch (e: unknown) {
                            setLangActionError(e instanceof Error ? e.message : String(e));
                          } finally {
                            setLangActionLoading(false);
                          }
                        })();
                      }}
                    >
                      {langActionLoading ? '提交中...' : '提交'}
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">翻译历史</h3>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
            disabled={historyLoading}
            onClick={() => void loadHistory(historyPageNo)}
          >
            刷新
          </button>
        </div>

        {historyError ? <div className="text-sm text-red-700">{historyError}</div> : null}
        {historyLoading ? <div className="text-sm text-gray-600">加载中...</div> : null}

        {!historyLoading && (historyPage?.content?.length ?? 0) === 0 ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600">
            暂无翻译历史
          </div>
        ) : null}

        {(historyPage?.content?.length ?? 0) > 0 ? (
          <div className="overflow-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b">
                  <th className="py-2 pr-4">时间</th>
                  <th className="py-2 pr-4">类型/ID</th>
                  <th className="py-2 pr-4">目标语言</th>
                  <th className="py-2 pr-4">模型</th>
                  <th className="py-2 pr-4">耗时</th>
                  <th className="py-2 pr-4">摘要</th>
                </tr>
              </thead>
              <tbody>
                {(historyPage?.content ?? []).map((h) => (
                  <tr key={h.id} className="border-b last:border-b-0 align-top">
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-600">{new Date(h.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4 whitespace-nowrap">
                      <span className="text-gray-900">{h.sourceType}</span>
                      <span className="text-gray-500"> #{h.sourceId}</span>
                    </td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.targetLang}</td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.model || '（默认）'}</td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.latencyMs ? `${h.latencyMs}ms` : '-'}</td>
                    <td className="py-2 pr-4 text-gray-700">
                      <div className="max-w-[520px]">
                        {h.sourceTitleExcerpt ? <div className="text-xs text-gray-500">标题：{h.sourceTitleExcerpt}</div> : null}
                        {h.sourceContentExcerpt ? <div className="text-xs text-gray-500">原文：{h.sourceContentExcerpt}</div> : null}
                        {h.translatedTitle ? <div className="text-xs text-gray-700">译题：{h.translatedTitle}</div> : null}
                      </div>
                    </td>
                  </tr>
                ))}
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
    </div>
  );
};

export default TranslateForm;
