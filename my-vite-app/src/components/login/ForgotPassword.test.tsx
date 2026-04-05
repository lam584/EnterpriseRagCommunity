import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

const authServiceMocks = vi.hoisted(() => {
  return {
    getRegistrationStatus: vi.fn(),
  };
});

const passwordResetServiceMocks = vi.hoisted(() => {
  return {
    getPasswordResetStatus: vi.fn(),
    resetPasswordByEmailCode: vi.fn(),
    resetPasswordByTotp: vi.fn(),
    sendPasswordResetEmailCode: vi.fn(),
  };
});

vi.mock('../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/authService')>();
  return {
    ...actual,
    getRegistrationStatus: authServiceMocks.getRegistrationStatus,
  };
});

vi.mock('../../services/passwordResetService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/passwordResetService')>();
  return {
    ...actual,
    getPasswordResetStatus: passwordResetServiceMocks.getPasswordResetStatus,
    resetPasswordByEmailCode: passwordResetServiceMocks.resetPasswordByEmailCode,
    resetPasswordByTotp: passwordResetServiceMocks.resetPasswordByTotp,
    sendPasswordResetEmailCode: passwordResetServiceMocks.sendPasswordResetEmailCode,
  };
});

vi.mock('./AuthFooter', () => {
  return { default: () => null };
});

import ForgotPassword from './ForgotPassword';
import { getRegistrationStatus } from '../../services/authService';
import {
  getPasswordResetStatus,
  resetPasswordByEmailCode,
  resetPasswordByTotp,
  sendPasswordResetEmailCode,
} from '../../services/passwordResetService';

const mockGetRegistrationStatus = vi.mocked(getRegistrationStatus);
const mockGetPasswordResetStatus = vi.mocked(getPasswordResetStatus);
const mockResetPasswordByEmailCode = vi.mocked(resetPasswordByEmailCode);
const mockResetPasswordByTotp = vi.mocked(resetPasswordByTotp);
const mockSendPasswordResetEmailCode = vi.mocked(sendPasswordResetEmailCode);

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

function renderForgotPassword() {
  return render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<div>REGISTER</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function mockBothVerifyMethods() {
  mockGetPasswordResetStatus.mockResolvedValue({
    allowed: true,
    totpEnabled: true,
    emailEnabled: true,
    message: null,
  } as never);
}

async function enterBothVerifyMethodsStep() {
  renderForgotPassword();
  fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'both@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: '下一步' }));
  await screen.findByText(/请选择其中一种方式验证/);
}

async function submitTotpReset(password = '123456', code = '123456') {
  fireEvent.change(screen.getByLabelText('动态验证码'), { target: { value: code } });
  fireEvent.change(screen.getByLabelText('新密码'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: '重置密码' }));
}

async function enterEmailResetFlow(email = 'email@example.com') {
  fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: email } });
  fireEvent.click(screen.getByRole('button', { name: '下一步' }));
  await screen.findByText('该账号可通过邮箱验证码找回密码。');
  fireEvent.click(screen.getByRole('button', { name: '发送邮箱验证码' }));
}

describe('ForgotPassword', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: true } as never);
  });

  afterEach(() => {
    cleanup();
  });

  it('shows error when password reset is not allowed', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: false,
      totpEnabled: false,
      emailEnabled: false,
      message: '暂不支持找回密码',
    } as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'a@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect((await screen.findAllByText('暂不支持找回密码')).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: '下一步' })).not.toBeNull();
  });

  it('resets by totp when totp-only is enabled and navigates to login with email state', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: true,
      emailEnabled: false,
      message: null,
    } as never);
    mockResetPasswordByTotp.mockResolvedValue(undefined as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'totp@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(await screen.findByText('该账号已启用 TOTP，请输入动态验证码并设置新密码。')).not.toBeNull();

    await submitTotpReset();

    expect(await screen.findByText('密码已重置，请使用新密码登录')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '去登录' }));
    expect(await screen.findByText('LOGIN:totp@example.com')).not.toBeNull();
    expect(mockResetPasswordByTotp).toHaveBeenCalledWith('totp@example.com', '123456', '123456');
  });

  it('resets by email code, supports send-code countdown rendering, and handles validation when both methods enabled', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: true,
      emailEnabled: true,
      message: null,
    } as never);
    mockSendPasswordResetEmailCode.mockResolvedValue({ resendWaitSeconds: 2 } as never);
    mockResetPasswordByEmailCode.mockResolvedValue(undefined as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'both@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(await screen.findByText('该账号同时支持 TOTP 和邮箱验证码，请选择其中一种方式验证：')).not.toBeNull();

    const submit = screen.getByRole('button', { name: '重置密码' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: '使用邮箱验证码' }));

    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: '123456' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: '123456' } });
    const form = screen.getByRole('button', { name: '重置密码' }).closest('form');
    fireEvent.submit(form as HTMLFormElement);
    expect(await screen.findByText('请输入邮箱验证码')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '发送邮箱验证码' }));
    expect(await screen.findByText('验证码已发送，请检查邮箱')).not.toBeNull();
    expect((screen.getByRole('button', { name: '2s' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '999999' } });
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));

    expect(await screen.findByText('密码已重置，请使用新密码登录')).not.toBeNull();
    expect(mockResetPasswordByEmailCode).toHaveBeenCalledWith('both@example.com', '999999', '123456');
  });

  it('validates totp format and password rules', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: true,
      emailEnabled: false,
      message: null,
    } as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'v@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    await screen.findByText('该账号已启用 TOTP，请输入动态验证码并设置新密码。');

    fireEvent.change(screen.getByLabelText('动态验证码'), { target: { value: 'abc' } });
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: '123456' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));
    expect(await screen.findByText('动态验证码格式不正确')).not.toBeNull();

    fireEvent.change(screen.getByLabelText('动态验证码'), { target: { value: '123456' } });
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: '123' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));
    expect(await screen.findByText('新密码长度至少 6 位')).not.toBeNull();

    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: '123456' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: '654321' } });
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));
    expect(await screen.findByText('两次输入的新密码不一致')).not.toBeNull();
  });

  it('shows error when status query fails', async () => {
    mockGetPasswordResetStatus.mockRejectedValue(new Error('查询失败') as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'a@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(await screen.findByText('查询失败')).not.toBeNull();
  });

  it('does not query status when email is empty', async () => {
    renderForgotPassword();

    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    expect(mockGetPasswordResetStatus).not.toHaveBeenCalled();
  });

  it('uses default resend wait seconds when resend response omits fields', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: false,
      emailEnabled: true,
      message: null,
    } as never);
    mockSendPasswordResetEmailCode.mockResolvedValue({} as never);

    renderForgotPassword();
    await enterEmailResetFlow();
    expect(await screen.findByText('验证码已发送，请检查邮箱')).not.toBeNull();
    expect((screen.getByRole('button', { name: '60s' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('re-enables send-code button after countdown reaches zero', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: false,
      emailEnabled: true,
      message: null,
    } as never);
    mockSendPasswordResetEmailCode.mockResolvedValue({ resendWaitSeconds: 1 } as never);

    renderForgotPassword();
    await enterEmailResetFlow();
    expect(await screen.findByText('验证码已发送，请检查邮箱')).not.toBeNull();

    expect((screen.getByRole('button', { name: '1s' }) as HTMLButtonElement).disabled).toBe(true);
    await new Promise((r) => setTimeout(r, 1100));

    expect((screen.getByRole('button', { name: '发送邮箱验证码' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows error when reset fails', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: true,
      emailEnabled: false,
      message: null,
    } as never);
    mockResetPasswordByTotp.mockRejectedValue(new Error('重置失败') as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'totp@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    await screen.findByText('该账号已启用 TOTP，请输入动态验证码并设置新密码。');

    await submitTotpReset();

    expect(await screen.findByText('重置失败')).not.toBeNull();
  });

  it('hides register link when registration is disabled', async () => {
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: false } as never);

    renderForgotPassword();

    await waitFor(() => {
      expect(screen.queryByRole('link', { name: '注册' })).toBeNull();
    });
  });

  it('shows error when send email code fails', async () => {
    mockGetPasswordResetStatus.mockResolvedValue({
      allowed: true,
      totpEnabled: false,
      emailEnabled: true,
      message: null,
    } as never);
    mockSendPasswordResetEmailCode.mockRejectedValue(new Error('发送失败') as never);

    renderForgotPassword();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { value: 'email@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    await screen.findByText('该账号可通过邮箱验证码找回密码。');

    fireEvent.click(screen.getByRole('button', { name: '发送邮箱验证码' }));
    expect(await screen.findByText('发送失败')).not.toBeNull();
  });

  it('defaults to registrationEnabled=true when getRegistrationStatus throws', async () => {
    mockGetRegistrationStatus.mockRejectedValue(new Error('network') as never);

    renderForgotPassword();

    expect(screen.getByRole('link', { name: '注册' })).not.toBeNull();
  });

  it('switches verify method to totp when both methods are enabled', async () => {
    mockBothVerifyMethods();
    await enterBothVerifyMethodsStep();

    fireEvent.click(screen.getByRole('button', { name: '使用 TOTP 验证' }));
    expect(await screen.findByLabelText('动态验证码')).not.toBeNull();
    expect(screen.queryByLabelText('邮箱验证码')).toBeNull();
  });

  it('goes back to email step via 上一步', async () => {
    mockBothVerifyMethods();
    await enterBothVerifyMethodsStep();

    fireEvent.click(screen.getByRole('button', { name: '上一步' }));
    expect(await screen.findByRole('button', { name: '下一步' })).not.toBeNull();
  });

  it('runs background carousel interval callback', async () => {
    vi.useFakeTimers();
    try {
      renderForgotPassword();
      vi.advanceTimersByTime(2000);
      await Promise.resolve();
    } finally {
      vi.useRealTimers();
    }
  });
});
