// src/components/borrow-management/RenewBook.tsx
import React, { useState } from 'react';

interface BorrowRecord {
  borrowId: string;
  readerId: string;
  readerUsername: string;
  readerGender: string;
  readerPhone: string;
  readerEmail: string;
  bookId: string;
  bookTitle: string;
  borrowTime: string;
  renewalCount: number;
  renewalDays: number;
  isOverdue: boolean;
  overdueDays: number;
  fine: string;
}

const RenewBook: React.FC = () => {
  const [bookId, setBookId] = useState('');
  const [showSuccess, setShowSuccess] = useState(true);
  const [renewalTime, setRenewalTime] = useState('14天');
  const [borrowRecord, _setBorrowRecord] = useState<BorrowRecord>({
    borrowId: '2',
    readerId: '1',
    readerUsername: 'reader1',
    readerGender: '男',
    readerPhone: '13900000001',
    readerEmail: 'reader1@example.com',
    bookId: '2',
    bookTitle: 'Python编程: 从入门到实践',
    borrowTime: '2023-05-10 14:20:00',
    renewalCount: 0,
    renewalDays: 0,
    isOverdue: true,
    overdueDays: 747,
    fine: '0.00'
  });

  const handleSearch = () => {
    // 在实际应用中，这里会调用API来查询图书借阅记录
    if (bookId.trim()) {
      setShowSuccess(true);
    }
  };

  const handleConfirmRenewal = () => {
    // 在实际应用中，这里会调用API来完成续借操作
    alert('续借成功！');
  };

  const handleCancelRenewal = () => {
    // 取消续借操作
    setBookId('');
    setShowSuccess(false);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  return (
    <div className="max-w-4xl mx-auto mt-10 p-6 bg-white shadow-md rounded-md">
      <h1 className="text-xl font-bold mb-4">续借管理</h1>

      {/* Success Message */}
      {showSuccess && (
        <div className="bg-green-100 text-green-800 px-4 py-2 rounded-md mb-4">
          查询成功!
        </div>
      )}

      {/* Search Input */}
      <div className="mb-4">
        <label htmlFor="bookId" className="block text-gray-700 mb-2">请输入图书 ID:</label>
        <input
          type="text"
          id="bookId"
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={bookId}
          onChange={(e) => setBookId(e.target.value)}
          onKeyPress={handleKeyPress}
        />
      </div>

      <button
        className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600 mb-6"
        onClick={handleSearch}
      >
        查询
      </button>

      {/* Borrow Record Information */}
      {showSuccess && (
        <div className="text-gray-700">
          <p>借阅id: {borrowRecord.borrowId}</p>
          <p>读者id: {borrowRecord.readerId}</p>
          <p>读者用户名: {borrowRecord.readerUsername}</p>
          <p>读者性别: {borrowRecord.readerGender}</p>
          <p>读者手机号: {borrowRecord.readerPhone}</p>
          <p>读者邮箱: {borrowRecord.readerEmail}</p>
          <p>图书id: {borrowRecord.bookId}</p>
          <p>书名: {borrowRecord.bookTitle}</p>
          <p>借阅时间: {borrowRecord.borrowTime}</p>
          <p>续借次数: {borrowRecord.renewalCount}</p>
          <p>已续借时长: {borrowRecord.renewalDays}</p>
          <p>是否逾期: {borrowRecord.isOverdue ? '是' : '否'}</p>
          <p>逾期天数: {borrowRecord.overdueDays} 天</p>
          <p>逾期罚款: {borrowRecord.fine} 元</p>
        </div>
      )}

      {/* Renewal Options */}
      {showSuccess && (
        <div className="mt-4">
          <label htmlFor="renewalTime" className="block text-gray-700 mb-2">续借时间</label>
          <select
            id="renewalTime"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            value={renewalTime}
            onChange={(e) => setRenewalTime(e.target.value)}
          >
            <option>14天</option>
          </select>
        </div>
      )}

      {/* Action Buttons */}
      {showSuccess && (
        <div className="mt-6 flex space-x-4">
          <button
            className="bg-red-500 text-white px-4 py-2 rounded-md hover:bg-red-600"
            onClick={handleConfirmRenewal}
          >
            确认续借
          </button>
          <button
            className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600"
            onClick={handleCancelRenewal}
          >
            取消续借
          </button>
        </div>
      )}
    </div>
  );
};

export default RenewBook;
