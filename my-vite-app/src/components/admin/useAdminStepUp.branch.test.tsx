import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { useAdminStepUp } from './useAdminStepUp';

vi.mock('../common/Modal', () => {
  return {
    default: (props: { isOpen: boolean; onClose: () => void; title?: string; children: any }) => {
      return (
        <div>
          <div>{props.title}</div>
          <button type="button" onClick={props.onClose}>
            modal-close
          </button>
          {props.children}
        </div>
      );
    },
  };
});

vi.mock('../ui/button', () => {
  return {
    Button: (props: any) => {
      const { onClick, children, disabled: _disabled, ...rest } = props;
      return (
        <button type="button" onClick={onClick} {...rest}>
          {children}
        </button>
      );
    },
  };
});

vi.mock('../ui/input', () => {
  return {
    Input: (props: any) => {
      return <input {...props} />;
    },
  };
});

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
import { getAdminStepUpStatus } from '../../services/adminStepUpService';

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

describe('useAdminStepUp extra branch coverage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('close 在未 ensure 前触发时不会 resolve（覆盖 resolveRef 为空分支）', async () => {
    render(<Host />);
    fireEvent.click(screen.getByText('modal-close'));
    expect(screen.getByTestId('result').textContent).toBe('');
  });

  it('methods 非数组时回退到空数组并提示无可用方式', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: null });
    render(<Host />);
    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');
    expect(screen.getByText('当前账号没有可用的二次验证方式（TOTP 未启用且邮箱验证码被禁止）。请先在账号安全里启用 TOTP，或联系管理员调整策略。')).toBeTruthy();
  });

  it('emailCountdown>0 时 sendEmail 直接返回（覆盖早返回分支）', async () => {
    (getAdminStepUpStatus as any).mockResolvedValue({ ok: false, methods: ['email'] });
    (sendAccountEmailVerificationCode as any).mockResolvedValue({ resendWaitSeconds: 2, codeTtlSeconds: 60 });
    render(<Host />);

    fireEvent.click(screen.getByText('go'));
    await screen.findByText('高权限操作需要二次验证');
    fireEvent.click(screen.getByText('发送验证码'));

    await act(async () => {
      await Promise.resolve();
    });

    fireEvent.click(screen.getByText('重发（2s）'));
    expect(sendAccountEmailVerificationCode).toHaveBeenCalledTimes(1);
  });
});
