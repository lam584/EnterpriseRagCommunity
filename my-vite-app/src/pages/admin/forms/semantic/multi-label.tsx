import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminGetPostTagGenConfig,
  adminListPostTagGenHistory,
  adminUpsertPostTagGenConfig,
  type Page,
  type PostTagGenConfigDTO,
  type PostTagGenHistoryDTO,
} from '../../../../services/tagGenAdminService';
import {
  adminGetPostLangLabelGenConfig,
  adminUpsertPostLangLabelGenConfig,
  type PostLangLabelGenConfigDTO,
} from '../../../../services/langLabelAdminService';
import {
  adminGetPostRiskTagGenConfig,
  adminUpsertPostRiskTagGenConfig,
  type PostRiskTagGenConfigDTO,
} from '../../../../services/riskTagGenAdminService';
import { suggestPostTags, type AiPostTagSuggestResponse } from '../../../../services/aiTagService';

type FormState = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model: string;
  temperature: string;
  defaultCount: string;
  maxCount: string;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
};

type LangFormState = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model: string;
  temperature: string;
  maxContentChars: string;
};

type RiskFormState = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model: string;
  temperature: string;
  maxCount: string;
  maxContentChars: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): PostTagGenConfigDTO {
  return {
    enabled: true,
    systemPrompt: '你是专业的中文社区运营编辑，擅长为帖子提炼主题标签。',
    promptTemplate: `请根据下面这段帖子内容生成 {{count}} 个中文主题标签。\n要求：\n- 标签应概括内容主题，优先使用常见领域词汇\n- 每个标签不超过 8 个汉字\n- 标签之间不要重复\n- 不要输出编号、不要输出解释文字\n- 只输出严格 JSON\n- JSON 格式：{\"tags\":[\"...\", \"...\"]}\n\n{{boardLine}}{{titleLine}}{{tagsLine}}帖子内容：\n{{content}}\n`,
    temperature: 0.4,
    defaultCount: 5,
    maxCount: 10,
    maxContentChars: 4000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
  };
}

function toFormState(cfg?: PostTagGenConfigDTO | null): FormState {
  return {
    enabled: Boolean(cfg?.enabled),
    systemPrompt: cfg?.systemPrompt ?? '',
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    defaultCount: cfg?.defaultCount === null || cfg?.defaultCount === undefined ? '' : String(cfg.defaultCount),
    maxCount: cfg?.maxCount === null || cfg?.maxCount === undefined ? '' : String(cfg.maxCount),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    historyEnabled: Boolean(cfg?.historyEnabled),
    historyKeepDays: cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined ? '' : String(cfg.historyKeepDays),
    historyKeepRows: cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined ? '' : String(cfg.historyKeepRows),
  };
}

function defaultLangConfig(): PostLangLabelGenConfigDTO {
  return {
    enabled: true,
    systemPrompt:
      '你是一个语言识别助手。\\n任务：根据输入的标题与正文，判断文本包含的自然语言。\\n输出要求：\\n1. 只输出 JSON（不要包裹 ```），格式：{\"languages\":[\"zh\",\"en\"]}\\n2. languages 使用简短语言代码（优先 ISO 639-1：zh/en/ja/ko/fr/de/es/ru/it/pt/...）。中文统一用 zh。\\n3. 如果文本明显由多种语言混合组成，请输出多个语言代码（最多 3 个）。\\n4. 不要输出解释、不要输出多余字段。\\n',
    promptTemplate:
      '请根据以下标题与正文判断文本包含的自然语言，并严格按 systemPrompt 的要求只输出 JSON（不要输出解释文字）。\\n\\n标题：\\n{{title}}\\n\\n正文：\\n{{content}}\\n',
    temperature: 0.0,
    maxContentChars: 8000,
  };
}

function toLangFormState(cfg?: PostLangLabelGenConfigDTO | null): LangFormState {
  return {
    enabled: Boolean(cfg?.enabled),
    systemPrompt: cfg?.systemPrompt ?? '',
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
  };
}

function defaultRiskConfig(): PostRiskTagGenConfigDTO {
  return {
    enabled: true,
    systemPrompt:
      '你是一个社区内容风险识别助手。\\n任务：根据输入的标题与正文，生成该帖子可能涉及的风险标签。\\n输出要求：\\n1. 只输出 JSON（不要包裹 ```），格式：{\"riskTags\":[\"诈骗\",\"隐私泄露\",\"仇恨言论\"]}。\\n2. riskTags 必须使用中文短语（不要英文/拼音），每个标签不超过 8 个汉字。\\n3. 标签应尽量稳定、可复用、能概括风险类型；最多输出 {{maxCount}} 个。\\n4. 如果内容看起来风险很低，可以输出空数组。\\n5. 不要输出解释、不要输出多余字段。\\n',
    promptTemplate:
      '请根据以下标题与正文生成风险标签，并严格按 systemPrompt 的要求只输出 JSON（不要输出解释文字）。\\n\\n标题：\\n{{title}}\\n\\n正文：\\n{{content}}\\n',
    model: '',
    temperature: 0.2,
    maxCount: 10,
    maxContentChars: 8000,
  };
}

function toRiskFormState(cfg?: PostRiskTagGenConfigDTO | null): RiskFormState {
  return {
    enabled: Boolean(cfg?.enabled),
    systemPrompt: cfg?.systemPrompt ?? '',
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    maxCount: cfg?.maxCount === null || cfg?.maxCount === undefined ? '' : String(cfg.maxCount),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
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

function validateLangForm(s: LangFormState): string[] {
  const errors: string[] = [];
  if (!s.systemPrompt.trim()) errors.push('systemPrompt 不能为空');
  if (!s.promptTemplate.trim()) errors.push('promptTemplate 不能为空');
  if (s.promptTemplate.length > 20000) errors.push('promptTemplate 过长（> 20000），请精简');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const mcc = parseOptionalNumber(s.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 100000)) errors.push('maxContentChars 需为 200~100000 的整数');

  return errors;
}

function validateRiskForm(s: RiskFormState): string[] {
  const errors: string[] = [];
  if (!s.systemPrompt.trim()) errors.push('systemPrompt 不能为空');
  if (!s.promptTemplate.trim()) errors.push('promptTemplate 不能为空');
  if (s.promptTemplate.length > 20000) errors.push('promptTemplate 过长（> 20000），请精简');

  const temp = parseOptionalNumber(s.temperature);
  if (temp !== undefined && (temp < 0 || temp > 2)) errors.push('temperature 需在 [0, 2] 范围内');

  const mc = parseOptionalNumber(s.maxCount);
  if (mc !== undefined && (!Number.isInteger(mc) || mc < 1 || mc > 50)) errors.push('maxCount 需为 1~50 的整数');

  const mcc = parseOptionalNumber(s.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 100000)) errors.push('maxContentChars 需为 200~100000 的整数');

  return errors;
}

function buildPayload(s: FormState) {
  const temperature = parseOptionalNumber(s.temperature);
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
    temperature: temperature === undefined ? null : temperature,
    defaultCount: defaultCount === undefined ? 5 : Math.trunc(defaultCount),
    maxCount: maxCount === undefined ? 10 : Math.trunc(maxCount),
    maxContentChars: maxContentChars === undefined ? 4000 : Math.trunc(maxContentChars),
    historyEnabled: s.historyEnabled,
    historyKeepDays: historyKeepDays === undefined ? null : Math.trunc(historyKeepDays),
    historyKeepRows: historyKeepRows === undefined ? null : Math.trunc(historyKeepRows),
  };
}

function buildLangPayload(s: LangFormState) {
  const temperature = parseOptionalNumber(s.temperature);
  const maxContentChars = parseOptionalNumber(s.maxContentChars);

  return {
    enabled: s.enabled,
    systemPrompt: s.systemPrompt,
    promptTemplate: s.promptTemplate,
    model: s.model.trim() ? s.model.trim() : null,
    temperature: temperature === undefined ? null : temperature,
    maxContentChars: maxContentChars === undefined ? 8000 : Math.trunc(maxContentChars),
  };
}

function buildRiskPayload(s: RiskFormState) {
  const temperature = parseOptionalNumber(s.temperature);
  const maxContentChars = parseOptionalNumber(s.maxContentChars);
  const maxCount = parseOptionalNumber(s.maxCount);

  return {
    enabled: s.enabled,
    systemPrompt: s.systemPrompt,
    promptTemplate: s.promptTemplate,
    model: s.model.trim() ? s.model.trim() : null,
    temperature: temperature === undefined ? null : temperature,
    maxCount: maxCount === undefined ? 10 : Math.trunc(maxCount),
    maxContentChars: maxContentChars === undefined ? 8000 : Math.trunc(maxContentChars),
  };
}

const MultiLabelForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  const [form, setForm] = useState<FormState>(() => toFormState(null));
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(null));

  const [langLoading, setLangLoading] = useState(false);
  const [langSaving, setLangSaving] = useState(false);
  const [langError, setLangError] = useState<string | null>(null);
  const [langSavedHint, setLangSavedHint] = useState<string | null>(null);
  const [langEditing, setLangEditing] = useState(false);
  const [langForm, setLangForm] = useState<LangFormState>(() => toLangFormState(null));
  const [langCommittedForm, setLangCommittedForm] = useState<LangFormState>(() => toLangFormState(null));

  const [riskLoading, setRiskLoading] = useState(false);
  const [riskSaving, setRiskSaving] = useState(false);
  const [riskError, setRiskError] = useState<string | null>(null);
  const [riskSavedHint, setRiskSavedHint] = useState<string | null>(null);
  const [riskEditing, setRiskEditing] = useState(false);
  const [riskForm, setRiskForm] = useState<RiskFormState>(() => toRiskFormState(null));
  const [riskCommittedForm, setRiskCommittedForm] = useState<RiskFormState>(() => toRiskFormState(null));

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0 && !saving && !loading;

  const hasUnsavedChanges = useMemo(() => {
    return (
      form.enabled !== committedForm.enabled ||
      form.systemPrompt !== committedForm.systemPrompt ||
      form.promptTemplate !== committedForm.promptTemplate ||
      form.model !== committedForm.model ||
      form.temperature !== committedForm.temperature ||
      form.defaultCount !== committedForm.defaultCount ||
      form.maxCount !== committedForm.maxCount ||
      form.maxContentChars !== committedForm.maxContentChars ||
      form.historyEnabled !== committedForm.historyEnabled ||
      form.historyKeepDays !== committedForm.historyKeepDays ||
      form.historyKeepRows !== committedForm.historyKeepRows
    );
  }, [form, committedForm]);

  const langFormErrors = useMemo(() => validateLangForm(langForm), [langForm]);
  const langFormWarnings = useMemo(() => {
    const warnings: string[] = [];
    const t = langForm.promptTemplate.trim();
    if (t && t.length < 50) warnings.push('promptTemplate 建议不少于 50 个字符（避免过短导致输出不稳定）');
    return warnings;
  }, [langForm.promptTemplate]);
  const langCanSave = langFormErrors.length === 0 && !langSaving && !langLoading;
  const langHasUnsavedChanges = useMemo(() => {
    return (
      langForm.enabled !== langCommittedForm.enabled ||
      langForm.systemPrompt !== langCommittedForm.systemPrompt ||
      langForm.promptTemplate !== langCommittedForm.promptTemplate ||
      langForm.model !== langCommittedForm.model ||
      langForm.temperature !== langCommittedForm.temperature ||
      langForm.maxContentChars !== langCommittedForm.maxContentChars
    );
  }, [langForm, langCommittedForm]);

  const riskFormErrors = useMemo(() => validateRiskForm(riskForm), [riskForm]);
  const riskFormWarnings = useMemo(() => {
    const warnings: string[] = [];
    const t = riskForm.promptTemplate.trim();
    if (t && t.length < 50) warnings.push('promptTemplate 建议不少于 50 个字符（避免过短导致输出不稳定）');
    return warnings;
  }, [riskForm.promptTemplate]);
  const riskCanSave = riskFormErrors.length === 0 && !riskSaving && !riskLoading;
  const riskHasUnsavedChanges = useMemo(() => {
    return (
      riskForm.enabled !== riskCommittedForm.enabled ||
      riskForm.systemPrompt !== riskCommittedForm.systemPrompt ||
      riskForm.promptTemplate !== riskCommittedForm.promptTemplate ||
      riskForm.model !== riskCommittedForm.model ||
      riskForm.temperature !== riskCommittedForm.temperature ||
      riskForm.maxCount !== riskCommittedForm.maxCount ||
      riskForm.maxContentChars !== riskCommittedForm.maxContentChars
    );
  }, [riskForm, riskCommittedForm]);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    try {
      const cfg = await adminGetPostTagGenConfig();
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

  const loadLangConfig = useCallback(async () => {
    setLangLoading(true);
    setLangError(null);
    setLangSavedHint(null);
    try {
      const cfg = await adminGetPostLangLabelGenConfig();
      const prompt = cfg?.promptTemplate?.trim();
      if (!prompt) {
        const next = toLangFormState({ ...defaultLangConfig(), ...cfg });
        setLangForm(next);
        setLangCommittedForm(next);
        setLangEditing(false);
        setLangSavedHint('后端配置为空，已加载内置默认值（可编辑后保存写入数据库）');
      } else {
        const next = toLangFormState(cfg);
        setLangForm(next);
        setLangCommittedForm(next);
        setLangEditing(false);
      }
    } catch (e) {
      const next = toLangFormState(defaultLangConfig());
      setLangForm(next);
      setLangCommittedForm(next);
      setLangEditing(false);
      setLangError(e instanceof Error ? e.message : String(e));
      setLangSavedHint('后端接口不可用，已加载前端默认配置（可用于演示）');
    } finally {
      setLangLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLangConfig();
  }, [loadLangConfig]);

  const loadRiskConfig = useCallback(async () => {
    setRiskLoading(true);
    setRiskError(null);
    setRiskSavedHint(null);
    try {
      const cfg = await adminGetPostRiskTagGenConfig();
      const prompt = cfg?.promptTemplate?.trim();
      if (!prompt) {
        const next = toRiskFormState({ ...defaultRiskConfig(), ...cfg });
        setRiskForm(next);
        setRiskCommittedForm(next);
        setRiskEditing(false);
        setRiskSavedHint('后端配置为空，已加载内置默认值（可编辑后保存写入数据库）');
      } else {
        const next = toRiskFormState(cfg);
        setRiskForm(next);
        setRiskCommittedForm(next);
        setRiskEditing(false);
      }
    } catch (e) {
      const next = toRiskFormState(defaultRiskConfig());
      setRiskForm(next);
      setRiskCommittedForm(next);
      setRiskEditing(false);
      setRiskError(e instanceof Error ? e.message : String(e));
      setRiskSavedHint('后端接口不可用，已加载前端默认配置（可用于演示）');
    } finally {
      setRiskLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRiskConfig();
  }, [loadRiskConfig]);

  const onSave = useCallback(async () => {
    if (!canSave) return;
    if (saving) return;
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertPostTagGenConfig(payload);
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

  const onSaveLang = useCallback(async () => {
    if (!langCanSave) return;
    if (langSaving) return;
    setLangSaving(true);
    setLangError(null);
    setLangSavedHint(null);
    try {
      const payload = buildLangPayload(langForm);
      const saved = await adminUpsertPostLangLabelGenConfig(payload);
      const next = toLangFormState(saved);
      setLangForm(next);
      setLangCommittedForm(next);
      setLangEditing(false);
      setLangSavedHint('保存成功');
    } catch (e) {
      setLangError(e instanceof Error ? e.message : String(e));
    } finally {
      setLangSaving(false);
    }
  }, [langCanSave, langForm, langSaving]);

  const onSaveRisk = useCallback(async () => {
    if (!riskCanSave) return;
    if (riskSaving) return;
    setRiskSaving(true);
    setRiskError(null);
    setRiskSavedHint(null);
    try {
      const payload = buildRiskPayload(riskForm);
      const saved = await adminUpsertPostRiskTagGenConfig(payload);
      const next = toRiskFormState(saved);
      setRiskForm(next);
      setRiskCommittedForm(next);
      setRiskEditing(false);
      setRiskSavedHint('保存成功');
    } catch (e) {
      setRiskError(e instanceof Error ? e.message : String(e));
    } finally {
      setRiskSaving(false);
    }
  }, [riskCanSave, riskForm, riskSaving]);

  const [testTitle, setTestTitle] = useState('');
  const [testContent, setTestContent] = useState('');
  const [testCount, setTestCount] = useState('');
  const [testResp, setTestResp] = useState<AiPostTagSuggestResponse | null>(null);
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
      const resp = await suggestPostTags({
        title: testTitle.trim() || undefined,
        content,
        count: n === undefined ? undefined : Math.trunc(n),
      });
      setTestResp(resp);
    } catch (e) {
      setTestError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(false);
    }
  }, [testContent, testCount, testTitle, testing]);

  const [historyPage, setHistoryPage] = useState<Page<PostTagGenHistoryDTO> | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);

  const loadHistory = useCallback(async (pageNo: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const p = await adminListPostTagGenHistory({ page: pageNo, size: 20 });
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
          <h3 className="text-lg font-semibold">主题标签生成</h3>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700">主题标签生成：</span>
              <select
                value={form.enabled ? 'true' : 'false'}
                disabled={!editing || loading || saving}
                onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.value === 'true' }))}
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
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={loading || saving}
              onClick={() => void loadConfig()}
            >
              刷新
            </button>
            {!editing ? (
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setEditing(true)}
                disabled={loading || saving}
              >
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={() => {
                    setForm(committedForm);
                    setEditing(false);
                    setError(null);
                    setSavedHint(null);
                  }}
                  disabled={saving || loading}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
                  onClick={() => void onSave()}
                  disabled={!canSave || !hasUnsavedChanges}
                >
                  {saving ? '保存中...' : '保存'}
                </button>
              </>
            )}
          </div>
        </div>

        {savedHint ? <div className="text-sm text-green-700">{savedHint}</div> : null}
        {error ? <div className="text-sm text-red-700">{error}</div> : null}
        {formErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {formErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-4 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">模型（可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.model}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, model: e.target.value }))}
              placeholder="留空则使用 app.ai.model"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">温度（0~2，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.temperature}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, temperature: e.target.value }))}
              placeholder="例如 0.4"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">默认生成数量</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.defaultCount}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, defaultCount: e.target.value }))}
              placeholder="默认 5"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">最大生成数量</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.maxCount}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, maxCount: e.target.value }))}
              placeholder="默认 10"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.maxContentChars}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 4000"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 xl:col-span-2">
            <div>
              <div className="text-sm font-medium text-gray-700 mb-1">历史保留天数</div>
              <input
                className="w-full rounded border px-3 py-2 border-gray-300"
                value={form.historyKeepDays}
                disabled={!editing}
                onChange={(e) => setForm((p) => ({ ...p, historyKeepDays: e.target.value }))}
                placeholder="例如 30"
              />
            </div>
            <div>
              <div className="text-sm font-medium text-gray-700 mb-1">历史保留条数</div>
              <input
                className="w-full rounded border px-3 py-2 border-gray-300"
                value={form.historyKeepRows}
                disabled={!editing}
                onChange={(e) => setForm((p) => ({ ...p, historyKeepRows: e.target.value }))}
                placeholder="例如 5000"
              />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">System Prompt</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[80px]"
              value={form.systemPrompt}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, systemPrompt: e.target.value }))}
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">Prompt Template</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[180px] font-mono text-xs"
              value={form.promptTemplate}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, promptTemplate: e.target.value }))}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">语言标签生成</h3>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={langLoading || langSaving}
              onClick={() => void loadLangConfig()}
            >
              刷新
            </button>
            {!langEditing ? (
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setLangEditing(true)}
                disabled={langLoading || langSaving}
              >
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={() => {
                    setLangForm(langCommittedForm);
                    setLangEditing(false);
                    setLangError(null);
                    setLangSavedHint(null);
                  }}
                  disabled={langSaving || langLoading}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
                  onClick={() => void onSaveLang()}
                  disabled={!langCanSave || !langHasUnsavedChanges}
                >
                  {langSaving ? '保存中...' : '保存'}
                </button>
              </>
            )}
          </div>
        </div>

        {langSavedHint ? <div className="text-sm text-green-700">{langSavedHint}</div> : null}
        {langError ? <div className="text-sm text-red-700">{langError}</div> : null}
        {langFormWarnings.length ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800 space-y-1">
            {langFormWarnings.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}
        {langFormErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {langFormErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={langForm.enabled}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, enabled: e.target.checked }))}
            />
            启用语言标签生成
          </label>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-4 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">模型（可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.model}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, model: e.target.value }))}
              placeholder="留空则使用 app.ai.model"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">温度（0~2，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.temperature}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, temperature: e.target.value }))}
              placeholder="例如 0"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={langForm.maxContentChars}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 8000"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">System Prompt</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[80px]"
              value={langForm.systemPrompt}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, systemPrompt: e.target.value }))}
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">Prompt Template</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[180px] font-mono text-xs"
              value={langForm.promptTemplate}
              disabled={!langEditing}
              onChange={(e) => setLangForm((p) => ({ ...p, promptTemplate: e.target.value }))}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">风险标签生成配置</h3>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={riskLoading || riskSaving}
              onClick={() => void loadRiskConfig()}
            >
              刷新
            </button>
            {!riskEditing ? (
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setRiskEditing(true)}
                disabled={riskLoading || riskSaving}
              >
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={() => {
                    setRiskForm(riskCommittedForm);
                    setRiskEditing(false);
                    setRiskError(null);
                    setRiskSavedHint(null);
                  }}
                  disabled={riskSaving || riskLoading}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
                  onClick={() => void onSaveRisk()}
                  disabled={!riskCanSave || !riskHasUnsavedChanges}
                >
                  {riskSaving ? '保存中...' : '保存'}
                </button>
              </>
            )}
          </div>
        </div>

        {riskSavedHint ? <div className="text-sm text-green-700">{riskSavedHint}</div> : null}
        {riskError ? <div className="text-sm text-red-700">{riskError}</div> : null}
        {riskFormWarnings.length ? (
          <div className="rounded border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800 space-y-1">
            {riskFormWarnings.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}
        {riskFormErrors.length ? (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700 space-y-1">
            {riskFormErrors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={riskForm.enabled}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, enabled: e.target.checked }))}
            />
            启用风险标签生成
          </label>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-4 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">模型（可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={riskForm.model}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, model: e.target.value }))}
              placeholder="留空则使用 app.ai.model"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">温度（0~2，可选）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={riskForm.temperature}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, temperature: e.target.value }))}
              placeholder="例如 0.2"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">最大生成数量</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={riskForm.maxCount}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, maxCount: e.target.value }))}
              placeholder="默认 10"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={riskForm.maxContentChars}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 8000"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">System Prompt</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[80px]"
              value={riskForm.systemPrompt}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, systemPrompt: e.target.value }))}
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">Prompt Template</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[180px] font-mono text-xs"
              value={riskForm.promptTemplate}
              disabled={!riskEditing}
              onChange={(e) => setRiskForm((p) => ({ ...p, promptTemplate: e.target.value }))}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <h4 className="text-base font-semibold">试运行（调用 /api/ai/posts/tag-suggestions）</h4>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <input
            className="rounded border px-3 py-2 border-gray-300 md:col-span-2"
            placeholder="标题（可选）"
            value={testTitle}
            onChange={(e) => setTestTitle(e.target.value)}
          />
          <input
            className="rounded border px-3 py-2 border-gray-300"
            placeholder="生成数量（可选）"
            value={testCount}
            onChange={(e) => setTestCount(e.target.value)}
          />
        </div>
        <textarea
          className="w-full rounded border px-3 py-2 border-gray-300 min-h-[120px]"
          placeholder="输入正文内容（至少 10 个字符）"
          value={testContent}
          onChange={(e) => setTestContent(e.target.value)}
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={testing}
            onClick={() => void onTest()}
            className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
          >
            {testing ? '生成中...' : '生成标签'}
          </button>
          {testResp?.model ? <div className="text-xs text-gray-500">model: {testResp.model}</div> : null}
          {typeof testResp?.latencyMs === 'number' ? <div className="text-xs text-gray-500">latency: {testResp.latencyMs}ms</div> : null}
        </div>
        {testError ? <div className="text-sm text-red-700">{testError}</div> : null}
        {testResp?.tags?.length ? (
          <div className="flex flex-wrap gap-2">
            {testResp.tags.map((t) => (
              <span key={t} className="px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm">
                {t}
              </span>
            ))}
          </div>
        ) : null}
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">生成历史</h4>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading}
              onClick={() => void loadHistory(historyPageNo)}
            >
              刷新
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading || historyPageNo <= 0}
              onClick={() => setHistoryPageNo((p) => Math.max(0, p - 1))}
            >
              上一页
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
              disabled={historyLoading || (historyPage?.content?.length ?? 0) === 0}
              onClick={() => setHistoryPageNo((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </div>

        {historyError ? <div className="text-sm text-red-700">{historyError}</div> : null}
        {historyLoading ? <div className="text-sm text-gray-600">加载中...</div> : null}

        {!historyLoading && !historyError && (historyPage?.content?.length ?? 0) === 0 ? (
          <div className="text-sm text-gray-500">暂无历史记录。</div>
        ) : null}

        {(historyPage?.content?.length ?? 0) > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2 pr-4">时间</th>
                  <th className="py-2 pr-4">用户</th>
                  <th className="py-2 pr-4">标题</th>
                  <th className="py-2 pr-4">标签</th>
                  <th className="py-2 pr-4">模型</th>
                  <th className="py-2 pr-4">耗时</th>
                </tr>
              </thead>
              <tbody>
                {historyPage?.content?.map((h) => (
                  <tr key={h.id} className="border-b">
                    <td className="py-2 pr-4">{new Date(h.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4">#{h.userId}</td>
                    <td className="py-2 pr-4 max-w-[280px]">
                      <span className="truncate block" title={h.titleExcerpt ?? ''}>
                        {h.titleExcerpt ?? '-'}
                      </span>
                    </td>
                    <td className="py-2 pr-4 max-w-[520px]">
                      <span className="truncate block" title={(h.tags ?? []).join('、')}>
                        {(h.tags ?? []).join('、')}
                      </span>
                    </td>
                    <td className="py-2 pr-4">{h.model ?? '-'}</td>
                    <td className="py-2 pr-4">{typeof h.latencyMs === 'number' ? `${h.latencyMs}ms` : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default MultiLabelForm;
