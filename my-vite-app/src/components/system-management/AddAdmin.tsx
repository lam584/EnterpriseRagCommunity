// src/components/system-management/AddAdmin.tsx
import React, { useState } from 'react';

const AddAdmin: React.FC = () => {
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        confirmPassword: '',
        phone: '',
        email: '',
        gender: '男',
        role: 'Administrator'
    });

    const [errors, setErrors] = useState<{
        username?: string;
        password?: string;
        confirmPassword?: string;
        phone?: string;
        email?: string;
    }>({});

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [id]: value
        }));
    };

    const validate = () => {
        const newErrors: typeof errors = {};

        if (!formData.username.trim()) {
            newErrors.username = '用户名不能为空';
        }

        if (formData.password.length < 6) {
            newErrors.password = '密码长度至少为6个字符';
        }

        if (formData.password !== formData.confirmPassword) {
            newErrors.confirmPassword = '两次输入的密码不一致';
        }

        const phoneRegex = /^1[3-9]\d{9}$/;
        if (!phoneRegex.test(formData.phone)) {
            newErrors.phone = '请输入有效的手机号码';
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(formData.email)) {
            newErrors.email = '请输入有效的邮箱地址';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        if (validate()) {
            // 调用API添加管理员
            console.log('提交的数据：', formData);
            // 这里可以添加API调用代码
            alert('管理员添加成功！');

            // 重置表单
            setFormData({
                username: '',
                password: '',
                confirmPassword: '',
                phone: '',
                email: '',
                gender: '男',
                role: 'Administrator'
            });
        }
    };

    return (
        <div className="flex justify-center">
            <div className="bg-white p-8 rounded-lg shadow-md w-full max-w-lg">
                <h1 className="text-xl font-semibold mb-6">添加新管理员</h1>
                <form onSubmit={handleSubmit}>
                    <div className="mb-4">
                        <label htmlFor="username" className="block text-gray-700 mb-2">用户名:</label>
                        <input
                            type="text"
                            id="username"
                            value={formData.username}
                            onChange={handleChange}
                            placeholder="请输入用户名"
                            className={`w-full border ${errors.username ? 'border-red-500' : 'border-gray-300'} rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                        />
                        {errors.username && <p className="text-red-500 text-sm mt-1">{errors.username}</p>}
                    </div>

                    <div className="mb-4">
                        <label htmlFor="password" className="block text-gray-700 mb-2">密码:</label>
                        <input
                            type="password"
                            id="password"
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="请输入密码"
                            className={`w-full border ${errors.password ? 'border-red-500' : 'border-gray-300'} rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                        />
                        {errors.password && <p className="text-red-500 text-sm mt-1">{errors.password}</p>}
                    </div>

                    <div className="mb-4">
                        <label htmlFor="confirmPassword" className="block text-gray-700 mb-2">确认密码:</label>
                        <input
                            type="password"
                            id="confirmPassword"
                            value={formData.confirmPassword}
                            onChange={handleChange}
                            placeholder="请确认密码"
                            className={`w-full border ${errors.confirmPassword ? 'border-red-500' : 'border-gray-300'} rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                        />
                        {errors.confirmPassword && <p className="text-red-500 text-sm mt-1">{errors.confirmPassword}</p>}
                    </div>

                    <div className="mb-4">
                        <label htmlFor="phone" className="block text-gray-700 mb-2">手机号:</label>
                        <input
                            type="text"
                            id="phone"
                            value={formData.phone}
                            onChange={handleChange}
                            placeholder="请输入手机号"
                            className={`w-full border ${errors.phone ? 'border-red-500' : 'border-gray-300'} rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                        />
                        {errors.phone && <p className="text-red-500 text-sm mt-1">{errors.phone}</p>}
                    </div>

                    <div className="mb-4">
                        <label htmlFor="email" className="block text-gray-700 mb-2">邮箱:</label>
                        <input
                            type="email"
                            id="email"
                            value={formData.email}
                            onChange={handleChange}
                            placeholder="请输入邮箱"
                            className={`w-full border ${errors.email ? 'border-red-500' : 'border-gray-300'} rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500`}
                        />
                        {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email}</p>}
                    </div>

                    <div className="mb-4">
                        <label htmlFor="gender" className="block text-gray-700 mb-2">性别:</label>
                        <select
                            id="gender"
                            value={formData.gender}
                            onChange={handleChange}
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option>男</option>
                            <option>女</option>
                        </select>
                    </div>

                    <div className="mb-6">
                        <label htmlFor="role" className="block text-gray-700 mb-2">角色:</label>
                        <select
                            id="role"
                            value={formData.role}
                            onChange={handleChange}
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option>SuperAdministrator</option>
                            <option>Administrator</option>
                            <option>User</option>
                        </select>
                    </div>

                    <button
                        type="submit"
                        className="w-full bg-green-500 text-white font-semibold py-2 rounded-lg hover:bg-green-600 focus:outline-none focus:ring-2 focus:ring-green-400"
                    >
                        提交
                    </button>
                </form>
            </div>
        </div>
    );
};

export default AddAdmin;
