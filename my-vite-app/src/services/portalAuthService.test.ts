import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./authService', () => {
  return {
    getCurrentAdmin: vi.fn(),
  };
});

describe('portalAuthService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.resetModules();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('getStoredUserId returns undefined on missing or invalid data', async () => {
    const { getStoredUserId } = await import('./portalAuthService');
    expect(getStoredUserId()).toBeUndefined();

    localStorage.setItem('userData', '{bad json');
    expect(getStoredUserId()).toBeUndefined();
  });

  it('getStoredUserId extracts id from known shapes', async () => {
    const { getStoredUserId } = await import('./portalAuthService');

    localStorage.setItem('userData', JSON.stringify({ id: '2' }));
    expect(getStoredUserId()).toBe(2);

    localStorage.setItem('userData', JSON.stringify({ id: ' 2 ' }));
    expect(getStoredUserId()).toBe(2);

    localStorage.setItem('userData', JSON.stringify({ id: 'NaN' }));
    expect(getStoredUserId()).toBeUndefined();

    localStorage.setItem('userData', JSON.stringify({ user: { id: 3 } }));
    expect(getStoredUserId()).toBe(3);

    localStorage.setItem('userData', JSON.stringify({ userData: { id: '4' } }));
    expect(getStoredUserId()).toBe(4);
  });

  it('resolvePortalAuthState prefers backend session and persists minimal user id', async () => {
    const auth = await import('./authService');
    (auth as unknown as { getCurrentAdmin: ReturnType<typeof vi.fn> }).getCurrentAdmin.mockResolvedValueOnce({ id: 1, username: 'u' });

    const { resolvePortalAuthState } = await import('./portalAuthService');
    const res = await resolvePortalAuthState();

    expect(res).toEqual({ isLoggedIn: true, userId: 1, user: { id: 1, username: 'u' } });
    expect(JSON.parse(localStorage.getItem('userData') || '{}')).toEqual({ id: 1 });
  });

  it('resolvePortalAuthState falls back to localStorage when backend fails', async () => {
    localStorage.setItem('userData', JSON.stringify({ id: 9 }));
    const auth = await import('./authService');
    (auth as unknown as { getCurrentAdmin: ReturnType<typeof vi.fn> }).getCurrentAdmin.mockRejectedValueOnce(new Error('no session'));

    const { resolvePortalAuthState } = await import('./portalAuthService');
    const res = await resolvePortalAuthState();

    expect(res).toEqual({ isLoggedIn: true, userId: 9 });
  });

  it('resolvePortalAuthState falls back to localStorage when backend returns invalid id', async () => {
    localStorage.setItem('userData', JSON.stringify({ id: 9 }));
    const auth = await import('./authService');
    (auth as unknown as { getCurrentAdmin: ReturnType<typeof vi.fn> }).getCurrentAdmin.mockResolvedValueOnce({ id: 'abc', username: 'u' });

    const { resolvePortalAuthState } = await import('./portalAuthService');
    const res = await resolvePortalAuthState();

    expect(res).toEqual({ isLoggedIn: true, userId: 9, user: { id: 'abc', username: 'u' } });
  });

  it('resolvePortalAuthState ignores localStorage write error', async () => {
    const auth = await import('./authService');
    (auth as unknown as { getCurrentAdmin: ReturnType<typeof vi.fn> }).getCurrentAdmin.mockResolvedValueOnce({ id: 1, username: 'u' });

    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('no storage');
    });

    const { resolvePortalAuthState } = await import('./portalAuthService');
    const res = await resolvePortalAuthState();

    expect(res).toEqual({ isLoggedIn: true, userId: 1, user: { id: 1, username: 'u' } });
    setItemSpy.mockRestore();
  });
});
