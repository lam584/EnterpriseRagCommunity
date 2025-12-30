import { getCsrfToken } from '../utils/csrfUtils';
import type { NotificationDTO, PageDTO } from '../types/notification';

const API_BASE = '/api/notifications';

export async function fetchNotifications(params?: {
  type?: string;
  unreadOnly?: boolean;
  page?: number;
  pageSize?: number;
}): Promise<PageDTO<NotificationDTO>> {
  const url = new URL(API_BASE, window.location.origin);
  if (params?.type) url.searchParams.set('type', params.type);
  if (params?.unreadOnly !== undefined) url.searchParams.set('unreadOnly', String(params.unreadOnly));
  url.searchParams.set('page', String(params?.page ?? 1));
  url.searchParams.set('pageSize', String(params?.pageSize ?? 20));

  const res = await fetch(url.toString().replace(window.location.origin, ''), {
    credentials: 'include',
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '获取通知失败' }));
    throw new Error(errorData.message || '获取通知失败');
  }
  return res.json();
}

export async function fetchUnreadCount(): Promise<number> {
  const res = await fetch(`${API_BASE}/unread-count`, { credentials: 'include' });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '获取未读数失败' }));
    throw new Error(errorData.message || '获取未读数失败');
  }
  const data = await res.json();
  return Number(data.count ?? 0);
}

export async function markNotificationRead(id: number): Promise<NotificationDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/${id}/read`, {
    method: 'PATCH',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '标记已读失败' }));
    throw new Error(errorData.message || '标记已读失败');
  }
  return res.json();
}

export async function markNotificationsRead(ids: number[]): Promise<number> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/read`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ ids }),
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '批量标记已读失败' }));
    throw new Error(errorData.message || '批量标记已读失败');
  }
  const data = await res.json();
  return Number(data.updated ?? 0);
}

export async function deleteNotification(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '删除通知失败' }));
    throw new Error(errorData.message || '删除通知失败');
  }
}

