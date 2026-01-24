import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, within, cleanup } from '@testing-library/react';

vi.mock('qrcode.react', () => {
  return {
    QRCodeSVG: (props: { value: string; size?: number }) => (
      <div data-testid="mock-qr" data-value={props.value} data-size={String(props.size ?? '')} />
    ),
  };
});

vi.mock('react-hot-toast', () => {
  return {
    toast: {
      success: vi.fn(),
      error: vi.fn(),
    },
  };
});

vi.mock('../../../../services/accountService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/accountService')>();
  return {
    ...actual,
    changePassword: vi.fn(),
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
  };
});

import AccountSecurityPage from './AccountSecurityPage';
import { getTotpPolicy, getTotpStatus, enrollTotp, verifyTotp } from '../../../../services/totpAccountService';

const mockGetTotpPolicy = vi.mocked(getTotpPolicy);
const mockGetTotpStatus = vi.mocked(getTotpStatus);
const mockEnrollTotp = vi.mocked(enrollTotp);
const mockVerifyTotp = vi.mocked(verifyTotp);

describe('AccountSecurityPage (TOTP)', () => {
  beforeEach(() => {
    vi.resetAllMocks();

    mockGetTotpPolicy.mockResolvedValue({
      issuer: 'EnterpriseRagCommunity',
      allowedAlgorithms: ['SHA1', 'SHA256', 'SHA512'],
      allowedDigits: [6, 8],
      allowedPeriodSeconds: [30, 60],
      maxSkew: 2,
      defaultAlgorithm: 'SHA1',
      defaultDigits: 6,
      defaultPeriodSeconds: 30,
      defaultSkew: 1,
    });
    mockGetTotpStatus.mockResolvedValue({ enabled: false });
    mockEnrollTotp.mockResolvedValue({
      otpauthUri: 'EnterpriseRagCommunity:test-1',
      secretBase32: 'ABC',
      algorithm: 'SHA1',
      digits: 6,
      periodSeconds: 30,
      skew: 1,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders TOTP section and can enroll', async () => {
    render(<AccountSecurityPage />);

    expect(await screen.findByText('二次验证（TOTP）')).not.toBeNull();
    expect(mockGetTotpPolicy).toHaveBeenCalledTimes(1);
    expect(mockGetTotpStatus).toHaveBeenCalledTimes(1);

    expect(await screen.findByText(/当前状态：未启用/)).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '生成密钥' }));

    expect(await screen.findByText('绑定信息')).not.toBeNull();
    expect(screen.getByDisplayValue('ABC')).not.toBeNull();
    expect(screen.getByTestId('totp-qr')).not.toBeNull();
    expect(screen.getByTestId('mock-qr').getAttribute('data-value')).toBe('EnterpriseRagCommunity:test-1');
  });

  it('supports maxSkew greater than 2', async () => {
    mockGetTotpPolicy.mockResolvedValueOnce({
      issuer: 'EnterpriseRagCommunity',
      allowedAlgorithms: ['SHA1', 'SHA256', 'SHA512'],
      allowedDigits: [6, 8],
      allowedPeriodSeconds: [30, 60],
      maxSkew: 6,
      defaultAlgorithm: 'SHA1',
      defaultDigits: 6,
      defaultPeriodSeconds: 30,
      defaultSkew: 3,
    });

    render(<AccountSecurityPage />);

    expect((await screen.findAllByText('二次验证（TOTP）')).length).toBeGreaterThan(0);

    const selects = screen.getAllByRole('combobox');
    expect(selects.length).toBeGreaterThanOrEqual(4);
    const skewSelect = selects[3];
    expect(within(skewSelect).getByRole('option', { name: '6' })).not.toBeNull();
  });

  it('shows backend error message when verify fails', async () => {
    mockVerifyTotp.mockRejectedValueOnce(new Error('验证码不正确'));

    render(<AccountSecurityPage />);

    fireEvent.click(await screen.findByRole('button', { name: '生成密钥' }));
    expect(await screen.findByText('绑定信息')).not.toBeNull();

    fireEvent.change(screen.getByPlaceholderText('6 或 8 位数字'), { target: { value: '000000' } });
    fireEvent.change(screen.getByPlaceholderText('用于启用前校验'), { target: { value: 'p' } });
    fireEvent.click(screen.getByRole('button', { name: '启用' }));

    expect(await screen.findByTestId('totp-error')).not.toBeNull();
    expect(screen.getByTestId('totp-error').textContent).toContain('验证码不正确');
  });
});
