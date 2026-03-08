import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

const authServiceMocks = vi.hoisted(() => {
  return {
    getRegistrationStatus: vi.fn(),
    registerAndGetStatus: vi.fn(),
    verifyRegister: vi.fn(),
  };
});

vi.mock('../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/authService')>();
  return {
    ...actual,
    getRegistrationStatus: authServiceMocks.getRegistrationStatus,
    registerAndGetStatus: authServiceMocks.registerAndGetStatus,
    verifyRegister: authServiceMocks.verifyRegister,
  };
});

vi.mock('./AuthFooter', () => {
  return { default: () => null };
});

import Register from './Register';
import { getRegistrationStatus, registerAndGetStatus, verifyRegister } from '../../services/authService';

const mockGetRegistrationStatus = vi.mocked(getRegistrationStatus);
const mockRegisterAndGetStatus = vi.mocked(registerAndGetStatus);
const mockVerifyRegister = vi.mocked(verifyRegister);

function LoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

describe('Register (branch coverage)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: true } as never);
  });

  afterEach(() => {
    cleanup();
  });

  it('shows registration-closed UI and navigates back to /login', async () => {
    mockGetRegistrationStatus.mockResolvedValue({ registrationEnabled: false } as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('当前站点已关闭用户注册')).not.toBeNull();
    expect(await screen.findByText('注册入口已关闭。如需开通，请联系管理员在后台开启“允许用户注册”。')).not.toBeNull();

    fireEvent.click(screen.getAllByRole('button', { name: '返回登录' })[0]);
    expect(await screen.findByText('LOGIN:')).not.toBeNull();
  });

  it('validates form fields and shows field errors', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    const form = screen.getByRole('button', { name: '注册' }).closest('form');
    fireEvent.submit(form as HTMLFormElement);

    expect(await screen.findByText('用户名不能为空')).not.toBeNull();
    expect(await screen.findByText('密码不能为空')).not.toBeNull();
    expect(await screen.findByText('邮箱不能为空')).not.toBeNull();

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'a' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'bad-email' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '654321' } });
    fireEvent.submit(form as HTMLFormElement);

    expect(await screen.findByText('用户名长度必须在2-20位之间')).not.toBeNull();
    expect(await screen.findByText('邮箱格式不正确')).not.toBeNull();
    expect(await screen.findByText('两次输入的密码不一致')).not.toBeNull();
  });

  it('handles verify step: requires code and shows error on verify failure', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'EMAIL_UNVERIFIED', message: '注册成功，请查收邮箱验证码完成激活' } as never);
    mockVerifyRegister.mockRejectedValue(new Error('激活失败：验证码错误') as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '注册' }));
    expect(await screen.findByText('注册成功，请查收邮箱验证码完成激活')).not.toBeNull();

    const verifyForm = screen.getByRole('button', { name: '激活账号' }).closest('form');
    fireEvent.submit(verifyForm as HTMLFormElement);
    expect(await screen.findByText('请输入邮箱验证码')).not.toBeNull();

    fireEvent.change(screen.getByLabelText(/邮箱验证码/), { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: '激活账号' }));
    expect(await screen.findByText('激活失败：验证码错误')).not.toBeNull();
  });

  it('defaults to registrationEnabled=true when getRegistrationStatus throws', async () => {
    mockGetRegistrationStatus.mockRejectedValue(new Error('network') as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByRole('button', { name: '注册' })).not.toBeNull();
  });

  it('clears field errors on input change', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    const form = screen.getByRole('button', { name: '注册' }).closest('form');
    fireEvent.submit(form as HTMLFormElement);

    await screen.findByText('用户名不能为空');

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'ab' } });
    expect(screen.queryByText('用户名不能为空')).toBeNull();
  });

  it('shows fallback error when register request throws non-Error', async () => {
    mockRegisterAndGetStatus.mockRejectedValue('bad' as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    expect(await screen.findByText('注册失败，请稍后重试')).not.toBeNull();
  });

  it('enters verify step when message mentions 验证码 even if status is not EMAIL_UNVERIFIED', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'ACTIVE', message: '已发送验证码，请查收' } as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    expect(await screen.findByText('已发送验证码，请查收')).not.toBeNull();
    expect(await screen.findByRole('button', { name: '激活账号' })).not.toBeNull();
  });

  it('verifies successfully and shows done CTA to login', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'EMAIL_UNVERIFIED', message: '注册成功，请查收邮箱验证码完成激活' } as never);
    mockVerifyRegister.mockResolvedValue(undefined as never);

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
    await screen.findByText('注册成功，请查收邮箱验证码完成激活');

    fireEvent.change(screen.getByLabelText(/邮箱验证码/), { target: { value: '999999' } });
    fireEvent.click(screen.getByRole('button', { name: '激活账号' }));

    expect(await screen.findByText('激活成功！请使用邮箱和密码登录')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '去登录' }));
    expect(await screen.findByText('LOGIN:test@example.com')).not.toBeNull();
  });

  it('returns to edit form from verify step', async () => {
    mockRegisterAndGetStatus.mockResolvedValue({ status: 'EMAIL_UNVERIFIED', message: '注册成功，请查收邮箱验证码完成激活' } as never);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123456' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123456' } });

    fireEvent.click(screen.getByRole('button', { name: '注册' }));
    await screen.findByText('注册成功，请查收邮箱验证码完成激活');

    fireEvent.click(screen.getByRole('button', { name: '返回修改信息' }));
    expect(await screen.findByRole('button', { name: '注册' })).not.toBeNull();
  });

  it('navigates to /login when clicking 返回登录', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole('button', { name: '返回登录' }));
    expect(await screen.findByText('LOGIN:')).not.toBeNull();
  });

  it('shows password length validation error', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<Register />} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/用户名/), { target: { name: 'username', value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/邮箱/), { target: { name: 'email', value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^密码/), { target: { name: 'password', value: '123' } });
    fireEvent.change(screen.getByLabelText(/确认密码/), { target: { name: 'confirmPassword', value: '123' } });

    const form = screen.getByRole('button', { name: '注册' }).closest('form');
    fireEvent.submit(form as HTMLFormElement);

    expect(await screen.findByText('密码长度必须在6-20位之间')).not.toBeNull();
  });

  it('runs carousel interval callback', async () => {
    vi.useFakeTimers();
    try {
      render(
        <MemoryRouter initialEntries={['/register']}>
          <Routes>
            <Route path="/register" element={<Register />} />
          </Routes>
        </MemoryRouter>
      );
      vi.advanceTimersByTime(2000);
      await Promise.resolve();
    } finally {
      vi.useRealTimers();
    }
  });
});
