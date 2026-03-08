import { describe, expect, it } from 'vitest';
import { clearApiBaseUrlForTests, getFetchCallInfo, installFetchMock, resetServiceTest, setApiBaseUrlForTests } from './serviceTestHarness';

describe('serviceTestHarness', () => {
  it('sets and clears API base url override', () => {
    clearApiBaseUrlForTests();
    setApiBaseUrlForTests('https://api.example.com');
    expect((globalThis as any).__VITE_API_BASE_URL__).toBe('https://api.example.com');
    clearApiBaseUrlForTests();
    expect((globalThis as any).__VITE_API_BASE_URL__).toBeUndefined();
  });

  it('installs fetch mock helpers and captures last call', async () => {
    resetServiceTest();
    const { replyOnce, replyJsonOnce, rejectOnce, lastCall } = installFetchMock();

    replyOnce({ ok: true, json: { a: 1 } });
    const r1 = await fetch('/a');
    await expect(r1.json()).resolves.toEqual({ a: 1 });
    expect(lastCall()?.[0]).toBe('/a');
    expect(getFetchCallInfo(lastCall())?.url).toBe('/a');

    replyJsonOnce({ ok: true, json: { b: 2 } });
    const r2 = await fetch('/b');
    await expect(r2.json()).resolves.toEqual({ b: 2 });
    expect(lastCall()?.[0]).toBe('/b');
    expect(getFetchCallInfo(lastCall())?.method).toBe('GET');

    rejectOnce(new Error('no'));
    await expect(fetch('/c')).rejects.toThrow('no');
    expect(lastCall()?.[0]).toBe('/c');
    expect(getFetchCallInfo(lastCall())?.url).toBe('/c');
  });
});
