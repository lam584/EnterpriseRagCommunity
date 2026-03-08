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

vi.mock('./pages/ForbiddenPage', () => ({
  default: () => <div>FORBIDDEN_PAGE</div>,
}));

import { AppRoutes } from './App';

function renderAt(path: any) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>
  );
}

function makeAccess({
  loading = false,
  roleAdmin = false,
  perms = {},
}: {
  loading?: boolean;
  roleAdmin?: boolean;
  perms?: Record<string, boolean>;
}) {
  return {
    loading,
    hasRole: (r: string) => (r === 'ADMIN' ? roleAdmin : false),
    hasPerm: (resource: string, action: string) => Boolean(perms[`${resource}:${action}`]),
  };
}

describe('AdminIndexRedirect (via AppRoutes /admin)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    checkInitialSetupStatusMock.mockResolvedValue({ setupRequired: false });
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      totpSetupRequired: false,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('shows loading placeholder when access is loading', async () => {
    useAccessMock.mockReturnValue(makeAccess({ loading: true }));
    renderAt('/admin');
    expect(await screen.findByText('加载中...')).not.toBeNull();
  });

  it('redirects to content when role ADMIN can enter and has content permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        roleAdmin: true,
        perms: {
          'admin_content:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('CONTENT_PAGE')).not.toBeNull();
  });

  it('redirects to review when has review permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_review:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('REVIEW_PAGE')).not.toBeNull();
  });

  it('redirects to semantic when has semantic permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_semantic:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('SEMANTIC_PAGE')).not.toBeNull();
  });

  it('redirects to retrieval when has retrieval permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_retrieval:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('RETRIEVAL_PAGE')).not.toBeNull();
  });

  it('redirects to metrics when has metrics permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_metrics:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('METRICS_PAGE')).not.toBeNull();
  });

  it('redirects to users when has users permission', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_users:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('USERS_PAGE')).not.toBeNull();
  });

  it('falls back to forbidden when can enter admin but lacks section permissions', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
        },
      })
    );

    renderAt('/admin');
    expect(await screen.findByText('FORBIDDEN_PAGE')).not.toBeNull();
  });

  it('renders llm-config page when navigating directly to /admin/llm-config', async () => {
    useAccessMock.mockReturnValue(
      makeAccess({
        perms: {
          'admin_ui:access': true,
          'admin_semantic:access': true,
        },
      })
    );

    renderAt('/admin/llm-config');
    expect(await screen.findByText('LLM_CONFIG_PAGE')).not.toBeNull();
  });
});

