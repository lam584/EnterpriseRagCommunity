// my-vite-app/src/contexts/AccessContext.tsx

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from './AuthContext';
import { fetchAccessContext, AccessContextDTO } from '../services/accessContextService';

export type AccessContextType = {
  email: string | null;
  roles: string[];
  permissions: string[];

  loading: boolean;
  refresh: () => Promise<AccessContextDTO>;

  hasRole: (role: string) => boolean;
  hasPerm: (resource: string, action: string) => boolean;
  hasAuthority: (authority: string) => boolean;
};

const Ctx = createContext<AccessContextType | undefined>(undefined);

export function useAccess() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAccess must be used within an AccessProvider');
  return ctx;
}

const empty: AccessContextDTO = { email: null, roles: [], permissions: [] };

/**
 * Normalize permission keys for comparisons.
 *
 * Backend `/api/auth/access-context` returns permissions as `resource:action` (no `PERM_` prefix).
 * But Spring Security authorities often use `PERM_resource:action`.
 *
 * To avoid mismatch, we accept BOTH formats on the client.
 */
function normalizePermKey(resource: string, action: string) {
  return `${String(resource).trim()}:${String(action).trim()}`;
}

function normalizeAuthorityKey(authority: string) {
  const a = String(authority ?? '').trim();
  if (!a) return '';
  return a.startsWith('PERM_') ? a.substring('PERM_'.length) : a;
}

function normalizeRoleKey(role: string) {
  const r = String(role ?? '').trim();
  if (!r) return '';
  const rr = r.replace(/^ROLE_/i, '');
  return rr.trim().toUpperCase();
}

export const AccessProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const [data, setData] = useState<AccessContextDTO>(empty);
  const [loading, setLoading] = useState(false);
  const [initialized, setInitialized] = useState(false);

  const inFlightRef = useRef<Promise<AccessContextDTO> | null>(null);

  const refresh = useCallback((): Promise<AccessContextDTO> => {
    if (authLoading || !isAuthenticated) {
      const next = empty;
      setData(next);
      setLoading(false);
      setInitialized(false);
      inFlightRef.current = null;
      return Promise.resolve(next);
    }
    if (inFlightRef.current) return inFlightRef.current;

    setLoading(true);
    const p = (async () => {
      try {
        const res = await fetchAccessContext();
        setData(res);
        return res;
      } finally {
        setLoading(false);
        setInitialized(true);
        inFlightRef.current = null;
      }
    })();

    inFlightRef.current = p;
    return p;
  }, [authLoading, isAuthenticated]);

  useEffect(() => {
    if (authLoading) return;
    if (!isAuthenticated) {
      setData(empty);
      setLoading(false);
      setInitialized(false);
      inFlightRef.current = null;
      return;
    }

    void refresh().catch(() => {});
  }, [authLoading, isAuthenticated, refresh]);

  const rolesSet = useMemo(() => {
    // Backend returns roles normalized as `ADMIN` (no ROLE_ prefix), but keep compatibility.
    const s = new Set<string>();
    for (const r of data.roles ?? []) {
      const rr = normalizeRoleKey(String(r ?? ''));
      if (!rr) continue;
      s.add(rr);
    }
    return s;
  }, [data.roles]);

  const permsSet = useMemo(() => {
    const s = new Set<string>();
    for (const p of data.permissions ?? []) {
      const pp = normalizeAuthorityKey(p);
      if (!pp) continue;
      s.add(pp);
    }
    return s;
  }, [data.permissions]);

  const value: AccessContextType = {
    email: data.email,
    roles: data.roles,
    permissions: data.permissions,
    loading: loading || (isAuthenticated && !initialized),
    refresh,

    hasRole: (role: string) => rolesSet.has(normalizeRoleKey(role)),
    hasAuthority: (authority: string) => {
      const a = String(authority ?? '').trim();
      if (!a) return false;

      // Role authorities: ROLE_ADMIN, ADMIN
      if (a.toUpperCase().startsWith('ROLE_')) return rolesSet.has(normalizeRoleKey(a));
      if (!a.includes(':')) return rolesSet.has(normalizeRoleKey(a));

      // Permission authorities: PERM_x:y, x:y
      return permsSet.has(normalizeAuthorityKey(a));
    },
    hasPerm: (resource: string, action: string) => {
      const key = normalizePermKey(resource, action);
      return permsSet.has(key);
    }
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
};

