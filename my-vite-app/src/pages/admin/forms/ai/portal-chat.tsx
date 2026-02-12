import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminGetPortalChatConfig, adminUpsertPortalChatConfig, type PortalChatConfigDTO } from '../../../../services/portalChatAdminService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';

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
  systemPrompt: string;
  deepThinkSystemPrompt: string;
};

type PostComposeFormState = {
  providerId: string;
  model: string;
  temperature: string;
  topP: string;
  chatHistoryLimit: string;
  defaultDeepThink: boolean;
  systemPrompt: string;
  deepThinkSystemPrompt: string;
  composeSystemPrompt: string;
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
      systemPrompt: '你是一个严谨、专业的中文助手。',
      deepThinkSystemPrompt: '你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。',
    },
    postCompose: {
      providerId: '',
      model: '',
      temperature: '',
      topP: '',
      chatHistoryLimit: '20',
      defaultDeepThink: false,
      systemPrompt: '你是一个严谨、专业的中文助手。',
      deepThinkSystemPrompt: '你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。',
      composeSystemPrompt:
        '你是一名发帖编辑助手。你要帮助用户完成“可发布的 Markdown 正文”，并在必要时与用户沟通确认细节。\n' +
        '你必须严格遵守以下输出协议（非常重要）：\n' +
        '1) 你只允许输出两类内容块，并且所有输出必须被包裹在其中之一：\n' +
        '   - <chat>...</chat>：与用户沟通（提问、确认、解释、澄清）。这部分只会显示在聊天窗口，不会写入正文。\n' +
        '   - <post>...</post>：帖子最终 Markdown 正文。这部分只会写入正文编辑框，不会显示在聊天窗口。\n' +
        '2) 当信息不足、需要用户确认/补充时：只输出 <chat>，不要输出 <post>。\n' +
        '3) 当你输出 <post> 时：内容必须是完整、可发布的最终 Markdown 正文；不要解释你的思考过程；不要使用```包裹正文。\n' +
        '4) 不要杜撰事实；缺少信息时在 <chat> 提问，或在 <post> 中用占位符明确标记缺失信息。\n' +
        '5) 若用户明确要求“直接写入正文/直接改写/不要提问/给出最终稿”，你必须直接输出 <post>，不要继续在 <chat> 中拉扯确认。\n' +
        '6) 标签必须使用半角尖括号：<post>/<chat>，不要转义为 &lt;post&gt;，也不要使用全角括号。\n' +
        '7) 除 <chat> 或 <post> 之外不要输出任何其他文本。\n',
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
      systemPrompt: String(a?.systemPrompt ?? d.assistant.systemPrompt),
      deepThinkSystemPrompt: String(a?.deepThinkSystemPrompt ?? d.assistant.deepThinkSystemPrompt),
    },
    postCompose: {
      providerId: String(p?.providerId ?? ''),
      model: String(p?.model ?? ''),
      temperature: p?.temperature == null ? '' : String(p.temperature),
      topP: p?.topP == null ? '' : String(p.topP),
      chatHistoryLimit: p?.chatHistoryLimit == null ? d.postCompose.chatHistoryLimit : String(p.chatHistoryLimit),
      defaultDeepThink: Boolean(p?.defaultDeepThink ?? d.postCompose.defaultDeepThink),
      systemPrompt: String(p?.systemPrompt ?? d.postCompose.systemPrompt),
      deepThinkSystemPrompt: String(p?.deepThinkSystemPrompt ?? d.postCompose.deepThinkSystemPrompt),
      composeSystemPrompt: String(p?.composeSystemPrompt ?? d.postCompose.composeSystemPrompt),
    },
  };
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
  if (!s.assistant.systemPrompt.trim()) errors.push('智能助手 systemPrompt 不能为空');
  if (!s.assistant.deepThinkSystemPrompt.trim()) errors.push('智能助手 deepThinkSystemPrompt 不能为空');

  const pt = parseOptionalNumber(s.postCompose.temperature);
  if (pt !== undefined && (pt < 0 || pt > 2)) errors.push('AI 发帖助手 temperature 需在 [0, 2] 范围内');
  const pp = parseOptionalNumber(s.postCompose.topP);
  if (pp !== undefined && (pp < 0 || pp > 1)) errors.push('AI 发帖助手 topP 需在 [0, 1] 范围内');
  const ph = parseOptionalNumber(s.postCompose.chatHistoryLimit);
  if (ph !== undefined && (!Number.isInteger(ph) || ph < 1 || ph > 200)) errors.push('AI 发帖助手 上下文长度需为 1~200 的整数');
  if (!s.postCompose.systemPrompt.trim()) errors.push('AI 发帖助手 systemPrompt 不能为空');
  if (!s.postCompose.deepThinkSystemPrompt.trim()) errors.push('AI 发帖助手 deepThinkSystemPrompt 不能为空');
  if (!s.postCompose.composeSystemPrompt.trim()) errors.push('AI 发帖助手 composeSystemPrompt 不能为空');

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
      systemPrompt: s.assistant.systemPrompt,
      deepThinkSystemPrompt: s.assistant.deepThinkSystemPrompt,
    },
    postComposeAssistant: {
      providerId: s.postCompose.providerId.trim() ? s.postCompose.providerId.trim() : null,
      model: s.postCompose.model.trim() ? s.postCompose.model.trim() : null,
      temperature: pt === undefined ? null : clampNumber(pt, 0, 2),
      topP: pp === undefined ? null : clampNumber(pp, 0, 1),
      chatHistoryLimit: ph === undefined ? null : Math.trunc(ph),
      defaultDeepThink: s.postCompose.defaultDeepThink,
      systemPrompt: s.postCompose.systemPrompt,
      deepThinkSystemPrompt: s.postCompose.deepThinkSystemPrompt,
      composeSystemPrompt: s.postCompose.composeSystemPrompt,
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

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = isEditing && formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    const a = form.assistant;
    const b = committedForm.assistant;
    const c = form.postCompose;
    const d = committedForm.postCompose;
    return (
      a.providerId !== b.providerId ||
      a.model !== b.model ||
      a.temperature !== b.temperature ||
      a.topP !== b.topP ||
      a.historyLimit !== b.historyLimit ||
      a.defaultDeepThink !== b.defaultDeepThink ||
      a.defaultUseRag !== b.defaultUseRag ||
      a.ragTopK !== b.ragTopK ||
      a.defaultStream !== b.defaultStream ||
      a.systemPrompt !== b.systemPrompt ||
      a.deepThinkSystemPrompt !== b.deepThinkSystemPrompt ||
      c.providerId !== d.providerId ||
      c.model !== d.model ||
      c.temperature !== d.temperature ||
      c.topP !== d.topP ||
      c.chatHistoryLimit !== d.chatHistoryLimit ||
      c.defaultDeepThink !== d.defaultDeepThink ||
      c.systemPrompt !== d.systemPrompt ||
      c.deepThinkSystemPrompt !== d.deepThinkSystemPrompt ||
      c.composeSystemPrompt !== d.composeSystemPrompt
    );
  }, [form, committedForm]);

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
    try {
      const cfg = await adminGetPortalChatConfig();
      const next = toFormState(cfg);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
    } catch (e) {
      const next = defaultForm();
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  const cancelEdit = useCallback(() => {
    setForm(committedForm);
    setIsEditing(false);
    setError(null);
    setSavedHint('已放弃修改');
  }, [committedForm]);

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
      const next = toFormState(saved);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      setSavedHint('已保存并生效');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [form]);

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

          <div className="space-y-2">
            <div>
              <div className="text-sm font-medium mb-1">系统提示词（普通）</div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={6}
                value={form.assistant.systemPrompt}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, systemPrompt: e.target.value } }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.assistant.systemPrompt.length}</div>
            </div>
            <div>
              <div className="text-sm font-medium mb-1">系统提示词（深度思考）</div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={6}
                value={form.assistant.deepThinkSystemPrompt}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, assistant: { ...p.assistant, deepThinkSystemPrompt: e.target.value } }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.assistant.deepThinkSystemPrompt.length}</div>
            </div>
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

          <div className="space-y-2">
            <div>
              <div className="text-sm font-medium mb-1">系统提示词（普通）</div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={4}
                value={form.postCompose.systemPrompt}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, systemPrompt: e.target.value } }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.postCompose.systemPrompt.length}</div>
            </div>
            <div>
              <div className="text-sm font-medium mb-1">系统提示词（深度思考）</div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={4}
                value={form.postCompose.deepThinkSystemPrompt}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, deepThinkSystemPrompt: e.target.value } }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.postCompose.deepThinkSystemPrompt.length}</div>
            </div>
            <div>
              <div className="text-sm font-medium mb-1">发帖助手提示词（输出协议）</div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={10}
                value={form.postCompose.composeSystemPrompt}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, postCompose: { ...p.postCompose, composeSystemPrompt: e.target.value } }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.postCompose.composeSystemPrompt.length}</div>
            </div>
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

 