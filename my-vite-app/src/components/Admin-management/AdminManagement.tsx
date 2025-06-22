// filepath: i:\待办事项\短学期\NewsPublishingSystem\my-vite-app\src\components\Admin-management\AdminManagement.tsx
import React, { useState } from 'react';
import AddAdmin from './AddAdmin';
import EditAdmin from './EditAdmin';
import DeleteAdmin from './DeleteAdmin';
import EditorPermissions from './AdminPermissions';

const navItems = [
    '添加管理员',
    '编辑管理员',
    '删除管理员',
    '权限管理'
];

const AdminManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">管理员管理</h1>
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
                                    className={`hover:text-purple-500 ${activeTab === idx ? 'text-purple-500 font-semibold' : ''}`}
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
                {activeTab === 0 && <AddAdmin />}
                {activeTab === 1 && <EditAdmin />}
                {activeTab === 2 && <DeleteAdmin />}
                {activeTab === 3 && <EditorPermissions />}
            </div>
        </main>
    );
};

export default AdminManagement;
