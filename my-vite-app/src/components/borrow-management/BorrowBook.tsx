import { useState, useEffect } from 'react';
import bookLoanService, {
  Reader as ReaderType,
  Book as BookType
} from '../../services/bookLoanService';

// 定义 Axios 错误响应的接口
interface AxiosErrorResponse {
  data?: {
    message?: string;
  };
}

// 类型守卫，用于检查是否为 Axios 错误响应
function isAxiosErrorResponse(obj: unknown): obj is { response: AxiosErrorResponse } {
  return (
    typeof obj === 'object' &&
    obj !== null &&
    'response' in obj &&
    typeof obj.response === 'object' &&
    obj.response !== null
  );
}

export default function BorrowBook() {
  const [readerQuery, setReaderQuery] = useState('');
  const [readerSuggestions, setReaderSuggestions] = useState<ReaderType[]>([]);
  const [selectedReader, setSelectedReader] = useState<ReaderType | null>(null);const [bookQuery, setBookQuery] = useState('');
  const [bookSuggestions, setBookSuggestions] = useState<BookType[]>([]);
  const [selectedBooks, setSelectedBooks] = useState<BookType[]>([]);const [durationDays, setDurationDays] = useState(7);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);// 读者模糊查询
  useEffect(() => {
    if (readerQuery) {
      const idParam = isNaN(Number(readerQuery)) ? undefined : Number(readerQuery);
      bookLoanService
          .queryReaders({
            id: idParam,
            account: readerQuery,
            phone: readerQuery,
            email: readerQuery
          })
          .then(res => setReaderSuggestions(res))
          .catch(console.error);
    } else {
      setReaderSuggestions([]);
    }
  }, [readerQuery]);// 图书模糊查询
  useEffect(() => {
    const idParam = isNaN(Number(bookQuery)) ? undefined : Number(bookQuery);
    if (bookQuery) {
      bookLoanService
          .queryBooks({ id: idParam, isbn: bookQuery, title: bookQuery })
          .then(res => setBookSuggestions(res))
          .catch(console.error);
    } else {
      setBookSuggestions([]);
    }
  }, [bookQuery]);// 选中读者
  const handleSelectReader = (r: ReaderType) => {
    setSelectedReader(r);
    setReaderQuery(r.account);
    setReaderSuggestions([]);
    setError(null);
    setSuccess(null);
  };// 选中图书
  const handleAddBook = (b: BookType) => {
    if (selectedBooks.find(sb => sb.id === b.id)) {
      setError('该图书已添加');
      return;
    }

    if (b.status !== '可借阅') {
      setError('该图书当前不可借阅');
      return;
    }

    setSelectedBooks(prev => [...prev, b]);
    setBookQuery('');
    setBookSuggestions([]);
    setError(null);
  };// 移除图书
  const handleRemoveBook = (id: number) => {
    setSelectedBooks(prev => prev.filter(b => b.id !== id));
  };// 确认借阅
  const handleConfirmBorrow = async () => {
    if (!selectedReader) {
      setError('请先选择读者');
      return;
    }
    if (selectedBooks.length === 0) {
      setError('请至少选择一本图书');
      return;
    }
    try {
      await bookLoanService.createBookLoans(
          selectedReader.id,
          selectedBooks.map(b => b.id),
          durationDays
      );
      setSuccess('借阅成功');
// 重置表单
      setSelectedReader(null);
      setReaderQuery('');
      setSelectedBooks([]);
      setDurationDays(7);
    } catch (e: unknown) {
      const errorMessage = isAxiosErrorResponse(e)
        ? e.response?.data?.message || '借阅失败'
        : '借阅失败';
      setError(errorMessage);
    }
  };return (
      <div className="max-w-4xl mx-auto bg-white shadow-md rounded-lg p-6">
        <h1 className="text-2xl font-bold mb-4">借出图书</h1>
        {success && (
            <div className="bg-green-100 text-green-700 px-4 py-2 rounded mb-4">
              {success}
            </div>
        )}
        {error && (
            <div className="bg-red-100 text-red-700 px-4 py-2 rounded mb-4">
              {error}
            </div>
        )}
        <div className="mb-6 relative">
          <label className="block text-gray-700 font-medium mb-2">
            搜索读者（ID/账号/邮箱/手机号）:
          </label>
          <input
              type="text"
              className="w-full border border-gray-300 rounded px-4 py-2"
              value={readerQuery}
              onChange={e => {
                setReaderQuery(e.target.value);
                setSelectedReader(null);
              }}
              placeholder="输入读者信息"
          />
          {readerSuggestions.length > 0 && (
              <ul className="absolute z-10 bg-white border border-gray-300 w-full mt-1 max-h-48 overflow-auto">
                {readerSuggestions.map(r => (
                    <li
                        key={r.id}
                        className="px-4 py-2 hover:bg-gray-100 cursor-pointer"
                        onClick={() => handleSelectReader(r)}
                    >
                        {r.account} （ID: {r.id}）
                    </li>
                ))}
              </ul>
          )}
        </div>
        {selectedReader && (
            <div className="mb-6 p-4 border border-gray-200 rounded bg-gray-50">
              <p>
                <strong>ID:</strong> {selectedReader.id}
              </p>
              <p>
                <strong>账号:</strong> {selectedReader.account}
              </p>
              <p>
                <strong>邮箱:</strong> {selectedReader.email}
              </p>
              <p>
                <strong>手机号:</strong> {selectedReader.phone}
              </p>
              <p>
                <strong>状态:</strong>{' '}
                {selectedReader.isActive ? '活动' : '禁用'}
              </p>
            </div>
        )}
        <div className="mb-6 relative">
          <label className="block text-gray-700 font-medium mb-2">
            搜索图书（ID/ISBN/书名）:
          </label>
          <input
              type="text"
              className="w-full border border-gray-300 rounded px-4 py-2"
              value={bookQuery}
              onChange={e => setBookQuery(e.target.value)}
              placeholder="输入图书信息"
          />
          {bookSuggestions.length > 0 && (
              <ul className="absolute z-10 bg-white border border-gray-300 w-full mt-1 max-h-48 overflow-auto">
                {bookSuggestions.map(b => (
                    <li
                        key={b.id}
                        className="px-4 py-2 hover:bg-gray-100 cursor-pointer"
                        onClick={() => handleAddBook(b)}
                    >
                      {b.title} （{b.id}） - 状态: {b.status}
                    </li>
                ))}
              </ul>
          )}
        </div>
        {selectedBooks.length > 0 && (
            <div className="mb-6">
              <p className="font-medium mb-2">已选择图书:</p>
              <table className="w-full border border-gray-300">
                <thead>
                <tr className="bg-gray-100">
                  <th className="px-4 py-2">ID</th>
                  <th className="px-4 py-2">书名</th>
                  <th className="px-4 py-2">操作</th>
                </tr>
                </thead>
                <tbody>
                {selectedBooks.map(b => (
                    <tr key={b.id}>
                      <td className="border px-4 py-2">{b.id}</td>
                      <td className="border px-4 py-2">{b.title}</td>
                      <td className="border px-4 py-2">
                        <button
                            className="text-red-500 hover:underline"
                            onClick={() => handleRemoveBook(b.id)}
                        >
                          移除
                        </button>
                      </td>
                    </tr>
                ))}
                </tbody>
              </table>
            </div>
        )}
        <div className="mb-6">
          <label className="block text-gray-700 font-medium mb-2">
            借阅天数:
          </label>
          <input
              type="number"
              min="1"
              className="w-32 border border-gray-300 rounded px-4 py-2"
              value={durationDays}
              onChange={e => setDurationDays(Number(e.target.value))}
          />
        </div>
        <button
            className="bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600"
            onClick={handleConfirmBorrow}
        >
          确认借阅
        </button>
      </div>
  );
}