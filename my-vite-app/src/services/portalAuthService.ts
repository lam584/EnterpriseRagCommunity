// src/services/portalAuthService.ts
// Portal pages: lightweight auth helper.
// Goal: detect if user is logged in by reusing the same server session as admin login.
// This intentionally does NOT implement RBAC; it only answers “logged in?” and provides user id.

import { getCurrentAdmin, type AdminDTO } from './authService';

type StoredAuth = { userData?: { id?: number | string }; id?: number | string; user?: { id?: number | string } };

function toNumId(v: unknown): number | undefined {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

export function getStoredUserId(): number | undefined {
  try {
    const raw = localStorage.getItem('userData');
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as StoredAuth;
    const id = parsed.userData?.id ?? parsed.id ?? parsed.user?.id;
    return toNumId(id);
  } catch {
    return undefined;
  }
}

export type PortalAuthState = {
  isLoggedIn: boolean;
  userId?: number;
  user?: AdminDTO;
};

/**
 * Server-first: if session is valid, returns admin DTO (shared with后台) and id.
 * Falls back to localStorage when backend session isn't available.
 */
export async function resolvePortalAuthState(): Promise<PortalAuthState> {
  try {
    const user = await getCurrentAdmin();
    const userId = toNumId((user as unknown as { id?: unknown }).id);

    // Some broken backend responses may not include id; treat as not logged in.
    if (!userId) {
      const fallback = getStoredUserId();
      return { isLoggedIn: Boolean(fallback), userId: fallback, user };
    }

    // Persist minimal data for portal pages (avoid storing full user / sensitive fields).
    try {
      localStorage.setItem('userData', JSON.stringify({ id: userId }));
    } catch {
      // ignore write error (private mode, quota etc.)
    }

    return { isLoggedIn: true, userId, user };
  } catch {
    const userId = getStoredUserId();
    return { isLoggedIn: Boolean(userId), userId };
  }
}
