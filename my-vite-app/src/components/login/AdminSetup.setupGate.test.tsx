import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';

import AdminSetup from './AdminSetup';

const setupServiceMocks = vi.hoisted(() => {
  return {
    saveSetupConfig: vi.fn(),
    initIndices: vi.fn(),
    completeSetup: vi.fn(),
    checkEnvFile: vi.fn(async () => ({ exists: false })),
  };
});

vi.mock('react-hot-toast', () => {
  return {
    default: {
      success: vi.fn(),
      error: vi.fn(),
    },
  };
});

vi.mock('../../services/setupService', () => {
  return {
    checkEnvFile: setupServiceMocks.checkEnvFile,
    generateTotpKey: vi.fn(),
    saveSetupConfig: setupServiceMocks.saveSetupConfig,
    testEsConnection: vi.fn(),
    initIndices: setupServiceMocks.initIndices,
    completeSetup: setupServiceMocks.completeSetup,
    checkIndicesStatus: vi.fn(),
    encryptValue: vi.fn(),
  };
});

function LoginPage() {
  const location = useLocation();
  const state = location.state as { setupJustCompleted?: boolean } | null;
  return <div>LOGIN:{state?.setupJustCompleted ? 'BYPASS' : ''}</div>;
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
    setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: false });
    setupServiceMocks.saveSetupConfig.mockResolvedValue(undefined);
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    setupServiceMocks.completeSetup.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
  });

  it('navigates to /login even when /login is guarded by setupRequired', async () => {
    render(
      <MemoryRouter initialEntries={['/admin-setup']}>
        <SetupGateRoutes />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByText('跳过'));
    await screen.findByText('创建管理员账户');

    fireEvent.change(screen.getByPlaceholderText('请输入管理员邮箱'), { target: { value: 'admin@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'Admin' } });
    fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: '123456' } });
    fireEvent.change(screen.getByPlaceholderText('请再次输入密码'), { target: { value: '123456' } });

    fireEvent.click(screen.getByText('完成设置'));

    expect(await screen.findByText('LOGIN:BYPASS')).not.toBeNull();
  });
});
