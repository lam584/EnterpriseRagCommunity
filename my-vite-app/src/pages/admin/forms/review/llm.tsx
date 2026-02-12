// llm.tsx
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  adminGetLlmModerationConfig,
  adminTestLlmModeration,
  adminUpsertLlmModerationConfig,
  type LlmModerationConfig,
  type LlmModerationTestResponse,
} from '../../../../services/moderationLlmService';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';

function clampNumber(v: number, min: number, max: number): number {
  if (!Number.isFinite(v)) return min;
  return Math.min(max, Math.max(min, v));
}

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}


type FormState = {
  promptTemplate: string;
  visionPromptTemplate: string;
  model: string;
  providerId: string;
  visionModel: string;
  visionProviderId: string;
  temperature: string;
  topP: string;
  visionTemperature: string;
  visionTopP: string;
  maxTokens: string;
  visionMaxTokens: string;
  enableThinking: boolean;
  visionEnableThinking: boolean;
  threshold: string;
  autoRun: boolean;
};

function defaultConfig(): LlmModerationConfig {
  return {
    promptTemplate: '',
    temperature: 0.2,
    topP: 0.2,
    enableThinking: false,
    visionTopP: 0.2,
    visionEnableThinking: false,
    threshold: 0.75,
    autoRun: true,
  };
}

function toFormState(cfg?: LlmModerationConfig | null): FormState {
  return {
    promptTemplate: cfg?.promptTemplate ?? '',
    visionPromptTemplate: cfg?.visionPromptTemplate ?? '',
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    visionModel: cfg?.visionModel ?? '',
    visionProviderId: cfg?.visionProviderId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    visionTemperature: cfg?.visionTemperature === null || cfg?.visionTemperature === undefined ? '' : String(cfg.visionTemperature),
    visionTopP: cfg?.visionTopP === null || cfg?.visionTopP === undefined ? '' : String(cfg.visionTopP),
    maxTokens: cfg?.maxTokens === null || cfg?.maxTokens === undefined ? '' : String(cfg.maxTokens),
    visionMaxTokens: cfg?.visionMaxTokens === null || cfg?.visionMaxTokens === undefined ? '' : String(cfg.visionMaxTokens),
    enableThinking: Boolean(cfg?.enableThinking),
    visionEnableThinking: Boolean(cfg?.visionEnableThinking),
    threshold: cfg?.threshold === null || cfg?.threshold === undefined ? '' : String(cfg.threshold),
    autoRun: Boolean(cfg?.autoRun),
  };
}

function validateForm(s: FormState): string[] {
  const errors: string[] = [];
  const prompt = s.promptTemplate.trim();
  if (!prompt) errors.push('提示词不能为空');
  if (prompt && prompt.length < 20) errors.push('提示词建议不少于 20 个字符（避免过短导致输出不稳定）');
  if (prompt.length > 8000) errors.push('提示词过长（> 8000），请精简或拆分');

  const visionPrompt = s.visionPromptTemplate.trim();
  if (!visionPrompt) errors.push('视觉提示词不能为空');
  if (visionPrompt && visionPrompt.length < 20) errors.push('视觉提示词建议不少于 20 个字符（避免过短导致输出不稳定）');
  if (visionPrompt.length > 8000) errors.push('视觉提示词过长（> 8000），请精简或拆分');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const topP = parseOptionalNumber(s.topP);
  if (topP !== undefined && (topP < 0 || topP > 1)) errors.push('topP 需在 [0, 1] 范围内');

  const vtemp = parseOptionalNumber(s.visionTemperature);
  if (vtemp !== undefined && (vtemp < 0 || vtemp > 2)) errors.push('visionTemperature 需在 [0, 2] 范围内');

  const vtopP = parseOptionalNumber(s.visionTopP);
  if (vtopP !== undefined && (vtopP < 0 || vtopP > 1)) errors.push('visionTopP 需在 [0, 1] 范围内');

  const mt = parseOptionalNumber(s.maxTokens);
  if (mt !== undefined && (!Number.isInteger(mt) || mt < 1 || mt > 32768)) errors.push('maxTokens 需为 1~32768 的整数');

  const vmt = parseOptionalNumber(s.visionMaxTokens);
  if (vmt !== undefined && (!Number.isInteger(vmt) || vmt < 1 || vmt > 32768)) errors.push('visionMaxTokens 需为 1~32768 的整数');

  const th = parseOptionalNumber(s.threshold);
  if (th !== undefined && (th < 0 || th > 1)) errors.push('threshold 需在 [0, 1] 范围内');
  if (s.autoRun && th === undefined) errors.push('开启自动运行时，必须设置 threshold');

  return errors;
}

function buildConfigPayload(s: FormState): LlmModerationConfig {
  const temperature = parseOptionalNumber(s.temperature);
  const topP = parseOptionalNumber(s.topP);
  const visionTemperature = parseOptionalNumber(s.visionTemperature);
  const visionTopP = parseOptionalNumber(s.visionTopP);
  const maxTokens = parseOptionalNumber(s.maxTokens);
  const visionMaxTokens = parseOptionalNumber(s.visionMaxTokens);
  const threshold = parseOptionalNumber(s.threshold);

  return {
    promptTemplate: s.promptTemplate,
    visionPromptTemplate: s.visionPromptTemplate,
    model: s.model.trim() ? s.model.trim() : undefined,
    providerId: s.providerId.trim() ? s.providerId.trim() : undefined,
    visionModel: s.visionModel.trim() ? s.visionModel.trim() : undefined,
    visionProviderId: s.visionProviderId.trim() ? s.visionProviderId.trim() : undefined,
    temperature: temperature === undefined ? undefined : clampNumber(temperature, 0, 2),
    topP: topP === undefined ? undefined : clampNumber(topP, 0, 1),
    visionTemperature: visionTemperature === undefined ? undefined : clampNumber(visionTemperature, 0, 2),
    visionTopP: visionTopP === undefined ? undefined : clampNumber(visionTopP, 0, 1),
    maxTokens: maxTokens === undefined ? undefined : Math.trunc(maxTokens),
    visionMaxTokens: visionMaxTokens === undefined ? undefined : Math.trunc(visionMaxTokens),
    enableThinking: s.enableThinking,
    visionEnableThinking: s.visionEnableThinking,
    threshold: threshold === undefined ? undefined : clampNumber(threshold, 0, 1),
    autoRun: s.autoRun,
  };
}

const LlmForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const initialQueueId = useMemo(() => {
    const raw = searchParams.get('queueId');
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) && n > 0 ? n : undefined;
  }, [searchParams]);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);
  const visionProviders = useMemo(() => {
    const list = providers.filter((p) => p.supportsVision === true);
    return list.length > 0 ? list : providers;
  }, [providers]);

  const [form, setForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [isEditing, setIsEditing] = useState(false);

  // 试运行
  const [queueId, setQueueId] = useState<string>(initialQueueId ? String(initialQueueId) : '');

  // If URL queueId changes (navigate to same page with another queueId), keep state in sync
  useEffect(() => {
    if (!initialQueueId) return;
    setQueueId(String(initialQueueId));
  }, [initialQueueId]);

  const [testText, setTestText] = useState<string>('');
  const [testImagesRaw, setTestImagesRaw] = useState<string>('');
  const [testResult, setTestResult] = useState<LlmModerationTestResponse | null>(null);

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    // 简单可靠：按字段对比（避免 JSON.stringify 因 key 顺序/空格等问题导致假阳性）
    return (
      form.promptTemplate !== committedForm.promptTemplate ||
      form.visionPromptTemplate !== committedForm.visionPromptTemplate ||
      form.model !== committedForm.model ||
      form.providerId !== committedForm.providerId ||
      form.visionModel !== committedForm.visionModel ||
      form.visionProviderId !== committedForm.visionProviderId ||
      form.temperature !== committedForm.temperature ||
      form.topP !== committedForm.topP ||
      form.visionTemperature !== committedForm.visionTemperature ||
      form.visionTopP !== committedForm.visionTopP ||
      form.maxTokens !== committedForm.maxTokens ||
      form.visionMaxTokens !== committedForm.visionMaxTokens ||
      form.enableThinking !== committedForm.enableThinking ||
      form.visionEnableThinking !== committedForm.visionEnableThinking ||
      form.threshold !== committedForm.threshold ||
      form.autoRun !== committedForm.autoRun
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
      const cfg = await adminGetLlmModerationConfig();
      const prompt = cfg?.promptTemplate?.trim();
      if (!prompt) {
        const next = toFormState({ ...defaultConfig(), ...cfg });
        setForm(next);
        setCommittedForm(next);
        setIsEditing(false);
        setSavedHint('后端配置为空，请点击「编辑配置」并保存提示词写入数据库');
      } else {
        const next = toFormState(cfg);
        setForm(next);
        setCommittedForm(next);
        setIsEditing(false);
      }
    } catch (e) {
      const next = toFormState(defaultConfig());
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
      const payload = buildConfigPayload(form);
      const saved = await adminUpsertLlmModerationConfig(payload);
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

  const runTest = useCallback(async () => {
    const errs = validateForm(form);
    if (errs.length > 0) {
      setError(errs.join('；'));
      return;
    }

    const qid = parseOptionalNumber(queueId);
    const text = testText.trim();
    const images = testImagesRaw
      .split('\n')
      .map((x) => x.trim())
      .filter(Boolean)
      .slice(0, 5)
      .map((url) => ({ url }));

    if (!qid && !text && images.length === 0) {
      setError('请填写 queueId、测试文本或图片 URL');
      return;
    }

    setTesting(true);
    setError(null);
    setTestResult(null);

    try {
      const payload = {
        queueId: qid,
        text: text || undefined,
        images: images.length ? images : undefined,
        // 允许试运行使用当前表单配置（避免必须先保存）
        configOverride: buildConfigPayload(form),
      };
      const res = await adminTestLlmModeration(payload);
      setTestResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [form, queueId, testText, testImagesRaw]);

  const qidForHistory = useMemo(() => {
    const n = Number(queueId);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [queueId]);

  return (
    <div className="space-y-3">
      <div className="bg-white rounded-lg shadow p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">LLM 审核层</h3>
            <div className="text-sm text-gray-500">
              这里配置“让大模型怎么审、怎么输出结果”的提示词与推理参数，并支持对指定内容试运行。
              分数如何映射为通过/拒绝/转人工，通常在「置信回退机制」里配置（本页更偏模型与提示词本身）。
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">LLM自动审核：</span>
              <select
                value={form.autoRun ? 'true' : 'false'}
                disabled={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, autoRun: e.target.value === 'true' }));
                  setSavedHint(null);
                }}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.autoRun ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
              >
                <option value="true" className="text-green-600">
                  开启
                </option>
                <option value="false" className="text-red-600">
                  关闭
                </option>
              </select>
            </div>
            <div className="text-xs text-gray-500 w-full">
              开启后，后台会定时从待审队列中取任务交给 LLM 自动审核并落库结果；关闭则只在需要时手动试运行/人工触发。
            </div>

            <button
              type="button"
              onClick={() => {
                void loadConfig();
                setError(null);
              }}
              className="rounded border px-3 py-1.5 disabled:opacity-60"
              disabled={loading || saving || testing}
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
                disabled={loading || saving || testing}
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
                  disabled={loading || saving || testing}
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

        {error ? (
          <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>
        ) : null}

        {!isEditing && hasUnsavedChanges ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 text-yellow-800 px-3 py-2 text-sm">
            当前表单与已生效配置不一致（可能来自自动填充/接口回退）。如需修改请点击「编辑配置」。
          </div>
        ) : null}

        {/* 配置表单 */}
        <div className="space-y-2">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-[360px_1fr]">
            <div className="space-y-2">
              <div className="text-sm font-semibold text-gray-700">文本审核模型</div>
              <ProviderModelSelect
                providers={providers}
                activeProviderId={activeProviderId}
                chatProviders={chatProviders}
                mode="chat"
                providerId={form.providerId}
                autoOptionLabel="自动（均衡负载）"
                model={form.model}
                disabled={!isEditing}
                onChange={(next) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, providerId: next.providerId, model: next.model }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500">选择用于“审核文本内容”的模型与提供方；不确定时用“自动（均衡负载）”。</div>

              <div>
                <div className="text-sm font-medium mb-1">温度</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：0.2"
                  value={form.temperature}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, temperature: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">影响输出随机性：越低越稳定、越可复现；越高越发散。一般建议 0~0.5。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">TOP-P</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：0.2"
                  value={form.topP}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, topP: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">影响采样范围：越低越保守、越稳定。一般建议 0.1~0.5。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">最大输出 tokens（maxTokens）</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：1024"
                  value={form.maxTokens}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, maxTokens: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">限制模型本次回答的最长长度，避免输出过长导致耗时/费用增加。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">兜底阈值（threshold，0~1）</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：0.75"
                  value={form.threshold}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, threshold: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">
                  当模型只返回 score、没有返回明确 decision 时的兜底：score ≥ threshold 判定 REJECT，否则 APPROVE。阈值越低越容易拒绝。
                </div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">启用深度思考</div>
                <select
                  value={form.enableThinking ? 'true' : 'false'}
                  disabled={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, enableThinking: e.target.value === 'true' }));
                    setSavedHint(null);
                  }}
                  className={`w-full rounded border px-3 py-1.5 text-sm font-semibold focus:outline-none ${
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
                <div className="text-xs text-gray-500 mt-1">部分模型的“思考模式”会更慢/更贵；只有在确实需要更强推理时再开启。</div>
              </div>
            </div>

            <div>
              <div className="text-sm font-medium mb-1">
                提示词（Prompt Template）
                {!isEditing ? <span className="text-xs text-gray-500 ml-2">（只读，点击右上角「编辑配置」修改）</span> : null}
              </div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={8}
                placeholder="请输入审核提示词。建议要求模型输出严格 JSON，例如：{decision, score, reasons, riskTags}（decision=APPROVE/REJECT/HUMAN，score=0~1）"
                value={form.promptTemplate}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, promptTemplate: e.target.value }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.promptTemplate.length}（建议 20~8000）</div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-[360px_1fr]">
            <div className="space-y-2">
              <div className="text-sm font-semibold text-gray-700">图片审核模型</div>

              <ProviderModelSelect
                providers={visionProviders}
                activeProviderId={activeProviderId}
                chatProviders={chatProviders}
                mode="chat"
                providerId={form.visionProviderId}
                model={form.visionModel}
                disabled={!isEditing}
                label="视觉模型:"
                autoOptionLabel="自动（均衡负载）"
                includeProviderOnlyOptions
                onChange={(next) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, visionProviderId: next.providerId, visionModel: next.model }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500">选择用于“看图并判断风险”的视觉模型；不确定时用“自动（均衡负载）”。</div>

              <div>
                <div className="text-sm font-medium mb-1">温度</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：0.2"
                  value={form.visionTemperature}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, visionTemperature: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">越低越稳定；建议与文本审核保持一致或更低。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">TOP-P</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：0.2"
                  value={form.visionTopP}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, visionTopP: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">越低越保守、越稳定；不确定时保持默认即可。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">最大输出 tokens（visionMaxTokens）</div>
                <input
                  className="w-full rounded border px-3 py-1.5 disabled:bg-gray-50"
                  placeholder="例如：1024"
                  value={form.visionMaxTokens}
                  readOnly={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, visionMaxTokens: e.target.value }));
                    setSavedHint(null);
                  }}
                />
                <div className="text-xs text-gray-500 mt-1">限制视觉模型输出长度，避免生成过长描述/理由。</div>
              </div>

              <div>
                <div className="text-sm font-medium mb-1">启用深度思考</div>
                <select
                  value={form.visionEnableThinking ? 'true' : 'false'}
                  disabled={!isEditing}
                  onChange={(e) => {
                    if (!isEditing) return;
                    setForm((p) => ({ ...p, visionEnableThinking: e.target.value === 'true' }));
                    setSavedHint(null);
                  }}
                  className={`w-full rounded border px-3 py-1.5 text-sm font-semibold focus:outline-none ${
                    form.visionEnableThinking ? 'text-purple-700 border-purple-200 bg-white' : 'text-gray-700 border-gray-200 bg-white'
                  } disabled:opacity-60 disabled:bg-gray-100`}
                >
                  <option value="false" className="text-gray-700">
                    关闭
                  </option>
                  <option value="true" className="text-purple-700">
                    开启
                  </option>
                </select>
                <div className="text-xs text-gray-500 mt-1">开启可能提升复杂图片判断能力，但会增加耗时与费用。</div>
              </div>
            </div>

            <div>
              <div className="text-sm font-medium mb-1">
                视觉提示词（Prompt Template）
                {!isEditing ? <span className="text-xs text-gray-500 ml-2">（只读，点击右上角「编辑配置」修改）</span> : null}
              </div>
              <textarea
                className="w-full rounded border px-3 py-1.5 font-mono text-sm disabled:bg-gray-50"
                rows={8}
                placeholder="请输入视觉审核提示词。建议要求模型输出严格 JSON，例如：{decision, score, reasons, riskTags, description}（description=图片内容文字描述）"
                value={form.visionPromptTemplate}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, visionPromptTemplate: e.target.value }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">当前长度：{form.visionPromptTemplate.length}（建议 20~8000）</div>
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

      <div className="bg-white rounded-lg shadow p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-lg font-semibold">LLM 试运行</div>
            <div className="text-sm text-gray-500">
              用于快速验证“提示词 + 参数”的效果：可以用 queueId 拉取真实待审内容，也可以手动粘贴文本/图片 URL。
              试运行默认使用当前表单配置（即使还没保存），方便边调边看结果。
            </div>
          </div>
          <button
            type="button"
            onClick={runTest}
            className="rounded bg-blue-600 text-white px-3 py-1.5 disabled:opacity-60"
            disabled={testing}
          >
            {testing ? '运行中…' : '运行试审核'}
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <div>
            <div className="text-sm font-medium mb-1">queueId（可选）</div>
            <input
              className="w-full md:max-w-[240px] rounded border px-3 py-1.5"
              placeholder="例如：123"
              value={queueId}
              onChange={(e) => setQueueId(e.target.value)}
            />
            {initialQueueId ? <div className="text-xs text-gray-500 mt-1">已从 URL 读取 queueId={initialQueueId}</div> : null}
            <div className="text-xs text-gray-500 mt-1">填写后会从待审队列加载对应内容；如果同时填写了测试文本/图片，将优先使用你手动输入的内容。</div>
          </div>

          <div className="md:col-span-2">
            <div className="text-sm font-medium mb-1">测试文本（可选）</div>
            <textarea
              className="w-full rounded border px-3 py-1.5 text-sm"
              rows={4}
              placeholder="粘贴要审核的文本（建议包含标题/正文/关键信息）…"
              value={testText}
              onChange={(e) => setTestText(e.target.value)}
            />
            <div className="text-xs text-gray-500 mt-1">不涉及图片时，主要看 decision/score/reasons 是否符合预期。</div>
          </div>

          <div className="md:col-span-2">
            <div className="text-sm font-medium mb-1">图片 URL（可选，每行一个，最多 5 个）</div>
            <textarea
              className="w-full rounded border px-3 py-1.5 text-sm"
              rows={3}
              placeholder="https://example.com/a.png"
              value={testImagesRaw}
              onChange={(e) => setTestImagesRaw(e.target.value)}
            />
            <div className="text-xs text-gray-500 mt-1">不填写时，后端会在 queueId 指向帖子且存在图片附件时自动带入。</div>
          </div>
        </div>

        {testResult ? (
          <div className="border rounded p-3 space-y-2">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-sm">
              <div>
                <span className="text-gray-500">Decision：</span>
                <span className="font-semibold">{testResult.decision}</span>
              </div>
              <div>
                <span className="text-gray-500">Score：</span>
                <span className="font-semibold">{testResult.score ?? '—'}</span>
              </div>
              <div>
                <span className="text-gray-500">Model：</span>
                <span className="font-semibold">{testResult.model ?? (form.model.trim() ? form.model : '—')}</span>
              </div>
            </div>
            {testResult.inputMode ? <div className="text-sm text-gray-600">inputMode：{testResult.inputMode}</div> : null}

            {testResult.stages ? (
              <div className="text-sm">
                <div className="font-medium mb-1">Stages</div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                  {(['text', 'image', 'cross'] as const).map((k) => {
                    const s = testResult.stages?.[k];
                    if (!s) return null;
                    const title = k === 'text' ? 'Text' : k === 'image' ? 'Image' : 'Cross';
                    const desc = s.description ? String(s.description) : '';
                    return (
                      <div key={k} className="rounded border bg-gray-50 p-2 space-y-1">
                        <div className="text-xs font-semibold text-gray-700">{title}</div>
                        <div className="text-xs text-gray-600">
                          <span className="text-gray-500">Decision：</span>
                          <span className="font-semibold">{s.decision ?? '—'}</span>
                        </div>
                        <div className="text-xs text-gray-600">
                          <span className="text-gray-500">Score：</span>
                          <span className="font-semibold">{s.score ?? '—'}</span>
                        </div>
                        {s.model ? <div className="text-[11px] text-gray-500 break-all">Model：{s.model}</div> : null}
                        {k === 'image' && desc ? (
                          <div className="pt-1">
                            <div className="text-[11px] text-gray-500 mb-1">Description</div>
                            <pre className="whitespace-pre-wrap text-[11px] bg-white rounded border p-2 max-h-[160px] overflow-auto">{desc}</pre>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : null}

            {testResult.reasons?.length ? (
              <div className="text-sm">
                <div className="font-medium mb-1">Reasons</div>
                <ul className="list-disc ml-5 text-gray-700">
                  {testResult.reasons.map((r, idx) => (
                    <li key={idx}>{r}</li>
                  ))}
                </ul>
              </div>
            ) : null}

            {testResult.riskTags?.length ? (
              <div className="text-sm">
                <div className="font-medium mb-1">Risk Tags</div>
                <div className="flex flex-wrap gap-2">
                  {testResult.riskTags.map((t) => (
                    <span key={t} className="inline-flex px-2 py-1 rounded text-xs bg-gray-100 text-gray-800">
                      {t}
                    </span>
                  ))}
                </div>
              </div>
            ) : null}

            {testResult.images?.length ? (
              <div className="text-sm">
                <div className="font-medium mb-1">Images</div>
                <div className="space-y-1">
                  {testResult.images.slice(0, 5).map((u, idx) => (
                    <a key={`${u}-${idx}`} href={u} target="_blank" rel="noreferrer" className="text-blue-700 hover:underline break-all">
                      {u}
                    </a>
                  ))}
                </div>
              </div>
            ) : null}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-sm text-gray-600">
              <div>latencyMs：{testResult.latencyMs ?? '—'}</div>
              <div>promptTokens：{testResult.usage?.promptTokens ?? '—'}</div>
              <div>totalTokens：{testResult.usage?.totalTokens ?? '—'}</div>
            </div>

            {testResult.promptMessages?.length ? (
              <div>
                <div className="text-sm font-medium mb-1">Prompt Messages</div>
                <div className="space-y-2">
                  {testResult.promptMessages.map((m, idx) => (
                    <div key={idx} className="rounded border bg-white p-2">
                      <div className="text-[11px] font-semibold text-gray-600 mb-1">{m.role}</div>
                      <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[240px]">{m.content}</pre>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {testResult.rawModelOutput ? (
              <div>
                <div className="text-sm font-medium mb-1">Raw Output</div>
                <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[360px]">
                  {testResult.rawModelOutput}
                </pre>
              </div>
            ) : (
              <div className="text-xs text-gray-500">无 rawModelOutput（后端若不返回可忽略）</div>
            )}
          </div>
        ) : (
          <div className="text-sm text-gray-500">还没有结果。填写 queueId 或测试文本后点击「运行试审核」。</div>
        )}
      </div>

      <ModerationPipelineHistoryPanel
        title="LLM 审核层 · 历史记录"
        initialMode={qidForHistory ? { kind: 'queue', queueId: qidForHistory } : undefined}
        stageFilter={['LLM']}
      />
    </div>
  );
};

export default LlmForm;
