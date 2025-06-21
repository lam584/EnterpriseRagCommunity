// src/components/new-management/EditNews.tsx
import React, { useState, useEffect, useRef } from 'react';
import { fetchCategories, TopicDTO } from '../../services/TopicService.ts';
// 引入富文本编辑器组件
import { Textarea } from '../ui/textarea';

// UI 组件库
import { Card, CardHeader, CardContent, CardFooter, CardTitle } from '../ui/card';
import { Label } from '../ui/label';
import { Input } from '../ui/input';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '../ui/select';
import { Button } from '../ui/button';
import { Alert, AlertTitle, AlertDescription } from '../ui/alert';

// 图标
import { FaCheckCircle, FaTimesCircle, FaSearch, FaEdit } from 'react-icons/fa';
import { AiOutlineClear } from 'react-icons/ai';

// 定义新闻DTO接口
interface NewsDTO {
  id?: number;
  title: string;
  content: string;
  summary: string;
  author: string;
  TopicId: number;
  Topic?: { id: number, name?: string };
  coverImage: string;
  isTop: boolean;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  views?: number;
  likes?: number;
  commentCount?: number;
}

const EditNewsForm: React.FC = () => {
  const [news, setNews] = useState<NewsDTO[]>([]);
  const [filteredNews, setFilteredNews] = useState<NewsDTO[]>([]);
  const [categories, setCategories] = useState<TopicDTO[]>([]);
  const [selectedNewsId, setSelectedNewsId] = useState<string>('');
  const [form, setForm] = useState<NewsDTO>({
    title: '',
    content: '',
    summary: '',
    author: '',
    TopicId: 0,
    coverImage: '',
    isTop: false,
    status: '待发布'
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);

  // 搜索条件
  const [searchCriteria, setSearchCriteria] = useState({
    keyword: '',
    searchField: 'title' // 默认按标题搜索
  });

  // 下拉菜单是否展开
  const [dropdownOpen, setDropdownOpen] = useState(false);
  // 最大显示数量
  const MAX_RECENT_NEWS = 10;

  // 添加一个引用来追踪下拉框状态
  const dropdownRef = useRef<HTMLDivElement>(null);
  // 标记鼠标是否在下拉框内
  const [isMouseInDropdown, setIsMouseInDropdown] = useState(false);

  useEffect(() => {
    // 加载所有选项数据
    Promise.all([
      fetchCategories().catch(() => []),
      // fetchNews().catch(() => []) // 需要实现新闻获取服务
      Promise.resolve([] as NewsDTO[]) // 明确指定返回类型为 NewsDTO[]
    ]).then(([categoriesData, newsData]) => {
      setCategories(categoriesData);
      setNews(newsData);

      // 默认展示最近更新的新闻
      const sortedNews = [...newsData].sort((a, b) => {
        const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return dateB - dateA; // 降序排列，最新的在前面
      }).slice(0, MAX_RECENT_NEWS);

      setFilteredNews(sortedNews);
    });
  }, []);

  // 当搜索条件变化时，过滤新闻
  useEffect(() => {
    if (!searchCriteria.keyword.trim()) {
      // 只更新列表，不自动展开
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
    if (filtered.length > 0 && searchCriteria.keyword.trim() !== '') {
      setDropdownOpen(true);
    }
  }, [searchCriteria, news]);

  // 选择新闻进行编辑
  const selectNewsForEdit = async (id: number) => {
    try {
      setLoading(true);
      setMessage(null);
      setDropdownOpen(false);
      setSelectedNewsId(id.toString());

      // 临时模拟，实际应从API获取
      // const newsData = await fetchNewsById(id);
      const newsData = news.find(item => item.id === id);

      if (newsData) {
        setForm({
          id: newsData.id,
          title: newsData.title,
          content: newsData.content,
          summary: newsData.summary,
          author: newsData.author,
          TopicId: newsData.Topic?.id || 0,
          coverImage: newsData.coverImage,
          isTop: newsData.isTop,
          status: newsData.status
        });
      }
    } catch (error) {
      console.error('加载新闻数据失败', error);
      setMessage({
        type: 'error',
        text: '加载新闻数据失败，请重试'
      });
    } finally {
      setLoading(false);
    }
  };

  // 表单验证
  const validateForm = () => {
    const e: Record<string, string> = {};
    if (!form.title.trim()) e.title = '标题不能为空';
    if (!form.content.trim()) e.content = '内容不能为空';
    if (!form.summary.trim()) e.summary = '摘要不能为空';
    if (!form.author.trim()) e.author = '作者不能为空';
    if (form.TopicId === 0) e.TopicId = '请选择新闻主题';

    setErrors(e);
    return Object.keys(e).length === 0;
  };

  // 处理表单提交
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    try {
      setLoading(true);
      // 调用后端API更新新闻
      // await updateNews(form);

      setMessage({
        type: 'success',
        text: '新闻更新成功！'
      });

      // 重置选中状态，但保留表单数据以便确认
      setSelectedNewsId('');
    } catch (error) {
      console.error('更新新闻失败', error);
      setMessage({
        type: 'error',
        text: '更新失败，请稍后重试'
      });
    } finally {
      setLoading(false);
    }
  };

  // 重置表单
  const resetForm = () => {
    setForm({
      title: '',
      content: '',
      summary: '',
      author: '',
      TopicId: 0,
      coverImage: '',
      isTop: false,
      status: '待发布'
    });
    setSelectedNewsId('');
    setErrors({});
    setMessage(null);
  };

  // 处理表单输入变化
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: value
    }));

    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 处理下拉选择变化
  const handleSelectChange = (name: string, value: string) => {
    if (name === 'TopicId') {
      setForm(prev => ({
        ...prev,
        [name]: parseInt(value)
      }));
    } else {
      setForm(prev => ({
        ...prev,
        [name]: value
      }));
    }

    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 处理布尔值变化
  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, checked } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: checked
    }));
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
      <CardHeader className="bg-gradient-to-r from-blue-600 to-purple-600 text-white">
        <CardTitle className="text-2xl flex items-center gap-2">
          <FaEdit /> 编辑新闻
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
                    className={`p-2 hover:bg-gray-100 cursor-pointer ${selectedNewsId === item.id?.toString() ? 'bg-blue-50' : ''}`}
                    onClick={() => item.id && selectNewsForEdit(item.id)}
                  >
                    <div className="font-medium">{item.title}</div>
                    <div className="text-sm text-gray-500 flex justify-between">
                      <span>作者: {item.author}</span>
                      <span>ID: {item.id}</span>
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

        {/* 编辑表单 */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* 标题 */}
            <div className="space-y-2">
              <Label htmlFor="title">新闻标题 <span className="text-red-500">*</span></Label>
              <Input
                id="title"
                name="title"
                placeholder="请输入新闻标题"
                value={form.title}
                onChange={handleInputChange}
                className={errors.title ? 'border-red-500' : ''}
              />
              {errors.title && <p className="text-red-500 text-sm">{errors.title}</p>}
            </div>

            {/* 作者 */}
            <div className="space-y-2">
              <Label htmlFor="author">作者 <span className="text-red-500">*</span></Label>
              <Input
                id="author"
                name="author"
                placeholder="请输入作者名称"
                value={form.author}
                onChange={handleInputChange}
                className={errors.author ? 'border-red-500' : ''}
              />
              {errors.author && <p className="text-red-500 text-sm">{errors.author}</p>}
            </div>

            {/* 新闻主题 */}
            <div className="space-y-2">
              <Label htmlFor="TopicId">新闻主题 <span className="text-red-500">*</span></Label>
              <Select
                value={form.TopicId.toString()}
                onValueChange={(value) => handleSelectChange('TopicId', value)}
              >
                <SelectTrigger className={errors.TopicId ? 'border-red-500' : ''}>
                  <SelectValue placeholder="选择新闻主题" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="0">请选择</SelectItem>
                  {categories.map(cat => (
                    <SelectItem key={cat.id!} value={cat.id!.toString()}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.TopicId && <p className="text-red-500 text-sm">{errors.TopicId}</p>}
            </div>

            {/* 封面图片链接 */}
            <div className="space-y-2">
              <Label htmlFor="coverImage">封面图片链接</Label>
              <Input
                id="coverImage"
                name="coverImage"
                placeholder="请输入封面图片URL"
                value={form.coverImage}
                onChange={handleInputChange}
              />
            </div>

            {/* 是否置顶 */}
            <div className="space-y-2 flex items-center">
              <input
                type="checkbox"
                id="isTop"
                name="isTop"
                checked={form.isTop}
                onChange={handleCheckboxChange}
                className="mr-2"
              />
              <Label htmlFor="isTop">是否置顶</Label>
            </div>

            {/* 状态选择 */}
            <div className="space-y-2">
              <Label htmlFor="status">状态</Label>
              <Select
                value={form.status}
                onValueChange={(value) => handleSelectChange('status', value)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="待发布">待发布</SelectItem>
                  <SelectItem value="已发布">已发布</SelectItem>
                  <SelectItem value="已下线">已下线</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* ���闻摘要 */}
          <div className="space-y-2">
            <Label htmlFor="summary">新闻摘要 <span className="text-red-500">*</span></Label>
            <Textarea
              id="summary"
              name="summary"
              placeholder="请输入新闻摘要"
              value={form.summary}
              onChange={handleInputChange}
              className={`min-h-20 ${errors.summary ? 'border-red-500' : ''}`}
            />
            {errors.summary && <p className="text-red-500 text-sm">{errors.summary}</p>}
          </div>

          {/* 新闻内容 */}
          <div className="space-y-2">
            <Label htmlFor="content">新闻内容 <span className="text-red-500">*</span></Label>
            <Textarea
              id="content"
              name="content"
              placeholder="请输入新闻内容"
              value={form.content}
              onChange={handleInputChange}
              className={`min-h-[200px] ${errors.content ? 'border-red-500' : ''}`}
            />
            {errors.content && <p className="text-red-500 text-sm">{errors.content}</p>}
          </div>
        </form>
      </CardContent>

      <CardFooter className="flex justify-between border-t p-4">
        <Button
          type="button"
          onClick={resetForm}
          variant="outline"
          className="gap-1"
        >
          <AiOutlineClear /> 重置表单
        </Button>
        <Button
          type="submit"
          onClick={handleSubmit}
          disabled={loading || !selectedNewsId}
          className="gap-1 bg-blue-600 hover:bg-blue-700"
        >
          <FaEdit /> {loading ? '更新中...' : '更新新闻'}
        </Button>
      </CardFooter>
    </Card>
  );
};

export default EditNewsForm;
