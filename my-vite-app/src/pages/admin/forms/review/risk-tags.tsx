import React, { useEffect, useState } from 'react';
import { createRiskTag, deleteRiskTag, listRiskTagsPage, updateRiskTag, type RiskTagDTO } from '../../../../services/riskTagService';
import { slugify } from '../../../../services/tagService';

const MAX_NAME = 64;
const MAX_DESC = 255;
const MAX_SLUG = 96;

function isValidSlug(s: string): boolean {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(s);
}

const RiskTagsForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalPages, setTotalPages] = useState(1);
  const [items, setItems] = useState<RiskTagDTO[]>([]);

  const [createForm, setCreateForm] = useState({ name: '', slug: '', description: '', active: true });

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState({ name: '', slug: '', description: '', active: true });

  const validate = (data: { name: string; slug: string; description: string }) => {
    const v: Record<string, string> = {};
    if (!data.name.trim()) v.name = '名称不能为空';
    else if (data.name.length > MAX_NAME) v.name = `名称不能超过 ${MAX_NAME} 字符`;

    if (!data.slug.trim()) v.slug = 'Slug 不能为空';
    else if (data.slug.length > MAX_SLUG) v.slug = `Slug 不能超过 ${MAX_SLUG} 字符`;
    else if (!isValidSlug(data.slug)) v.slug = 'Slug 必须为 kebab-case（小写字母/数字/短横线）';

    if (data.description && data.description.length > MAX_DESC) v.description = `描述不能超过 ${MAX_DESC} 字符`;
    return v;
  };

  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    const t = window.setTimeout(() => {
      (async () => {
        setLoading(true);
        setMessage(null);
        try {
          const page = await listRiskTagsPage({
            page: pageNo,
            pageSize,
            keyword: keyword.trim() ? keyword.trim() : undefined,
          });
          setItems(page.content ?? []);
          setTotalPages(Math.max(1, page.totalPages ?? 1));
        } catch (e: unknown) {
          setMessage(e instanceof Error ? e.message : String(e));
        } finally {
          setLoading(false);
        }
      })();
    }, 200);
    return () => window.clearTimeout(t);
  }, [keyword, pageNo, pageSize]);

  const onCreateNameBlur = () => {
    if (!createForm.slug.trim() && createForm.name.trim()) {
      setCreateForm((p) => ({ ...p, slug: slugify(p.name) }));
    }
  };

  const submitCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    const v = validate(createForm);
    setErrors(v);
    if (Object.keys(v).length) return;

    setSubmitting(true);
    try {
      const created = await createRiskTag({
        tenantId: 1,
        name: createForm.name.trim(),
        slug: createForm.slug.trim(),
        description: createForm.description.trim() ? createForm.description.trim() : undefined,
        active: createForm.active,
      });
      setMessage(`已创建风险标签：${created.name} (#${created.id})`);
      setCreateForm({ name: '', slug: '', description: '', active: true });
      setPageNo(1);
    } catch (e: unknown) {
      setMessage(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = (t: RiskTagDTO) => {
    setEditingId(t.id);
    setEditForm({
      name: t.name,
      slug: t.slug,
      description: t.description ?? '',
      active: t.active,
    });
    setErrors({});
    setMessage(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm({ name: '', slug: '', description: '', active: true });
    setErrors({});
  };

  const saveEdit = async (id: number) => {
    setMessage(null);
    const v = validate(editForm);
    setErrors(v);
    if (Object.keys(v).length) return;

    setSubmitting(true);
    try {
      const updated = await updateRiskTag(id, {
        name: editForm.name.trim(),
        slug: editForm.slug.trim(),
        description: editForm.description.trim() ? editForm.description.trim() : null,
        active: editForm.active,
      });
      setItems((prev) => prev.map((x) => (x.id === id ? updated : x)));
      setEditingId(null);
      setMessage(`已更新风险标签：${updated.name} (#${updated.id})`);
    } catch (e: unknown) {
      setMessage(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    const ok = window.confirm('确定删除该风险标签吗？（正在使用的标签无法删除）');
    if (!ok) return;
    setSubmitting(true);
    setMessage(null);
    try {
      await deleteRiskTag(id);
      setItems((prev) => prev.filter((x) => x.id !== id));
      setMessage('删除成功');
    } catch (e: unknown) {
      setMessage(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const canPrev = pageNo > 1;
  const canNext = pageNo < totalPages;

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow p-6 space-y-4">
        <h3 className="text-lg font-semibold">风险标签管理</h3>
        {message ? <div className="text-sm text-gray-700">{message}</div> : null}

        <form onSubmit={submitCreate} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-gray-700 mb-1">名称</label>
            <input
              className={`w-full rounded border px-3 py-2 ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
              value={createForm.name}
              onChange={(e) => setCreateForm((p) => ({ ...p, name: e.target.value }))}
              onBlur={onCreateNameBlur}
              placeholder="例如 scam"
              disabled={submitting}
            />
            {errors.name ? <div className="text-xs text-red-600 mt-1">{errors.name}</div> : null}
          </div>

          <div>
            <label className="block text-sm text-gray-700 mb-1">Slug</label>
            <input
              className={`w-full rounded border px-3 py-2 ${errors.slug ? 'border-red-500' : 'border-gray-300'}`}
              value={createForm.slug}
              onChange={(e) => setCreateForm((p) => ({ ...p, slug: e.target.value }))}
              placeholder="kebab-case"
              disabled={submitting}
            />
            {errors.slug ? <div className="text-xs text-red-600 mt-1">{errors.slug}</div> : null}
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm text-gray-700 mb-1">描述（可选）</label>
            <textarea
              className={`w-full rounded border px-3 py-2 ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
              value={createForm.description}
              onChange={(e) => setCreateForm((p) => ({ ...p, description: e.target.value }))}
              rows={2}
              disabled={submitting}
            />
            {errors.description ? <div className="text-xs text-red-600 mt-1">{errors.description}</div> : null}
          </div>

          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={createForm.active}
              onChange={(e) => setCreateForm((p) => ({ ...p, active: e.target.checked }))}
              disabled={submitting}
            />
            启用
          </label>

          <div className="md:col-span-2">
            <button
              type="submit"
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
              disabled={submitting}
            >
              {submitting ? '提交中...' : '新增风险标签'}
            </button>
          </div>
        </form>
      </div>

      <div className="bg-white rounded-lg shadow p-6 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <input
              className="rounded border px-3 py-2"
              placeholder="搜索 name/slug/description"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPageNo(1);
              }}
            />
            <select
              className="rounded border px-3 py-2"
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPageNo(1);
              }}
            >
              <option value={10}>每页 10</option>
              <option value={25}>每页 25</option>
              <option value={50}>每页 50</option>
              <option value={100}>每页 100</option>
            </select>
          </div>
          <div className="text-sm text-gray-600">
            {loading ? '加载中...' : `第 ${pageNo} / ${totalPages} 页`}
          </div>
        </div>

        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2">ID</th>
                <th className="text-left px-3 py-2">名称</th>
                <th className="text-left px-3 py-2">Slug</th>
                <th className="text-left px-3 py-2">描述</th>
                <th className="text-left px-3 py-2">使用次数</th>
                <th className="text-left px-3 py-2">启用</th>
                <th className="text-left px-3 py-2">创建时间</th>
                <th className="text-right px-3 py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 ? (
                <tr>
                  <td className="px-3 py-4 text-gray-500" colSpan={8}>
                    {loading ? '加载中…' : '暂无数据'}
                  </td>
                </tr>
              ) : (
                items.map((t) => {
                  const editing = editingId === t.id;
                  return (
                    <tr key={t.id} className="border-t">
                      <td className="px-3 py-2">{t.id}</td>
                      <td className="px-3 py-2">
                        {editing ? (
                          <input
                            className="rounded border px-2 py-1 w-full"
                            value={editForm.name}
                            onChange={(e) => setEditForm((p) => ({ ...p, name: e.target.value }))}
                          />
                        ) : (
                          t.name
                        )}
                      </td>
                      <td className="px-3 py-2">
                        {editing ? (
                          <input
                            className="rounded border px-2 py-1 w-full"
                            value={editForm.slug}
                            onChange={(e) => setEditForm((p) => ({ ...p, slug: e.target.value }))}
                          />
                        ) : (
                          <span className="text-gray-700">{t.slug}</span>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        {editing ? (
                          <input
                            className="rounded border px-2 py-1 w-full"
                            value={editForm.description}
                            onChange={(e) => setEditForm((p) => ({ ...p, description: e.target.value }))}
                          />
                        ) : (
                          <span className="text-gray-700">{t.description ?? ''}</span>
                        )}
                      </td>
                      <td className="px-3 py-2">{t.usageCount}</td>
                      <td className="px-3 py-2">
                        {editing ? (
                          <input
                            type="checkbox"
                            checked={editForm.active}
                            onChange={(e) => setEditForm((p) => ({ ...p, active: e.target.checked }))}
                          />
                        ) : t.active ? (
                          '是'
                        ) : (
                          '否'
                        )}
                      </td>
                      <td className="px-3 py-2">{new Date(t.createdAt).toLocaleString()}</td>
                      <td className="px-3 py-2 text-right whitespace-nowrap">
                        {editing ? (
                          <>
                            <button
                              type="button"
                              className="rounded bg-blue-600 text-white px-3 py-1 mr-2 disabled:opacity-60"
                              disabled={submitting}
                              onClick={() => void saveEdit(t.id)}
                            >
                              保存
                            </button>
                            <button
                              type="button"
                              className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-60"
                              disabled={submitting}
                              onClick={cancelEdit}
                            >
                              取消
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              type="button"
                              className="rounded border px-3 py-1 mr-2 hover:bg-gray-50 disabled:opacity-60"
                              disabled={submitting}
                              onClick={() => startEdit(t)}
                            >
                              编辑
                            </button>
                            <button
                              type="button"
                              className="rounded border border-red-300 text-red-700 px-3 py-1 hover:bg-red-50 disabled:opacity-60"
                              disabled={submitting || t.system}
                              onClick={() => void handleDelete(t.id)}
                            >
                              删除
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between text-sm">
          <div className="text-gray-600">共 {items.length} 条（本页）</div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1 disabled:opacity-60"
              disabled={!canPrev || loading}
              onClick={() => setPageNo((p) => Math.max(1, p - 1))}
            >
              上一页
            </button>
            <button
              type="button"
              className="rounded border px-3 py-1 disabled:opacity-60"
              disabled={!canNext || loading}
              onClick={() => setPageNo((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RiskTagsForm;
