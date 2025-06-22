import React, { useState, useEffect } from 'react';
import { FaSearch, FaFilter, FaFileCsv, FaArrowLeft, FaArrowRight, FaSyncAlt, FaTimes, FaInfoCircle } from 'react-icons/fa';
import { Card, CardHeader, CardContent, CardTitle } from '../ui/card';
import { Button } from '../ui/button';
import { Alert, AlertTitle, AlertDescription } from '../ui/alert';
import {
  NewsDTO,
  searchNewsBasic,
  searchNewsAdvanced
} from '../../services/NewsService';

interface BasicCriteria {
  title: string;
  author: string;
  categoryId: string;
  status: string;
}
interface AdvancedCriteria extends BasicCriteria {
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
  const itemsPerPage = 10;
  const [categories, setCategories] = useState<{id:number,name:string}[]>([]);
  // 基本 & 高级条件
  const [criteria, setCriteria] = useState<BasicCriteria>({ title:'', author:'', categoryId:'', status:'' });
  const [advCriteria, setAdvCriteria] = useState<AdvancedCriteria>({
    id:'', idExact:false, title:'', titleExact:false,
    author:'', authorExact:false, categoryId:'', categoryIdExact:false,
    status:'', createdStartDate:'', createdEndDate:'',
    updatedStartDate:'', updatedEndDate:'',
    viewsMin:'', viewsMax:'', likesMin:'', likesMax:'',
    commentCountMin:'', commentCountMax:'', isTop:false
  });
  // UI 状态
  const [showAdvancedSearch, setShowAdvancedSearch] = useState(false);
  const [isAdvancedSearchActive, setIsAdvancedSearchActive] = useState(false);
  const [searchResults, setSearchResults] = useState<NewsDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [isSearched, setIsSearched] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    // mn拉取分类
    setCategories([
      {id:1,name:'政治'},{id:2,name:'经济'},{id:3,name:'科技'},
      {id:4,name:'文化'},{id:5,name:'体育'},{id:6,name:'娱乐'},{id:7,name:'社会'}
    ]);
  }, []);

  // 输入处理略（与原来类似）

  /** 核心：分页拉数据 */
  const fetchData = async (page: number, advanced: boolean) => {
    setLoading(true);
    setMessage('');
    try {
      const resp = advanced
          ? await searchNewsAdvanced(advCriteria, page - 1, itemsPerPage)
          : await searchNewsBasic(criteria, page - 1, itemsPerPage);
      // 类型转换，确保类型兼容
      setSearchResults(resp.content as unknown as NewsDTO[]);
      setTotalPages(resp.totalPages);
      setMessage(resp.totalElements > 0
          ? `查询到 ${resp.totalElements} 条结果` : '没有找到匹配的新闻');
      setIsSearched(true);
      setCurrentPage(page);
    } catch (err) {
      console.error(err);
      setMessage('搜索失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleBasicSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setIsAdvancedSearchActive(false);
    fetchData(1, false);
  };
  const handleAdvancedSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setIsAdvancedSearchActive(true);
    fetchData(1, true);
  };
  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      fetchData(page, isAdvancedSearchActive);
    }
  };

  /** CSV 导出 */
  const exportToCSV = () => {
    if (searchResults.length === 0) {
      setMessage('没有数据可以导出');
      return;
    }
    const headers = ['ID','标题','作者','分类','状态','创建时间','更新时间','阅读量','点赞数','评论数'];
    const rows = searchResults.map(n => [
      n.id.toString(), 
      n.title, 
      n.authorName || '', 
      n.topicName || '',
      n.status,
      new Date(n.createdAt).toLocaleString(),
      n.updatedAt ? new Date(n.updatedAt).toLocaleString() : '',
      (n.viewCount ?? 0).toString(),
      (n.likeCount ?? 0).toString(),
      (n.commentCount ?? 0).toString()
    ]);
    const csv = [
      headers.join(','),
      ...rows.map(r => r.map(c=>`"${c}"`).join(','))
    ].join('\n');
    const blob = new Blob([csv], { type:'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `新闻搜索结果_${new Date().toISOString().slice(0,10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
      <Card className="w-full bg-white">
        <CardHeader className="bg-gradient-to-r from-blue-600 to-purple-600 text-white">
          <CardTitle className="text-2xl flex items-center gap-2">
            <FaSearch/> 查找新闻
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-6">
          {/* 基本表单 */}
          <form onSubmit={handleBasicSearch} className="mb-6">
            {/* 四列输入 */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">标题</label>
                <input
                  type="text"
                  name="title"
                  value={criteria.title}
                  onChange={e => setCriteria({...criteria, title: e.target.value})}
                  className="w-full p-2 border rounded"
                  placeholder="输入新闻标题"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">作者</label>
                <input
                  type="text"
                  name="author"
                  value={criteria.author}
                  onChange={e => setCriteria({...criteria, author: e.target.value})}
                  className="w-full p-2 border rounded"
                  placeholder="输入作者名称"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">分类</label>
                <select
                  name="categoryId"
                  value={criteria.categoryId}
                  onChange={e => setCriteria({...criteria, categoryId: e.target.value})}
                  className="w-full p-2 border rounded"
                >
                  <option value="">全部分类</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">状态</label>
                <select
                  name="status"
                  value={criteria.status}
                  onChange={e => setCriteria({...criteria, status: e.target.value})}
                  className="w-full p-2 border rounded"
                >
                  <option value="">全部状态</option>
                  <option value="DRAFT">草稿</option>
                  <option value="PUBLISHED">已发布</option>
                  <option value="UNDER_REVIEW">审核中</option>
                  <option value="REJECTED">已拒绝</option>
                </select>
              </div>
            </div>
            <div className="mt-4 flex justify-between">
              <div className="flex gap-2">
                <Button type="button" variant="outline" onClick={()=>{
                  setCriteria({ title:'',author:'',categoryId:'',status:'' });
                  setIsSearched(false);
                  setMessage('');
                  setSearchResults([]);
                }}>
                  <FaTimes/> 清空
                </Button>
                <Button type="button" variant={showAdvancedSearch ? 'secondary':'outline'} onClick={()=>setShowAdvancedSearch(x=>!x)}>
                  <FaFilter/> {showAdvancedSearch?'收起高级':'高级搜索'}
                </Button>
              </div>
              <Button type="submit" className="bg-blue-600 text-white flex items-center gap-1" disabled={loading}>
                {loading? <FaSyncAlt className="animate-spin"/>:<FaSearch/>}
                {loading?'搜索中...':'搜索新闻'}
              </Button>
            </div>
          </form>

          {/* 高级表单 */}
          {showAdvancedSearch && (
              <form onSubmit={handleAdvancedSearch} className="border-t pt-4 mb-6">
                {/* 各种高级输入项，省略，保持原来结构，只要 name、value 对应上 advCriteria */}
                <div className="flex justify-between">
                  <Button type="button" variant="outline" onClick={()=>{
                    setAdvCriteria({
                      id:'',idExact:false,title:'',titleExact:false,
                      author:'',authorExact:false,categoryId:'',categoryIdExact:false,
                      status:'',createdStartDate:'',createdEndDate:'',
                      updatedStartDate:'',updatedEndDate:'',
                      viewsMin:'',viewsMax:'',likesMin:'',likesMax:'',
                      commentCountMin:'',commentCountMax:'',isTop:false
                    });
                  }}>
                    <FaTimes/> 清空高级
                  </Button>
                  <Button type="submit" className="bg-blue-600 text-white flex items-center gap-1" disabled={loading}>
                    {loading? <FaSyncAlt className="animate-spin"/>:<FaSearch/>}
                    {loading?'高级搜索中...':'执行高级搜索'}
                  </Button>
                </div>
              </form>
          )}

          <Alert className="mb-6">
            <AlertTitle>提示</AlertTitle>
            <AlertDescription>
              可以通过基本/高级搜索快速筛选新闻。
            </AlertDescription>
          </Alert>

          {/* 结果区域 */}
          {isSearched ? (
              <div className="mt-6">
                <div className="flex justify-between mb-4">
                  <div className="text-sm text-gray-500">{message}</div>
                  {searchResults.length>0 && (
                      <Button variant="outline" size="sm" onClick={exportToCSV}>
                        <FaFileCsv/> 导出CSV
                      </Button>
                  )}
                </div>
                {searchResults.length>0 ? (
                    <>
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left border-collapse">
                          <thead className="bg-gray-100 uppercase text-xs text-gray-700">
                          <tr>
                            <th className="px-4 py-2">ID</th>
                            <th className="px-4 py-2">标题</th>
                            <th className="px-4 py-2">作者</th>
                            <th className="px-4 py-2">分类</th>
                            <th className="px-4 py-2">状态</th>
                            <th className="px-4 py-2 text-center">阅读量</th>
                            <th className="px-4 py-2 text-center">点赞</th>
                            <th className="px-4 py-2 text-center">评论</th>
                            <th className="px-4 py-2">更新时间</th>
                          </tr>
                          </thead>
                          <tbody>
                          {searchResults.map((n, i) => (
                              <tr key={n.id} className={`${i%2? 'bg-gray-50':''} border-b`}>
                                <td className="px-4 py-2">{n.id}</td>
                                <td className="px-4 py-2">{n.title}</td>
                                <td className="px-4 py-2">{n.authorName}</td>
                                <td className="px-4 py-2">{n.topicName}</td>
                                <td className="px-4 py-2">
                            <span className={`px-2 py-1 text-xs rounded ${
                                n.status==='已发布'?'bg-green-100 text-green-800':
                                    n.status==='待发布'?'bg-yellow-100 text-yellow-800':
                                        'bg-gray-100 text-gray-800'}`}>
                              {n.status}
                            </span>
                                </td>
                                <td className="px-4 py-2 text-center"><FaSearch className="inline-block mr-1"/> {n.viewCount ?? 0}</td>
                                <td className="px-4 py-2 text-center"><FaSearch className="inline-block mr-1"/> {n.likeCount ?? 0}</td>
                                <td className="px-4 py-2 text-center"><FaSearch className="inline-block mr-1"/> {n.commentCount ?? 0}</td>
                                <td className="px-4 py-2">{n.updatedAt ? new Date(n.updatedAt).toLocaleString() : '-'}</td>
                              </tr>
                          ))}
                          </tbody>
                        </table>
                      </div>
                      {/* 分页 */}
                      {totalPages>1 && (
                          <div className="flex justify-between items-center mt-4">
                            <div className="text-sm text-gray-500">第 {currentPage} 页 / 共 {totalPages} 页</div>
                            <div className="flex gap-2">
                              <Button size="sm" variant="outline" onClick={()=>handlePageChange(currentPage-1)} disabled={currentPage===1}><FaArrowLeft/></Button>
                              {Array.from({length: totalPages}, (_,i)=>i+1).map(p=>(
                                  <Button key={p} size="sm" variant={p===currentPage?'default':'outline'} onClick={()=>handlePageChange(p)}>{p}</Button>
                              ))}
                              <Button size="sm" variant="outline" onClick={()=>handlePageChange(currentPage+1)} disabled={currentPage===totalPages}><FaArrowRight/></Button>
                            </div>
                          </div>
                      )}
                    </>
                ) : (
                    <div className="text-center py-10">
                      <FaInfoCircle className="mx-auto text-gray-400 text-4xl mb-2"/>
                      <p className="text-gray-500">没有找到匹配的新闻记录</p>
                    </div>
                )}
              </div>
          ) : (
              <div className="text-center py-16">
                <FaSearch className="mx-auto text-gray-300 text-5xl mb-4"/>
                <p className="text-gray-500">请使用上方搜索框查找新闻</p>
              </div>
          )}
        </CardContent>
      </Card>
  );
};

export default SearchNews;
