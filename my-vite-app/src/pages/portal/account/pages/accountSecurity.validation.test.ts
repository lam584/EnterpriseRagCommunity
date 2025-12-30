import { describe, expect, it } from 'vitest';
import { validateChangePasswordForm } from './accountSecurity.validation';

describe('validateChangePasswordForm', () => {
  it('returns error when old password is empty', () => {
    expect(
      validateChangePasswordForm({ oldPwd: '', newPwd: 'abcdef', confirmNewPwd: 'abcdef' }),
    ).toBe('请输入旧密码');
  });

  it('returns error when confirm password does not match', () => {
    expect(
      validateChangePasswordForm({ oldPwd: 'old', newPwd: 'abcdef', confirmNewPwd: 'abcdeg' }),
    ).toBe('两次输入的新密码不一致');
  });

  it('returns null for valid inputs', () => {
    expect(
      validateChangePasswordForm({ oldPwd: 'oldpwd', newPwd: 'abcdef', confirmNewPwd: 'abcdef' }),
    ).toBeNull();
  });
});

