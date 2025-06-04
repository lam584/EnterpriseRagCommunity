// src/components/fine-management/FineRecords.tsx
import React, { useState } from 'react';

const FineRecords: React.FC = () => {
    // 状态管理
    const [searchSuccess, _setSearchSuccess] = useState(true);

    // 模拟的罚款记录数据
    const fineRecordsData = [
        {
            orderId: '5',
            bookId: '7',
            bookName: '红楼梦',
            readerId: '4',
            username: 'vip2',
            phone: '13900000004',
            overdueDays: 15,
            fineAmount: '15.00',
            paymentStatus: '未缴清',
            actualReturnDate: '未还款',
            remarks: '图书《红楼梦》逾期未还'
        }
    ];

    return (
        <div className="max-w-6xl mx-auto mt-8">
            {/* 导航菜单 */}
            <div className="text-lg font-semibold text-gray-800 mb-4">
                <span>罚款记录</span>
                <span className="text-gray-500 mx-2">/</span>
                <span className="text-gray-500">缴纳欠款</span>
                <span className="text-gray-500 mx-2">/</span>
                <span className="text-gray-500">管理逾期罚款规则</span>
            </div>

            {/* 内容主体 */}
            <div className="bg-white shadow rounded-lg p-6">
                {/* 搜索成功提示 */}
                {searchSuccess && (
                    <div className="bg-green-100 text-green-700 px-4 py-2 rounded mb-4">
                        查询成功
                    </div>
                )}

                {/* 罚款记录表格 */}
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse border border-gray-200 text-sm text-gray-700">
                        <thead>
                            <tr className="bg-gray-100">
                                <th className="border border-gray-200 px-4 py-2 text-left">订单号</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">图书ID</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">书名</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">读者ID</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">用户名</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">手机号</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">逾期(天)</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">欠款(元)</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">缴清状态</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">实际还款日期</th>
                                <th className="border border-gray-200 px-4 py-2 text-left">备注</th>
                            </tr>
                        </thead>
                        <tbody>
                            {fineRecordsData.map((record, index) => (
                                <tr key={index}>
                                    <td className="border border-gray-200 px-4 py-2">{record.orderId}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.bookId}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.bookName}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.readerId}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.username}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.phone}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.overdueDays}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.fineAmount}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.paymentStatus}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.actualReturnDate}</td>
                                    <td className="border border-gray-200 px-4 py-2">{record.remarks}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

export default FineRecords;
