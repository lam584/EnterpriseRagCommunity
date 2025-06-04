// src/components/reader-management/SearchReader.tsx
import React, { useState } from 'react';

const SearchReader: React.FC = () => {
    const [searchTerm, setSearchTerm] = useState('');

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-lg font-bold mb-4">读者查询</h2>
                <div className="mb-4">
                    <label htmlFor="searchTerm" className="block text-gray-700 mb-2">搜索条件:</label>
                    <div className="flex">
                        <input
                            type="text"
                            id="searchTerm"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="flex-1 border border-gray-300 rounded-l px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            placeholder="输入读者ID、姓名或身份证号..."
                        />
                        <button
                            className="bg-blue-500 text-white px-4 py-2 rounded-r hover:bg-blue-600"
                        >
                            搜索
                        </button>
                    </div>
                </div>

                <div className="text-gray-600 mt-6">
                    <p>搜索结果将在这里显示...</p>
                </div>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">读者查询功能使用说明</p>
                </div>
            </div>
        </div>
    );
};

export default SearchReader;
