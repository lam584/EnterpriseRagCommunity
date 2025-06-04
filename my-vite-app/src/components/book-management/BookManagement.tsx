// src/components/book-management/BookManagement.tsx
import React, { useState } from 'react';
import AddBookForm from './AddBookForm';
import EditBookForm from './EditBookForm';
import DeleteBookForm from './DeleteBookForm';
import SearchBook from './SearchBook';
import CategoryManage from './CategoryManage';
import ShelfManage from './ShelfManage';

const navItems = [
    '添加新书',
    '编辑图书信息',
    '删除图书',
    '查找图书',
    '图书分类管理',
    '书架管理'
];

const BookManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">图书管理</h1>
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
                {activeTab === 0 && <AddBookForm />}
                {activeTab === 1 && <EditBookForm />}
                {activeTab === 2 && <DeleteBookForm />}
                {activeTab === 3 && <SearchBook />}
                {activeTab === 4 && <CategoryManage />}
                {activeTab === 5 && <ShelfManage />}
            </div>
        </main>
    );
};

export default BookManagement;
