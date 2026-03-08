import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchRejectOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import {
  adminCreateModerationRule,
  adminDeleteModerationRule,
  adminListModerationRules,
  adminUpdateModerationRule,
} from './moderationRulesService';

describe('moderationRulesService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminListModerationRules sends GET with credentials and query params', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: [
        {
          id: 1,
          name: 'r1',
          type: 'KEYWORD',
          pattern: 'abc',
          severity: 'LOW',
          enabled: true,
          metadata: { category: 'SENSITIVE' },
          createdAt: '2020-01-01T00:00:00.000Z',
        },
      ],
    });

    const res = await adminListModerationRules({ q: 'abc', enabled: true, type: 'KEYWORD', severity: 'LOW', category: 'SENSITIVE' });

    expect(res).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/moderation/rules?');
    expect(url).toContain('q=abc');
    expect(url).toContain('enabled=true');
    expect(url).toContain('type=KEYWORD');
    expect(url).toContain('severity=LOW');
    expect(url).toContain('category=SENSITIVE');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminListModerationRules supports backend { content: [...] } shape', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        content: [
          {
            id: 2,
            name: 'r2',
            type: 'URL',
            pattern: 'example.com',
            severity: 'HIGH',
            enabled: false,
            metadata: null,
            updatedAt: '2020-01-02T00:00:00.000Z',
          },
        ],
      },
    });

    const res = await adminListModerationRules();
    expect(res.map((x) => x.id)).toEqual([2]);
  });

  it('adminListModerationRules returns empty array on unknown backend shape', async () => {
    mockFetchResponseOnce({ ok: true, json: { foo: 'bar' } });
    await expect(adminListModerationRules()).resolves.toEqual([]);
  });

  it('adminListModerationRules throws backend message and does not fallback on 4xx', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListModerationRules()).rejects.toThrow('bad');
  });

  it('adminListModerationRules falls back to localStorage on network error and applies filters/sort', async () => {
    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        {
          id: 1,
          name: 'ad 1',
          type: 'KEYWORD',
          pattern: 'buy now',
          severity: 'LOW',
          enabled: true,
          metadata: { tags: ['ad'] },
          updatedAt: '2020-01-01T00:00:00.000Z',
        },
        {
          id: 2,
          name: 'url 1',
          type: 'URL',
          pattern: 'http',
          severity: 'MEDIUM',
          enabled: true,
          metadata: null,
          updatedAt: '2020-01-03T00:00:00.000Z',
        },
        {
          id: 3,
          name: 'sensitive 1',
          type: 'KEYWORD',
          pattern: 'secret',
          severity: 'HIGH',
          enabled: false,
          metadata: { category: 'SENSITIVE' },
          updatedAt: '2020-01-02T00:00:00.000Z',
        },
      ]),
    );

    mockFetchRejectOnce(new TypeError('Failed to fetch'));

    const res = await adminListModerationRules({ enabled: true, category: 'URL', q: 'url' });

    expect(res.map((x) => x.id)).toEqual([2]);
  });

  it('adminListModerationRules local fallback handles missing/invalid local data', async () => {
    window.localStorage.removeItem('admin.moderation.rules.v1');
    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    await expect(adminListModerationRules()).resolves.toEqual([]);

    window.localStorage.setItem('admin.moderation.rules.v1', '{');
    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    await expect(adminListModerationRules()).resolves.toEqual([]);
  });

  it('adminListModerationRules treats "failed to fetch" Error as network fallback and rethrows others', async () => {
    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        {
          id: 1,
          name: 'b1',
          type: 'KEYWORD',
          pattern: 'x',
          severity: 'LOW',
          enabled: true,
          metadata: { category: 'BLACKLIST' },
          createdAt: '2020-01-01T00:00:00.000Z',
        },
      ]),
    );

    mockFetchRejectOnce(new Error('failed to fetch'));
    await expect(adminListModerationRules({ category: 'BLACKLIST' })).resolves.toHaveLength(1);

    mockFetchRejectOnce(new Error('boom'));
    await expect(adminListModerationRules()).rejects.toThrow('boom');
  });

  it('adminCreateModerationRule local fallback handles non-numeric ids', async () => {
    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        { id: 'x', name: 'bad', type: 'KEYWORD', pattern: 'x', severity: 'LOW', enabled: true, metadata: null },
        { id: 2, name: 'ok', type: 'KEYWORD', pattern: 'y', severity: 'LOW', enabled: true, metadata: null },
      ]),
    );

    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    const created = await adminCreateModerationRule({
      name: 'new',
      type: 'KEYWORD',
      pattern: 'z',
      severity: 'HIGH',
      enabled: false,
      metadata: null,
    });
    expect(created.id).toBe(3);
  });

  it('adminCreateModerationRule sends POST with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        id: 1,
        name: 'r1',
        type: 'KEYWORD',
        pattern: 'abc',
        severity: 'LOW',
        enabled: true,
        metadata: null,
      },
    });

    const res = await adminCreateModerationRule({
      name: 'r1',
      type: 'KEYWORD',
      pattern: 'abc',
      severity: 'LOW',
      enabled: true,
      metadata: null,
    });

    expect(res.id).toBe(1);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
    });
  });

  it('adminCreateModerationRule throws backend message on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(
      adminCreateModerationRule({
        name: 'r1',
        type: 'KEYWORD',
        pattern: 'abc',
        severity: 'LOW',
        enabled: true,
        metadata: null,
      }),
    ).rejects.toThrow('bad');
  });

  it('adminCreateModerationRule falls back to localStorage on network error with incremental id', async () => {
    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        {
          id: 7,
          name: 'old',
          type: 'KEYWORD',
          pattern: 'x',
          severity: 'LOW',
          enabled: true,
          metadata: null,
          createdAt: '2020-01-01T00:00:00.000Z',
          updatedAt: '2020-01-01T00:00:00.000Z',
        },
      ]),
    );

    mockFetchRejectOnce(new TypeError('Failed to fetch'));

    const created = await adminCreateModerationRule({
      name: 'new',
      type: 'KEYWORD',
      pattern: 'y',
      severity: 'HIGH',
      enabled: false,
      metadata: { category: 'BLACKLIST' },
    });

    expect(created.id).toBe(8);
    expect(created.createdAt).toMatch(/\d{4}-\d{2}-\d{2}T/);
    expect(created.updatedAt).toMatch(/\d{4}-\d{2}-\d{2}T/);
    const raw = window.localStorage.getItem('admin.moderation.rules.v1');
    expect(raw).toContain('"id":8');
  });

  it('adminUpdateModerationRule supports backend success and backend error', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: { id: 1, enabled: false } });
    await expect(adminUpdateModerationRule(1, { enabled: false })).resolves.toMatchObject({ enabled: false });
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
    });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateModerationRule(1, { enabled: true })).rejects.toThrow('bad');
  });

  it('adminUpdateModerationRule falls back to localStorage and throws when missing', async () => {
    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        {
          id: 1,
          name: 'r1',
          type: 'KEYWORD',
          pattern: 'a',
          severity: 'LOW',
          enabled: true,
          metadata: null,
          createdAt: '2020-01-01T00:00:00.000Z',
          updatedAt: '2020-01-01T00:00:00.000Z',
        },
      ]),
    );

    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    const updated = await adminUpdateModerationRule(1, { enabled: false, pattern: 'b' });
    expect(updated.enabled).toBe(false);
    expect(updated.pattern).toBe('b');

    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    await expect(adminUpdateModerationRule(999, { enabled: false })).rejects.toThrow('规则不存在或已被删除');
  });

  it('adminDeleteModerationRule throws backend message on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminDeleteModerationRule(1)).rejects.toThrow('bad');
  });

  it('adminDeleteModerationRule sends csrf header, and falls back to localStorage on network error', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: {} });
    await expect(adminDeleteModerationRule(1)).resolves.toBeUndefined();
    expect(fetchMock1).toHaveBeenCalledTimes(1);
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
    });

    window.localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        { id: 1, name: 'r1', type: 'KEYWORD', pattern: 'a', severity: 'LOW', enabled: true, metadata: null },
        { id: 2, name: 'r2', type: 'KEYWORD', pattern: 'b', severity: 'LOW', enabled: true, metadata: null },
      ]),
    );
    mockFetchRejectOnce(new TypeError('Failed to fetch'));
    await expect(adminDeleteModerationRule(1)).resolves.toBeUndefined();
    expect(window.localStorage.getItem('admin.moderation.rules.v1')).not.toContain('"id":1');
  });
});
