// src/components/fine-management/FineRules.tsx
import React, { useState } from 'react';

const FineRules: React.FC = () => {
    // 状态管理
    const [loadSuccess, _setLoadSuccess] = useState(true);
    const [_isEditing, setIsEditing] = useState(false);

    // 模拟的罚款规则数据
    const fineRulesData = [
        {
            name: '轻微逾期',
            minDays: 1,
            maxDays: 7,
            dailyFine: 0.50,
            status: '启用'
        },
        {
            name: '一般逾期',
            minDays: 8,
            maxDays: 30,
            dailyFine: 1.00,
            status: '启用'
        },
        {
            name: '严重逾期',
            minDays: 31,
            maxDays: 90,
            dailyFine: 2.00,
            status: '启用'
        },
        {
            name: '长期逾期',
            minDays: 91,
            maxDays: 999,
            dailyFine: 5.00,
            status: '启用'
        }
    ];

    // 处理编辑按钮点击
    const handleEditClick = () => {
        setIsEditing(true);
    };

    return (
        <div className="max-w-4xl mx-auto mt-10 bg-white shadow-md rounded-lg p-6">
            <h1 className="text-xl font-bold mb-4">管理逾期罚款规则</h1>

            {/* 成功提示信息 */}
            {loadSuccess && (
                <div className="bg-green-100 text-green-700 px-4 py-2 rounded mb-6">
                    获取逾期罚款规则成功!
                </div>
            )}

            {/* 罚款规则表格 */}
            <div className="overflow-x-auto">
                <table className="w-full border-collapse border border-gray-300 text-sm text-left">
                    <thead>
                        <tr className="bg-gray-100">
                            <th className="border border-gray-300 px-4 py-2">规则名称</th>
                            <th className="border border-gray-300 px-4 py-2">逾期天数下限</th>
                            <th className="border border-gray-300 px-4 py-2">逾期天数上限</th>
                            <th className="border border-gray-300 px-4 py-2">逾期每日罚款</th>
                            <th className="border border-gray-300 px-4 py-2">规则状态</th>
                        </tr>
                    </thead>
                    <tbody>
                        {fineRulesData.map((rule, index) => (
                            <tr key={index}>
                                <td className="border border-gray-300 px-4 py-2">{rule.name}</td>
                                <td className="border border-gray-300 px-4 py-2">{rule.minDays}</td>
                                <td className="border border-gray-300 px-4 py-2">{rule.maxDays}</td>
                                <td className="border border-gray-300 px-4 py-2">{rule.dailyFine.toFixed(2)}</td>
                                <td className="border border-gray-300 px-4 py-2">{rule.status}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* 编辑按钮 */}
            <div className="mt-6">
                <button
                    className="bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600"
                    onClick={handleEditClick}
                >
                    编辑
                </button>
            </div>
        </div>
    );
};

export default FineRules;
