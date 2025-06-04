// src/components/system-management/system-management.tsx
import React, { useState } from 'react';
import ReaderPermissions from './ReaderPermissions';
import AdminPermissions from './AdminPermissions';
import AddAdmin from './AddAdmin';
import ResetPassword from './ResetPassword';
import BackupRestore from './BackupRestore';
import SystemLogs from './SystemLogs';

const navItems = [
    '读者权限管理',
    '管理员权限管理',
    '添加新管理员',
    '重置其他管理员密码',
    '系统备份与恢复',
    '系统日志管理'
];

const SystemManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">系统设置</h1>
                <nav className="mt-4">
                    <ul className="flex space-x-4 text-gray-600">
                        {navItems.map((label, idx) => (
                            <li key={label}>
                                <a
                                    href="#"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        setActiveTab(idx);
                                    }}
                                    className={`hover:text-blue-500 ${activeTab === idx ? 'text-blue-500 font-semibold' : ''}`}
                                >
                                    {label}
                                </a>
                            </li>
                        ))}
                    </ul>
                </nav>
            </div>

            {/* 根据活动标签显示不同的组件 */}
            <div className="mt-6">
                {activeTab === 0 && <ReaderPermissions />}
                {activeTab === 1 && <AdminPermissions />}
                {activeTab === 2 && <AddAdmin />}
                {activeTab === 3 && <ResetPassword />}
                {activeTab === 4 && <BackupRestore />}
                {activeTab === 5 && <SystemLogs />}
            </div>
        </main>
    );
};

export default SystemManagement;
