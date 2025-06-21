// src/components/new-management/SearchNews.tsx
import React, { useState, useEffect } from 'react';
// 引入图标
import { FaSearch, FaFilter, FaFileCsv, FaArrowLeft, FaArrowRight, FaSyncAlt, FaTimes, FaInfoCircle, FaEye, FaThumbsUp, FaComment } from 'react-icons/fa';

// UI 组件
import { Card, CardHeader, CardContent, CardTitle } from '../ui/card';
import { Label } from '../ui/label';
import { Input } from '../ui/input';
import { Button } from '../ui/button';
import { Alert, AlertTitle, AlertDescription } from '../ui/alert';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '../ui/select';
import { Checkbox } from '../ui/checkbox';

// 新闻DTO接口
interface NewsDTO {
  id?: number;
  title: string;
  content: string;
  summary: string;
  author: string;
  categoryId: number;
  category?: { id: number, name?: string };
  coverImage: string;
  isTop: boolean;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  views?: number;
  likes?: number;
  commentCount?: number;
}

// 为基础搜索条件声明一个接口
interface BasicCriteria {
  title: string;
  author: string;
  categoryId: string;
  status: string;
}

// 高级搜索条件接口
interface AdvancedSearchCriteria extends BasicCriteria {
  id: string;
  idExact: boolean;
  titleExact: boolean;
  authorExact: boolean;
  categoryIdExact: boolean;
  createdStartDate: string;
  createdEndDate: string;
  updatedStartDate: string;
  updatedEndDate: string;
  viewsMin: string;
  viewsMax: string;
  likesMin: string;
  likesMax: string;
  commentCountMin: string;
  commentCountMax: string;
  isTop: boolean;
}

const SearchNews: React.FC = () => {
  // 基本搜索状态
  const [criteria, setCriteria] = useState<BasicCriteria>({ 
    title: '', 
    author: '', 
    categoryId: '', 
    status: '' 
  });
  
  // 错误状态
  const [errors, setErrors] = useState<Record<string, string>>({});

  // 高级搜索状态
  const [advCriteria, setAdvCriteria] = useState<AdvancedSearchCriteria>({
    id: '',
    idExact: false,
    title: '',
    titleExact: false,
    author: '',
    authorExact: false,
    categoryId: '',
    categoryIdExact: false,
    status: '',
    createdStartDate: '',
    createdEndDate: '',
    updatedStartDate: '',
    updatedEndDate: '',
    viewsMin: '',
    viewsMax: '',
    likesMin: '',
    likesMax: '',
    commentCountMin: '',
    commentCountMax: '',
    isTop: false
  });

  const [showAdvancedSearch, setShowAdvancedSearch] = useState<boolean>(false);
  const [searchResults, setSearchResults] = useState<NewsDTO[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isSearched, setIsSearched] = useState<boolean>(false);
  const [categories, setCategories] = useState<{id: number, name: string}[]>([]);
  const itemsPerPage = 10;

  // 页面加载时获取分类列表
  useEffect(() => {
    // 模拟获取分类列表，实际开发需要调用API
    const dummyCategories = [
      { id: 1, name: '政治' },
      { id: 2, name: '经济' },
      { id: 3, name: '科技' },
      { id: 4, name: '文化' },
      { id: 5, name: '体育' },
      { id: 6, name: '娱乐' },
      { id: 7, name: '社会' }
    ];
    setCategories(dummyCategories);
  }, []);

  // 处理基本搜索输入变化
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setCriteria(prev => ({ ...prev, [name]: value }));
    
    // 清除错误
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 处理高级搜索输入变化
  const handleAdvancedInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setAdvCriteria(prev => ({ ...prev, [name]: value }));
    
    // 清除错误
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 处理高级搜索复选框变化
  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, checked } = e.target;
    setAdvCriteria(prev => ({ ...prev, [name]: checked }));
  };

  // 切换高级搜索
  const toggleAdvancedSearch = () => {
    setShowAdvancedSearch(prev => !prev);
  };

  // 清空基本搜索条件
  const handleClearCriteria = () => {
    setCriteria({ title: '', author: '', categoryId: '', status: '' });
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
      title: '',
      titleExact: false,
      author: '',
      authorExact: false,
      categoryId: '',
      categoryIdExact: false,
      status: '',
      createdStartDate: '',
      createdEndDate: '',
      updatedStartDate: '',
      updatedEndDate: '',
      viewsMin: '',
      viewsMax: '',
      likesMin: '',
      likesMax: '',
      commentCountMin: '',
      commentCountMax: '',
      isTop: false
    });
  };

  // 处理基本搜索提交
  const handleBasicSearch = (e: React.FormEvent) => {
    e.preventDefault();
    performSearch();
  };

  // 处理高级搜索提交
  const handleAdvancedSearch = (e: React.FormEvent) => {
    e.preventDefault();
    performSearch(true);
  };

  // 执行搜索
  const performSearch = (isAdvanced = false) => {
    setLoading(true);
    setMessage('');
    setCurrentPage(1);
    
    // 构建搜索参数
    const searchParams = isAdvanced ? advCriteria : criteria;
    
    // 模拟搜索API调用
    setTimeout(() => {
      // 这里应该调用实际的搜索API
      // const results = await searchNews(searchParams);
      
      // 使用模拟数据
      let mockResults: NewsDTO[] = Array(15).fill(null).map((_, idx) => ({
        id: idx + 1,
        title: `测试新闻标题 ${idx + 1}`,
        content: `这是测试新闻内容 ${idx + 1}`,
        summary: `这是测试新闻摘要 ${idx + 1}`,
        author: `作者${idx % 5 + 1}`,
        categoryId: idx % 7 + 1,
        category: { id: idx % 7 + 1, name: categories[idx % 7]?.name },
        coverImage: '',
        isTop: idx < 3,
        status: idx % 3 === 0 ? '已发布' : (idx % 3 === 1 ? '待发布' : '已下线'),
        createdAt: new Date(Date.now() - idx * 86400000).toISOString(),
        updatedAt: new Date(Date.now() - idx * 43200000).toISOString(),
        views: 100 + idx * 10,
        likes: 20 + idx * 5,
        commentCount: 5 + idx * 2
      }));

      // 根据搜索参数过滤结果
      // 基本搜索条件筛选
      if (searchParams.title) {
        mockResults = mockResults.filter(news => {
          if (isAdvanced) {
            // 高级搜索模式下可以使用高级搜索属性
            return (searchParams as AdvancedSearchCriteria).titleExact
              ? news.title === searchParams.title
              : news.title.includes(searchParams.title);
          } else {
            // 基本搜索模式
            return news.title.includes(searchParams.title);
          }
        });
      }

      if (searchParams.author) {
        mockResults = mockResults.filter(news => {
          if (isAdvanced) {
            // 高级搜索模式下可以使用高��搜索属性
            return (searchParams as AdvancedSearchCriteria).authorExact
              ? news.author === searchParams.author
              : news.author.includes(searchParams.author);
          } else {
            // 基本搜索模式
            return news.author.includes(searchParams.author);
          }
        });
      }

      if (searchParams.categoryId) {
        mockResults = mockResults.filter(news =>
          news.categoryId.toString() === searchParams.categoryId
        );
      }

      if (searchParams.status) {
        mockResults = mockResults.filter(news => news.status === searchParams.status);
      }

      // 如果是高级搜索，应用其他高级过滤条件
      if (isAdvanced) {
        const advParams = searchParams as AdvancedSearchCriteria;

        // ID搜索
        if (advParams.id) {
          mockResults = mockResults.filter(news =>
            advParams.idExact
              ? news.id?.toString() === advParams.id
              : news.id?.toString().includes(advParams.id)
          );
        }

        // 置顶状态搜索
        if (advParams.isTop) {
          mockResults = mockResults.filter(news => news.isTop === true);
        }

        // 可以根据需要添加更多高级搜索条件
        // 例如时间范围、阅读量、点赞数、评论数等
      }

      setSearchResults(mockResults);
      setTotalPages(Math.ceil(mockResults.length / itemsPerPage));
      setMessage(mockResults.length > 0 ? `查询到 ${mockResults.length} 条结果` : '没有找到匹配的新闻');
      setIsSearched(true);
      setLoading(false);
    }, 500);
  };

  // 处理分页
  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

  // 导出CSV
  const exportToCSV = () => {
    if (searchResults.length === 0) {
      setMessage('没有数据可以导出');
      return;
    }

    try {
      // 准备CSV头部
      const headers = [
        'ID', '标题', '作者', '分类', '状态', 
        '创建时间', '更新时间', '阅读量', '点赞数', '评论数'
      ];

      // 准备CSV行数据
      const rows = searchResults.map(news => [
        news.id?.toString() || '',
        news.title,
        news.author,
        news.category?.name || '',
        news.status,
        news.createdAt ? new Date(news.createdAt).toLocaleString() : '',
        news.updatedAt ? new Date(news.updatedAt).toLocaleString() : '',
        news.views?.toString() || '0',
        news.likes?.toString() || '0',
        news.commentCount?.toString() || '0'
      ]);

      // 合并头部和行
      const csvContent = [
        headers.join(','),
        ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
      ].join('\n');

      // 创建Blob对象
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);

      // 创建下载链接
      const link = document.createElement('a');
      link.setAttribute('href', url);
      link.setAttribute('download', `新闻搜索结果_${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);

      // 触发下载
      link.click();

      // 清理
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('导出CSV失败', error);
      setMessage('导出CSV失败');
    }
  };

  // 计算当前页面应显示的结果
  const displayedResults = searchResults.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  return (
    <Card className="w-full  bg-white">
      <CardHeader className="bg-gradient-to-r from-blue-600 to-purple-600 text-white">
        <CardTitle className="text-2xl flex items-center gap-2">
          <FaSearch /> 查找新闻
        </CardTitle>
      </CardHeader>

      <CardContent className="pt-6">
        {/* 基本搜索表单 */}
        <form onSubmit={handleBasicSearch} className="mb-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-2">
              <Label htmlFor="title">新闻标题</Label>
              <Input
                id="title"
                name="title"
                placeholder="请输入新闻标题"
                value={criteria.title}
                onChange={handleInputChange}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="author">作者</Label>
              <Input
                id="author"
                name="author"
                placeholder="请输入作者"
                value={criteria.author}
                onChange={handleInputChange}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="categoryId">新闻分类</Label>
              <Select
                value={criteria.categoryId}
                onValueChange={(value) => handleInputChange({ 
                  target: { name: 'categoryId', value } 
                } as React.ChangeEvent<HTMLSelectElement>)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择分类" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">所有分类</SelectItem>
                  {categories.map(cat => (
                    <SelectItem key={cat.id} value={cat.id.toString()}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="status">状态</Label>
              <Select
                value={criteria.status}
                onValueChange={(value) => handleInputChange({ 
                  target: { name: 'status', value } 
                } as React.ChangeEvent<HTMLSelectElement>)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">所有状态</SelectItem>
                  <SelectItem value="已发布">已发布</SelectItem>
                  <SelectItem value="待发布">待发布</SelectItem>
                  <SelectItem value="已下线">已下线</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap justify-between items-center gap-2">
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                className="flex items-center gap-1"
                onClick={handleClearCriteria}
              >
                <FaTimes /> 清空条件
              </Button>
              <Button
                type="button"
                variant={showAdvancedSearch ? "secondary" : "outline"}
                className="flex items-center gap-1"
                onClick={toggleAdvancedSearch}
              >
                <FaFilter /> {showAdvancedSearch ? "收起高级搜索" : "高级搜索"}
              </Button>
            </div>
            <Button 
              type="submit" 
              className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1"
              disabled={loading}
            >
              {loading ? <FaSyncAlt className="animate-spin" /> : <FaSearch />}
              {loading ? "搜索中..." : "搜索新闻"}
            </Button>
          </div>
        </form>

        {/* 高级搜索表单 */}
        {showAdvancedSearch && (
          <form onSubmit={handleAdvancedSearch} className="border-t pt-4 mb-6">
            <h3 className="text-lg font-medium mb-4">高级搜索选项</h3>
            
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-4">
              {/* ID搜索 */}
              <div className="space-y-2">
                <Label htmlFor="id">新闻ID</Label>
                <div className="flex items-center space-x-2">
                  <Input
                    id="id"
                    name="id"
                    placeholder="新闻ID"
                    value={advCriteria.id}
                    onChange={handleAdvancedInputChange}
                    className="flex-1"
                  />
                  <div className="flex items-center space-x-1">
                    <Checkbox
                      id="idExact"
                      checked={advCriteria.idExact}
                      onCheckedChange={(checked) => 
                        handleCheckboxChange({ 
                          target: { name: 'idExact', checked: !!checked } 
                        } as React.ChangeEvent<HTMLInputElement>)
                      }
                    />
                    <Label htmlFor="idExact" className="text-xs">精确</Label>
                  </div>
                </div>
              </div>
              
              {/* 标题搜索 */}
              <div className="space-y-2">
                <Label htmlFor="advTitle">标题</Label>
                <div className="flex items-center space-x-2">
                  <Input
                    id="advTitle"
                    name="title"
                    placeholder="新闻标题"
                    value={advCriteria.title}
                    onChange={handleAdvancedInputChange}
                    className="flex-1"
                  />
                  <div className="flex items-center space-x-1">
                    <Checkbox
                      id="titleExact"
                      checked={advCriteria.titleExact}
                      onCheckedChange={(checked) => 
                        handleCheckboxChange({ 
                          target: { name: 'titleExact', checked: !!checked } 
                        } as React.ChangeEvent<HTMLInputElement>)
                      }
                    />
                    <Label htmlFor="titleExact" className="text-xs">精确</Label>
                  </div>
                </div>
              </div>
              
              {/* 作者搜索 */}
              <div className="space-y-2">
                <Label htmlFor="advAuthor">作者</Label>
                <div className="flex items-center space-x-2">
                  <Input
                    id="advAuthor"
                    name="author"
                    placeholder="作者"
                    value={advCriteria.author}
                    onChange={handleAdvancedInputChange}
                    className="flex-1"
                  />
                  <div className="flex items-center space-x-1">
                    <Checkbox
                      id="authorExact"
                      checked={advCriteria.authorExact}
                      onCheckedChange={(checked) => 
                        handleCheckboxChange({ 
                          target: { name: 'authorExact', checked: !!checked } 
                        } as React.ChangeEvent<HTMLInputElement>)
                      }
                    />
                    <Label htmlFor="authorExact" className="text-xs">精确</Label>
                  </div>
                </div>
              </div>
              
              {/* 创建时间区间 */}
              <div className="space-y-2">
                <Label>创建时间范围</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="date"
                    name="createdStartDate"
                    value={advCriteria.createdStartDate}
                    onChange={handleAdvancedInputChange}
                    placeholder="开始日期"
                  />
                  <Input
                    type="date"
                    name="createdEndDate"
                    value={advCriteria.createdEndDate}
                    onChange={handleAdvancedInputChange}
                    placeholder="结束日期"
                  />
                </div>
              </div>
              
              {/* 更新时间区间 */}
              <div className="space-y-2">
                <Label>更新时间范围</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="date"
                    name="updatedStartDate"
                    value={advCriteria.updatedStartDate}
                    onChange={handleAdvancedInputChange}
                    placeholder="开始日期"
                  />
                  <Input
                    type="date"
                    name="updatedEndDate"
                    value={advCriteria.updatedEndDate}
                    onChange={handleAdvancedInputChange}
                    placeholder="结束日期"
                  />
                </div>
              </div>
              
              <div className="space-y-2">
                <Label>阅读量范围</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="number"
                    name="viewsMin"
                    value={advCriteria.viewsMin}
                    onChange={handleAdvancedInputChange}
                    placeholder="最小值"
                    min="0"
                  />
                  <Input
                    type="number"
                    name="viewsMax"
                    value={advCriteria.viewsMax}
                    onChange={handleAdvancedInputChange}
                    placeholder="最大值"
                    min="0"
                  />
                </div>
              </div>
              
              {/* 点赞数范围 */}
              <div className="space-y-2">
                <Label>点赞数范围</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="number"
                    name="likesMin"
                    value={advCriteria.likesMin}
                    onChange={handleAdvancedInputChange}
                    placeholder="最小值"
                    min="0"
                  />
                  <Input
                    type="number"
                    name="likesMax"
                    value={advCriteria.likesMax}
                    onChange={handleAdvancedInputChange}
                    placeholder="最大值"
                    min="0"
                  />
                </div>
              </div>
              
              {/* 评论数范围 */}
              <div className="space-y-2">
                <Label>评论数范围</Label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="number"
                    name="commentCountMin"
                    value={advCriteria.commentCountMin}
                    onChange={handleAdvancedInputChange}
                    placeholder="最小值"
                    min="0"
                  />
                  <Input
                    type="number"
                    name="commentCountMax"
                    value={advCriteria.commentCountMax}
                    onChange={handleAdvancedInputChange}
                    placeholder="最大值"
                    min="0"
                  />
                </div>
              </div>
              
              {/* 置顶状态 */}
              <div className="flex items-center space-x-2 mt-6">
                <Checkbox
                  id="isTop"
                  checked={advCriteria.isTop}
                  onCheckedChange={(checked) => 
                    handleCheckboxChange({ 
                      target: { name: 'isTop', checked: !!checked } 
                    } as React.ChangeEvent<HTMLInputElement>)
                  }
                />
                <Label htmlFor="isTop">仅显示置顶新闻</Label>
              </div>
            </div>

            <div className="flex justify-between mt-4">
              <Button
                type="button"
                variant="outline"
                className="flex items-center gap-1"
                onClick={handleClearAdvancedCriteria}
              >
                <FaTimes /> 清空高级条件
              </Button>
              <Button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1"
                disabled={loading}
              >
                {loading ? <FaSyncAlt className="animate-spin" /> : <FaSearch />}
                {loading ? "高级搜索中..." : "执行高级搜索"}
              </Button>
            </div>
          </form>

        )}
        {/* 新增的 Alert 提示 */}
        <Alert className="mb-6">
          <AlertTitle>提示</AlertTitle>
          <AlertDescription>
            可以通过上方的基本搜索快速查找新闻，点击“高级搜索”可以使用更多筛选条件进行精确查询。
          </AlertDescription>
        </Alert>
        {/* 搜索结果 */}
        {isSearched && (
          <div className="mt-6">
            {/* 结果信息 */}
            <div className="flex justify-between items-center mb-4">
              <div className="text-sm text-gray-500">
                {message}
              </div>
              {searchResults.length > 0 && (
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="flex items-center gap-1"
                  onClick={exportToCSV}
                >
                  <FaFileCsv /> 导出CSV
                </Button>
              )}
            </div>

            {/* 搜索结果表格 */}
            {searchResults.length > 0 && (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left border-collapse">
                    <thead className="text-xs text-gray-700 uppercase bg-gray-100">
                      <tr>
                        <th className="px-4 py-3">ID</th>
                        <th className="px-4 py-3">标题</th>
                        <th className="px-4 py-3">作者</th>
                        <th className="px-4 py-3">分类</th>
                        <th className="px-4 py-3">状态</th>
                        <th className="px-4 py-3 text-center">阅读量</th>
                        <th className="px-4 py-3 text-center">点赞</th>
                        <th className="px-4 py-3 text-center">评论</th>
                        <th className="px-4 py-3">更新时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      {displayedResults.map((news, idx) => (
                        <tr 
                          key={news.id} 
                          className={`border-b ${idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'} ${news.isTop ? 'bg-yellow-50' : ''}`}
                        >
                          <td className="px-4 py-3">{news.id}</td>
                          <td className="px-4 py-3 font-medium">
                            <div className="flex items-center">
                              {news.isTop && (
                                <span className="bg-yellow-100 text-yellow-800 text-xs font-medium me-2 px-2 py-0.5 rounded">置顶</span>
                              )}
                              {news.title}
                            </div>
                          </td>
                          <td className="px-4 py-3">{news.author}</td>
                          <td className="px-4 py-3">{news.category?.name}</td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-1 rounded text-xs ${
                              news.status === '已发布' ? 'bg-green-100 text-green-800' :
                              news.status === '待发布' ? 'bg-yellow-100 text-yellow-800' :
                              'bg-gray-100 text-gray-800'
                            }`}>
                              {news.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <div className="flex items-center justify-center">
                              <FaEye className="mr-1 text-gray-400" />
                              {news.views}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <div className="flex items-center justify-center">
                              <FaThumbsUp className="mr-1 text-gray-400" />
                              {news.likes}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <div className="flex items-center justify-center">
                              <FaComment className="mr-1 text-gray-400" />
                              {news.commentCount}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-xs">
                            {news.updatedAt ? new Date(news.updatedAt).toLocaleString() : '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* 分页控件 */}
                {totalPages > 1 && (
                  <div className="flex justify-between items-center mt-4">
                    <div className="text-sm text-gray-500">
                      第 {currentPage} 页，共 {totalPages} 页
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePageChange(currentPage - 1)}
                        disabled={currentPage === 1}
                      >
                        <FaArrowLeft />
                      </Button>
                      {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                        <Button
                          key={page}
                          variant={currentPage === page ? "default" : "outline"}
                          size="sm"
                          onClick={() => handlePageChange(page)}
                        >
                          {page}
                        </Button>
                      ))}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePageChange(currentPage + 1)}
                        disabled={currentPage === totalPages}
                      >
                        <FaArrowRight />
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}

            {/* 无结果提示 */}
            {searchResults.length === 0 && isSearched && (
              <div className="text-center py-10">
                <FaInfoCircle className="mx-auto text-gray-400 text-4xl mb-3" />
                <p className="text-gray-500">没有找到匹配的新闻记录</p>
                <p className="text-gray-400 text-sm mt-1">请尝试不同的搜索条件</p>
              </div>
            )}
          </div>
        )}

        {/* 初始提示 */}
        {!isSearched && (
          <div className="text-center py-16">
            <FaSearch className="mx-auto text-gray-300 text-5xl mb-4" />
            <p className="text-gray-500">请使用上方搜索框查找新闻</p>
            <p className="text-gray-400 text-sm mt-1">可以使用高级搜索获得更精确的结果</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default SearchNews;
