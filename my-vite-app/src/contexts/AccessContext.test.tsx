import React from 'react';
import { describe, expect, it, vi } from 'vitest';

// IMPORTANT: mock service before importing provider
vi.mock('../services/accessContextService', () => {
  return {
    fetchAccessContext: vi.fn(async () => ({
      email: 'u@example.com',
      roles: ['ADMIN'],
      permissions: ['admin_ui:access']
    }))
  };
});

import { renderHook, waitFor } from '@testing-library/react';
import { AccessProvider, useAccess } from './AccessContext';

describe('AccessContext', () => {
  it('hasPerm should accept permission keys without PERM_ prefix (API contract)', async () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => <AccessProvider>{children}</AccessProvider>;
    const { result } = renderHook(() => useAccess(), { wrapper });

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.hasPerm('admin_ui', 'access')).toBe(true);
  });

  it('hasPerm/hasAuthority should also accept legacy PERM_ prefixed values', async () => {
    const { fetchAccessContext } = await import('../services/accessContextService');
    (fetchAccessContext as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      email: 'u@example.com',
      roles: ['ROLE_ADMIN'],
      permissions: ['PERM_admin_ui:access']
    });

    const wrapper = ({ children }: { children: React.ReactNode }) => <AccessProvider>{children}</AccessProvider>;
    const { result } = renderHook(() => useAccess(), { wrapper });

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.hasPerm('admin_ui', 'access')).toBe(true);
    expect(result.current.hasAuthority('PERM_admin_ui:access')).toBe(true);
    expect(result.current.hasAuthority('admin_ui:access')).toBe(true);
    expect(result.current.hasRole('ADMIN')).toBe(true);
  });
});

