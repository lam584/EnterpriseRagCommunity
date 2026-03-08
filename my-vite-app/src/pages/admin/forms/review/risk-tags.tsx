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
  const [totalElements, setTotalElements] = useState(0);
  const [hasNextPage, setHasNextPage] = useState(false);
  const [items, setItems] = useState<RiskTagDTO[]>([]);

  const [createForm, setCreateForm] = useState({ name: '', slug: '', description: '', active: true, threshold: 0.8 });

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState({ name: '', slug: '', description: '', active: true, threshold: 0.8 });

  const validate = (data: { name: string; slug: string; description: string; threshold: number }) => {
    const v: Record<string, string> = {};
    if (!data.name.trim()) v.name = '名称不能为空';
    else if (data.name.length > MAX_NAME) v.name = `名称不能超过 ${MAX_NAME} 字符`;

    if (!data.slug.trim()) v.slug = 'Slug 不能为空';
    else if (data.slug.length > MAX_SLUG) v.slug = `Slug 不能超过 ${MAX_SLUG} 字符`;
    else if (!isValidSlug(data.slug)) v.slug = 'Slug 必须为 kebab-case（小写字母/数字/短横线）';

    if (data.description && data.description.length > MAX_DESC) v.description = `描述不能超过 ${MAX_DESC} 字符`;

    if (data.threshold < 0 || data.threshold > 1) v.threshold = '阈值必须在 0.0 到 1.0 之间';
    return v;
  };

  const [errors, setErrors] = useState<Record<string, string>>({});

  const copyText = async (label: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setMessage(`已复制：${label}`);
    } catch (e: unknown) {
      setMessage(e instanceof Error ? e.message : String(e));
    }
  };

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
          const content = page.content ?? [];
          const nextTotalPages = typeof page.totalPages === 'number' ? Math.max(1, page.totalPages) : 1;
          const nextTotalElements = typeof page.totalElements === 'number' ? page.totalElements : 0;
          const backendPageIndex = typeof page.number === 'number' ? Math.max(0, page.number) : Math.max(0, pageNo - 1);
          const hasMoreByFlag = page.last === false;
          const hasMoreByPageCount = backendPageIndex + 1 < nextTotalPages;
          const hasMoreByTotal = nextTotalElements > (backendPageIndex + 1) * pageSize;

          setItems(content);
          setTotalPages(nextTotalPages);
          setTotalElements(Math.max(0, nextTotalElements));
          setHasNextPage(hasMoreByFlag || hasMoreByPageCount || hasMoreByTotal);
        } catch (e: unknown) {
          setHasNextPage(false);
          setMessage(e instanceof Error ? e.message : String(e));
        } finally {
          setLoading(false);
        }
      })();
    }, 200);
    return () => window.clearTimeout(t);
  }, [keyword, pageNo, pageSize]);

  useEffect(() => {
    setSelectedId((prev) => {
      if (prev == null) return null;
      return items.some((x) => x.id === prev) ? prev : null;
    });
  }, [items]);

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
        threshold: createForm.threshold,
      });
      setMessage(`已创建风险标签：${created.name} (#${created.id})`);
      setCreateForm({ name: '', slug: '', description: '', active: true, threshold: 0.8 });
      setPageNo(1);
    } catch (e: unknown) {
      setMessage(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = (t: RiskTagDTO) => {
    setSelectedId(t.id);
    setEditingId(t.id);
    setEditForm({
      name: t.name,
      slug: t.slug,
      description: t.description ?? '',
      active: t.active,
      threshold: t.threshold ?? 0.8,
    });
    setErrors({});
    setMessage(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm({ name: '', slug: '', description: '', active: true, threshold: 0.8 });
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
        threshold: editForm.threshold,
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
  const resolvedTotalPages = Math.max(totalPages, Math.ceil(totalElements / pageSize) || 1);
  const canPrev = pageNo > 1;
  const canNext = hasNextPage || pageNo < resolvedTotalPages;

  const inputBase =
    'w-full h-9 rounded-md border bg-white px-3 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500';
  const textareaBase =
    'w-full rounded-md border bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500';
  const labelBase = 'block text-xs font-medium text-gray-600 mb-1';
  return (
    <div className="space-y-4">
      <div className="border border-gray-200 bg-white shadow-sm">
        <div className="flex items-start justify-between gap-4 px-4 pt-4 md:px-5 md:pt-5">
          <div>
            <h3 className="text-base font-semibold text-gray-900">风险标签管理</h3>
            <div className="text-xs text-gray-500 mt-1">新增审核流程使用的风险标签</div>
          </div>
          {message ? (
            <div className="text-xs text-blue-900 bg-blue-50 border border-blue-100 rounded-md px-3 py-2 max-w-[28rem]">
              {message}
            </div>
          ) : null}
        </div>

        <form onSubmit={submitCreate} className="px-4 pb-4 pt-3 md:px-5 md:pb-5 grid grid-cols-1 md:grid-cols-12 gap-3">
          <div className="md:col-span-4">
            <label className={labelBase}>名称</label>
            <input
              className={`${inputBase} ${errors.name ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20' : 'border-gray-300'}`}
              value={createForm.name}
              onChange={(e) => setCreateForm((p) => ({ ...p, name: e.target.value }))}
              onBlur={onCreateNameBlur}
              placeholder="例如 scam"
              disabled={submitting}
            />
            {errors.name ? <div className="text-xs text-red-600 mt-1">{errors.name}</div> : null}
          </div>

          <div className="md:col-span-4">
            <label className={labelBase}>标签唯一标识（Slug）</label>
            <input
              className={`${inputBase} ${errors.slug ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20' : 'border-gray-300'}`}
              value={createForm.slug}
              onChange={(e) => setCreateForm((p) => ({ ...p, slug: e.target.value }))}
              placeholder="kebab-case"
              disabled={submitting}
            />
            {errors.slug ? <div className="text-xs text-red-600 mt-1">{errors.slug}</div> : null}
          </div>

          <div className="md:col-span-2">
            <label className={labelBase}>启用</label>
            <select
              className={`${inputBase} border-gray-300`}
              value={createForm.active ? 'true' : 'false'}
              onChange={(e) => setCreateForm((p) => ({ ...p, active: e.target.value === 'true' }))}
              disabled={submitting}
            >
              <option value="true">启用</option>
              <option value="false">禁用</option>
            </select>
          </div>

          <div className="md:col-span-2">
            <label className={labelBase}>阈值 (0-1)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              max="1"
              className={`${inputBase} ${errors.threshold ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20' : 'border-gray-300'}`}
              value={createForm.threshold}
              onChange={(e) => setCreateForm((p) => ({ ...p, threshold: parseFloat(e.target.value) }))}
              disabled={submitting}
            />
            {errors.threshold ? <div className="text-xs text-red-600 mt-1">{errors.threshold}</div> : null}
          </div>

          <div className="md:col-span-12">
            <label className={labelBase}>描述（可选）</label>
            <textarea
              className={`${textareaBase} ${errors.description ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20' : 'border-gray-300'}`}
              value={createForm.description}
              onChange={(e) => setCreateForm((p) => ({ ...p, description: e.target.value }))}
              rows={2}
              disabled={submitting}
            />
            {errors.description ? <div className="text-xs text-red-600 mt-1">{errors.description}</div> : null}
          </div>

          <div className="md:col-span-12 flex justify-end">
            <button
              type="submit"
              className="h-9 rounded-md bg-blue-600 text-white px-4 text-sm font-medium hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
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
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm bg-white hover:bg-gray-50 disabled:opacity-60"
              disabled={loading || items.length === 0}
              onClick={() =>
                void copyText(
                  'allowed_labels(name)',
                  JSON.stringify(
                    items
                      .filter((x) => Boolean(x.active))
                      .map((x) => x.name)
                      .filter((x) => Boolean(x && String(x).trim())),
                    null,
                    2
                  )
                )
              }
            >
              复制启用名称 JSON
            </button>
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm bg-white hover:bg-gray-50 disabled:opacity-60"
              disabled={loading || items.length === 0}
              onClick={() =>
                void copyText(
                  'allowed_labels(slug)',
                  JSON.stringify(
                    items
                      .filter((x) => Boolean(x.active))
                      .map((x) => x.slug)
                      .filter((x) => Boolean(x && String(x).trim())),
                    null,
                    2
                  )
                )
              }
            >
              复制启用 Slug JSON
            </button>
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm bg-white hover:bg-gray-50 disabled:opacity-60"
              disabled={loading}
              onClick={() => void copyText('hard_reject_labels', JSON.stringify([], null, 2))}
            >
              复制 hard_reject_labels 空数组
            </button>
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm bg-white hover:bg-gray-50 disabled:opacity-60"
              disabled={loading || submitting || editingId != null || items.length === 0}
              onClick={() => {
                const target = selectedId == null ? null : items.find((x) => x.id === selectedId) ?? null;
                if (!target) {
                  setMessage('请先在表格中点击选择一行，再点击编辑');
                  return;
                }
                startEdit(target);
              }}
            >
              编辑
            </button>
            <div className="text-sm text-gray-600">{loading ? '加载中...' : `第 ${pageNo} / ${resolvedTotalPages} 页`}</div>
          </div>
        </div>

        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2">ID</th>
                <th className="text-left px-3 py-2">名称</th>
                <th className="text-left px-3 py-2">标签唯一标识（Slug）</th>
                <th className="text-left px-3 py-2">描述</th>
                <th className="text-left px-3 py-2">阈值</th>
                <th className="text-left px-3 py-2">使用次数</th>
                <th className="text-left px-3 py-2">启用</th>
                <th className="text-left px-3 py-2">创建时间</th>
                <th className="text-right px-3 py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 ? (
                <tr>
                  <td className="px-3 py-4 text-gray-500" colSpan={9}>
                    {loading ? '加载中…' : '暂无数据'}
                  </td>
                </tr>
              ) : (
                items.map((t) => {
                  const editing = editingId === t.id;
                  const selected = selectedId === t.id;
                  return (
                    <tr
                      key={t.id}
                      className={`border-t ${selected ? 'bg-blue-50' : ''} ${editing ? '' : 'hover:bg-gray-50 cursor-pointer'}`}
                      onClick={
                        editing
                          ? undefined
                          : () => {
                              setSelectedId(t.id);
                            }
                      }
                    >
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
                      <td className="px-3 py-2">
                        {editing ? (
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            max="1"
                            className="rounded border px-2 py-1 w-20"
                            value={editForm.threshold}
                            onChange={(e) => setEditForm((p) => ({ ...p, threshold: parseFloat(e.target.value) }))}
                          />
                        ) : (
                          t.threshold
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
                            <button
                              type="button"
                              className="rounded border border-red-300 text-red-700 px-3 py-1 ml-2 hover:bg-red-50 disabled:opacity-60"
                              disabled={submitting || t.system}
                              onClick={() => void handleDelete(t.id)}
                            >
                              删除
                            </button>
                          </>
                        ) : null}
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
            {canPrev ? (
              <button
                type="button"
                className="rounded border px-3 py-1 disabled:opacity-60"
                disabled={loading}
                onClick={() => setPageNo((p) => Math.max(1, p - 1))}
              >
                上一页
              </button>
            ) : null}
            {canNext ? (
              <button
                type="button"
                className="rounded border px-3 py-1 disabled:opacity-60"
                disabled={loading}
                onClick={() => setPageNo((p) => p + 1)}
              >
                下一页
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RiskTagsForm;
