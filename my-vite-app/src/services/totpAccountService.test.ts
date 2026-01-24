import { describe, expect, it, vi, beforeEach } from 'vitest';
import { verifyTotp } from './totpAccountService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('totpAccountService', () => {
  beforeEach(() => {
    (globalThis as any).fetch = vi.fn();
  });

  it('verifyTotp throws field-level validation message', async () => {
    (fetch as any).mockResolvedValue({
      ok: false,
      json: async () => ({ code: '验证码格式不正确' }),
    });

    await expect(verifyTotp('bad', 'p')).rejects.toThrow('验证码格式不正确');
  });

  it('verifyTotp throws message', async () => {
    (fetch as any).mockResolvedValue({
      ok: false,
      json: async () => ({ message: '验证码不正确' }),
    });

    await expect(verifyTotp('000000', 'p')).rejects.toThrow('验证码不正确');
  });
});
