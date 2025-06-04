// src/components/fine-management/fine-management.tsx
import React, { useState } from 'react';
import FineRecords from './FineRecords';
import PayFine from './PayFine';
import FineRules from './FineRules';

const navItems = [
    '罚款记录',
    '缴纳欠款',
    '管理逾期罚款规则'
];

const FineManagement: React.FC = () => {
    const [activeTab, setActiveTab] = useState(0);

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            {/* Header */}
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold">罚款管理</h1>
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
                {activeTab === 0 && <FineRecords />}
                {activeTab === 1 && <PayFine />}
                {activeTab === 2 && <FineRules />}
                {/* 其他标签的组件可以在这里添加 */}
                {activeTab > 2 && (
                    <div className="bg-white shadow-md rounded-md p-6 text-center">
                        <h2 className="text-xl font-bold mb-4">{navItems[activeTab]}</h2>
                        <p className="text-gray-600">此功能正在开发中...</p>
                    </div>
                )}
            </div>
        </main>
    );
};

export default FineManagement;
