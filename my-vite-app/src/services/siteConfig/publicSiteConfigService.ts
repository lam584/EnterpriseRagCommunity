export interface PublicSiteConfig {
  beianText: string | null;
  beianHref: string | null;
  copyrightText: string | null;
}

let siteConfigCache: PublicSiteConfig | null = null;
let inFlightSiteConfigPromise: Promise<PublicSiteConfig> | null = null;

export async function getPublicSiteConfig(): Promise<PublicSiteConfig> {
  if (siteConfigCache) return siteConfigCache;
  if (inFlightSiteConfigPromise) return inFlightSiteConfigPromise;

  inFlightSiteConfigPromise = (async () => {
    const res = await fetch('/api/public/site-config');
    if (!res.ok) {
      throw new Error('Failed to load site config');
    }
    const data = (await res.json()) as PublicSiteConfig;
    siteConfigCache = data;
    return data;
  })();

  try {
    return await inFlightSiteConfigPromise;
  } finally {
    inFlightSiteConfigPromise = null;
  }
}
