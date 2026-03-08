import React from 'react';
import type { RetrievalEventLogDTO, RetrievalHitLogDTO } from '../../../../services/retrievalHybridService';

type UiClasses = {
  btnSecondaryClass: string;
};

type Props = {
  ui: UiClasses;
  loading: boolean;
  logs: RetrievalEventLogDTO[];
  logsPage: number;
  logsTotal: number;
  logsTotalPages: number;
  selectedEventId: number | null;
  selectedHits: RetrievalHitLogDTO[] | null;

  fmtDateTime: (v: unknown) => string;
  onRefresh: () => void;
  onSelectEvent: (eventId: number) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
};

const HybridLogsSection: React.FC<Props> = ({
  ui,
  loading,
  logs,
  logsPage,
  logsTotal,
  logsTotalPages,
  selectedEventId,
  selectedHits,
  fmtDateTime,
  onRefresh,
  onSelectEvent,
  onPrevPage,
  onNextPage,
}) => {
  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="flex items-center justify-between">
        <div className="font-medium">日志（检索事件 / 检索命中）</div>
        <button className={ui.btnSecondaryClass} onClick={onRefresh} disabled={loading}>
          刷新
        </button>
      </div>
      <div className="overflow-auto border border-gray-100 rounded">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-2 py-2 text-left">时间</th>
              <th className="px-2 py-2 text-left">事件编号</th>
              <th className="px-2 py-2 text-left">数量（BM25/向量/混合）</th>
              <th className="px-2 py-2 text-left">重排</th>
              <th className="px-2 py-2 text-left">查询</th>
              <th className="px-2 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {logs.map(ev => (
              <tr key={ev.id} className="border-t">
                <td className="px-2 py-2 whitespace-nowrap">{fmtDateTime(ev.createdAt)}</td>
                <td className="px-2 py-2 whitespace-nowrap">{ev.id}</td>
                <td className="px-2 py-2 whitespace-nowrap">
                  {(ev.bm25K ?? 0).toString()}/{(ev.vecK ?? 0).toString()}/{(ev.hybridK ?? '—').toString()}
                </td>
                <td className="px-2 py-2 whitespace-nowrap">
                  {(ev.rerankModel ?? '—').toString()} / {(ev.rerankK ?? '—').toString()}
                </td>
                <td className="px-2 py-2">{(ev.queryText ?? '').toString().slice(0, 120)}</td>
                <td className="px-2 py-2 whitespace-nowrap text-right">
                  <button className={ui.btnSecondaryClass} onClick={() => onSelectEvent(ev.id)} disabled={loading}>
                    查看命中
                  </button>
                </td>
              </tr>
            ))}
            {logs.length === 0 && (
              <tr>
                <td className="px-2 py-6 text-center text-gray-500" colSpan={6}>
                  暂无日志
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <div className="flex items-center justify-between">
        <div className="text-xs text-gray-500">
          第 {logsPage + 1} / {logsTotalPages} 页，共 {logsTotal} 条
        </div>
        <div className="flex items-center gap-2">
          <button className={ui.btnSecondaryClass} disabled={loading || logsPage <= 0} onClick={onPrevPage}>
            上一页
          </button>
          <button className={ui.btnSecondaryClass} disabled={loading || logsPage + 1 >= logsTotalPages} onClick={onNextPage}>
            下一页
          </button>
        </div>
      </div>

      {selectedEventId != null && (
        <div className="rounded border border-gray-100 p-2">
          <div className="font-medium">命中详情：事件编号={selectedEventId}</div>
          {!selectedHits && <div className="text-gray-500 text-sm mt-1">加载中…</div>}
          {selectedHits && (
            <div className="mt-2 overflow-auto border border-gray-100 rounded">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-2 py-2 text-left">排名</th>
                    <th className="px-2 py-2 text-left">类型</th>
                    <th className="px-2 py-2 text-left">文档编号</th>
                    <th className="px-2 py-2 text-left">分片编号</th>
                    <th className="px-2 py-2 text-left">得分</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedHits.map(h => (
                    <tr key={h.id} className="border-t">
                      <td className="px-2 py-2">{h.rank ?? '—'}</td>
                      <td className="px-2 py-2">{h.hitType ?? '—'}</td>
                      <td className="px-2 py-2">{h.postId ?? '—'}</td>
                      <td className="px-2 py-2">{h.chunkId ?? '—'}</td>
                      <td className="px-2 py-2">{h.score ?? '—'}</td>
                    </tr>
                  ))}
                  {selectedHits.length === 0 && (
                    <tr>
                      <td className="px-2 py-6 text-center text-gray-500" colSpan={5}>
                        无命中
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default HybridLogsSection;

