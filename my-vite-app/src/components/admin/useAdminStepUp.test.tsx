import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, act } from '@testing-library/react';
import { useState } from 'react';
import { useAdminStepUp } from './useAdminStepUp';

vi.mock('../../services/emailVerificationService', () => {
  return {
    sendAccountEmailVerificationCode: vi.fn(),
  };
});

vi.mock('../../services/adminStepUpService', () => {
  return {
    getAdminStepUpStatus: vi.fn(),
    verifyAdminStepUp: vi.fn(),
  };
});

import { sendAccountEmailVerificationCode } from '../../services/emailVerificationService';
import { getAdminStepUpStatus, verifyAdminStepUp } from '../../services/adminStepUpService';

function Host() {
  const { ensureAdminStepUp, adminStepUpModal } = useAdminStepUp();
  const [result, setResult] = useState<string>('');
  return (
    <div>
      <button
        onClick={async () => {
          const r = await ensureAdminStepUp();
          setResult(r.ensured ? 'yes' : 'no');
        }}
      >
        go
      </button>
      <div data-testid="result">{result}</div>
      {adminStepUpModal}
    </div>
  );
}

describe('useAdminStepUp', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('resolves immediately when step-up already ok', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: true });
    render(<Host />);
    fireEvent.click(screen.getByText('go'));
    await screen.findByText('yes');
    expect(screen.queryByText('高权限操作需要二次验证')).toBeNull();
  });

  it('opens modal, validates empty code, and verifies successfully', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockResolvedValue({ resendWaitSeconds: 2, codeTtlSeconds: 60 });
    (verifyAdminStepUp as any).mockResolvedValue({});

    render(<Host />);
    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('发送验证码'));
    await screen.findByText('验证码有效期约 1 分钟');
    expect(sendAccountEmailVerificationCode).toHaveBeenCalledWith('ADMIN_STEP_UP');

    fireEvent.click(screen.getByText('确认'));
    await screen.findByText('请输入验证码');

    const input = screen.getByPlaceholderText('输入验证码') as HTMLInputElement;
    fireEvent.change(input, { target: { value: ' 123 ' } });
    fireEvent.click(screen.getByText('确认'));

    await screen.findByText('yes');
    expect(verifyAdminStepUp).toHaveBeenCalledWith({ method: 'email', code: '123' });
  });

  it('resolves ensured=false when cancelled', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['totp'] });
    render(<Host />);
    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('取消'));
    await screen.findByText('no');
  });

  it('shows no-methods hint and disables confirm when no methods are available', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: [] });
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    expect(
      screen.getByText(
        '当前账号没有可用的二次验证方式（TOTP 未启用且邮箱验证码被禁止）。请先在账号安全里启用 TOTP，或联系管理员调整策略。',
      ),
    ).not.toBeNull();
    expect((screen.getByRole('button', { name: '确认' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders sendEmail error and keeps modal open when send fails', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockRejectedValue(new Error('boom'));
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('发送验证码'));
    await screen.findByText('boom');
    expect(screen.getByText('高权限操作需要二次验证')).not.toBeNull();
  });

  it('prevents re-send during countdown and returns to ready after countdown', async () => {
    vi.useFakeTimers();
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockResolvedValue({ resendWaitSeconds: 2, codeTtlSeconds: 60 });
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText('高权限操作需要二次验证')).not.toBeNull();

    vi.useFakeTimers();
    fireEvent.click(screen.getByText('发送验证码'));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText('请在 2s 后重发')).not.toBeNull();
    expect(sendAccountEmailVerificationCode).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText('重发（2s）'));
    expect(sendAccountEmailVerificationCode).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await Promise.resolve();
    });
    expect(screen.getByText('验证码将发送到当前登录邮箱')).not.toBeNull();
    expect(screen.getByText('发送验证码')).not.toBeNull();
  });

  it('shows verify error and does not resolve when verify fails', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (verifyAdminStepUp as any).mockRejectedValue(new Error('bad'));
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    const input = screen.getByPlaceholderText('输入验证码') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '123' } });
    fireEvent.click(screen.getByText('确认'));

    await screen.findByText('bad');
    expect(screen.getByText('高权限操作需要二次验证')).not.toBeNull();
    expect(screen.getByTestId('result').textContent).toBe('');
  });

  it('defaults to totp when totp is available and allows switching methods', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['totp', 'email'] });
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    expect(screen.queryByText('发送验证码')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '邮箱验证码' }));
    expect(screen.getByText('发送验证码')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'TOTP' }));
    expect(screen.queryByText('发送验证码')).toBeNull();
  });

  it('uses default resend wait seconds and default error messages', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockResolvedValue({});
    (verifyAdminStepUp as any).mockRejectedValue('x');
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('发送验证码'));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText('重发（180s）')).not.toBeNull();
    expect(screen.queryByText('验证码有效期约')).toBeNull();

    const input = screen.getByPlaceholderText('输入验证码') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '123' } });
    fireEvent.click(screen.getByText('确认'));
    await screen.findByText('验证失败');
  });

  it('uses default error message when sendEmail throws non-Error', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockRejectedValue('x');
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('发送验证码'));
    await screen.findByText('发送验证码失败');
  });

  it('uses default resend wait when resendWaitSeconds is non-numeric and ignores non-positive ttl', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockResolvedValue({ resendWaitSeconds: 'abc', codeTtlSeconds: 0 });
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');

    fireEvent.click(screen.getByText('发送验证码'));
    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText('重发（180s）')).not.toBeNull();
    expect(screen.queryByText('验证码有效期约')).toBeNull();
  });
});
