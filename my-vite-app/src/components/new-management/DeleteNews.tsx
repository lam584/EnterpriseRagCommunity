import React, { useState, useEffect, useRef } from 'react';
import { searchNews, deleteNews, NewsDTO } from '../../services/NewsService';
import { Card, CardHeader, CardContent, CardTitle } from '../ui/card';
import { Label } from '../ui/label';
import { Input } from '../ui/input';
import { Button } from '../ui/button';
import { Alert, AlertTitle, AlertDescription } from '../ui/alert';
import {
  Select, SelectTrigger, SelectValue,
  SelectContent, SelectItem
} from '../ui/select';
import {
  FaTrash, FaCheckCircle, FaTimesCircle,
  FaSearch, FaExclamationTriangle
} from 'react-icons/fa';

// const MAX_RECENT_NEWS = 10;

const DeleteNews: React.FC = () => {
  const [filteredNews, setFilteredNews] = useState<NewsDTO[]>([]);
  const [selected, setSelected] = useState<NewsDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [message, setMessage] = useState<{type:'success'|'error', text:string}|null>(null);

  const [criteria, setCriteria] = useState({
    searchField: 'title',
    keyword: ''
  });

  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [inside, setInside] = useState(false);

  // 点击页面任意处关闭下拉
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!inside &&
          dropdownRef.current &&
          !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [inside]);

  // 每次搜索条件变化时调用后端
  useEffect(() => {
    if (!criteria.keyword.trim()) {
      setFilteredNews([]);
      return;
    }
    const fn = async () => {
      try {
        setLoading(true);
        // 修改参数传递方式，构建一个NewsSearchCriteria对象
        const searchCriteria = {
          [criteria.searchField]: criteria.keyword
        };
        const list = await searchNews(searchCriteria);
        setFilteredNews(list);
        setDropdownOpen(true);
      } catch {
        setMessage({type:'error', text:'搜索失败'});
      } finally {
        setLoading(false);
      }
    };
    fn();
  }, [criteria]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setCriteria(prev => ({...prev, [name]: value}));
    setMessage(null);
    setShowConfirm(false);
    setSelected(null);
  };

  const pick = (item: NewsDTO) => {
    setSelected(item);
    setShowConfirm(true);
    setDropdownOpen(false);
  };

  const confirmDelete = async () => {
    if (!selected) return;
    try {
      setLoading(true);
      await deleteNews(selected.id);
      setMessage({type:'success', text:`“${selected.title}” 已删除`});
      setSelected(null);
      setShowConfirm(false);
      setFilteredNews(prev => prev.filter(n => n.id !== selected.id));
    } catch {
      setMessage({type:'error', text:'删除失败，请重试'});
    } finally {
      setLoading(false);
    }
  };

  const cancelDelete = () => {
    setShowConfirm(false);
    setSelected(null);
  };

  return (
      <Card className="max-w-3xl mx-auto bg-white">
        <CardHeader className="bg-red-600 text-white">
          <CardTitle className="flex items-center gap-2">
            <FaTrash /> 删除新闻
          </CardTitle>
        </CardHeader>
        <CardContent className="p-6">
          {message && (
              <Alert className={`mb-4 ${message.type==='success' ? 'bg-green-50 border-green-200 text-green-800' : 'bg-red-50 border-red-200 text-red-800'}`}>
                <AlertTitle className="flex items-center gap-2">
                  {message.type==='success' ? <FaCheckCircle className="text-green-500"/> : <FaTimesCircle className="text-red-500"/>}
                  {message.type==='success' ? '成功' : '错误'}
                </AlertTitle>
                <AlertDescription>{message.text}</AlertDescription>
              </Alert>
          )}

          {/* 确认删除 */}
          {showConfirm && selected && (
              <Alert className="mb-6 bg-yellow-50 text-yellow-800 border-yellow-200">
                <AlertTitle className="flex items-center gap-2">
                  <FaExclamationTriangle className="text-yellow-500"/> 确认删除
                </AlertTitle>
                <AlertDescription>
                  <p>您确定要删除以下新闻么？此操作不可撤销。</p>
                  <div className="border border-yellow-200 rounded p-4 bg-white mt-2">
                    <h3 className="font-bold">{selected.title}</h3>
                    <p className="text-sm text-gray-600">作者: {selected.authorName}</p>
                    <p className="text-sm text-gray-600">ID: {selected.id}</p>
                    <p className="mt-2 text-gray-700">{selected.summary}</p>
                  </div>
                  <div className="mt-4 flex justify-end gap-2">
                    <Button variant="outline" onClick={cancelDelete} disabled={loading}
                            className="border-yellow-500 text-yellow-700 hover:bg-yellow-50">
                      取消
                    </Button>
                    <Button onClick={confirmDelete} className="bg-red-500 text-white hover:bg-red-600"
                            disabled={loading}>
                      {loading?'删除中...':'确认删除'}
                    </Button>
                  </div>
                </AlertDescription>
              </Alert>
          )}

          {/* 搜索区域 */}
          <div className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 items-end">
              <div>
                <Label htmlFor="searchField">搜索字段</Label>
                <Select value={criteria.searchField}
                        onValueChange={(v: string)=>handleChange({target:{name:'searchField',value:v}} as never)}>
                  <SelectTrigger><SelectValue/></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="title">标题</SelectItem>
                    <SelectItem value="author">作者</SelectItem>
                    <SelectItem value="id">ID</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="md:col-span-2 relative">
                <Label htmlFor="keyword">关键词</Label>
                <Input id="keyword" name="keyword"
                       value={criteria.keyword}
                       onChange={handleChange}
                       onFocus={()=>setDropdownOpen(true)}
                       className="pr-8"/>
                <FaSearch className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400"/>
              </div>
            </div>

            {dropdownOpen && filteredNews.length>0 && (
                <div ref={dropdownRef}
                     onMouseEnter={()=>setInside(true)}
                     onMouseLeave={()=>setInside(false)}
                     className="mt-1 border bg-white rounded shadow max-h-64 overflow-auto z-10">
                  {filteredNews.map(item=>(
                      <div key={item.id}
                           className="p-3 hover:bg-gray-100 cursor-pointer border-b last:border-0"
                           onClick={()=>pick(item)}>
                        <div className="font-medium">{item.title}</div>
                        <div className="text-sm text-gray-500 flex justify-between">
                          <span>作者: {item.authorName}</span>
                          <span>ID: {item.id}</span>
                        </div>
                      </div>
                  ))}
                </div>
            )}
          </div>

          {(!dropdownOpen || filteredNews.length===0) && !showConfirm && (
              <div className="text-center text-gray-500 py-12">
                <p>请输入关键词搜索新闻，然后从下拉列表中选择要删除的项。</p>
              </div>
          )}
        </CardContent>
      </Card>
  );
};

export default DeleteNews;