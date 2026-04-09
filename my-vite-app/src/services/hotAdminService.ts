import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type HotScoreConfigDTO = {
  likeWeight?: number | null;
  favoriteWeight?: number | null;
  commentWeight?: number | null;
  viewWeight?: number | null;
  allDecayDays?: number | null;
  autoRefreshEnabled?: boolean | null;
  autoRefreshIntervalMinutes?: number | null;
};

export type HotScoreRecomputeResult = {
  ok?: boolean;
  window?: string;
  at?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  changedCount?: number;
  increasedCount?: number;
  decreasedCount?: number;
  unchangedCount?: number;
  increasedScoreDelta?: number;
  decreasedScoreDelta?: number;
};

export type HotScoreRecomputeLogItem = {
  id: number;
  window: string;
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  changedCount: number;
  increasedCount: number;
  decreasedCount: number;
  unchangedCount: number;
  increasedScoreDelta: number;
  decreasedScoreDelta: number;
  createdAt: string;
};

export type PagedHotScoreRecomputeLogs = {
  content: HotScoreRecomputeLogItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function adminGetHotScoreConfig(): Promise<HotScoreConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/hot-scores/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取热榜配置失败');
  return (data ?? {}) as HotScoreConfigDTO;
}

export async function adminUpdateHotScoreConfig(payload: HotScoreConfigDTO): Promise<HotScoreConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/hot-scores/config'), {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存热榜配置失败');
  return (data ?? {}) as HotScoreConfigDTO;
}

export type HotRecomputeWindow = '24h' | '7d' | '30d' | '3m' | '6m' | '1y' | 'all';

export async function adminRecomputeHotScores(window: HotRecomputeWindow): Promise<HotScoreRecomputeResult> {
  const path = window === '24h'
    ? '/api/admin/hot-scores/recompute-24h'
    : window === '7d'
      ? '/api/admin/hot-scores/recompute-7d'
      : window === '30d'
        ? '/api/admin/hot-scores/recompute-30d'
        : window === '3m'
          ? '/api/admin/hot-scores/recompute-3m'
          : window === '6m'
            ? '/api/admin/hot-scores/recompute-6m'
            : window === '1y'
              ? '/api/admin/hot-scores/recompute-1y'
      : '/api/admin/hot-scores/recompute-all';

  const res = await fetch(apiUrl(path), {
    method: 'POST',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '重算热度分失败');
  return (data ?? {}) as HotScoreRecomputeResult;
}

export async function adminGetHotScoreRecomputeLogs(page = 0, size = 20): Promise<PagedHotScoreRecomputeLogs> {
  const res = await fetch(apiUrl(`/api/admin/hot-scores/recompute-logs?page=${page}&size=${size}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取重算日志失败');
  return (data ?? {}) as PagedHotScoreRecomputeLogs;
}
