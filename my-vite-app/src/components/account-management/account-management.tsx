// src/components/account-management/AccountManagement.tsx
import React, { useState } from 'react';
import EditProfile from './EditProfile';
import ChangePassword from './ChangePassword';
import Logout from './Logout';

const AccountManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState<string | null>(null);
    const [userData, setUserData] = useState({
        id: '1',
        username: 'admin',
        email: '1234567890@qq.com',
        phone: '12345678901',
        gender: '男'
    });

    // 处理表单提交
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        // 可以添加表单验证和提交逻辑
    };

    // 处理按钮点击
    const handleButtonClick = (action: string) => {
        setActiveTab(action);
    };

    return (
        <div className="flex-1 p-6 bg-gray-100 min-h-screen w-full">
            {/* 页面标题 */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">账户信息管理</h1>
            </div>

            {/* 主内容区 */}
            {activeTab ? (
                <div className="mt-6">
                    {activeTab === 'edit' && <EditProfile userData={userData} />}
                    {activeTab === 'change-password' && <ChangePassword />}
                    {activeTab === 'logout' && <Logout />}
                    <div className="mt-4">
                        <button
                            onClick={() => setActiveTab(null)}
                            className="text-blue-500 hover:text-blue-700"
                        >
                            返回账户信息
                        </button>
                    </div>
                </div>
            ) : (
                <div className="max-w-lg mx-auto bg-white p-6 rounded-lg shadow-md">
                    <h1 className="text-2xl font-bold mb-2">账户信息管理</h1>
                    <p className="text-gray-600 mb-6">在一个地方管理您的账户和安全设置。</p>

                    <form onSubmit={handleSubmit}>
                        <div className="mb-4">
                            <label htmlFor="id" className="block text-gray-700 font-medium mb-1">id:</label>
                            <input
                                type="text"
                                id="id"
                                value={userData.id}
                                className="w-full border border-gray-300 rounded-md p-2"
                                readOnly
                            />
                        </div>
                        <div className="mb-4">
                            <label htmlFor="username" className="block text-gray-700 font-medium mb-1">用户名：</label>
                            <input
                                type="text"
                                id="username"
                                value={userData.username}
                                className="w-full border border-gray-300 rounded-md p-2"
                                readOnly
                            />
                        </div>
                        <div className="mb-4">
                            <label htmlFor="email" className="block text-gray-700 font-medium mb-1">电子邮件：</label>
                            <input
                                type="email"
                                id="email"
                                value={userData.email}
                                className="w-full border border-gray-300 rounded-md p-2"
                                readOnly
                            />
                        </div>
                        <div className="mb-4">
                            <label htmlFor="phone" className="block text-gray-700 font-medium mb-1">手机号：</label>
                            <input
                                type="text"
                                id="phone"
                                value={userData.phone}
                                className="w-full border border-gray-300 rounded-md p-2"
                                readOnly
                            />
                        </div>
                        <div className="mb-4">
                            <label htmlFor="gender" className="block text-gray-700 font-medium mb-1">性别：</label>
                            <select
                                id="gender"
                                value={userData.gender}
                                onChange={(e) => setUserData({...userData, gender: e.target.value})}
                                className="w-full border border-gray-300 rounded-md p-2"
                            >
                                <option>男</option>
                                <option>女</option>
                            </select>
                        </div>
                        <div className="flex space-x-4">
                            <button
                                type="button"
                                className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
                                onClick={() => handleButtonClick('edit')}
                            >
                                编辑个人信息
                            </button>
                            <button
                                type="button"
                                className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
                                onClick={() => handleButtonClick('change-password')}
                            >
                                更改密码
                            </button>
                            <button
                                type="button"
                                className="bg-red-500 text-white px-4 py-2 rounded-md hover:bg-red-600"
                                onClick={() => handleButtonClick('logout')}
                            >
                                退出登录
                            </button>
                        </div>
                    </form>

                    <p className="text-gray-500 text-sm mt-6">
                        我们建议您的密码应至少包括大写字母、小写字母、数字、符号，以提高您对您的账户安全性的控制。
                    </p>
                </div>
            )}
        </div>
    );
};

export default AccountManagement;
