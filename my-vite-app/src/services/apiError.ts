export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly payload?: Record<string, unknown>;

  constructor(message: string, status: number, code?: string, payload?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.payload = payload;
  }
}

export async function toApiError(res: Response, fallbackMessage: string): Promise<ApiError> {
  const ct = res.headers.get('content-type') || '';
  let payload: Record<string, unknown> | undefined;
  if (ct.includes('application/json')) {
    payload = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  } else {
    const text = await res.text().catch(() => '');
    payload = text ? { message: text } : undefined;
  }

  const message =
    (payload && typeof payload.message === 'string' && payload.message) ||
    (payload && typeof payload.error === 'string' && payload.error) ||
    fallbackMessage;
  const code = payload && typeof payload.code === 'string' ? payload.code : undefined;
  return new ApiError(message, res.status, code, payload);
}

export function isAdminStepUpRequired(e: unknown): boolean {
  return e instanceof ApiError && e.status === 403 && e.code === 'ADMIN_STEP_UP_REQUIRED';
}

