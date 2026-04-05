import type { BoardDTO } from './boardService';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

const apiUrl = serviceApiUrl;


export async function listMyModeratedBoards(): Promise<BoardDTO[]> {
  const res = await fetch(apiUrl('/api/moderator/boards'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ([]));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取版主版块失败');
  return Array.isArray(data) ? (data as BoardDTO[]) : [];
}

