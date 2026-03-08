import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../services/accessContextService', () => ({
  fetchAccessContext: vi.fn(),
}));

import { useAuth } from './AuthContext';
import { fetchAccessContext } from '../services/accessContextService';
import { AccessProvider, useAccess } from './AccessContext';

type AccessCtx = ReturnType<typeof useAccess>;

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function Consumer(props: { onCtx: (ctx: AccessCtx) => void }) {
  const ctx = useAccess();
  props.onCtx(ctx);
  return (
    <div>
      <div data-testid="email">{ctx.email ?? ''}</div>
      <div data-testid="loading">{ctx.loading ? '1' : '0'}</div>
    </div>
  );
}

describe('AccessContext', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('useAccess throws when used outside AccessProvider', () => {
    expect(() => render(<Consumer onCtx={() => {}} />)).toThrow('useAccess must be used within an AccessProvider');
  });

  it('refresh returns empty and does not call fetchAccessContext when unauthenticated', async () => {
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: false, loading: false });

    let ctx: AccessCtx | undefined;
    render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    const res = await ctx!.refresh();
    expect(res).toEqual({ email: null, roles: [], permissions: [] });
    expect(screen.getByTestId('loading').textContent).toBe('0');
    expect((fetchAccessContext as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(0);
  });

  it('refresh returns empty when auth is loading and does not call fetchAccessContext', async () => {
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: true, loading: true });

    let ctx: AccessCtx | undefined;
    render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    const res = await ctx!.refresh();
    expect(res).toEqual({ email: null, roles: [], permissions: [] });
    expect((fetchAccessContext as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(0);
    expect(screen.getByTestId('loading').textContent).toBe('1');
  });

  it('refresh de-dupes concurrent calls and updates data when authenticated', async () => {
    const d = createDeferred<{ email: string | null; roles: string[]; permissions: string[] }>();
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: true, loading: false });
    (fetchAccessContext as unknown as ReturnType<typeof vi.fn>).mockReturnValueOnce(d.promise);

    let ctx: AccessCtx | undefined;
    render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    const p1 = ctx!.refresh();
    const p2 = ctx!.refresh();
    expect((fetchAccessContext as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(1);

    d.resolve({ email: 'a@b.com', roles: ['ADMIN'], permissions: [] });
    const [r1, r2] = await Promise.all([p1, p2]);
    expect(r1.email).toBe('a@b.com');
    expect(r2.email).toBe('a@b.com');

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('a@b.com');
    });
    expect(screen.getByTestId('loading').textContent).toBe('0');
  });

  it('hasRole/hasAuthority/hasPerm normalize keys and handle empty values', async () => {
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: true, loading: false });
    (fetchAccessContext as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      email: 'a@b.com',
      roles: ['ROLE_ADMIN', ' USER ', '', '  ', undefined as unknown as string],
      permissions: ['PERM_post:read', 'post:write', 'PERM_', '  ', undefined as unknown as string],
    });

    let ctx: AccessCtx | undefined;
    render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('a@b.com');
    });

    expect(ctx!.hasRole('ADMIN')).toBe(true);
    expect(ctx!.hasRole('ROLE_ADMIN')).toBe(true);
    expect(ctx!.hasRole('USER')).toBe(true);

    expect(ctx!.hasAuthority('ROLE_ADMIN')).toBe(true);
    expect(ctx!.hasAuthority('ADMIN')).toBe(true);
    expect(ctx!.hasAuthority('PERM_post:read')).toBe(true);
    expect(ctx!.hasAuthority('post:read')).toBe(true);
    expect(ctx!.hasAuthority('')).toBe(false);
    expect(ctx!.hasAuthority('   ')).toBe(false);
    expect(ctx!.hasAuthority(undefined as unknown as string)).toBe(false);

    expect(ctx!.hasPerm(' post ', ' read ')).toBe(true);
    expect(ctx!.hasRole(undefined as unknown as string)).toBe(false);
  });

  it('tolerates undefined roles/permissions from service response', async () => {
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: true, loading: false });
    (fetchAccessContext as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      email: 'a@b.com',
      roles: undefined,
      permissions: undefined,
    });

    let ctx: AccessCtx | undefined;
    render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('a@b.com');
    });
    expect(ctx!.hasRole('ADMIN')).toBe(false);
    expect(ctx!.hasAuthority('post:read')).toBe(false);
  });

  it('useEffect resets state when auth becomes unauthenticated', async () => {
    let auth = { isAuthenticated: true, loading: false };
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockImplementation(() => auth);
    (fetchAccessContext as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      email: 'a@b.com',
      roles: ['ADMIN'],
      permissions: [],
    });

    let ctx: AccessCtx | undefined;
    const r = render(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('a@b.com');
    });

    auth = { isAuthenticated: false, loading: false };
    r.rerender(
      <AccessProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AccessProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('email').textContent).toBe('');
    });
    expect(screen.getByTestId('loading').textContent).toBe('0');

    const res = await ctx!.refresh();
    expect(res).toEqual({ email: null, roles: [], permissions: [] });
  });
});
