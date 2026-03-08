import { describe, expect, it } from 'vitest';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

describe('publicSiteConfigService', () => {
  it('getPublicSiteConfig returns json on success', async () => {
    resetServiceTest();
    const { getPublicSiteConfig } = await import('./publicSiteConfigService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: { beianText: null, beianHref: null } });
    await expect(getPublicSiteConfig()).resolves.toEqual({ beianText: null, beianHref: null });
  });

  it('getPublicSiteConfig throws on non-2xx', async () => {
    resetServiceTest();
    const { getPublicSiteConfig } = await import('./publicSiteConfigService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, text: '' });
    await expect(getPublicSiteConfig()).rejects.toThrow('Failed to load site config');
  });
});
