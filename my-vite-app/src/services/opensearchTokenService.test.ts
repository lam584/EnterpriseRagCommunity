import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('opensearchTokenService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('tokenizeText returns json on success', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: { result: { tokens: ['a'] } } });
    await expect(tokenizeText('t')).resolves.toMatchObject({ result: { tokens: ['a'] } });
  });

  it('tokenizeText prefers backend message when error body is json', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, text: JSON.stringify({ message: 'bad' }), headers: { 'content-type': 'application/json' } });
    await expect(tokenizeText('t')).rejects.toThrow('bad');
  });

  it('tokenizeText falls back to raw text when json message is not string', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, text: JSON.stringify({ message: 1 }), headers: { 'content-type': 'application/json' } });
    await expect(tokenizeText('t')).rejects.toThrow('{"message":1}');
  });

  it('tokenizeText falls back to raw text when error body is not json', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, text: 'oops' });
    await expect(tokenizeText('t')).rejects.toThrow('oops');
  });

  it('tokenizeText falls back to status when error body is empty', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 503, text: '' });
    await expect(tokenizeText('t')).rejects.toThrow('请求失败: 503');
  });

  it('tokenizeText sends opts in body and csrf header', async () => {
    const { tokenizeText } = await import('./opensearchTokenService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { result: { tokens: ['a'] } } });
    await tokenizeText('t', { workspaceName: 'ws', serviceId: 'svc' });

    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('POST');
    expect(info?.url).toContain('/api/ai/tokenizer');
    expect(info?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(JSON.parse(String(info?.body))).toEqual({ text: 't', workspaceName: 'ws', serviceId: 'svc' });
  });
});
