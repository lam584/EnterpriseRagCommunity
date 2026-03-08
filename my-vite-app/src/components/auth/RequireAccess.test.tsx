import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Outlet, Route, Routes, useLocation, useOutletContext } from 'react-router-dom';
import { RequireAccess } from './RequireAccess';

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

function ParentLayout() {
  return <Outlet context={{ foo: 'bar' }} />;
}

function Child() {
  const { foo } = useOutletContext<{ foo: string }>();
  return <div>{foo}</div>;
}

function LoginPage() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string } } | null;
  return <div>LOGIN:{state?.from?.pathname ?? ''}</div>;
}

function ForbiddenPage() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string } } | null;
  return <div>FORBIDDEN:{state?.from?.pathname ?? ''}</div>;
}

function SecurityLanding() {
  const location = useLocation();
  const state = location.state as { from?: { pathname?: string } } | null;
  return <div>SECURITY:{location.search}:{state?.from?.pathname ?? ''}</div>;
}

describe('RequireAccess', () => {
  let hasPerm: ReturnType<typeof vi.fn>;
  let hasRole: ReturnType<typeof vi.fn>;

  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    hasPerm = vi.fn(() => true);
    hasRole = vi.fn(() => false);

    useAuthMock.mockReturnValue({ isAuthenticated: true, loading: false, totpSetupRequired: false });
    useAccessMock.mockReturnValue({
      hasPerm,
      hasRole,
      loading: false,
      refresh: vi.fn(() => Promise.resolve()),
    });
  });

  it('renders loading screen while auth is loading', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, loading: true, totpSetupRequired: false });

    render(
      <MemoryRouter initialEntries={['/child']}>
        <Routes>
          <Route element={<RequireAccess />}>
            <Route path="child" element={<div>CHILD</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('加载中...')).not.toBeNull();
  });

  it('renders loading screen while access is loading', () => {
    useAccessMock.mockReturnValue({
      hasPerm,
      hasRole,
      loading: true,
      refresh: vi.fn(() => Promise.resolve()),
    });

    render(
      <MemoryRouter initialEntries={['/child']}>
        <Routes>
          <Route element={<RequireAccess />}>
            <Route path="child" element={<div>CHILD</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('加载中...')).not.toBeNull();
  });

  it('redirects to loginPath with state.from when unauthenticated', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, loading: false, totpSetupRequired: false });

    render(
      <MemoryRouter initialEntries={['/private']}>
        <Routes>
          <Route path="my-login" element={<LoginPage />} />
          <Route element={<RequireAccess loginPath="/my-login" />}>
            <Route path="private" element={<div>PRIVATE</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('LOGIN:/private')).not.toBeNull();
  });

  it('redirects to totp setup when totpSetupRequired and not already on security page', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: true, loading: false, totpSetupRequired: true });

    render(
      <MemoryRouter initialEntries={['/needs-totp']}>
        <Routes>
          <Route element={<RequireAccess />}>
            <Route path="needs-totp" element={<div>NEEDS_TOTP</div>} />
            <Route path="portal/account/security" element={<SecurityLanding />} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('SECURITY:?force=totp:/needs-totp')).not.toBeNull();
  });

  it('does not redirect to totp setup when already on /portal/account/security', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: true, loading: false, totpSetupRequired: true });

    render(
      <MemoryRouter initialEntries={['/portal/account/security']}>
        <Routes>
          <Route element={<RequireAccess />}>
            <Route path="portal/account/security" element={<SecurityLanding />} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('SECURITY::')).not.toBeNull();
  });

  it('redirects to forbiddenPath when permission check fails', () => {
    hasPerm.mockReturnValue(false);

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route path="nope" element={<ForbiddenPage />} />
          <Route element={<RequireAccess resource="r" action="a" forbiddenPath="/nope" />}>
            <Route path="secret" element={<div>SECRET</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('FORBIDDEN:/secret')).not.toBeNull();
  });

  it('allows access when allowRoles matches even if permission check fails', () => {
    hasPerm.mockReturnValue(false);
    hasRole.mockImplementation((r: string) => r === 'ADMIN');

    render(
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<RequireAccess resource="r" action="a" allowRoles={['ADMIN']} />}>
            <Route path="secret" element={<div>OK</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('OK')).not.toBeNull();
  });

  it('allows access when requiresAuth=false even if unauthenticated', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, loading: false, totpSetupRequired: false });

    render(
      <MemoryRouter initialEntries={['/public']}>
        <Routes>
          <Route element={<RequireAccess requiresAuth={false} />}>
            <Route path="public" element={<div>PUBLIC</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('PUBLIC')).not.toBeNull();
  });

  it('forwards parent outlet context to nested routes', () => {
    render(
      <MemoryRouter initialEntries={['/child']}>
        <Routes>
          <Route element={<ParentLayout />}>
            <Route element={<RequireAccess />}>
              <Route path="child" element={<Child />} />
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('bar')).not.toBeNull();
  });
});
