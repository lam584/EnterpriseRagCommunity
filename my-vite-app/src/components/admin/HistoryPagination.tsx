import React from 'react';

export type HistoryPaginationProps = {
  pageNo: number;
  pageSize: number;
  totalPages: number;
  loading: boolean;
  onPageSizeChange: (nextSize: number) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
};

export const HistoryPagination: React.FC<HistoryPaginationProps> = ({
  pageNo,
  pageSize,
  totalPages,
  loading,
  onPageSizeChange,
  onPrevPage,
  onNextPage,
}) => {
  return (
    <div className="flex flex-wrap items-center justify-end gap-2 pt-2">
      <div className="flex items-center gap-2">
        <span className="text-xs text-gray-500">每页</span>
        <select
          className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs disabled:opacity-50"
          value={pageSize}
          disabled={loading}
          onChange={(e) => {
            const nextSize = Math.max(1, Math.trunc(Number(e.target.value) || 20));
            onPageSizeChange(nextSize);
          }}
        >
          {[10, 20, 50, 100].map((n) => (
            <option key={n} value={n}>
              {n} 条
            </option>
          ))}
        </select>
      </div>
      <button
        type="button"
        className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
        onClick={onPrevPage}
        disabled={loading || pageNo <= 0}
      >
        上一页
      </button>
      <div className="text-xs text-gray-500">
        第 {pageNo + 1} 页 / 共 {totalPages || 0} 页
      </div>
      <button
        type="button"
        className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
        onClick={onNextPage}
        disabled={loading || (totalPages > 0 && pageNo + 1 >= totalPages)}
      >
        下一页
      </button>
    </div>
  );
};
