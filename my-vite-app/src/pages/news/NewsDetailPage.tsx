// src/pages/news/NewsDetailPage.tsx
import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { getNewsById, mockTopics, getHotNews } from '../../mockData/newsData';

// 模拟评论数据
interface Comment {
  id: string;
  username: string;
  content: string;
  time: string;
  avatar: string;
}

// 模拟评论数据初始化函数
const generateMockComments = (newsId: string): Comment[] => {
  return [
    {
      id: `${newsId}-1`,
      username: '张三',
      content: '这篇新闻真是太有见地了，感谢分享！',
      time: '2025-06-20 15:30',
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=张三'
    },
    {
      id: `${newsId}-2`,
      username: '李四',
      content: '对此事有不同看法，我认为还需要更多的调查和研究。',
      time: '2025-06-21 09:45',
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=李四'
    },
    {
      id: `${newsId}-3`,
      username: '王五',
      content: '支持作者的观点，希望能有后续报道！',
      time: '2025-06-21 14:15',
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=王五'
    }
  ];
};

export const NewsDetailPage = () => {
  const { newsId } = useParams<{ newsId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [newsItem, setNewsItem] = useState<any>(null);
  const [hotNews, setHotNews] = useState<any[]>([]);

  // 评论相关状态
  const [comments, setComments] = useState<Comment[]>([]);
  const [newComment, setNewComment] = useState('');
  const [commentUsername, setCommentUsername] = useState('访客');

  useEffect(() => {
    // mn加载数据
    const fetchData = async () => {
      setLoading(true);

      if (newsId) {
        // 获取新闻详情
        const newsDetail = getNewsById(newsId);

        if (!newsDetail) {
          // 如果新闻不存在，重定向到新闻主页
          navigate('/news');
          return;
        }

        setNewsItem(newsDetail);

        // 获取热门新闻
        const hot = getHotNews(5);
        setHotNews(hot);

        // 设置模拟评论数据
        const mockComments = generateMockComments(newsId);
        setComments(mockComments);
      }

      // mn网络请求延迟
      setTimeout(() => {
        setLoading(false);
      }, 500);
    };

    fetchData();
  }, [newsId, navigate]);

  // 提交评论
  const handleCommentSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!newComment.trim()) return;

    // 生成新评论的ID
    const commentId = `${newsId}-${comments.length + 1}`;

    // 创建新评论对象
    const comment: Comment = {
      id: commentId,
      username: commentUsername,
      content: newComment.trim(),
      time: new Date().toISOString(),
      avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${commentUsername}`
    };

    // 更新评论状态
    setComments([...comments, comment]);
    setNewComment('');
  };

  if (loading) {
    return <div className="flex justify-center items-center min-h-screen">加载中...</div>;
  }

  if (!newsItem) {
    return <div className="container mx-auto px-4 py-8">新闻不存在</div>;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-4">
        <Link to="/news" className="text-blue-600 hover:underline flex items-center">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          返回新闻列表
        </Link>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* 新闻详情 */}
        <div className="md:col-span-2">
          <article className="bg-white p-6 rounded-lg shadow">
            <h1 className="text-3xl font-bold mb-4">{newsItem.title}</h1>

            <div className="flex flex-wrap items-center text-gray-500 mb-6 text-sm">
              <span className="mr-4">作者: {newsItem.author}</span>
              <span className="mr-4">发布时间: {newsItem.publishDate}</span>
              <span>浏览: {newsItem.views}</span>

              <div className="w-full mt-2 flex flex-wrap gap-2">
                {newsItem.topics.map((topicId: string) => {
                  const topic = mockTopics.find(t => t.id === topicId);
                  return topic ? (
                    <Link
                      key={topic.id}
                      to={`/news/topic/${topic.id}`}
                      className="bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded"
                    >
                      {topic.name}
                    </Link>
                  ) : null;
                })}
              </div>
            </div>

            {newsItem.coverImage && (
              <div className="mb-6">
                <img
                  src={newsItem.coverImage}
                  alt={newsItem.title}
                  className="w-full rounded-lg"
                />
              </div>
            )}

            <div className="prose max-w-none">
              {newsItem.content.split('\n').map((paragraph: string, index: number) => (
                <p key={index} className="mb-4">{paragraph.trim()}</p>
              ))}
            </div>
          </article>

          {/* 评论区 */}
          <div className="mt-8">
            <h3 className="text-lg font-bold mb-4">评论区</h3>

            <div className="space-y-4">
              {comments.map((comment) => (
                <div key={comment.id} className="flex gap-4 border-b pb-4 mb-4 last:border-b-0">
                  <img src={comment.avatar} alt={comment.username} className="w-10 h-10 rounded-full" />
                  <div className="flex-1">
                    <div className="flex justify-between items-center mb-1">
                      <span className="font-medium">{comment.username}</span>
                      <span className="text-xs text-gray-500">{comment.time}</span>
                    </div>
                    <p className="text-gray-700">{comment.content}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* 评论表单 */}
            <form onSubmit={handleCommentSubmit} className="mt-4 bg-gray-50 p-4 rounded-lg shadow">
              <h4 className="text-md font-semibold mb-2">添加评论</h4>
              <div className="flex gap-4">
                <input
                  type="text"
                  value={commentUsername}
                  onChange={(e) => setCommentUsername(e.target.value)}
                  placeholder="昵称"
                  className="flex-1 p-2 border rounded"
                />
              </div>
              <div className="mt-2">
                <textarea
                  value={newComment}
                  onChange={(e) => setNewComment(e.target.value)}
                  placeholder="评论内容"
                  className="w-full p-2 border rounded resize-none"
                  rows={3}
                />
              </div>
              <button
                type="submit"
                className="mt-2 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                发表评论
              </button>
            </form>
          </div>
        </div>

        {/* 侧边栏 */}
        <div className="md:col-span-1">
          <div className="bg-gray-50 p-4 rounded-lg shadow mb-6">
            <h3 className="text-lg font-bold mb-4 border-b pb-2">热门新闻</h3>
            <div className="space-y-4">
              {hotNews.map((item) => (
                <div key={item.id} className="border-b pb-3 last:border-b-0">
                  <Link
                    to={`/news/${item.id}`}
                    className={`hover:text-blue-600 ${item.id === newsId ? 'text-blue-600 font-medium' : ''}`}
                  >
                    <h4 className="font-medium mb-1">{item.title}</h4>
                    <div className="flex justify-between text-xs text-gray-500">
                      <span>{item.publishDate}</span>
                      <span>浏览: {item.views}</span>
                    </div>
                  </Link>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-gray-50 p-4 rounded-lg shadow">
            <h3 className="text-lg font-bold mb-4 border-b pb-2">新闻分类</h3>
            <div className="space-y-2">
              {mockTopics.map((topic) => (
                <Link
                  key={topic.id}
                  to={`/news/topic/${topic.id}`}
                  className="block hover:bg-gray-100 p-2 rounded"
                >
                  {topic.name}
                  {topic.description && (
                    <span className="text-xs text-gray-500 block">{topic.description}</span>
                  )}
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
