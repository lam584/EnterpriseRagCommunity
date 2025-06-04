// src/components/book-management/DeleteBookForm.tsx 搜索选择改进版
import React, { useState, useEffect, useRef } from 'react';
import { fetchBooks, deleteBook, BookDTO } from '../../services/bookService';

const MAX_RECENT_BOOKS = 10;

interface ExtendedBookDTO extends BookDTO {
  createdAt?: string;
  updatedAt?: string;
}

const DeleteBookForm: React.FC = () => {
  const [books, setBooks] = useState<ExtendedBookDTO[]>([]);
  const [filteredBooks, setFilteredBooks] = useState<ExtendedBookDTO[]>([]);
  const [selectedBook, setSelectedBook] = useState<ExtendedBookDTO | null>(null);

  const [loading, setLoading] = useState<boolean>(false);
  const [showConfirm, setShowConfirm] = useState<boolean>(false);
  const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);

  // 搜索条件
  const [searchCriteria, setSearchCriteria] = useState({
    keyword: '',
    searchField: 'title' // 默认按书名搜索
  });

  // 下拉菜单是否展开
  const [dropdownOpen, setDropdownOpen] = useState(false);
  
  // 添加引用来追踪下拉框状态
  const dropdownRef = useRef<HTMLDivElement>(null);
  // 添加定时器引用
  const timeoutRef = useRef<number | null>(null);

  const loadBooks = () => {
    setLoading(true);
    fetchBooks()
        .then(data => {
          setBooks(data);

          // 默认展示最近更新的图书
          const sortedBooks = [...data].sort((a, b) => {
            const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
            const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
            return dateB - dateA; // 降序排列，最新的在前面
          }).slice(0, MAX_RECENT_BOOKS);

          setFilteredBooks(sortedBooks);
          setDropdownOpen(true);
        })
        .catch(() => setMessage({type: 'error', text: '加载图书列表失败'}))
        .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadBooks();
  }, []);

  // 当搜索条件变化时，过滤图书
  useEffect(() => {
    if (!searchCriteria.keyword.trim()) {
      const recentBooks = [...books].sort((a, b) => {
        const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return dateB - dateA; // 降序排列，最新的在前面
      }).slice(0, MAX_RECENT_BOOKS);

      setFilteredBooks(recentBooks);
      setDropdownOpen(true);
      return;
    }

    const keyword = searchCriteria.keyword.toLowerCase();
    const filtered = books.filter(book => {
      switch (searchCriteria.searchField) {
        case 'id':
          return book.id?.toString() === keyword || book.id?.toString().includes(keyword);
        case 'isbn':
          return book.isbn.toLowerCase().includes(keyword);
        case 'author':
          return book.author.toLowerCase().includes(keyword);
        case 'publisher':
          return book.publisher.toLowerCase().includes(keyword);
        case 'title':
        default:
          return book.title.toLowerCase().includes(keyword);
      }
    });
    setFilteredBooks(filtered);
    setDropdownOpen(true);
  }, [searchCriteria, books]);

  // 添加点击外部关闭下拉框的处理函数
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setSearchCriteria(prev => ({ ...prev, [name]: value }));
  };
  
  // 添加搜索框失焦处理函数
  const handleSearchBlur = () => {
    // 使用setTimeout延迟关闭下拉框，让用户有时间点击选项
    timeoutRef.current = window.setTimeout(() => {
      setDropdownOpen(false);
    }, 200); // 200毫秒延迟
  };

  // 当鼠标进入下拉框时取消定时器
  const handleDropdownMouseEnter = () => {
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  };

  // 当鼠标离开下拉框时启动关闭定时器
  const handleDropdownMouseLeave = () => {
    timeoutRef.current = window.setTimeout(() => {
      setDropdownOpen(false);
    }, 200);
  };

  const handleBookSelect = (book: BookDTO) => {
    setSelectedBook(book);
    setDropdownOpen(false);
    setShowConfirm(false);
    setMessage(null);
  };

  const handleDeleteClick = () => {
    setShowConfirm(true);
  };

  const handleConfirmDelete = async () => {
    if (!selectedBook?.id) return;

    setLoading(true);
    try {
      await deleteBook(selectedBook.id);
      setMessage({type: 'success', text: `图书"${selectedBook.title}"已成功删除`});
      setSelectedBook(null);
      setShowConfirm(false);
      loadBooks(); // 重新加载图书列表
    } catch (err) {
      setMessage({type: 'error', text: '删除失败，可能是图书已被借出或有其他关联记录'});
    } finally {
      setLoading(false);
    }
  };

  const handleCancelDelete = () => {
    setShowConfirm(false);
  };

  return (
      <div className="flex">
        <div className="flex-1 bg-white shadow-md rounded-md p-6">
          <h2 className="text-lg font-bold mb-4">删除图书</h2>

          {message && (
              <div className={`p-4 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                {message.text}
              </div>
          )}

          <div className="mb-6">
            <label className="block mb-2 font-medium">搜索并选择要删除的图书</label>

            <div className="flex mb-2">
              <select
                  name="searchField"
                  value={searchCriteria.searchField}
                  onChange={handleSearchChange}
                  className="border border-gray-300 rounded-l p-2"
              >
                <option value="id">ID</option>
                <option value="title">书名</option>
                <option value="isbn">ISBN</option>
                <option value="author">作者</option>
                <option value="publisher">出版社</option>
              </select>
              <input
                  name="keyword"
                  value={searchCriteria.keyword}
                  onChange={handleSearchChange}
                  className="flex-1 border border-gray-300 p-2 rounded-r"
                  placeholder={searchCriteria.searchField === 'id' ? "输入图书ID..." : "输入关键词搜索图书..."}
                  onFocus={() => setDropdownOpen(true)}
                  onBlur={handleSearchBlur}
                  disabled={loading}
              />
            </div>

            {dropdownOpen && filteredBooks.length > 0 && (
                <div className="relative" ref={dropdownRef}>
                  <div 
                    className="absolute z-10 w-full bg-white border border-gray-300 rounded shadow-lg overflow-y-auto"
                    style={{ maxHeight: '360px' }} // 设置为4.5本图书的高度（每本约75px）
                    onMouseEnter={handleDropdownMouseEnter}
                    onMouseLeave={handleDropdownMouseLeave}
                  >
                    {filteredBooks.map(book => (
                        <div
                            key={book.id}
                            className="p-2 hover:bg-gray-100 cursor-pointer border-b border-gray-200"
                            onClick={() => handleBookSelect(book)}
                        >
                          <div className="font-medium">
                            <span className="text-gray-500 mr-2">ID: {book.id}</span>
                            {book.title}
                          </div>
                          <div className="text-sm text-gray-600">
                            {book.author} | ISBN: {book.isbn} | {book.publisher}
                          </div>
                          {book.updatedAt && (
                              <div className="text-xs text-gray-500">
                                更新时间: {new Date(book.updatedAt).toLocaleString()}
                              </div>
                          )}
                        </div>
                    ))}
                  </div>
                </div>
            )}

            {filteredBooks.length === 0 && searchCriteria.keyword && (
                <div className="text-gray-500 mt-1">未找到匹配的图书</div>
            )}

            {selectedBook && selectedBook.id && (
                <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
                  <span className="text-blue-700">已选择图书ID: {selectedBook.id}</span>
                </div>
            )}

            {selectedBook && !showConfirm && (
                <div className="p-4 border rounded bg-gray-50 mb-4 mt-4">
                  <h3 className="font-medium mb-2">图书详情</h3>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    <div><span className="font-medium">ISBN:</span> {selectedBook.isbn}</div>
                    <div><span className="font-medium">书名:</span> {selectedBook.title}</div>
                    <div><span className="font-medium">作者:</span> {selectedBook.author}</div>
                    <div><span className="font-medium">出版社:</span> {selectedBook.publisher}</div>
                    <div><span className="font-medium">状态:</span> {selectedBook.status}</div>
                    <div><span className="font-medium">定价:</span> ¥{Number(selectedBook.price).toFixed(2)}</div>
                  </div>
                  <button
                      onClick={handleDeleteClick}
                      className="mt-4 bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
                      disabled={loading}
                  >
                    删除此图书
                  </button>
                </div>
            )}

            {showConfirm && (
                <div className="p-4 border border-red-300 rounded bg-red-50 mb-4">
                  <h3 className="font-medium text-red-600 mb-2">确认删除</h3>
                  <p className="mb-4">您确定要删除图书 <strong>"{selectedBook?.title}"</strong> 吗？此操作不可撤销。</p>
                  <div className="flex space-x-4">
                    <button
                        onClick={handleConfirmDelete}
                        className="bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
                        disabled={loading}
                    >
                      {loading ? '处理中...' : '确认删除'}
                    </button>
                    <button
                        onClick={handleCancelDelete}
                        className="bg-gray-300 text-gray-800 px-4 py-2 rounded hover:bg-gray-400"
                        disabled={loading}
                    >
                      取消
                    </button>
                  </div>
                </div>
            )}
          </div>
        </div>
        <div className="w-1/4 ml-6">
          <div className="bg-white shadow-md rounded-md p-6">
            <h2 className="text-xl font-bold mb-4">删除说明</h2>
            <ul className="list-disc pl-5 space-y-2 text-gray-700">
              <li>通过搜索框查找并选择要删除的图书</li>
              <li>可以按书名、ISBN、作者或出版社搜索</li>
              <li>删除前请确认图书未被借出</li>
              <li>删除操作不可恢复，请谨慎操作</li>
            </ul>
          </div>
        </div>
      </div>
  );
};

export default DeleteBookForm;
