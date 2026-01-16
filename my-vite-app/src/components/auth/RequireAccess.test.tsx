import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: vi.fn(),
}));
vi.mock('../../contexts/AccessContext', () => ({
  useAccess: vi.fn(),
}));

import { useAuth } from '../../contexts/AuthContext';
import { useAccess } from '../../contexts/AccessContext';
import { RequireAccess } from './RequireAccess';

// Use typed helpers to avoid `any`.
const mockUseAuth = vi.mocked(useAuth);
const mockUseAccess = vi.mocked(useAccess);

function LoginPage() {
  return <div>LOGIN</div>;
}

function ForbiddenPage() {
  return <div>FORBIDDEN</div>;
}

function ProtectedContent() {
  return <div>OK</div>;
}

describe('RequireAccess', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('redirects unauthenticated users to /login when requiresAuth', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, loading: false } as ReturnType<typeof useAuth>);
    mockUseAccess.mockReturnValue({
      loading: false,
      refresh: vi.fn(),
      hasPerm: () => false,
      hasRole: () => false,
      hasAuthority: () => false,
      email: null,
      roles: [],
      permissions: [],
    } as ReturnType<typeof useAccess>);

    render(
      <MemoryRouter initialEntries={['/portal/posts/create']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAccess requiresAuth />}>
            <Route path="/portal/posts/create" element={<ProtectedContent />} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('LOGIN')).not.toBeNull();
  });

  it('redirects authenticated but unauthorized users to /forbidden when perm requirement is set', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, loading: false } as ReturnType<typeof useAuth>);
    mockUseAccess.mockReturnValue({
      loading: false,
      refresh: vi.fn(),
      hasPerm: () => false,
      hasRole: () => false,
      hasAuthority: () => false,
      email: 'x@example.com',
      roles: ['user'],
      permissions: [],
    } as ReturnType<typeof useAccess>);

    render(
      <MemoryRouter initialEntries={['/portal/interact/likes']}>
        <Routes>
          <Route path="/forbidden" element={<ForbiddenPage />} />
          <Route element={<RequireAccess requiresAuth resource="portal_interact_likes" action="view" />}>
            <Route path="/portal/interact/likes" element={<ProtectedContent />} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('FORBIDDEN')).not.toBeNull();
  });

  it('allows access when authenticated and no perm requirement', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, loading: false } as ReturnType<typeof useAuth>);
    mockUseAccess.mockReturnValue({
      loading: false,
      refresh: vi.fn(),
      hasPerm: () => false,
      hasRole: () => false,
      hasAuthority: () => false,
      email: 'x@example.com',
      roles: ['user'],
      permissions: [],
    } as ReturnType<typeof useAccess>);

    render(
      <MemoryRouter initialEntries={['/x']}>
        <Routes>
          <Route element={<RequireAccess requiresAuth />}>
            <Route path="/x" element={<ProtectedContent />} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('OK')).not.toBeNull();
  });
});

