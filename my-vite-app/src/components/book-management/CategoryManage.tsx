// src/components/book-management/CategoryManage.tsx
import React, { useState, useEffect } from 'react';
import { fetchCategories, createCategory, updateCategory, deleteCategory, CategoryDTO } from '../../services/categoryService';

const CategoryManage: React.FC = () => {
    const [categories, setCategories] = useState<CategoryDTO[]>([]);
    const [formData, setFormData] = useState<CategoryDTO>({ name: '', description: '' });
    const [message, setMessage] = useState('');
    const [isEditing, setIsEditing] = useState(false);
    const [loading, setLoading] = useState(false);

    const loadCategories = () => {
        setLoading(true);
        fetchCategories()
            .then(data => {
                setCategories(data);
                setLoading(false);
            })
            .catch(() => {
                setMessage('加载分类列表失败');
                setLoading(false);
            });
    };

    useEffect(() => {
        loadCategories();
    }, []);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            if (isEditing && formData.id) {
                await updateCategory(formData.id, formData);
                setMessage('分类更新成功');
            } else {
                await createCategory(formData);
                setMessage('分类创建成功');
            }

            // 重置表单和状态
            setFormData({ name: '', description: '' });
            setIsEditing(false);
            loadCategories();
        } catch (error) {
            setMessage(isEditing ? '更新分类失败' : '创建分类失败');
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = (category: CategoryDTO) => {
        setFormData(category);
        setIsEditing(true);
        setMessage('');
    };

    const handleCancel = () => {
        setFormData({ name: '', description: '' });
        setIsEditing(false);
        setMessage('');
    };

    const handleDelete = async (id?: number) => {
        if (!id) return;

        if (!window.confirm('确定要删除此分类吗？')) return;

        setLoading(true);
        try {
            await deleteCategory(id);
            setMessage('分类删除成功');
            loadCategories();
        } catch {
            setMessage('删除分类失败，可能该分类下有关联图书');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-lg font-bold mb-4">图书分类管理</h2>

                {message && <div className="mb-4 p-2 bg-blue-100 text-blue-800 rounded">{message}</div>}

                <form onSubmit={handleSubmit} className="mb-6 p-4 border rounded">
                    <h3 className="font-medium mb-2">{isEditing ? '编辑分类' : '添加分类'}</h3>
                    <div className="mb-3">
                        <label className="block text-sm font-medium mb-1">分类名称</label>
                        <input
                            name="name"
                            placeholder="请输入分类名称"
                            value={formData.name}
                            onChange={handleChange}
                            className="border p-2 w-full rounded"
                            required
                        />
                    </div>
                    <div className="mb-3">
                        <label className="block text-sm font-medium mb-1">分类描述</label>
                        <textarea
                            name="description"
                            placeholder="请输入分类描述"
                            value={formData.description}
                            onChange={handleChange}
                            className="border p-2 w-full rounded"
                            rows={3}
                        />
                    </div>
                    <div className="flex space-x-2">
                        <button
                            type="submit"
                            className="bg-green-500 text-white px-4 py-2 rounded hover:bg-green-600"
                            disabled={loading}
                        >
                            {loading ? '处理中...' : isEditing ? '保存修改' : '创建分类'}
                        </button>
                        {isEditing && (
                            <button
                                type="button"
                                onClick={handleCancel}
                                className="bg-gray-300 text-gray-800 px-4 py-2 rounded hover:bg-gray-400"
                                disabled={loading}
                            >
                                取消
                            </button>
                        )}
                    </div>
                </form>

                <div className="overflow-x-auto">
                    <table className="w-full border-collapse border border-gray-300 text-left">
                        <thead>
                        <tr className="bg-gray-100">
                            <th className="border border-gray-300 px-4 py-2">ID</th>
                            <th className="border border-gray-300 px-4 py-2">分类名称</th>
                            <th className="border border-gray-300 px-4 py-2">分类描述</th>
                            <th className="border border-gray-300 px-4 py-2">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        {categories.length > 0 ? (
                            categories.map(cat => (
                                <tr key={cat.id} className="hover:bg-gray-50">
                                    <td className="border p-2">{cat.id}</td>
                                    <td className="border p-2">{cat.name}</td>
                                    <td className="border p-2">{cat.description || '-'}</td>
                                    <td className="border p-2 space-x-2">
                                        <button
                                            onClick={() => handleEdit(cat)}
                                            className="text-blue-600 hover:underline"
                                            disabled={loading}
                                        >
                                            编辑
                                        </button>
                                        <button
                                            onClick={() => handleDelete(cat.id)}
                                            className="text-red-600 hover:underline"
                                            disabled={loading}
                                        >
                                            删除
                                        </button>
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan={4} className="border p-4 text-center text-gray-500">
                                    {loading ? '加载中...' : '暂无分类数据'}
                                </td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <ul className="list-disc pl-5 mt-2 text-gray-600 space-y-2">
                        <li>查看所有图书分类及其描述</li>
                        <li>添加新分类时，分类名称为必填项</li>
                        <li>点击"编辑"可修改已有分类信息</li>
                        <li>删除分类前，请确保该分类下没有关联图书</li>
                    </ul>
                </div>
            </div>
        </div>
    );
};

export default CategoryManage;