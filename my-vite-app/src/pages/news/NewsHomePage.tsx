// src/pages/news/NewsHomePage.tsx
import { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { mockNews, mockTopics, getNewsByTopic, getHotNews, NewsItem, Topic } from '../../mockData/newsData';

export const NewsHomePage = () => {
  const { topicId } = useParams<{ topicId: string }>();
  const [news, setNews] = useState<NewsItem[]>([]);
  const [hotNews, setHotNews] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [currentTopic, setCurrentTopic] = useState<Topic | null>(null);

  useEffect(() => {
    // mn加载数据
    const fetchData = () => {
      setLoading(true);

      // 根据主题ID过滤新闻或获取所有新闻
      let filteredNews: NewsItem[];
      if (topicId) {
        filteredNews = getNewsByTopic(topicId);
        const topic = mockTopics.find(t => t.id === topicId) || null;
        setCurrentTopic(topic);
      } else {
        filteredNews = [...mockNews];
        setCurrentTopic(null);
      }

      // 获取热门新闻
      const hot = getHotNews(5);

      // mn网络请求延迟
      setTimeout(() => {
        setNews(filteredNews);
        setHotNews(hot);
        setLoading(false);
      }, 500);
    };

    fetchData();
  }, [topicId]);

  if (loading) {
    return <div className="flex justify-center items-center min-h-screen">加载中...</div>;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <header className="mb-8">
        <h1 className="text-3xl font-bold mb-2">新闻中心</h1>
        <div className="flex flex-wrap gap-2 mb-4">
          <Link
            to="/news"
            className={`px-4 py-2 rounded-full ${!currentTopic ? 'bg-blue-600 text-white' : 'bg-gray-200'}`}
          >
            全部
          </Link>
          {mockTopics.map(topic => (
            <Link
              key={topic.id}
              to={`/news/topic/${topic.id}`}
              className={`px-4 py-2 rounded-full ${currentTopic?.id === topic.id ? 'bg-blue-600 text-white' : 'bg-gray-200'}`}
            >
              {topic.name}
            </Link>
          ))}
        </div>
        {currentTopic && (
          <div className="bg-gray-100 p-4 rounded-lg">
            <h2 className="text-xl font-semibold mb-1">{currentTopic.name}</h2>
            {currentTopic.description && <p className="text-gray-600">{currentTopic.description}</p>}
          </div>
        )}
      </header>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* 主要新闻列表 */}
        <div className="md:col-span-2">
          <h2 className="text-2xl font-bold mb-4">
            {currentTopic ? `${currentTopic.name}新闻` : '最新新闻'}
          </h2>

          {news.length === 0 ? (
            <div className="bg-gray-100 p-6 rounded-lg text-center">
              暂无相关新闻
            </div>
          ) : (
            <div className="space-y-6">
              {news.map(item => (
                <div key={item.id} className="bg-white p-4 rounded-lg shadow hover:shadow-md transition-shadow">
                  <Link to={`/news/${item.id}`} className="block">
                    {item.coverImage && (
                      <div className="mb-3 overflow-hidden rounded-lg">
                        <img
                          src={item.coverImage}
                          alt={item.title}
                          className="w-full h-48 object-cover hover:scale-105 transition-transform"
                        />
                      </div>
                    )}
                    <h3 className="text-xl font-bold mb-2 hover:text-blue-600">{item.title}</h3>
                    <p className="text-gray-600 mb-2">{item.summary}</p>
                    <div className="flex justify-between text-sm text-gray-500">
                      <span>{item.author}</span>
                      <span>{item.publishDate}</span>
                    </div>
                  </Link>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 侧边栏 */}
        <div className="md:col-span-1">
          <div className="bg-gray-50 p-4 rounded-lg shadow">
            <h3 className="text-lg font-bold mb-4 border-b pb-2">热门新闻</h3>
            <div className="space-y-4">
              {hotNews.map(item => (
                <div key={item.id} className="border-b pb-3 last:border-b-0">
                  <Link to={`/news/${item.id}`} className="hover:text-blue-600">
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
        </div>
      </div>
    </div>
  );
};
