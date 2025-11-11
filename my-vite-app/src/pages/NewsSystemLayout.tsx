// src/pages/NewsSystemLayout.tsx
import React, { useState } from 'react';
import {
    FaNewspaper,
    FaUserFriends,
    FaChartPie,
    FaCog,
    FaUserEdit,
    FaInfoCircle,
    FaUserPlus,
    FaComments
} from 'react-icons/fa';

// 导入所有管理组件
import UserManagement from '../components/user-management/UserManagement';
import EditorManagement from '../components/Admin-management/AdminManagement.tsx';

// 主导航项
const sidebarCategories = [
    {
        id: 'new',
        label: '新闻管理',
        icon: <FaNewspaper />,
        subItems: [
            { id: 'addNew', label: '发布新闻' },
            { id: 'editNew', label: '编辑新闻' },
            { id: 'deleteNew', label: '删除新闻' },
            { id: 'searchNew', label: '查找新闻' },
            { id: 'categoryManage', label: '新闻主题管理' }
        ]
    },
    {
        id: 'user',
        label: '用户管理',
        icon: <FaUserFriends />,
        subItems: [
            { id: 'addUser', label: '添加用户' },
            { id: 'editUser', label: '编辑用户信息' },
            { id: 'deleteUser', label: '删除用户' },
            { id: 'searchUser', label: '用户查询' }
        ]
    },
    {
        id: 'editor',
        label: '管理员管理',
        icon: <FaUserPlus />,
        subItems: [
            { id: 'addEditor', label: '添加管理员' },
            { id: 'editEditor', label: '管理员信息管理' },
            { id: 'deleteEditor', label: '删除管理员' },
            { id: 'editorPermissions', label: '管理员权限管理' }
        ]
    },
    {
        id: 'comment',
        label: '评论管理',
        icon: <FaComments />,
        subItems: [
            { id: 'reviewComments', label: '评论审核' },
            { id: 'deleteComments', label: '删除评论' },
            { id: 'searchComments', label: '评论查询' }
        ]
    },
    {
        id: 'stats',
        label: '统计报表',
        icon: <FaChartPie />,
        subItems: [
            { id: 'newReadStats', label: '新闻阅读统计' },
            { id: 'userReadStats', label: '用户阅读统计' },
            { id: 'userRegStats', label: '用户注册统计' },
            { id: 'userLoginStats', label: '用户登录统计' },
            { id: 'newsPublishStats', label: '新闻发布统计' },
            { id: 'commentStats', label: '评论统计' }
        ]
    },
    {
        id: 'system',
        label: '系统设置',
        icon: <FaCog />,
        subItems: [
            { id: 'userPermissions', label: '用户权限管理' },
            { id: 'adminPermissions', label: '管理员权限管理' },
            { id: 'addAdmin', label: '添加管理员' },
            { id: 'resetPassword', label: '重置密码' },
            { id: 'backup', label: '系统备份与恢复' },
            { id: 'logs', label: '系统日志管理' }
        ]
    },
    {
        id: 'account',
        label: '个人账户',
        icon: <FaUserEdit />,
        subItems: [
            { id: 'editProfile', label: '个人信息' },
            { id: 'changePassword', label: '修改密码' },
            { id: 'logout', label: '退出登录' }
        ]
    },
    {
        id: 'help',
        label: '帮助与支持',
        icon: <FaInfoCircle />,
        subItems: [
            { id: 'helpCenter', label: '帮助中心' },
            { id: 'contactAdmin', label: '联系管理员' }
        ]
    }
];

const NewsSystemLayout: React.FC = () => {
    const [activeCategory, setActiveCategory] = useState('new');

    // 处理主类别点击
    const handleCategoryClick = (categoryId: string) => {
        setActiveCategory(categoryId);
    };

    return (
        <div className="flex h-screen">
            {/* Sidebar */}
            <aside className="w-64 bg-gradient-to-b from-purple-700 to-purple-500 text-white shadow-xl flex flex-col">
                <div className="flex items-center justify-center h-20 border-b border-purple-800">
                    <FaNewspaper className="text-3xl" />
                    <span className="ml-2 text-2xl font-bold">新闻发布系统</span>
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
                    © 2024 新闻发布系统
                </div>
            </aside>

            {/* Main Content */}
            <div className="flex-1 overflow-auto">
                {activeCategory === 'user' && <UserManagement />}
                {activeCategory === 'editor' && <EditorManagement />}
            </div>
        </div>
    );
};

export default NewsSystemLayout;
