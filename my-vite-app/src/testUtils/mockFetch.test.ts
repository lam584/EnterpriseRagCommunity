import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createMockResponse,
  mockFetch,
  mockFetchJsonOnce,
  mockFetchQueue,
  mockFetchRejectOnce,
  mockFetchResponseOnce,
  mockFetchTextOnce,
} from './mockFetch';

describe('mockFetch', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('createMockResponse supports ok/status/text/json and headers.get', async () => {
    const res = createMockResponse({
      status: 418,
      text: 'nope',
      headers: { 'Content-Type': 'text/plain' },
    });

    expect(res.ok).toBe(false);
    expect(res.status).toBe(418);
    expect(res.headers.get('content-type')).toBe('text/plain');
    await expect(res.json()).rejects.toBeTruthy();
    await expect(res.text()).resolves.toBe('nope');
  });

  it('createMockResponse supports default status/ok/statusText and json/text helpers', async () => {
    const r1 = createMockResponse({ ok: false });
    expect(r1.status).toBe(400);
    expect(r1.ok).toBe(false);
    expect(r1.statusText).toBe('Error');

    const r2 = createMockResponse({ status: 204 });
    expect(r2.ok).toBe(true);
    expect(r2.statusText).toBe('OK');

    const r3 = createMockResponse({ json: { a: 1 } });
    expect(r3.headers.get('content-type')).toBe('application/json');
    expect(await r3.text()).toBe(JSON.stringify({ a: 1 }));
    expect(await r3.json()).toEqual({ a: 1 });

    const r4 = createMockResponse({ text: 'hello' });
    expect(r4.headers.get('content-type')).toBe('text/plain; charset=utf-8');
    expect(await r4.text()).toBe('hello');

    const r5 = createMockResponse({ text: () => '{"ok":true}' });
    expect(await r5.json()).toEqual({ ok: true });

    const r6 = createMockResponse({ json: () => ({ b: 2 }), headers: { 'content-type': undefined, foo: 'bar' } });
    expect(r6.headers.get('foo')).toBe('bar');
    expect(await r6.text()).toBe(JSON.stringify({ b: 2 }));
  });

  it('createMockResponse can throw from json/text methods', async () => {
    const r1 = createMockResponse({ jsonError: new Error('bad json') });
    await expect(r1.json()).rejects.toThrow('bad json');

    const r2 = createMockResponse({ textError: new Error('bad text') });
    await expect(r2.text()).rejects.toThrow('bad text');
  });

  it('mock helpers install global fetch and return expected response', async () => {
    const f1 = mockFetch();
    f1.mockResolvedValueOnce(createMockResponse({ ok: true, text: 'x' }));
    expect(await (await fetch('/x')).text()).toBe('x');

    const f2 = mockFetchJsonOnce({ ok: true, json: { a: 1 } });
    expect(await (await fetch('/a')).json()).toEqual({ a: 1 });
    expect(f2).toHaveBeenCalledTimes(1);

    const f3 = mockFetchTextOnce({ ok: false, status: 500, statusText: 'NO', text: 'bad' });
    const r3 = await fetch('/b');
    expect(r3.ok).toBe(false);
    expect(r3.status).toBe(500);
    expect(r3.statusText).toBe('NO');
    expect(await r3.text()).toBe('bad');
    expect(f3).toHaveBeenCalledTimes(1);

    const f4 = mockFetchResponseOnce({ ok: true, json: { c: 3 } });
    expect(await (await fetch('/c')).json()).toEqual({ c: 3 });
    expect(f4).toHaveBeenCalledTimes(1);

    const f5 = mockFetchRejectOnce(new Error('net'));
    await expect(fetch('/d')).rejects.toThrow('net');
    expect(f5).toHaveBeenCalledTimes(1);
  });

  it('mockFetchQueue returns responses in order and supports reject steps', async () => {
    const fetchMock = mockFetchQueue([
      { ok: true, json: { a: 1 } },
      { ok: false, status: 500, text: 'bad' },
      { reject: new Error('net') },
    ]);

    const r1 = await fetch('/a');
    expect(await r1.json()).toEqual({ a: 1 });

    const r2 = await fetch('/b');
    expect(r2.ok).toBe(false);
    expect(r2.status).toBe(500);
    expect(await r2.text()).toBe('bad');

    await expect(fetch('/c')).rejects.toThrow('net');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('mockFetchQueue throws when queue is exhausted', async () => {
    mockFetchQueue([]);
    await expect(fetch('/x')).rejects.toThrow('No more mock fetch responses');
  });
});
