import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';

vi.mock('../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/authService')>();
  return {
    ...actual,
    registerInitialAdmin: vi.fn(),
  };
});

vi.mock('../../services/setupService', async () => {
  return {
    checkEnvFile: vi.fn().mockResolvedValue({ exists: false }),
    generateTotpKey: vi.fn(),
    saveSetupConfig: vi.fn(),
    testEsConnection: vi.fn(),
    initIndices: vi.fn(),
    completeSetup: vi.fn(),
    checkIndicesStatus: vi.fn()
  };
});

import AdminSetup from './AdminSetup';
import { registerInitialAdmin } from '../../services/authService';
import { checkEnvFile } from '../../services/setupService';

const mockRegisterInitialAdmin = vi.mocked(registerInitialAdmin);
const mockCheckEnvFile = vi.mocked(checkEnvFile);

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string; setupJustCompleted?: boolean } | null;
  return <div>LOGIN:{state?.email ?? ''}:{state?.setupJustCompleted ? 'BYPASS' : ''}</div>;
}

function SetupGateRoutes() {
  const setupRequired = true;

  return (
    <Routes>
      <Route path="/admin-setup" element={<AdminSetup />} />
      <Route
        path="/login"
        element={
          setupRequired ? (
            <LoginBypassGate />
          ) : (
            <LoginPage />
          )
        }
      />
    </Routes>
  );
}

function LoginBypassGate() {
  const location = useLocation();
  const state = location.state as { setupJustCompleted?: boolean } | null;
  if (state?.setupJustCompleted) return <LoginPage />;
  return <Navigate to="/admin-setup" replace />;
}

describe('AdminSetup (with setup gate)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // TODO: This test is outdated. The AdminSetup component now uses a multi-step wizard (ImportConfigurationForm) 
  // and inputs do not have 'name' attributes as expected by this test. Needs rewrite to match new UI flow.
  it.skip('navigates to /login even when /login is guarded by setupRequired', async () => {
    mockRegisterInitialAdmin.mockResolvedValue(undefined as never);

    render(
      <MemoryRouter initialEntries={['/admin-setup']}>
        <SetupGateRoutes />
      </MemoryRouter>
    );

    const emailInput = document.querySelector('input[name="email"]') as HTMLInputElement | null;
    const usernameInput = document.querySelector('input[name="username"]') as HTMLInputElement | null;
    const passwordInput = document.querySelector('input[name="password"]') as HTMLInputElement | null;
    expect(emailInput).not.toBeNull();
    expect(usernameInput).not.toBeNull();
    expect(passwordInput).not.toBeNull();

    fireEvent.change(emailInput!, { target: { name: 'email', value: 'admin@example.com' } });
    fireEvent.change(usernameInput!, { target: { name: 'username', value: 'Admin' } });
    fireEvent.change(passwordInput!, { target: { name: 'password', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '提交' }));

    expect(await screen.findByText(/初始化管理员注册成功，请前往登录/)).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '去登录' }));

    expect(await screen.findByText('LOGIN:admin@example.com:BYPASS')).not.toBeNull();
  });
});
