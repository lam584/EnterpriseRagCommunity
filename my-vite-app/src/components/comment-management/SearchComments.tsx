// src/components/comment-management/SearchComments.tsx
import React, { useState } from 'react';

interface Comment {
    id: string;
    newsTitle: string;
    newsId: string;
    content: string;
    userName: string;
    userId: string;
    createTime: string;
    status: 'pending' | 'approved' | 'rejected';
    likes: number;
}

const SearchComments: React.FC = () => {
    const [searchTerm, setSearchTerm] = useState('');
    const [searchType, setSearchType] = useState('content');
    const [dateRange, setDateRange] = useState({ start: '', end: '' });
    const [loading, setLoading] = useState(false);
    const [searchResults, setSearchResults] = useState<Comment[]>([]);

    // 模拟评论数据
    const mockComments: Comment[] = [
        { id: '1', newsTitle: '国内疫情最新情况', newsId: '101', content: '希望疫情早日结束，大家都能恢复正常生活！', userName: '关心市民', userId: 'u1001', createTime: '2023-09-18 14:30', status: 'approved', likes: 12 },
        { id: '2', newsTitle: '人工智能技术最新进展', newsId: '102', content: '人工智能的发展真是日新月异，期待更多创新应用！', userName: '科技迷', userId: 'u1002', createTime: '2023-09-18 15:10', status: 'approved', likes: 8 },
        { id: '3', newsTitle: '本市今日天气情况', newsId: '103', content: '今天天气真好，适合户外活动。', userName: '晴天', userId: 'u1003', createTime: '2023-09-18 09:22', status: 'approved', likes: 3 },
        { id: '4', newsTitle: '国内疫情最新情况', newsId: '101', content: '这篇报道很及时，信息很全面。', userName: '新闻迷', userId: 'u1004', createTime: '2023-09-17 23:15', status: 'approved', likes: 7 },
        { id: '5', newsTitle: '世界杯最新赛况', newsId: '105', content: '比赛真精彩！支持国家队！', userName: '球迷一号', userId: 'u1005', createTime: '2023-09-17 22:05', status: 'approved', likes: 25 },
        { id: '6', newsTitle: '世界杯最新赛况', newsId: '105', content: '这场比赛太精彩了，球员们都发挥得很出色！', userName: '体育迷', userId: 'u1006', createTime: '2023-09-17 22:30', status: 'approved', likes: 15 },
        { id: '7', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '国产科幻电影的里程碑之作，支持！', userName: '电影爱好者', userId: 'u1007', createTime: '2023-09-16 20:18', status: 'approved', likes: 42 },
        { id: '8', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '剧情设计得真好，特效也很震撼！', userName: '影评人', userId: 'u1008', createTime: '2023-09-16 20:45', status: 'approved', likes: 32 },
        { id: '9', newsTitle: '新款智能手机发布', newsId: '107', content: '性价比很高，值得购买！', userName: '数码控', userId: 'u1009', createTime: '2023-09-15 16:30', status: 'approved', likes: 18 },
        { id: '10', newsTitle: '新款智能手机发布', newsId: '107', content: '外观设计一般，但性能还不错。', userName: '科技评测师', userId: 'u1010', createTime: '2023-09-15 17:20', status: 'approved', likes: 9 },
    ];

    const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;
        setDateRange(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleSearch = () => {
        setLoading(true);

        // 模拟API搜索
        setTimeout(() => {
            let results = [...mockComments];

            // 按关键词和类型筛选
            if (searchTerm) {
                switch(searchType) {
                    case 'content':
                        results = results.filter(comment =>
                            comment.content.toLowerCase().includes(searchTerm.toLowerCase())
                        );
                        break;
                    case 'user':
                        results = results.filter(comment =>
                            comment.userName.toLowerCase().includes(searchTerm.toLowerCase())
                        );
                        break;
                    case 'news':
                        results = results.filter(comment =>
                            comment.newsTitle.toLowerCase().includes(searchTerm.toLowerCase())
                        );
                        break;
                }
            }

            // 按日期范围筛选
            if (dateRange.start) {
                results = results.filter(comment => {
                    const commentDate = comment.createTime.split(' ')[0];
                    return commentDate >= dateRange.start;
                });
            }

            if (dateRange.end) {
                results = results.filter(comment => {
                    const commentDate = comment.createTime.split(' ')[0];
                    return commentDate <= dateRange.end;
                });
            }

            setSearchResults(results);
            setLoading(false);
        }, 800);
    };

    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4">评论查询</h2>

            {/* 搜索控件 */}
            <div className="mb-6 p-4 bg-gray-50 rounded-md">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                    <div className="flex">
                        <select
                            value={searchType}
                            onChange={(e) => setSearchType(e.target.value)}
                            className="w-32 px-3 py-2 border border-gray-300 rounded-l-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        >
                            <option value="content">评论内容</option>
                            <option value="user">用户名</option>
                            <option value="news">新闻标题</option>
                        </select>
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="输入搜索关键词"
                            className="flex-1 px-3 py-2 border-t border-r border-b border-gray-300 rounded-r-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />
                    </div>

                    <div className="flex space-x-4">
                        <div className="flex-1">
                            <label className="block text-sm text-gray-600 mb-1">开始日期</label>
                            <input
                                type="date"
                                name="start"
                                value={dateRange.start}
                                onChange={handleDateChange}
                                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            />
                        </div>
                        <div className="flex-1">
                            <label className="block text-sm text-gray-600 mb-1">结束日期</label>
                            <input
                                type="date"
                                name="end"
                                value={dateRange.end}
                                onChange={handleDateChange}
                                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                            />
                        </div>
                    </div>
                </div>

                <div className="flex justify-end">
                    <button
                        onClick={handleSearch}
                        disabled={loading}
                        className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 focus:outline-none disabled:bg-purple-300"
                    >
                        {loading ? '查询中...' : '查询'}
                    </button>
                </div>
            </div>

            {/* 搜索结果 */}
            {loading ? (
                <div className="text-center py-6">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-700 mx-auto"></div>
                    <p className="mt-2 text-gray-500">数据加载中...</p>
                </div>
            ) : searchResults.length > 0 ? (
                <div className="overflow-x-auto">
                    <table className="min-w-full bg-white border border-gray-200">
                        <thead>
                            <tr>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    ID
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    新闻标题
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    评论内容
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    评论者
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    评论时间
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    点赞数
                                </th>
                                <th className="px-4 py-3 border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    状态
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {searchResults.map((comment) => (
                                <tr key={comment.id}>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        {comment.id}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        {comment.newsTitle}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200 max-w-xs truncate">
                                        {comment.content}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        {comment.userName}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        {comment.createTime}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        {comment.likes}
                                    </td>
                                    <td className="px-4 py-3 border-b border-gray-200">
                                        <span className={`px-2 py-1 rounded text-xs ${
                                            comment.status === 'approved' ? 'bg-green-100 text-green-800' : 
                                            comment.status === 'rejected' ? 'bg-red-100 text-red-800' : 
                                            'bg-yellow-100 text-yellow-800'
                                        }`}>
                                            {comment.status === 'approved' ? '已通过' :
                                             comment.status === 'rejected' ? '已拒绝' : '待审核'}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            ) : (
                <div className="text-center py-10 text-gray-500">
                    {searchTerm || dateRange.start || dateRange.end ?
                        '没有找到符合条件的评论' :
                        '请输入搜索条件进行查询'}
                </div>
            )}
        </div>
    );
};

export default SearchComments;
