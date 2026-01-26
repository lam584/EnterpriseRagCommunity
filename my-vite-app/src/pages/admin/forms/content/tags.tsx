// tags.tsx - 管理员标签管理（前端模拟，遵循后端 TagDTO 约束）
import React, { useEffect, useState } from 'react';
import { createTag, listTagsPage, updateTag, deleteTag as deleteTagApi, type TagDTO, type TagCreateDTO, slugify, type TagType } from '../../../../services/tagService';

const MAX_NAME = 64;
const MAX_DESC = 255;
const MAX_SLUG = 96;

const TYPES: TagType[] = ['TOPIC', 'LANGUAGE', 'RISK', 'SYSTEM'];

const TagsForm: React.FC = () => {
  // 表单数据（参考 TagDTO）
  const [form, setForm] = useState<TagCreateDTO>({ tenantId: 1, type: 'TOPIC', name: '', slug: '', description: '', system: false, active: true });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // 列表与状态
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<TagDTO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [seeding, setSeeding] = useState(false);
  const [typeFilter, setTypeFilter] = useState<TagType | 'ALL'>('ALL');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalPages, setTotalPages] = useState(1);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const ctrl = new AbortController();
    setLoading(true);
    const t = window.setTimeout(() => {
      (async () => {
        try {
          const page = await listTagsPage(
            {
              page: pageNo,
              pageSize,
              keyword: keyword.trim() ? keyword.trim() : undefined,
              type: typeFilter === 'ALL' ? undefined : typeFilter,
              sortBy: 'createdAt',
              sortOrder: 'desc',
            },
            { signal: ctrl.signal }
          );
          setItems(page.content ?? []);
          setTotalPages(Math.max(1, page.totalPages ?? 1));
        } finally {
          setLoading(false);
        }
      })();
    }, 200);
    return () => {
      window.clearTimeout(t);
      ctrl.abort();
    };
  }, [keyword, pageNo, pageSize, reloadToken, typeFilter]);

  const onChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value, type, checked } = e.target as HTMLInputElement;
    if (name === 'tenantId') {
      const n = value === '' ? undefined : Number(value);
      setForm(prev => ({ ...prev, tenantId: Number.isNaN(n as number) ? undefined : (n as number) }));
      if (errors.tenantId) setErrors(prev => ({ ...prev, tenantId: '' }));
      return;
    }
    if (type === 'checkbox') {
      setForm(prev => ({ ...prev, [name]: checked } as never));
      if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
      return;
    }
    setForm(prev => ({ ...prev, [name]: value } as never));
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
  };

  const onNameBlur = () => {
    // 自动生成 slug（若为空）
    if (!form.slug?.trim() && form.name?.trim()) {
      const s = slugify(form.name);
      setForm(prev => ({ ...prev, slug: s }));
    }
  };

  // 本地校验（与服务约束一致）
  const validate = (data: TagCreateDTO) => {
    const v: Record<string, string> = {};
    if (!data.type) v.type = '请选择类型';
    if (!data.name || !data.name.trim()) v.name = 'Tag name is required';
    else if (data.name.length > MAX_NAME) v.name = `Tag name must not exceed ${MAX_NAME} characters`;
    if (!data.slug || !data.slug.trim()) v.slug = 'Slug is required';
    else if (data.slug.length > MAX_SLUG) v.slug = `Slug must not exceed ${MAX_SLUG} characters`;
    if (data.description && data.description.length > MAX_DESC) v.description = `Description must not exceed ${MAX_DESC} characters`;
    return v;
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    const v = validate(form);
    setErrors(v);
    if (Object.keys(v).length) return;
    try {
      setSubmitting(true);
      const created = await createTag({
        tenantId: form.tenantId,
        type: form.type,
        name: form.name.trim(),
        slug: form.slug.trim(),
        description: form.description?.trim() || undefined,
        system: !!form.system,
        active: !!form.active,
      });
      setMessage(`已创建标签：${created.name} (#${created.id})`);
      // 重置 name/description/slug，保留 tenantId/type/system/active
      setForm(prev => ({ tenantId: prev.tenantId, type: prev.type, name: '', slug: '', description: '', system: prev.system, active: prev.active }));
      setPageNo(1);
      setReloadToken(x => x + 1);
    } catch (err: unknown) {
      const fieldErrors = (err as { fieldErrors?: Record<string, string> } | undefined)?.fieldErrors;
      if (fieldErrors) setErrors(fieldErrors);
      const msg = err instanceof Error ? err.message : '创建失败';
      setMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const resetForm = () => {
    setForm(prev => ({ tenantId: prev.tenantId, type: prev.type, name: '', slug: '', description: '', system: prev.system, active: prev.active }));
    setErrors({});
    setMessage(null);
  };

  const refresh = async () => {
    setReloadToken(x => x + 1);
  };

  const seedSamples = async () => {
    setSeeding(true);
    try {
      const samples: Array<Partial<TagCreateDTO> & { name: string; slug?: string }> = [
        { type: 'LANGUAGE', name: 'Java', description: 'Java 生态、JVM、Spring 等' },
        { type: 'LANGUAGE', name: 'Python', description: '数据科学、AI、脚本自动化' },
        { type: 'TOPIC', name: '前端', description: 'React / Vue / 工程化' },
        { type: 'TOPIC', name: '后端', description: '微服务、数据库、缓存与消息队列' },
        { type: 'TOPIC', name: '数据库', description: 'MySQL / Postgres / NoSQL' },
        { type: 'TOPIC', name: '架构', description: 'DDD、事件驱动、可观测性' },
      ];
      for (const s of samples) {
        try {
          await createTag({ tenantId: form.tenantId ?? 1, type: (s.type as TagType) ?? 'TOPIC', name: s.name, slug: slugify(s.slug ?? s.name), description: s.description });
        } catch {
          // 忽略重复
        }
      }
      setPageNo(1);
      await refresh();
    } finally {
      setSeeding(false);
    }
  };

  // const incUsage = async (t: TagDTO) => {
  //   try {
  //     await incrementUsage([t.name]);
  //     setItems(prev => prev.map(x => (x.id === t.id ? { ...x, usageCount: (x.usageCount ?? 0) + 1 } : x)));
  //   } catch (e: unknown) {
  //     const msg = e instanceof Error ? e.message : '操作失败';
  //     alert(msg);
  //   }
  // };

  // 编辑状态管理
  const [editingId, setEditingId] = useState<number | null>(null);
  type TagEditForm = { type: TagType; name: string; slug: string; description?: string; system?: boolean; active?: boolean };
  const [editForm, setEditForm] = useState<TagEditForm>({ type: 'TOPIC', name: '', slug: '', description: '', system: false, active: true });

  const startEdit = (tag: TagDTO) => {
    setEditingId(tag.id);
    setEditForm({ type: tag.type, name: tag.name, slug: tag.slug, description: tag.description || '', system: !!tag.system, active: !!tag.active });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm({ type: 'TOPIC', name: '', slug: '', description: '', system: false, active: true });
  };

  const saveEdit = async (id: number) => {
    setMessage(null);
    const v = validate({ tenantId: form.tenantId, type: editForm.type, name: editForm.name.trim(), slug: editForm.slug.trim(), description: editForm.description ?? '', system: editForm.system, active: editForm.active });
    setErrors(v);
    if (Object.keys(v).length) return;
    try {
      setSubmitting(true);
      const updated = await updateTag(id, {
        type: editForm.type,
        name: editForm.name.trim(),
        slug: editForm.slug.trim(),
        description: editForm.description?.trim() || undefined,
        system: !!editForm.system,
        active: !!editForm.active,
      });
      setMessage(`已更新标签：${updated.name} (#${updated.id})`);
      setItems(prev => prev.map(it => (it.id === updated.id ? updated : it)));
      setEditingId(null);
    } catch (err: unknown) {
      const fieldErrors = (err as { fieldErrors?: Record<string, string> } | undefined)?.fieldErrors;
      if (fieldErrors) setErrors(fieldErrors);
      const msg = err instanceof Error ? err.message : '更新失败';
      setMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('确定要删除此标签吗？')) return;
    setLoading(true);
    try {
      await deleteTagApi(id);
      setItems(prev => prev.filter(it => it.id !== id));
      setReloadToken(x => x + 1);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '删除失败';
      alert(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* 新建标签表单 */}
      <div className="bg-white rounded-lg shadow p-8 space-y-4">
        <h3 className="text-lg font-semibold">标签体系管理</h3>
        {message && (
          <div className={`text-sm ${Object.keys(errors).length ? 'text-red-600' : 'text-green-600'}`}>{message}</div>
        )}
        <form onSubmit={onSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-gray-700 mb-1">Tenant ID（可选）</label>
            <input
              name="tenantId"
              type="number"
              value={form.tenantId == null ? '' : form.tenantId}
              onChange={onChange}
              className={`w-full rounded border px-3 py-2 ${errors.tenantId ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="默认 1"
            />
            {errors.tenantId && <p className="text-xs text-red-600 mt-1">{errors.tenantId}</p>}
          </div>

          <div>
            <label className="block text-sm text-gray-700 mb-1">标签类型</label>
            <select name="type" value={form.type} onChange={onChange} className={`w-full rounded border px-3 py-2 ${errors.type ? 'border-red-500' : 'border-gray-300'}`}>
              {TYPES.map(t => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
            {errors.type && <p className="text-xs text-red-600 mt-1">{errors.type}</p>}
          </div>

          <div>
            <label className="block text-sm text-gray-700 mb-1">标签名称</label>
            <input
              name="name"
              value={form.name}
              onChange={onChange}
              onBlur={onNameBlur}
              className={`w-full rounded border px-3 py-2 ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="必填，不超过 64 个字符"
              maxLength={MAX_NAME}
              required
            />
            <div className="flex items-center justify-between mt-1">
              <p className="text-xs text-gray-400">不超过 {MAX_NAME} 个字符</p>
              <p className="text-xs text-gray-400">{form.name.length}/{MAX_NAME}</p>
            </div>
            {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
          </div>

          <div>
            <label className="block text-sm text-gray-700 mb-1">Slug</label>
            <input
              name="slug"
              value={form.slug}
              onChange={onChange}
              className={`w-full rounded border px-3 py-2 ${errors.slug ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="必填，kebab-case，不超过 96 个字符"
              maxLength={MAX_SLUG}
              required
            />
            <div className="flex items-center justify-between mt-1">
              <p className="text-xs text-gray-400">不超过 {MAX_SLUG} 个字符</p>
              <p className="text-xs text-gray-400">{form.slug.length}/{MAX_SLUG}</p>
            </div>
            {errors.slug && <p className="text-xs text-red-600 mt-1">{errors.slug}</p>}
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm text-gray-700 mb-1">描述</label>
            <textarea
              name="description"
              value={form.description || ''}
              onChange={onChange}
              className={`w-full rounded border px-3 py-2 ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="可选，不超过 255 个字符"
              maxLength={MAX_DESC}
              rows={3}
            />
            <div className="flex items-center justify-between mt-1">
              <p className="text-xs text-gray-400">不超过 {MAX_DESC} 个字符</p>
              <p className="text-xs text-gray-400">{(form.description?.length ?? 0)}/{MAX_DESC}</p>
            </div>
            {errors.description && <p className="text-xs text-red-600 mt-1">{errors.description}</p>}
          </div>

          <div className="flex items-center gap-4 md:col-span-2">
            <label className="inline-flex items-center gap-2">
              <input type="checkbox" name="system" checked={!!form.system} onChange={onChange} />
              <span className="text-sm text-gray-700">系统标签</span>
            </label>
            <label className="inline-flex items-center gap-2">
              <input type="checkbox" name="active" checked={!!form.active} onChange={onChange} />
              <span className="text-sm text-gray-700">可用</span>
            </label>
          </div>

          <div className="md:col-span-2 flex justify-end gap-3">
            <button
              type="button"
              onClick={resetForm}
              className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
              disabled={submitting}
            >
              重置
            </button>
            <button
              type="submit"
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
              disabled={submitting}
            >
              {submitting ? '保存中...' : '保存'}
            </button>
          </div>
        </form>
      </div>

      {/* 搜索 + 操作 */}
      <div className="bg-white rounded-lg shadow p-6 space-y-4">
        <div className="flex flex-col md:flex-row md:items-center gap-3">
          <input
            className="rounded border px-3 py-2 border-gray-300 md:flex-1"
            placeholder="按名称、Slug、类型或 ID 搜索"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPageNo(1);
            }}
          />
          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value as TagType | 'ALL');
              setPageNo(1);
            }}
            disabled={loading}
            title="按类型过滤"
          >
            <option value="ALL">全部类型</option>
            {TYPES.map(t => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
          <select
            className="rounded border px-3 py-2 border-gray-300"
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value));
              setPageNo(1);
            }}
            disabled={loading}
            title="每页数量"
          >
            {[10, 25, 50, 100].map((n) => (
              <option key={n} value={n}>
                {n}/页
              </option>
            ))}
          </select>
          <div className="flex gap-2">
            <button type="button" className="rounded border px-4 py-2" onClick={() => setKeyword('')} disabled={loading}>清空</button>
            <button type="button" className="rounded border px-4 py-2 text-purple-700 border-purple-300" onClick={seedSamples} disabled={seeding} title="仅前端内存模拟">
              {seeding ? '生成中…' : '生成示例数据'}
            </button>
            <button type="button" className="rounded border px-4 py-2" onClick={refresh} disabled={loading}>刷新</button>
          </div>
        </div>

        {/* 列表 */}
        {loading ? (
          <p className="text-sm text-gray-500">加载中…</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-gray-500">暂无数据。</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2 pr-4">ID</th>
                  <th className="py-2 pr-4">Tenant</th>
                  <th className="py-2 pr-4">类型</th>
                  <th className="py-2 pr-4">名称</th>
                  <th className="py-2 pr-4">Slug</th>
                  <th className="py-2 pr-4">描述</th>
                  <th className="py-2 pr-4">系统</th>
                  <th className="py-2 pr-4">可用</th>
                  <th className="py-2 pr-4">使用量</th>
                  <th className="py-2 pr-4">创建时间</th>
                  <th className="py-2 pr-4">操作</th>
                </tr>
              </thead>
              <tbody>
                {items.map(t => (
                  <tr key={t.id} className="border-b hover:bg-gray-50">
                    <td className="py-2 pr-4">{t.id}</td>
                    <td className="py-2 pr-4">{t.tenantId ?? '-'}</td>
                    <td className="py-2 pr-4">
                      {editingId === t.id ? (
                        <select
                          className={`w-full rounded border px-2 py-1 text-sm ${errors.type ? 'border-red-500' : 'border-gray-300'}`}
                          value={editForm.type}
                          onChange={(e) => setEditForm(prev => ({ ...prev, type: e.target.value as TagType }))}
                        >
                          {TYPES.map(tp => <option key={tp} value={tp}>{tp}</option>)}
                        </select>
                      ) : (
                        <span>{t.type}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4 max-w-[240px]">
                      {editingId === t.id ? (
                        <input
                          className={`w-full rounded border px-2 py-1 text-sm ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
                          value={editForm.name}
                          onChange={(e) => setEditForm(prev => ({ ...prev, name: e.target.value }))}
                          maxLength={MAX_NAME}
                        />
                      ) : (
                        <span className="truncate block" title={t.name}>{t.name}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4 max-w-[200px]">
                      {editingId === t.id ? (
                        <input
                          className={`w-full rounded border px-2 py-1 text-sm ${errors.slug ? 'border-red-500' : 'border-gray-300'}`}
                          value={editForm.slug}
                          onChange={(e) => setEditForm(prev => ({ ...prev, slug: e.target.value }))}
                          maxLength={MAX_SLUG}
                        />
                      ) : (
                        <span className="truncate block" title={t.slug}>{t.slug}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4 max-w-[360px]">
                      {editingId === t.id ? (
                        <input
                          className={`w-full rounded border px-2 py-1 text-sm ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
                          value={editForm.description || ''}
                          onChange={(e) => setEditForm(prev => ({ ...prev, description: e.target.value }))}
                          maxLength={MAX_DESC}
                        />
                      ) : (
                        <span className="truncate block" title={t.description || ''}>{t.description ?? '-'}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">
                      {editingId === t.id ? (
                        <input type="checkbox" checked={!!editForm.system} onChange={(e) => setEditForm(prev => ({ ...prev, system: e.target.checked }))} />
                      ) : (
                        <span>{t.system ? '是' : '否'}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">
                      {editingId === t.id ? (
                        <input type="checkbox" checked={!!editForm.active} onChange={(e) => setEditForm(prev => ({ ...prev, active: e.target.checked }))} />
                      ) : (
                        <span>{t.active ? '是' : '否'}</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">{t.usageCount ?? 0}</td>
                    <td className="py-2 pr-4">{new Date(t.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4">
                      {editingId === t.id ? (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs"
                            onClick={() => saveEdit(t.id)}
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs"
                            onClick={cancelEdit}
                          >
                            取消
                          </button>
                        </div>
                      ) : (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs"
                            onClick={() => startEdit(t)}
                          >
                            编辑
                          </button>
                          <button
                            type="button"
                            className="rounded border px-2 py-1 text-xs text-red-600"
                            onClick={() => handleDelete(t.id)}
                          >
                            删除
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex items-center justify-end gap-2 pt-3">
              <button
                type="button"
                className="rounded border px-3 py-1 text-sm disabled:opacity-50"
                disabled={loading || pageNo <= 1}
                onClick={() => setPageNo(p => Math.max(1, p - 1))}
              >
                上一页
              </button>
              <div className="text-sm text-gray-600">
                第 {pageNo} 页 / 共 {Math.max(1, totalPages)} 页
              </div>
              <button
                type="button"
                className="rounded border px-3 py-1 text-sm disabled:opacity-50"
                disabled={loading || pageNo >= totalPages}
                onClick={() => setPageNo(p => p + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default TagsForm;
