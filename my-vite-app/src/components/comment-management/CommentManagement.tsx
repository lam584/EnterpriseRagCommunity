// src/components/comment-management/CommentManagement.tsx
import React, { useState } from 'react';
import ReviewComments from './ReviewComments';
import DeleteComments from './DeleteComments';
import SearchComments from './SearchComments';

// 定义子菜单项
const subMenuItems = [
    { id: 'reviewComments', label: '评论审核' },
    { id: 'deleteComments', label: '删除评论' },
    { id: 'searchComments', label: '评论查询' }
];

const CommentManagement: React.FC = () => {
    const [activeSubMenu, setActiveSubMenu] = useState('reviewComments');

    return (
        <main className="flex-1 p-6 bg-[rgb(221,221,221)] min-h-screen w-full">
            <div className="bg-white shadow-md rounded-md p-4 mb-6">
                <h1 className="text-2xl font-bold mb-6">评论管理</h1>
                <nav className="mt-4">
                    <ul className="flex space-x-4 text-gray-600">
                        {subMenuItems.map((item) => (
                            <button
                                key={item.id}
                                onClick={() => setActiveSubMenu(item.id)}
                                className={`px-4 py-2 rounded-lg ${
                                    activeSubMenu === item.id
                                        ? 'bg-purple-600 text-white'
                                        : 'bg-gray-200 hover:bg-gray-300'
                                }`}
                            >
                                {item.label}
                            </button>
                        ))}
                    </ul>
                </nav>
            </div>

            {/* 根据活动标签显示不同的组件 */}
            <div className="mt-6">
                {activeSubMenu === 'reviewComments' && <ReviewComments />}
                {activeSubMenu === 'deleteComments' && <DeleteComments />}
                {activeSubMenu === 'searchComments' && <SearchComments />}
            </div>
        </main>
    );
};

export default CommentManagement;
