import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';

vi.mock('./pages/ForbiddenPage', () => ({
  default: () => <div>FORBIDDEN_PAGE</div>,
}));

import {
  checkInitialSetupStatusMock,
  makeAccess,
  renderAppRoutes,
  useAccessMock,
  useAuthMock,
} from './App.test-helpers';
import { AppRoutes } from './App';

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
    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin', AppRoutes);
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

    renderAppRoutes('/admin/llm-config', AppRoutes);
    expect(await screen.findByText('LLM_CONFIG_PAGE')).not.toBeNull();
  });
});
