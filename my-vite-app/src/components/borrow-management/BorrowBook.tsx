// src/components/borrow-management/BorrowBook.tsx
import React, { useState } from 'react';

interface Book {
  id: string;
  title: string;
  overdueDays: number;
  renewalDays: number;
  fine: string;
}

interface Reader {
  id: string;
  username: string;
  phoneNumber: string;
  email: string;
  role: string;
  borrowedBooks: Book[];
}

const BorrowBook: React.FC = () => {
  const [bookId, setBookId] = useState('');
  const [showSuccess, setShowSuccess] = useState(false);
  const [reader, _setReader] = useState<Reader>({
    id: '1',
    username: 'reader1',
    phoneNumber: '13900000001',
    email: 'reader1@example.com',
    role: '普通读者',
    borrowedBooks: [
      {
        id: '2',
        title: 'Python编程：从入门到实践',
        overdueDays: 747,
        renewalDays: 0,
        fine: '0.00'
      }
    ]
  });

  const handleAddBook = () => {
    if (bookId.trim()) {
      // 在实际应用中，这里会调用API来验证图书ID并添加到借阅清单
      setShowSuccess(true);
      // 清空输入框
      setBookId('');
    }
  };

  const handleConfirmBorrow = () => {
    // 在实际应用中，这里会调用API来完成借阅操作
    alert('借阅成功！');
    setShowSuccess(false);
  };

  const handleCancelBorrow = () => {
    // 取消借阅操作
    setShowSuccess(false);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleAddBook();
    }
  };

  return (
    <div className="max-w-4xl mx-auto bg-white shadow-md rounded-lg p-6">
      <h1 className="text-2xl font-bold mb-4">借出图书</h1>

      {/* Success Message */}
      {showSuccess && (
        <div className="bg-green-100 text-green-700 px-4 py-2 rounded mb-4">
          查找成功!
        </div>
      )}

      {/* Input Section */}
      <div className="mb-6">
        <label htmlFor="book-id" className="block text-gray-700 font-medium mb-2">请输入要借阅的图书ID:</label>
        <div className="flex">
          <input
            type="text"
            id="book-id"
            className="flex-1 border border-gray-300 rounded-l px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="图书ID"
            value={bookId}
            onChange={(e) => setBookId(e.target.value)}
            onKeyPress={handleKeyPress}
          />
          <button
            className="bg-green-500 text-white px-4 py-2 rounded-r hover:bg-green-600"
            onClick={handleAddBook}
          >
            <i className="fas fa-plus"></i>
          </button>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex space-x-4 mb-6">
        <button
          className="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600"
          onClick={handleConfirmBorrow}
        >
          确认借阅
        </button>
        <button
          className="bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600"
          onClick={handleCancelBorrow}
        >
          取消借阅
        </button>
      </div>

      {/* Reader Information */}
      <div className="mb-6">
        <p className="text-gray-700"><strong>读者id:</strong> {reader.id}</p>
        <p className="text-gray-700"><strong>用户名:</strong> {reader.username}</p>
        <p className="text-gray-700"><strong>手机号:</strong> {reader.phoneNumber}</p>
        <p className="text-gray-700"><strong>邮箱:</strong> {reader.email}</p>
        <p className="text-gray-700"><strong>角色:</strong> {reader.role}</p>
      </div>

      {/* Borrowed Books Table */}
      <div>
        <p className="text-gray-700 font-medium mb-2">该读者已借阅图书:</p>
        <table className="w-full border border-gray-300 text-left">
          <thead>
            <tr className="bg-gray-100">
              <th className="border border-gray-300 px-4 py-2">图书ID</th>
              <th className="border border-gray-300 px-4 py-2">书名</th>
              <th className="border border-gray-300 px-4 py-2">逾期(天)</th>
              <th className="border border-gray-300 px-4 py-2">续借(天)</th>
              <th className="border border-gray-300 px-4 py-2">欠款(元)</th>
            </tr>
          </thead>
          <tbody>
            {reader.borrowedBooks.map((book) => (
              <tr key={book.id}>
                <td className="border border-gray-300 px-4 py-2">{book.id}</td>
                <td className="border border-gray-300 px-4 py-2">{book.title}</td>
                <td className="border border-gray-300 px-4 py-2">{book.overdueDays}</td>
                <td className="border border-gray-300 px-4 py-2">{book.renewalDays}</td>
                <td className="border border-gray-300 px-4 py-2">{book.fine}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default BorrowBook;
