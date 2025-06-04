// src/components/borrow-management/ReturnBook.tsx
import React, { useState } from 'react';

const ReturnBook: React.FC = () => {
  const [bookId, setBookId] = useState('');
  const [error, setError] = useState<string | null>('图书ID1《深入理解Java虚拟机》不是借阅中状态。错误代码：401');
  const [success, setSuccess] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    // 在实际应用中，这里会调用API尝试归还图书
    if (bookId === '1') {
      // 模拟归还失败情况
      setError('图书ID1《深入理解Java虚拟机》不是借阅中状态。错误代码：401');
      setSuccess(null);
    } else if (bookId.trim()) {
      // 模拟归还成功情况
      setSuccess(`图书ID${bookId}归还成功！`);
      setError(null);
      // 清空输入框
      setBookId('');
    }
  };

  return (
    <div className="bg-white shadow-md rounded-md p-6 w-full max-w-md mx-auto">
      <h1 className="text-xl font-semibold mb-4">归还图书</h1>

      {/* 错误信息 */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          <p>{error}</p>
        </div>
      )}

      {/* 成功信息 */}
      {success && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
          <p>{success}</p>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <label htmlFor="book-id" className="block text-gray-700 font-medium mb-2">请输入图书ID:</label>
        <input
          type="text"
          id="book-id"
          className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 mb-4"
          placeholder="请输入图书ID"
          value={bookId}
          onChange={(e) => setBookId(e.target.value)}
        />
        <button
          type="submit"
          className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          归还图书
        </button>
      </form>
    </div>
  );
};

export default ReturnBook;
