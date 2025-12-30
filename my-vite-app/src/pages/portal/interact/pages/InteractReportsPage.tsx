import { useEffect, useMemo, useState } from 'react';
import NotificationList from '../components/NotificationList';
import { deleteNotification, fetchNotifications, markNotificationsRead, markNotificationRead } from '../../../../services/notificationService';
import type { NotificationDTO } from '../../../../types/notification';

function toErrorMessage(e: unknown): string {
  if (e instanceof Error) return e.message;
  if (typeof e === 'string') return e;
  try {
    return JSON.stringify(e);
  } catch {
    return '加载失败';
  }
}

export default function InteractReportsPage() {
  const [items, setItems] = useState<NotificationDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const unreadIds = useMemo(() => items.filter((x) => !x.readAt).map((x) => x.id), [items]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchNotifications({ type: 'REPORT', page: 1, pageSize: 50 });
      setItems(page.content ?? []);
    } catch (e: unknown) {
      setError(toErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function onMarkRead(id: number) {
    await markNotificationRead(id);
    await load();
  }

  async function onDelete(id: number) {
    await deleteNotification(id);
    await load();
  }

  async function markAllRead() {
    if (unreadIds.length === 0) return;
    await markNotificationsRead(unreadIds);
    await load();
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">举报</h3>
        <button
          type="button"
          className="text-sm text-blue-700 hover:underline disabled:text-gray-400"
          onClick={markAllRead}
          disabled={unreadIds.length === 0}
        >
          全部标为已读
        </button>
      </div>

      {loading ? <div className="text-gray-500">加载中...</div> : null}
      {error ? <div className="text-red-600">{error}</div> : null}

      <NotificationList items={items} onMarkRead={onMarkRead} onDelete={onDelete} />
    </div>
  );
}
