// my-vite-app/src/contexts/AccessContext.tsx

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
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

export const AccessProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [data, setData] = useState<AccessContextDTO>(empty);
  const [loading, setLoading] = useState(true);

  const inFlightRef = useRef<Promise<AccessContextDTO> | null>(null);

  const refresh = useCallback(async () => {
    if (inFlightRef.current) return inFlightRef.current;

    setLoading(true);
    const p = (async () => {
      try {
        const res = await fetchAccessContext();
        setData(res);
        return res;
      } finally {
        setLoading(false);
        inFlightRef.current = null;
      }
    })();

    inFlightRef.current = p;
    return p;
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const rolesSet = useMemo(() => {
    // Backend returns roles normalized as `ADMIN` (no ROLE_ prefix), but keep compatibility.
    const s = new Set<string>();
    for (const r of data.roles ?? []) {
      const rr = String(r ?? '').trim();
      if (!rr) continue;
      s.add(rr.startsWith('ROLE_') ? rr.substring('ROLE_'.length) : rr);
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
    loading,
    refresh,

    hasRole: (role: string) => rolesSet.has(String(role).trim().replace(/^ROLE_/i, '')),
    hasAuthority: (authority: string) => {
      const a = String(authority ?? '').trim();
      if (!a) return false;

      // Role authorities: ROLE_ADMIN, ADMIN
      if (a.toUpperCase().startsWith('ROLE_')) return rolesSet.has(a.substring('ROLE_'.length));
      if (!a.includes(':')) return rolesSet.has(a);

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

