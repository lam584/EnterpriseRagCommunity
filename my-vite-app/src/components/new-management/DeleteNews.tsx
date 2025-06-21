// src/components/new-management/DeleteNews.tsx
import React, { useState, useEffect, useRef } from 'react';
// 这里将来需要替换为实际的新闻服务API
// import { fetchCategories, CategoryDTO } from '../../services/categoryService';

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

// 图标
import { FaTrash, FaCheckCircle, FaTimesCircle, FaSearch, FaExclamationTriangle } from 'react-icons/fa';

const MAX_RECENT_NEWS = 10;

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

const DeleteNews: React.FC = () => {
  const [news, setNews] = useState<NewsDTO[]>([]);
  const [filteredNews, setFilteredNews] = useState<NewsDTO[]>([]);
  const [selectedNews, setSelectedNews] = useState<NewsDTO | null>(null);

  const [loading, setLoading] = useState<boolean>(false);
  const [showConfirm, setShowConfirm] = useState<boolean>(false);
  const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);

  // 搜索条件
  const [searchCriteria, setSearchCriteria] = useState({
    keyword: '',
    searchField: 'title' // 默认按标题搜索
  });

  // 下拉菜单是否展开
  const [dropdownOpen, setDropdownOpen] = useState(false);

  // 添加引用来追踪下拉框状态
  const dropdownRef = useRef<HTMLDivElement>(null);
  // 添加标记鼠标是否在下拉框内的状态
  const [isMouseInDropdown, setIsMouseInDropdown] = useState(false);

  const loadNews = () => {
    setLoading(true);
    // 这里将来需要替换为实际的新闻API调用
    // fetchNews()
    Promise.resolve<NewsDTO[]>([]) // 临时使用空数组，明确指定类型
      .then(data => {
        setNews(data);

        // 默认展示最近更新的新闻
        const sortedNews = [...data]
          .sort((a, b) => {
            const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
            const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
            return dateB - dateA; // 降序排列，最新的在前面
          })
          .slice(0, MAX_RECENT_NEWS);

        setFilteredNews(sortedNews);
      })
      .catch(() => setMessage({type: 'error', text: '加载新闻列表失败'}))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadNews();
  }, []);

  // 当搜索条件变化时，过滤新闻
  useEffect(() => {
    if (!searchCriteria.keyword.trim()) {
      const recentNews = [...news]
        .sort((a, b) => {
          const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
          const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
          return dateB - dateA;
        })
        .slice(0, MAX_RECENT_NEWS);

      setFilteredNews(recentNews);
      return;
    }

    const keyword = searchCriteria.keyword.toLowerCase();
    const filtered = news.filter(item => {
      switch (searchCriteria.searchField) {
        case 'id':
          return item.id?.toString() === keyword || item.id?.toString().includes(keyword);
        case 'author':
          return item.author.toLowerCase().includes(keyword);
        case 'title':
        default:
          return item.title.toLowerCase().includes(keyword);
      }
    });
    setFilteredNews(filtered);
    // 只有输入关键词后才展开
    setDropdownOpen(true);
  }, [searchCriteria, news]);

  // 选择新闻进行删除
  const selectNewsForDelete = (selectedNews: NewsDTO) => {
    setSelectedNews(selectedNews);
    setDropdownOpen(false);
    setShowConfirm(true);
    setMessage(null);
  };

  // 确认删除
  const confirmDelete = async () => {
    if (!selectedNews?.id) return;

    try {
      setLoading(true);
      // 调用删除API
      // await deleteNews(selectedNews.id);

      setMessage({
        type: 'success',
        text: `"${selectedNews.title}" 已成功删除`
      });

      // 重新加载新闻列表
      loadNews();

      // 重置状态
      setSelectedNews(null);
      setShowConfirm(false);
    } catch (error) {
      console.error('删除新闻失败', error);
      setMessage({
        type: 'error',
        text: '删除新闻失败，请重试'
      });
    } finally {
      setLoading(false);
    }
  };

  // 取消删除
  const cancelDelete = () => {
    setShowConfirm(false);
    setSelectedNews(null);
  };

  // 处理搜索条件变化
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setSearchCriteria(prev => ({
      ...prev,
      [name]: value
    }));
  };

  // 点击外部关闭下拉框
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        !isMouseInDropdown
      ) {
        setDropdownOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isMouseInDropdown]);

  return (
    <Card className="w-full max-w-4xl mx-auto  bg-white">
      <CardHeader className="bg-gradient-to-r from-red-600 to-orange-500 text-white">
        <CardTitle className="text-2xl flex items-center gap-2">
          <FaTrash /> 删除新闻
        </CardTitle>
      </CardHeader>

      <CardContent className="pt-6">
        {message && (
          <Alert className={`mb-6 ${message.type === 'success' ? 'bg-green-50 text-green-800 border-green-200' : 'bg-red-50 text-red-800 border-red-200'}`}>
            <AlertTitle className="flex items-center gap-2">
              {message.type === 'success' ? <FaCheckCircle className="text-green-500" /> : <FaTimesCircle className="text-red-500" />}
              {message.type === 'success' ? '成功' : '错误'}
            </AlertTitle>
            <AlertDescription>{message.text}</AlertDescription>
          </Alert>
        )}

        {showConfirm && selectedNews && (
          <Alert className="mb-6 bg-yellow-50 text-yellow-800 border-yellow-200">
            <AlertTitle className="flex items-center gap-2">
              <FaExclamationTriangle className="text-yellow-500" />
              确认删除
            </AlertTitle>
            <AlertDescription className="space-y-4">
              <p>您确定要删除以下新闻吗？此操作不可撤销。</p>
              <div className="bg-white p-4 rounded border border-yellow-200">
                <h3 className="font-bold">{selectedNews.title}</h3>
                <p className="text-sm text-gray-500">作者: {selectedNews.author}</p>
                <p className="text-sm text-gray-500">ID: {selectedNews.id}</p>
                <p className="mt-2 text-sm">{selectedNews.summary}</p>
              </div>
              <div className="flex justify-end gap-2 mt-4">
                <Button
                  variant="outline"
                  onClick={cancelDelete}
                  className="border-yellow-500 text-yellow-700 hover:bg-yellow-50"
                  disabled={loading}
                >
                  取消
                </Button>
                <Button
                  onClick={confirmDelete}
                  className="bg-red-500 hover:bg-red-600 text-white"
                  disabled={loading}
                >
                  {loading ? '删除中...' : '确认删除'}
                </Button>
              </div>
            </AlertDescription>
          </Alert>
        )}

        {/* 搜索区域 */}
        <div className="mb-6 relative">
          <div className="flex flex-col md:flex-row gap-2 items-end">
            <div className="flex-1">
              <Label htmlFor="searchField">搜索字段</Label>
              <Select
                value={searchCriteria.searchField}
                onValueChange={(value) => handleSearchChange({
                  target: { name: 'searchField', value }
                } as React.ChangeEvent<HTMLSelectElement>)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择搜索字段" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="title">标题</SelectItem>
                  <SelectItem value="author">作者</SelectItem>
                  <SelectItem value="id">ID</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex-[2]">
              <Label htmlFor="keyword">关键词</Label>
              <div className="relative">
                <Input
                  id="keyword"
                  name="keyword"
                  placeholder="输入关键词搜索新闻..."
                  className="pr-8"
                  value={searchCriteria.keyword}
                  onChange={handleSearchChange}
                  onFocus={() => setDropdownOpen(true)}
                />
                <FaSearch className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400" />
              </div>
            </div>
          </div>

          {/* 搜索结果下拉框 */}
          {dropdownOpen && (
            <div
              ref={dropdownRef}
              className="absolute z-10 mt-1 w-full bg-white border border-gray-300 rounded-md shadow-lg max-h-80 overflow-auto"
              onMouseEnter={() => setIsMouseInDropdown(true)}
              onMouseLeave={() => setIsMouseInDropdown(false)}
            >
              {filteredNews.length > 0 ? (
                filteredNews.map(item => (
                  <div
                    key={item.id}
                    className="p-3 hover:bg-gray-100 cursor-pointer border-b border-gray-100 last:border-0"
                    onClick={() => selectNewsForDelete(item)}
                  >
                    <div className="font-medium">{item.title}</div>
                    <div className="text-sm text-gray-500 flex justify-between">
                      <span>作者: {item.author}</span>
                      <span>ID: {item.id}</span>
                    </div>
                    <div className="text-xs text-gray-400 mt-1">
                      {item.updatedAt ? new Date(item.updatedAt).toLocaleString() : '无更新时间'}
                    </div>
                  </div>
                ))
              ) : (
                <div className="p-3 text-center text-gray-500">
                  {searchCriteria.keyword ? '没有找到匹配的新闻' : '最近没有更新的新闻'}
                </div>
              )}
            </div>
          )}
        </div>

        {/* 说明文字 */}
        {!showConfirm && (
          <div className="text-center text-gray-600 p-8">
            <FaSearch className="mx-auto mb-3 text-3xl text-gray-400" />
            <p className="mb-2">请使用上方搜索框查找要删除的新闻</p>
            <p className="text-sm text-gray-500">删除操作不可撤销，请谨慎操作</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default DeleteNews;
