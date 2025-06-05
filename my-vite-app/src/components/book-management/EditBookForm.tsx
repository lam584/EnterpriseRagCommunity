// src/components/book-management/EditBookForm.tsx 搜索选择改进版
import React, { useState, useEffect, useRef } from 'react';
import { fetchCategories, CategoryDTO } from '../../services/categoryService';
import { fetchShelves, ShelfDTO } from '../../services/shelfService';
import { fetchBookById, updateBook, BookDTO, fetchBooks } from '../../services/bookService';

// 扩展 BookDTO 接口，确保包含时间字段
interface ExtendedBookDTO extends BookDTO {
  createdAt?: string;
  updatedAt?: string;
}

// 添加一个带有索引签名的接口，用于替代不明确的 any 类型
interface FormWithNamedFields extends BookDTO {
  [key: string]: string | number | boolean | { id: number } | undefined;
}

const EditBookForm: React.FC = () => {
  const [books, setBooks] = useState<ExtendedBookDTO[]>([]);
  const [filteredBooks, setFilteredBooks] = useState<ExtendedBookDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [shelves, setShelves] = useState<ShelfDTO[]>([]);
  const [selectedBookId, setSelectedBookId] = useState<string>('');
  const [form, setForm] = useState<BookDTO>({ isbn:'', title:'', author:'', publisher:'', edition:'', price:'0', category:{id:0}, shelf:{id:0}, status:'可借阅', printTimes:'' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);

  // 搜索条件
  const [searchCriteria, setSearchCriteria] = useState({
    keyword: '',
    searchField: 'title' // 默认按书名搜索
  });

  // 下拉菜单是否展开
  const [dropdownOpen, setDropdownOpen] = useState(false);
  // 最大显示数量
  const MAX_RECENT_BOOKS = 10;

  // 添加一个引用来追踪下拉框状态
  const dropdownRef = useRef<HTMLDivElement>(null);
  // 添加一个定时器引用
  const timeoutRef = useRef<number | null>(null);
  // 标记鼠标是否在下拉框内
  const [isMouseInDropdown, setIsMouseInDropdown] = useState(false);

  useEffect(() => {
    // 加载所有选项数据
    Promise.all([
      fetchCategories().catch(() => []),
      fetchShelves().catch(() => []),
      fetchBooks().catch(() => [])
    ]).then(([categoriesData, shelvesData, booksData]) => {
      setCategories(categoriesData);
      setCategories(categoriesData);
      setShelves(shelvesData);
      setBooks(booksData);

      // 默认展示最近更新的图书
      const sortedBooks = [...booksData].sort((a, b) => {
        const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return dateB - dateA; // 降序排列，最新的在前面
      }).slice(0, MAX_RECENT_BOOKS);

      setFilteredBooks(sortedBooks);
      // setDropdownOpen(true); // 移除页面加载时自动展开下拉框
    });
  }, []);

  // 当搜索条件变化时，过滤图书
  useEffect(() => {
    if (!searchCriteria.keyword.trim()) {
      // 只更新列表，不自动展开
      const recentBooks = [...books]
          .sort((a, b) => {
            const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
            const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
            return dateB - dateA;
          })
          .slice(0, MAX_RECENT_BOOKS);
      setFilteredBooks(recentBooks);
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
    // 只在有关键词时展开
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
    // 延迟关闭下拉框，让用户有时间点击选项
    timeoutRef.current = window.setTimeout(() => {
      if (!isMouseInDropdown) {
        setDropdownOpen(false);
      }
    }, 200); // 200毫秒延迟
  };

  // 当鼠标进入下拉框时取消定时器，并标记在下拉框内
  const handleDropdownMouseEnter = () => {
    setIsMouseInDropdown(true);
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  };

  // 当鼠标离开下拉框时，标记不在下拉框内，但不立即关闭
  const handleDropdownMouseLeave = () => {
    setIsMouseInDropdown(false);
    // 不再自动关闭下拉框，交由 onBlur 控制
  };

  const handleBookSelect = async (bookId: number) => {
    setSelectedBookId(String(bookId));
    setDropdownOpen(false);
    setLoading(true);
    setMessage(null);

    try {
      const book = await fetchBookById(bookId);
      setForm(book);
      setErrors({});
    } catch {
      setMessage({type: 'error', text: '加载图书信息失败'});
    } finally {
      setLoading(false);
    }
  };

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!form.isbn) newErrors.isbn = 'ISBN不能为空';
    if (!form.title) newErrors.title = '书名不能为空';
    if (!form.author) newErrors.author = '作者不能为空';
    if (!form.publisher) newErrors.publisher = '出版社不能为空';
    if (!form.category.id) newErrors.category = '必须选择分类';
    if (!form.shelf.id) newErrors.shelf = '必须选择书架';
    if (Number(form.price) <= 0) newErrors.price = '价格必须大于0';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    if (['category','shelf'].includes(name)) {
      const id = Number(value);
      setForm(f => ({ ...f, [name]: { id } } as FormWithNamedFields));
    } else if (name === 'price') {
      setForm(f => ({ ...f, price: value }));
    } else {
      setForm(f => ({ ...f, [name]: value } as FormWithNamedFields));
    }

    if (errors[name]) {
      setErrors(prev => {
        const newErrors = {...prev};
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || !form.id) return;

    setLoading(true);
    setMessage(null);

    try {
      await updateBook(form.id, form);
      setMessage({type: 'success', text: '图书信息更新成功！'});

      const updatedBooks = await fetchBooks();
      setBooks(updatedBooks);
      setFilteredBooks(updatedBooks);
    } catch {
      setMessage({type: 'error', text: '更新失败，请检查填写内容或稍后重试'});
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="flex">
        <div className="flex-1 bg-white shadow-md rounded-md p-6">
          <h2 className="text-lg font-bold mb-4">编辑图书</h2>

          {message && (
              <div className={`p-4 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                {message.text}
              </div>
          )}

          {/* 搜索图书部分 */}
          <div className="mb-6">
            <label className="block mb-1">搜索并选择要编辑的图书</label>
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
                      style={{ maxHeight: '360px' }} // 设置为4.5本图书的高度（每本约50px）
                      onMouseEnter={handleDropdownMouseEnter}
                      onMouseLeave={handleDropdownMouseLeave}
                  >
                    {filteredBooks.map(book => (
                        <div
                            key={book.id}
                            className="p-2 hover:bg-gray-100 cursor-pointer border-b border-gray-200"
                            onClick={() => book.id && handleBookSelect(book.id)}
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

            {selectedBookId && (
                <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
                  <span className="text-blue-700">已选择图书ID: {selectedBookId}</span>
                </div>
            )}
          </div>

          {selectedBookId && (
              <form className="space-y-4" onSubmit={handleSubmit}>
                <div className="grid grid-cols-2 gap-4">
                  {/* ISBN */}
                  <div>
                    <label className="block mb-1">ISBN号</label>
                    <input
                        name="isbn"
                        value={form.isbn}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.isbn ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.isbn && <p className="text-red-500 text-sm mt-1">{errors.isbn}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">书名</label>
                    <input
                        name="title"
                        value={form.title}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.title ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.title && <p className="text-red-500 text-sm mt-1">{errors.title}</p>}
                  </div>

                  {/* 其他字段保持不变 */}
                  <div>
                    <label className="block mb-1">作者</label>
                    <input
                        name="author"
                        value={form.author}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.author ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.author && <p className="text-red-500 text-sm mt-1">{errors.author}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">出版社</label>
                    <input
                        name="publisher"
                        value={form.publisher}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.publisher ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.publisher && <p className="text-red-500 text-sm mt-1">{errors.publisher}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">版次</label>
                    <input
                        name="edition"
                        value={form.edition}
                        onChange={handleChange}
                        className="w-full border border-gray-300 p-2 rounded"
                    />
                  </div>

                  <div>
                    <label className="block mb-1">价格</label>
                    <input
                        name="price"
                        type="number"
                        value={form.price}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.price ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.price && <p className="text-red-500 text-sm mt-1">{errors.price}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">分类</label>
                    <select
                        name="category"
                        value={form.category.id || ''}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.category ? 'border-red-500' : 'border-gray-300'}`}
                    >
                      <option value="">请选择分类...</option>
                      {categories.map(category => (
                          <option key={category.id} value={category.id}>
                            {category.name}
                          </option>
                      ))}
                    </select>
                    {errors.category && <p className="text-red-500 text-sm mt-1">{errors.category}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">书架</label>
                    <select
                        name="shelf"
                        value={form.shelf.id || ''}
                        onChange={handleChange}
                        className={`w-full border p-2 rounded ${errors.shelf ? 'border-red-500' : 'border-gray-300'}`}
                    >
                      <option value="">请选择书架...</option>
                      {shelves.map(shelf => (
                          <option key={shelf.id} value={shelf.id}>
                            {shelf.shelfCode} ({shelf.locationDescription})
                          </option>
                      ))}
                    </select>
                    {errors.shelf && <p className="text-red-500 text-sm mt-1">{errors.shelf}</p>}
                  </div>

                  <div>
                    <label className="block mb-1">状态</label>
                    <select
                        name="status"
                        value={form.status}
                        onChange={handleChange}
                        className="w-full border border-gray-300 p-2 rounded"
                    >
                      <option value="可借阅">可借阅</option>
                      <option value="已借出">已借出</option>
                      <option value="维修中">维修中</option>
                      <option value="已报废">已报废</option>
                    </select>
                  </div>

                  <div>
                    <label className="block mb-1">印次</label>
                    <input
                        name="printTimes"
                        value={form.printTimes}
                        onChange={handleChange}
                        className="w-full border border-gray-300 p-2 rounded"
                    />
                  </div>
                </div>
                <button
                    type="submit"
                    disabled={loading}
                    className={`bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  {loading ? '更新中...' : '保存修改'}
                </button>
              </form>
          )}
        </div>
        <div className="w-1/4 ml-6">
          <div className="bg-white shadow-md rounded-md p-6">
            <h2 className="text-xl font-bold mb-4">编辑指南</h2>
            <ul className="list-disc pl-5 space-y-2 text-gray-700">
              <li>使用搜索框查找并选择要编辑的图书</li>
              <li>可以按ID、书名、ISBN、作者或出版社搜索</li>
              <li>修改相关信���后点击保存</li>
              <li>ISBN、书名、作者等为必填项</li>
            </ul>
          </div>
        </div>
      </div>
  );
}

export default EditBookForm;

