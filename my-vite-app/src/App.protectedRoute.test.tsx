import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';

vi.mock('./components/login/Login', () => ({
  default: () => <div>LOGIN_PAGE</div>,
}));

import {
  checkInitialSetupStatusMock,
  renderAppRoutes,
  useAccessMock,
  useAuthMock,
} from './App.test-helpers';
import { AppRoutes } from './App';

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

    renderAppRoutes('/admin/content', AppRoutes);
    expect(await screen.findByText('加载中...')).not.toBeNull();
  });

  it('redirects to login when not authenticated', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: false,
      loading: false,
      totpSetupRequired: false,
    });

    renderAppRoutes('/admin/content', AppRoutes);
    expect(await screen.findByText('LOGIN_PAGE')).not.toBeNull();
  });

  it('renders child routes when authenticated', async () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      totpSetupRequired: false,
    });

    renderAppRoutes('/admin/content', AppRoutes);
    expect(await screen.findByText('CONTENT_PAGE')).not.toBeNull();
  });
});
