import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockConsole } from '../testUtils/mockConsole';
import { AuthProvider, useAuth } from './AuthContext';

vi.mock('../services/authService', () => ({
  getCurrentAdmin: vi.fn(),
}));

vi.mock('../services/security2faPolicyAccountService', () => ({
  getMySecurity2faPolicy: vi.fn(),
}));

vi.mock('../services/totpAccountService', () => ({
  getTotpStatus: vi.fn(),
}));

import { getCurrentAdmin } from '../services/authService';
import { getMySecurity2faPolicy } from '../services/security2faPolicyAccountService';
import { getTotpStatus } from '../services/totpAccountService';

type AuthCtx = ReturnType<typeof useAuth>;

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function Consumer(props: { onCtx: (ctx: AuthCtx) => void }) {
  const ctx = useAuth();
  props.onCtx(ctx);
  return (
    <div>
      <div data-testid="auth">{ctx.isAuthenticated ? '1' : '0'}</div>
      <div data-testid="loading">{ctx.loading ? '1' : '0'}</div>
      <div data-testid="totp">{ctx.totpSetupRequired ? '1' : '0'}</div>
      <div data-testid="user">{ctx.currentUser?.username ?? ''}</div>
    </div>
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('useAuth throws when used outside AuthProvider', () => {
    const c = mockConsole(['error']);
    expect(() => render(<Consumer onCtx={() => {}} />)).toThrow('useAuth must be used within an AuthProvider');
    c.restore();
  });

  it('mount triggers refreshAuth and sets totpSetupRequired=true when required', async () => {
    const c = mockConsole(['error', 'log']);
    (getCurrentAdmin as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: 'a@b.com',
      username: 'u',
      isDeleted: false,
    });
    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ totpRequired: true });
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ enabled: false });

    let lastCtx: AuthCtx | undefined;
    render(
      <AuthProvider>
        <Consumer onCtx={(ctx) => (lastCtx = ctx)} />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('0');
    });
    expect(screen.getByTestId('auth').textContent).toBe('1');
    expect(screen.getByTestId('totp').textContent).toBe('1');
    expect(screen.getByTestId('user').textContent).toBe('u');
    expect((getCurrentAdmin as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(1);
    expect(lastCtx?.isAuthenticated).toBe(true);
    c.restore();
  });

  it('refreshAuth keeps authenticated when 2FA fetch fails and sets totpSetupRequired=false', async () => {
    const c = mockConsole(['error', 'log']);
    (getCurrentAdmin as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: 'a@b.com',
      username: 'u',
      isDeleted: false,
    });
    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('nope'));
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ enabled: false });

    render(
      <AuthProvider>
        <Consumer onCtx={() => {}} />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('0');
    });
    expect(screen.getByTestId('auth').textContent).toBe('1');
    expect(screen.getByTestId('totp').textContent).toBe('0');
    c.restore();
  });

  it('refreshAuth sets unauthenticated state when getCurrentAdmin fails', async () => {
    const c = mockConsole(['error']);
    (getCurrentAdmin as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('fail'));

    let ctx: AuthCtx | undefined;
    render(
      <AuthProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('0');
    });
    expect(screen.getByTestId('auth').textContent).toBe('0');
    expect(screen.getByTestId('totp').textContent).toBe('0');
    expect(screen.getByTestId('user').textContent).toBe('');

    await ctx!.refreshSecurityGate();
    expect((getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(0);
    c.restore();
  });

  it('refreshAuth de-dupes concurrent refreshes', async () => {
    const c = mockConsole(['error', 'log']);
    const d = createDeferred<{ id: number; email: string; username: string; isDeleted: boolean }>();
    (getCurrentAdmin as unknown as ReturnType<typeof vi.fn>).mockReturnValueOnce(d.promise);
    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ totpRequired: false });
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ enabled: true });

    let ctx: AuthCtx | undefined;
    render(
      <AuthProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(ctx).not.toBeUndefined();
    });

    const p1 = ctx!.refreshAuth();
    const p2 = ctx!.refreshAuth();

    d.resolve({ id: 1, email: 'a@b.com', username: 'u', isDeleted: false });
    const [r1, r2] = await Promise.all([p1, p2]);
    expect(r1).toBe(true);
    expect(r2).toBe(true);

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('0');
    });
    expect((getCurrentAdmin as unknown as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(1);
    c.restore();
  });

  it('refreshSecurityGate respects isAuthenticated and handles errors', async () => {
    const c = mockConsole(['error', 'log']);
    (getCurrentAdmin as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: 'a@b.com',
      username: 'u',
      isDeleted: false,
    });
    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ totpRequired: false });
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ enabled: false });

    let ctx: AuthCtx | undefined;
    render(
      <AuthProvider>
        <Consumer onCtx={(c0) => (ctx = c0)} />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('0');
    });
    expect(screen.getByTestId('totp').textContent).toBe('0');

    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ totpRequired: true });
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ enabled: false });
    await ctx!.refreshSecurityGate();
    await waitFor(() => {
      expect(screen.getByTestId('totp').textContent).toBe('1');
    });

    (getMySecurity2faPolicy as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('fail'));
    (getTotpStatus as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ enabled: false });
    await ctx!.refreshSecurityGate();
    await waitFor(() => {
      expect(screen.getByTestId('totp').textContent).toBe('0');
    });

    c.restore();
  });
});
