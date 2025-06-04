// src/components/book-management/ShelfManage.tsx
import React, { useState, useEffect } from 'react';
import { fetchShelves, createShelf, updateShelf, deleteShelf, ShelfDTO } from '../../services/shelfService';

const ShelfManage: React.FC = () => {
    const [shelves, setShelves] = useState<ShelfDTO[]>([]);
    const [form, setForm] = useState<Partial<ShelfDTO>>({ shelfCode: '', locationDescription: '', capacity: 0 });
    const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const loadShelves = () => {
        setLoading(true);
        fetchShelves()
            .then(data => {
                setShelves(data);
                setLoading(false);
            })
            .catch(() => {
                setMessage({type: 'error', text: '加载书架列表失败'});
                setLoading(false);
            });
    };

    useEffect(() => {
        loadShelves();
    }, []);

    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!form.shelfCode?.trim()) {
            newErrors.shelfCode = '书架编码不能为空';
        }

        if (!form.locationDescription?.trim()) {
            newErrors.locationDescription = '位置描述不能为空';
        }

        if (!form.capacity || form.capacity <= 0) {
            newErrors.capacity = '容量必须大于0';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: name === 'capacity' ? Number(value) : value }));

        // 清除字段错误
        if (errors[name]) {
            setErrors(prev => {
                const newErrors = {...prev};
                delete newErrors[name];
                return newErrors;
            });
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateForm()) return;

        setLoading(true);
        setMessage(null);

        try {
            if (form.id) {
                await updateShelf(form.id, form as ShelfDTO);
                setMessage({type: 'success', text: '书架信息已更新'});
            } else {
                await createShelf(form as ShelfDTO);
                setMessage({type: 'success', text: '新书架已创建'});
            }
            setForm({ shelfCode: '', locationDescription: '', capacity: 0 });
            loadShelves();
        } catch (error) {
            setMessage({type: 'error', text: form.id ? '更新书架失败' : '创建书架失败'});
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = (shelf: ShelfDTO) => {
        setForm(shelf);
        setMessage(null);
        setErrors({});
    };

    const handleCancel = () => {
        setForm({ shelfCode: '', locationDescription: '', capacity: 0 });
        setMessage(null);
        setErrors({});
    };

    const handleDelete = async (id?: number) => {
        if (!id) return;

        if (!window.confirm('确定要删除此书架吗？如果书架上有图书，将无法删除。')) return;

        setLoading(true);
        try {
            await deleteShelf(id);
            setMessage({type: 'success', text: '书架已删除'});
            loadShelves();
        } catch {
            setMessage({type: 'error', text: '删除书架失败，可能该书架上存在图书'});
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-lg font-bold mb-4">书架管理</h2>

                {message && (
                    <div className={`p-3 rounded mb-4 ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                        {message.text}
                    </div>
                )}

                <form onSubmit={handleSave} className="mb-6 p-4 border rounded">
                    <h3 className="font-medium mb-3">{form.id ? '编辑书架' : '添加书架'}</h3>

                    <div className="mb-3">
                        <label className="block text-sm font-medium mb-1">书架编码</label>
                        <input
                            name="shelfCode"
                            value={form.shelfCode || ''}
                            onChange={handleChange}
                            placeholder="例如: A-101"
                            className={`border p-2 w-full rounded ${errors.shelfCode ? 'border-red-500' : ''}`}
                        />
                        {errors.shelfCode && <p className="text-red-500 text-sm mt-1">{errors.shelfCode}</p>}
                    </div>

                    <div className="mb-3">
                        <label className="block text-sm font-medium mb-1">位置描述</label>
                        <input
                            name="locationDescription"
                            value={form.locationDescription || ''}
                            onChange={handleChange}
                            placeholder="例如: 一楼东南角"
                            className={`border p-2 w-full rounded ${errors.locationDescription ? 'border-red-500' : ''}`}
                        />
                        {errors.locationDescription && <p className="text-red-500 text-sm mt-1">{errors.locationDescription}</p>}
                    </div>

                    <div className="mb-3">
                        <label className="block text-sm font-medium mb-1">容量</label>
                        <input
                            name="capacity"
                            type="number"
                            value={form.capacity || ''}
                            onChange={handleChange}
                            placeholder="可容纳图书数量"
                            className={`border p-2 w-full rounded ${errors.capacity ? 'border-red-500' : ''}`}
                            min="1"
                        />
                        {errors.capacity && <p className="text-red-500 text-sm mt-1">{errors.capacity}</p>}
                    </div>

                    <div className="flex space-x-2">
                        <button
                            type="submit"
                            className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
                            disabled={loading}
                        >
                            {loading ? '处理中...' : form.id ? '保存修改' : '创建书架'}
                        </button>

                        {form.id && (
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
                            <th className="border border-gray-300 px-4 py-2">书架编码</th>
                            <th className="border border-gray-300 px-4 py-2">位置描述</th>
                            <th className="border border-gray-300 px-4 py-2">容量</th>
                            <th className="border border-gray-300 px-4 py-2">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        {shelves.length > 0 ? (
                            shelves.map(s => (
                                <tr key={s.id} className="hover:bg-gray-50">
                                    <td className="border p-2">{s.id}</td>
                                    <td className="border p-2">{s.shelfCode}</td>
                                    <td className="border p-2">{s.locationDescription}</td>
                                    <td className="border p-2">{s.capacity}</td>
                                    <td className="border p-2 space-x-2">
                                        <button
                                            onClick={() => handleEdit(s)}
                                            className="text-blue-600 hover:underline"
                                            disabled={loading}
                                        >
                                            编辑
                                        </button>
                                        <button
                                            onClick={() => handleDelete(s.id)}
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
                                <td colSpan={5} className="border p-4 text-center text-gray-500">
                                    {loading ? '加载中...' : '暂无书架数据'}
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
                    <p className="text-gray-700">书架管理功能使用说明：</p>
                    <ul className="list-disc pl-5 mt-2 text-gray-600 space-y-2">
                        <li>书架编码必须是唯一的标识符</li>
                        <li>位置描述应尽量详细，便于读者查找</li>
                        <li>容量表示该书架最多可存放的图书数量</li>
                        <li>已有图书的书架不能删除，必须先移走图书</li>
                    </ul>
                </div>
            </div>
        </div>
    );
};

export default ShelfManage;