export function isAbsoluteUrl(url: string): boolean {
  return /^[a-zA-Z][a-zA-Z\d+.-]*:/.test(url);
}

/**
 * Convert markdown URLs like `/uploads/...` into an absolute URL pointing to the backend,
 * so images can load in Vite dev (5173) and in split-front/back deployments.
 */
export function resolveAssetUrl(input: string | undefined | null): string | undefined {
  if (!input) return undefined;
  const raw = String(input);
  if (!raw) return undefined;

  // Keep data/blob urls as-is.
  if (raw.startsWith('data:') || raw.startsWith('blob:')) return raw;

  // Already absolute.
  if (isAbsoluteUrl(raw)) return raw;

  const apiBase =
    (globalThis as unknown as { __VITE_API_BASE_URL__?: unknown }).__VITE_API_BASE_URL__ ??
    import.meta.env.VITE_API_BASE_URL ??
    '';

  // Markdown commonly stores uploads as `/uploads/...`
  if (raw.startsWith('/uploads/')) {
    return apiBase ? `${apiBase}${raw}` : raw;
  }

  return raw;
}
