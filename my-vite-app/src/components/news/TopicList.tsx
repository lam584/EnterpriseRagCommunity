import { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchAllTopics } from '../../services/NewsService';
import { Topic } from '../../types/news';
import { Button } from '../ui/button';
import { Skeleton } from '../ui/skeleton';
import { ScrollArea } from '../ui/scroll-area';

interface TopicListProps {
  horizontal?: boolean;
  onSelectTopic?: (topicId: number) => void;
}

export function TopicList({ horizontal = false, onSelectTopic }: TopicListProps) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [loading, setLoading] = useState(true);
  const { topicId } = useParams<{ topicId: string }>();

  useEffect(() => {
    const loadTopics = async () => {
      try {
        setLoading(true);
        const data = await fetchAllTopics();
        setTopics(data);
      } catch (error) {
        console.error('Failed to load topics:', error);
      } finally {
        setLoading(false);
      }
    };

    loadTopics();
  }, []);

  if (loading) {
    return horizontal ? (
      <div className="flex space-x-2 overflow-x-auto py-2">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-8 w-20 rounded-full" />
        ))}
      </div>
    ) : (
      <div className="space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={i} className="h-8 w-full rounded-md" />
        ))}
      </div>
    );
  }

  if (!topics.length) {
    return <div className="text-center py-2 text-sm text-gray-500">暂无主题</div>;
  }

  if (horizontal) {
    return (
      <ScrollArea className="w-full whitespace-nowrap pb-2">
        <div className="flex space-x-2">
          <Button
            key="all"
            variant={!topicId ? "default" : "outline"}
            size="sm"
            className="rounded-full"
            asChild={!onSelectTopic}
            onClick={onSelectTopic ? () => onSelectTopic(0) : undefined}
          >
            {onSelectTopic ? (
              <span>全部</span>
            ) : (
              <Link to="/news">全部</Link>
            )}
          </Button>

          {topics.map((topic) => (
            <Button
              key={topic.id}
              variant={topicId === String(topic.id) ? "default" : "outline"}
              size="sm"
              className="rounded-full"
              asChild={!onSelectTopic}
              onClick={onSelectTopic ? () => onSelectTopic(topic.id) : undefined}
            >
              {onSelectTopic ? (
                <span>{topic.name}</span>
              ) : (
                <Link to={`/news/topic/${topic.id}`}>{topic.name}</Link>
              )}
            </Button>
          ))}
        </div>
      </ScrollArea>
    );
  }

  return (
    <div className="space-y-2">
      <Button
        key="all"
        variant={!topicId ? "default" : "outline"}
        className="w-full justify-start"
        asChild={!onSelectTopic}
        onClick={onSelectTopic ? () => onSelectTopic(0) : undefined}
      >
        {onSelectTopic ? (
          <span>全部新闻</span>
        ) : (
          <Link to="/news">全部新闻</Link>
        )}
      </Button>

      {topics.map((topic) => (
        <Button
          key={topic.id}
          variant={topicId === String(topic.id) ? "default" : "outline"}
          className="w-full justify-start"
          asChild={!onSelectTopic}
          onClick={onSelectTopic ? () => onSelectTopic(topic.id) : undefined}
        >
          {onSelectTopic ? (
            <span>{topic.name}</span>
          ) : (
            <Link to={`/news/topic/${topic.id}`}>{topic.name}</Link>
          )}
        </Button>
      ))}
    </div>
  );
}
