// src/components/fine-management/PayFine.tsx
import React, { useState } from 'react';

const PayFine: React.FC = () => {
    // 状态管理
    const [searchSuccess, setSearchSuccess] = useState(true);
    const [readerId, setReaderId] = useState('4');
    const [paymentAmount, setPaymentAmount] = useState('');
    const [remarks, setRemarks] = useState('');

    // 模拟的读者数据
    const readerData = {
        id: '4',
        username: 'vip2',
        phone: '13900000004',
        email: 'vip2@example.com',
        gender: '女',
        permissionId: '2',
        totalFine: 15
    };

    // 模拟的欠款记录
    const fineRecord = {
        readerId: '4',
        borrowOrderId: '5',
        overdueDays: 15,
        fineAmount: '15.00',
        isPaid: '否',
        dueDate: '2023-06-05 00:00:00',
        paymentId: '0.00',
        paidAmount: '0.00',
        remarks: '图书《红楼梦》逾期未还'
    };

    // 处理查询按钮点击
    const handleSearch = () => {
        // 这里可以添加实际的查询逻辑
        setSearchSuccess(true);
    };

    // 处理提交按钮点击
    const handleSubmit = () => {
        // 这里可以添加实际的支付提交逻辑
        alert(`已提交支付: ${paymentAmount}元`);
    };

    return (
        <div className="max-w-5xl mx-auto mt-10 bg-white p-6 rounded shadow">
            <h1 className="text-2xl font-bold mb-4">缴纳读者逾期欠款</h1>

            {/* 搜索成功提示 */}
            {searchSuccess && (
                <div className="bg-green-100 text-green-800 px-4 py-2 rounded mb-4">
                    查询成功!
                </div>
            )}

            {/* 读者ID查询部分 */}
            <div className="mb-6">
                <label htmlFor="readerId" className="block text-gray-700 font-medium mb-2">请输入读者ID：</label>
                <div className="flex items-center space-x-4">
                    <input
                        type="text"
                        id="readerId"
                        value={readerId}
                        onChange={(e) => setReaderId(e.target.value)}
                        className="border border-gray-300 rounded px-4 py-2 w-full focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="请输入读者ID"
                    />
                    <button
                        className="bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600"
                        onClick={handleSearch}
                    >
                        查询
                    </button>
                </div>
            </div>

            {/* 读者信息表格 */}
            <table className="w-full border-collapse border border-gray-300 mb-6">
                <thead>
                    <tr className="bg-gray-100">
                        <th className="border border-gray-300 px-4 py-2">读者ID</th>
                        <th className="border border-gray-300 px-4 py-2">用户名</th>
                        <th className="border border-gray-300 px-4 py-2">手机号</th>
                        <th className="border border-gray-300 px-4 py-2">邮箱</th>
                        <th className="border border-gray-300 px-4 py-2">性别</th>
                        <th className="border border-gray-300 px-4 py-2">权限ID</th>
                        <th className="border border-gray-300 px-4 py-2">欠款金额</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.id}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.username}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.phone}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.email}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.gender}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.permissionId}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{readerData.totalFine}</td>
                    </tr>
                </tbody>
            </table>

            {/* 欠款记录表格 */}
            <table className="w-full border-collapse border border-gray-300 mb-6">
                <thead>
                    <tr className="bg-gray-100">
                        <th className="border border-gray-300 px-4 py-2">读者ID</th>
                        <th className="border border-gray-300 px-4 py-2">借阅订单ID</th>
                        <th className="border border-gray-300 px-4 py-2">逾期天数</th>
                        <th className="border border-gray-300 px-4 py-2">欠款金额</th>
                        <th className="border border-gray-300 px-4 py-2">是否缴清欠款</th>
                        <th className="border border-gray-300 px-4 py-2">截止日期</th>
                        <th className="border border-gray-300 px-4 py-2">支付账单ID</th>
                        <th className="border border-gray-300 px-4 py-2">已支付金额</th>
                        <th className="border border-gray-300 px-4 py-2">备注</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.readerId}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.borrowOrderId}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.overdueDays}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.fineAmount}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.isPaid}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.dueDate}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.paymentId}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.paidAmount}</td>
                        <td className="border border-gray-300 px-4 py-2 text-center">{fineRecord.remarks}</td>
                    </tr>
                </tbody>
            </table>

            {/* 支付表单 */}
            <div className="mb-6">
                <p className="text-gray-700 font-medium mb-2">请输入读者支付金额(该读者欠款为{readerData.totalFine}元)：</p>
                <div className="space-y-4">
                    <input
                        type="text"
                        value={paymentAmount}
                        onChange={(e) => setPaymentAmount(e.target.value)}
                        className="border border-gray-300 rounded px-4 py-2 w-full focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="支付金额"
                    />
                    <input
                        type="text"
                        value={remarks}
                        onChange={(e) => setRemarks(e.target.value)}
                        className="border border-gray-300 rounded px-4 py-2 w-full focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="备注"
                    />
                </div>
            </div>

            {/* 提交按钮 */}
            <button
                className="bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600"
                onClick={handleSubmit}
            >
                提交
            </button>
        </div>
    );
};

export default PayFine;
