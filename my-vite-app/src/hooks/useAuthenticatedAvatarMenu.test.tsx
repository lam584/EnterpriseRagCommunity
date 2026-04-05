import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useAuthenticatedAvatarMenu } from './useAuthenticatedAvatarMenu';

const serviceMocks = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../services/accountService', () => ({
  getMyProfile: serviceMocks.getMyProfile,
}));

vi.mock('../services/authService', () => ({
  logout: serviceMocks.logout,
}));

function Host(props: {
  isAuthenticated: boolean;
  setCurrentUser: ReturnType<typeof vi.fn>;
  setIsAuthenticated: ReturnType<typeof vi.fn>;
}) {
  const menu = useAuthenticatedAvatarMenu({
    isAuthenticated: props.isAuthenticated,
    setCurrentUser: props.setCurrentUser,
    setIsAuthenticated: props.setIsAuthenticated,
  });

  return (
    <div>
      <div data-testid="outside">outside</div>
      <div ref={menu.userMenuRef}>
        <div data-testid="avatar">{menu.profileAvatarUrl ?? ''}</div>
        <button type="button" onClick={() => menu.setUserMenuOpen(true)}>
          open
        </button>
        <button type="button" onClick={() => void menu.handleLogout()}>
          logout
        </button>
        <div data-testid="menu-state">{menu.userMenuOpen ? 'open' : 'closed'}</div>
      </div>
    </div>
  );
}

describe('useAuthenticatedAvatarMenu', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    serviceMocks.getMyProfile.mockResolvedValue({ avatarUrl: 'https://img.example/avatar.png' });
    serviceMocks.logout.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('loads avatar when authenticated', async () => {
    render(<Host isAuthenticated setCurrentUser={vi.fn()} setIsAuthenticated={vi.fn()} />);

    await waitFor(() => {
      expect(serviceMocks.getMyProfile).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByTestId('avatar').textContent).toBe('https://img.example/avatar.png');
  });

  it('clears menu when clicking outside', async () => {
    render(<Host isAuthenticated setCurrentUser={vi.fn()} setIsAuthenticated={vi.fn()} />);

    fireEvent.click(screen.getByText('open'));
    expect(screen.getByTestId('menu-state').textContent).toBe('open');

    fireEvent.mouseDown(screen.getByTestId('outside'));
    await waitFor(() => {
      expect(screen.getByTestId('menu-state').textContent).toBe('closed');
    });
  });

  it('logs out and clears auth state', async () => {
    const setCurrentUser = vi.fn();
    const setIsAuthenticated = vi.fn();
    const removeItem = vi.fn();
    vi.stubGlobal('localStorage', { removeItem });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { href: 'http://localhost/' },
    });

    render(<Host isAuthenticated setCurrentUser={setCurrentUser} setIsAuthenticated={setIsAuthenticated} />);

    fireEvent.click(screen.getByText('open'));
    fireEvent.click(screen.getByText('logout'));

    await waitFor(() => {
      expect(serviceMocks.logout).toHaveBeenCalledTimes(1);
    });
    expect(removeItem).toHaveBeenCalledWith('userData');
    expect(setCurrentUser).toHaveBeenCalledWith(null);
    expect(setIsAuthenticated).toHaveBeenCalledWith(false);
    expect(screen.getByTestId('menu-state').textContent).toBe('closed');
    expect(window.location.href).toBe('/login');
  });
});
