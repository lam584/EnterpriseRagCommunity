import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { adminGetLogRetentionConfig, adminUpdateLogRetentionConfig } from './logRetentionService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('logRetentionService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('adminGetLogRetentionConfig sends GET with credentials', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        enabled: true,
        keepDays: 7,
        mode: 'DELETE',
        maxPerRun: 6000,
        auditLogsEnabled: true,
        accessLogsEnabled: false,
        purgeArchivedEnabled: true,
        purgeArchivedKeepDays: 30,
      },
    });

    const res = await adminGetLogRetentionConfig();

    expect(res).toEqual({
      enabled: true,
      keepDays: 7,
      mode: 'DELETE',
      maxPerRun: 6000,
      auditLogsEnabled: true,
      accessLogsEnabled: false,
      purgeArchivedEnabled: true,
      purgeArchivedKeepDays: 30,
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/log-retention');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminGetLogRetentionConfig applies defaults for missing optional fields', async () => {
    mockFetchResponseOnce({ ok: true, json: { enabled: false, keepDays: 90, mode: 'ARCHIVE_TABLE' } });

    await expect(adminGetLogRetentionConfig()).resolves.toEqual({
      enabled: false,
      keepDays: 90,
      mode: 'ARCHIVE_TABLE',
      maxPerRun: 5000,
      auditLogsEnabled: true,
      accessLogsEnabled: true,
      purgeArchivedEnabled: false,
      purgeArchivedKeepDays: 365,
    });
  });

  it('adminGetLogRetentionConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetLogRetentionConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(adminGetLogRetentionConfig()).rejects.toThrow('获取配置失败');
  });

  it('adminGetLogRetentionConfig falls back when json parsing throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminGetLogRetentionConfig()).rejects.toThrow('获取配置失败');
  });

  it('adminUpdateLogRetentionConfig sends PUT with csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        enabled: false,
        keepDays: 1,
        mode: 'ARCHIVE_TABLE',
        maxPerRun: 5000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      },
    });
    const payload = {
      enabled: false,
      keepDays: 1,
      mode: 'ARCHIVE_TABLE' as const,
      maxPerRun: 5000,
      auditLogsEnabled: true,
      accessLogsEnabled: true,
      purgeArchivedEnabled: false,
      purgeArchivedKeepDays: 365,
    };

    await expect(adminUpdateLogRetentionConfig(payload)).resolves.toEqual(payload);

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/log-retention');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify(payload),
    });
  });

  it('adminUpdateLogRetentionConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'oops' } });
    await expect(
      adminUpdateLogRetentionConfig({
        enabled: true,
        keepDays: 2,
        mode: 'DELETE',
        maxPerRun: 5000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      })
    ).rejects.toThrow('oops');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(
      adminUpdateLogRetentionConfig({
        enabled: true,
        keepDays: 2,
        mode: 'DELETE',
        maxPerRun: 5000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      })
    ).rejects.toThrow('更新配置失败');
  });

  it('adminUpdateLogRetentionConfig falls back when json parsing throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(
      adminUpdateLogRetentionConfig({
        enabled: true,
        keepDays: 2,
        mode: 'DELETE',
        maxPerRun: 5000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      })
    ).rejects.toThrow('更新配置失败');
  });
});
