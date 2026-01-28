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
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';

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
  model: string;
  temperature: string;
  maxTokens: string;
  threshold: string;
  autoRun: boolean;
  maxConcurrent: string;
  minDelayMs: string;
  qps: string;
};

function defaultConfig(): LlmModerationConfig {
  return {
    promptTemplate: '',
    temperature: 0.2,
    threshold: 0.75,
    autoRun: false,
  };
}

function toFormState(cfg?: LlmModerationConfig | null): FormState {
  return {
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    maxTokens: cfg?.maxTokens === null || cfg?.maxTokens === undefined ? '' : String(cfg.maxTokens),
    threshold: cfg?.threshold === null || cfg?.threshold === undefined ? '' : String(cfg.threshold),
    autoRun: Boolean(cfg?.autoRun),
    maxConcurrent: cfg?.maxConcurrent === null || cfg?.maxConcurrent === undefined ? '' : String(cfg.maxConcurrent),
    minDelayMs: cfg?.minDelayMs === null || cfg?.minDelayMs === undefined ? '' : String(cfg.minDelayMs),
    qps: cfg?.qps === null || cfg?.qps === undefined ? '' : String(cfg.qps),
  };
}

function validateForm(s: FormState): string[] {
  const errors: string[] = [];
  const prompt = s.promptTemplate.trim();
  if (!prompt) errors.push('提示词不能为空');
  if (prompt && prompt.length < 20) errors.push('提示词建议不少于 20 个字符（避免过短导致输出不稳定）');
  if (prompt.length > 8000) errors.push('提示词过长（> 8000），请精简或拆分');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const mt = parseOptionalNumber(s.maxTokens);
  if (mt !== undefined && (!Number.isInteger(mt) || mt < 1 || mt > 32768)) errors.push('maxTokens 需为 1~32768 的整数');

  const th = parseOptionalNumber(s.threshold);
  if (th !== undefined && (th < 0 || th > 1)) errors.push('threshold 需在 [0, 1] 范围内');
  if (s.autoRun && th === undefined) errors.push('开启自动运行时，必须设置 threshold');

  const mc = parseOptionalNumber(s.maxConcurrent);
  if (mc !== undefined && (!Number.isInteger(mc) || mc < 1 || mc > 100)) errors.push('maxConcurrent 需为 1~100 的整数');
  const md = parseOptionalNumber(s.minDelayMs);
  if (md !== undefined && (!Number.isInteger(md) || md < 0 || md > 60000)) errors.push('minDelayMs 需为 0~60000 的整数(ms)');
  const qps = parseOptionalNumber(s.qps);
  if (qps !== undefined && (qps < 0 || qps > 1000)) errors.push('qps 需在 [0, 1000] 范围内（0 表示不限制）');

  return errors;
}

function buildConfigPayload(s: FormState): LlmModerationConfig {
  const temperature = parseOptionalNumber(s.temperature);
  const maxTokens = parseOptionalNumber(s.maxTokens);
  const threshold = parseOptionalNumber(s.threshold);
  const maxConcurrent = parseOptionalNumber(s.maxConcurrent);
  const minDelayMs = parseOptionalNumber(s.minDelayMs);
  const qps = parseOptionalNumber(s.qps);

  return {
    promptTemplate: s.promptTemplate,
    model: s.model.trim() ? s.model.trim() : undefined,
    temperature: temperature === undefined ? undefined : clampNumber(temperature, 0, 2),
    maxTokens: maxTokens === undefined ? undefined : Math.trunc(maxTokens),
    threshold: threshold === undefined ? undefined : clampNumber(threshold, 0, 1),
    autoRun: s.autoRun,
    maxConcurrent: maxConcurrent === undefined ? undefined : Math.trunc(maxConcurrent),
    minDelayMs: minDelayMs === undefined ? undefined : Math.trunc(minDelayMs),
    qps: qps === undefined ? undefined : clampNumber(qps, 0, 1000),
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

  const [form, setForm] = useState<FormState>(() => toFormState(null));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(null));
  const [isEditing, setIsEditing] = useState(false);

  // 试运行
  const [queueId, setQueueId] = useState<string>(initialQueueId ? String(initialQueueId) : '');

  // If URL queueId changes (navigate to same page with another queueId), keep state in sync
  useEffect(() => {
    if (!initialQueueId) return;
    setQueueId(String(initialQueueId));
  }, [initialQueueId]);

  const [testText, setTestText] = useState<string>('');
  const [testResult, setTestResult] = useState<LlmModerationTestResponse | null>(null);

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    // 简单可靠：按字段对比（避免 JSON.stringify 因 key 顺序/空格等问题导致假阳性）
    return (
      form.promptTemplate !== committedForm.promptTemplate ||
      form.model !== committedForm.model ||
      form.temperature !== committedForm.temperature ||
      form.maxTokens !== committedForm.maxTokens ||
      form.threshold !== committedForm.threshold ||
      form.autoRun !== committedForm.autoRun ||
      form.maxConcurrent !== committedForm.maxConcurrent ||
      form.minDelayMs !== committedForm.minDelayMs ||
      form.qps !== committedForm.qps
    );
  }, [form, committedForm]);

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
    if (!qid && !text) {
      setError('请填写 queueId 或输入要审核的文本');
      return;
    }

    setTesting(true);
    setError(null);
    setTestResult(null);

    try {
      const payload = {
        queueId: qid,
        text: text || undefined,
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
  }, [form, queueId, testText]);

  const qidForHistory = useMemo(() => {
    const n = Number(queueId);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [queueId]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">LLM 审核层</h3>
            <div className="text-sm text-gray-500">配置大模型审核提示词与参数，并支持对指定内容试运行。</div>
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

            <button
              type="button"
              onClick={() => {
                void loadConfig();
                setError(null);
              }}
              className="rounded border px-3 py-2 disabled:opacity-60"
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
                className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
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
                  className="rounded border px-3 py-2 disabled:opacity-60"
                  disabled={loading || saving || testing}
                  title="放弃未保存的修改，并恢复到最近一次加载/保存的配置"
                >
                  放弃修改
                </button>
                <button
                  type="button"
                  onClick={save}
                  className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
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
        <div className="space-y-3">
          <div>
            <div className="text-sm font-medium mb-1">
              提示词（Prompt Template）
              {!isEditing ? <span className="text-xs text-gray-500 ml-2">（只读，点击右上角「编辑配置」修改）</span> : null}
            </div>
            <textarea
              className="w-full rounded border px-3 py-2 font-mono text-sm disabled:bg-gray-50"
              rows={10}
              placeholder="请输入审核提示词。建议要求模型输出严格 JSON：{decision, score, reasons, riskTags}..."
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

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <div className="text-sm font-medium mb-1">模型（可选）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：qwen-plus / qwen-max"
                value={form.model}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, model: e.target.value }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">temperature（可选，0~2）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：0.2"
                value={form.temperature}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, temperature: e.target.value }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">maxTokens（可选）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：1024"
                value={form.maxTokens}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, maxTokens: e.target.value }));
                  setSavedHint(null);
                }}
              />
            </div>

            <div>
              <div className="text-sm font-medium mb-1">threshold（可选，0~1）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：0.75"
                value={form.threshold}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, threshold: e.target.value }));
                  setSavedHint(null);
                }}
              />
            </div>


          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <div className="text-sm font-medium mb-1">maxConcurrent（并发数，可选）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：4"
                value={form.maxConcurrent}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, maxConcurrent: e.target.value }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">建议 1~10；越大越容易打爆上游</div>
            </div>

            <div>
              <div className="text-sm font-medium mb-1">minDelayMs（请求间隔，可选）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：200"
                value={form.minDelayMs}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, minDelayMs: e.target.value }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">0 表示不加延迟</div>
            </div>

            <div>
              <div className="text-sm font-medium mb-1">qps（可选，0=不限）</div>
              <input
                className="w-full rounded border px-3 py-2 disabled:bg-gray-50"
                placeholder="例如：2"
                value={form.qps}
                readOnly={!isEditing}
                onChange={(e) => {
                  if (!isEditing) return;
                  setForm((p) => ({ ...p, qps: e.target.value }));
                  setSavedHint(null);
                }}
              />
              <div className="text-xs text-gray-500 mt-1">用于全局限流</div>
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

        {/* 试运行 */}
        <div className="border-t pt-4 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-base font-semibold">LLM 试运行</div>
              <div className="text-sm text-gray-500">可用 queueId 直接拉取待审内容，或粘贴一段文本进行测试。</div>
            </div>
            <button
              type="button"
              onClick={runTest}
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
              disabled={testing}
            >
              {testing ? '运行中…' : '运行试审核'}
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <div className="text-sm font-medium mb-1">queueId（可选）</div>
              <input
                className="w-full rounded border px-3 py-2"
                placeholder="例如：123"
                value={queueId}
                onChange={(e) => setQueueId(e.target.value)}
              />
              {initialQueueId ? (
                <div className="text-xs text-gray-500 mt-1">已从 URL 读取 queueId={initialQueueId}</div>
              ) : null}
            </div>

            <div>
              <div className="text-sm font-medium mb-1">测试文本（可选）</div>
              <textarea
                className="w-full rounded border px-3 py-2 text-sm"
                rows={4}
                placeholder="粘贴要审核的文本..."
                value={testText}
                onChange={(e) => setTestText(e.target.value)}
              />
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

              <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-sm text-gray-600">
                <div>latencyMs：{testResult.latencyMs ?? '—'}</div>
                <div>promptTokens：{testResult.usage?.promptTokens ?? '—'}</div>
                <div>totalTokens：{testResult.usage?.totalTokens ?? '—'}</div>
              </div>

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
