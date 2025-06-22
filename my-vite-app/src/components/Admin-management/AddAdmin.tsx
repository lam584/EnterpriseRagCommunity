// src/components/editor-management/AddAdmin.tsx
import React, { useState, useEffect } from 'react';
import { getAdmins, addAdmin, Admin, AdminFormData } from '../../services/mockAdminService';

const AddAdmin: React.FC = () => {
    const [formData, setFormData] = useState<AdminFormData>({
        username: '',
        password: '',
        confirmPassword: '',
        email: '',
        name: '',
        phone: '',
        department: '',
        role: 'editor'
    });

    const [message, setMessage] = useState<{ type: 'error' | 'success' | 'info' | ''; text: string }>({
        type: '',
        text: ''
    });

    const [admins, setAdmins] = useState<Admin[]>([]);

    // 初始化时加载mn数据
    useEffect(() => {
        fetchAdmins();
    }, []);

    const fetchAdmins = async () => {
        const list = await getAdmins();
        setAdmins(list);
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const resetForm = () => {
        setFormData({
            username: '',
            password: '',
            confirmPassword: '',
            email: '',
            name: '',
            phone: '',
            department: '',
            role: 'editor'
        });
        setMessage({ type: '', text: '' });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // 简单表单验证
        if (!formData.username || !formData.password || !formData.email) {
            setMessage({ type: 'error', text: '用户名、密码和邮箱为必填项！' });
            return;
        }
        if (formData.password !== formData.confirmPassword) {
            setMessage({ type: 'error', text: '两次输入的密码不一致！' });
            return;
        }

        setMessage({ type: 'info', text: '正在添加管理员...' });

        try {
            const newAdmin = await addAdmin(formData);
            setAdmins(prev => [...prev, newAdmin]);
            setMessage({ type: 'success', text: '管理员添加成功！' });
            resetForm();
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
        } catch (err) {
            setMessage({ type: 'error', text: '添加失败，请重试。' });
        }
    };

    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4">添加管理员</h2>

            {message.text && (
                <div
                    className={`p-3 rounded mb-4 ${
                        message.type === 'error' ? 'bg-red-100 text-red-700'
                            : message.type === 'success' ? 'bg-green-100 text-green-700'
                                : 'bg-blue-100 text-blue-700'
                    }`}
                >
                    {message.text}
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                    {/* 用户名 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            用户名 *
                        </label>
                        <input
                            type="text"
                            name="username"
                            value={formData.username}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            required
                        />
                    </div>

                    {/* 邮箱 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            邮箱 *
                        </label>
                        <input
                            type="email"
                            name="email"
                            value={formData.email}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            required
                        />
                    </div>

                    {/* 密码 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            密码 *
                        </label>
                        <input
                            type="password"
                            name="password"
                            value={formData.password}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            required
                        />
                    </div>

                    {/* 确认密码 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            确认密码 *
                        </label>
                        <input
                            type="password"
                            name="confirmPassword"
                            value={formData.confirmPassword}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            required
                        />
                    </div>

                    {/* 其他可选项 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            姓名
                        </label>
                        <input
                            type="text"
                            name="name"
                            value={formData.name}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            手机号
                        </label>
                        <input
                            type="tel"
                            name="phone"
                            value={formData.phone}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            所属部门
                        </label>
                        <input
                            type="text"
                            name="department"
                            value={formData.department}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />
                    </div>

                    {/* 角色 */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            角色
                        </label>
                        <select
                            name="role"
                            value={formData.role}
                            onChange={handleChange}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        >
                            <option value="admin">普通管理员</option>
                            <option value="reviewer">评论审核员</option>
                            <option value="editor">编辑</option>
                            <option value="chief_editor">主编</option>
                            <option value="senior_admin">高级管理员</option>
                            <option value="super_admin">超级管理员</option>
                        </select>
                    </div>
                </div>

                <div className="flex justify-end mt-6">
                    <button
                        type="button"
                        onClick={resetForm}
                        className="px-4 py-2 mr-2 rounded-md bg-gray-200 hover:bg-gray-300 focus:outline-none"
                    >
                        重置
                    </button>
                    <button
                        type="submit"
                        className="px-4 py-2 rounded-md bg-purple-600 text-white hover:bg-purple-700 focus:outline-none"
                    >
                        添加管理员
                    </button>
                </div>
            </form>

            {/* 列表展示 */}
            {admins.length > 0 && (
                <div className="mt-8">
                    <h3 className="text-lg font-semibold mb-2">管理员列表</h3>
                    <table className="min-w-full bg-white border">
                        <thead className="bg-gray-100">
                        <tr>
                            <th className="px-4 py-2 border">用户名</th>
                            <th className="px-4 py-2 border">邮箱</th>
                            <th className="px-4 py-2 border">角色</th>
                            <th className="px-4 py-2 border">创建时间</th>
                        </tr>
                        </thead>
                        <tbody>
                        {admins.map(admin => (
                            <tr key={admin.id} className="odd:bg-gray-50">
                                <td className="px-4 py-2 border">{admin.username}</td>
                                <td className="px-4 py-2 border">{admin.email}</td>
                                <td className="px-4 py-2 border">{admin.role}</td>
                                <td className="px-4 py-2 border">
                                    {new Date(admin.createdAt).toLocaleString()}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AddAdmin;