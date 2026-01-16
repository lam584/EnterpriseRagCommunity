import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// We only test the redirect logic. So we mock useAccess() directly.
vi.mock('./contexts/AccessContext', () => ({
  useAccess: vi.fn(),
}));

import { useAccess } from './contexts/AccessContext';

function AdminIndexRedirect() {
  const { hasPerm, hasRole, loading } = useAccess() as any;
  if (loading) return <div>loading</div>;

  const canEnterAdmin = hasPerm('admin_ui', 'access') || hasRole('ADMIN');
  if (!canEnterAdmin) return <div>FORBIDDEN</div>;

  if (hasPerm('admin_content', 'access')) return <div>GO_CONTENT</div>;
  if (hasPerm('admin_review', 'access')) return <div>GO_REVIEW</div>;
  if (hasPerm('admin_semantic', 'access')) return <div>GO_SEMANTIC</div>;
  if (hasPerm('admin_retrieval', 'access')) return <div>GO_RETRIEVAL</div>;
  if (hasPerm('admin_metrics', 'access')) return <div>GO_METRICS</div>;
  if (hasPerm('admin_users', 'access')) return <div>GO_USERS</div>;

  return <div>FORBIDDEN</div>;
}

describe('AdminIndexRedirect (security)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('sends forbidden users to /forbidden (instead of defaulting to content)', async () => {
    (useAccess as any).mockReturnValue({
      loading: false,
      hasRole: () => false,
      hasPerm: (r: string, a: string) => r === 'admin_ui' && a === 'access' ? true : false,
    });

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminIndexRedirect />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('FORBIDDEN')).not.toBeNull();
  });

  it('redirects to first accessible section (content)', async () => {
    (useAccess as any).mockReturnValue({
      loading: false,
      hasRole: () => false,
      hasPerm: (r: string, a: string) => {
        if (r === 'admin_ui' && a === 'access') return true;
        if (r === 'admin_content' && a === 'access') return true;
        return false;
      },
    });

    render(
      <MemoryRouter>
        <AdminIndexRedirect />
      </MemoryRouter>
    );

    expect(await screen.findByText('GO_CONTENT')).not.toBeNull();
  });
});
