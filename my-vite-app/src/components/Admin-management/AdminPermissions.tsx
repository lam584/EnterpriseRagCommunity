// my-vite-app/src/components/Admin-management/AdminPermissions.tsx
import React, { useState, useEffect, ChangeEvent, FormEvent } from 'react';

/**
 * 管理员权限 dto
 */
interface AdminPermissionDTO {
    id: number;
    name: string;
    description: string;
    allowEditUserProfile: boolean;
    createdAt: string; // ISO 时间字符串
    updatedAt: string;
}

// 初始数据
const sampleData: AdminPermissionDTO[] = [
    {
        id: 1,
        name: '超级管理员',
        description: '拥有系统所有权限',
        allowEditUserProfile: true,
        createdAt: '2023-01-01T09:00:00.000Z',
        updatedAt: '2023-01-01T09:00:00.000Z',
    },
    {
        id: 2,
        name: '内容编辑',
        description: '可以编辑文章和评论',
        allowEditUserProfile: false,
        createdAt: '2023-02-15T14:30:00.000Z',
        updatedAt: '2023-02-15T14:30:00.000Z',
    },
];

const AdminPermissions: React.FC = () => {
    // 数据状态
    const [permissions, setPermissions] = useState<AdminPermissionDTO[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // 弹窗相关
    const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
    const [editingItem, setEditingItem] = useState<Partial<AdminPermissionDTO>>({});

    // 全局消息
    const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

    // mn“获取列表”
    useEffect(() => {
        try {
            // mn延迟
            setTimeout(() => {
                setPermissions(sampleData);
                setLoading(false);
            }, 300);
        } catch (e) {
            console.error(e);
            setError('加载数据失败');
            setLoading(false);
        }
    }, []);

    // 打开新增/编辑弹窗
    const openModal = (item?: AdminPermissionDTO) => {
        if (item) {
            setEditingItem({ ...item });
        } else {
            setEditingItem({
                name: '',
                description: '',
                allowEditUserProfile: false,
            });
        }
        setIsModalOpen(true);
    };

    // 关闭弹窗
    const closeModal = () => {
        setIsModalOpen(false);
        setEditingItem({});
    };

    // 表单字段改变
    const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        const { name, value, type } = e.target;
        const checked = (e.target as HTMLInputElement).checked;
        setEditingItem((prev) => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
    };

    // 保存（新增/更新）
    const handleSave = (e: FormEvent) => {
        e.preventDefault();
        const now = new Date().toISOString();
        try {
            if (editingItem.id !== undefined) {
                // 更新
                setPermissions((prev) =>
                    prev.map((p) =>
                        p.id === editingItem.id
                            ? {
                                ...p,
                                name: editingItem.name || p.name,
                                description: editingItem.description || p.description,
                                allowEditUserProfile: editingItem.allowEditUserProfile || false,
                                updatedAt: now,
                            }
                            : p
                    )
                );
                setMessage({ type: 'success', text: '更新成功' });
            } else {
                // 新增
                setPermissions((prev) => {
                    const newId = prev.length > 0 ? Math.max(...prev.map((p) => p.id)) + 1 : 1;
                    const newItem: AdminPermissionDTO = {
                        id: newId,
                        name: editingItem.name || '',
                        description: editingItem.description || '',
                        allowEditUserProfile: editingItem.allowEditUserProfile || false,
                        createdAt: now,
                        updatedAt: now,
                    };
                    return [...prev, newItem];
                });
                setMessage({ type: 'success', text: '创建成功' });
            }
            closeModal();
        } catch (e) {
            console.error(e);
            setMessage({ type: 'error', text: '保存失败，请检查输入并重试' });
        }
    };

    // 删除
    const handleDelete = (id: number) => {
        if (!window.confirm('确定要删除此权限吗？')) return;
        try {
            setPermissions((prev) => prev.filter((p) => p.id !== id));
            setMessage({ type: 'success', text: '删除成功' });
        } catch (e) {
            console.error(e);
            setMessage({ type: 'error', text: '删除失败，请稍后重试' });
        }
    };

    // 格式化时间
    const fmt = (iso: string) => new Date(iso).toLocaleString('zh-CN');

    return (
        <div className="p-6 bg-gray-50 min-h-screen">
            <h1 className="text-2xl font-bold mb-6">管理员权限管理</h1>

            {/* 消息提示 */}
            {message && (
                <div
                    className={`mb-4 px-4 py-2 rounded ${
                        message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}
                >
                    {message.text}
                </div>
            )}

            {/* 操作按钮 */}
            <div className="flex justify-end mb-4">
                <button
                    onClick={() => openModal()}
                    className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 focus:outline-none"
                >
                    新增权限
                </button>
            </div>

            {/* 列表表格 */}
            <div className="overflow-auto bg-white shadow rounded-lg">
                <table className="min-w-full text-sm text-left">
                    <thead className="bg-gray-100">
                    <tr>
                        <th className="px-4 py-3">名称</th>
                        <th className="px-4 py-3">描述</th>
                        <th className="px-4 py-3">允许编辑用户资料</th>
                        <th className="px-4 py-3">创建时间</th>
                        <th className="px-4 py-3">更新时间</th>
                        <th className="px-4 py-3">操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        <tr>
                            <td colSpan={6} className="text-center py-8 text-gray-500">
                                加载中…
                            </td>
                        </tr>
                    ) : error ? (
                        <tr>
                            <td colSpan={6} className="text-center py-8 text-red-500">
                                {error}
                            </td>
                        </tr>
                    ) : permissions.length === 0 ? (
                        <tr>
                            <td colSpan={6} className="text-center py-8 text-gray-500">
                                暂无数据
                            </td>
                        </tr>
                    ) : (
                        permissions.map((item) => (
                            <tr key={item.id} className="hover:bg-gray-50">
                                <td className="px-4 py-2">{item.name}</td>
                                <td className="px-4 py-2">{item.description}</td>
                                <td className="px-4 py-2">
                                    {item.allowEditUserProfile ? (
                                        <span className="text-green-600">是</span>
                                    ) : (
                                        <span className="text-red-600">否</span>
                                    )}
                                </td>
                                <td className="px-4 py-2">{fmt(item.createdAt)}</td>
                                <td className="px-4 py-2">{fmt(item.updatedAt)}</td>
                                <td className="px-4 py-2 space-x-2">
                                    <button onClick={() => openModal(item)} className="text-blue-600 hover:underline">
                                        编辑
                                    </button>
                                    <button onClick={() => handleDelete(item.id)} className="text-red-600 hover:underline">
                                        删除
                                    </button>
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>

            {/* 新增/编辑弹窗 */}
            {isModalOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-[480px] p-6 shadow-lg">
                        <h2 className="text-xl font-semibold mb-4">
                            {editingItem.id !== undefined ? '编辑权限' : '新增权限'}
                        </h2>
                        <form onSubmit={handleSave} className="space-y-4">
                            {/* 名称 */}
                            <div>
                                <label className="block text-gray-700 mb-1">名称</label>
                                <input
                                    name="name"
                                    value={editingItem.name || ''}
                                    onChange={handleChange}
                                    required
                                    className="w-full px-3 py-2 border border-gray-300 rounded focus:ring-blue-500 focus:outline-none"
                                />
                            </div>
                            {/* 描述 */}
                            <div>
                                <label className="block text-gray-700 mb-1">描述</label>
                                <textarea
                                    name="description"
                                    value={editingItem.description || ''}
                                    onChange={handleChange}
                                    rows={3}
                                    className="w-full px-3 py-2 border border-gray-300 rounded focus:ring-blue-500 focus:outline-none"
                                />
                            </div>
                            {/* 允许编辑用户资料 */}
                            <div className="flex items-center">
                                <input
                                    id="allowEditUserProfile"
                                    name="allowEditUserProfile"
                                    type="checkbox"
                                    checked={!!editingItem.allowEditUserProfile}
                                    onChange={handleChange}
                                    className="h-5 w-5 text-blue-600 focus:ring-blue-500"
                                />
                                <label htmlFor="allowEditUserProfile" className="ml-2 text-gray-700">
                                    允许编辑用户资料
                                </label>
                            </div>
                            {/* 操作按钮 */}
                            <div className="flex justify-end space-x-4 mt-6">
                                <button
                                    type="button"
                                    onClick={closeModal}
                                    className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300"
                                >
                                    取消
                                </button>
                                <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
                                    保存
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminPermissions;