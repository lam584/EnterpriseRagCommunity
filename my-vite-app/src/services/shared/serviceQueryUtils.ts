export function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

export function createFetchSignal(args: {
  signal?: AbortSignal;
  timeoutMs?: number;
}): { signal?: AbortSignal; cleanup: () => void } {
  const hasOuter = Boolean(args.signal);
  const hasTimeout = typeof args.timeoutMs === 'number' && Number.isFinite(args.timeoutMs) && args.timeoutMs > 0;
  if (!hasOuter && !hasTimeout) return { signal: undefined, cleanup: () => {} };

  const controller = new AbortController();
  const onAbort = () => {
    try {
      controller.abort();
    } catch {}
  };

  let timeoutId: number | null = null;
  if (hasTimeout) {
    timeoutId = window.setTimeout(() => onAbort(), Math.max(1, Math.floor(args.timeoutMs as number)));
  }

  if (args.signal) {
    if (args.signal.aborted) onAbort();
    else args.signal.addEventListener('abort', onAbort, { once: true });
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      if (timeoutId != null) window.clearTimeout(timeoutId);
      if (args.signal) args.signal.removeEventListener('abort', onAbort);
    },
  };
}
