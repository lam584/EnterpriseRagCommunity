import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminGetPostTitleGenConfig,
  adminListPostTitleGenHistory,
  adminUpsertPostTitleGenConfig,
  type Page,
  type PostTitleGenConfigDTO,
  type PostTitleGenHistoryDTO,
} from '../../../../services/titleGenAdminService';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { suggestPostTitles, type AiPostTitleSuggestResponse } from '../../../../services/aiTitleService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';

type FormState = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model: string;
  providerId: string;
  temperature: string;
  topP: string;
  enableThinking: boolean;
  defaultCount: string;
  maxCount: string;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): PostTitleGenConfigDTO {
  return {
    enabled: true,
    systemPrompt: '你是专业的中文社区运营编辑，擅长给帖子拟标题。',
    promptTemplate: `请为下面这段社区帖子内容生成 {{count}} 个中文标题候选。\n要求：\n- 每个标题不超过 30 个汉字\n- 风格适度多样（提问式/总结式/爆点式），但不要低俗\n- 标题之间不要重复\n- 只输出严格 JSON，不要输出任何解释文字\n- JSON 格式：{\"titles\":[\"...\", \"...\"]}\n\n{{boardLine}}{{tagsLine}}帖子内容：\n{{content}}\n`,
    temperature: 0.4,
    topP: 0.9,
    enableThinking: false,
    defaultCount: 5,
    maxCount: 10,
    maxContentChars: 4000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
  };
}

function toFormState(cfg?: PostTitleGenConfigDTO | null): FormState {
  return {
    enabled: Boolean(cfg?.enabled),
    systemPrompt: cfg?.systemPrompt ?? '',
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    providerId: cfg?.providerId ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    topP: cfg?.topP === null || cfg?.topP === undefined ? '' : String(cfg.topP),
    enableThinking: Boolean(cfg?.enableThinking),
    defaultCount: cfg?.defaultCount === null || cfg?.defaultCount === undefined ? '' : String(cfg.defaultCount),
    maxCount: cfg?.maxCount === null || cfg?.maxCount === undefined ? '' : String(cfg.maxCount),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    historyEnabled: Boolean(cfg?.historyEnabled),
    historyKeepDays: cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined ? '' : String(cfg.historyKeepDays),
    historyKeepRows: cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined ? '' : String(cfg.historyKeepRows),
  };
}

function validateForm(s: FormState): string[] {
  const errors: string[] = [];

  if (!s.systemPrompt.trim()) errors.push('systemPrompt 不能为空');
  if (!s.promptTemplate.trim()) errors.push('promptTemplate 不能为空');
  if (s.promptTemplate.trim().length < 50) errors.push('promptTemplate 建议不少于 50 个字符（避免过短导致输出不稳定）');
  if (s.promptTemplate.length > 20000) errors.push('promptTemplate 过长（> 20000），请精简');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const topP = parseOptionalNumber(s.topP);
  if (topP !== undefined && (topP < 0 || topP > 1)) errors.push('topP 需在 [0, 1] 范围内');

  const dc = parseOptionalNumber(s.defaultCount);
  const mc = parseOptionalNumber(s.maxCount);
  if (dc !== undefined && (!Number.isInteger(dc) || dc < 1 || dc > 50)) errors.push('defaultCount 需为 1~50 的整数');
  if (mc !== undefined && (!Number.isInteger(mc) || mc < 1 || mc > 50)) errors.push('maxCount 需为 1~50 的整数');
  if (dc !== undefined && mc !== undefined && dc > mc) errors.push('defaultCount 不能大于 maxCount');

  const mcc = parseOptionalNumber(s.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 50000)) errors.push('maxContentChars 需为 200~50000 的整数');

  const hkd = parseOptionalNumber(s.historyKeepDays);
  if (hkd !== undefined && (!Number.isInteger(hkd) || hkd < 1)) errors.push('historyKeepDays 必须为正整数');
  const hkr = parseOptionalNumber(s.historyKeepRows);
  if (hkr !== undefined && (!Number.isInteger(hkr) || hkr < 1)) errors.push('historyKeepRows 必须为正整数');

  return errors;
}

function buildPayload(s: FormState) {
  const temperature = parseOptionalNumber(s.temperature);
  const topP = parseOptionalNumber(s.topP);
  const defaultCount = parseOptionalNumber(s.defaultCount);
  const maxCount = parseOptionalNumber(s.maxCount);
  const maxContentChars = parseOptionalNumber(s.maxContentChars);
  const historyKeepDays = parseOptionalNumber(s.historyKeepDays);
  const historyKeepRows = parseOptionalNumber(s.historyKeepRows);

  return {
    enabled: s.enabled,
    systemPrompt: s.systemPrompt,
    promptTemplate: s.promptTemplate,
    model: s.model.trim() ? s.model.trim() : null,
    providerId: s.providerId.trim() ? s.providerId.trim() : null,
    temperature: temperature === undefined ? null : temperature,
    topP: topP === undefined ? null : topP,
    enableThinking: s.enableThinking,
    defaultCount: defaultCount === undefined ? 5 : Math.trunc(defaultCount),
    maxCount: maxCount === undefined ? 10 : Math.trunc(maxCount),
    maxContentChars: maxContentChars === undefined ? 4000 : Math.trunc(maxContentChars),
    historyEnabled: s.historyEnabled,
    historyKeepDays: historyKeepDays === undefined ? null : Math.trunc(historyKeepDays),
    historyKeepRows: historyKeepRows === undefined ? null : Math.trunc(historyKeepRows),
  };
}

const TitleGenForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

  const [form, setForm] = useState<FormState>(() => toFormState(null));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(null));

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    return (
      form.enabled !== committedForm.enabled ||
      form.systemPrompt !== committedForm.systemPrompt ||
      form.promptTemplate !== committedForm.promptTemplate ||
      form.model !== committedForm.model ||
      form.providerId !== committedForm.providerId ||
      form.temperature !== committedForm.temperature ||
      form.topP !== committedForm.topP ||
      form.enableThinking !== committedForm.enableThinking ||
      form.defaultCount !== committedForm.defaultCount ||
      form.maxCount !== committedForm.maxCount ||
      form.maxContentChars !== committedForm.maxContentChars ||
      form.historyEnabled !== committedForm.historyEnabled ||
      form.historyKeepDays !== committedForm.historyKeepDays ||
      form.historyKeepRows !== committedForm.historyKeepRows
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
      const cfg = await adminGetPostTitleGenConfig();
      const prompt = cfg?.promptTemplate?.trim();
      if (!prompt) {
        const next = toFormState({ ...defaultConfig(), ...cfg });
        setForm(next);
        setCommittedForm(next);
        setEditing(false);
        setSavedHint('后端配置为空，已加载内置默认值（可编辑后保存写入数据库）');
      } else {
        const next = toFormState(cfg);
        setForm(next);
        setCommittedForm(next);
        setEditing(false);
      }
    } catch (e) {
      const next = toFormState(defaultConfig());
      setForm(next);
      setCommittedForm(next);
      setEditing(false);
      setError(e instanceof Error ? e.message : String(e));
      setSavedHint('后端接口不可用，已加载前端默认配置（可用于演示）');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

  const onSave = useCallback(async () => {
    if (!canSave) return;
    if (saving) return;
    setSaving(true);  
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertPostTitleGenConfig(payload);
      const next = toFormState(saved);
      setForm(next);
      setCommittedForm(next);
      setEditing(false);
      setSavedHint('保存成功');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [canSave, form, saving]);

  const [testContent, setTestContent] = useState('');
  const [testCount, setTestCount] = useState('');
  const [testResp, setTestResp] = useState<AiPostTitleSuggestResponse | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  const onTest = useCallback(async () => {
    const content = testContent.trim();
    if (testing) return;
    if (content.length < 10) {
      setTestError('内容太短了，至少输入 10 个字符用于试运行。');
      return;
    }
    setTesting(true);
    setTestError(null);
    setTestResp(null);
    try {
      const n = parseOptionalNumber(testCount);
      const resp = await suggestPostTitles({
        content,
        count: n === undefined ? undefined : Math.trunc(n),
      });
      setTestResp(resp);
    } catch (e) {
      setTestError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [testContent, testCount, testing]);

  const [historyPage, setHistoryPage] = useState<Page<PostTitleGenHistoryDTO> | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);

  const loadHistory = useCallback(async (pageNo: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const p = await adminListPostTitleGenHistory({ page: pageNo, size: 20 });
      setHistoryPage(p);
    } catch (e) {
      setHistoryError(e instanceof Error ? e.message : String(e));
      setHistoryPage(null);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHistory(historyPageNo);
  }, [loadHistory, historyPageNo]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">标题生成</h3>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">启用标题生成：</span>
              <select
                value={form.enabled ? 'true' : 'false'}
                onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.value === 'true' }))}
                disabled={!editing || loading || saving}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.enabled ? 'text-green-600 border-green-200 bg-white' : 'text-red-600 border-red-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
                title={!editing ? '只读（点击右侧「编辑」后可修改）' : '修改开关（需保存生效）'}
              >
                <option value="true" className="text-green-600">
                  开启
                </option>
                <option value="false" className="text-red-600">
                  关闭
                </option>
              </select>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">启用深度思考：</span>
              <select
                value={form.enableThinking ? 'true' : 'false'}
                onChange={(e) => setForm((p) => ({ ...p, enableThinking: e.target.value === 'true' }))}
                disabled={!editing || loading || saving}
                className={`rounded border px-3 py-1 text-sm font-semibold focus:outline-none ${
                  form.enableThinking ? 'text-purple-700 border-purple-200 bg-white' : 'text-gray-700 border-gray-200 bg-white'
                } disabled:opacity-60 disabled:bg-gray-100`}
                title={!editing ? '只读（点击右侧「编辑」后可修改）' : '修改开关（需保存生效）'}
              >
                <option value="false" className="text-gray-700">
                  关闭
                </option>
                <option value="true" className="text-purple-700">
                  开启
                </option>
              </select>
            </div>
            <button
              type="button"
              onClick={() => void loadConfig()}
              disabled={loading}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              {loading ? '刷新中...' : '刷新配置'}
            </button>
            <button
              type="button"
              onClick={() => {
                if (editing) {
                  setForm(committedForm);
                  setEditing(false);
                  setError(null);
                  setSavedHint(null);
                } else {
                  setEditing(true);
                }
              }}
              disabled={loading || saving}
              className={`px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm ${
                editing ? 'bg-gray-50' : 'bg-white'
              }`}
            >
              {editing ? '取消编辑' : '编辑'}
            </button>
            {editing && (
              <button
                type="button"
                onClick={() => void onSave()}
                disabled={loading || saving || !canSave || !hasUnsavedChanges}
                className="px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 text-sm"
              >
                {saving ? '保存中...' : '保存配置'}
              </button>
            )}
          </div>
        </div>

        {savedHint && <div className="text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-md px-3 py-2">{savedHint}</div>}
        {error && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{error}</div>}

        {editing && formErrors.length > 0 && (
          <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2 space-y-1">
            {formErrors.map((m) => (
              <div key={m}>{m}</div>
            ))}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <div className="text-xs text-gray-600 mb-1">默认生成数量 defaultCount</div>
            <input
              value={form.defaultCount}
              onChange={(e) => setForm((p) => ({ ...p, defaultCount: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 5"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">最大生成数量 maxCount</div>
            <input
              value={form.maxCount}
              onChange={(e) => setForm((p) => ({ ...p, maxCount: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 10"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">上下文长度 maxContentChars</div>
            <input
              value={form.maxContentChars}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 8000"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="md:col-span-2">
            <ProviderModelSelect
              providers={providers}
              activeProviderId={activeProviderId}
              chatProviders={chatProviders}
              mode="chat"
              providerId={form.providerId}
              model={form.model}
              disabled={!editing || loading || saving}
              selectClassName="w-full rounded border px-3 py-2 text-sm bg-white disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              onChange={(next) => setForm((p) => ({ ...p, providerId: next.providerId, model: next.model }))}
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">温度 temperature（可选）</div>
            <input
              value={form.temperature}
              onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.4"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">TOP-P topP（可选）</div>
            <input
              value={form.topP}
              onChange={(e) => setForm((p) => ({ ...p, topP: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 0.9"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">历史保留天数 historyKeepDays（可选）</div>
            <input
              value={form.historyKeepDays}
              onChange={(e) => setForm((p) => ({ ...p, historyKeepDays: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如 30"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <div className="text-xs text-gray-600 mb-1">System Prompt（会作为 system 角色消息）</div>
            <textarea
              value={form.systemPrompt}
              onChange={(e) => setForm((p) => ({ ...p, systemPrompt: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm min-h-[90px] disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="例如：你是专业的中文社区运营编辑..."
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">Prompt Template（支持占位符）</div>
            <textarea
              value={form.promptTemplate}
              onChange={(e) => setForm((p) => ({ ...p, promptTemplate: e.target.value }))}
              disabled={!editing || loading || saving}
              className="w-full rounded border px-3 py-2 text-sm min-h-[90px] disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
              placeholder="使用 {{count}} / {{boardLine}} / {{tagsLine}} / {{content}}"
            />
            <div className="text-[11px] text-gray-500 mt-1">
              可用占位符：{'{{count}}'} / {'{{boardLine}}'} / {'{{tagsLine}}'} / {'{{content}}'}
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">试运行</h4>
          <button
            type="button"
            onClick={() => void onTest()}
            disabled={testing}
            className="px-3 py-1.5 rounded-md bg-gray-100 hover:bg-gray-200 disabled:opacity-60 text-sm"
          >
            {testing ? '生成中...' : '用当前配置生成标题'}
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="md:col-span-2">
            <textarea
              value={testContent}
              onChange={(e) => setTestContent(e.target.value)}
              className="w-full rounded border px-3 py-2 text-sm min-h-[90px]"
              placeholder="输入一段帖子正文用于试运行"
            />
          </div>
          <div>
            <div className="text-xs text-gray-600 mb-1">count（可选，留空用默认）</div>
            <input
              value={testCount}
              onChange={(e) => setTestCount(e.target.value)}
              className="w-full rounded border px-3 py-2 text-sm"
              placeholder="例如 5"
            />
            <div className="text-xs text-gray-500 mt-2">
              说明：试运行也会走真实生成接口；如开启历史记录，会产生一条历史。
            </div>
          </div>
        </div>

        {testError && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{testError}</div>}

        {testResp && (
          <div className="space-y-2">
            <div className="text-xs text-gray-600">
              model: {testResp.model ?? '-'}，latency: {testResp.latencyMs ?? '-'}ms
            </div>
            <div className="flex flex-wrap gap-2">
              {testResp.titles?.map((t, idx) => (
                <span key={`${idx}-${t}`} className="px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm">
                  {t}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">生成历史</h4>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void loadHistory(historyPageNo)}
              disabled={historyLoading}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              {historyLoading ? '刷新中...' : '刷新'}
            </button>
            <button
              type="button"
              onClick={() => setHistoryPageNo((p) => Math.max(0, p - 1))}
              disabled={historyLoading || historyPageNo <= 0}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              上一页
            </button>
            <button
              type="button"
              onClick={() => setHistoryPageNo((p) => p + 1)}
              disabled={historyLoading || (historyPage?.content?.length ?? 0) === 0}
              className="px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm"
            >
              下一页
            </button>
          </div>
        </div>

        {historyError && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{historyError}</div>}

        {historyPage?.content?.length ? (
          <div className="space-y-2">
            {historyPage.content.map((row) => (
              <div key={row.id} className="border border-gray-200 rounded-md p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="text-xs text-gray-600">
                    #{row.id} · {row.createdAt} · userId={row.userId} · count={row.requestedCount} · latency={row.latencyMs ?? '-'}ms
                  </div>
                  <div className="text-xs text-gray-600">
                    model={row.model ?? '-'} · temp={row.temperature ?? '-'} · promptV={row.promptVersion ?? '-'}
                  </div>
                </div>
                {row.contentExcerpt && <div className="text-xs text-gray-600 mt-2">excerpt: {row.contentExcerpt}</div>}
                <div className="flex flex-wrap gap-2 mt-2">
                  {row.titles?.map((t, idx) => (
                    <span key={`${row.id}-${idx}`} className="px-2 py-1 rounded-full border border-gray-300 bg-white text-xs">
                      {t}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-gray-600">{historyLoading ? '加载中...' : '暂无历史记录'}</div>
        )}
      </div>
    </div>
  );
};

export default TitleGenForm;
