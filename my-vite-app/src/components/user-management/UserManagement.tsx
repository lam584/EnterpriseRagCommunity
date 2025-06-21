import React, { useState } from 'react';
import AddUser from './AddUser.tsx';
import EditUser from '././EditUser.tsx';
import DeleteUser from './DeleteUser.tsx';
import SearchUser from './SearchUser.tsx';

const navItems = [
    '添加新用户',
    '编辑用户信息',
    '删除用户',
    '用户查询'
];

const UserManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        // 使用Tailwind的任意值语法指定RGB(221,221,221)
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">用户管理</h1>
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
                {activeTab === 0 && <AddUser />}
                {activeTab === 1 && <EditUser />}
                {activeTab === 2 && <DeleteUser />}
                {activeTab === 3 && <SearchUser />}
            </div>
        </main>
    );
};

export default UserManagement;
