import { describe, expect, it, vi } from 'vitest';
import { EMPTY_SITE_CONFIG, applySiteConfigIfActive, fallbackSiteConfigIfActive } from './beianFooterShared';

describe('beianFooterShared', () => {
  it('applySiteConfigIfActive sets config when component is active', () => {
    const setSiteConfig = vi.fn();
    const cfg = { beianText: 'a', beianHref: 'b', copyrightText: 'c' };

    applySiteConfigIfActive(false, setSiteConfig, cfg);

    expect(setSiteConfig).toHaveBeenCalledWith(cfg);
  });

  it('applySiteConfigIfActive ignores updates when component is cancelled', () => {
    const setSiteConfig = vi.fn();

    applySiteConfigIfActive(true, setSiteConfig, EMPTY_SITE_CONFIG);

    expect(setSiteConfig).not.toHaveBeenCalled();
  });

  it('fallbackSiteConfigIfActive applies shared empty config when active', () => {
    const setSiteConfig = vi.fn();

    fallbackSiteConfigIfActive(false, setSiteConfig);

    expect(setSiteConfig).toHaveBeenCalledWith(EMPTY_SITE_CONFIG);
  });
});
