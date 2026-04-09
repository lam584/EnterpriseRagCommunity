import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  adminGetLlmModerationConfig,
  adminTestLlmModeration,
  adminUpsertLlmModerationConfig,
  type LlmModerationConfig,
  type LlmModerationStages,
  type LlmModerationTestResponse,
} from '../../../../services/moderationLlmService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminBatchGetPrompts, adminUpdatePromptContent, type PromptContentDTO } from '../../../../services/promptsAdminService';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';
import PromptContentCard, { type PromptContentDraft } from '../../../../components/admin/PromptContentCard';
import {
  buildProviderModelValue,
  flattenProviderModelOptions,
  parseProviderModelValue,
} from '../../../portal/assistant/pages/AssistantChatPage.shared';

type TabKey = 'config' | 'test' | 'history';

type FormState = {
  multimodalPromptCode: string;
  judgePromptCode: string;
  autoRun: boolean;
};

function toFormState(cfg?: LlmModerationConfig | null): FormState {
  return {
    multimodalPromptCode: cfg?.multimodalPromptCode ?? 'MODERATION_MULTIMODAL',
    judgePromptCode: cfg?.judgePromptCode ?? 'MODERATION_JUDGE',
    autoRun: cfg?.autoRun == null ? true : Boolean(cfg.autoRun),
  };
}

function buildConfigPayload(form: FormState): LlmModerationConfig {
  return {
    multimodalPromptCode: form.multimodalPromptCode.trim(),
    judgePromptCode: form.judgePromptCode.trim(),
    autoRun: form.autoRun,
  };
}

function toPromptDraft(dto?: PromptContentDTO | null): PromptContentDraft {
  return {
    name: dto?.name ?? '',
    systemPrompt: dto?.systemPrompt ?? '',
    userPromptTemplate: dto?.userPromptTemplate ?? '',
    visionProviderId: dto?.visionProviderId ?? null,
    visionModel: dto?.visionModel ?? null,
    temperature: dto?.temperature ?? null,
    topP: dto?.topP ?? null,
    maxTokens: dto?.maxTokens ?? null,
    enableDeepThinking: dto?.enableDeepThinking ?? null,
  };
}

function listPromptCodes(form: FormState): string[] {
  return Array.from(
    new Set([
      form.multimodalPromptCode.trim(),
      form.judgePromptCode.trim(),
    ].filter((s) => s.length > 0))
  );
}

function validateForm(form: FormState): string[] {
  const errors: string[] = [];
  if (!form.multimodalPromptCode.trim()) errors.push('必须填写多模态提示词编码');
  if (!form.judgePromptCode.trim()) errors.push('必须填写裁决提示词编码');
  return errors;
}

function parseQueueId(raw: string): number | undefined {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return Math.trunc(n);
}

function parseImageLines(raw: string): Array<{ url: string }> {
  return raw
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .slice(0, 5)
    .map((url) => ({ url }));
}

function formatEvidenceItem(raw: string): string {
  const text = String(raw ?? '').trim();
  if (!text) return '';
  if (!text.startsWith('{') && !text.startsWith('[')) return text;
  try {
    const value: unknown = JSON.parse(text);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const obj = value as Record<string, unknown>;
      const before = typeof obj.before_context === 'string' ? obj.before_context.trim() : '';
      const after = typeof obj.after_context === 'string' ? obj.after_context.trim() : '';
      if (before || after) return `锚点 前文="${before}" 后文="${after}"`;
      const quote = typeof obj.quote === 'string' ? obj.quote.trim() : '';
      if (quote) return `引文：${quote}`;
    }
  } catch {
    return text;
  }
  return text;
}

function renderStageCards(stages?: LlmModerationStages | null) {
  if (!stages) return null;
  const items: Array<{ key: keyof LlmModerationStages; title: string }> = [
    { key: 'text', title: '兼容文本' },
    { key: 'image', title: '多模态' },
    { key: 'judge', title: '裁决' },
    { key: 'upgrade', title: '升级' },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
      {items.map((item) => {
        const stage = stages[item.key];
        if (!stage) return null;
        return (
          <div key={item.key} className="rounded-lg border border-gray-200 bg-white p-3">
            <div className="text-xs font-semibold text-gray-700">{item.title}</div>
            <div className="mt-2 text-xs text-gray-600">决策：<span className="font-semibold">{stage.decision ?? '-'}</span></div>
            <div className="text-xs text-gray-600">分数：<span className="font-semibold">{stage.score ?? '-'}</span></div>
            <div className="text-xs text-gray-600">不确定性：<span className="font-semibold">{stage.uncertainty ?? '-'}</span></div>
            {stage.model ? <div className="mt-1 text-[11px] text-gray-500 break-all">模型：{stage.model}</div> : null}
            {stage.description ? (
              <pre className="mt-2 max-h-36 overflow-auto whitespace-pre-wrap rounded border border-gray-200 bg-gray-50 p-2 text-[11px]">
                {stage.description}
              </pre>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

const LlmForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const initialQueueId = useMemo(() => {
    const raw = searchParams.get('queueId');
    if (!raw) return undefined;
    return parseQueueId(raw);
  }, [searchParams]);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);

  const [form, setForm] = useState<FormState>(() => toFormState(null));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(null));
  const [isEditing, setIsEditing] = useState(false);

  const [promptLoadError, setPromptLoadError] = useState<string | null>(null);
  const [promptDrafts, setPromptDrafts] = useState<Record<string, PromptContentDraft | null>>({});
  const [committedPromptDrafts, setCommittedPromptDrafts] = useState<Record<string, PromptContentDraft | null>>({});

  const [tab, setTab] = useState<TabKey>('config');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

  const [queueId, setQueueId] = useState<string>(initialQueueId ? String(initialQueueId) : '');
  const [testText, setTestText] = useState('');
  const [testImagesRaw, setTestImagesRaw] = useState('');
  const [testResult, setTestResult] = useState<LlmModerationTestResponse | null>(null);

  useEffect(() => {
    if (initialQueueId) setQueueId(String(initialQueueId));
  }, [initialQueueId]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
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

  const flatModelOptions = useMemo(() => flattenProviderModelOptions(chatProviders), [chatProviders]);

  const multimodalModelValue = useMemo(() => {
    const draft = promptDrafts[form.multimodalPromptCode];
    if (!draft) return '';
    const providerId = String(draft.visionProviderId ?? '').trim();
    const model = String(draft.visionModel ?? '').trim();
    return buildProviderModelValue(providerId, model);
  }, [promptDrafts, form.multimodalPromptCode]);

  const multimodalModelOptions = useMemo(() => {
    if (!multimodalModelValue) return flatModelOptions;
    if (flatModelOptions.some((it) => it.value === multimodalModelValue)) return flatModelOptions;
    const parsed = parseProviderModelValue(multimodalModelValue);
    if (!parsed) return flatModelOptions;
    return [
      {
        providerId: parsed.providerId,
        providerLabel: parsed.providerId,
        model: parsed.model,
        value: multimodalModelValue,
      },
      ...flatModelOptions,
    ];
  }, [flatModelOptions, multimodalModelValue]);

  const hasUnsavedChanges = useMemo(() => {
    const formChanged =
      form.multimodalPromptCode !== committedForm.multimodalPromptCode ||
      form.judgePromptCode !== committedForm.judgePromptCode ||
      form.autoRun !== committedForm.autoRun;

    const codes = listPromptCodes(form).sort();
    const promptChanged =
      JSON.stringify(codes.map((code) => promptDrafts[code] ?? null)) !==
      JSON.stringify(codes.map((code) => committedPromptDrafts[code] ?? null));

    return formChanged || promptChanged;
  }, [form, committedForm, promptDrafts, committedPromptDrafts]);

  const loadPromptDrafts = useCallback(async (nextForm: FormState) => {
    const codes = listPromptCodes(nextForm);
    const response = await adminBatchGetPrompts(codes);
    const map: Record<string, PromptContentDraft | null> = {};
    for (const code of codes) map[code] = null;
    for (const dto of response.prompts ?? []) {
      if (!dto?.promptCode) continue;
      map[dto.promptCode] = toPromptDraft(dto);
    }
    setPromptDrafts(map);
    setCommittedPromptDrafts(map);
  }, []);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setHint(null);
    setPromptLoadError(null);
    try {
      const cfg = await adminGetLlmModerationConfig();
      const next = toFormState(cfg);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);

      try {
        await loadPromptDrafts(next);
      } catch (e) {
        setPromptLoadError(e instanceof Error ? e.message : String(e));
        setPromptDrafts({});
        setCommittedPromptDrafts({});
      }
    } catch (e) {
      const next = toFormState(null);
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      setError(e instanceof Error ? e.message : String(e));
      setPromptDrafts({});
      setCommittedPromptDrafts({});
    } finally {
      setLoading(false);
    }
  }, [loadPromptDrafts]);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  const save = useCallback(async () => {
    const validationErrors = validateForm(form);
    if (validationErrors.length > 0) {
      setError(validationErrors.join('; '));
      return;
    }

    setSaving(true);
    setError(null);
    setHint(null);

    try {
      const savedConfig = await adminUpsertLlmModerationConfig(buildConfigPayload(form));

      const codes = listPromptCodes(form).sort();
      const changedCodes = codes.filter((code) => {
        return JSON.stringify(promptDrafts[code] ?? null) !== JSON.stringify(committedPromptDrafts[code] ?? null);
      });

      const promptErrors: string[] = [];
      for (const code of changedCodes) {
        const draft = promptDrafts[code];
        if (!draft) continue;
        try {
          await adminUpdatePromptContent(code, {
            systemPrompt: draft.systemPrompt,
            userPromptTemplate: draft.userPromptTemplate,
            visionProviderId: draft.visionProviderId ?? null,
            visionModel: draft.visionModel ?? null,
            temperature: draft.temperature ?? null,
            topP: draft.topP ?? null,
            maxTokens: draft.maxTokens ?? null,
            enableDeepThinking: draft.enableDeepThinking ?? null,
          });
        } catch (e) {
          promptErrors.push(e instanceof Error ? e.message : String(e));
        }
      }

      const nextForm = toFormState(savedConfig);
      setForm(nextForm);
      setCommittedForm(nextForm);
      setIsEditing(false);
      if (promptErrors.length > 0) {
        setError(`配置已保存，但提示词内容更新失败：${promptErrors[0]}`);
      } else {
        try {
          await loadPromptDrafts(nextForm);
        } catch (e) {
          setPromptLoadError(e instanceof Error ? e.message : String(e));
        }
        setHint('保存成功');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [form, promptDrafts, committedPromptDrafts, loadPromptDrafts]);

  const cancelEdit = useCallback(() => {
    setForm(committedForm);
    setPromptDrafts(committedPromptDrafts);
    setIsEditing(false);
    setError(null);
    setHint('已丢弃未保存更改');
  }, [committedForm, committedPromptDrafts]);

  const runTest = useCallback(async () => {
    const validationErrors = validateForm(form);
    if (validationErrors.length > 0) {
      setError(validationErrors.join('; '));
      return;
    }

    const qid = parseQueueId(queueId.trim());
    const text = testText.trim();
    const images = parseImageLines(testImagesRaw);

    if (!qid && !text && images.length === 0) {
      setError('请提供 queueId、测试文本或图片 URL');
      return;
    }

    setTesting(true);
    setError(null);
    setHint(null);
    setTestResult(null);

    try {
      const res = await adminTestLlmModeration({
        queueId: qid,
        text: text || undefined,
        images: images.length ? images : undefined,
        configOverride: buildConfigPayload(form),
      });
      setTestResult(res);
      setHint('测试完成');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [form, queueId, testText, testImagesRaw]);

  const historyQueueId = useMemo(() => parseQueueId(queueId.trim()), [queueId]);

  return (
    <div className="space-y-4 w-full">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h3 className="text-xl font-semibold text-gray-900">LLM 审核</h3>
            <p className="text-sm text-gray-600 mt-1">
              主审核统一使用多模态提示词；裁决提示词继续用于升级与终审。
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button type="button" className="border rounded px-3 py-1.5 text-sm" onClick={() => void loadConfig()} disabled={loading}>
              刷新
            </button>

            {!isEditing ? (
              <button type="button" className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm" onClick={() => setIsEditing(true)} disabled={loading}>
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={cancelEdit}
                  disabled={saving}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:opacity-60"
                  onClick={save}
                  disabled={saving || !hasUnsavedChanges}
                >
                  {saving ? '保存中...' : '保存'}
                </button>
              </>
            )}
          </div>
        </div>

        {error ? <div className="rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700">{error}</div> : null}
        {hint ? <div className="rounded border border-green-200 bg-green-50 p-2 text-sm text-green-700">{hint}</div> : null}

        <div className="flex gap-2 border-b border-gray-200 pb-2">
          {([
            { key: 'config', label: '配置' },
            { key: 'test', label: '测试运行' },
            { key: 'history', label: '历史' },
          ] as const).map((item) => (
            <button
              key={item.key}
              type="button"
              className={`rounded px-3 py-1.5 text-sm ${tab === item.key ? 'bg-blue-600 text-white' : 'border text-gray-700'}`}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'config' ? (
        <div className="space-y-4">
          <div className="bg-white rounded-lg shadow p-4 space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-1 gap-3">
              <label className="text-sm">
                <div className="text-gray-700 mb-1">自动运行</div>
                <select
                  className="w-full rounded border px-3 py-2"
                  value={String(form.autoRun)}
                  disabled={!isEditing}
                  onChange={(e) => setForm((s) => ({ ...s, autoRun: e.target.value === 'true' }))}
                >
                  <option value="true">启用</option>
                  <option value="false">禁用</option>
                </select>
              </label>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4">
            <div className="bg-white rounded-lg shadow p-4 space-y-2">
              <div className="text-sm font-medium text-gray-700">多模态模型选择</div>
              <select
                className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-50 disabled:text-gray-500"
                value={multimodalModelValue}
                disabled={!isEditing || !(promptDrafts[form.multimodalPromptCode] ?? null)}
                onChange={(e) => {
                  if (!isEditing) return;
                  const nextValue = String(e.target.value ?? '');
                  const parsed = parseProviderModelValue(nextValue);
                  setPromptDrafts((s) => {
                    const current = s[form.multimodalPromptCode];
                    if (!current) return s;
                    return {
                      ...s,
                      [form.multimodalPromptCode]: {
                        ...current,
                        visionProviderId: parsed?.providerId ?? '',
                        visionModel: parsed?.model ?? '',
                      },
                    };
                  });
                }}
              >
                <option value="">自动（均衡负载）</option>
                {multimodalModelOptions.map((it) => (
                  <option key={it.value} value={it.value}>
                    {it.providerLabel}：{it.model}
                  </option>
                ))}
              </select>
              <div className="text-xs text-gray-500">从当前可用聊天模型池中选择多模态审核模型；留空表示自动路由。</div>
            </div>

            <PromptContentCard
              title="多模态提示词"
              draft={promptDrafts[form.multimodalPromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => setPromptDrafts((s) => ({ ...s, [form.multimodalPromptCode]: next }))}
              hint={promptLoadError ?? undefined}
              showRuntimeParams
            />

            <PromptContentCard
              title="裁决提示词"
              draft={promptDrafts[form.judgePromptCode] ?? null}
              editing={isEditing}
              onChange={(next) => setPromptDrafts((s) => ({ ...s, [form.judgePromptCode]: next }))}
              hint={promptLoadError ?? undefined}
              showRuntimeParams
            />
          </div>
        </div>
      ) : null}

      {tab === 'test' ? (
        <div className="space-y-4">
          <div className="bg-white rounded-lg shadow p-4 space-y-3">
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
              <label className="text-sm">
                <div className="text-gray-700 mb-1">queueId（可选）</div>
                <input className="w-full rounded border px-3 py-2" value={queueId} onChange={(e) => setQueueId(e.target.value)} />
              </label>

              <label className="text-sm lg:col-span-2">
                <div className="text-gray-700 mb-1">文本（可选）</div>
                <textarea className="w-full rounded border px-3 py-2 min-h-[110px]" value={testText} onChange={(e) => setTestText(e.target.value)} />
              </label>
            </div>

            <label className="text-sm block">
              <div className="text-gray-700 mb-1">图片 URL（可选，每行一个，最多 5 个）</div>
              <textarea className="w-full rounded border px-3 py-2 min-h-[90px]" value={testImagesRaw} onChange={(e) => setTestImagesRaw(e.target.value)} />
            </label>

            <button type="button" className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm" onClick={runTest} disabled={testing}>
              {testing ? '运行中...' : '执行测试'}
            </button>
          </div>

          <div className="bg-white rounded-lg shadow p-4 space-y-3">
            <div className="text-base font-semibold text-gray-900">结果</div>

            {testResult ? (
              <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-5 gap-3 text-sm">
                  <div className="rounded border border-gray-200 bg-gray-50 p-3">决策：<span className="font-semibold">{testResult.decision}</span></div>
                  <div className="rounded border border-gray-200 bg-gray-50 p-3">分数：<span className="font-semibold">{testResult.score ?? '-'}</span></div>
                  <div className="rounded border border-gray-200 bg-gray-50 p-3">严重度：<span className="font-semibold">{testResult.severity ?? '-'}</span></div>
                  <div className="rounded border border-gray-200 bg-gray-50 p-3">不确定性：<span className="font-semibold">{testResult.uncertainty ?? '-'}</span></div>
                  <div className="rounded border border-gray-200 bg-gray-50 p-3">模型：<span className="font-semibold">{testResult.model ?? '-'}</span></div>
                </div>

                {renderStageCards(testResult.stages)}

                {testResult.evidence?.length ? (
                  <div>
                    <div className="text-sm font-medium text-gray-900 mb-2">证据</div>
                    <ul className="list-disc ml-5 text-sm text-gray-700 space-y-1">
                      {testResult.evidence.map((item, idx) => <li key={idx}>{formatEvidenceItem(item)}</li>)}
                    </ul>
                  </div>
                ) : null}

                {testResult.reasons?.length ? (
                  <div>
                    <div className="text-sm font-medium text-gray-900 mb-2">原因</div>
                    <ul className="list-disc ml-5 text-sm text-gray-700 space-y-1">
                      {testResult.reasons.map((item, idx) => <li key={idx}>{item}</li>)}
                    </ul>
                  </div>
                ) : null}

                {testResult.riskTags?.length ? (
                  <div>
                    <div className="text-sm font-medium text-gray-900 mb-2">风险标签</div>
                    <div className="flex flex-wrap gap-2">
                      {testResult.riskTags.map((tag) => (
                        <span key={tag} className="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs text-gray-800">
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                ) : null}

                <details className="rounded border border-gray-200 bg-white p-3" open>
                  <summary className="cursor-pointer text-sm font-medium text-gray-900">原始输出</summary>
                  <pre className="mt-3 max-h-[420px] overflow-auto whitespace-pre-wrap rounded border border-gray-200 bg-gray-50 p-2 text-xs">
                    {testResult.rawModelOutput ?? '（空）'}
                  </pre>
                </details>
              </div>
            ) : (
              <div className="text-sm text-gray-500">暂无测试结果。</div>
            )}
          </div>
        </div>
      ) : null}

      {tab === 'history' ? (
        <ModerationPipelineHistoryPanel
          title="LLM 审核历史"
          initialMode={historyQueueId ? { kind: 'queue', queueId: historyQueueId } : undefined}
          stageFilter={['LLM']}
        />
      ) : null}
    </div>
  );
};

export default LlmForm;
