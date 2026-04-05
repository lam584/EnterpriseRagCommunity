import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

vi.mock('../../../../services/aiProvidersAdminService', () => ({
  adminGetAiProvidersConfig: vi.fn(),
}));

vi.mock('../../../../services/aiChatOptionsService', () => ({
  getAiChatOptions: vi.fn(),
}));

vi.mock('../../../../services/promptsAdminService', () => ({
  adminBatchGetPrompts: vi.fn(),
  adminUpdatePromptContent: vi.fn(),
}));

import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions } from '../../../../services/aiChatOptionsService';
import { adminBatchGetPrompts } from '../../../../services/promptsAdminService';
import { adminUpdatePromptContent } from '../../../../services/promptsAdminService';
import {
  applySavedConfigState,
  buildSuggestionPayload,
  loadPromptDraftState,
  useAiProviderOptions,
  usePromptConfigEditorState,
  validatePromptRangeFields,
  validateSuggestionConfigForm,
} from './semanticConfigShared';

describe('semanticConfigShared', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminUpdatePromptContent).mockResolvedValue(undefined as never);
  });

  it('usePromptConfigEditorState exposes default editor state', () => {
    const { result } = renderHook(() => usePromptConfigEditorState());

    expect(result.current.loading).toBe(false);
    expect(result.current.saving).toBe(false);
    expect(result.current.testing).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.savedHint).toBeNull();
    expect(result.current.editing).toBe(false);
    expect(result.current.promptLoadError).toBeNull();
    expect(result.current.committedPromptDraft).toBeNull();
    expect(result.current.promptDraft).toBeNull();
  });

  it('useAiProviderOptions loads provider config and chat options', async () => {
    vi.mocked(adminGetAiProvidersConfig).mockResolvedValue({
      activeProviderId: 'p1',
      providers: [{ id: 'p1', name: 'Provider 1' }],
    } as never);
    vi.mocked(getAiChatOptions).mockResolvedValue({
      providers: [{ providerId: 'p1', models: [{ model: 'm1', displayName: 'M1' }] }],
    } as never);

    const { result } = renderHook(() => useAiProviderOptions());

    await waitFor(() => {
      expect(result.current.activeProviderId).toBe('p1');
      expect(result.current.providers).toHaveLength(1);
      expect(result.current.chatProviders).toHaveLength(1);
    });
  });

  it('buildSuggestionPayload applies defaults and truncates numeric values', () => {
    expect(
      buildSuggestionPayload({
        enabled: true,
        promptCode: 'TAG_GEN',
        defaultCount: '5.9',
        maxCount: '',
        maxContentChars: '4000.7',
        historyEnabled: false,
        historyKeepDays: '',
        historyKeepRows: '22.8',
      }),
    ).toEqual({
      enabled: true,
      promptCode: 'TAG_GEN',
      defaultCount: 5,
      maxCount: 10,
      maxContentChars: 4000,
      historyEnabled: false,
      historyKeepDays: null,
      historyKeepRows: 22,
    });
  });

  it('validatePromptRangeFields validates shared prompt range inputs', () => {
    expect(
      validatePromptRangeFields(
        {
          promptCode: ' ',
          temperature: '2.5',
          topP: '-1',
          maxContentChars: '100001',
        },
        100000,
      ),
    ).toEqual([
      'promptCode 不能为空',
      'temperature 需在 [0, 2] 范围内',
      'topP 需在 [0, 1] 范围内',
      'maxContentChars 需为 200~100000 的整数',
    ]);
  });

  it('validateSuggestionConfigForm reports numeric and ordering errors', () => {
    expect(
      validateSuggestionConfigForm({
        enabled: true,
        promptCode: ' ',
        temperature: '3',
        topP: '2',
        defaultCount: '0',
        maxCount: '0',
        maxContentChars: '100',
        historyEnabled: true,
        historyKeepDays: '0',
        historyKeepRows: '0',
      }),
    ).toEqual([
      'promptCode 不能为空',
      'temperature 需在 [0, 2] 范围内',
      'topP 需在 [0, 1] 范围内',
      'defaultCount 需为 1~50 的整数',
      'maxCount 需为 1~50 的整数',
      'maxContentChars 需为 200~50000 的整数',
      'historyKeepDays 必须为正整数',
      'historyKeepRows 必须为正整数',
    ]);
  });

  it('validateSuggestionConfigForm accepts valid inputs', () => {
    expect(
      validateSuggestionConfigForm({
        enabled: true,
        promptCode: 'TITLE_GEN',
        temperature: '1.2',
        topP: '0.9',
        defaultCount: '3',
        maxCount: '5',
        maxContentChars: '3000',
        historyEnabled: true,
        historyKeepDays: '10',
        historyKeepRows: '50',
      }),
    ).toEqual([]);
  });

  it('loadPromptDraftState applies prompt draft when prompt exists', async () => {
    vi.mocked(adminBatchGetPrompts).mockResolvedValue({
      prompts: [{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }],
      missingCodes: [],
    } as never);

    const setError = vi.fn();
    const setCommitted = vi.fn();
    const setDraft = vi.fn();

    await loadPromptDraftState('TAG_GEN', setError, setCommitted, setDraft);

    expect(setError).not.toHaveBeenCalled();
    expect(setCommitted).toHaveBeenCalledWith({ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' });
    expect(setDraft).toHaveBeenCalledWith({ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' });
  });

  it('loadPromptDraftState resets prompt draft when prompt is missing', async () => {
    vi.mocked(adminBatchGetPrompts).mockResolvedValue({
      prompts: [],
      missingCodes: ['TAG_GEN'],
    } as never);

    const setError = vi.fn();
    const setCommitted = vi.fn();
    const setDraft = vi.fn();

    await loadPromptDraftState('TAG_GEN', setError, setCommitted, setDraft);

    expect(setCommitted).toHaveBeenCalledWith(null);
    expect(setDraft).toHaveBeenCalledWith(null);
  });

  it('loadPromptDraftState reports error and resets draft when request fails', async () => {
    vi.mocked(adminBatchGetPrompts).mockRejectedValue(new Error('boom'));

    const setError = vi.fn();
    const setCommitted = vi.fn();
    const setDraft = vi.fn();

    await loadPromptDraftState('TAG_GEN', setError, setCommitted, setDraft);

    expect(setError).toHaveBeenCalledWith('boom');
    expect(setCommitted).toHaveBeenCalledWith(null);
    expect(setDraft).toHaveBeenCalledWith(null);
  });

  it('applySavedConfigState saves config and prompt draft successfully', async () => {
    const setCommittedPromptDraft = vi.fn();
    const setForm = vi.fn();
    const setCommittedForm = vi.fn();
    const setEditing = vi.fn();
    const setError = vi.fn();
    const setSavedHint = vi.fn();

    await applySavedConfigState({
      saveConfig: async () => ({ promptCode: 'TAG_GEN', enabled: true }),
      toFormState: (saved) => ({ promptCode: saved.promptCode, enabled: saved.enabled }),
      promptCode: 'TAG_GEN',
      promptDraft: { name: 'n', systemPrompt: 's', userPromptTemplate: 'u' },
      promptHasUnsavedChanges: true,
      setCommittedPromptDraft,
      setForm,
      setCommittedForm,
      setEditing,
      setError,
      setSavedHint,
    });

    expect(setCommittedPromptDraft).toHaveBeenCalledWith({ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' });
    expect(setForm).toHaveBeenCalledWith({ promptCode: 'TAG_GEN', enabled: true });
    expect(setCommittedForm).toHaveBeenCalledWith({ promptCode: 'TAG_GEN', enabled: true });
    expect(setEditing).toHaveBeenCalledWith(false);
    expect(setSavedHint).toHaveBeenCalledWith('保存成功');
  });

  it('applySavedConfigState keeps config save and reports prompt save failure', async () => {
    vi.mocked(adminUpdatePromptContent).mockRejectedValueOnce(new Error('prompt boom'));

    const setCommittedPromptDraft = vi.fn();
    const setForm = vi.fn();
    const setCommittedForm = vi.fn();
    const setEditing = vi.fn();
    const setError = vi.fn();
    const setSavedHint = vi.fn();

    await applySavedConfigState({
      saveConfig: async () => ({ promptCode: 'TITLE_GEN', enabled: false }),
      toFormState: (saved) => ({ promptCode: saved.promptCode, enabled: saved.enabled }),
      promptCode: 'TITLE_GEN',
      promptDraft: { name: 'n', systemPrompt: 's', userPromptTemplate: 'u' },
      promptHasUnsavedChanges: true,
      setCommittedPromptDraft,
      setForm,
      setCommittedForm,
      setEditing,
      setError,
      setSavedHint,
    });

    expect(setCommittedPromptDraft).not.toHaveBeenCalled();
    expect(setForm).toHaveBeenCalledWith({ promptCode: 'TITLE_GEN', enabled: false });
    expect(setCommittedForm).toHaveBeenCalledWith({ promptCode: 'TITLE_GEN', enabled: false });
    expect(setEditing).toHaveBeenCalledWith(false);
    expect(setError).toHaveBeenCalledWith('配置已保存，但提示词保存失败：prompt boom');
  });
});
