export function serviceApiUrl(path: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || '';
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return base ? `${base}${normalized}` : normalized;
}

