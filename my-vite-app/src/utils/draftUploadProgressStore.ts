export type DraftUploadSession = {
  uploadId: string;
  kind: 'image' | 'attachment';
  fileName: string;
  fileSize: number;
  loaded: number;
  total: number;
  status: 'uploading' | 'paused';
  updatedAt: string;
};

type StoreV1 = {
  v: 1;
  drafts: Record<
    string,
    {
      updatedAt: string;
      sessions: DraftUploadSession[];
    }
  >;
};

const STORAGE_KEY = 'portal.posts.compose.draftUploadSessions.v1';
const MAX_SESSIONS_PER_DRAFT = 64;

function safeParseJson(text: string): unknown {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

function isSession(x: unknown): x is DraftUploadSession {
  if (!x || typeof x !== 'object') return false;
  const o = x as Record<string, unknown>;
  const uploadId = typeof o.uploadId === 'string' ? o.uploadId.trim() : '';
  if (!uploadId) return false;
  const kind = o.kind === 'image' || o.kind === 'attachment' ? o.kind : null;
  if (!kind) return false;
  const fileName = typeof o.fileName === 'string' ? o.fileName : '';
  const fileSize = typeof o.fileSize === 'number' && Number.isFinite(o.fileSize) ? o.fileSize : NaN;
  const loaded = typeof o.loaded === 'number' && Number.isFinite(o.loaded) ? o.loaded : NaN;
  const total = typeof o.total === 'number' && Number.isFinite(o.total) ? o.total : NaN;
  const status = o.status === 'uploading' || o.status === 'paused' ? o.status : null;
  const updatedAt = typeof o.updatedAt === 'string' ? o.updatedAt : '';
  if (!fileName) return false;
  if (!Number.isFinite(fileSize) || fileSize < 0) return false;
  if (!Number.isFinite(loaded) || loaded < 0) return false;
  if (!Number.isFinite(total) || total < 0) return false;
  if (!status) return false;
  if (!updatedAt) return false;
  return true;
}

function normalizeDraftId(draftId: string): string {
  return String(draftId || '').trim();
}

function loadStore(): StoreV1 {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const data = safeParseJson(raw || '');
    if (!data || typeof data !== 'object') return { v: 1, drafts: {} };
    const obj = data as Record<string, unknown>;
    if (obj.v !== 1) return { v: 1, drafts: {} };
    const drafts = obj.drafts && typeof obj.drafts === 'object' ? (obj.drafts as Record<string, unknown>) : {};
    const out: StoreV1 = { v: 1, drafts: {} };
    for (const [k, v] of Object.entries(drafts)) {
      if (!k) continue;
      if (!v || typeof v !== 'object') continue;
      const vv = v as Record<string, unknown>;
      const updatedAt = typeof vv.updatedAt === 'string' ? vv.updatedAt : '';
      const sessionsRaw = Array.isArray(vv.sessions) ? vv.sessions : [];
      const sessions = sessionsRaw.filter(isSession).slice(0, MAX_SESSIONS_PER_DRAFT);
      if (!sessions.length) continue;
      out.drafts[k] = { updatedAt: updatedAt || new Date().toISOString(), sessions };
    }
    return out;
  } catch {
    return { v: 1, drafts: {} };
  }
}

function saveStore(next: StoreV1): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
  }
}

export function loadDraftUploadSessions(draftId: string): DraftUploadSession[] {
  const id = normalizeDraftId(draftId);
  if (!id) return [];
  const store = loadStore();
  const entry = store.drafts[id];
  return Array.isArray(entry?.sessions) ? entry.sessions.slice(0, MAX_SESSIONS_PER_DRAFT) : [];
}

export function saveDraftUploadSessions(draftId: string, sessions: DraftUploadSession[]): void {
  const id = normalizeDraftId(draftId);
  if (!id) return;
  const list = Array.isArray(sessions) ? sessions.filter(isSession) : [];
  const unique: DraftUploadSession[] = [];
  const seen = new Set<string>();
  for (const s of list) {
    if (seen.has(s.uploadId)) continue;
    seen.add(s.uploadId);
    unique.push(s);
    if (unique.length >= MAX_SESSIONS_PER_DRAFT) break;
  }
  const store = loadStore();
  if (unique.length === 0) {
    delete store.drafts[id];
    saveStore(store);
    return;
  }
  store.drafts[id] = { updatedAt: new Date().toISOString(), sessions: unique };
  saveStore(store);
}

export function clearDraftUploadSessions(draftId: string): void {
  const id = normalizeDraftId(draftId);
  if (!id) return;
  const store = loadStore();
  if (!store.drafts[id]) return;
  delete store.drafts[id];
  saveStore(store);
}

