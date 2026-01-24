import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminCreateModerationRule,
  adminDeleteModerationRule,
  adminListModerationRules,
  adminUpdateModerationRule,
} from '../../../../services/moderationRulesService';
import type {
  ModerationRuleCategory,
  ModerationRuleCreatePayload,
  ModerationRuleDTO,
  ModerationRuleListQuery,
  ModerationRuleSeverity,
  ModerationRuleType,
} from '../../../../types/moderationRules';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';
import { useSearchParams } from 'react-router-dom';

function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return String(s);
  return d.toLocaleString();
}

function categoryLabel(c: ModerationRuleCategory): string {
  switch (c) {
    case 'SENSITIVE':
      return '敏感词';
    case 'BLACKLIST':
      return '黑名单';
    case 'URL':
      return 'URL/链接';
    case 'AD':
      return '广告模式';
    default:
      return String(c);
  }
}

function categoryToType(c: ModerationRuleCategory): ModerationRuleType {
  switch (c) {
    case 'URL':
      return 'URL';
    case 'AD':
      return 'PATTERN';
    case 'BLACKLIST':
    case 'SENSITIVE':
    default:
      return 'KEYWORD';
  }
}

function inferCategory(rule: ModerationRuleDTO): ModerationRuleCategory {
  const md = (rule.metadata ?? {}) as Record<string, unknown>;
  const cat = md.category;
  if (cat === 'SENSITIVE' || cat === 'BLACKLIST' || cat === 'URL' || cat === 'AD') return cat;
  if (rule.type === 'URL') return 'URL';
  const tags = md.tags;
  if (Array.isArray(tags) && tags.includes('ad')) return 'AD';
  return 'SENSITIVE';
}

type EditorState = {
  open: boolean;
  mode: 'create' | 'edit';
  editingId?: number;
};

type EditorForm = {
  category: ModerationRuleCategory;
  name: string;
  pattern: string;
  severity: ModerationRuleSeverity;
  enabled: boolean;
};

const defaultEditorForm: EditorForm = {
  category: 'SENSITIVE',
  name: '',
  pattern: '',
  severity: 'MEDIUM',
  enabled: true,
};

const RulesForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);

  const [query, setQuery] = useState<ModerationRuleListQuery>({
    q: '',
    category: '',
    type: '',
    severity: '',
    enabled: '',
  });

  const [items, setItems] = useState<ModerationRuleDTO[]>([]);

  const [editor, setEditor] = useState<EditorState>({ open: false, mode: 'create' });
  const [form, setForm] = useState<EditorForm>(defaultEditorForm);
  const [formError, setFormError] = useState<string | null>(null);

  const qidForHistory = useMemo(() => {
    const raw = searchParams.get('queueId') ?? '';
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [searchParams]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminListModerationRules(query);
      setItems(res ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    load();
  }, [load]);

  // 当 category 选择变化时，UI 上隐藏 type 字段，但自动推导 rule.type
  const effectiveType = useMemo(() => {
    if (query.category) {
      return categoryToType(query.category);
    }
    return query.type || '';
  }, [query.category, query.type]);

  const openCreate = useCallback(() => {
    setHint(null);
    setFormError(null);
    setEditor({ open: true, mode: 'create' });
    setForm(defaultEditorForm);
  }, []);

  const openEdit = useCallback((rule: ModerationRuleDTO) => {
    setHint(null);
    setFormError(null);
    setEditor({ open: true, mode: 'edit', editingId: rule.id });
    setForm({
      category: inferCategory(rule),
      name: rule.name ?? '',
      pattern: rule.pattern ?? '',
      severity: rule.severity ?? 'MEDIUM',
      enabled: !!rule.enabled,
    });
  }, []);

  const closeEditor = useCallback(() => {
    setEditor({ open: false, mode: 'create' });
    setFormError(null);
  }, []);

  const validateEditor = useCallback((): string | null => {
    if (!form.name.trim()) return '请填写规则名称';
    if (form.name.trim().length > 96) return '规则名称过长（建议 <= 96）';
    if (!form.pattern.trim()) return '请填写匹配内容（pattern）';

    if (form.category === 'URL') {
      const p = form.pattern.trim();
      // 轻校验：domain/prefix/regex 都允许，但至少看起来像 URL 或 domain
      if (!p.includes('.') && !p.startsWith('http') && !p.startsWith('^')) {
        return 'URL 规则建议填写域名/URL 前缀/正则（例如 example.com 或 https://example.com/ 或 ^https?://）';
      }
    }
    return null;
  }, [form]);

  const save = useCallback(async () => {
    setSaving(true);
    setFormError(null);
    setError(null);
    setHint(null);

    const ve = validateEditor();
    if (ve) {
      setFormError(ve);
      setSaving(false);
      return;
    }

    const payload: ModerationRuleCreatePayload = {
      name: form.name.trim(),
      type: categoryToType(form.category),
      pattern: form.pattern.trim(),
      severity: form.severity,
      enabled: form.enabled,
      metadata: {
        category: form.category,
        tags: form.category === 'AD' ? ['ad'] : undefined,
      },
    };

    try {
      if (editor.mode === 'create') {
        await adminCreateModerationRule(payload);
        setHint('已创建规则');
      } else {
        if (!editor.editingId) throw new Error('缺少编辑目标');
        await adminUpdateModerationRule(editor.editingId, payload);
        setHint('已保存修改');
      }
      closeEditor();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [closeEditor, editor.editingId, editor.mode, form, load, validateEditor]);

  const toggleEnabled = useCallback(
    async (rule: ModerationRuleDTO) => {
      setError(null);
      setHint(null);
      try {
        await adminUpdateModerationRule(rule.id, { enabled: !rule.enabled });
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    },
    [load]
  );

  const remove = useCallback(
    async (rule: ModerationRuleDTO) => {
      const ok = window.confirm(`确认删除规则「${rule.name}」？`);
      if (!ok) return;
      setError(null);
      setHint(null);
      try {
        await adminDeleteModerationRule(rule.id);
        setHint('已删除');
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    },
    [load]
  );

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">规则过滤层</h3>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={openCreate}
              className="rounded border px-4 py-2 disabled:opacity-60"
              disabled={loading}
            >
              新建规则
            </button>
            <button
              type="button"
              onClick={load}
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
              disabled={loading}
            >
              {loading ? '刷新中…' : '刷新'}
            </button>
          </div>
        </div>

        {error ? (
          <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>
        ) : null}
        {hint ? (
          <div className="rounded border border-green-200 bg-green-50 text-green-700 px-3 py-2 text-sm">{hint}</div>
        ) : null}

        {/* 筛选器 */}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
          <input
            className="rounded border px-3 py-2"
            placeholder="搜索：名称 / pattern / 类型"
            value={query.q ?? ''}
            onChange={(e) => setQuery((p) => ({ ...p, q: e.target.value }))}
          />

          <select
            className="rounded border px-3 py-2"
            value={query.category ?? ''}
            onChange={(e) => {
              const v = e.target.value as ModerationRuleCategory | '';
              setQuery((p) => ({ ...p, category: v, type: '' }));
            }}
          >
            <option value="">全部类别</option>
            <option value="SENSITIVE">敏感词</option>
            <option value="BLACKLIST">黑名单</option>
            <option value="URL">URL/链接</option>
            <option value="AD">广告模式</option>
          </select>

          <select
            className="rounded border px-3 py-2"
            value={effectiveType}
            onChange={(e) => setQuery((p) => ({ ...p, type: (e.target.value as ModerationRuleType) || '' }))}
            disabled={!!query.category}
            title={query.category ? '已由类别自动推导 type' : '按后端 RuleType 筛选（可选）'}
          >
            <option value="">全部 type</option>
            <option value="KEYWORD">KEYWORD</option>
            <option value="REGEX">REGEX</option>
            <option value="URL">URL</option>
            <option value="PATTERN">PATTERN</option>
          </select>

          <select
            className="rounded border px-3 py-2"
            value={query.severity ?? ''}
            onChange={(e) => setQuery((p) => ({ ...p, severity: (e.target.value as ModerationRuleSeverity) || '' }))}
          >
            <option value="">全部严重级别</option>
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
          </select>

          <select
            className="rounded border px-3 py-2"
            value={String(query.enabled ?? '')}
            onChange={(e) => {
              const v = e.target.value;
              setQuery((p) => ({
                ...p,
                enabled: v === '' ? '' : v === 'true',
              }));
            }}
          >
            <option value="">全部状态</option>
            <option value="true">启用</option>
            <option value="false">停用</option>
          </select>
        </div>

        {/* 列表 */}
        <div className="overflow-x-auto rounded border">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-gray-700">
              <tr>
                <th className="text-left p-2">ID</th>
                <th className="text-left p-2">名称</th>
                <th className="text-left p-2">类别</th>
                <th className="text-left p-2">type</th>
                <th className="text-left p-2">pattern</th>
                <th className="text-left p-2">严重</th>
                <th className="text-left p-2">启用</th>
                <th className="text-left p-2">更新时间</th>
                <th className="text-left p-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 ? (
                <tr>
                  <td className="p-3 text-gray-500" colSpan={9}>
                    {loading ? '加载中…' : '暂无规则。你可以点击「新建规则」添加敏感词/黑名单/URL/广告模式规则。'}
                  </td>
                </tr>
              ) : (
                items.map((it) => {
                  const cat = inferCategory(it);
                  return (
                    <tr key={it.id} className="border-t">
                      <td className="p-2 text-gray-600">{it.id}</td>
                      <td className="p-2 font-medium text-gray-900">{it.name}</td>
                      <td className="p-2">{categoryLabel(cat)}</td>
                      <td className="p-2 text-gray-600">{it.type}</td>
                      <td className="p-2 text-gray-700 max-w-[520px]">
                        <div className="truncate" title={it.pattern}>
                          {it.pattern}
                        </div>
                      </td>
                      <td className="p-2">{it.severity}</td>
                      <td className="p-2">
                        <button
                          type="button"
                          onClick={() => toggleEnabled(it)}
                          className={`rounded px-2 py-1 border ${it.enabled ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-50 text-gray-700 border-gray-200'}`}
                          title="点击切换启用状态"
                        >
                          {it.enabled ? '启用' : '停用'}
                        </button>
                      </td>
                      <td className="p-2 text-gray-600">{formatDateTime(it.updatedAt ?? it.createdAt)}</td>
                      <td className="p-2">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            className="rounded border px-2 py-1"
                            onClick={() => openEdit(it)}
                          >
                            编辑
                          </button>
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-red-600 border-red-200"
                            onClick={() => remove(it)}
                          >
                            删除
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Editor Dialog */}
        {editor.open ? (
          <div className="fixed inset-0 z-50">
            <div className="absolute inset-0 bg-black/40" onClick={closeEditor} />
            <div className="absolute inset-0 flex items-center justify-center p-4">
              <div className="w-full max-w-2xl rounded bg-white shadow-lg overflow-hidden">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                  <div className="font-semibold">{editor.mode === 'create' ? '新建规则' : '编辑规则'}</div>
                  <button type="button" className="rounded border px-2 py-1" onClick={closeEditor}>
                    关闭
                  </button>
                </div>

                <div className="p-4 space-y-3">
                  {formError ? (
                    <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{formError}</div>
                  ) : null}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <div className="text-sm text-gray-600">类别</div>
                      <select
                        className="rounded border px-3 py-2 w-full"
                        value={form.category}
                        onChange={(e) => setForm((p) => ({ ...p, category: e.target.value as ModerationRuleCategory }))}
                      >
                        <option value="SENSITIVE">敏感词</option>
                        <option value="BLACKLIST">黑名单</option>
                        <option value="URL">URL/链接</option>
                        <option value="AD">广告模式</option>
                      </select>
                      <div className="text-xs text-gray-500">
                        将映射到后端 type：<b>{categoryToType(form.category)}</b>
                      </div>
                    </div>

                    <div className="space-y-1">
                      <div className="text-sm text-gray-600">严重级别</div>
                      <select
                        className="rounded border px-3 py-2 w-full"
                        value={form.severity}
                        onChange={(e) => setForm((p) => ({ ...p, severity: e.target.value as ModerationRuleSeverity }))}
                      >
                        <option value="LOW">LOW</option>
                        <option value="MEDIUM">MEDIUM</option>
                        <option value="HIGH">HIGH</option>
                      </select>
                    </div>

                    <div className="space-y-1 md:col-span-2">
                      <div className="text-sm text-gray-600">规则名称</div>
                      <input
                        className="rounded border px-3 py-2 w-full"
                        value={form.name}
                        onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                        placeholder="例如：涉政敏感词 / 拉黑域名 / 广告关键词组合"
                      />
                    </div>

                    <div className="space-y-1 md:col-span-2">
                      <div className="text-sm text-gray-600">匹配内容（pattern）</div>
                      <textarea
                        className="rounded border px-3 py-2 w-full min-h-[120px]"
                        value={form.pattern}
                        onChange={(e) => setForm((p) => ({ ...p, pattern: e.target.value }))}
                        placeholder={
                          form.category === 'URL'
                            ? '示例：example.com\nhttps://spam.example.com/\n^https?://(www\\.)?spam\\.com'
                            : form.category === 'AD'
                              ? '示例：加微信|VX|代办|返利|刷流水'
                              : '示例：敏感词1\n敏感词2\n…'
                        }
                      />
                      <div className="text-xs text-gray-500">
                        建议用换行写多条；实际匹配逻辑可由后端处理，这里先作为规则内容存储。
                      </div>
                    </div>

                    <div className="flex items-center gap-2 md:col-span-2">
                      <input
                        id="enabled"
                        type="checkbox"
                        checked={form.enabled}
                        onChange={(e) => setForm((p) => ({ ...p, enabled: e.target.checked }))}
                      />
                      <label htmlFor="enabled" className="text-sm text-gray-700">
                        启用该规则
                      </label>
                    </div>
                  </div>
                </div>

                <div className="px-4 py-3 border-t flex items-center justify-end gap-2">
                  <button type="button" className="rounded border px-4 py-2" onClick={closeEditor} disabled={saving}>
                    取消
                  </button>
                  <button
                    type="button"
                    className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                    onClick={save}
                    disabled={saving}
                  >
                    {saving ? '保存中…' : '保存'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </div>

      <ModerationPipelineHistoryPanel
        title="规则过滤层 · 历史记录"
        initialMode={qidForHistory ? { kind: 'queue', queueId: qidForHistory } : undefined}
        stageFilter={['RULE']}
      />
    </div>
  );
};

export default RulesForm;
