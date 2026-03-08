import { describe, expect, it } from 'vitest';
import { installFetchMock, resetServiceTest, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';

describe('publicUserProfileService', () => {
  it('getPublicUserProfile returns dto on success', async () => {
    resetServiceTest();
    setApiBaseUrlForTests('');
    const { getPublicUserProfile } = await import('./publicUserProfileService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: { id: 1, username: 'u' } });
    await expect(getPublicUserProfile(1)).resolves.toEqual({ id: 1, username: 'u' });
  });

  it('getPublicUserProfile throws backend message on failure', async () => {
    resetServiceTest();
    setApiBaseUrlForTests('');
    const { getPublicUserProfile } = await import('./publicUserProfileService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPublicUserProfile(1)).rejects.toThrow('bad');
  });

  it('getPublicUserProfile throws fallback when message missing', async () => {
    resetServiceTest();
    setApiBaseUrlForTests('');
    const { getPublicUserProfile } = await import('./publicUserProfileService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, json: {} });
    await expect(getPublicUserProfile(1)).rejects.toThrow('加载用户资料失败');
  });

  it('getPublicUserProfile covers json parse fallback', async () => {
    resetServiceTest();
    setApiBaseUrlForTests('');
    const { getPublicUserProfile } = await import('./publicUserProfileService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPublicUserProfile(1)).resolves.toEqual({});

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPublicUserProfile(1)).rejects.toThrow('加载用户资料失败');
  });
});
