import { useState, useEffect } from 'react';
// import { NewsItem, NewsResponse, NewsSearchCriteria } from '../../types/news';
import { NewsResponse, NewsSearchCriteria } from '../../types/news';
import { NewsCard } from './NewsCard';
import { fetchNewsList } from '../../services/NewsService';
import { Pagination } from '../ui/pagination';
import {
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '../ui/pagination';
import { Skeleton } from '../ui/skeleton';

interface NewsListProps {
  initialCriteria?: NewsSearchCriteria;
  variant?: 'grid' | 'list';
  showPagination?: boolean;
}

export function NewsList({
  initialCriteria = { page: 0, size: 12 },
  variant = 'grid',
  showPagination = true
}: NewsListProps) {
  const [loading, setLoading] = useState<boolean>(true);
  const [newsData, setNewsData] = useState<NewsResponse | null>(null);
  const [criteria, setCriteria] = useState<NewsSearchCriteria>(initialCriteria);

  useEffect(() => {
    const loadNews = async () => {
      setLoading(true);
      try {
        const data = await fetchNewsList(criteria);
        setNewsData(data);
      } catch (error) {
        console.error('Failed to load news:', error);
      } finally {
        setLoading(false);
      }
    };

    loadNews();
  }, [criteria]);

  const handlePageChange = (page: number) => {
    setCriteria(prev => ({ ...prev, page }));
    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // 获取要显示的页码范围
  const getPaginationRange = () => {
    if (!newsData) return [];

    const totalPages = newsData.totalPages;
    const currentPage = newsData.number;
    const delta = 1; // 当前页前后各显示多少页

    const range = [];
    const rangeWithDots = [];

    for (let i = 0; i < totalPages; i++) {
      if (
        i === 0 || // 第一页
        i === totalPages - 1 || // 最后一页
        (i >= currentPage - delta && i <= currentPage + delta) // 当前页附近的页码
      ) {
        range.push(i);
      }
    }

    let prev = 0;
    for (const i of range) {
      if (i - prev === 2) {
        rangeWithDots.push(prev + 1);
      } else if (i - prev !== 1) {
        rangeWithDots.push('...');
      }
      rangeWithDots.push(i);
      prev = i;
    }

    return rangeWithDots;
  };

  if (loading && !newsData) {
    return (
      <div className={variant === 'grid'
        ? "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
        : "flex flex-col space-y-4"
      }>
        {Array.from({ length: initialCriteria.size || 12 }).map((_, i) => (
          <div key={i} className="space-y-3">
            <Skeleton className="h-[200px] w-full rounded-lg" />
            <div className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-[80%]" />
              <div className="flex justify-between">
                <Skeleton className="h-3 w-[100px]" />
                <Skeleton className="h-3 w-[60px]" />
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!newsData?.content.length) {
    return (
      <div className="text-center py-10">
        <h3 className="text-lg font-medium text-gray-500">暂无新闻</h3>
        <p className="text-sm text-gray-400 mt-2">请稍后再试或更改搜索条件</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className={variant === 'grid'
        ? "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
        : "flex flex-col space-y-6"
      }>
        {newsData.content.map((newsItem) => (
          <div key={newsItem.id}>
            <NewsCard
              news={newsItem}
              variant={variant === 'list' ? 'horizontal' : 'default'}
            />
          </div>
        ))}
      </div>

      {showPagination && newsData.totalPages > 1 && (
        <Pagination className="mt-8">
          <PaginationContent>
            <PaginationItem>
              <PaginationPrevious
                onClick={() => newsData.first ? null : handlePageChange(newsData.number - 1)}
                className={newsData.first ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              />
            </PaginationItem>

            {getPaginationRange().map((page, i) => (
              <PaginationItem key={i}>
                {page === '...' ? (
                  <span className="px-4 py-2">...</span>
                ) : (
                  <PaginationLink
                    onClick={() => handlePageChange(page as number)}
                    isActive={newsData.number === page}
                    className="cursor-pointer"
                  >
                    {(page as number) + 1}
                  </PaginationLink>
                )}
              </PaginationItem>
            ))}

            <PaginationItem>
              <PaginationNext
                onClick={() => newsData.last ? null : handlePageChange(newsData.number + 1)}
                className={newsData.last ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}
    </div>
  );
}
