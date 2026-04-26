import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  RotateCcw,
  Save,
  Pencil,
  X,
  RefreshCw,
  Bot,
  PenTool,
  MessageSquareText,
  SlidersHorizontal,
  Trash2,
  AlertCircle,
  CheckCircle2,
  Zap,
  BrainCircuit,
  Waves,
} from 'lucide-react';
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
  allowManualModelSelection: boolean;
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
  allowManualModelSelection: boolean;
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

type PromptWorkspaceItem = {
  id: string;
  title: string;
  description: string;
  code: string;
  draft: PromptContentDraft | null;
  onChange: (next: PromptContentDraft) => void;
};

function defaultForm(): FormState {
  return {
    assistant: {
      allowManualModelSelection: true,
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
      allowManualModelSelection: true,
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
      allowManualModelSelection: Boolean(a?.allowManualModelSelection ?? d.assistant.allowManualModelSelection),
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
      allowManualModelSelection: Boolean(p?.allowManualModelSelection ?? d.postCompose.allowManualModelSelection),
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
      allowManualModelSelection: s.assistant.allowManualModelSelection,
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
      allowManualModelSelection: s.postCompose.allowManualModelSelection,
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

/* ─── compact design tokens ─── */
const inputClassName = 'w-full rounded-md border border-slate-200 bg-white px-2.5 py-1 text-sm text-slate-900 shadow-sm transition focus:border-violet-400 focus:outline-none focus:ring-2 focus:ring-violet-100 disabled:bg-slate-50 disabled:text-slate-400 h-7';
const selectClassName = 'w-full rounded-md border border-slate-200 bg-white px-2.5 py-1 text-sm text-slate-900 shadow-sm transition focus:border-violet-400 focus:outline-none focus:ring-2 focus:ring-violet-100 disabled:bg-slate-50 disabled:text-slate-400 h-7';
const ghostBtn = 'inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-violet-200 hover:text-violet-700 disabled:cursor-not-allowed disabled:opacity-50';
const dangerBtn = 'inline-flex items-center gap-1 rounded-md border border-rose-200 bg-white px-2 py-1 text-[11px] font-medium text-rose-600 transition hover:border-rose-300 hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-50';

function FormField(props: { label: string; hint?: string; children: React.ReactNode; action?: React.ReactNode }) {
  const { label, hint, children, action } = props;
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs font-medium text-slate-700">{label}</div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      {children}
      {hint ? <div className="text-[11px] leading-4 text-slate-400">{hint}</div> : null}
    </div>
  );
}

function ToggleRow(props: {
  label: string; hint?: string; checked: boolean; disabled?: boolean; onChange: (v: boolean) => void;
  tone?: 'violet' | 'amber'; icon?: React.ReactNode;
}) {
  const { label, hint, checked, disabled, onChange, tone = 'violet', icon } = props;
  const checkColor = tone === 'amber' ? 'text-amber-600 focus:ring-amber-500' : 'text-violet-600 focus:ring-violet-500';
  return (
    <label className={`flex items-start gap-2 rounded-md border px-2 py-1.5 transition cursor-pointer ${checked ? (tone === 'amber' ? 'border-amber-200 bg-amber-50/50' : 'border-violet-200 bg-violet-50/50') : 'border-slate-200 bg-white hover:border-slate-300'}`}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className={`mt-0.5 rounded border-gray-300 ${checkColor}`}
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1 text-xs font-medium text-slate-800">
          {icon ? <span className="text-slate-400">{icon}</span> : null}
          {label}
        </div>
        {hint ? <div className="text-[11px] leading-4 text-slate-500">{hint}</div> : null}
      </div>
    </label>
  );
}

function NumberField(props: {
  value: string; placeholder?: string; disabled?: boolean; onChange: (v: string) => void; onClear?: () => void;
}) {
  const { value, placeholder, disabled, onChange, onClear } = props;
  return (
    <div className="flex items-center gap-1.5">
      <input
        className={`${inputClassName} flex-1`}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
      {onClear ? (
        <button type="button" className={ghostBtn} onClick={onClear} disabled={disabled || !value} title="清空该字段">
          <Trash2 className="h-3 w-3" />
        </button>
      ) : null}
    </div>
  );
}

/* ─── main component ─── */
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

  const [mainTab, setMainTab] = useState<'config' | 'prompt'>('config');
  const [assistantPromptTab, setAssistantPromptTab] = useState<'system' | 'deep-think'>('system');
  const [postComposePromptTab, setPostComposePromptTab] = useState<'system' | 'deep-think' | 'compose'>('system');

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = isEditing && formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    const a = form.assistant; const b = committedForm.assistant;
    const c = form.postCompose; const d = committedForm.postCompose;
    const formChanged =
      a.allowManualModelSelection !== b.allowManualModelSelection ||
      a.providerId !== b.providerId || a.model !== b.model ||
      a.temperature !== b.temperature || a.topP !== b.topP ||
      a.historyLimit !== b.historyLimit || a.defaultDeepThink !== b.defaultDeepThink ||
      a.defaultUseRag !== b.defaultUseRag || a.ragTopK !== b.ragTopK ||
      a.defaultStream !== b.defaultStream ||
      a.systemPromptCode !== b.systemPromptCode || a.deepThinkSystemPromptCode !== b.deepThinkSystemPromptCode ||
      c.allowManualModelSelection !== d.allowManualModelSelection ||
      c.providerId !== d.providerId || c.model !== d.model ||
      c.temperature !== d.temperature || c.topP !== d.topP ||
      c.chatHistoryLimit !== d.chatHistoryLimit || c.defaultDeepThink !== d.defaultDeepThink ||
      c.systemPromptCode !== d.systemPromptCode || c.deepThinkSystemPromptCode !== d.deepThinkSystemPromptCode ||
      c.composeSystemPromptCode !== d.composeSystemPromptCode;

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
      } catch { if (!cancelled) { setProviders([]); setActiveProviderId(''); } }
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const opts = await getAiChatOptions();
        if (cancelled) return;
        setChatProviders((opts.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[]);
      } catch { if (!cancelled) setChatProviders([]); }
    })();
    return () => { cancelled = true; };
  }, []);

  const loadConfig = useCallback(async () => {
    setLoading(true); setError(null); setSavedHint(null); setPromptLoadError(null);
    try {
      const cfg = await adminGetPortalChatConfig();
      const next = toFormState(cfg);
      setForm(next); setCommittedForm(next); setIsEditing(false);
      try {
        const codes = listPromptCodes(next);
        const resp = await adminBatchGetPrompts(codes);
        const map: Record<string, PromptContentDraft | null> = {};
        for (const code of codes) map[code] = null;
        for (const dto of resp.prompts ?? []) { if (dto?.promptCode) map[dto.promptCode] = toPromptDraft(dto); }
        setPromptDrafts(map); setCommittedPromptDrafts(map);
      } catch (e: unknown) {
        setPromptLoadError(e instanceof Error ? e.message : String(e));
        setPromptDrafts({}); setCommittedPromptDrafts({});
      }
    } catch (e) {
      const next = defaultForm();
      setForm(next); setCommittedForm(next); setIsEditing(false);
      setError(e instanceof Error ? e.message : String(e));
      setPromptDrafts({}); setCommittedPromptDrafts({});
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void loadConfig(); }, [loadConfig]);

  const cancelEdit = useCallback(() => {
    setForm(committedForm); setPromptDrafts(committedPromptDrafts);
    setIsEditing(false); setError(null); setSavedHint('已放弃修改');
  }, [committedForm, committedPromptDrafts]);

  const save = useCallback(async () => {
    const errs = validateForm(form);
    if (errs.length > 0) { setError(errs.join('；')); return; }
    setSaving(true); setError(null); setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertPortalChatConfig(payload);
      const codes = listPromptCodes(form).sort();
      const changedCodes = codes.filter((code) => JSON.stringify(promptDrafts[code] ?? null) !== JSON.stringify(committedPromptDrafts[code] ?? null));
      const promptErrors: string[] = [];
      for (const code of changedCodes) {
        const draft = promptDrafts[code]; if (!draft) continue;
        try { await adminUpdatePromptContent(code, { systemPrompt: draft.systemPrompt, userPromptTemplate: draft.userPromptTemplate }); }
        catch (e: unknown) { promptErrors.push(e instanceof Error ? e.message : String(e)); }
      }
      if (changedCodes.length && promptErrors.length === 0) setCommittedPromptDrafts(promptDrafts);
      const next = toFormState(saved);
      setForm(next); setCommittedForm(next); setIsEditing(false);
      if (promptErrors.length) setError(`配置已保存，但提示词保存失败：${promptErrors[0]}`);
      else setSavedHint('已保存并生效');
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setSaving(false); }
  }, [form, promptDrafts, committedPromptDrafts]);

  const restoreAllDefaults = useCallback(() => {
    if (!isEditing) return;
    const defs = defaultForm();
    setForm((prev) => ({
      assistant: { ...defs.assistant, systemPromptCode: prev.assistant.systemPromptCode, deepThinkSystemPromptCode: prev.assistant.deepThinkSystemPromptCode },
      postCompose: { ...defs.postCompose, systemPromptCode: prev.postCompose.systemPromptCode, deepThinkSystemPromptCode: prev.postCompose.deepThinkSystemPromptCode, composeSystemPromptCode: prev.postCompose.composeSystemPromptCode },
    }));
    setSavedHint(null); setError(null);
  }, [isEditing]);

  const restoreAssistantDefaults = useCallback(() => {
    if (!isEditing) return;
    const defs = defaultForm().assistant;
    setForm((prev) => ({ ...prev, assistant: { ...defs, systemPromptCode: prev.assistant.systemPromptCode, deepThinkSystemPromptCode: prev.assistant.deepThinkSystemPromptCode } }));
    setSavedHint(null); setError(null);
  }, [isEditing]);

  const restorePostComposeDefaults = useCallback(() => {
    if (!isEditing) return;
    const defs = defaultForm().postCompose;
    setForm((prev) => ({ ...prev, postCompose: { ...defs, systemPromptCode: prev.postCompose.systemPromptCode, deepThinkSystemPromptCode: prev.postCompose.deepThinkSystemPromptCode, composeSystemPromptCode: prev.postCompose.composeSystemPromptCode } }));
    setSavedHint(null); setError(null);
  }, [isEditing]);

  const clearAssistantModelOverride = useCallback(() => {
    if (!isEditing) return;
    setForm((prev) => ({ ...prev, assistant: { ...prev.assistant, providerId: '', model: '' } }));
    setSavedHint(null);
  }, [isEditing]);

  const clearPostComposeModelOverride = useCallback(() => {
    if (!isEditing) return;
    setForm((prev) => ({ ...prev, postCompose: { ...prev.postCompose, providerId: '', model: '' } }));
    setSavedHint(null);
  }, [isEditing]);

  const assistantPromptItems: PromptWorkspaceItem[] = [
    {
      id: 'system', title: '系统提示词', description: '标准问答模式的基础提示词。',
      code: form.assistant.systemPromptCode,
      draft: promptDrafts[form.assistant.systemPromptCode] ?? null,
      onChange: (next) => { if (!isEditing) return; setPromptDrafts((prev) => ({ ...prev, [form.assistant.systemPromptCode]: next })); setSavedHint(null); },
    },
    {
      id: 'deep-think', title: '深度思考', description: '复杂问题场景下的推理提示词。',
      code: form.assistant.deepThinkSystemPromptCode,
      draft: promptDrafts[form.assistant.deepThinkSystemPromptCode] ?? null,
      onChange: (next) => { if (!isEditing) return; setPromptDrafts((prev) => ({ ...prev, [form.assistant.deepThinkSystemPromptCode]: next })); setSavedHint(null); },
    },
  ];

  const postComposePromptItems: PromptWorkspaceItem[] = [
    {
      id: 'system', title: '系统提示词', description: '发帖助手的基础写作指导。',
      code: form.postCompose.systemPromptCode,
      draft: promptDrafts[form.postCompose.systemPromptCode] ?? null,
      onChange: (next) => { if (!isEditing) return; setPromptDrafts((prev) => ({ ...prev, [form.postCompose.systemPromptCode]: next })); setSavedHint(null); },
    },
    {
      id: 'deep-think', title: '深度思考', description: '先规划结构、再生成正文时使用。',
      code: form.postCompose.deepThinkSystemPromptCode,
      draft: promptDrafts[form.postCompose.deepThinkSystemPromptCode] ?? null,
      onChange: (next) => { if (!isEditing) return; setPromptDrafts((prev) => ({ ...prev, [form.postCompose.deepThinkSystemPromptCode]: next })); setSavedHint(null); },
    },
    {
      id: 'compose', title: '发帖输出协议', description: '规定 chat/post 两类输出块的协议。',
      code: form.postCompose.composeSystemPromptCode,
      draft: promptDrafts[form.postCompose.composeSystemPromptCode] ?? null,
      onChange: (next) => { if (!isEditing) return; setPromptDrafts((prev) => ({ ...prev, [form.postCompose.composeSystemPromptCode]: next })); setSavedHint(null); },
    },
  ];

  const StatusBadge = ({ icon, text, color }: { icon: React.ReactNode; text: string; color: string }) => (
    <span className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-medium ${color}`}>
      {icon}{text}
    </span>
  );

  return (
    <div className="mx-auto w-full max-w-[1600px] space-y-2">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-sm">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-slate-500" />
          <h2 className="text-sm font-bold text-slate-900">前台对话配置</h2>
          <StatusBadge icon={<AlertCircle className="h-3 w-3" />} text={isEditing ? '编辑中' : '只读'} color={isEditing ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-slate-200 bg-slate-50 text-slate-600'} />
          <StatusBadge icon={hasUnsavedChanges ? <AlertCircle className="h-3 w-3" /> : <CheckCircle2 className="h-3 w-3" />} text={hasUnsavedChanges ? '未保存' : '已同步'} color={hasUnsavedChanges ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'} />
        </div>
        <div className="flex items-center gap-1.5">
          {isEditing ? (
            <button type="button" className={dangerBtn} onClick={restoreAllDefaults} title="将所有参数恢复为系统推荐值">
              <RotateCcw className="h-3 w-3" />
              全部恢复默认
            </button>
          ) : null}
          <button type="button" onClick={() => { void loadConfig(); setError(null); }} className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-50" disabled={loading || saving}>
            <RefreshCw className="h-3 w-3" />
            刷新
          </button>
          {!isEditing ? (
            <button type="button" onClick={() => { setIsEditing(true); setSavedHint(null); setError(null); }} className="inline-flex items-center gap-1 rounded-md bg-violet-600 px-2.5 py-1.5 text-xs font-semibold text-white transition hover:bg-violet-500 disabled:opacity-50" disabled={loading || saving}>
              <Pencil className="h-3 w-3" />
              编辑
            </button>
          ) : (
            <>
              <button type="button" onClick={cancelEdit} className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-50" disabled={loading || saving}>
                <X className="h-3 w-3" />
                放弃
              </button>
              <button type="button" onClick={save} className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2.5 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-500 disabled:opacity-50" disabled={!canSave} title={formErrors.length ? formErrors.join('\n') : '保存并生效'}>
                <Save className="h-3 w-3" />
                {saving ? '保存中…' : '保存'}
              </button>
            </>
          )}
        </div>
      </div>

      {/* Alerts */}
      {savedHint ? (
        <div className="flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs text-emerald-700">
          <CheckCircle2 className="h-3.5 w-3.5" />
          {savedHint}
        </div>
      ) : null}
      {error ? (
        <div className="flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs text-rose-700">
          <AlertCircle className="h-3.5 w-3.5" />
          {error}
        </div>
      ) : null}
      {!isEditing && hasUnsavedChanges ? (
        <div className="flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs text-amber-800">
          <AlertCircle className="h-3.5 w-3.5" />
          当前表单与已生效配置不一致。如需重新整理参数，请先进入编辑模式。
        </div>
      ) : null}
      {formErrors.length ? (
        <div className="rounded-lg border border-amber-200 bg-gradient-to-r from-amber-50 to-orange-50 px-3 py-2 text-xs text-amber-900">
          <div className="flex items-center gap-1 font-semibold"><AlertCircle className="h-3.5 w-3.5" />配置校验提示</div>
          <ul className="mt-1 list-disc space-y-0.5 pl-4 text-amber-800">{formErrors.map((x) => (<li key={x}>{x}</li>))}</ul>
        </div>
      ) : null}

      {/* Main Tabs */}
      <div className="flex items-center gap-1 bg-slate-50/80 rounded-t-lg border border-slate-200 border-b-0 px-2 pt-1.5">
        <button type="button" onClick={() => setMainTab('config')} className={`inline-flex items-center gap-1 rounded-t-md border-b-2 px-3 py-1.5 text-xs font-semibold transition ${mainTab === 'config' ? 'border-violet-500 bg-white text-violet-700 shadow-sm' : 'border-transparent bg-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-100/60'}`}>
          <SlidersHorizontal className="h-3 w-3" />
          参数配置
        </button>
        <button type="button" onClick={() => setMainTab('prompt')} className={`inline-flex items-center gap-1 rounded-t-md border-b-2 px-3 py-1.5 text-xs font-semibold transition ${mainTab === 'prompt' ? 'border-violet-500 bg-white text-violet-700 shadow-sm' : 'border-transparent bg-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-100/60'}`}>
          <MessageSquareText className="h-3 w-3" />
          提示词编辑
        </button>
      </div>

      {/* ─── 参数配置页 ─── */}
      {mainTab === 'config' ? (
        <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
          {/* 左栏：智能助手 */}
          <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
              <div className="flex items-center gap-2">
                <div className="flex h-6 w-6 items-center justify-center rounded-md bg-violet-100 text-violet-600"><Bot className="h-3.5 w-3.5" /></div>
                <h3 className="text-sm font-bold text-slate-900">智能助手</h3>
              </div>
              <div className="flex items-center gap-1 text-[11px]">
                <span className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-slate-600">{form.assistant.providerId || form.assistant.model ? '已指定' : '自动均衡'}</span>
                <span className={`rounded border px-1.5 py-0.5 ${form.assistant.defaultUseRag ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>RAG {form.assistant.defaultUseRag ? '开' : '关'}</span>
                <span className={`rounded border px-1.5 py-0.5 ${form.assistant.defaultStream ? 'border-sky-200 bg-sky-50 text-sky-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>流式 {form.assistant.defaultStream ? '开' : '关'}</span>
              </div>
            </div>

            <div className="space-y-2 p-3">
              {/* 模型 */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><Zap className="h-3 w-3 text-amber-500" />模型策略</div>
                  {isEditing ? (
                    <div className="flex gap-1">
                      <button type="button" className={ghostBtn} onClick={clearAssistantModelOverride}><Trash2 className="h-3 w-3" />清空模型</button>
                      <button type="button" className={dangerBtn} onClick={restoreAssistantDefaults}><RotateCcw className="h-3 w-3" />恢复默认</button>
                    </div>
                  ) : null}
                </div>
                <FormField label="默认模型" hint="留空时由系统按照全局路由自动选择最合适的 Provider。">
                  <ProviderModelSelect providers={providers} activeProviderId={activeProviderId} chatProviders={chatProviders} mode="chat" providerId={form.assistant.providerId} autoOptionLabel="自动（均衡负载）" model={form.assistant.model} disabled={!isEditing} label="" selectClassName={selectClassName}
                    onChange={(next) => { if (!isEditing) return; setForm((p) => ({ ...p, assistant: { ...p.assistant, providerId: next.providerId, model: next.model } })); setSavedHint(null); }}
                  />
                </FormField>
                <ToggleRow label="允许用户手动选模" hint="开启后，前台用户可自行切换模型。" checked={form.assistant.allowManualModelSelection} disabled={!isEditing}
                  onChange={(v) => { if (!isEditing) return; setForm((p) => ({ ...p, assistant: { ...p.assistant, allowManualModelSelection: v } })); setSavedHint(null); }}
                />
              </div>

              {/* 采样 */}
              <div className="space-y-1.5">
                <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><BrainCircuit className="h-3 w-3 text-violet-500" />采样参数</div>
                <div className="grid grid-cols-3 gap-2">
                  <FormField label="温度" hint="0.2~0.4" action={isEditing ? <button type="button" className={ghostBtn} onClick={() => { setForm((p) => ({ ...p, assistant: { ...p.assistant, temperature: '' } })); setSavedHint(null); }}><Trash2 className="h-3 w-3" /></button> : null}>
                    <NumberField value={form.assistant.temperature} placeholder="0.2" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, temperature: v } })); setSavedHint(null); }} />
                  </FormField>
                  <FormField label="TOP-P" hint="通常留空" action={isEditing ? <button type="button" className={ghostBtn} onClick={() => { setForm((p) => ({ ...p, assistant: { ...p.assistant, topP: '' } })); setSavedHint(null); }}><Trash2 className="h-3 w-3" /></button> : null}>
                    <NumberField value={form.assistant.topP} placeholder="0.9" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, topP: v } })); setSavedHint(null); }} />
                  </FormField>
                  <FormField label="上下文长度" hint="默认 20">
                    <NumberField value={form.assistant.historyLimit} placeholder="20" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, historyLimit: v } })); setSavedHint(null); }} />
                  </FormField>
                  <FormField label="RAG TopK" hint="默认 6">
                    <NumberField value={form.assistant.ragTopK} placeholder="6" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, ragTopK: v } })); setSavedHint(null); }} />
                  </FormField>
                </div>
              </div>

              {/* 行为 */}
              <div className="space-y-1.5">
                <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><Waves className="h-3 w-3 text-sky-500" />默认行为</div>
                <div className="grid grid-cols-3 gap-2">
                  <ToggleRow label="流式输出" hint="长回答实时反馈" checked={form.assistant.defaultStream} disabled={!isEditing}
                    onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultStream: v } })); setSavedHint(null); }}
                  />
                  <ToggleRow label="深度思考" hint="复杂分析推理" checked={form.assistant.defaultDeepThink} disabled={!isEditing}
                    onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultDeepThink: v } })); setSavedHint(null); }}
                  />
                  <ToggleRow label="启用 RAG" hint="知识库问答" checked={form.assistant.defaultUseRag} disabled={!isEditing}
                    onChange={(v) => { setForm((p) => ({ ...p, assistant: { ...p.assistant, defaultUseRag: v } })); setSavedHint(null); }}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* 右栏：AI 发帖助手 */}
          <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
              <div className="flex items-center gap-2">
                <div className="flex h-6 w-6 items-center justify-center rounded-md bg-amber-100 text-amber-600"><PenTool className="h-3.5 w-3.5" /></div>
                <h3 className="text-sm font-bold text-slate-900">AI 发帖助手</h3>
              </div>
              <div className="flex items-center gap-1 text-[11px]">
                <span className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-slate-600">{form.postCompose.providerId || form.postCompose.model ? '已指定' : '自动均衡'}</span>
                <span className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-slate-600">上下文 {form.postCompose.chatHistoryLimit || '默认'}</span>
                <span className={`rounded border px-1.5 py-0.5 ${form.postCompose.defaultDeepThink ? 'border-violet-200 bg-violet-50 text-violet-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>深度思考 {form.postCompose.defaultDeepThink ? '开' : '关'}</span>
              </div>
            </div>

            <div className="space-y-2 p-3">
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><Zap className="h-3 w-3 text-amber-500" />模型策略</div>
                  {isEditing ? (
                    <div className="flex gap-1">
                      <button type="button" className={ghostBtn} onClick={clearPostComposeModelOverride}><Trash2 className="h-3 w-3" />清空模型</button>
                      <button type="button" className={dangerBtn} onClick={restorePostComposeDefaults}><RotateCcw className="h-3 w-3" />恢复默认</button>
                    </div>
                  ) : null}
                </div>
                <FormField label="默认模型" hint="留空后由系统自动路由；若想控制文风一致性，可指定固定模型。">
                  <ProviderModelSelect providers={providers} activeProviderId={activeProviderId} chatProviders={chatProviders} mode="chat" providerId={form.postCompose.providerId} autoOptionLabel="自动（均衡负载）" model={form.postCompose.model} disabled={!isEditing} label="" selectClassName={selectClassName}
                    onChange={(next) => { if (!isEditing) return; setForm((p) => ({ ...p, postCompose: { ...p.postCompose, providerId: next.providerId, model: next.model } })); setSavedHint(null); }}
                  />
                </FormField>
                <ToggleRow label="允许用户手动选模" hint="仅在你希望发帖页支持用户自己切换模型时开启。" checked={form.postCompose.allowManualModelSelection} disabled={!isEditing} tone="amber"
                  onChange={(v) => { if (!isEditing) return; setForm((p) => ({ ...p, postCompose: { ...p.postCompose, allowManualModelSelection: v } })); setSavedHint(null); }}
                />
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><BrainCircuit className="h-3 w-3 text-violet-500" />采样参数</div>
                <div className="grid grid-cols-3 gap-2">
                  <FormField label="温度" hint="推荐 0.2" action={isEditing ? <button type="button" className={ghostBtn} onClick={() => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, temperature: '' } })); setSavedHint(null); }}><Trash2 className="h-3 w-3" /></button> : null}>
                    <NumberField value={form.postCompose.temperature} placeholder="0.2" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, temperature: v } })); setSavedHint(null); }} />
                  </FormField>
                  <FormField label="TOP-P" hint="通常留空" action={isEditing ? <button type="button" className={ghostBtn} onClick={() => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, topP: '' } })); setSavedHint(null); }}><Trash2 className="h-3 w-3" /></button> : null}>
                    <NumberField value={form.postCompose.topP} placeholder="0.9" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, topP: v } })); setSavedHint(null); }} />
                  </FormField>
                  <FormField label="上下文长度" hint="默认 20">
                    <NumberField value={form.postCompose.chatHistoryLimit} placeholder="20" disabled={!isEditing} onChange={(v) => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, chatHistoryLimit: v } })); setSavedHint(null); }} />
                  </FormField>
                </div>
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center gap-1 text-xs font-semibold text-slate-700"><Waves className="h-3 w-3 text-sky-500" />默认行为</div>
                <div className="grid grid-cols-3 gap-2">
                  <ToggleRow label="深度思考" hint="先列提纲再成稿" checked={form.postCompose.defaultDeepThink} disabled={!isEditing} tone="amber"
                    onChange={(v) => { setForm((p) => ({ ...p, postCompose: { ...p.postCompose, defaultDeepThink: v } })); setSavedHint(null); }}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {/* ─── 提示词编辑页 ─── */}
      {mainTab === 'prompt' ? (
        <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
          {/* 智能助手提示词 */}
          <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center gap-2 border-b border-slate-100 px-3 py-2">
              <div className="flex h-6 w-6 items-center justify-center rounded-md bg-violet-100 text-violet-600"><Bot className="h-3.5 w-3.5" /></div>
              <h3 className="text-sm font-bold text-slate-900">智能助手提示词</h3>
            </div>
            <div className="p-3">
              <div className="grid gap-2 xl:grid-cols-[140px_minmax(0,1fr)]">
                <div className="flex flex-row gap-2 xl:flex-col">
                  {assistantPromptItems.map((item) => {
                    const active = item.id === assistantPromptTab;
                    return (
                      <button key={item.id} type="button" onClick={() => setAssistantPromptTab(item.id as 'system' | 'deep-think')}
                        className={`rounded-md border px-2.5 py-2 text-left transition ${active ? 'border-violet-300 bg-violet-50 text-violet-950 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'}`}>
                        <div className="text-xs font-semibold">{item.title}</div>
                        <div className="text-[11px] leading-4 text-slate-500">{item.description}</div>
                      </button>
                    );
                  })}
                </div>
                <div className="rounded-md border border-slate-100 bg-slate-50/50 p-2 [&_textarea]:min-h-[80px] [&_textarea]:max-h-[140px]">
                  {(() => {
                    const item = assistantPromptItems.find((i) => i.id === assistantPromptTab) ?? assistantPromptItems[0];
                    if (!item) return <div className="text-xs text-slate-500">暂无可展示的提示词卡片。</div>;
                    return (
                      <PromptContentCard compact title={item.title} draft={item.draft} editing={isEditing} onChange={item.onChange} hint={promptLoadError ?? undefined} showUserPromptTemplate={false} />
                    );
                  })()}
                </div>
              </div>
            </div>
          </div>

          {/* 发帖助手提示词 */}
          <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center gap-2 border-b border-slate-100 px-3 py-2">
              <div className="flex h-6 w-6 items-center justify-center rounded-md bg-amber-100 text-amber-600"><PenTool className="h-3.5 w-3.5" /></div>
              <h3 className="text-sm font-bold text-slate-900">发帖助手提示词</h3>
            </div>
            <div className="p-3">
              <div className="grid gap-2 xl:grid-cols-[140px_minmax(0,1fr)]">
                <div className="flex flex-row gap-2 xl:flex-col">
                  {postComposePromptItems.map((item) => {
                    const active = item.id === postComposePromptTab;
                    return (
                      <button key={item.id} type="button" onClick={() => setPostComposePromptTab(item.id as 'system' | 'deep-think' | 'compose')}
                        className={`rounded-md border px-2.5 py-2 text-left transition ${active ? 'border-amber-300 bg-amber-50 text-amber-950 shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'}`}>
                        <div className="text-xs font-semibold">{item.title}</div>
                        <div className="text-[11px] leading-4 text-slate-500">{item.description}</div>
                      </button>
                    );
                  })}
                </div>
                <div className="rounded-md border border-slate-100 bg-slate-50/50 p-2 [&_textarea]:min-h-[80px] [&_textarea]:max-h-[140px]">
                  {(() => {
                    const item = postComposePromptItems.find((i) => i.id === postComposePromptTab) ?? postComposePromptItems[0];
                    if (!item) return <div className="text-xs text-slate-500">暂无可展示的提示词卡片。</div>;
                    return (
                      <PromptContentCard compact title={item.title} draft={item.draft} editing={isEditing} onChange={item.onChange} hint={promptLoadError ?? undefined} showUserPromptTemplate={false} />
                    );
                  })()}
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default PortalChatConfigForm;
