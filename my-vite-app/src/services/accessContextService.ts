// my-vite-app/src/services/accessContextService.ts

export type AccessContextDTO = {
  email: string | null;
  /** authorities like ROLE_ADMIN */
  roles: string[];
  /** authorities like PERM_resource:action */
  permissions: string[];
};

export async function fetchAccessContext(): Promise<AccessContextDTO> {
  const res = await fetch('/api/auth/access-context', {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('Failed to fetch access context');
  }

  const data = (await res.json()) as AccessContextDTO;
  return {
    email: data?.email ?? null,
    roles: Array.isArray(data?.roles) ? data.roles : [],
    permissions: Array.isArray(data?.permissions) ? data.permissions : []
  };
}

