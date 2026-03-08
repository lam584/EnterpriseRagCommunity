import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Outlet } from 'react-router-dom';

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

vi.mock('./components/auth/RequirePermission', () => ({
  RequirePermission: () => <Outlet />,
}));

vi.mock('./pages/admin/AdminDashboardLayout', () => ({
  default: () => (
    <div>
      ADMIN_LAYOUT
      <Outlet />
    </div>
  ),
}));

vi.mock('./pages/admin/sections', () => ({
  ContentMgmtPage: () => <div>CONTENT_PAGE</div>,
  ReviewCenterPage: () => <div>REVIEW_PAGE</div>,
  SemanticBoostPage: () => <div>SEMANTIC_PAGE</div>,
  RetrievalRagPage: () => <div>RETRIEVAL_PAGE</div>,
  MetricsMonitorPage: () => <div>METRICS_PAGE</div>,
  UsersRBACPage: () => <div>USERS_PAGE</div>,
  LlmConfigPage: () => <div>LLM_CONFIG_PAGE</div>,
}));

vi.mock('./components/login/Login', () => ({
  default: () => <div>LOGIN_PAGE</div>,
}));

import { AppRoutes } from './App';

function renderAt(path: any) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.resetAllMocks();

    checkInitialSetupStatusMock.mockResolvedValue({ setupRequired: false });

    useAccessMock.mockReturnValue({
      loading: false,
      hasPerm: () => true,
      hasRole: () => false,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders loading placeholder when auth is loading', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: false,
      loading: true,
      totpSetupRequired: false,
    });

    renderAt('/admin/content');
    expect(await screen.findByText('加载中...')).not.toBeNull();
  });

  it('redirects to login when not authenticated', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: false,
      loading: false,
      totpSetupRequired: false,
    });

    renderAt('/admin/content');
    expect(await screen.findByText('LOGIN_PAGE')).not.toBeNull();
  });

  it('renders child routes when authenticated', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      totpSetupRequired: false,
    });

    renderAt('/admin/content');
    expect(await screen.findByText('CONTENT_PAGE')).not.toBeNull();
  });
});

