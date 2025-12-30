import { useMemo } from 'react';
import type { NotificationDTO } from '../../../../types/notification';

export default function NotificationList(props: {
  items: NotificationDTO[];
  onMarkRead?: (id: number) => void;
  onDelete?: (id: number) => void;
}) {
  const sorted = useMemo(() => {
    const base = props.items ?? [];
    return [...base].sort((a, b) => {
      const ta = new Date(a.createdAt).getTime();
      const tb = new Date(b.createdAt).getTime();
      return tb - ta;
    });
  }, [props.items]);

  if (sorted.length === 0) {
    return <div className="text-gray-500">暂无通知</div>;
  }

  return (
    <div className="space-y-2">
      {sorted.map((n) => {
        const unread = !n.readAt;
        return (
          <div
            key={n.id}
            className={`rounded border p-3 ${unread ? 'border-blue-200 bg-blue-50' : 'border-gray-200 bg-white'}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="font-medium text-gray-900 truncate">{n.title}</div>
                {n.content ? <div className="mt-1 text-sm text-gray-700 break-words">{n.content}</div> : null}
                <div className="mt-2 text-xs text-gray-500">{new Date(n.createdAt).toLocaleString()}</div>
              </div>

              <div className="flex shrink-0 items-center gap-2">
                {unread && props.onMarkRead ? (
                  <button
                    type="button"
                    className="text-xs text-blue-700 hover:underline"
                    onClick={() => props.onMarkRead?.(n.id)}
                  >
                    标为已读
                  </button>
                ) : null}
                {props.onDelete ? (
                  <button
                    type="button"
                    className="text-xs text-gray-600 hover:underline"
                    onClick={() => props.onDelete?.(n.id)}
                  >
                    删除
                  </button>
                ) : null}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
