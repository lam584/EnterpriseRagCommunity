// src/components/stats-management/stats-management.tsx
import React, { useState } from 'react';
import BookStats from './BookStats';
import ReaderStats from './ReaderStats';
import FineStats from './FineStats';
import InventoryStats from './InventoryStats';

const navItems = [
    '图书借阅统计',
    '读者借阅统计',
    '罚款统计',
    '库存统计'
];

const StatsManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">统计报表</h1>
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
                {activeTab === 0 && <BookStats />}
                {activeTab === 1 && <ReaderStats />}
                {activeTab === 2 && <FineStats />}
                {activeTab === 3 && <InventoryStats />}
            </div>
        </main>
    );
};

export default StatsManagement;
