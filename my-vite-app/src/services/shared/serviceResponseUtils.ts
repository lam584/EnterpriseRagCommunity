export interface SendCodeResponse {
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}

export function parseSendCodeResponse(
  data: Record<string, unknown>,
): SendCodeResponse {
  if (typeof data.message === 'string') {
    return {
      message: data.message,
      resendWaitSeconds: typeof data.resendWaitSeconds === 'number' ? data.resendWaitSeconds : undefined,
      codeTtlSeconds: typeof data.codeTtlSeconds === 'number' ? data.codeTtlSeconds : undefined,
    };
  }

  return {
    message: undefined,
    resendWaitSeconds: typeof data.resendWaitSeconds === 'number' ? data.resendWaitSeconds : undefined,
    codeTtlSeconds: typeof data.codeTtlSeconds === 'number' ? data.codeTtlSeconds : undefined,
  };
}

export function getErrorMessage(data: Record<string, unknown>, fallbackMessage: string): string {
  return typeof data.message === 'string' ? data.message : fallbackMessage;
}
