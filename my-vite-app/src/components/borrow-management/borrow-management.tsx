// src/components/book-management/BookManagement.tsx
import React, { useState } from 'react';
import BorrowBook from './BorrowBook';
import ReturnBook from './ReturnBook';
import RenewBook from './RenewBook';
import BorrowQuery from './BorrowQuery';

const navItems = [
    '借出图书',
    '归还图书',
    '续借管理',
    '图书借阅情况查询'
];

const BorrowManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">借阅管理</h1>
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
                {activeTab === 0 && <BorrowBook />}
                {activeTab === 1 && <ReturnBook />}
                {activeTab === 2 && <RenewBook />}
                {activeTab === 3 && <BorrowQuery />}
            </div>
        </main>
    );
};

export default BorrowManagement;
