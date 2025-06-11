// src/components/borrow-management/BorrowQuery.tsx
import React, { useState } from 'react';

interface BorrowRecord {
  borrowId: string;
  bookId: string;
  bookTitle: string;
  readerId: string;
  readerUsername: string;
  adminId: string;
  borrowTime: string;
  returnTime: string;
  status: string;
  bookPrice: string;
}

const BorrowQuery: React.FC = () => {
  const [searchParams, setSearchParams] = useState({
    borrowId: '',
    readerId: '',
    bookId: '',
    adminId: '',
    minPrice: '',
    maxPrice: '',
    borrowStartDate: '',
    borrowEndDate: '',
    returnStartDate: '',
    returnEndDate: '',
    borrowStatus: '请选择',
    exactSearchBorrowId: false,
    exactSearchReaderId: false,
    exactSearchBookId: false,
    exactSearchAdminId: false
  });

  const [showSuccess, setShowSuccess] = useState(true);
  const [searchResults, ] = useState<BorrowRecord[]>([
    {
      borrowId: '1',
      bookId: '1',
      bookTitle: '深入理解Java虚拟机',
      readerId: '1',
      readerUsername: 'reader1',
      adminId: '1',
      borrowTime: '2023-05-01 10:00:00',
      returnTime: '2023-05-15 16:30:00',
      status: '已归还',
      bookPrice: '0.00'
    }
  ]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { id, value } = e.target;
    setSearchParams(prev => ({
      ...prev,
      [id]: value
    }));
  };

  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, checked } = e.target;
    setSearchParams(prev => ({
      ...prev,
      [id]: checked
    }));
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    // 在实际应用中，这里会调用API根据搜索参数获取借阅记录
    setShowSuccess(true);
  };

  return (
    <div className="max-w-7xl mx-auto p-6 bg-white shadow-md mt-10 rounded-lg">
      <h1 className="text-2xl font-bold mb-6">图书借阅情况查询</h1>

      <form className="grid grid-cols-2 gap-6" onSubmit={handleSearch}>
        {/* Left Column */}
        <div>
          <div className="mb-4">
            <label htmlFor="borrowId" className="block text-gray-700 font-medium">借阅ID</label>
            <input
              type="text"
              id="borrowId"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.borrowId}
              onChange={handleInputChange}
            />
            <div className="mt-2">
              <input
                type="checkbox"
                id="exactSearchBorrowId"
                className="mr-2"
                checked={searchParams.exactSearchBorrowId}
                onChange={handleCheckboxChange}
              />
              <label htmlFor="exactSearchBorrowId" className="text-gray-600">精确搜索</label>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="readerId" className="block text-gray-700 font-medium">读者ID</label>
            <input
              type="text"
              id="readerId"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.readerId}
              onChange={handleInputChange}
            />
            <div className="mt-2">
              <input
                type="checkbox"
                id="exactSearchReaderId"
                className="mr-2"
                checked={searchParams.exactSearchReaderId}
                onChange={handleCheckboxChange}
              />
              <label htmlFor="exactSearchReaderId" className="text-gray-600">精确搜索</label>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="minPrice" className="block text-gray-700 font-medium">最低价格</label>
            <input
              type="text"
              id="minPrice"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.minPrice}
              onChange={handleInputChange}
            />
          </div>

          <div className="mb-4">
            <label htmlFor="borrowStartDate" className="block text-gray-700 font-medium">借阅日期（起始）</label>
            <div className="relative">
              <input
                type="text"
                id="borrowStartDate"
                className="w-full border border-gray-300 rounded-md p-2"
                value={searchParams.borrowStartDate}
                onChange={handleInputChange}
              />
              <i className="fas fa-calendar-alt absolute top-3 right-3 text-gray-400"></i>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="returnStartDate" className="block text-gray-700 font-medium">归还日期（起始）</label>
            <div className="relative">
              <input
                type="text"
                id="returnStartDate"
                className="w-full border border-gray-300 rounded-md p-2"
                value={searchParams.returnStartDate}
                onChange={handleInputChange}
              />
              <i className="fas fa-calendar-alt absolute top-3 right-3 text-gray-400"></i>
            </div>
          </div>
        </div>

        {/* Right Column */}
        <div>
          <div className="mb-4">
            <label htmlFor="bookId" className="block text-gray-700 font-medium">图书ID</label>
            <input
              type="text"
              id="bookId"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.bookId}
              onChange={handleInputChange}
            />
            <div className="mt-2">
              <input
                type="checkbox"
                id="exactSearchBookId"
                className="mr-2"
                checked={searchParams.exactSearchBookId}
                onChange={handleCheckboxChange}
              />
              <label htmlFor="exactSearchBookId" className="text-gray-600">精确搜索</label>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="adminId" className="block text-gray-700 font-medium">管理员ID</label>
            <input
              type="text"
              id="adminId"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.adminId}
              onChange={handleInputChange}
            />
            <div className="mt-2">
              <input
                type="checkbox"
                id="exactSearchAdminId"
                className="mr-2"
                checked={searchParams.exactSearchAdminId}
                onChange={handleCheckboxChange}
              />
              <label htmlFor="exactSearchAdminId" className="text-gray-600">精确搜索</label>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="maxPrice" className="block text-gray-700 font-medium">最高价格</label>
            <input
              type="text"
              id="maxPrice"
              className="w-full border border-gray-300 rounded-md p-2"
              value={searchParams.maxPrice}
              onChange={handleInputChange}
            />
          </div>

          <div className="mb-4">
            <label htmlFor="borrowEndDate" className="block text-gray-700 font-medium">借阅日期（结束）</label>
            <div className="relative">
              <input
                type="text"
                id="borrowEndDate"
                className="w-full border border-gray-300 rounded-md p-2"
                value={searchParams.borrowEndDate}
                onChange={handleInputChange}
              />
              <i className="fas fa-calendar-alt absolute top-3 right-3 text-gray-400"></i>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="returnEndDate" className="block text-gray-700 font-medium">归还日期（结束）</label>
            <div className="relative">
              <input
                type="text"
                id="returnEndDate"
                className="w-full border border-gray-300 rounded-md p-2"
                value={searchParams.returnEndDate}
                onChange={handleInputChange}
              />
              <i className="fas fa-calendar-alt absolute top-3 right-3 text-gray-400"></i>
            </div>
          </div>
        </div>
      </form>

      <div className="mb-4">
        <label htmlFor="borrowStatus" className="block text-gray-700 font-medium">借阅状态</label>
        <select
          id="borrowStatus"
          className="w-full border border-gray-300 rounded-md p-2"
          value={searchParams.borrowStatus}
          onChange={handleInputChange}
        >
          <option>请选择</option>
        </select>
      </div>

      <button
        type="submit"
        className="bg-green-500 text-white px-6 py-2 rounded-md hover:bg-green-600"
        onClick={handleSearch}
      >
        搜索
      </button>

      {showSuccess && (
        <div className="mt-6 bg-green-100 text-green-700 p-4 rounded-md">
          借阅记录搜索成功！
        </div>
      )}

      {showSuccess && searchResults.length > 0 && (
        <div className="mt-6">
          <h2 className="text-xl font-bold mb-4">搜索结果</h2>
          <table className="w-full border border-gray-300 text-left">
            <thead>
              <tr className="bg-gray-200">
                <th className="border border-gray-300 p-2">借阅ID</th>
                <th className="border border-gray-300 p-2">图书ID</th>
                <th className="border border-gray-300 p-2">书名</th>
                <th className="border border-gray-300 p-2">读者ID</th>
                <th className="border border-gray-300 p-2">用户名</th>
                <th className="border border-gray-300 p-2">管理员ID</th>
                <th className="border border-gray-300 p-2">借阅时间</th>
                <th className="border border-gray-300 p-2">归还时间</th>
                <th className="border border-gray-300 p-2">状态</th>
                <th className="border border-gray-300 p-2">图书价格</th>
              </tr>
            </thead>
            <tbody>
              {searchResults.map((record) => (
                <tr key={record.borrowId}>
                  <td className="border border-gray-300 p-2">{record.borrowId}</td>
                  <td className="border border-gray-300 p-2">{record.bookId}</td>
                  <td className="border border-gray-300 p-2">{record.bookTitle}</td>
                  <td className="border border-gray-300 p-2">{record.readerId}</td>
                  <td className="border border-gray-300 p-2">{record.readerUsername}</td>
                  <td className="border border-gray-300 p-2">{record.adminId}</td>
                  <td className="border border-gray-300 p-2">{record.borrowTime}</td>
                  <td className="border border-gray-300 p-2">{record.returnTime}</td>
                  <td className="border border-gray-300 p-2">{record.status}</td>
                  <td className="border border-gray-300 p-2">{record.bookPrice}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default BorrowQuery;