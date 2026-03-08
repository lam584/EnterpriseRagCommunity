import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('UserService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('fetchReaders builds query string and throws backend message on failure', async () => {
    const { fetchReaders } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: '获取读者列表失败x' } });
    await expect(fetchReaders('a', undefined, 'e')).rejects.toThrow('获取读者列表失败x');
    expect(String(lastCall()?.[0] || '')).toContain('/api/readers/dto?');
  });

  it('fetchReaders omits empty query params and returns list on success', async () => {
    const { fetchReaders } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: [{ id: 1, account: 'a', phone: 'p', email: 'e' }] });
    await expect(fetchReaders('', undefined, '')).resolves.toHaveLength(1);

    const url = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(url.pathname).toBe('/api/readers/dto');
    expect(url.search).toBe('');
  });

  it('fetchReaders includes phone query param when provided', async () => {
    const { fetchReaders } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: [] });
    await fetchReaders(undefined, 'p', undefined);
    const url = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(url.searchParams.get('phone')).toBe('p');
  });

  it('fetchReaders falls back when error body is not json or message missing', async () => {
    const { fetchReaders } = await import('./UserService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(fetchReaders()).rejects.toThrow('获取读者列表失败');

    replyOnce({ ok: false, status: 400, json: {} });
    await expect(fetchReaders()).rejects.toThrow('获取读者列表失败');
  });

  it('fetchReaderById returns dto on success', async () => {
    const { fetchReaderById } = await import('./UserService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: 1, account: 'a', phone: 'p', email: 'e' } });
    await expect(fetchReaderById(1)).resolves.toMatchObject({ id: 1, account: 'a' });
  });

  it('fetchReaderById throws backend message and falls back on json parse failure', async () => {
    const { fetchReaderById } = await import('./UserService');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(fetchReaderById(1)).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(fetchReaderById(1)).rejects.toThrow('获取读者详情失败');
  });

  it('fetchReaderById falls back when backend has no message', async () => {
    const { fetchReaderById } = await import('./UserService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(fetchReaderById(1)).rejects.toThrow('获取读者详情失败');
  });

  it('createReader sends csrf header and returns dto', async () => {
    const { createReader } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: 1, account: 'a', phone: 'p', email: 'e' } });
    await expect(createReader({ account: 'a' } as any)).resolves.toMatchObject({ id: 1 });
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
  });

  it('createReader throws fallback on json parse failure', async () => {
    const { createReader } = await import('./UserService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(createReader({ account: 'a' } as any)).rejects.toThrow('创建读者失败');
  });

  it('createReader falls back when backend has no message', async () => {
    const { createReader } = await import('./UserService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(createReader({ account: 'a' } as any)).rejects.toThrow('创建读者失败');
  });

  it('updateReader sends PUT with csrf header and returns dto on success', async () => {
    const { updateReader } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: 1, account: 'a' } });
    await expect(updateReader(1, { account: 'a' } as any)).resolves.toMatchObject({ id: 1 });

    const info = getFetchCallInfo(lastCall())!;
    expect(info.url).toBe('/api/readers/1');
    expect(info.method).toBe('PUT');
    expect(info.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info.body).toBe(JSON.stringify({ account: 'a' }));
  });

  it('updateReader and deleteReader throw fallback message when error body is not json', async () => {
    const { updateReader, deleteReader } = await import('./UserService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, json: undefined });
    await expect(updateReader(1, { account: 'a' } as any)).rejects.toThrow('更新读者失败');

    replyOnce({ ok: false, status: 400, json: undefined });
    await expect(deleteReader(1)).rejects.toThrow('删除读者失败');
  });

  it('updateReader and deleteReader fall back when json parsing fails', async () => {
    const { updateReader, deleteReader } = await import('./UserService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(updateReader(1, { account: 'a' } as any)).rejects.toThrow('更新读者失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(deleteReader(1)).rejects.toThrow('删除读者失败');
  });

  it('deleteReader returns void on success', async () => {
    const { deleteReader } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: {} });
    await expect(deleteReader(1)).resolves.toBeUndefined();

    const info = getFetchCallInfo(lastCall())!;
    expect(info.url).toBe('/api/readers/1');
    expect(info.method).toBe('DELETE');
    expect(info.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('searchReaders builds query string and returns list', async () => {
    const { searchReaders } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: [{ id: 1, account: 'a', phone: 'p', email: 'e' }] });
    const res = await searchReaders({ id: 1, account: 'a', role: 'r' });
    expect(res).toHaveLength(1);
    expect(String(lastCall()?.[0] || '')).toContain('/api/readers/search/dto?');
  });

  it('searchReaders includes id=0 and omits empty filters; throws fallback on parse failure', async () => {
    const { searchReaders } = await import('./UserService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(searchReaders({ id: 0, account: '', role: '' })).resolves.toHaveLength(0);
    const u1 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u1.pathname).toBe('/api/readers/search/dto');
    expect(u1.searchParams.get('id')).toBe('0');
    expect(u1.searchParams.has('account')).toBe(false);
    expect(u1.searchParams.has('role')).toBe(false);

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(searchReaders({ id: 0 })).rejects.toThrow('搜索读者失败');
  });

  it('searchReaders includes optional filters when provided and falls back when backend has no message', async () => {
    const { searchReaders } = await import('./UserService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await searchReaders({ id: 1, phone: 'p', email: 'e', sex: 's', startDate: '2020-01-01', endDate: '2020-01-02' });
    const u1 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u1.searchParams.get('phone')).toBe('p');
    expect(u1.searchParams.get('email')).toBe('e');
    expect(u1.searchParams.get('sex')).toBe('s');
    expect(u1.searchParams.get('startDate')).toBe('2020-01-01');
    expect(u1.searchParams.get('endDate')).toBe('2020-01-02');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(searchReaders({ id: 1 })).rejects.toThrow('搜索读者失败');
  });
});
