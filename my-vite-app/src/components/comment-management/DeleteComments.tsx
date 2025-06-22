// src/components/comment-management/DeleteComments.tsx
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
}

const DeleteComments: React.FC = () => {
    const [searchTerm, setSearchTerm] = useState('');
    const [searchType, setSearchType] = useState('content');
    const [loading, setLoading] = useState(false);
    const [searchResults, setSearchResults] = useState<Comment[]>([]);
    const [message, setMessage] = useState({ type: '', text: '' });

    // mn评论数据
    const mockComments: Comment[] = [
        { id: '1', newsTitle: '国内疫情最新情况', newsId: '101', content: '希望疫情早日结束，大家都能恢复正常生活！', userName: '关心市民', userId: 'u1001', createTime: '2023-09-18 14:30', status: 'approved' },
        { id: '2', newsTitle: '人工智能技术最新进展', newsId: '102', content: '人工智能的发展真是日新月异，期待更多创新应用！', userName: '科技迷', userId: 'u1002', createTime: '2023-09-18 15:10', status: 'approved' },
        { id: '3', newsTitle: '本市今日天气情况', newsId: '103', content: '今天天气真好，适合户外活动。', userName: '晴天', userId: 'u1003', createTime: '2023-09-18 09:22', status: 'approved' },
        { id: '4', newsTitle: '国内疫情最新情况', newsId: '101', content: '这篇报道很及时，信息很全面。', userName: '新闻迷', userId: 'u1004', createTime: '2023-09-17 23:15', status: 'approved' },
        { id: '5', newsTitle: '世界杯最新赛况', newsId: '105', content: '比赛真精彩！支持国家队！', userName: '球迷一号', userId: 'u1005', createTime: '2023-09-17 22:05', status: 'approved' },
        { id: '6', newsTitle: '世界杯最新赛况', newsId: '105', content: '这场比赛太精彩了，球员们都发挥得很出色！', userName: '体育迷', userId: 'u1006', createTime: '2023-09-17 22:30', status: 'approved' },
        { id: '7', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '国产科幻电影的里程碑之作，支持！', userName: '电影爱好者', userId: 'u1007', createTime: '2023-09-17 20:18', status: 'approved' },
        { id: '8', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '剧情设计得真好，特效也很震撼！', userName: '影评人', userId: 'u1008', createTime: '2023-09-17 20:45', status: 'approved' },
    ];

    const handleSearch = () => {
        if (!searchTerm.trim()) {
            setMessage({ type: 'error', text: '请输入搜索关键词' });
            return;
        }

        setLoading(true);
        setMessage({ type: '', text: '' });

        // mnAPI搜索
        setTimeout(() => {
            let results: Comment[] = [];

            switch(searchType) {
                case 'content':
                    results = mockComments.filter(comment =>
                        comment.content.toLowerCase().includes(searchTerm.toLowerCase())
                    );
                    break;
                case 'user':
                    results = mockComments.filter(comment =>
                        comment.userName.toLowerCase().includes(searchTerm.toLowerCase())
                    );
                    break;
                case 'news':
                    results = mockComments.filter(comment =>
                        comment.newsTitle.toLowerCase().includes(searchTerm.toLowerCase())
                    );
                    break;
                default:
                    results = [];
            }

            setSearchResults(results);
            setLoading(false);

            if (results.length === 0) {
                setMessage({ type: 'info', text: '未找到匹配的评论' });
            }
        }, 800);
    };

    const handleDelete = (commentId: string) => {
        if (window.confirm('确定要删除此评论吗？此操作不可恢复。')) {
            setMessage({ type: 'info', text: '正在删除评论...' });

            // mn删除操作
            setTimeout(() => {
                setSearchResults(prev => prev.filter(comment => comment.id !== commentId));
                setMessage({ type: 'success', text: '评论已成功删除' });

                // 3秒后清除成功消息
                setTimeout(() => {
                    if (setMessage) { // 检查组件是否还挂载
                        setMessage(prev => {
                            if (prev.type === 'success' && prev.text === '评论已成功删除') {
                                return { type: '', text: '' };
                            }
                            return prev;
                        });
                    }
                }, 3000);
            }, 1000);
        }
    };

    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4">删除评论</h2>

            {/* 搜索控件 */}
            <div className="mb-6 p-4 bg-gray-50 rounded-md">
                <div className="flex mb-4">
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
                        className="flex-1 px-3 py-2 border-t border-b border-gray-300 focus:outline-none focus:ring-2 focus:ring-purple-500"
                    />
                    <button
                        onClick={handleSearch}
                        disabled={loading}
                        className="px-4 py-2 bg-purple-600 text-white rounded-r-md hover:bg-purple-700 focus:outline-none disabled:bg-purple-300"
                    >
                        {loading ? '搜索中...' : '搜索'}
                    </button>
                </div>

                <div className="text-sm text-gray-600">
                    提示：可以通过评论内容、用户名或新闻标题搜索评论
                </div>
            </div>

            {message.text && (
                <div className={`p-3 rounded mb-4 ${
                    message.type === 'error' ? 'bg-red-100 text-red-700' : 
                    message.type === 'success' ? 'bg-green-100 text-green-700' :
                    'bg-blue-100 text-blue-700'
                }`}>
                    {message.text}
                </div>
            )}

            {/* 搜索结果 */}
            {searchResults.length > 0 && (
                <div className="space-y-4 mt-4">
                    {searchResults.map((comment) => (
                        <div
                            key={comment.id}
                            className="p-4 border border-gray-200 rounded-lg bg-white"
                        >
                            <div className="flex justify-between items-start">
                                <div>
                                    <h3 className="font-medium">
                                        新闻：{comment.newsTitle}
                                    </h3>
                                    <div className="text-sm text-gray-500 mt-1">
                                        评论者：{comment.userName} | 时间：{comment.createTime}
                                    </div>
                                </div>
                                <button
                                    onClick={() => handleDelete(comment.id)}
                                    className="px-3 py-1 bg-red-600 text-white text-sm rounded hover:bg-red-700"
                                >
                                    删除
                                </button>
                            </div>
                            <div className="mt-2 p-3 bg-gray-50 rounded">
                                {comment.content}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default DeleteComments;
