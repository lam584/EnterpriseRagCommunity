import { render } from '@testing-library/react';
import type { ComponentType } from 'react';
import { MemoryRouter, Outlet } from 'react-router-dom';
import { vi } from 'vitest';

export const { useAuthMock, useAccessMock, checkInitialSetupStatusMock } = vi.hoisted(() => ({
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

export function renderAppRoutes(path: string, AppRoutes: ComponentType) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>
  );
}

export function makeAccess(args: {
  loading?: boolean;
  roleAdmin?: boolean;
  perms?: Record<string, boolean>;
}) {
  const { loading = false, roleAdmin = false, perms = {} } = args;
  return {
    loading,
    hasRole: (role: string) => (role === 'ADMIN' ? roleAdmin : false),
    hasPerm: (resource: string, action: string) => Boolean(perms[`${resource}:${action}`]),
  };
}
