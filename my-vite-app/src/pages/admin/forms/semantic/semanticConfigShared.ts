import { useEffect, useState } from 'react';
import type { PromptContentDraft } from '../../../../components/admin/PromptContentCard';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminBatchGetPrompts, adminUpdatePromptContent, type PromptContentDTO } from '../../../../services/promptsAdminService';

type SuggestionPayloadInput = {
  enabled: boolean;
  promptCode: string;
  temperature?: string;
  topP?: string;
  defaultCount: string;
  maxCount: string;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
};

type PromptDraftSetter = (value: PromptContentDraft | null) => void;
type PromptErrorSetter = (value: string | null) => void;
type StringSetter = (value: string | null) => void;
type BooleanSetter = (value: boolean) => void;
type FormSetter<FormState> = (value: FormState) => void;

type PromptRangeValidationInput = {
  promptCode: string;
  temperature: string;
  topP: string;
  maxContentChars: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : undefined;
}

export function validatePromptRangeFields(input: PromptRangeValidationInput, maxContentCharsUpperBound: number): string[] {
  const errors: string[] = [];
  if (!input.promptCode.trim()) errors.push('promptCode 不能为空');

  const temp = parseOptionalNumber(input.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const topP = parseOptionalNumber(input.topP);
  if (topP !== undefined && (topP < 0 || topP > 1)) errors.push('topP 需在 [0, 1] 范围内');

  const mcc = parseOptionalNumber(input.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > maxContentCharsUpperBound)) {
    errors.push(`maxContentChars 需为 200~${maxContentCharsUpperBound} 的整数`);
  }

  return errors;
}

export function validateSuggestionConfigForm(input: SuggestionPayloadInput): string[] {
  const errors: string[] = [];
  if (!input.promptCode.trim()) errors.push('promptCode 不能为空');

  const temp = parseOptionalNumber(input.temperature ?? '');
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const topP = parseOptionalNumber(input.topP ?? '');
  if (topP !== undefined && (topP < 0 || topP > 1)) errors.push('topP 需在 [0, 1] 范围内');

  const dc = parseOptionalNumber(input.defaultCount);
  const mc = parseOptionalNumber(input.maxCount);
  if (dc !== undefined && (!Number.isInteger(dc) || dc < 1 || dc > 50)) errors.push('defaultCount 需为 1~50 的整数');
  if (mc !== undefined && (!Number.isInteger(mc) || mc < 1 || mc > 50)) errors.push('maxCount 需为 1~50 的整数');
  if (dc !== undefined && mc !== undefined && dc > mc) errors.push('defaultCount 不能大于 maxCount');

  const mcc = parseOptionalNumber(input.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 50000)) errors.push('maxContentChars 需为 200~50000 的整数');

  const hkd = parseOptionalNumber(input.historyKeepDays);
  if (hkd !== undefined && (!Number.isInteger(hkd) || hkd < 1)) errors.push('historyKeepDays 必须为正整数');

  const hkr = parseOptionalNumber(input.historyKeepRows);
  if (hkr !== undefined && (!Number.isInteger(hkr) || hkr < 1)) errors.push('historyKeepRows 必须为正整数');

  return errors;
}

export function buildSuggestionPayload(input: SuggestionPayloadInput) {
  const defaultCount = parseOptionalNumber(input.defaultCount);
  const maxCount = parseOptionalNumber(input.maxCount);
  const maxContentChars = parseOptionalNumber(input.maxContentChars);
  const historyKeepDays = parseOptionalNumber(input.historyKeepDays);
  const historyKeepRows = parseOptionalNumber(input.historyKeepRows);

  return {
    enabled: input.enabled,
    promptCode: input.promptCode,
    defaultCount: defaultCount === undefined ? 5 : Math.trunc(defaultCount),
    maxCount: maxCount === undefined ? 10 : Math.trunc(maxCount),
    maxContentChars: maxContentChars === undefined ? 4000 : Math.trunc(maxContentChars),
    historyEnabled: input.historyEnabled,
    historyKeepDays: historyKeepDays === undefined ? null : Math.trunc(historyKeepDays),
    historyKeepRows: historyKeepRows === undefined ? null : Math.trunc(historyKeepRows),
  };
}

export function toPromptDraft(dto?: PromptContentDTO | null): PromptContentDraft {
  return {
    name: dto?.name ?? '',
    systemPrompt: dto?.systemPrompt ?? '',
    userPromptTemplate: dto?.userPromptTemplate ?? '',
  };
}

export async function loadPromptDraftState(
  promptCode: string,
  setPromptLoadError: PromptErrorSetter,
  setCommittedPromptDraft: PromptDraftSetter,
  setPromptDraft: PromptDraftSetter,
): Promise<void> {
  try {
    const resp = await adminBatchGetPrompts([promptCode]);
    const dto = resp.prompts?.[0];
    if (!dto || (resp.missingCodes ?? []).length > 0) {
      setCommittedPromptDraft(null);
      setPromptDraft(null);
      return;
    }
    const draft = toPromptDraft(dto);
    setCommittedPromptDraft(draft);
    setPromptDraft(draft);
  } catch (e: unknown) {
    setPromptLoadError(e instanceof Error ? e.message : String(e));
    setCommittedPromptDraft(null);
    setPromptDraft(null);
  }
}

export function applyUnavailableFallback<FormState>(
  next: FormState,
  error: unknown,
  setForm: (value: FormState) => void,
  setCommittedForm: (value: FormState) => void,
  setEditing: BooleanSetter,
  setError: StringSetter,
  setSavedHint: StringSetter,
  setCommittedPromptDraft: PromptDraftSetter,
  setPromptDraft: PromptDraftSetter,
): void {
  setForm(next);
  setCommittedForm(next);
  setEditing(false);
  setError(error instanceof Error ? error.message : String(error));
  setSavedHint('后端接口不可用，已加载前端默认配置（可用于演示）');
  setCommittedPromptDraft(null);
  setPromptDraft(null);
}

export async function applySavedConfigState<FormState, SavedDTO>(params: {
  saveConfig: () => Promise<SavedDTO>;
  toFormState: (saved: SavedDTO) => FormState;
  promptCode: string;
  promptDraft: PromptContentDraft | null;
  promptHasUnsavedChanges: boolean;
  setCommittedPromptDraft: PromptDraftSetter;
  setForm: FormSetter<FormState>;
  setCommittedForm: FormSetter<FormState>;
  setEditing: BooleanSetter;
  setError: StringSetter;
  setSavedHint: StringSetter;
}): Promise<void> {
  const saved = await params.saveConfig();

  let promptUpdateErr: string | null = null;
  if (params.promptDraft && params.promptHasUnsavedChanges) {
    try {
      await adminUpdatePromptContent(params.promptCode, {
        systemPrompt: params.promptDraft.systemPrompt,
        userPromptTemplate: params.promptDraft.userPromptTemplate,
      });
      params.setCommittedPromptDraft(params.promptDraft);
    } catch (e: unknown) {
      promptUpdateErr = e instanceof Error ? e.message : String(e);
    }
  }

  const next = params.toFormState(saved);
  params.setForm(next);
  params.setCommittedForm(next);
  params.setEditing(false);
  if (promptUpdateErr) {
    params.setError(`配置已保存，但提示词保存失败：${promptUpdateErr}`);
  } else {
    params.setSavedHint('保存成功');
  }
}

export function usePromptConfigEditorState() {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [promptLoadError, setPromptLoadError] = useState<string | null>(null);
  const [committedPromptDraft, setCommittedPromptDraft] = useState<PromptContentDraft | null>(null);
  const [promptDraft, setPromptDraft] = useState<PromptContentDraft | null>(null);

  return {
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
  };
}

export function useAiProviderOptions() {
  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

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

  return {
    providers,
    activeProviderId,
    chatProviders,
  };
}
