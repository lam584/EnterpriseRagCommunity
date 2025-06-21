// src/components/comment-management/ReviewComments.tsx
import React, { useState, useEffect } from 'react';

interface Comment {
    id: string;
    newsTitle: string;
    newsId: string;
    content: string;
    userName: string;
    userId: string;
    createTime: string;
    status: 'pending' | 'approved' | 'rejected';
    reportCount?: number;
}

const ReviewComments: React.FC = () => {
    const [comments, setComments] = useState<Comment[]>([]);
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState({ type: '', text: '' });
    const [filter, setFilter] = useState('pending'); // pending, all

    // 模拟获取评论数据
    useEffect(() => {
        setLoading(true);

        // 模拟API请求
        setTimeout(() => {
            const mockComments: Comment[] = [
                { id: '1', newsTitle: '国内疫情最新情况', newsId: '101', content: '希望疫情早日结束，大家都能恢复正常生活！', userName: '关心市民', userId: 'u1001', createTime: '2023-09-18 14:30', status: 'pending' },
                { id: '2', newsTitle: '人工智能技术最新进展', newsId: '102', content: '人工智能的发展真是日新月异，期待更多创新应用！', userName: '科技迷', userId: 'u1002', createTime: '2023-09-18 15:10', status: 'pending' },
                { id: '3', newsTitle: '本市今日天气情况', newsId: '103', content: '今天天气真好，适合户外活动。', userName: '晴天', userId: 'u1003', createTime: '2023-09-18 09:22', status: 'approved' },
                { id: '4', newsTitle: '国内疫情最新情况', newsId: '101', content: '这篇报道很及时，信息很全面。', userName: '新闻迷', userId: 'u1004', createTime: '2023-09-17 23:15', status: 'pending', reportCount: 0 },
                { id: '5', newsTitle: '世界杯最新赛况', newsId: '105', content: '比赛真精彩！支持国家队！', userName: '球迷一号', userId: 'u1005', createTime: '2023-09-17 22:05', status: 'pending', reportCount: 0 },
                { id: '6', newsTitle: '世界杯最新赛况', newsId: '105', content: '裁判的判罚有问题，明显偏向对方！！！', userName: '愤怒球迷', userId: 'u1006', createTime: '2023-09-17 22:30', status: 'pending', reportCount: 3 },
                { id: '7', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '国产科幻电影的里程碑之作，支持！', userName: '电影爱好者', userId: 'u1007', createTime: '2023-09-17 20:18', status: 'pending', reportCount: 0 },
                { id: '8', newsTitle: '电影《流浪地球2》票房突破40亿', newsId: '106', content: '剧情设计得真好，特效也很震撼！', userName: '影评人', userId: 'u1008', createTime: '2023-09-17 20:45', status: 'pending', reportCount: 0 },
            ];

            if (filter === 'pending') {
                setComments(mockComments.filter(comment => comment.status === 'pending'));
            } else {
                setComments(mockComments);
            }

            setLoading(false);
        }, 1000);
    }, [filter]);

    // 处理评论审核
    const handleReview = (commentId: string, action: 'approve' | 'reject') => {
        setComments(prevComments =>
            prevComments.map(comment =>
                comment.id === commentId
                    ? { ...comment, status: action === 'approve' ? 'approved' : 'rejected' }
                    : comment
            )
        );

        // 设置消息提示
        setMessage({
            type: 'success',
            text: action === 'approve' ? '评论已通过审核' : '评论已被拒绝'
        });

        // 清除消息提示
        setTimeout(() => {
            setMessage({ type: '', text: '' });
        }, 3000);
    };

    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4">评论审核</h2>

            {/* 过滤器 */}
            <div className="mb-6">
                <div className="flex space-x-2">
                    <button
                        onClick={() => setFilter('pending')}
                        className={`px-4 py-2 rounded ${
                            filter === 'pending' 
                                ? 'bg-purple-600 text-white' 
                                : 'bg-gray-200 hover:bg-gray-300'
                        }`}
                    >
                        待审核评论
                    </button>
                    <button
                        onClick={() => setFilter('all')}
                        className={`px-4 py-2 rounded ${
                            filter === 'all' 
                                ? 'bg-purple-600 text-white' 
                                : 'bg-gray-200 hover:bg-gray-300'
                        }`}
                    >
                        全部评论
                    </button>
                </div>
            </div>

            {message.text && (
                <div className={`p-3 rounded mb-4 ${
                    message.type === 'error' ? 'bg-red-100 text-red-700' : 
                    'bg-green-100 text-green-700'
                }`}>
                    {message.text}
                </div>
            )}

            {/* 评论列表 */}
            {loading ? (
                <div className="text-center py-6">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-700 mx-auto"></div>
                    <p className="mt-2 text-gray-500">加载中...</p>
                </div>
            ) : comments.length > 0 ? (
                <div className="space-y-4">
                    {comments.map((comment) => (
                        <div
                            key={comment.id}
                            className={`p-4 border rounded-lg ${
                                comment.reportCount && comment.reportCount > 0 
                                    ? 'bg-red-50 border-red-200' 
                                    : 'bg-white border-gray-200'
                            }`}
                        >
                            <div className="flex justify-between items-start">
                                <div>
                                    <h3 className="font-medium">
                                        新闻：{comment.newsTitle}
                                    </h3>
                                    <div className="text-sm text-gray-500 mt-1">
                                        评论者：{comment.userName} | 时间：{comment.createTime}
                                        {comment.reportCount && comment.reportCount > 0 && (
                                            <span className="ml-2 text-red-600">
                                                被举报 {comment.reportCount} 次
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="flex space-x-2">
                                    {comment.status === 'pending' && (
                                        <>
                                            <button
                                                onClick={() => handleReview(comment.id, 'approve')}
                                                className="px-3 py-1 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                                            >
                                                通过
                                            </button>
                                            <button
                                                onClick={() => handleReview(comment.id, 'reject')}
                                                className="px-3 py-1 bg-red-600 text-white text-sm rounded hover:bg-red-700"
                                            >
                                                拒绝
                                            </button>
                                        </>
                                    )}
                                    {comment.status === 'approved' && (
                                        <span className="px-3 py-1 bg-green-100 text-green-800 text-sm rounded">
                                            已通过
                                        </span>
                                    )}
                                    {comment.status === 'rejected' && (
                                        <span className="px-3 py-1 bg-red-100 text-red-800 text-sm rounded">
                                            已拒绝
                                        </span>
                                    )}
                                </div>
                            </div>
                            <div className="mt-2 p-3 bg-gray-50 rounded">
                                {comment.content}
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="text-center py-10 text-gray-500">
                    没有需要审核的评论
                </div>
            )}
        </div>
    );
};

export default ReviewComments;
