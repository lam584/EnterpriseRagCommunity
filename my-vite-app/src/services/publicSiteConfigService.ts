export interface PublicSiteConfig {
  beianText: string | null;
  beianHref: string | null;
}

export async function getPublicSiteConfig(): Promise<PublicSiteConfig> {
  const res = await fetch('/api/public/site-config');
  if (!res.ok) {
    throw new Error('Failed to load site config');
  }
  return res.json();
}
