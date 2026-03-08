import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const toastMocks = vi.hoisted(() => {
  return {
    success: vi.fn(),
    error: vi.fn(),
  };
});

vi.mock('react-hot-toast', () => {
  return {
    toast: toastMocks,
  };
});

vi.mock('qrcode.react', () => {
  return {
    QRCodeSVG: (props: { value: string; size?: number }) => (
      <div data-testid="mock-qr" data-value={props.value} data-size={String(props.size ?? '')} />
    ),
  };
});

vi.mock('./ChangePasswordCard', () => {
  return {
    default: () => <div data-testid="mock-change-password-card" />,
  };
});

vi.mock('../../../../components/common/Modal', () => {
  return {
    default: (props: { isOpen: boolean; children: React.ReactNode }) => (props.isOpen ? <div data-testid="mock-modal">{props.children}</div> : null),
  };
});

vi.mock('../../../../components/common/OtpCodeInput', () => {
  return {
    default: () => <div data-testid="mock-otp" />,
  };
});

vi.mock('./AccountConnectionsPage', () => {
  return {
    ChangeEmailSection: () => null,
  };
});

vi.mock('../../../../contexts/AuthContext', () => {
  return {
    useAuth: () => ({
      refreshSecurityGate: vi.fn(),
    }),
  };
});

vi.mock('../../../../services/totpAccountService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/totpAccountService')>();
  return {
    ...actual,
    getTotpPolicy: vi.fn(),
    getTotpStatus: vi.fn(),
    enrollTotp: vi.fn(),
    verifyTotp: vi.fn(),
    disableTotp: vi.fn(),
    verifyTotpPassword: vi.fn(),
  };
});

vi.mock('../../../../services/security2faPolicyAccountService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/security2faPolicyAccountService')>();
  return {
    ...actual,
    getMySecurity2faPolicy: vi.fn(),
    updateMyLogin2faPreference: vi.fn(),
    verifyMyLogin2faPreferencePassword: vi.fn(),
  };
});

import AccountSecurityPage from './AccountSecurityPage';
import { toast } from 'react-hot-toast';
import { getTotpPolicy, getTotpStatus } from '../../../../services/totpAccountService';
import { getMySecurity2faPolicy } from '../../../../services/security2faPolicyAccountService';

const mockGetTotpPolicy = vi.mocked(getTotpPolicy);
const mockGetTotpStatus = vi.mocked(getTotpStatus);
const mockGetMySecurity2faPolicy = vi.mocked(getMySecurity2faPolicy);

describe('AccountSecurityPage (initial load failure)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockGetTotpPolicy.mockRejectedValueOnce(new Error('加载失败'));
    mockGetTotpStatus.mockResolvedValueOnce({ enabled: false });
    mockGetMySecurity2faPolicy.mockResolvedValueOnce(null as any);
  });

  afterEach(() => {
    cleanup();
  });

  it('shows totpErrorMsg and toast.error when initial load fails', async () => {
    render(
      <MemoryRouter>
        <AccountSecurityPage />
      </MemoryRouter>,
    );

    const alert = await screen.findByTestId('totp-error');
    expect(alert.textContent).toContain('加载失败');
    expect(toast.error).toHaveBeenCalledWith('加载失败');
  });
});
