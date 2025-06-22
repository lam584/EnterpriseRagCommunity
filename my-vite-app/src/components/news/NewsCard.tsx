import { Link } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale';
import { NewsItem } from '../../types/news';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter
} from '../ui/card';
import { Badge } from '../ui/badge';
import { EyeIcon, MessageSquare, ThumbsUp } from 'lucide-react';

interface NewsCardProps {
  news: NewsItem;
  variant?: 'default' | 'compact' | 'horizontal';
}

export function NewsCard({ news, variant = 'default' }: NewsCardProps) {
  const publishDate = news.publishedAt ? new Date(news.publishedAt) : new Date(news.createdAt);
  const timeAgo = formatDistanceToNow(publishDate, { addSuffix: true, locale: zhCN });

  if (variant === 'compact') {
    return (
      <Link to={`/news/${news.id}`} className="block">
        <Card className="h-full hover:shadow-md transition-shadow">
          <CardContent className="p-3">
            <h3 className="text-sm font-medium line-clamp-2">{news.title}</h3>
            <div className="mt-1 text-xs text-gray-500">{timeAgo}</div>
          </CardContent>
        </Card>
      </Link>
    );
  }

  if (variant === 'horizontal') {
    return (
      <Link to={`/news/${news.id}`} className="block">
        <Card className="flex overflow-hidden hover:shadow-md transition-shadow">
          {news.coverImage && (
            <div className="w-1/3 min-w-[120px] bg-cover bg-center" style={{ backgroundImage: `url(${news.coverImage})` }} />
          )}
          <div className="flex flex-col p-4 flex-1">
            <CardHeader className="p-0 pb-2">
              <CardTitle className="text-lg font-bold line-clamp-2">{news.title}</CardTitle>
              <div className="flex items-center mt-1 space-x-2">
                <Badge variant="outline">{news.topicName}</Badge>
                <span className="text-xs text-gray-500">{timeAgo}</span>
              </div>
            </CardHeader>
            <CardContent className="p-0 flex-1">
              <p className="text-sm text-gray-600 line-clamp-2">{news.summary}</p>
            </CardContent>
            <CardFooter className="p-0 pt-2">
              <div className="flex items-center text-xs text-gray-500 space-x-4">
                <span className="flex items-center">
                  <EyeIcon className="w-3 h-3 mr-1" />
                  {news.viewCount}
                </span>
                <span className="flex items-center">
                  <MessageSquare className="w-3 h-3 mr-1" />
                  {news.commentCount}
                </span>
                <span className="flex items-center">
                  <ThumbsUp className="w-3 h-3 mr-1" />
                  {news.likeCount}
                </span>
              </div>
            </CardFooter>
          </div>
        </Card>
      </Link>
    );
  }

  // Default full card
  return (
    <Link to={`/news/${news.id}`} className="block">
      <Card className="h-full hover:shadow-md transition-shadow">
        {news.coverImage && (
          <div
            className="w-full h-48 bg-cover bg-center rounded-t-lg"
            style={{ backgroundImage: `url(${news.coverImage})` }}
          />
        )}
        <CardHeader className="p-4 pb-2">
          <div className="flex justify-between items-start">
            <CardTitle className="text-xl font-bold line-clamp-2">{news.title}</CardTitle>
          </div>
          <div className="flex items-center mt-2 space-x-2">
            <Badge variant="outline">{news.topicName}</Badge>
            <span className="text-xs text-gray-500">{timeAgo}</span>
          </div>
        </CardHeader>

        <CardContent className="p-4 pt-2">
          <p className="text-sm text-gray-600 line-clamp-3">{news.summary}</p>
        </CardContent>

        <CardFooter className="p-4 pt-2 flex justify-between">
          <div className="text-sm text-gray-500">
            作者: {news.authorName}
          </div>
          <div className="flex items-center space-x-4">
            <span className="flex items-center text-sm text-gray-500">
              <EyeIcon className="w-4 h-4 mr-1" />
              {news.viewCount}
            </span>
            <span className="flex items-center text-sm text-gray-500">
              <MessageSquare className="w-4 h-4 mr-1" />
              {news.commentCount}
            </span>
            <span className="flex items-center text-sm text-gray-500">
              <ThumbsUp className="w-4 h-4 mr-1" />
              {news.likeCount}
            </span>
          </div>
        </CardFooter>
      </Card>
    </Link>
  );
}
