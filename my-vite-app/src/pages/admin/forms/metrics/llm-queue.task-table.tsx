import { useEffect, useMemo, useState } from 'react';
import type { LlmQueueTaskDTO } from '../../../../services/llmQueueAdminService';
import { fmtHmsTs, fmtMs, fmtNum, fmtType } from './llm-queue.sparkline';

export function TaskTable({
  title,
  tasks,
  pageable,
  showIndex = false,
  showDuration = true,
  showTokensPerSec = true,
  showTokensOut = true,
  showModelProvider = false,
  providerNameById = {},
  onViewDetails,
  typeLabels = {},
}: {
  title: string;
  tasks: LlmQueueTaskDTO[];
  pageable?: boolean;
  showIndex?: boolean;
  showDuration?: boolean;
  showTokensPerSec?: boolean;
  showTokensOut?: boolean;
  showModelProvider?: boolean;
  providerNameById?: Record<string, string>;
  onViewDetails?: (task: LlmQueueTaskDTO) => void;
  typeLabels?: Record<string, string>;
}) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(5);
  const [pageSizeInput, setPageSizeInput] = useState('5');
  const pageCount = useMemo(() => Math.max(1, Math.ceil(tasks.length / Math.max(1, pageSize))), [tasks.length, pageSize]);
  useEffect(() => {
    setPage((p) => Math.min(Math.max(0, p), Math.max(0, pageCount - 1)));
  }, [pageCount]);
  useEffect(() => {
    setPageSizeInput(String(pageSize));
  }, [pageSize]);
  const hasPrevPage = pageable && page > 0;
  const hasNextPage = pageable && page < pageCount - 1;
  const showPageIndicator = pageable && pageCount > 1;

  const shownTasks = useMemo(() => {
    if (!pageable) return tasks;
    const size = Math.max(1, pageSize);
    const start = Math.max(0, page) * size;
    return tasks.slice(start, start + size);
  }, [tasks, pageable, page, pageSize]);

  const colCount =
    6 +
    (showIndex ? 1 : 0) +
    (showDuration ? 1 : 0) +
    (showTokensPerSec ? 1 : 0) +
    (showTokensOut ? 1 : 0) +
    (onViewDetails ? 1 : 0);

  const formatType = (t: string | undefined | null) => {
    const x = (t || 'UNKNOWN').toString().trim().toUpperCase();
    return typeLabels[x] || fmtType(x);
  };

  const formatModelText = (t: LlmQueueTaskDTO) => {
    const model = String(t.model ?? '').trim();
    if (!showModelProvider) return model || '—';
    const pid = String(t.providerId ?? '').trim();
    const providerName = pid ? providerNameById[pid] || pid : '';
    if (providerName && model) return `${providerName}：${model}`;
    return providerName || model || '—';
  };

  const rowBase = useMemo(() => {
    if (!pageable) return 0;
    const size = Math.max(1, pageSize);
    return Math.max(0, page) * size;
  }, [pageable, page, pageSize]);

  return (
    <div className="rounded border">
      <div className="px-3 py-2 bg-gray-50 flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm font-semibold">{title}</div>
        {pageable && tasks.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600 font-normal">
            <div className="hidden sm:block">共 {tasks.length} 条</div>
            <div className="flex items-center gap-1">
              <span>每页</span>
              <input
                className="rounded border px-2 py-1 bg-white w-20"
                inputMode="numeric"
                value={pageSizeInput}
                onChange={(e) => {
                  const v = e.target.value;
                  setPageSizeInput(v);
                  const n = Math.floor(Number(v));
                  if (!Number.isFinite(n) || n < 1) return;
                  if (n !== pageSize) {
                    setPageSize(n);
                    setPage(0);
                  }
                }}
                onBlur={() => {
                  const n = Math.floor(Number(pageSizeInput));
                  if (!Number.isFinite(n) || n < 1) setPageSizeInput(String(pageSize));
                }}
              />
              <select
                className="rounded border px-2 py-1 bg-white"
                value="__quick__"
                onChange={(e) => {
                  const n = Math.max(1, Number(e.target.value) || 5);
                  setPageSize(n);
                  setPage(0);
                }}
              >
                <option value="__quick__" disabled>
                  快选
                </option>
                {[5, 15, 50, 200, 500, 1000].map((n) => (
                  <option key={n} value={String(n)}>
                    {n}
                  </option>
                ))}
              </select>
            </div>
            {hasPrevPage ? (
              <button type="button" className="rounded border px-2 py-1 bg-white" onClick={() => setPage((p) => Math.max(0, p - 1))}>
                上一页
              </button>
            ) : null}
            {hasNextPage ? (
              <button
                type="button"
                className="rounded border px-2 py-1 bg-white"
                onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
              >
                下一页
              </button>
            ) : null}
            {showPageIndicator ? (
              <div className="hidden sm:block">
                第 {page + 1}/{pageCount} 页
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-white text-gray-700">
            <tr className="text-left">
              {showIndex ? <th className="pl-2 pr-1 py-2 text-right w-12 whitespace-nowrap">序号</th> : null}
              <th className="px-3 py-2">类型</th>
              <th className="px-3 py-2">状态</th>
              <th className="px-3 py-2">时间</th>
              <th className="px-3 py-2">模型</th>
              <th className="px-3 py-2 text-right">等待</th>
              {showDuration ? <th className="px-3 py-2 text-right">耗时</th> : null}
              {showTokensPerSec ? <th className="px-3 py-2 text-right">tokens/sec</th> : null}
              {showTokensOut ? <th className="px-3 py-2 text-right">tokens(out)</th> : null}
              <th className="px-3 py-2">错误</th>
              {onViewDetails ? <th className="px-3 py-2">操作</th> : null}
            </tr>
          </thead>
          <tbody>
            {shownTasks.length === 0 ? (
              <tr className="border-t">
                <td className="px-3 py-3 text-gray-500" colSpan={colCount}>
                  暂无数据
                </td>
              </tr>
            ) : (
              shownTasks.map((t, i) => (
                <tr key={t.id} className="border-t">
                  {showIndex ? <td className="pl-2 pr-1 py-2 text-right tabular-nums w-12 whitespace-nowrap">{rowBase + i + 1}</td> : null}
                  <td className="px-3 py-2">{formatType(t.type)}</td>
                  <td className="px-3 py-2 font-mono text-xs">{t.status}</td>
                  <td className="px-3 py-2 text-xs text-gray-500 whitespace-nowrap">{fmtHmsTs(t.finishedAt || t.createdAt)}</td>
                  <td className="px-3 py-2 font-mono text-xs break-all">{formatModelText(t)}</td>
                  <td className="px-3 py-2 text-right">{fmtMs(t.waitMs)}</td>
                  {showDuration ? <td className="px-3 py-2 text-right">{fmtMs(t.durationMs)}</td> : null}
                  {showTokensPerSec ? (
                    <td className="px-3 py-2 text-right font-mono text-xs">{t.tokensPerSec == null ? '—' : fmtNum(t.tokensPerSec)}</td>
                  ) : null}
                  {showTokensOut ? (
                    <td className="px-3 py-2 text-right font-mono text-xs">{t.tokensOut == null ? '—' : fmtNum(t.tokensOut)}</td>
                  ) : null}
                  <td className="px-3 py-2 text-xs">
                    <div className="max-w-[360px] truncate" title={t.error || ''}>
                      {t.status === 'FAILED' ? t.error || '—' : '—'}
                    </div>
                  </td>
                  {onViewDetails ? (
                    <td className="px-3 py-2 text-xs whitespace-nowrap">
                      <button type="button" className="rounded border px-2 py-1 hover:bg-gray-50" onClick={() => onViewDetails(t)}>
                        详情
                      </button>
                    </td>
                  ) : null}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
