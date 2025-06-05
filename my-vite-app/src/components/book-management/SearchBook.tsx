// src/components/book-management/SearchBook.tsx 改进版
import React, { useState, useEffect } from 'react';
import { fetchBooks, advancedSearch, BookDTO, AdvancedSearchCriteria } from '../../services/bookService';

const SearchBook: React.FC = () => {
  // 基本搜索状态
  const [criteria, setCriteria] = useState({ id: '', isbn: '', title: '', author: '', publisher: '' });
  // 添加错误状态
  const [errors, setErrors] = useState({ id: '' });

  // 高级搜索状态
  const [advCriteria, setAdvCriteria] = useState<AdvancedSearchCriteria>({
    id: '',
    idExact: false,
    isbn: '',
    isbnExact: false,
    title: '',
    titleExact: false,
    author: '',
    authorExact: false,
    publisher: '',
    publisherExact: false,
    edition: '',
    editionExact: false,
    category: '',
    categoryExact: false,
    shelvesCode: '',
    shelvesCodeExact: false,
    priceMin: undefined,
    priceMax: undefined,
    printTimes: '',
    printTimesExact: false,
    status: ''
  });

  const [showAdvancedSearch, setShowAdvancedSearch] = useState<boolean>(false);
  const [searchResults, setSearchResults] = useState<BookDTO[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [totalPages, setTotalPages] = useState<number>(1);
  const itemsPerPage = 10;

  // 处理基本搜索输入变化
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setCriteria(prev => ({ ...prev, [name]: value }));
  };

  // 处理高级搜索输入变化
  const handleAdvancedInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setAdvCriteria(prev => ({ ...prev, [name]: value }));
  };

  // 处理高级搜索复选框变化
  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, checked } = e.target;
    setAdvCriteria(prev => ({ ...prev, [name]: checked }));
  };

  // 清空基本搜索条件
  const handleClearCriteria = () => {
    setCriteria({ id: '', isbn: '', title: '', author: '', publisher: '' });
    setSearchResults([]);
    setMessage('');
  };

  // 清空高级搜索条件
  const handleClearAdvancedCriteria = () => {
    setAdvCriteria({
      id: '',
      idExact: false,
      isbn: '',
      isbnExact: false,
      title: '',
      titleExact: false,
      author: '',
      authorExact: false,
      publisher: '',
      publisherExact: false,
      edition: '',
      editionExact: false,
      category: '',
      categoryExact: false,
      shelvesCode: '',
      shelvesCodeExact: false,
      priceMin: undefined,
      priceMax: undefined,
      printTimes: '',
      printTimesExact: false,
      status: ''
    });
    setSearchResults([]);
    setMessage('');
  };

  // 基本搜索处理
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setCurrentPage(1); // 重置到第一页

    try {
      // 检查是否至少有一个搜索条件
      if (!criteria.id && !criteria.isbn && !criteria.title && !criteria.author && !criteria.publisher) {
        setMessage('请至少输入一个搜索条件');
        setSearchResults([]);
        setLoading(false);
        return;
      }

      // 验证ID格式
      if (criteria.id && !/^\d+$/.test(criteria.id)) {
        setMessage('图书ID必须是数字');
        setSearchResults([]);
        setLoading(false);
        return;
      }

      // 创建一个新的搜索条件对象，将id转换为number类型
      const searchCriteria: Partial<BookDTO> = {
        isbn: criteria.isbn,
        title: criteria.title,
        author: criteria.author,
        publisher: criteria.publisher
      };
      
      // 如果id不为空，则转换为数字
      if (criteria.id) {
        searchCriteria.id = parseInt(criteria.id);
      }

      const result = await fetchBooks(searchCriteria);
      setSearchResults(result);
      setTotalPages(Math.ceil(result.length / itemsPerPage));

      if (result.length === 0) {
        setMessage('未找到匹配的图书');
      }
    } catch {
      setMessage('搜索过程中发生错误');
      setSearchResults([]);
    } finally {
      setLoading(false);
    }
  };

  // 高级搜索处理
  const handleAdvancedSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setCurrentPage(1); // 重置到第一页

    try {
      // 检查是否至少有一个搜索条件
      const hasAnyCriteria = Object.entries(advCriteria).some(([key, value]) => {
        return key !== 'idExact' &&
               key !== 'isbnExact' &&
               key !== 'titleExact' &&
               key !== 'authorExact' &&
               key !== 'publisherExact' &&
               key !== 'editionExact' &&
               key !== 'categoryExact' &&
               key !== 'shelvesCodeExact' &&
               key !== 'printTimesExact' &&
               value !== '' &&
               value !== undefined &&
               value !== false;
      });

      if (!hasAnyCriteria) {
        setMessage('请至少输入一个搜索条件');
        setSearchResults([]);
        setLoading(false);
        return;
      }

      // 验证ID格式
      if (advCriteria.id && !/^\d+$/.test(advCriteria.id)) {
        setMessage('图书ID必须是数字');
        setSearchResults([]);
        setLoading(false);
        return;
      }

      const result = await advancedSearch(advCriteria);
      setSearchResults(result);
      setTotalPages(Math.ceil(result.length / itemsPerPage));

      if (result.length === 0) {
        setMessage('未找到匹配的图书');
      }
    } catch {
      setMessage('搜索过程中发生错误');
      setSearchResults([]);
    } finally {
      setLoading(false);
    }
  };

  // 获取当前页数据
  const getCurrentPageData = () => {
    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    return searchResults.slice(startIndex, endIndex);
  };

  // 翻页
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  // 导出搜索结果为CSV
  const handleExport = () => {
    if (searchResults.length === 0) return;

    const headers = ['ID','标题','作者','出版社','ISBN号','版次','定价','分类','书架','状态','印次'];
    const csvRows = [
      headers.join(','),
      ...searchResults.map(book => [
        book.id,
        `"${book.title}"`,  // 加引号避免逗号问题
        `"${book.author}"`,
        `"${book.publisher}"`,
        book.isbn,
        book.edition,
        book.price,
        book.category?.name || '',
        book.shelf?.shelfCode || '',
        book.status,
        book.printTimes
      ].join(','))
    ];

    const csvContent = csvRows.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `图书搜索结果_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // 切换高级搜索/基础搜索
  const toggleAdvancedSearch = () => {
    setShowAdvancedSearch(!showAdvancedSearch);
    // 清空搜索结果和消息
    if (searchResults.length > 0) {
      setSearchResults([]);
      setMessage('');
    }
  };

  // 表单验证
  useEffect(() => {
    const newErrors = { id: '' };

    // 基本搜索ID验证
    if (criteria.id && !/^\d+$/.test(criteria.id)) {
      newErrors.id = '图书ID必须是数字';
    }

    setErrors(newErrors);
  }, [criteria.id]);

  return (
      <div className="space-y-6">
        {/* 搜索表单 */}
        <div className="max-w-7xl mx-auto p-6 bg-white shadow-md rounded-md">
          <div className="flex justify-between items-center mb-6">
            <h1 className="text-2xl font-bold">查找图书</h1>
            <button
              onClick={toggleAdvancedSearch}
              className="text-blue-600 hover:text-blue-800"
            >
              {showAdvancedSearch ? '切换到基础搜索' : '切换到高级搜索'}
            </button>
          </div>

          {!showAdvancedSearch ? (
            // 基础搜索表单
            <form onSubmit={handleSearch} className="grid grid-cols-2 gap-6">
              <div>
                <label className="block text-gray-700 mb-2">图书ID</label>
                <input
                    name="id"
                    value={criteria.id}
                    onChange={handleInputChange}
                    className="w-full border p-2 rounded"
                    placeholder="输入图书ID"
                />
                {errors.id && <p className="text-red-500 text-sm mt-1">{errors.id}</p>}
              </div>
              <div>
                <label className="block text-gray-700 mb-2">ISBN号</label>
                <input
                    name="isbn"
                    value={criteria.isbn}
                    onChange={handleInputChange}
                    className="w-full border p-2 rounded"
                    placeholder="输入ISBN号"
                />
              </div>
              <div>
                <label className="block text-gray-700 mb-2">书名</label>
                <input
                    name="title"
                    value={criteria.title}
                    onChange={handleInputChange}
                    className="w-full border p-2 rounded"
                    placeholder="输入书名关键词"
                />
              </div>
              <div>
                <label className="block text-gray-700 mb-2">作者</label>
                <input
                    name="author"
                    value={criteria.author}
                    onChange={handleInputChange}
                    className="w-full border p-2 rounded"
                    placeholder="输入作者名"
                />
              </div>
              <div>
                <label className="block text-gray-700 mb-2">出版社</label>
                <input
                    name="publisher"
                    value={criteria.publisher}
                    onChange={handleInputChange}
                    className="w-full border p-2 rounded"
                    placeholder="输入出版社名称"
                />
              </div>

              {/* 按钮区 */}
              <div className="col-span-2 flex space-x-4">
                <button
                    type="submit"
                    disabled={loading}
                    className={`bg-green-500 text-white px-6 py-2 rounded-md hover:bg-green-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  {loading ? '搜索中...' : '搜索'}
                </button>
                <button
                    type="button"
                    onClick={handleClearCriteria}
                    className="bg-gray-300 text-gray-700 px-6 py-2 rounded-md hover:bg-gray-400"
                >
                  清空条件
                </button>
              </div>
            </form>
          ) : (
            // 高级搜索表单
            <form onSubmit={handleAdvancedSearch} className="grid grid-cols-2 gap-4">
              {/* ID字段 */}
              <div>
                <label htmlFor="id" className="block">图书ID</label>
                <input
                  type="text"
                  id="id"
                  name="id"
                  value={advCriteria.id || ''}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="idExact"
                    name="idExact"
                    checked={advCriteria.idExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="idExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* ISBN */}
              <div>
                <label htmlFor="isbn" className="block">ISBN号</label>
                <input
                  type="text"
                  id="isbn"
                  name="isbn"
                  value={advCriteria.isbn}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="isbnExact"
                    name="isbnExact"
                    checked={advCriteria.isbnExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="isbnExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 书名 */}
              <div>
                <label htmlFor="title" className="block">书名</label>
                <input
                  type="text"
                  id="title"
                  name="title"
                  value={advCriteria.title}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="titleExact"
                    name="titleExact"
                    checked={advCriteria.titleExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="titleExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 作者 */}
              <div>
                <label htmlFor="author" className="block">作者</label>
                <input
                  type="text"
                  id="author"
                  name="author"
                  value={advCriteria.author}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="authorExact"
                    name="authorExact"
                    checked={advCriteria.authorExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="authorExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 出版社 */}
              <div>
                <label htmlFor="publisher" className="block">出版社</label>
                <input
                  type="text"
                  id="publisher"
                  name="publisher"
                  value={advCriteria.publisher}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="publisherExact"
                    name="publisherExact"
                    checked={advCriteria.publisherExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="publisherExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 版次 */}
              <div>
                <label htmlFor="edition" className="block">版次</label>
                <input
                  type="text"
                  id="edition"
                  name="edition"
                  value={advCriteria.edition}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="editionExact"
                    name="editionExact"
                    checked={advCriteria.editionExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="editionExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 分类 */}
              <div>
                <label htmlFor="category" className="block">分类</label>
                <input
                  type="text"
                  id="category"
                  name="category"
                  value={advCriteria.category}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="categoryExact"
                    name="categoryExact"
                    checked={advCriteria.categoryExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="categoryExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 书架编号 */}
              <div>
                <label htmlFor="shelvesCode" className="block">书架编号</label>
                <input
                  type="text"
                  id="shelvesCode"
                  name="shelvesCode"
                  value={advCriteria.shelvesCode}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="shelvesCodeExact"
                    name="shelvesCodeExact"
                    checked={advCriteria.shelvesCodeExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="shelvesCodeExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 最低价格 */}
              <div>
                <label htmlFor="priceMin" className="block">最低价格</label>
                <input
                  type="number"
                  step="0.01"
                  id="priceMin"
                  name="priceMin"
                  value={advCriteria.priceMin || ''}
                  onChange={(e) => setAdvCriteria({...advCriteria, priceMin: e.target.value ? parseFloat(e.target.value) : undefined})}
                  className="mt-1 p-2 w-full border rounded"
                />
              </div>

              {/* 最高价格 */}
              <div>
                <label htmlFor="priceMax" className="block">最高价格</label>
                <input
                  type="number"
                  step="0.01"
                  id="priceMax"
                  name="priceMax"
                  value={advCriteria.priceMax || ''}
                  onChange={(e) => setAdvCriteria({...advCriteria, priceMax: e.target.value ? parseFloat(e.target.value) : undefined})}
                  className="mt-1 p-2 w-full border rounded"
                />
              </div>

              {/* 印次 */}
              <div>
                <label htmlFor="printTimes" className="block">印次</label>
                <input
                  type="text"
                  id="printTimes"
                  name="printTimes"
                  value={advCriteria.printTimes}
                  onChange={handleAdvancedInputChange}
                  className="mt-1 p-2 w-full border rounded"
                />
                <div className="mt-1">
                  <input
                    type="checkbox"
                    id="printTimesExact"
                    name="printTimesExact"
                    checked={advCriteria.printTimesExact}
                    onChange={handleCheckboxChange}
                    className="mr-2"
                  />
                  <label htmlFor="printTimesExact" className="inline">精确搜索</label>
                </div>
              </div>

              {/* 状态 */}
              <div>
                <label htmlFor="status" className="block">状态</label>
                <select
                  id="status"
                  name="status"
                  value={advCriteria.status}
                  onChange={handleAdvancedInputChange}
                  className="border border-gray-300 rounded px-3 py-2 mt-1 focus:outline-none focus:border-blue-500 w-full"
                >
                  <option value="">全部状态</option>
                  <option value="可借阅">可借阅</option>
                  <option value="借阅中">借阅中</option>
                  <option value="禁止借阅">禁止借阅</option>
                </select>
              </div>

              {/* 按钮区 */}
              <div className="col-span-2 flex space-x-4 mt-4">
                <button
                  type="submit"
                  disabled={loading}
                  className={`bg-green-500 text-white px-6 py-2 rounded-md hover:bg-green-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  {loading ? '搜索中...' : '高级搜索'}
                </button>
                <button
                  type="button"
                  onClick={handleClearAdvancedCriteria}
                  className="bg-gray-300 text-gray-700 px-6 py-2 rounded-md hover:bg-gray-400"
                >
                  清空条件
                </button>
              </div>
            </form>
          )}
        </div>

        {/* 搜索结果 */}
        <div className="max-w-7xl mx-auto p-6 bg-white shadow-md rounded-md">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-bold">搜索结果</h2>
            {searchResults.length > 0 && (
                <button
                    onClick={handleExport}
                    className="bg-blue-500 text-white px-4 py-1 rounded-md hover:bg-blue-600 text-sm"
                >
                  导出CSV
                </button>
            )}
          </div>

          {message && (
              <div className="p-4 mb-4 bg-blue-100 text-blue-800 rounded">
                {message}
              </div>
          )}

          {searchResults.length > 0 ? (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full border border-gray-300 text-left">
                    <thead>
                    <tr className="bg-gray-100">
                      <th className="border p-2">ID</th>
                      <th className="border p-2">标题</th>
                      <th className="border p-2">作者</th>
                      <th className="border p-2">��版社</th>
                      <th className="border p-2">ISBN号</th>
                      <th className="border p-2">版次</th>
                      <th className="border p-2">定价</th>
                      <th className="border p-2">分类</th>
                      <th className="border p-2">书架</th>
                      <th className="border p-2">状态</th>
                      <th className="border p-2">印次</th>
                    </tr>
                    </thead>
                    <tbody>
                    {getCurrentPageData().map((book) => (
                        <tr key={book.id} className="hover:bg-gray-50">
                          <td className="border p-2">{book.id}</td>
                          <td className="border p-2">{book.title}</td>
                          <td className="border p-2">{book.author}</td>
                          <td className="border p-2">{book.publisher}</td>
                          <td className="border p-2">{book.isbn}</td>
                          <td className="border p-2">{book.edition}</td>
                          <td className="border p-2">{Number(book.price).toFixed(2)}</td>
                          <td className="border p-2">{book.category?.name}</td>
                          <td className="border p-2">{book.shelf?.shelfCode}</td>
                          <td className="border p-2">
                        <span className={`px-2 py-1 rounded text-sm ${book.status === '可借阅' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                          {book.status}
                        </span>
                          </td>
                          <td className="border p-2">{book.printTimes}</td>
                        </tr>
                    ))}
                    </tbody>
                  </table>
                </div>

                {/* 分页 */}
                {totalPages > 1 && (
                    <div className="flex justify-center mt-4">
                      <div className="flex space-x-1">
                        <button
                            onClick={() => handlePageChange(currentPage - 1)}
                            disabled={currentPage === 1}
                            className={`px-3 py-1 rounded ${currentPage === 1 ? 'bg-gray-200 text-gray-500' : 'bg-gray-300 hover:bg-gray-400 text-gray-800'}`}
                        >
                          上一页
                        </button>

                        {/* 页码按钮 */}
                        {[...Array(totalPages)].map((_, i) => (
                            <button
                                key={i}
                                onClick={() => handlePageChange(i + 1)}
                                className={`px-3 py-1 rounded ${currentPage === i + 1 ? 'bg-green-500 text-white' : 'bg-gray-300 hover:bg-gray-400 text-gray-800'}`}
                            >
                              {i + 1}
                            </button>
                        ))}

                        <button
                            onClick={() => handlePageChange(currentPage + 1)}
                            disabled={currentPage === totalPages}
                            className={`px-3 py-1 rounded ${currentPage === totalPages ? 'bg-gray-200 text-gray-500' : 'bg-gray-300 hover:bg-gray-400 text-gray-800'}`}
                        >
                          下一页
                        </button>
                      </div>
                    </div>
                )}
              </>
          ) : (
              <p className="text-gray-500 text-center py-4">
                {loading ? '搜索中...' : '无搜索结果'}
              </p>
          )}
        </div>
      </div>
  );
};

export default SearchBook;
