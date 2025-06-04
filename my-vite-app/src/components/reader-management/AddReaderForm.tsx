// src/components/reader-management/AddReaderForm.tsx
import React, { useState } from 'react';

const AddReaderForm: React.FC = () => {
    // 状态管理
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        confirmPassword: '',
        phone: '',
        email: '',
        gender: '男',
        role: '普通读者'
    });

    // 成功和错误消息状态
    const [successMessage, setSuccessMessage] = useState('获取角色成功!');
    const [errorMessage, setErrorMessage] = useState('');
    const [showSuccess, setShowSuccess] = useState(true);
    const [showError, setShowError] = useState(false);

    // 处理输入变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setFormData({
            ...formData,
            [id]: value
        });
    };

    // 处理表单提交
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        // 这里可以添加表单验证逻辑
        if (formData.password !== formData.confirmPassword) {
            setErrorMessage('两次输入的密码不一致');
            setShowError(true);
            setShowSuccess(false);
            return;
        }

        // 模拟API请求提交数据
        console.log('提交的读者数据:', formData);

        // 模拟成功响应
        setSuccessMessage('添加读者成功!');
        setShowSuccess(true);
        setShowError(false);

        // 清空表单
        setFormData({
            username: '',
            password: '',
            confirmPassword: '',
            phone: '',
            email: '',
            gender: '男',
            role: '普通读者'
        });
    };

    return (
        <div className="max-w-2xl mx-auto mt-10 bg-white p-6 rounded shadow">
            <h1 className="text-xl font-bold mb-4">添加新读者</h1>

            {/* 成功消息 */}
            {showSuccess && (
                <div className="bg-green-100 text-green-700 px-4 py-2 rounded mb-4">
                    {successMessage}
                </div>
            )}

            {/* 错误消息 */}
            {showError && (
                <div className="bg-red-100 text-red-700 px-4 py-2 rounded mb-6">
                    {errorMessage}
                </div>
            )}

            {/* 表单 */}
            <form onSubmit={handleSubmit}>
                <div className="mb-4">
                    <label htmlFor="username" className="block text-gray-700 mb-1">用户名:</label>
                    <input
                        type="text"
                        id="username"
                        value={formData.username}
                        onChange={handleInputChange}
                        placeholder="请输入用户名"
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="mb-4">
                    <label htmlFor="password" className="block text-gray-700 mb-1">密码:</label>
                    <input
                        type="password"
                        id="password"
                        value={formData.password}
                        onChange={handleInputChange}
                        placeholder="请输入密码"
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="mb-4">
                    <label htmlFor="confirmPassword" className="block text-gray-700 mb-1">确认密码:</label>
                    <input
                        type="password"
                        id="confirmPassword"
                        value={formData.confirmPassword}
                        onChange={handleInputChange}
                        placeholder="请确认密码"
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="mb-4">
                    <label htmlFor="phone" className="block text-gray-700 mb-1">手机号:</label>
                    <input
                        type="text"
                        id="phone"
                        value={formData.phone}
                        onChange={handleInputChange}
                        placeholder="请输入手机号"
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="mb-4">
                    <label htmlFor="email" className="block text-gray-700 mb-1">邮箱:</label>
                    <input
                        type="email"
                        id="email"
                        value={formData.email}
                        onChange={handleInputChange}
                        placeholder="请输入邮箱"
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="mb-4">
                    <label htmlFor="gender" className="block text-gray-700 mb-1">性别:</label>
                    <select
                        id="gender"
                        value={formData.gender}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                        <option value="男">男</option>
                        <option value="女">女</option>
                    </select>
                </div>

                <div className="mb-6">
                    <label htmlFor="role" className="block text-gray-700 mb-1">角色:</label>
                    <select
                        id="role"
                        value={formData.role}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                        <option value="普通读者">普通读者</option>
                        <option value="管理员">管理员</option>
                    </select>
                </div>

                <button
                    type="submit"
                    className="w-full bg-green-500 text-white py-2 rounded hover:bg-green-600 focus:outline-none focus:ring-2 focus:ring-green-500"
                >
                    提交
                </button>
            </form>
        </div>
    );
};

export default AddReaderForm;
