import type { PostDTO } from '../../../../services/postService';
import type { SpringPage } from '../../../../types/page';
import PostCard from './PostCard';
import type { JSX } from 'react';

export type PostFeedProps = {
  page: SpringPage<PostDTO> | null;
  loading: boolean;
  error: string | null;
  onRetry?: () => void;
  onPrev?: () => void;
  onNext?: () => void;
  showStatus?: boolean;
  /** Optional actions area rendered inside each PostCard. */
  renderActions?: (post: PostDTO) => JSX.Element | null;
};

function SkeletonRow() {
  return (
    <div className="flex gap-3 rounded-xl border border-gray-200 bg-white p-4">
      <div className="h-10 w-10 rounded-full bg-gray-200" />
      <div className="flex-1 space-y-2">
        <div className="h-3 w-2/5 bg-gray-200 rounded" />
        <div className="h-4 w-4/5 bg-gray-200 rounded" />
        <div className="h-3 w-3/5 bg-gray-200 rounded" />
      </div>
      <div className="h-20 w-28 bg-gray-200 rounded-lg" />
    </div>
  );
}

export default function PostFeed({ page, loading, error, onRetry, onPrev, onNext, showStatus, renderActions }: PostFeedProps) {
  const content = page?.content ?? [];

  const totalElements = page?.totalElements ?? content.length;
  const pageSize = page?.size ?? content.length;
  const totalPages = page?.totalPages ?? (pageSize > 0 ? Math.ceil(totalElements / pageSize) : 0);
  const showPagination = !!page && totalPages > 1;

  return (
    <div>
      {error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 flex items-center justify-between gap-3">
          <span className="min-w-0 truncate">加载失败：{error}</span>
          {onRetry ? (
            <button className="shrink-0 px-3 py-1 rounded-md bg-red-600 text-white hover:bg-red-700" onClick={onRetry}>
              重试
            </button>
          ) : null}
        </div>
      ) : null}

      {loading && !page ? (
        <>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </>
      ) : null}

      {!loading && !error && content.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600">暂无帖子</div>
      ) : null}

      {content.map((p: PostDTO) => (
        <PostCard key={p.id} post={p} showStatus={showStatus} renderActions={renderActions} />
      ))}

      {page ? (
        <div className="flex items-center justify-between pt-2">
          <div className="text-xs text-gray-500">
            第 {Math.max(1, (page.number ?? 0) + 1)} 页 / 共 {page.totalPages ?? 0} 页，{page.totalElements ?? 0} 条
          </div>
          {showPagination ? (
            <div className="flex gap-2">
              <button
                className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
                onClick={onPrev}
                disabled={loading || !!page.first || (page.number ?? 0) <= 0}
                type="button"
              >
                上一页
              </button>
              <button
                className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
                onClick={onNext}
                disabled={loading || !!page.last || (page.totalPages ?? 0) <= 0}
                type="button"
              >
                下一页
              </button>
            </div>
          ) : (
            <div />
          )}
        </div>
      ) : null}
    </div>
  );
}
