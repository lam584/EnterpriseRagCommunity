// src/components/system-management/NewAddAdmin.tsx
import React, { useState } from 'react';

const NewAddAdmin: React.FC = () => {
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        confirmPassword: '',
        phone: '',
        email: '',
        gender: '男',
        role: 'Administrator'
    });
    const [isSuccess, setIsSuccess] = useState(false);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [id]: value
        }));
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        // 表单验证
        if (formData.password !== formData.confirmPassword) {
            alert('两次输入的密码不一致');
            return;
        }

        // 处理提交逻辑
        console.log('提交新管理员数据:', formData);

        // 显示成功消息
        setIsSuccess(true);

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

        // 3秒后隐藏成功消息
        setTimeout(() => {
            setIsSuccess(false);
        }, 3000);
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-xl font-bold mb-6">添加新管理员</h2>

                {isSuccess && (
                    <div className="bg-green-100 text-green-800 px-4 py-2 rounded mb-4">
                        管理员添加成功！
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div className="mb-4">
                        <label htmlFor="username" className="block text-gray-700 mb-2">用户名:</label>
                        <input
                            type="text"
                            id="username"
                            value={formData.username}
                            onChange={handleChange}
                            placeholder="请输入用户名"
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div className="mb-4">
                        <label htmlFor="password" className="block text-gray-700 mb-2">密码:</label>
                        <input
                            type="password"
                            id="password"
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="请输入密码"
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div className="mb-4">
                        <label htmlFor="confirmPassword" className="block text-gray-700 mb-2">确认密码:</label>
                        <input
                            type="password"
                            id="confirmPassword"
                            value={formData.confirmPassword}
                            onChange={handleChange}
                            placeholder="请确认密码"
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div className="mb-4">
                        <label htmlFor="phone" className="block text-gray-700 mb-2">手机号:</label>
                        <input
                            type="text"
                            id="phone"
                            value={formData.phone}
                            onChange={handleChange}
                            placeholder="请输入手机号"
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div className="mb-4">
                        <label htmlFor="email" className="block text-gray-700 mb-2">邮箱:</label>
                        <input
                            type="email"
                            id="email"
                            value={formData.email}
                            onChange={handleChange}
                            placeholder="请输入邮箱"
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div className="mb-4">
                        <label htmlFor="gender" className="block text-gray-700 mb-2">性别:</label>
                        <select
                            id="gender"
                            value={formData.gender}
                            onChange={handleChange}
                            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="男">男</option>
                            <option value="女">女</option>
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
                            <option value="SuperAdministrator">SuperAdministrator</option>
                            <option value="Administrator">Administrator</option>
                            <option value="User">User</option>
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

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">
                        1. 填写管理员基本信息<br />
                        2. 选择适当的角色权限<br />
                        3. 点击"提交"按钮创建新账号<br />
                        4. SuperAdministrator具有所有权限<br />
                        5. Administrator具有部分管理权限<br />
                        6. User仅具有基础操作权限
                    </p>
                </div>
            </div>
        </div>
    );
};

export default NewAddAdmin;
