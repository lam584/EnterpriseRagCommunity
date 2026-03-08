import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import React from 'react';

const authServiceMocks = vi.hoisted(() => {
  return {
    getRegistrationStatus: vi.fn(),
    login: vi.fn(),
    resendLogin2faEmail: vi.fn(),
    resendRegisterCode: vi.fn(),
    verifyLogin2fa: vi.fn(),
    verifyRegister: vi.fn(),
  };
});

const authContextMocks = vi.hoisted(() => {
  return {
    setCurrentUser: vi.fn(),
    setIsAuthenticated: vi.fn(),
  };
});

vi.mock('../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/authService')>();
  return {
    ...actual,
    getRegistrationStatus: authServiceMocks.getRegistrationStatus,
    login: authServiceMocks.login,
    resendLogin2faEmail: authServiceMocks.resendLogin2faEmail,
    resendRegisterCode: authServiceMocks.resendRegisterCode,
    verifyLogin2fa: authServiceMocks.verifyLogin2fa,
    verifyRegister: authServiceMocks.verifyRegister,
  };
});

vi.mock('../../contexts/AuthContext', () => {
  return {
    useAuth: () => {
      return {
        setCurrentUser: authContextMocks.setCurrentUser,
        setIsAuthenticated: authContextMocks.setIsAuthenticated,
      };
    },
  };
});

vi.mock('./AuthFooter', () => {
  return {
    default: () => null,
  };
});

vi.mock('../common/OtpCodeInput', () => {
  return {
    default: (props: {
      digits: number;
      value: string;
      onChange: (v: string) => void;
      onComplete?: (v: string) => void;
      disabled?: boolean;
    }) => {
      return (
        <input
          aria-label="OTP"
          value={props.value}
          disabled={props.disabled}
          onChange={(e) => {
            const v = (e.target as HTMLInputElement).value;
            props.onChange(v);
            if (props.onComplete && v.length === props.digits) props.onComplete(v);
          }}
        />
      );
    },
  };
});

import Login from './Login';
import {
  getRegistrationStatus,
  login,
  resendLogin2faEmail,
  resendRegisterCode,
  verifyLogin2fa,
  verifyRegister,
} from '../../services/authService';

const mockGetRegistrationStatus = vi.mocked(getRegistrationStatus);
const mockLogin = vi.mocked(login);
const mockResendLogin2faEmail = vi.mocked(resendLogin2faEmail);
const mockResendRegisterCode = vi.mocked(resendRegisterCode);
const mockVerifyLogin2fa = vi.mocked(verifyLogin2fa);
const mockVerifyRegister = vi.mocked(verifyRegister);

function HomePage() {
  return <div>HOME</div>;
}

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/portal/discover/home" element={<HomePage />} />
        <Route path="/login-state" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>
  );
}

let csrfFetchDone: Promise<void> | null = null;

const mockCsrfFetch = (token?: string, reject?: boolean) => {
  const fetchMock = vi.fn();
  let done: (() => void) | null = null;
  csrfFetchDone = new Promise<void>((resolve) => {
    done = resolve;
  });
  if (reject) {
    fetchMock.mockRejectedValue(new Error('network'));
  } else {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => {
        done?.();
        return { token };
      },
    });
  }
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
};

const waitCsrfReady = async () => {
  if (!csrfFetchDone) return;
  await csrfFetchDone;
  await Promise.resolve();
  await Promise.resolve();
};

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: true } as never);
    mockCsrfFetch('csrf-token');
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    cleanup();
  });

  it('blocks submit when csrf token is missing', async () => {
    mockCsrfFetch('');
    renderLogin();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(screen.getByText('安全令牌缺失，请刷新页面重试')).not.toBeNull();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('logs in successfully and stores remembered credentials when rememberMe is checked', async () => {
    mockLogin.mockResolvedValue({ id: 1, email: 'test@example.com', username: 'u', isDeleted: false } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByLabelText('记住密码'));

    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('HOME')).not.toBeNull();
    expect(authContextMocks.setCurrentUser).toHaveBeenCalled();
    expect(authContextMocks.setIsAuthenticated).toHaveBeenCalledWith(true);

    expect(localStorage.getItem('rememberedEmail')).toBe('test@example.com');
    expect(localStorage.getItem('rememberedPassword')).toBe('123456');
  });

  it('shows error when csrf token fetch fails', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    mockCsrfFetch(undefined, true);
    renderLogin();

    expect(await screen.findByText('无法获取安全令牌，请刷新页面重试')).not.toBeNull();
    expect(mockLogin).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('hides register link when registration is disabled', async () => {
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: false } as never);

    renderLogin();
    await waitCsrfReady();
    await Promise.resolve();

    expect(screen.queryByRole('link', { name: '注册' })).toBeNull();
  });

  it('keeps register link visible when getRegistrationStatus fails', async () => {
    mockGetRegistrationStatus.mockRejectedValue(new Error('network') as never);

    renderLogin();
    await waitCsrfReady();
    await Promise.resolve();

    expect(mockGetRegistrationStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('link', { name: '注册' })).not.toBeNull();
  });

  it('clears remembered credentials when rememberMe is not checked', async () => {
    localStorage.setItem('rememberedEmail', 'old@example.com');
    localStorage.setItem('rememberedPassword', 'old');
    mockLogin.mockResolvedValue({ id: 1, email: 'test@example.com', username: 'u', isDeleted: false } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.click(screen.getByLabelText('记住密码'));
    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('HOME')).not.toBeNull();
    expect(localStorage.getItem('rememberedEmail')).toBeNull();
    expect(localStorage.getItem('rememberedPassword')).toBeNull();
  });

  it('enters verify mode for EMAIL_NOT_VERIFIED and supports verify success', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'verify@example.com';
    mockLogin.mockRejectedValue(err as never);
    mockVerifyRegister.mockResolvedValue(undefined as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'verify@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('账号未完成邮箱验证')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '验证激活' }));
    expect(await screen.findByText('请输入邮箱验证码')).not.toBeNull();

    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '1234' } });
    fireEvent.click(screen.getByRole('button', { name: '验证激活' }));

    expect(await screen.findByText('激活成功，请使用邮箱和密码登录')).not.toBeNull();
    expect(mockVerifyRegister).toHaveBeenCalledWith('verify@example.com', '1234');
  });

  it('uses form email when EMAIL_NOT_VERIFIED does not provide email and supports resend defaults', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    mockLogin.mockRejectedValue(err as never);
    mockResendRegisterCode.mockResolvedValue({} as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'fallback@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    fireEvent.click(screen.getByRole('button', { name: /^重发验证码/ }));
    expect(await screen.findByText('验证码已发送，请查收邮箱')).not.toBeNull();
    expect(mockResendRegisterCode).toHaveBeenCalledWith('fallback@example.com');
  });

  it('shows error when verify register fails', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'verify@example.com';
    mockLogin.mockRejectedValue(err as never);
    mockVerifyRegister.mockRejectedValue(new Error('激活失败') as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'verify@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '1234' } });
    fireEvent.click(screen.getByRole('button', { name: '验证激活' }));

    expect(await screen.findByText('激活失败')).not.toBeNull();
  });

  it('blocks resend when register code is in cooldown', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'cooldown2@example.com';
    mockLogin.mockRejectedValue(err as never);
    mockResendRegisterCode.mockResolvedValue({ resendWaitSeconds: 120, message: 'sent' } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'cooldown2@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    fireEvent.click(screen.getByRole('button', { name: /^重发验证码/ }));
    await screen.findByText('验证码已发送，请查收邮箱');

    const resendButton = screen.getByRole('button', { name: /重发验证码（/ }) as HTMLButtonElement;
    expect(resendButton.disabled).toBe(true);
    expect(mockResendRegisterCode).toHaveBeenCalledTimes(1);
  });

  it('clears resend cooldown localStorage key after expiry', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'expire@example.com';
    mockLogin.mockRejectedValue(err as never);

    const key = 'login:resend-register-code:availableAt:expire@example.com';
    localStorage.setItem(key, String(Date.now() - 1000));

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'expire@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');
    await Promise.resolve();

    expect(localStorage.getItem(key)).toBeNull();
  });

  it('does not apply resend cooldown when localStorage value is invalid', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'invalid@example.com';
    mockLogin.mockRejectedValue(err as never);

    const key = 'login:resend-register-code:availableAt:invalid@example.com';
    localStorage.setItem(key, 'NaN');

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'invalid@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    const resend = screen.getByRole('button', { name: '重发验证码' }) as HTMLButtonElement;
    expect(resend.disabled).toBe(false);
  });

  it('shows error when resend register code fails', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'resendfail@example.com';
    mockLogin.mockRejectedValue(err as never);
    mockResendRegisterCode.mockRejectedValue(new Error('发送失败') as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'resendfail@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    fireEvent.click(screen.getByRole('button', { name: '重发验证码' }));
    expect(await screen.findByText('发送失败')).not.toBeNull();
  });

  it('handles resend register code cooldown and restores countdown from localStorage', async () => {
    const err = new Error('账号未完成邮箱验证') as Error & { code?: string; email?: string };
    err.code = 'EMAIL_NOT_VERIFIED';
    err.email = 'cooldown@example.com';
    mockLogin.mockRejectedValue(err as never);
    mockResendRegisterCode.mockResolvedValue({ resendWaitSeconds: 120, message: 'sent' } as never);

    const { unmount } = renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'cooldown@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    fireEvent.click(screen.getByRole('button', { name: /^重发验证码/ }));
    expect(await screen.findByText('验证码已发送，请查收邮箱')).not.toBeNull();

    unmount();
    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'cooldown@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('账号未完成邮箱验证');

    expect((screen.getByRole('button', { name: /重发验证码（/ }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('supports 2FA email flow with resend cooldown and verify success', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['totp', 'email'];
    err.totpDigits = 8;
    mockLogin.mockRejectedValue(err as never);
    mockResendLogin2faEmail.mockResolvedValue({ resendWaitSeconds: 2, message: 'sent' } as never);
    mockVerifyLogin2fa.mockResolvedValue({ id: 1, email: 'x', username: 'u', isDeleted: false } as never);

    localStorage.setItem('rememberedEmail', 'test@example.com');
    localStorage.setItem('rememberedPassword', 'old');

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('登录需要二次验证')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '完成验证' }));
    expect(await screen.findByText('请选择验证方式')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '使用邮箱验证码' }));

    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }));
    expect(await screen.findByText('sent')).not.toBeNull();
    expect((screen.getByRole('button', { name: '2s' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '999999' } });
    fireEvent.click(screen.getByRole('button', { name: '完成验证' }));

    expect(await screen.findByText('HOME')).not.toBeNull();
    expect(mockVerifyLogin2fa).toHaveBeenCalledWith('email', '999999');
  });

  it('shows error when 2FA resend fails', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[] };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['email'];
    mockLogin.mockRejectedValue(err as never);
    mockResendLogin2faEmail.mockRejectedValue(new Error('发送失败') as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }));
    expect(await screen.findByText('发送失败')).not.toBeNull();
  });

  it('blocks 2FA resend when countdown is active', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['email'];
    mockLogin.mockRejectedValue(err as never);
    mockResendLogin2faEmail.mockResolvedValue({ resendWaitSeconds: 2, message: 'sent' } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }));
    await screen.findByText('sent');

    const resendButton = screen.getByRole('button', { name: '2s' }) as HTMLButtonElement;
    expect(resendButton.disabled).toBe(true);
    fireEvent.click(resendButton);

    expect(mockResendLogin2faEmail).toHaveBeenCalledTimes(1);
  });

  it('shows error when auto-selected email 2FA has empty code and filters invalid methods', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['sms', 'email'];
    mockLogin.mockRejectedValue(err as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.click(screen.getByRole('button', { name: '完成验证' }));
    expect(await screen.findByText('请输入验证码')).not.toBeNull();
    expect(mockVerifyLogin2fa).not.toHaveBeenCalled();
  });

  it('shows error when 2FA verify fails', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['email'];
    mockLogin.mockRejectedValue(err as never);
    mockVerifyLogin2fa.mockRejectedValue(new Error('验证失败') as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.change(screen.getByLabelText('邮箱验证码'), { target: { value: '999999' } });
    fireEvent.click(screen.getByRole('button', { name: '完成验证' }));

    expect(await screen.findByText('验证失败')).not.toBeNull();
  });

  it('uses default resend message and cooldown when 2FA resend response omits fields', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['email'];
    mockLogin.mockRejectedValue(err as never);
    mockResendLogin2faEmail.mockResolvedValue({} as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.click(screen.getByRole('button', { name: '发送验证码' }));
    expect(await screen.findByText('验证码已发送，请查收邮箱')).not.toBeNull();
    expect((screen.getByRole('button', { name: '60s' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('auto-verifies totp with 8 digits when totpDigits=8', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['totp'];
    err.totpDigits = 8;
    mockLogin.mockRejectedValue(err as never);
    mockVerifyLogin2fa.mockResolvedValue({ id: 1, email: 'x', username: 'u', isDeleted: false } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.change(screen.getByLabelText('OTP'), { target: { value: '12345678' } });

    expect(await screen.findByText('HOME')).not.toBeNull();
    expect(mockVerifyLogin2fa).toHaveBeenCalledWith('totp', '12345678');
  });

  it('auto-verifies totp when only totp method is available', async () => {
    const err = new Error('登录需要二次验证') as Error & { code?: string; methods?: string[]; totpDigits?: number };
    err.code = 'LOGIN_2FA_REQUIRED';
    err.methods = ['totp'];
    err.totpDigits = 6;
    mockLogin.mockRejectedValue(err as never);
    mockVerifyLogin2fa.mockResolvedValue({ id: 1, email: 'x', username: 'u', isDeleted: false } as never);

    renderLogin();
    await waitCsrfReady();

    fireEvent.change(screen.getByLabelText('邮箱'), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { name: 'password', value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('登录需要二次验证');

    fireEvent.change(screen.getByLabelText('OTP'), { target: { value: '123456' } });

    expect(await screen.findByText('HOME')).not.toBeNull();
    expect(mockVerifyLogin2fa).toHaveBeenCalledWith('totp', '123456');
  });
});
