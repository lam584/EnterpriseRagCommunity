import React, { useState } from 'react';
import AddReader from './AddReader';
import EditReader from './EditReader';
import DeleteReader from './DeleteReader';
import SearchReader from './SearchReader';

const navItems = [
    '添加新读者',
    '编辑读者信息',
    '删除读者',
    '读者查询'
];

const ReaderManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        // 使用Tailwind的任意值语法指定RGB(221,221,221)
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">读者管理</h1>
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
                {activeTab === 0 && <AddReader />}
                {activeTab === 1 && <EditReader />}
                {activeTab === 2 && <DeleteReader />}
                {activeTab === 3 && <SearchReader />}
            </div>
        </main>
    );
};

export default ReaderManagement;
