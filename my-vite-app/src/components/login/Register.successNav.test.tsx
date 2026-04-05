import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';

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

import { registerAndGetStatus } from '../../services/authService';
import { renderRegisterRoutes, submitRegisterForm } from './Register.test-helpers';

const mockRegisterAndGetStatus = vi.mocked(registerAndGetStatus);

describe('Register (success navigation)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows "去登录" after success and navigates with email state', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'ACTIVE', message: '注册成功！请使用您的邮箱和密码登录' } as never);

    renderRegisterRoutes({ includeLogin: true });
    submitRegisterForm();

    expect(await screen.findByText('注册成功！请使用您的邮箱和密码登录')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '去登录' }));

    expect(await screen.findByText('LOGIN:test@example.com')).not.toBeNull();
  });
});
