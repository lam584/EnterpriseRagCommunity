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

const DEFAULT_PROMPT_TEMPLATE = `你是一个【社区内容审核】模型。你只负责审核，不要与用户聊天，也不要提供任何建议。
你的任务：依据【审核标准】审查【待审核内容】，并且【只输出严格 JSON】。

【关键要求（必须遵守）】
1) 你必须只输出 1 个 JSON 对象（不要 Markdown、不要代码块、不要解释、不要多余换行前后缀）。
2) JSON 必须能被直接解析：使用双引号、布尔/数字合法、数组合法、无尾逗号、无评论。
3) 你不得输出除下述字段外的任何字段；不得输出待审核内容的复述/摘抄。
4) 如遇信息不足、语境不清、疑似转述/引用导致无法判断真实意图：decision=HUMAN，并在 reasons 写明缺失信息。
5) 输出必须稳定：decision 与 score 区间必须匹配（见【评分与决策映射】）。

【输入说明】
- 待审核内容可能来自：帖子标题/正文/评论/用户签名/链接描述/转发文本。
- 待审核内容可能包含：URL、账号、群号、二维码描述、联系方式、口语、错别字、谐音、拆字、emoji。

【输出字段】
- decision: "APPROVE" | "REJECT" | "HUMAN"
- score: 0~1 风险分（越高越危险）
- reasons: 1~5 条简短原因（中文，尽量不复述敏感细节，不包含个人信息；每条<=25字为佳）
- riskTags: 风险标签数组（字符串，尽量用给定示例；可为空数组）

【评分与决策映射（默认）】
- 明确违规：decision=REJECT 且 score>=0.85
- 高风险/不确定：decision=HUMAN 且 0.60<=score<0.85
- 低风险：decision=APPROVE 且 score<0.60
注意：当 decision 为 REJECT/HUMAN 时，score 也必须落在对应区间内（不要出现 REJECT 却给 0.3）。

【审核标准（更细化）】
A. 直接拒绝（REJECT）——出现以下任一类，且语义明确：
- 色情/性服务："sexual_content"、"sex_service"、"porn_ad"
  - 成人内容引流、露骨性描写、招嫖、性交易、涉未成年人性内容（极高危）
- 暴恐/血腥/武器："terror"、"violence"、"weapon"、"extremism"
  - 暴力威胁、虐杀血腥、极端主义宣传、武器/爆炸物制作与购买渠道
- 诈骗/赌博/黑产/引流："fraud"、"gambling"、"black_market"、"traffic_diversion"
  - 诈骗话术、博彩引流、售卖违禁品、灰黑产导流、提供联系方式/群号/二维码/外链
- 隐私泄露/人肉："privacy"、"doxxing"
  - 身份证/手机号/住址/银行卡/人脸等敏感信息，或诱导收集与曝光
- 明确违法教程/交易："illegal_instruction"
  - 教唆违法、提供可操作步骤/渠道/价格/购买方式（含暗语）

B. 转人工（HUMAN）——高风险但需要上下文判断：
- 仇恨/辱骂/人身攻击："hate"、"harassment"
  - 针对群体的歧视/仇恨；强烈辱骂、威胁但语境不清（如玩笑/互喷/引用）
- 涉政敏感/煽动："politics"、"incitement"
  - 号召动员、煽动对立、可能引战的内容
- 高风险建议："medical"、"legal"、"finance"
  - 医疗/法律/金融建议可能造成现实伤害，且缺少资质声明或上下文
- 上下文不足："insufficient_context"
  - 断章取义、引用不明、无法判断是否为讽刺/转述/学术讨论

C. 允许通过（APPROVE）——风险低且不包含上述违规：
- 正常交流、技术讨论、学习资料、日常吐槽、客观新闻/科普（不含引流与隐私）

【riskTags 选取建议】
- 可多选；如无法归类可留空数组。
- 引流/联系方式优先加："traffic_diversion"。

【输出格式（再次强调）】
你必须只输出一个 JSON 对象，结构如下：
{
  "decision": "APPROVE"|"REJECT"|"HUMAN",
  "score": number,
  "reasons": string[],
  "riskTags": string[]
}

【待审核内容】
{{text}}`;

function defaultConfig(): LlmModerationConfig {
  return {
    promptTemplate: DEFAULT_PROMPT_TEMPLATE,
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
        // 后端返回空配置时，使用前端默认值兜底
        const next = toFormState({ ...defaultConfig(), ...cfg });
        setForm(next);
        setCommittedForm(next);
        setIsEditing(false);
        setSavedHint('后端配置为空，已加载内置默认值提示词（可点击「编辑配置」后保存写入数据库）');
      } else {
        const next = toFormState(cfg);
        setForm(next);
        setCommittedForm(next);
        setIsEditing(false);
      }
    } catch (e) {
      // 后端未实现/网络问题时，允许页面仍可用
      const next = toFormState(defaultConfig());
      setForm(next);
      setCommittedForm(next);
      setIsEditing(false);
      setError(e instanceof Error ? e.message : String(e));
      setSavedHint('后端接口不可用，已加载前端默认提示词（可用于快速演示）');
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
          <div className="flex flex-col items-end gap-2">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  if (!isEditing) return;
                  setForm(toFormState(defaultConfig()));
                  setSavedHint('已恢复为内置默认提示词（记得点「保存配置」写入数据库）');
                  setError(null);
                }}
                className="rounded border px-3 py-2 disabled:opacity-60"
                disabled={!isEditing || loading || saving || testing}
                title={!isEditing ? '请先点击「编辑配置」' : '将表单恢复为内置默认提示词（不会自动保存）'}
              >
                恢复默认
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

            <div className="flex items-center gap-2 bg-gray-50 px-3 py-2 rounded border border-gray-200">
              <span className="text-sm font-medium text-gray-700">自动运行LLM审核： </span>
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
              <div className="text-xs text-gray-500 mt-1">用于全局限流（近似）</div>
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
