import type { ReactNode } from 'react';
import type { IndexSyncStatus } from '../../../../services/retrievalIndexSyncAdminService';

export function renderIndexStatus(status?: IndexSyncStatus): ReactNode {
  if (!status) return <span className="text-gray-400">加载中</span>;
  const isOk = status.indexed;
  const label = isOk ? (status.reason || '已同步') : (status.reason || '失败');
  return (
    <div className="flex items-center gap-2">
      <span className={isOk ? 'text-emerald-700' : 'text-rose-700'}>{label}</span>
      <span className="text-xs text-gray-500">({status.docCount ?? 0})</span>
      {!isOk ? (
        <button
          type="button"
          className="text-blue-600 hover:underline text-xs"
          onClick={() => {
            const detail = [
              `状态: ${status.status ?? '-'}`,
              `原因: ${status.reason ?? '-'}`,
              `详情: ${status.detail ?? '-'}`,
              `索引: ${status.indexName ?? '-'}`,
              `文档数: ${status.docCount ?? 0}`,
            ].join('\n');
            window.alert(detail);
          }}
        >
          查看详情
        </button>
      ) : null}
    </div>
  );
}
