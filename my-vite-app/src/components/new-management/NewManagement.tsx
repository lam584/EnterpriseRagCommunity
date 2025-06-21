// src/components/new-management/NewManagement.tsx
import React, { useState } from 'react';
import AddNewForm from './AddNews.tsx';
import EditBookForm from './EditNews';
import DeleteBookForm from './DeleteNews';
import SearchBook from './SearchNews';
import TopicManage from './TopicManage.tsx';

const navItems = [
    '发布新闻',
    '编辑新闻',
    '删除新闻',
    '查找新闻',
    '新闻主题管理'
];

const NewManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">新闻管理</h1>
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
                {activeTab === 0 && <AddNewForm />}
                {activeTab === 1 && <EditBookForm />}
                {activeTab === 2 && <DeleteBookForm />}
                {activeTab === 3 && <SearchBook />}
                {activeTab === 4 && <TopicManage />}
            </div>
        </main>
    );
};

export default NewManagement;
