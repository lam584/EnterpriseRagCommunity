import React, { useState } from 'react';
import { adminCreateBoard, BoardCreateDTO } from '../../../../services/boardService';

const BoardForm: React.FC = () => {
  const [form, setForm] = useState<BoardCreateDTO>({ tenantId: 1, parentId: null, name: '', description: '', visible: true, sortOrder: 0 });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const onChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value, type, checked } = e.target as HTMLInputElement;
    setForm(prev => ({
      ...prev,
      [name]: type === 'number' ? (value === '' ? undefined : Number(value))
              : type === 'checkbox' ? checked
              : value
    }));
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
  };

  const validate = (data: BoardCreateDTO) => {
    const v: Record<string, string> = {};
    if (data.tenantId != null && Number.isNaN(data.tenantId)) v.tenantId = 'Invalid Tenant ID';
    if (data.parentId != null && Number.isNaN(data.parentId as any)) v.parentId = 'Invalid Parent ID';
    if (!data.name || !data.name.trim()) v.name = 'Name is required';
    else if (data.name.length > 64) v.name = 'Max 64 characters';
    if (data.description && data.description.length > 255) v.description = 'Max 255 characters';
    if (data.sortOrder != null && !Number.isInteger(data.sortOrder)) v.sortOrder = 'Must be integer';
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
      const created = await adminCreateBoard(form);
      setMessage(`已创建版块：${created.name} (#${created.id})`);
      setForm(prev => ({ tenantId: prev.tenantId, parentId: null, name: '', description: '', visible: true, sortOrder: 0 }));
    } catch (err: any) {
      const fieldErrors = err?.fieldErrors as Record<string, string> | undefined;
      if (fieldErrors) setErrors(fieldErrors);
      setMessage(err?.message || '创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow p-8 space-y-4">
      <h3 className="text-lg font-semibold">版块管理</h3>
      {message && (
        <div className={`text-sm ${Object.keys(errors).length ? 'text-red-600' : 'text-green-600'}`}>{message}</div>
      )}
      <form onSubmit={onSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm text-gray-700 mb-1">Tenant ID（可选）</label>
          <input
            name="tenantId"
            type="number"
            value={form.tenantId ?? ''}
            onChange={onChange}
            className={`w-full rounded border px-3 py-2 ${errors.tenantId ? 'border-red-500' : 'border-gray-300'}`}
            placeholder="例如：1（留空表示无租户）"
          />
          {errors.tenantId && <p className="text-xs text-red-600 mt-1">{errors.tenantId}</p>}
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-1">父板块ID（可选）</label>
          <input
            name="parentId"
            type="number"
            value={form.parentId ?? ''}
            onChange={onChange}
            className={`w-full rounded border px-3 py-2 ${errors.parentId ? 'border-red-500' : 'border-gray-300'}`}
            placeholder="例如：上级板块ID"
          />
          {errors.parentId && <p className="text-xs text-red-600 mt-1">{errors.parentId}</p>}
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-1">名称</label>
          <input
            name="name"
            value={form.name}
            onChange={onChange}
            className={`w-full rounded border px-3 py-2 ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
            placeholder="版块名称"
            maxLength={64}
          />
          <p className="text-xs text-gray-400 mt-1">不超过 64 个字符</p>
          {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
        </div>
        <div className="md:col-span-2">
          <label className="block text-sm text-gray-700 mb-1">描述</label>
          <textarea
            name="description"
            value={form.description || ''}
            onChange={onChange}
            className={`w-full rounded border px-3 py-2 ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
            placeholder="可选，不超过 255 个字符"
            maxLength={255}
            rows={3}
          />
          {errors.description && <p className="text-xs text-red-600 mt-1">{errors.description}</p>}
        </div>
        <div>
          <label className="inline-flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" name="visible" checked={form.visible ?? true} onChange={onChange} />
            是否可见
          </label>
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-1">排序权重</label>
          <input
            name="sortOrder"
            type="number"
            value={form.sortOrder ?? 0}
            onChange={onChange}
            className={`w-full rounded border px-3 py-2 ${errors.sortOrder ? 'border-red-500' : 'border-gray-300'}`}
            placeholder="默认 0，值越小越靠前"
          />
          {errors.sortOrder && <p className="text-xs text-red-600 mt-1">{errors.sortOrder}</p>}
        </div>
        <div className="md:col-span-2 flex justify-end gap-3">
          <button
            type="button"
            onClick={() => {
              setForm(prev => ({ tenantId: prev.tenantId, parentId: null, name: '', description: '', visible: true, sortOrder: 0 }));
              setErrors({});
              setMessage(null);
            }}
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
  );
};

export default BoardForm;
