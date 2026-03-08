import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { RequirePermission } from './RequirePermission';

const { useAuthMock, useAccessMock } = vi.hoisted(() => ({
  useAuthMock: vi.fn(),
  useAccessMock: vi.fn(),
}));

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: useAuthMock,
}));

vi.mock('../../contexts/AccessContext', () => ({
  useAccess: useAccessMock,
}));

function LoginPage() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string; search?: string } } | null;
  return <div>LOGIN:{`${state?.from?.pathname ?? ''}${state?.from?.search ?? ''}`}</div>;
}

function ForbiddenPage() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string } } | null;
  return <div>FORBIDDEN:{state?.from?.pathname ?? ''}</div>;
}

function NopePage() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string } } | null;
  return <div>NOPE:{state?.from?.pathname ?? ''}</div>;
}

describe('RequirePermission', () => {
  let hasPerm: ReturnType<typeof vi.fn>;
  let hasRole: ReturnType<typeof vi.fn>;

  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    hasPerm = vi.fn(() => true);
    hasRole = vi.fn(() => false);

    useAuthMock.mockReturnValue({ isAuthenticated: true, loading: false });
    useAccessMock.mockReturnValue({ hasPerm, hasRole, loading: false });
  });

  it('renders loading screen while auth is loading', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, loading: true });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequirePermission resource="r" action="a" />}>
            <Route path="protected" element={<div>PROTECTED</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('加载中...')).not.toBeNull();
  });

  it('renders loading screen while access is loading', () => {
    useAccessMock.mockReturnValue({ hasPerm, hasRole, loading: true });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequirePermission resource="r" action="a" />}>
            <Route path="protected" element={<div>PROTECTED</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('加载中...')).not.toBeNull();
  });

  it('redirects to /login with state.from when unauthenticated', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, loading: false });

    render(
      <MemoryRouter initialEntries={['/protected?foo=1']}>
        <Routes>
          <Route path="login" element={<LoginPage />} />
          <Route element={<RequirePermission resource="r" action="a" />}>
            <Route path="protected" element={<div>PROTECTED</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('LOGIN:/protected?foo=1')).not.toBeNull();
  });

  it('redirects to /forbidden when permission check fails', () => {
    hasPerm.mockReturnValue(false);

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route path="forbidden" element={<ForbiddenPage />} />
          <Route element={<RequirePermission resource="r" action="a" />}>
            <Route path="secret" element={<div>SECRET</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('FORBIDDEN:/secret')).not.toBeNull();
  });

  it('redirects to redirectTo when permission check fails', () => {
    hasPerm.mockReturnValue(false);

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route path="nope" element={<NopePage />} />
          <Route element={<RequirePermission resource="r" action="a" redirectTo="/nope" />}>
            <Route path="secret" element={<div>SECRET</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('NOPE:/secret')).not.toBeNull();
  });

  it('allows access when allowRoles matches even if permission check fails', () => {
    hasPerm.mockReturnValue(false);
    hasRole.mockImplementation((r: string) => r === 'ADMIN');

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<RequirePermission resource="r" action="a" allowRoles={['ADMIN']} />}>
            <Route path="secret" element={<div>OK</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('OK')).not.toBeNull();
  });

  it('renders Outlet when permission check passes', () => {
    hasPerm.mockReturnValue(true);

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<RequirePermission resource="r" action="a" />}>
            <Route path="secret" element={<div>OK</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('OK')).not.toBeNull();
  });
});

