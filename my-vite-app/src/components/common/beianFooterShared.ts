import type { PublicSiteConfig } from '../../services/publicSiteConfigService';

type SiteConfigSetter = (value: PublicSiteConfig) => void;

export const EMPTY_SITE_CONFIG: PublicSiteConfig = {
  beianText: null,
  beianHref: null,
  copyrightText: null,
};

export function applySiteConfigIfActive(cancelled: boolean, setSiteConfig: SiteConfigSetter, cfg: PublicSiteConfig): void {
  if (!cancelled) {
    setSiteConfig(cfg);
  }
}

export function fallbackSiteConfigIfActive(cancelled: boolean, setSiteConfig: SiteConfigSetter): void {
  if (!cancelled) {
    setSiteConfig(EMPTY_SITE_CONFIG);
  }
}
