function resolveApiBase(): string {
  const globalBase = (globalThis as { __VITE_API_BASE_URL__?: unknown }).__VITE_API_BASE_URL__;
  if (typeof globalBase === 'string') return globalBase;
  return import.meta.env.VITE_API_BASE_URL || '';
}

export function serviceApiUrl(path: string): string {
  const base = resolveApiBase();
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return base ? `${base}${normalized}` : normalized;
}
