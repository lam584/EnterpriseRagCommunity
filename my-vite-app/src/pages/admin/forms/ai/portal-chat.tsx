import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminGetPortalChatConfig, adminUpsertPortalChatConfig, type PortalChatConfigDTO } from '../../../../services/portalChatAdminService';
import { adminBatchGetPrompts, adminUpdatePromptContent, type PromptContentDTO } from '../../../../services/promptsAdminService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import PromptContentCard, { type PromptContentDraft } from '../../../../components/admin/PromptContentCard';

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function clampNumber(v: number, min: number, max: number): number {
  if (!Number.isFinite(v)) return min;
  return Math.min(max, Math.max(min, v));
}

type AssistantFormState = {
  providerId: string;
  model: string;
  temperature: string;
  topP: string;
  historyLimit: string;
  defaultDeepThink: boolean;
  defaultUseRag: boolean;
  ragTopK: string;
  defaultStream: boolean;
  systemPromptCode: string;
  deepThinkSystemPromptCode: string;
};

type PostComposeFormState = {
  providerId: string;
  model: string;
  temperature: string;
  topP: string;
  chatHistoryLimit: string;
  defaultDeepThink: boolean;
  systemPromptCode: string;
  deepThinkSystemPromptCode: string;
  composeSystemPromptCode: string;
};

type FormState = {
  assistant: AssistantFormState;
  postCompose: PostComposeFormState;
};

function defaultForm(): FormState {
  return {
    assistant: {
      providerId: '',
      model: '',
      temperature: '',
      topP: '',
      historyLimit: '20',
      defaultDeepThink: false,
      defaultUseRag: true,
      ragTopK: '6',
      defaultStream: true,
      systemPromptCode: 'PORTAL_CHAT_ASSISTANT',
      deepThinkSystemPromptCode: 'PORTAL_CHAT_ASSISTANT_DEEP_THINK',
    },
    postCompose: {
      providerId: '',
      model: '',
      temperature: '',
      topP: '',
      chatHistoryLimit: '20',
      defaultDeepThink: false,
      systemPromptCode: 'PORTAL_POST_COMPOSE',
      deepThinkSystemPromptCode: 'PORTAL_POST_COMPOSE_DEEP_THINK',
      composeSystemPromptCode: 'PORTAL_POST_COMPOSE_PROTOCOL',
    },
  };
}

function toFormState(dto?: PortalChatConfigDTO | null): FormState {
  const d = defaultForm();
  const a = dto?.assistantChat ?? null;
  const p = dto?.postComposeAssistant ?? null;
  return {
    assistant: {
      providerId: String(a?.providerId ?? ''),
      model: String(a?.model ?? ''),
      temperature: a?.temperature == null ? '' : String(a.temperature),
      topP: a?.topP == null ? '' : String(a.topP),
      historyLimit: a?.historyLimit == null ? d.assistant.historyLimit : String(a.historyLimit),
      defaultDeepThink: Boolean(a?.defaultDeepThink ?? d.assistant.defaultDeepThink),
      defaultUseRag: Boolean(a?.defaultUseRag ?? d.assistant.defaultUseRag),
      ragTopK: a?.ragTopK == null ? d.assistant.ragTopK : String(a.ragTopK),
      defaultStream: Boolean(a?.defaultStream ?? d.assistant.defaultStream),
      systemPromptCode: String(a?.systemPromptCode ?? d.assistant.systemPromptCode),
      deepThinkSystemPromptCode: String(a?.deepThinkSystemPromptCode ?? d.assistant.deepThinkSystemPromptCode),
    },
    postCompose: {
      providerId: String(p?.providerId ?? ''),
      model: String(p?.model ?? ''),
      temperature: p?.temperature == null ? '' : String(p.temperature),
      topP: p?.topP == null ? '' : String(p.topP),
      chatHistoryLimit: p?.chatHistoryLimit == null ? d.postCompose.chatHistoryLimit : String(p.chatHistoryLimit),
      defaultDeepThink: Boolean(p?.defaultDeepThink ?? d.postCompose.defaultDeepThink),
      systemPromptCode: String(p?.systemPromptCode ?? d.postCompose.systemPromptCode),
      deepThinkSystemPromptCode: String(p?.deepThinkSystemPromptCode ?? d.postCompose.deepThinkSystemPromptCode),
      composeSystemPromptCode: String(p?.composeSystemPromptCode ?? d.postCompose.composeSystemPromptCode),
    },
  };
}

function toPromptDraft(dto?: PromptContentDTO | null): PromptContentDraft {
  return {
    name: dto?.name ?? '',
    systemPrompt: dto?.systemPrompt ?? '',
    userPromptTemplate: dto?.userPromptTemplate ?? '',
  };
}

function listPromptCodes(s: FormState): string[] {
  const codes = [
    s.assistant.systemPromptCode,
    s.assistant.deepThinkSystemPromptCode,
    s.postCompose.systemPromptCode,
    s.postCompose.deepThinkSystemPromptCode,
    s.postCompose.composeSystemPromptCode,
  ];
  return Array.from(new Set(codes.map((x) => String(x ?? '').trim()).filter((x) => x.length > 0)));
}

function validateForm(s: FormState): string[] {
  const errors: string[] = [];

  const at = parseOptionalNumber(s.assistant.temperature);
  if (at !== undefined && (at < 0 || at > 2)) errors.push('智能助手 temperature 需在 [0, 2] 范围内');
  const ap = parseOptionalNumber(s.assistant.topP);
  if (ap !== undefined && (ap < 0 || ap > 1)) errors.push('智能助手 topP 需在 [0, 1] 范围内');
  const ah = parseOptionalNumber(s.assistant.historyLimit);
  if (ah !== undefined && (!Number.isInteger(ah) || ah < 1 || ah > 200)) errors.push('智能助手 上下文长度需为 1~200 的整数');
  const ak = parseOptionalNumber(s.assistant.ragTopK);
  if (ak !== undefined && (!Number.isInteger(ak) || ak < 1 || ak > 50)) errors.push('智能助手 ragTopK 需为 1~50 的整数');
  if (!s.assistant.systemPromptCode.trim()) errors.push('智能助手 systemPromptCode 不能为空');
  if (!s.assistant.deepThinkSystemPromptCode.trim()) errors.push('智能助手 deepThinkSystemPromptCode 不能为空');

  const pt = parseOptionalNumber(s.postCompose.temperature);
  if (pt !== undefined && (pt < 0 || pt > 2)) errors.push('AI 发帖助手 temperature 需在 [0, 2] 范围内');
  const pp = parseOptionalNumber(s.postCompose.topP);
  if (pp !== undefined && (pp < 0 || pp > 1)) errors.push('AI 发帖助手 topP 需在 [0, 1] 范围内');
  const ph = parseOptionalNumber(s.postCompose.chatHistoryLimit);
  if (ph !== undefined && (!Number.isInteger(ph) || ph < 1 || ph > 200)) errors.push('AI 发帖助手 上下文长度需为 1~200 的整数');
  if (!s.postCompose.systemPromptCode.trim()) errors.push('AI 发帖助手 systemPromptCode 不能为空');
  if (!s.postCompose.deepThinkSystemPromptCode.trim()) errors.push('AI 发帖助手 deepThinkSystemPromptCode 不能为空');
  if (!s.postCompose.composeSystemPromptCode.trim()) errors.push('AI 发帖助手 composeSystemPromptCode 不能为空');

  return errors;
}

function buildPayload(s: FormState): PortalChatConfigDTO {
  const at = parseOptionalNumber(s.assistant.temperature);
  const ap = parseOptionalNumber(s.assistant.topP);
  const ah = parseOptionalNumber(s.assistant.historyLimit);
  const ak = parseOptionalNumber(s.assistant.ragTopK);

  const pt = parseOptionalNumber(s.postCompose.temperature);
  const pp = parseOptionalNumber(s.postCompose.topP);
  const ph = parseOptionalNumber(s.postCompose.chatHistoryLimit);

  return {
    assistantChat: {
      providerId: s.assistant.providerId.trim() ? s.assistant.providerId.trim() : null,
      model: s.assistant.model.trim() ? s.assistant.model.trim() : null,
      temperature: at === undefined ? null : clampNumber(at, 0, 2),
      topP: ap === undefined ? null : clampNumber(ap, 0, 1),
      historyLimit: ah === undefined ? null : Math.trunc(ah),
      defaultDeepThink: s.assistant.defaultDeepThink,
      defaultUseRag: s.assistant.defaultUseRag,
      ragTopK: ak === undefined ? null : Math.trunc(ak),
      defaultStream: s.assistant.defaultStream,
      systemPromptCode: s.assistant.systemPromptCode,
      deepThinkSystemPromptCode: s.assistant.deepThinkSystemPromptCode,
    },
    postComposeAssistant: {
      providerId: s.postCompose.providerId.trim() ? s.postCompose.providerId.trim() : null,
      model: s.postCompose.model.trim() ? s.postCompose.model.trim() : null,
      temperature: pt === undefined ? null : clampNumber(pt, 0, 2),
      topP: pp === undefined ? null : clampNumber(pp, 0, 1),
      chatHistoryLimit: ph === undefined ? null : Math.trunc(ph),
      defaultDeepThink: s.postCompose.defaultDeepThink,
      systemPromptCode: s.postCompose.systemPromptCode,
      deepThinkSystemPromptCode: s.postCompose.deepThinkSystemPromptCode,
      composeSystemPromptCode: s.postCompose.composeSystemPromptCode,
    },
  };
}

const PortalChatConfigForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);

  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

  const [form, setForm] = useState<FormState>(() => defaultForm());
  const [committedForm, setCommittedForm] = useState<FormState>(() => defaultForm());

  const [promptLoadError, setPromptLoadError] = useState<string | null>(null);
  const [promptDrafts, setPromptDrafts] = useState<Record<string, PromptContentDraft | null>>({});
  const [committedPromptDrafts, setCommittedPromptDrafts] = useState<Record<string, PromptContentDraft | null>>({});

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = isEditing && formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    const a = form.assistant;
    const b = committedForm.assistant;
    const c = form.postCompose;
    const d = committedForm.postCompose;
    const formChanged =
      a.providerId !== b.providerId ||
      a.model !== b.model ||
      a.temperature !== b.temperature ||
      a.topP !== b.topP ||
      a.historyLimit !== b.historyLimit ||
      a.defaultDeepThink !== b.defaultDeepThink ||
      a.defaultUseRag !== b.defaultUseRag ||
      a.ragTopK !== b.ragTopK ||
      a.defaultStream !== b.defaultStream ||
      a.systemPromptCode !== b.systemPromptCode ||
      a.deepThinkSystemPromptCode !== b.deepThinkSystemPromptCode ||
      c.providerId !== d.providerId ||
      c.model !== d.model ||
      c.temperature !== d.temperature ||
      c.topP !== d.topP ||
      c.chatHistoryLimit !== d.chatHistoryLimit ||
      c.defaultDeepThink !== d.defaultDeepThink ||
      c.systemPromptCode !== d.systemPromptCode ||
      c.deepThinkSystemPromptCode !== d.deepThinkSystemPromptCode ||
      c.composeSystemPromptCode !== d.composeSystemPromptCode
      ;

    const codes = listPromptCodes(form).sort();
    const promptChanged =
      JSON.stringify(codes.map((code) => promptDrafts[code] ?? null)) !==
      JSON.stringify(codes.map((code) => committedPromptDrafts[code] ?? null));

    return formChanged || promptChanged;
  }, [form, committedForm, promptDrafts, committedPromptDrafts]);

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
      const cfg = await adminGetPortalChatConfig();
      const next = toFormState(cfg);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);

      try {
        const codes = listPromptCodes(next);
        const resp = await adminBatchGetPrompts(codes);
        const map: Record<string, PromptContentDraft | null> = {};
        for (const code of codes) map[code] = null;
        for (const dto of resp.prompts ?? []) {
          if (!dto?.promptCode) continue;
          map[dto.promptCode] = toPromptDraft(dto);
        }
        setPromptDrafts(map);
        setCommittedPromptDrafts(map);
      } catch (e: unknown) {
        setPromptLoadError(e instanceof Error ? e.message : String(e));
        setPromptDrafts({});
        setCommittedPromptDrafts({});
      }
    } catch (e) {
      const next = defaultForm();
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      setError(e instanceof Error ? e.message : String(e));
      setPromptDrafts({});
      setCommittedPromptDrafts({});
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  const cancelEdit = useCallback(() => {
    setForm(committedForm);
    setPromptDrafts(committedPromptDrafts);
    setIsEditing(false);
    setError(null);
    setSavedHint('已放弃修改');
  }, [committedForm, committedPromptDrafts]);

  const save = useCallback(async () => {
    const errs = validateForm(form);
    if (errs.length > 0) {
      setError(errs.join('；'));
      return;
    }
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertPortalChatConfig(payload);

      const codes = listPromptCodes(form).sort();
      const changedCodes = codes.filter((code) => JSON.stringify(promptDrafts[code] ?? null) !== JSON.stringify(committedPromptDrafts[code] ?? null));

      const promptErrors: string[] = [];
      for (const code of changedCodes) {
        const draft = promptDrafts[code];
        if (!draft) continue;
        try {
          await adminUpdatePromptContent(code, { systemPrompt: draft.systemPrompt, userPromptTemplate: draft.userPromptTemplate });
        } catch (e: unknown) {
          promptErrors.push(e instanceof Error ? e.message : String(e));
        }
      }
      if (changedCodes.length && promptErrors.length === 0) {
        setCommittedPromptDrafts(promptDrafts);
      }

      const next = toFormState(saved);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      if (promptErrors.length) {
        setError(`配置已保存，但提示词保存失败：${promptErrors[0]}`);
      } else {
        setSavedHint('已保存并生效');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [form, promptDrafts, committedPromptDrafts]);

  return (
    <div className="space-y-3">
      <div className="bg-white rounded-lg shadow p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">前台对话配置</h3>
            <div className="text-sm text-gray-500">管理“智能助手 / AI 发帖助手”的默认模型与提示词参数。</div>
          </div>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                void loadConfig();
                setError(null);
              }}
              className="rounded border px-3 py-1.5 disabled:opacity-60"
              disabled={loading || saving}
              title="从后端重新加载配置（会覆盖未保存的修改）"
            >
              刷新
            </button>

            {!isEditing ? (
              <button
                type="button"
                onClick={() => {
                  setIsEditing(true);
                  setSavedHint(null);
                  setError(null);
                }}
                className="rounded bg-blue-600 text-white px-3 py-1.5 disabled:opacity-60"
                disabled={loading || saving}
                title="进入编辑模式"
              >
                编辑配置
              </button>
            ) : (
              <>
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="rounded border px-3 py-1.5 disabled:opacity-60"
                  disabled={loading || saving}
                  title="放弃未保存的修改，并恢复到最近一次加载/保存的配置"
                >
                  放弃修改
                </button>
                <button
                  type="button"
                  onClick={save}
                  className="rounded bg-blue-600 text-white px-3 py-1.5 disabled:opacity-60"
                  disabled={!canSave}
                  title={formErrors.length ? formErrors.join('\n') : '保存并生效'}
                >
                  {saving ? '保存中…' : '保存配置'}
                </button>
              </>
            )}
          </div>
        </div>

        {savedHint ? (
          <div className="rounded border border-green-200 bg-green-50 text-green-700 px-3 py-2 text-sm">{savedHint}</div>
        ) : null}

        {error ? <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div> : null}

        {!isEditing && hasUnsavedChanges ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 text-yellow-800 px-3 py-2 text-sm">
            当前表单与已生效配置不一致（可能来自自动回退/接口默认值）。如需修改请点击「编辑配置」。
          </div>
        ) : null}
      </div>

      <div className="bg-white rounded-lg shadow p-3 space-y-3">
        <div>
          <div className="text-lg font-semibold">智能助手</div>
          <div className="text-sm text-gray-500">对应前台“智能助手”对话页。</div>
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-[360px_1fr]">
          <div className="space-y-2">
            <div className="text-sm font-semibold text-gray-700">默认模型</div>
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={form.assistant.providerId}
              autoOptionLabel="自动（均衡负载）"
              model={form.assistant.model}
              disabled={!isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setForm((p) => ({ ...p, assistant: { ...p.assistant, providerId: next.providerId, model: next.model } }));
                setSavedHint(null);
              }}
            />

            <div>
              <div className="text-sm font-medium mb-1">温度</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：0.2（留空=不覆盖）"
                value={form.assistant.temperature}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, temperature: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">TOP-P</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：0.9（留空=不覆盖）"
                value={form.assistant.topP}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, topP: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">上下文长度</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：20"
                value={form.assistant.historyLimit}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, historyLimit: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">RAG TopK</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：6"
                value={form.assistant.ragTopK}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, ragTopK: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div className="grid grid-cols-1 gap-2">
              <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={form.assistant.defaultStream}
                  disabled={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultStream: e.target.checked } }));
                    setSavedHint(null);
                  }}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                默认流式输出
              </label>
              <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={form.assistant.defaultDeepThink}
                  disabled={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultDeepThink: e.target.checked } }));
                    setSavedHint(null);
                  }}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                默认深度思考
              </label>
              <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={form.assistant.defaultUseRag}
                  disabled={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultUseRag: e.target.checked } }));
                    setSavedHint(null);
                  }}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                默认启用 RAG
              </label>
            </div>
          </div>

          <div className="space-y-3">
            <PromptContentCard
              title="系统提示词"
              draft={promptDrafts[form.assistant.systemPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setPromptDrafts((p) => ({ ...p, [form.assistant.systemPromptCode]: next }));
                setSavedHint(null);
              }}
              hint={promptLoadError ?? undefined}
              showUserPromptTemplate={false}
            />
            <PromptContentCard
              title="系统提示词（深度思考）"
              draft={promptDrafts[form.assistant.deepThinkSystemPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setPromptDrafts((p) => ({ ...p, [form.assistant.deepThinkSystemPromptCode]: next }));
                setSavedHint(null);
              }}
              hint={promptLoadError ?? undefined}
              showUserPromptTemplate={false}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-3 space-y-3">
        <div>
          <div className="text-lg font-semibold">AI 发帖助手</div>
          <div className="text-sm text-gray-500">对应发帖页右下角的“AI 发帖助手”。</div>
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-[360px_1fr]">
          <div className="space-y-2">
            <div className="text-sm font-semibold text-gray-700">默认模型</div>
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={form.postCompose.providerId}
              autoOptionLabel="自动（均衡负载）"
              model={form.postCompose.model}
              disabled={!isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setForm((p) => ({ ...p, postCompose: { ...p.postCompose, providerId: next.providerId, model: next.model } }));
                setSavedHint(null);
              }}
            />

            <div>
              <div className="text-sm font-medium mb-1">温度</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：0.2（留空=不覆盖）"
                value={form.postCompose.temperature}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, temperature: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">TOP-P</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：0.9（留空=不覆盖）"
                value={form.postCompose.topP}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, topP: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">上下文长度</div>
              <input
                className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                placeholder="例如：20"
                value={form.postCompose.chatHistoryLimit}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, chatHistoryLimit: e.target.value } }));
                  setSavedHint(null);
                }}
              />
            </div>

            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={form.postCompose.defaultDeepThink}
                disabled={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, defaultDeepThink: e.target.checked } }));
                  setSavedHint(null);
                }}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              默认深度思考
            </label>
          </div>

          <div className="space-y-3">
            <PromptContentCard
              title="系统提示词"
              draft={promptDrafts[form.postCompose.systemPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setPromptDrafts((p) => ({ ...p, [form.postCompose.systemPromptCode]: next }));
                setSavedHint(null);
              }}
              hint={promptLoadError ?? undefined}
              showUserPromptTemplate={false}
            />
            <PromptContentCard
              title="系统提示词（深度思考）"
              draft={promptDrafts[form.postCompose.deepThinkSystemPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setPromptDrafts((p) => ({ ...p, [form.postCompose.deepThinkSystemPromptCode]: next }));
                setSavedHint(null);
              }}
              hint={promptLoadError ?? undefined}
              showUserPromptTemplate={false}
            />
            <PromptContentCard
              title="发帖助手提示词（输出协议）"
              draft={promptDrafts[form.postCompose.composeSystemPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => {
                if (!isEditing) return;
                setPromptDrafts((p) => ({ ...p, [form.postCompose.composeSystemPromptCode]: next }));
                setSavedHint(null);
              }}
              hint={promptLoadError ?? undefined}
              showUserPromptTemplate={false}
            />
          </div>
        </div>

        {formErrors.length ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 text-yellow-800 px-3 py-2 text-sm">
            <div className="font-medium mb-1">配置校验提示：</div>
            <ul className="list-disc ml-5">
              {formErrors.map((x) => (
                <li key={x}>{x}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default PortalChatConfigForm;

 
