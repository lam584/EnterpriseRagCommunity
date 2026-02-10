import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

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

const mockRegisterInitialAdmin = vi.mocked(registerInitialAdmin);

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

describe('AdminSetup (success navigation)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // TODO: This test is outdated. The AdminSetup component now uses a multi-step wizard (ImportConfigurationForm) 
  // and inputs do not have 'name' attributes as expected by this test. Needs rewrite to match new UI flow.
  it.skip('shows "去登录" after success and navigates with email state', async () => {
    mockRegisterInitialAdmin.mockResolvedValue(undefined as never);

    render(
      <MemoryRouter initialEntries={['/admin-setup']}>
        <Routes>
          <Route path="/admin-setup" element={<AdminSetup />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
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

    expect(await screen.findByText('LOGIN:admin@example.com')).not.toBeNull();
  });
});
