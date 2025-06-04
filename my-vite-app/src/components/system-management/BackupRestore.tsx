// src/components/system-management/BackupRestore.tsx
import React from 'react';

const CategoryManage: React.FC = () => {
    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-lg font-bold mb-4">系统备份与恢复</h2>
                <div className="text-gray-600">此功能正在开发中...</div>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">图书分类管理功能使用说明</p>
                </div>
            </div>
        </div>
    );
};

export default CategoryManage;
