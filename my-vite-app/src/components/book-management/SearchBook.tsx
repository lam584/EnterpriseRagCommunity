// src/components/book-management/SearchBook.tsx 改进版
import React, { useState, useEffect } from 'react';
import { FaBarcode } from 'react-icons/fa';
import { fetchBooks, advancedSearch, BookDTO, AdvancedSearchCriteria } from '../../services/bookService';
// 添加图标库
import { FaSearch, FaFilter, FaFileCsv, FaArrowLeft, FaArrowRight, FaBook, FaSyncAlt, FaTimes, FaInfoCircle } from 'react-icons/fa';

// 为基础搜索条件声明一个接口，方便 useState 推断类型
interface BasicCriteria {
  id: string;
  isbn: string;
  title: string;
  author: string;
  publisher: string;
}

const SearchBook: React.FC = () => {
  // 基本搜索状态 - 添加类型声明，解决 "Parameter 'prev' implicitly has an 'any' type" 错误
  const [criteria, setCriteria] = useState<BasicCriteria>({ id: '', isbn: '', title: '', author: '', publisher: '' });
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
  // 添加搜索请求状态
  const [isSearched, setIsSearched] = useState<boolean>(false);
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
    if (isSearched) {
      setSearchResults([]);
      setMessage('');
      setIsSearched(false);
    }
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
    if (isSearched) {
      setSearchResults([]);
      setMessage('');
      setIsSearched(false);
    }
  };

  // 基本搜索处理
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setCurrentPage(1); // 重置到第一页
    setIsSearched(true);

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
    setIsSearched(true);

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
    // 滚动到结果顶部
    document.getElementById('search-results')?.scrollIntoView({ behavior: 'smooth' });
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
      setIsSearched(false);
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

  // 生成页码数组，包含省略号
  const getPaginationRange = () => {
    const delta = 2; // 当前页前后显示的页数
    const range = [];

    // 显示第一页
    range.push(1);

    // 计算当前页码的前后范围
    const rangeStart = Math.max(2, currentPage - delta);
    const rangeEnd = Math.min(totalPages - 1, currentPage + delta);

    // 添加前省略号
    if (rangeStart > 2) {
      range.push('...');
    }

    // 添加中间页码
    for (let i = rangeStart; i <= rangeEnd; i++) {
      range.push(i);
    }

    // 添加后省略号
    if (rangeEnd < totalPages - 1) {
      range.push('...');
    }

    // 添加最后一页
    if (totalPages > 1) {
      range.push(totalPages);
    }

    return range;
  };

  return (
      <div className="flex flex-col md:flex-row gap-6 animate-fadeIn">
        {/* 左侧搜索表单和结果 */}
        <div className="md:w-3/4 space-y-6">
          {/* 搜索表单 */}
          <div className="bg-white shadow-lg rounded-lg p-6 border-t-4 border-green-500 transition-all duration-300 hover:shadow-xl">
            <div className="flex justify-between items-center mb-6">
              <h1 className="text-2xl font-bold flex items-center text-gray-800">
                <FaBook className="mr-2 text-green-500" /> 图书检索
              </h1>
              <button
                onClick={toggleAdvancedSearch}
                className="flex items-center text-blue-600 hover:text-blue-800 transition-colors duration-200 bg-blue-50 hover:bg-blue-100 px-4 py-2 rounded-full text-sm"
              >
                {showAdvancedSearch ? (
                  <>
                    <FaSearch className="mr-2" />
                    切换到基础搜索
                  </>
                ) : (
                  <>
                    <FaFilter className="mr-2" />
                    切换到高级搜索
                  </>
                )}
              </button>
            </div>

            {!showAdvancedSearch ? (
              // 基础搜索表单
              <form onSubmit={handleSearch} className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="group">
                  <label className="block text-gray-700 mb-2 font-medium">图书ID</label>
                  <div className="relative">
                    <input
                      name="id"
                      value={criteria.id}
                      onChange={handleInputChange}
                      className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200 pl-10"
                      placeholder="输入图书ID"
                    />
                    <span className="absolute left-3 top-2.5 text-gray-400 transition-all duration-200 group-hover:text-green-500">
                      #
                    </span>
                  </div>
                  {errors.id && <p className="text-red-500 text-sm mt-1 animate-fadeIn">{errors.id}</p>}
                </div>
                <div className="group">
                  <label className="block text-gray-700 mb-2 font-medium">ISBN号</label>
                  <div className="relative">
                    <input
                      name="isbn"
                      value={criteria.isbn}
                      onChange={handleInputChange}
                      className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200 pl-10"
                      placeholder="输入ISBN号"
                    />
                    <span className="absolute left-3 top-2.5 text-gray-400 transition-all duration-200 group-hover:text-green-500">
                      <FaBarcode />
                    </span>
                  </div>
                </div>
                <div className="group">
                  <label className="block text-gray-700 mb-2 font-medium">书名</label>
                  <div className="relative">
                    <input
                      name="title"
                      value={criteria.title}
                      onChange={handleInputChange}
                      className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200 pl-10"
                      placeholder="输入书名关键词"
                    />
                    <span className="absolute left-3 top-2.5 text-gray-400 transition-all duration-200 group-hover:text-green-500">
                      Aa
                    </span>
                  </div>
                </div>
                <div className="group">
                  <label className="block text-gray-700 mb-2 font-medium">作者</label>
                  <div className="relative">
                    <input
                      name="author"
                      value={criteria.author}
                      onChange={handleInputChange}
                      className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200 pl-10"
                      placeholder="输入作者名"
                    />
                    <span className="absolute left-3 top-2.5 text-gray-400 transition-all duration-200 group-hover:text-green-500">
                      @
                    </span>
                  </div>
                </div>
                <div className="group">
                  <label className="block text-gray-700 mb-2 font-medium">出版社</label>
                  <div className="relative">
                    <input
                      name="publisher"
                      value={criteria.publisher}
                      onChange={handleInputChange}
                      className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200 pl-10"
                      placeholder="输入出版社名称"
                    />
                    <span className="absolute left-3 top-2.5 text-gray-400 transition-all duration-200 group-hover:text-green-500">
                      &copy;
                    </span>
                  </div>
                </div>

                {/* 按钮区 */}
                <div className="md:col-span-2 flex flex-wrap gap-4 mt-2">
                  <button
                    type="submit"
                    disabled={loading}
                    className={`flex-1 md:flex-none flex items-center justify-center bg-gradient-to-r from-green-500 to-green-600 hover:from-green-600 hover:to-green-700 text-white px-8 py-2.5 rounded-md transition-all duration-300 shadow hover:shadow-lg ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                  >
                    {loading ? (
                      <>
                        <FaSyncAlt className="animate-spin mr-2" /> 搜索中...
                      </>
                    ) : (
                      <>
                        <FaSearch className="mr-2" /> 搜索
                      </>
                    )}
                  </button>
                  <button
                    type="button"
                    onClick={handleClearCriteria}
                    className="flex-1 md:flex-none flex items-center justify-center bg-gray-100 text-gray-700 hover:bg-gray-200 px-8 py-2.5 rounded-md transition-all duration-200 border border-gray-300"
                  >
                    <FaTimes className="mr-2" /> 清空条件
                  </button>
                </div>
              </form>
            ) : (
              // 高级搜索表单 - 使用卡片式布局
              <form onSubmit={handleAdvancedSearch} className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 基本信息卡片 */}
                <div className="col-span-full bg-gray-50 p-4 rounded-lg mb-4 border border-gray-200">
                  <h3 className="text-md font-semibold text-gray-700 mb-3 flex items-center">
                    <FaBook className="mr-2 text-green-500" />
                    基本信息
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* ID字段 */}
                    <div>
                      <label htmlFor="id" className="block text-sm font-medium text-gray-700">图书ID</label>
                      <div className="relative mt-1">
                        <input
                          type="text"
                          id="id"
                          name="id"
                          value={advCriteria.id || ''}
                          onChange={handleAdvancedInputChange}
                          className="w-full border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500 transition-all duration-200"
                        />
                        <div className="mt-1 flex items-center">
                          <input
                            type="checkbox"
                            id="idExact"
                            name="idExact"
                            checked={advCriteria.idExact}
                            onChange={handleCheckboxChange}
                            className="h-4 w-4 accent-green-500"
                          />
                          <label htmlFor="idExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                        </div>
                      </div>
                    </div>

                    {/* ISBN */}
                    <div>
                      <label htmlFor="isbn" className="block text-sm font-medium text-gray-700">ISBN号</label>
                      <input
                        type="text"
                        id="isbn"
                        name="isbn"
                        value={advCriteria.isbn}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="isbnExact"
                          name="isbnExact"
                          checked={advCriteria.isbnExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="isbnExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 书名 */}
                    <div>
                      <label htmlFor="title" className="block text-sm font-medium text-gray-700">书名</label>
                      <input
                        type="text"
                        id="title"
                        name="title"
                        value={advCriteria.title}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="titleExact"
                          name="titleExact"
                          checked={advCriteria.titleExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="titleExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 作者 */}
                    <div>
                      <label htmlFor="author" className="block text-sm font-medium text-gray-700">作者</label>
                      <input
                        type="text"
                        id="author"
                        name="author"
                        value={advCriteria.author}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="authorExact"
                          name="authorExact"
                          checked={advCriteria.authorExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="authorExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 出版信息卡片 */}
                <div className="col-span-full bg-gray-50 p-4 rounded-lg mb-4 border border-gray-200">
                  <h3 className="text-md font-semibold text-gray-700 mb-3 flex items-center">
                    <FaInfoCircle className="mr-2 text-blue-500" />
                    出版信息
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* 出版社 */}
                    <div>
                      <label htmlFor="publisher" className="block text-sm font-medium text-gray-700">出版社</label>
                      <input
                        type="text"
                        id="publisher"
                        name="publisher"
                        value={advCriteria.publisher}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="publisherExact"
                          name="publisherExact"
                          checked={advCriteria.publisherExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="publisherExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 版次 */}
                    <div>
                      <label htmlFor="edition" className="block text-sm font-medium text-gray-700">版次</label>
                      <input
                        type="text"
                        id="edition"
                        name="edition"
                        value={advCriteria.edition}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="editionExact"
                          name="editionExact"
                          checked={advCriteria.editionExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="editionExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 印次 */}
                    <div>
                      <label htmlFor="printTimes" className="block text-sm font-medium text-gray-700">印次</label>
                      <input
                        type="text"
                        id="printTimes"
                        name="printTimes"
                        value={advCriteria.printTimes}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="printTimesExact"
                          name="printTimesExact"
                          checked={advCriteria.printTimesExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="printTimesExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 价格区间 */}
                    <div className="flex gap-2 items-center">
                      <div className="flex-1">
                        <label htmlFor="priceMin" className="block text-sm font-medium text-gray-700">最低价格</label>
                        <input
                          type="number"
                          step="0.01"
                          id="priceMin"
                          name="priceMin"
                          value={advCriteria.priceMin || ''}
                          onChange={(e) => setAdvCriteria({...advCriteria, priceMin: e.target.value ? parseFloat(e.target.value) : undefined})}
                          className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                          min="0"
                        />
                      </div>
                      <span className="mt-6 text-gray-500">至</span>
                      <div className="flex-1">
                        <label htmlFor="priceMax" className="block text-sm font-medium text-gray-700">最高价格</label>
                        <input
                          type="number"
                          step="0.01"
                          id="priceMax"
                          name="priceMax"
                          value={advCriteria.priceMax || ''}
                          onChange={(e) => setAdvCriteria({...advCriteria, priceMax: e.target.value ? parseFloat(e.target.value) : undefined})}
                          className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                          min="0"
                        />
                      </div>
                    </div>
                  </div>
                </div>

                {/* 馆藏信息卡片 */}
                <div className="col-span-full bg-gray-50 p-4 rounded-lg mb-4 border border-gray-200">
                  <h3 className="text-md font-semibold text-gray-700 mb-3 flex items-center">
                    <FaInfoCircle className="mr-2 text-orange-500" />
                    馆藏信息
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {/* 分类 */}
                    <div>
                      <label htmlFor="category" className="block text-sm font-medium text-gray-700">分类</label>
                      <input
                        type="text"
                        id="category"
                        name="category"
                        value={advCriteria.category}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="categoryExact"
                          name="categoryExact"
                          checked={advCriteria.categoryExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="categoryExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 书架编号 */}
                    <div>
                      <label htmlFor="shelvesCode" className="block text-sm font-medium text-gray-700">书架编号</label>
                      <input
                        type="text"
                        id="shelvesCode"
                        name="shelvesCode"
                        value={advCriteria.shelvesCode}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      />
                      <div className="mt-1 flex items-center">
                        <input
                          type="checkbox"
                          id="shelvesCodeExact"
                          name="shelvesCodeExact"
                          checked={advCriteria.shelvesCodeExact}
                          onChange={handleCheckboxChange}
                          className="h-4 w-4 accent-green-500"
                        />
                        <label htmlFor="shelvesCodeExact" className="ml-2 text-sm text-gray-700">精确匹配</label>
                      </div>
                    </div>

                    {/* 状态 */}
                    <div>
                      <label htmlFor="status" className="block text-sm font-medium text-gray-700">状态</label>
                      <select
                        id="status"
                        name="status"
                        value={advCriteria.status}
                        onChange={handleAdvancedInputChange}
                        className="w-full mt-1 border border-gray-300 p-2 rounded-md focus:ring-2 focus:ring-green-300 focus:border-green-500"
                      >
                        <option value="">全部状态</option>
                        <option value="可借阅">可借阅</option>
                        <option value="借阅中">借阅中</option>
                        <option value="禁止借阅">禁止借阅</option>
                      </select>
                    </div>
                  </div>
                </div>

                {/* 按钮区 */}
                <div className="col-span-full flex flex-wrap gap-4 mt-2">
                  <button
                    type="submit"
                    disabled={loading}
                    className={`flex-1 md:flex-none flex items-center justify-center bg-gradient-to-r from-blue-500 to-blue-600 hover:from-blue-600 hover:to-blue-700 text-white px-8 py-2.5 rounded-md transition-all duration-300 shadow hover:shadow-lg ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                  >
                    {loading ? (
                      <>
                        <FaSyncAlt className="animate-spin mr-2" /> 搜索中...
                      </>
                    ) : (
                      <>
                        <FaFilter className="mr-2" /> 高级搜索
                      </>
                    )}
                  </button>
                  <button
                    type="button"
                    onClick={handleClearAdvancedCriteria}
                    className="flex-1 md:flex-none flex items-center justify-center bg-gray-100 text-gray-700 hover:bg-gray-200 px-8 py-2.5 rounded-md transition-all duration-200 border border-gray-300"
                  >
                    <FaTimes className="mr-2" /> 清空条件
                  </button>
                </div>
              </form>
            )}
          </div>

          {/* 搜索结果 */}
          <div id="search-results" className="bg-white shadow-lg rounded-lg p-6 border-t-4 border-blue-500 transition-all duration-300 hover:shadow-xl">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-xl font-bold text-gray-800 flex items-center">
                <FaBook className="mr-2 text-blue-500" /> 搜索结果
                {searchResults.length > 0 && (
                  <span className="ml-2 text-sm bg-blue-100 text-blue-800 py-0.5 px-2 rounded-full">
                    共 {searchResults.length} 条
                  </span>
                )}
              </h2>
              {searchResults.length > 0 && (
                <button
                  onClick={handleExport}
                  className="flex items-center bg-blue-50 hover:bg-blue-100 text-blue-600 px-3 py-1.5 rounded-md transition-all duration-200 text-sm border border-blue-200"
                >
                  <FaFileCsv className="mr-2" /> 导出CSV
                </button>
              )}
            </div>

            {message && (
              <div className={`p-4 mb-4 rounded flex items-center ${isSearched && searchResults.length === 0 ? 'bg-yellow-50 text-yellow-700 border border-yellow-200' : 'bg-blue-50 text-blue-700 border border-blue-200'}`}>
                <FaInfoCircle className="mr-2" />
                {message}
              </div>
            )}

            {loading && (
              <div className="flex justify-center items-center py-12">
                <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-green-500"></div>
              </div>
            )}

            {!loading && searchResults.length > 0 && (
              <>
                <div className="overflow-x-auto rounded-lg border border-gray-200">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="bg-gray-100">
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">ID</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">标题</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">作者</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">出版社</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">ISBN号</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">版次</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">定价</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">分类</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">书架</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">状态</th>
                        <th className="px-4 py-3 text-sm font-semibold text-gray-600">印次</th>
                      </tr>
                    </thead>
                    <tbody>
                      {getCurrentPageData().map((book, index) => (
                        <tr
                          key={book.id}
                          className={`hover:bg-gray-50 transition-colors duration-150 ${index % 2 === 0 ? 'bg-white' : 'bg-gray-50'}`}
                        >
                          <td className="border-t px-4 py-3 text-sm text-gray-900">{book.id}</td>
                          <td className="border-t px-4 py-3 text-sm font-medium text-blue-700">{book.title}</td>
                          <td className="border-t px-4 py-3 text-sm text-gray-700">{book.author}</td>
                          <td className="border-t px-4 py-3 text-sm text-gray-700">{book.publisher}</td>
                          <td className="border-t px-4 py-3 text-sm text-gray-500 font-mono">{book.isbn}</td>
                          <td className="border-t px-4 py-3 text-sm text-gray-700">{book.edition}</td>
                          <td className="border-t px-4 py-3 text-sm text-gray-700">¥{Number(book.price).toFixed(2)}</td>
                          <td className="border-t px-4 py-3 text-sm">
                            <span className="bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded-full text-xs">
                              {book.category?.name || '--'}
                            </span>
                          </td>
                          <td className="border-t px-4 py-3 text-sm font-mono text-gray-700">{book.shelf?.shelfCode || '--'}</td>
                          <td className="border-t px-4 py-3 text-sm">
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs whitespace-nowrap
                                ${book.status === '可借阅' ? 'bg-green-50 text-green-700' : 
                                  book.status === '借阅中' ? 'bg-blue-50 text-blue-700' : 
                                  'bg-red-50 text-red-700'}`}
                            >
                              {book.status}
                            </span>
                          </td>
                          <td className="border-t px-4 py-3 text-sm text-gray-700">{book.printTimes}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* 精美分页 */}
                {totalPages > 1 && (
                  <div className="flex justify-center mt-6">
                    <nav className="flex items-center bg-white rounded-md shadow-sm border border-gray-200" aria-label="分页">
                      <button
                        onClick={() => handlePageChange(currentPage - 1)}
                        disabled={currentPage === 1}
                        className={`flex items-center px-3 py-2 rounded-l-md border-r border-gray-200 transition-colors
                          ${currentPage === 1 
                            ? 'bg-gray-50 text-gray-400 cursor-not-allowed' 
                            : 'bg-white text-gray-700 hover:bg-gray-50 hover:text-blue-600'}`}
                      >
                        <FaArrowLeft className={`mr-1 ${currentPage === 1 ? 'text-gray-300' : 'text-blue-500'}`} />
                        上一页
                      </button>

                      {/* 智能分页按钮 */}
                      {getPaginationRange().map((page, i) => (
                        page === '...' ? (
                          <span key={`ellipsis-${i}`} className="px-3 py-2 border-r border-gray-200 text-gray-500">
                            ...
                          </span>
                        ) : (
                          <button
                            key={`page-${page}`}
                            onClick={() => handlePageChange(Number(page))}
                            className={`px-3.5 py-2 border-r border-gray-200 transition-colors
                              ${currentPage === page
                                ? 'bg-blue-50 text-blue-700 font-medium border-blue-100'
                                : 'bg-white text-gray-700 hover:bg-gray-50 hover:text-blue-600'}`}
                          >
                            {page}
                          </button>
                        )
                      ))}

                      <button
                        onClick={() => handlePageChange(currentPage + 1)}
                        disabled={currentPage === totalPages}
                        className={`flex items-center px-3 py-2 rounded-r-md transition-colors
                          ${currentPage === totalPages 
                            ? 'bg-gray-50 text-gray-400 cursor-not-allowed' 
                            : 'bg-white text-gray-700 hover:bg-gray-50 hover:text-blue-600'}`}
                      >
                        下一页
                        <FaArrowRight className={`ml-1 ${currentPage === totalPages ? 'text-gray-300' : 'text-blue-500'}`} />
                      </button>
                    </nav>
                  </div>
                )}
              </>
            )}

            {!loading && searchResults.length === 0 && isSearched && (
              <div className="flex flex-col items-center justify-center py-12">
                <img
                  src="https://img.icons8.com/color/96/000000/empty-box.png"
                  alt="无结果"
                  className="w-20 h-20 mb-4 opacity-70"
                />
                <p className="text-gray-500 mb-2">未找到匹配的图书</p>
                <p className="text-gray-400 text-sm">请尝试调整搜索条件</p>
              </div>
            )}

            {!loading && !isSearched && (
              <div className="flex flex-col items-center justify-center py-12 text-gray-400">
                <FaSearch className="w-12 h-12 mb-4 opacity-30" />
                <p>请输入搜索条件并点击搜索按钮</p>
              </div>
            )}
          </div>
        </div>

        {/* 右侧帮助面板 */}
        <div className="md:w-1/4">
          <div className="bg-white shadow-lg rounded-lg p-6 border-t-4 border-orange-500 sticky top-6 transition-all duration-300 hover:shadow-xl">
            <h2 className="text-xl font-bold mb-4 flex items-center text-gray-800">
              <FaInfoCircle className="mr-2 text-orange-500" />
              检索指南
            </h2>

            <div className="space-y-4">
              {/* 基础指南卡片 */}
              <div className="bg-orange-50 p-4 rounded-lg border border-orange-100">
                <h3 className="font-medium text-orange-800 mb-2">基础搜索提示</h3>
                <ul className="list-disc pl-5 space-y-2 text-gray-700 text-sm">
                  <li>
                    所有带<span className="text-red-500 font-bold">*</span>的字段为必填项
                  </li>
                  <li>ISBN格式必须为13位数字</li>
                  <li>图书ID必须为数字</li>
                  <li>支持关键词部分匹配搜索</li>
                </ul>
              </div>

              {/* 高级指南卡片 */}
              <div className="bg-blue-50 p-4 rounded-lg border border-blue-100">
                <h3 className="font-medium text-blue-800 mb-2">高级搜索功能</h3>
                <ul className="list-disc pl-5 space-y-2 text-gray-700 text-sm">
                  <li>勾选"精确匹配"可进行完全匹配</li>
                  <li>可设置价格区间进行筛选</li>
                  <li>状态筛选可查看可借/借出图书</li>
                </ul>
              </div>

              {/* 快捷操作卡片 */}
              <div className="bg-green-50 p-4 rounded-lg border border-green-100">
                <h3 className="font-medium text-green-800 mb-2">结果操作</h3>
                <ul className="list-disc pl-5 space-y-2 text-gray-700 text-sm">
                  <li>点击"导出CSV"可下载搜索结果</li>
                  <li>使用分页查看大量搜索结果</li>
                  <li>结果按图书ID排序显示</li>
                </ul>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-dashed border-gray-200">
              <p className="text-xs text-gray-500 italic">如需更多帮助，请联系图书管理员</p>
            </div>
          </div>
        </div>
      </div>
  );
};

export default SearchBook;
