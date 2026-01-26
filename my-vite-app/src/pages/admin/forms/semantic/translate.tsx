import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminGetTranslateConfig,
  adminListTranslateHistory,
  adminUpsertTranslateConfig,
  type Page,
  type SemanticTranslateConfigDTO,
  type SemanticTranslateHistoryDTO,
} from '../../../../services/translateAdminService';
import { LLM_SUPPORTED_LANGUAGES } from '../../../../constants/llmSupportedLanguages';

type FormState = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model: string;
  temperature: string;
  maxContentChars: string;
  historyEnabled: boolean;
  historyKeepDays: string;
  historyKeepRows: string;
  allowedTargetLanguagesText: string;
};

function parseOptionalNumber(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

function defaultConfig(): SemanticTranslateConfigDTO {
  return {
    enabled: true,
    systemPrompt:
      '你是一个专业的翻译助手。\n要求：\n1. 把用户提供的标题与正文翻译成目标语言；\n2. 正文输出必须为 Markdown，尽量保留原文的结构（标题层级/列表/引用/代码块/表格等）；\n3. 不要添加与原文无关的内容，不要进行总结，不要输出额外解释；\n4. 输出严格为 JSON（不要包裹 ```），字段如下：\n   - title: 翻译后的标题（纯文本）\n   - markdown: 翻译后的正文 Markdown\n',
    promptTemplate: '目标语言：{{targetLang}}\n\n标题：\n{{title}}\n\n正文（Markdown）：\n{{content}}\n',
    temperature: 0.2,
    maxContentChars: 8000,
    historyEnabled: true,
    historyKeepDays: 30,
    historyKeepRows: 5000,
    allowedTargetLanguages: LLM_SUPPORTED_LANGUAGES,
  };
}

function toFormState(cfg?: SemanticTranslateConfigDTO | null): FormState {
  const langs = (cfg?.allowedTargetLanguages?.length ? cfg.allowedTargetLanguages : LLM_SUPPORTED_LANGUAGES).filter(Boolean);
  return {
    enabled: Boolean(cfg?.enabled),
    systemPrompt: cfg?.systemPrompt ?? '',
    promptTemplate: cfg?.promptTemplate ?? '',
    model: cfg?.model ?? '',
    temperature: cfg?.temperature === null || cfg?.temperature === undefined ? '' : String(cfg.temperature),
    maxContentChars: cfg?.maxContentChars === null || cfg?.maxContentChars === undefined ? '' : String(cfg.maxContentChars),
    historyEnabled: Boolean(cfg?.historyEnabled),
    historyKeepDays: cfg?.historyKeepDays === null || cfg?.historyKeepDays === undefined ? '' : String(cfg.historyKeepDays),
    historyKeepRows: cfg?.historyKeepRows === null || cfg?.historyKeepRows === undefined ? '' : String(cfg.historyKeepRows),
    allowedTargetLanguagesText: langs.join('\n'),
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

  const mcc = parseOptionalNumber(s.maxContentChars);
  if (mcc !== undefined && (!Number.isInteger(mcc) || mcc < 200 || mcc > 100000)) errors.push('maxContentChars 需为 200~100000 的整数');

  const hkd = parseOptionalNumber(s.historyKeepDays);
  if (hkd !== undefined && (!Number.isInteger(hkd) || hkd < 1)) errors.push('historyKeepDays 必须为正整数');
  const hkr = parseOptionalNumber(s.historyKeepRows);
  if (hkr !== undefined && (!Number.isInteger(hkr) || hkr < 1)) errors.push('historyKeepRows 必须为正整数');
  return errors;
}

function buildPayload(s: FormState) {
  const temperature = parseOptionalNumber(s.temperature);
  const maxContentChars = parseOptionalNumber(s.maxContentChars);
  const historyKeepDays = parseOptionalNumber(s.historyKeepDays);
  const historyKeepRows = parseOptionalNumber(s.historyKeepRows);

  const allowedTargetLanguages = Array.from(
    new Set(
      s.allowedTargetLanguagesText
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n')
        .split('\n')
        .map((x) => x.trim())
        .filter((x) => x.length > 0)
    )
  );

  return {
    enabled: s.enabled,
    systemPrompt: s.systemPrompt,
    promptTemplate: s.promptTemplate,
    model: s.model.trim() ? s.model.trim() : null,
    temperature: temperature === undefined ? null : temperature,
    maxContentChars: maxContentChars === undefined ? 8000 : Math.trunc(maxContentChars),
    historyEnabled: s.historyEnabled,
    historyKeepDays: historyKeepDays === undefined ? null : Math.trunc(historyKeepDays),
    historyKeepRows: historyKeepRows === undefined ? null : Math.trunc(historyKeepRows),
    allowedTargetLanguages,
  };
}

const TranslateForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [committedForm, setCommittedForm] = useState<FormState>(() => toFormState(defaultConfig()));
  const [form, setForm] = useState<FormState>(() => toFormState(defaultConfig()));

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPageNo, setHistoryPageNo] = useState(0);
  const [historyPage, setHistoryPage] = useState<Page<SemanticTranslateHistoryDTO> | null>(null);

  const formErrors = useMemo(() => validateForm(form), [form]);
  const canSave = formErrors.length === 0;
  const hasUnsavedChanges = useMemo(() => JSON.stringify(form) !== JSON.stringify(committedForm), [form, committedForm]);

  const loadHistory = useCallback(async (pageNo: number) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const p = await adminListTranslateHistory({ page: pageNo, size: 20 });
      setHistoryPage(p);
      setHistoryPageNo(pageNo);
    } catch (e: unknown) {
      setHistoryError(e instanceof Error ? e.message : String(e));
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSavedHint(null);
    try {
      const cfg = await adminGetTranslateConfig();
      const merged = { ...defaultConfig(), ...cfg };
      const next = toFormState(merged);
      setCommittedForm(next);
      setForm(next);
      setEditing(false);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
    void loadHistory(0);
  }, [loadConfig, loadHistory]);

  const onSave = useCallback(async () => {
    if (saving || !canSave || !hasUnsavedChanges) return;
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpsertTranslateConfig(payload);
      const merged = { ...defaultConfig(), ...saved };
      const next = toFormState(merged);
      setCommittedForm(next);
      setForm(next);
      setEditing(false);
      setSavedHint('保存成功');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [saving, canSave, hasUnsavedChanges, form]);

  const totalPages = useMemo(() => {
    const total = historyPage?.totalElements ?? 0;
    const size = historyPage?.size ?? 20;
    return size > 0 ? Math.ceil(total / size) : 0;
  }, [historyPage]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">翻译</h3>
          <div className="flex items-center gap-2">
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

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={form.enabled}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.checked }))}
            />
            启用翻译
          </label>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={form.historyEnabled}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, historyEnabled: e.target.checked }))}
            />
            记录翻译历史（用于缓存与审计）
          </label>
        </div>

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
              placeholder="例如 0.2"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">上下文长度（字符）</div>
            <input
              className="w-full rounded border px-3 py-2 border-gray-300"
              value={form.maxContentChars}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, maxContentChars: e.target.value }))}
              placeholder="默认 8000"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 xl:col-span-1">
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

        <div>
          <div className="text-sm font-medium text-gray-700 mb-1">前台可选翻译目标语言（每行一个）</div>
          <textarea
            className="w-full rounded border px-3 py-2 border-gray-300 min-h-[160px]"
            value={form.allowedTargetLanguagesText}
            disabled={!editing}
            onChange={(e) => setForm((p) => ({ ...p, allowedTargetLanguagesText: e.target.value }))}
            placeholder="例如：\n英语（English）\n简体中文（Simplified Chinese）"
          />
          <div className="mt-1 text-xs text-gray-500">留空时，前台会回退到默认支持列表。</div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">System Prompt</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[140px]"
              value={form.systemPrompt}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, systemPrompt: e.target.value }))}
            />
          </div>
          <div>
            <div className="text-sm font-medium text-gray-700 mb-1">Prompt Template</div>
            <textarea
              className="w-full rounded border px-3 py-2 border-gray-300 min-h-[220px] font-mono text-xs"
              value={form.promptTemplate}
              disabled={!editing}
              onChange={(e) => setForm((p) => ({ ...p, promptTemplate: e.target.value }))}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">翻译历史</h3>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-60"
            disabled={historyLoading}
            onClick={() => void loadHistory(historyPageNo)}
          >
            刷新
          </button>
        </div>

        {historyError ? <div className="text-sm text-red-700">{historyError}</div> : null}
        {historyLoading ? <div className="text-sm text-gray-600">加载中...</div> : null}

        {!historyLoading && (historyPage?.content?.length ?? 0) === 0 ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600">
            暂无翻译历史
          </div>
        ) : null}

        {(historyPage?.content?.length ?? 0) > 0 ? (
          <div className="overflow-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b">
                  <th className="py-2 pr-4">时间</th>
                  <th className="py-2 pr-4">类型/ID</th>
                  <th className="py-2 pr-4">目标语言</th>
                  <th className="py-2 pr-4">模型</th>
                  <th className="py-2 pr-4">耗时</th>
                  <th className="py-2 pr-4">摘要</th>
                </tr>
              </thead>
              <tbody>
                {(historyPage?.content ?? []).map((h) => (
                  <tr key={h.id} className="border-b last:border-b-0 align-top">
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-600">{new Date(h.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4 whitespace-nowrap">
                      <span className="text-gray-900">{h.sourceType}</span>
                      <span className="text-gray-500"> #{h.sourceId}</span>
                    </td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.targetLang}</td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.model || '（默认）'}</td>
                    <td className="py-2 pr-4 whitespace-nowrap text-gray-700">{h.latencyMs ? `${h.latencyMs}ms` : '-'}</td>
                    <td className="py-2 pr-4 text-gray-700">
                      <div className="max-w-[520px]">
                        {h.sourceTitleExcerpt ? <div className="text-xs text-gray-500">标题：{h.sourceTitleExcerpt}</div> : null}
                        {h.sourceContentExcerpt ? <div className="text-xs text-gray-500">原文：{h.sourceContentExcerpt}</div> : null}
                        {h.translatedTitle ? <div className="text-xs text-gray-700">译题：{h.translatedTitle}</div> : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        <div className="flex items-center justify-between pt-2">
          <button
            type="button"
            className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
            onClick={() => void loadHistory(Math.max(0, historyPageNo - 1))}
            disabled={historyLoading || historyPageNo <= 0}
          >
            上一页
          </button>
          <div className="text-xs text-gray-500">
            第 {historyPageNo + 1} 页 / 共 {totalPages || 0} 页（共 {historyPage?.totalElements ?? 0} 条）
          </div>
          <button
            type="button"
            className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
            onClick={() => void loadHistory(historyPageNo + 1)}
            disabled={historyLoading || (totalPages > 0 && historyPageNo + 1 >= totalPages)}
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
};

export default TranslateForm;
