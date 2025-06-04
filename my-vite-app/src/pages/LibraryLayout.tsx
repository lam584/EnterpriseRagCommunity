// src/pages/LibraryLayout.tsx
import React, { useState } from 'react';
import {
    FaBook,
    FaUserFriends,
    FaBookReader,
    FaFileInvoiceDollar,
    FaChartPie,
    FaCog,
    FaUserEdit,
    FaInfoCircle,
    // FaList,
    // FaExchangeAlt,
    // FaClipboardList,
    // FaShieldAlt,
    // FaUsersCog
} from 'react-icons/fa';

// 导入所有管理组件
import BookManagement from '../components/book-management/BookManagement';
import ReaderManagement from '../components/reader-management/reader-management';
import BorrowManagement from '../components/borrow-management/borrow-management';
import FineManagement from '../components/fine-management/fine-management';
import StatsManagement from '../components/stats-management/stats-management';
import SystemManagement from '../components/system-management/system-management';
import AccountManagement from '../components/account-management/account-management';
import HelpCenter from '../components/help-center/HelpCenter';

// 主导航项
const sidebarCategories = [
    {
        id: 'book',
        label: '图书管理',
        icon: <FaBook />,
        subItems: [
            { id: 'addBook', label: '添加新书' },
            { id: 'editBook', label: '编辑图书信息' },
            { id: 'deleteBook', label: '删除图书' },
            { id: 'searchBook', label: '查找图书' },
            { id: 'categoryManage', label: '图书分类管理' },
            { id: 'shelfManage', label: '书架管理' }
        ]
    },
    {
        id: 'reader',
        label: '读者管理',
        icon: <FaUserFriends />,
        subItems: [
            { id: 'addReader', label: '添加新读者' },
            { id: 'editReader', label: '编辑读者信息' },
            { id: 'deleteReader', label: '删除读者' },
            { id: 'searchReader', label: '读者查询' }
        ]
    },
    {
        id: 'borrow',
        label: '借阅管理',
        icon: <FaBookReader />,
        subItems: [
            { id: 'borrowBook', label: '借出图书' },
            { id: 'returnBook', label: '归还图书' },
            { id: 'renewBook', label: '续借管理' },
            { id: 'borrowQuery', label: '图书借阅情况查询' }
        ]
    },
    {
        id: 'fine',
        label: '罚款管理',
        icon: <FaFileInvoiceDollar />,
        subItems: [
            { id: 'fineRecords', label: '罚款记录' },
            { id: 'payFine', label: '缴纳欠款' },
            { id: 'fineRules', label: '管理逾期罚款规则' }
        ]
    },
    {
        id: 'stats',
        label: '统计报表',
        icon: <FaChartPie />,
        subItems: [
            { id: 'bookStats', label: '图书借阅统计' },
            { id: 'readerStats', label: '读者借阅统计' },
            { id: 'fineStats', label: '罚款统计' },
            { id: 'inventoryStats', label: '库存统计' }
        ]
    },
    {
        id: 'system',
        label: '系统设置',
        icon: <FaCog />,
        subItems: [
            { id: 'readerPermissions', label: '读者权限管理' },
            { id: 'adminPermissions', label: '管理员权限管理' },
            { id: 'addAdmin', label: '添加新管理员' },
            { id: 'resetPassword', label: '重置其他管理员密码' },
            { id: 'backup', label: '系统备份与恢复' },
            { id: 'logs', label: '系统日志管理' }
        ]
    },
    {
        id: 'account',
        label: '个人账户',
        icon: <FaUserEdit />,
        subItems: [
            { id: 'editProfile', label: '编辑个人信息' },
            { id: 'changePassword', label: '更改密码' },
            { id: 'logout', label: '退出登录' }
        ]
    },
    {
        id: 'help',
        label: '帮助与支持',
        icon: <FaInfoCircle />,
        subItems: [
            {
                id: 'helpCenter', label: '帮助中心'
            }
        ]
    }
];

const LibraryLayout: React.FC = () => {
    const [activeCategory, setActiveCategory] = useState('book');

    // 处理主类别点击
    const handleCategoryClick = (categoryId: string) => {
        setActiveCategory(categoryId);
    };

    return (
        <div className="flex h-screen">
            {/* Sidebar */}
            <aside className="w-64 bg-gradient-to-b from-purple-700 to-purple-500 text-white shadow-xl flex flex-col">
                <div className="flex items-center justify-center h-20 border-b border-purple-800">
                    <FaBook className="text-3xl" />
                    <span className="ml-2 text-2xl font-bold">图书管理系统</span>
                </div>
                <ul className="flex-1 mt-4">
                    {sidebarCategories.map((item) => {
                        const selected = item.id === activeCategory;
                        return (
                            <li
                                key={item.id}
                                onClick={() => handleCategoryClick(item.id)}
                                className={`px-6 py-3 mx-3 my-1 flex items-center rounded-lg cursor-pointer duration-200 ${
                                    selected
                                        ? 'bg-purple-800 shadow-inner'
                                        : 'hover:bg-purple-500 hover:shadow-lg'
                                }`}
                            >
                                <div className={`text-xl transition-colors duration-200`}>
                                    {item.icon}
                                </div>
                                <span className="ml-4 font-medium">{item.label}</span>
                            </li>
                        );
                    })}
                </ul>
                <div className="p-4 border-t border-purple-800 text-sm text-purple-200">
                    © 2024 图书系统
                </div>
            </aside>

            {/* Main Content */}
            <div className="flex-1 overflow-auto">
                {activeCategory === 'book' && <BookManagement />}
                {activeCategory === 'reader' && <ReaderManagement />}
                {activeCategory === 'borrow' && <BorrowManagement />}
                {activeCategory === 'fine' && <FineManagement />}
                {activeCategory === 'stats' && <StatsManagement />}
                {activeCategory === 'system' && <SystemManagement />}
                {activeCategory === 'account' && <AccountManagement />}
                {activeCategory === 'help' && <HelpCenter />}
            </div>
        </div>
    );
};

export default LibraryLayout;
