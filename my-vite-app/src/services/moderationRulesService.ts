import { getCsrfToken } from '../utils/csrfUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
import type {
  ModerationRuleCreatePayload,
  ModerationRuleDTO,
  ModerationRuleListQuery,
  ModerationRuleUpdatePayload,
} from '../types/moderationRules';

const apiUrl = serviceApiUrl;


const STORAGE_KEY = 'admin.moderation.rules.v1';

function safeJsonParse<T>(raw: string | null): T | undefined {
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return undefined;
  }
}

function loadLocal(): ModerationRuleDTO[] {
  const data = safeJsonParse<ModerationRuleDTO[]>(window.localStorage.getItem(STORAGE_KEY));
  if (!Array.isArray(data)) return [];
  return data;
}

function saveLocal(items: ModerationRuleDTO[]): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

function nextId(items: ModerationRuleDTO[]): number {
  return items.reduce((m, it) => Math.max(m, Number(it.id) || 0), 0) + 1;
}

function normalizeText(s: unknown): string {
  return String(s ?? '').trim().toLowerCase();
}

function inferCategory(rule: ModerationRuleDTO): 'SENSITIVE' | 'BLACKLIST' | 'URL' | 'AD' {
  const md = (rule.metadata ?? {}) as Record<string, unknown>;
  const cat = md.category;
  if (cat === 'SENSITIVE' || cat === 'BLACKLIST' || cat === 'URL' || cat === 'AD') return cat;
  if (rule.type === 'URL') return 'URL';
  const tags = md.tags;
  if (Array.isArray(tags) && tags.includes('ad')) return 'AD';
  return 'SENSITIVE';
}

function localFilter(items: ModerationRuleDTO[], query: ModerationRuleListQuery): ModerationRuleDTO[] {
  const q = normalizeText(query.q);
  return items.filter((it) => {
    if (query.enabled !== '' && query.enabled !== undefined) {
      if (it.enabled !== query.enabled) return false;
    }
    if (query.type && it.type !== query.type) return false;
    if (query.severity && it.severity !== query.severity) return false;
    if (query.category) {
      if (inferCategory(it) !== query.category) return false;
    }
    if (q) {
      const hay = `${it.name} ${it.pattern} ${it.type} ${it.severity}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });
}

function sortLocal(items: ModerationRuleDTO[]): ModerationRuleDTO[] {
  return [...items].sort((a, b) => {
    const ta = new Date(a.updatedAt ?? a.createdAt ?? 0).getTime();
    const tb = new Date(b.updatedAt ?? b.createdAt ?? 0).getTime();
    return tb - ta;
  });
}

async function tryFetchJson(res: Response): Promise<unknown> {
  return res.json().catch(() => ({}));
}

function shouldFallbackToLocal(e: unknown): boolean {
  // Network error / CORS / 404 etc.
  if (e instanceof TypeError) return true;
  if (e instanceof Error) {
    const msg = e.message.toLowerCase();
    if (msg.includes('failed to fetch')) return true;
  }
  return false;
}

export async function adminListModerationRules(query: ModerationRuleListQuery = {}): Promise<ModerationRuleDTO[]> {
  // 1) try backend
  try {
    const sp = new URLSearchParams();
    if (query.q) sp.set('q', query.q);
    if (query.type) sp.set('type', query.type);
    if (query.severity) sp.set('severity', query.severity);
    if (query.enabled !== '' && query.enabled !== undefined) sp.set('enabled', String(query.enabled));
    if (query.category) sp.set('category', query.category);

    const qs = sp.toString();
    const res = await fetch(apiUrl(`/api/admin/moderation/rules${qs ? `?${qs}` : ''}`), {
      method: 'GET',
      credentials: 'include',
    });

    const data: unknown = await tryFetchJson(res);
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取规则列表失败');

    // backend compatibility: [{...}] OR { content: [...] }
    if (Array.isArray(data)) return data as ModerationRuleDTO[];
    if (data && typeof data === 'object' && 'content' in data && Array.isArray((data as { content?: unknown }).content)) {
      return (data as { content: ModerationRuleDTO[] }).content;
    }
    return [];
  } catch (e) {
    if (!shouldFallbackToLocal(e)) throw e;
    return sortLocal(localFilter(loadLocal(), query));
  }
}

export async function adminCreateModerationRule(payload: ModerationRuleCreatePayload): Promise<ModerationRuleDTO> {
  try {
    const csrfToken = await getCsrfToken();
    const res = await fetch(apiUrl('/api/admin/moderation/rules'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      credentials: 'include',
      body: JSON.stringify(payload),
    });

    const data: unknown = await tryFetchJson(res);
    if (!res.ok) throw new Error(getBackendMessage(data) || '创建规则失败');
    return data as ModerationRuleDTO;
  } catch (e) {
    if (!shouldFallbackToLocal(e)) throw e;
    const items = loadLocal();
    const now = new Date().toISOString();
    const created: ModerationRuleDTO = {
      id: nextId(items),
      createdAt: now,
      updatedAt: now,
      ...payload,
    };
    saveLocal(sortLocal([created, ...items]));
    return created;
  }
}

export async function adminUpdateModerationRule(id: number, payload: ModerationRuleUpdatePayload): Promise<ModerationRuleDTO> {
  try {
    const csrfToken = await getCsrfToken();
    const res = await fetch(apiUrl(`/api/admin/moderation/rules/${id}`), {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      credentials: 'include',
      body: JSON.stringify(payload),
    });

    const data: unknown = await tryFetchJson(res);
    if (!res.ok) throw new Error(getBackendMessage(data) || '更新规则失败');
    return data as ModerationRuleDTO;
  } catch (e) {
    if (!shouldFallbackToLocal(e)) throw e;
    const items = loadLocal();
    const idx = items.findIndex((x) => x.id === id);
    if (idx < 0) throw new Error('规则不存在或已被删除');
    const now = new Date().toISOString();
    const updated: ModerationRuleDTO = {
      ...items[idx],
      ...payload,
      updatedAt: now,
    };
    const next = [...items.slice(0, idx), updated, ...items.slice(idx + 1)];
    saveLocal(sortLocal(next));
    return updated;
  }
}

export async function adminDeleteModerationRule(id: number): Promise<void> {
  try {
    const csrfToken = await getCsrfToken();
    const res = await fetch(apiUrl(`/api/admin/moderation/rules/${id}`), {
      method: 'DELETE',
      headers: {
        'X-XSRF-TOKEN': csrfToken,
      },
      credentials: 'include',
    });

    const data: unknown = await tryFetchJson(res);
    if (!res.ok) throw new Error(getBackendMessage(data) || '删除规则失败');
  } catch (e) {
    if (!shouldFallbackToLocal(e)) throw e;
    const items = loadLocal();
    saveLocal(items.filter((x) => x.id !== id));
  }
}
