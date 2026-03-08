import { describe, expect, it } from 'vitest';
import { ApiError, isAdminStepUpRequired, toApiError } from './apiError';

describe('apiError', () => {
  it('toApiError extracts json payload message and code', async () => {
    const res = new Response(JSON.stringify({ message: 'bad', code: 'X', extra: 1 }), {
      status: 400,
      headers: { 'content-type': 'application/json' },
    });

    const err = await toApiError(res, 'fallback');

    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe('bad');
    expect(err.code).toBe('X');
    expect(err.status).toBe(400);
  });

  it('toApiError falls back to text and fallback message', async () => {
    const resText = new Response('plain', { status: 500, headers: { 'content-type': 'text/plain' } });
    const errText = await toApiError(resText, 'fallback');
    expect(errText.message).toBe('plain');

    const resEmpty = new Response('', { status: 500, headers: { 'content-type': 'text/plain' } });
    const errEmpty = await toApiError(resEmpty, 'fallback');
    expect(errEmpty.message).toBe('fallback');
  });

  it('toApiError prefers payload.error and tolerates parse failures', async () => {
    const resError = new Response(JSON.stringify({ message: '', error: 'e', code: 123 }), {
      status: 400,
      headers: { 'content-type': 'application/json' },
    });
    const err1 = await toApiError(resError, 'fallback');
    expect(err1.message).toBe('e');
    expect(err1.code).toBeUndefined();

    const resJsonThrow = {
      status: 500,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => {
        throw new Error('bad json');
      },
      text: async () => '',
    } as unknown as Response;
    const err2 = await toApiError(resJsonThrow, 'fallback');
    expect(err2.message).toBe('fallback');

    const resTextThrow = {
      status: 500,
      headers: new Headers({ 'content-type': 'text/plain' }),
      json: async () => ({}),
      text: async () => {
        throw new Error('bad text');
      },
    } as unknown as Response;
    const err3 = await toApiError(resTextThrow, 'fallback');
    expect(err3.message).toBe('fallback');
  });

  it('isAdminStepUpRequired checks ApiError fields', () => {
    const err = new ApiError('m', 403, 'ADMIN_STEP_UP_REQUIRED');
    expect(isAdminStepUpRequired(err)).toBe(true);
    expect(isAdminStepUpRequired(new ApiError('m', 403, 'OTHER'))).toBe(false);
    expect(isAdminStepUpRequired(new Error('x'))).toBe(false);
  });
});
