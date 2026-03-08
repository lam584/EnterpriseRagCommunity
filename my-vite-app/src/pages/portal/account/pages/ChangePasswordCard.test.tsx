import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';

const navigate = vi.fn();
const authMocks = vi.hoisted(() => {
  return {
    setCurrentUser: vi.fn(),
    setIsAuthenticated: vi.fn(),
  };
});
const serviceMocks = vi.hoisted(() => {
  return {
    logout: vi.fn(),
    changePassword: vi.fn(),
  };
});
const toastMocks = vi.hoisted(() => {
  return {
    success: vi.fn(),
    error: vi.fn(),
  };
});

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = (await importOriginal()) as any;
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

vi.mock('react-hot-toast', () => {
  return {
    toast: toastMocks,
  };
});

vi.mock('../../../../contexts/AuthContext', () => {
  return {
    useAuth: () => authMocks,
  };
});

vi.mock('../../../../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/authService')>();
  return {
    ...actual,
    logout: serviceMocks.logout,
  };
});

vi.mock('../../../../services/accountService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/accountService')>();
  return {
    ...actual,
    changePassword: serviceMocks.changePassword,
  };
});

vi.mock('../../../../components/common/OtpCodeInput', () => {
  return {
    default: (props: { value: string; onChange: (v: string) => void; disabled?: boolean; digits?: number; autoFocus?: boolean }) => (
      <input
        data-testid="otp-input"
        value={props.value}
        disabled={Boolean(props.disabled)}
        autoFocus={Boolean(props.autoFocus)}
        onChange={(e) => props.onChange(e.target.value)}
      />
    ),
  };
});

import ChangePasswordCard from './ChangePasswordCard';
import { toast } from 'react-hot-toast';
import { changePassword } from '../../../../services/accountService';
import { logout } from '../../../../services/authService';

const mockChangePassword = vi.mocked(changePassword);
const mockLogout = vi.mocked(logout);

function renderCard(override?: Partial<React.ComponentProps<typeof ChangePasswordCard>>) {
  const setErrorMsg = vi.fn();
  const props: React.ComponentProps<typeof ChangePasswordCard> = {
    enabled: true,
    activeTotpDigits: 6,
    policyLoaded: true,
    totpAllowedByPolicy: true,
    totpRequiredByPolicy: false,
    emailOtpAllowedByPolicy: true,
    emailOtpRequiredByPolicy: false,
    errorMsg: null,
    setErrorMsg,
    ...override,
  };
  render(<ChangePasswordCard {...props} />);
  return { setErrorMsg };
}

describe('ChangePasswordCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.setCurrentUser.mockClear();
    authMocks.setIsAuthenticated.mockClear();
    mockChangePassword.mockReset();
    mockLogout.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
  });

  it('validates form on next step and shows error', async () => {
    const { setErrorMsg } = renderCard();

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(setErrorMsg).toHaveBeenCalledWith('请输入旧密码');
    expect(toast.error).toHaveBeenCalledWith('请输入旧密码');
    expect(screen.queryByText('二次验证')).toBeNull();
  });

  it('blocks submit when policy requires TOTP but user has not enabled it', async () => {
    const { setErrorMsg } = renderCard({
      enabled: false,
      policyLoaded: true,
      totpRequiredByPolicy: true,
      totpAllowedByPolicy: true,
      emailOtpAllowedByPolicy: true,
      emailOtpRequiredByPolicy: false,
    });

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));
    const pwdInputs = Array.from(document.querySelectorAll('input[type="password"]'));
    fireEvent.change(pwdInputs[0], { target: { value: 'old' } });
    fireEvent.change(pwdInputs[1], { target: { value: 'newpwd1' } });
    fireEvent.change(pwdInputs[2], { target: { value: 'newpwd1' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    fireEvent.click(await screen.findByRole('button', { name: '更新密码' }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('管理员已强制启用 TOTP，请先启用后再修改密码');
    });
    expect(setErrorMsg).toHaveBeenLastCalledWith('管理员已强制启用 TOTP，请先启用后再修改密码');
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it('submits successfully, calls changePassword, then logs out and navigates after 800ms', async () => {
    mockChangePassword.mockResolvedValue(undefined);
    mockLogout.mockResolvedValue(undefined);

    const { setErrorMsg } = renderCard({
      enabled: true,
      policyLoaded: true,
      totpRequiredByPolicy: true,
      totpAllowedByPolicy: true,
      emailOtpAllowedByPolicy: true,
      emailOtpRequiredByPolicy: false,
      activeTotpDigits: 6,
    });

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));
    const pwdInputs = Array.from(document.querySelectorAll('input[type="password"]'));
    fireEvent.change(pwdInputs[0], { target: { value: 'old' } });
    fireEvent.change(pwdInputs[1], { target: { value: 'newpwd1' } });
    fireEvent.change(pwdInputs[2], { target: { value: 'newpwd1' } });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    fireEvent.change(screen.getAllByTestId('otp-input')[0], { target: { value: '123456' } });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '更新密码' }));

    await Promise.resolve();
    await Promise.resolve();
    expect(mockChangePassword).toHaveBeenCalledWith({
      currentPassword: 'old',
      newPassword: 'newpwd1',
      totpCode: '123456',
      emailCode: undefined,
    });
    expect(setErrorMsg).toHaveBeenCalledWith(null);
    expect(toast.success).toHaveBeenCalledWith('密码修改成功，请重新登录');

    await vi.runAllTimersAsync();
    await Promise.resolve();

    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(authMocks.setCurrentUser).toHaveBeenCalledWith(null);
    expect(authMocks.setIsAuthenticated).toHaveBeenCalledWith(false);
    expect(navigate).toHaveBeenCalledWith('/login', { replace: true });
  });
});
