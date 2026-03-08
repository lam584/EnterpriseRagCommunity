import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

vi.mock('../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/authService')>();
  return {
    ...actual,
    registerAndGetStatus: vi.fn(),
    getRegistrationStatus: vi.fn(async () => ({ registrationEnabled: true })),
  };
});

vi.mock('./AuthFooter', () => {
  return { default: () => null };
});

import Register from './Register';
import { registerAndGetStatus } from '../../services/authService';

const mockRegisterAndGetStatus = vi.mocked(registerAndGetStatus);

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

describe('Register (success navigation)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows "去登录" after success and navigates with email state', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'ACTIVE', message: '注册成功！请使用您的邮箱和密码登录' } as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    expect(await screen.findByText('注册成功！请使用您的邮箱和密码登录')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '去登录' }));

    expect(await screen.findByText('LOGIN:test@example.com')).not.toBeNull();
  });
});
