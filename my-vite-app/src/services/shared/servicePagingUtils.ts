export function buildPageQuery(params?: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
}): string {
  const sp = new URLSearchParams();
  sp.set('page', String(params?.page ?? 0));
  sp.set('size', String(params?.size ?? 20));
  if (params?.from) sp.set('from', params.from);
  if (params?.to) sp.set('to', params.to);
  return sp.toString();
}
