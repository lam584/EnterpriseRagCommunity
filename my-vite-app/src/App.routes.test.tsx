import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const { useAuthMock, useAccessMock, checkInitialSetupStatusMock } = vi.hoisted(() => ({
  useAuthMock: vi.fn(),
  useAccessMock: vi.fn(),
  checkInitialSetupStatusMock: vi.fn(),
}));

vi.mock('./contexts/AuthContext', () => ({
  AuthProvider: ({ children }: any) => children,
  useAuth: useAuthMock,
}));

vi.mock('./contexts/AccessContext', () => ({
  AccessProvider: ({ children }: any) => children,
  useAccess: useAccessMock,
}));

vi.mock('./services/authService', () => ({
  checkInitialSetupStatus: checkInitialSetupStatusMock,
}));

vi.mock('./components/login/Login', () => ({
  default: () => <div>LOGIN_PAGE</div>,
}));

vi.mock('./components/login/Register', () => ({
  default: () => <div>REGISTER_PAGE</div>,
}));

vi.mock('./components/login/ForgotPassword', () => ({
  default: () => <div>FORGOT_PASSWORD_PAGE</div>,
}));

vi.mock('./pages/ForbiddenPage', () => ({
  default: () => <div>FORBIDDEN_PAGE</div>,
}));

vi.mock('./components/login/AdminSetup', () => ({
  default: () => <div>ADMIN_SETUP_PAGE</div>,
}));

vi.mock('./pages/admin/AdminDashboardLayout', () => ({
  default: () => <div>ADMIN_DASHBOARD_LAYOUT</div>,
}));

vi.mock('./pages/portal/CommunityPortalLayout', () => ({
  default: () => <div>PORTAL_LAYOUT</div>,
}));

vi.mock('./pages/portal/discover/DiscoverLayout', () => ({
  default: () => <div>DISCOVER_LAYOUT</div>,
  DiscoverIndexRedirect: () => <div>DISCOVER_INDEX_REDIRECT</div>,
}));

vi.mock('./pages/portal/posts/PostsLayout', () => ({
  default: () => <div>POSTS_LAYOUT</div>,
  PostsIndexRedirect: () => <div>POSTS_INDEX_REDIRECT</div>,
}));

vi.mock('./pages/portal/interact/InteractLayout', () => ({
  default: () => <div>INTERACT_LAYOUT</div>,
  InteractIndexRedirect: () => <div>INTERACT_INDEX_REDIRECT</div>,
}));

vi.mock('./pages/portal/assistant/AssistantLayout', () => ({
  default: () => <div>ASSISTANT_LAYOUT</div>,
  AssistantIndexRedirect: () => <div>ASSISTANT_INDEX_REDIRECT</div>,
}));

vi.mock('./pages/portal/account/AccountLayout', () => ({
  default: () => <div>ACCOUNT_LAYOUT</div>,
  AccountIndexRedirect: () => <div>ACCOUNT_INDEX_REDIRECT</div>,
}));

vi.mock('./pages/portal/moderation/ModerationLayout', () => ({
  default: () => <div>MODERATION_LAYOUT</div>,
  ModerationIndexRedirect: () => <div>MODERATION_INDEX_REDIRECT</div>,
}));

vi.mock('./components/auth/RequireModeratedBoards', () => ({
  default: () => <div>REQUIRE_MODERATED_BOARDS</div>,
}));

import { AppRoutes } from './App';

function renderAt(path: any) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>
  );
}

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.resetAllMocks();

    checkInitialSetupStatusMock.mockResolvedValue({ setupRequired: false });

    useAuthMock.mockReturnValue({
      isAuthenticated: false,
      loading: false,
      totpSetupRequired: false,
    });

    useAccessMock.mockReturnValue({
      loading: false,
      hasPerm: () => false,
      hasRole: () => false,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders /register route', async () => {
    renderAt('/register');
    expect(await screen.findByText('REGISTER_PAGE')).not.toBeNull();
  });

  it('renders /forgot-password route', async () => {
    renderAt('/forgot-password');
    expect(await screen.findByText('FORGOT_PASSWORD_PAGE')).not.toBeNull();
  });

  it('redirects /admin to /login when not authenticated', async () => {
    renderAt('/admin');
    expect(await screen.findByText('LOGIN_PAGE')).not.toBeNull();
  });

  it('redirects /admin to /forbidden when authenticated but lacks permission', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      totpSetupRequired: false,
    });

    useAccessMock.mockReturnValue({
      loading: false,
      hasPerm: () => false,
      hasRole: () => false,
    });

    renderAt('/admin');
    expect(await screen.findByText('FORBIDDEN_PAGE')).not.toBeNull();
  });

  it('shows loading text while checkInitialSetupStatus is pending', () => {
    checkInitialSetupStatusMock.mockReturnValue(new Promise(() => {}));
    renderAt('/');
    expect(screen.getByText('检查系统状态中...')).not.toBeNull();
  });

  it('redirects / to ADMIN_SETUP_PAGE when setupRequired=true', async () => {
    checkInitialSetupStatusMock.mockResolvedValueOnce({ setupRequired: true });
    renderAt('/');
    expect(await screen.findByText('ADMIN_SETUP_PAGE')).not.toBeNull();
  });

  it('redirects / to PORTAL_LAYOUT when setupRequired=false', async () => {
    checkInitialSetupStatusMock.mockResolvedValueOnce({ setupRequired: false });
    renderAt('/');
    expect(await screen.findByText('PORTAL_LAYOUT')).not.toBeNull();
  });

  it('falls back to setupRequired=false on checkInitialSetupStatus reject and renders PORTAL_LAYOUT', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    checkInitialSetupStatusMock.mockRejectedValueOnce(new Error('fail'));
    renderAt('/');
    expect(await screen.findByText('PORTAL_LAYOUT')).not.toBeNull();
    expect(errorSpy).toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it('redirects /admin-setup to / when setupRequired=false and renders PORTAL_LAYOUT', async () => {
    checkInitialSetupStatusMock.mockResolvedValueOnce({ setupRequired: false });
    renderAt('/admin-setup');
    expect(await screen.findByText('PORTAL_LAYOUT')).not.toBeNull();
  });

  it('redirects /login to /admin-setup when setupRequired=true and no bypass', async () => {
    checkInitialSetupStatusMock.mockResolvedValueOnce({ setupRequired: true });
    renderAt('/login');
    expect(await screen.findByText('ADMIN_SETUP_PAGE')).not.toBeNull();
  });

  it('renders login when bypassing setup after completing setup', async () => {
    checkInitialSetupStatusMock.mockResolvedValueOnce({ setupRequired: true });
    renderAt({ pathname: '/login', state: { setupJustCompleted: true } });
    expect(await screen.findByText('LOGIN_PAGE')).not.toBeNull();
  });
});
