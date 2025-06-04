// src/components/system-management/ResetPassword.tsx
import React, { useState } from 'react';

const ResetPassword: React.FC = () => {
    const [adminId, setAdminId] = useState('');
    const [isSuccess, setIsSuccess] = useState(false);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        // 处理重置密码的逻辑
        console.log('重置密码，管理员ID:', adminId);
        setIsSuccess(true);

        // 重置表单
        setAdminId('');

        // 3秒后隐藏成功消息
        setTimeout(() => {
            setIsSuccess(false);
        }, 3000);
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-xl font-bold mb-2">重置其他管理员密码</h2>
                <p className="text-sm text-gray-500 mb-4">注：此功能仅限超级管理员使用</p>

                {isSuccess && (
                    <div className="bg-green-100 text-green-800 px-4 py-2 rounded mb-4">
                        密码重置成功！
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <label
                        htmlFor="admin-id"
                        className="block text-sm font-medium text-gray-700 mb-2"
                    >
                        请输入管理员ID
                    </label>
                    <input
                        type="text"
                        id="admin-id"
                        value={adminId}
                        onChange={(e) => setAdminId(e.target.value)}
                        className="w-full border border-gray-300 rounded-md p-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="请输入管理员ID"
                    />
                    <button
                        type="submit"
                        className="mt-4 w-full bg-blue-500 text-white font-medium py-2 rounded-md hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                    >
                        重置密码
                    </button>
                </form>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">
                        1. 输入需要重置密码的管理员ID<br />
                        2. 点击"重置密码"按钮<br />
                        3. 系统将自动重置该管理员的密码并发送到其绑定邮箱<br />
                        4. 此功能仅限超级管理员使用
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ResetPassword;
