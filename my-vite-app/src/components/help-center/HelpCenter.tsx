// src/components/help-center/HelpCenter.tsx
import React, { useState } from 'react';
import {
    FaBook,
    FaUserGraduate,
    FaSearch,
    FaCalendarAlt,
    FaTools
} from 'react-icons/fa';

const HelpCenter: React.FC = () => {
    const [searchQuery, setSearchQuery] = useState('');

    // 帮助中心卡片数据
    const helpCategories = [
        {
            icon: <FaBook className="text-3xl text-black mb-4" />,
            title: "图书管理",
            description: "图书入库、借阅、归还等操作说明",
            articles: 35
        },
        {
            icon: <FaUserGraduate className="text-3xl text-black mb-4" />,
            title: "读者服务",
            description: "关于读者注册、借阅规则等问题",
            articles: 22
        },
        {
            icon: <FaSearch className="text-3xl text-black mb-4" />,
            title: "图书检索",
            description: "如何使用系统进行图书检索",
            articles: 18
        },
        {
            icon: <FaCalendarAlt className="text-3xl text-black mb-4" />,
            title: "活动通知",
            description: "图书馆举办活动的通知与详情",
            articles: 12
        },
        {
            icon: <FaTools className="text-3xl text-black mb-4" />,
            title: "系统设置",
            description: "管理员权限、系统配置等相关设置",
            articles: 10
        }
    ];

    // 处理搜索输入变化
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchQuery(e.target.value);
    };

    return (
        <div className="bg-gray-100 w-full">
            <div className="max-w-4xl mx-auto py-10">
                {/* Title */}
                <h1 className="text-2xl font-bold text-center mb-6">图书馆管理系统帮助中心</h1>

                {/* Search Bar */}
                <div className="mb-8">
                    <input
                        type="text"
                        placeholder="🔍 搜索文章..."
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        value={searchQuery}
                        onChange={handleSearchChange}
                    />
                </div>

                {/* Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                    {helpCategories.map((category, index) => (
                        <div key={index} className="bg-white shadow-md rounded-lg p-6 text-center hover:shadow-lg transition-shadow duration-300">
                            {category.icon}
                            <h2 className="text-lg font-bold mb-2">{category.title}</h2>
                            <p className="text-gray-600 mb-2">{category.description}</p>
                            <p className="text-gray-500">{category.articles} 篇</p>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default HelpCenter;
