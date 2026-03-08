import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

vi.mock('./tagService', () => ({
  slugify: (name: string) => `slug-${name}`,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('riskTagService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('listRiskTagsPage trims keyword and maps backend fields with fallbacks', async () => {
    const { listRiskTagsPage } = await import('./riskTagService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: {
        content: [
          {
            id: 1,
            tenantId: null,
            type: 'RISK',
            name: 'N',
            slug: 's',
            description: null,
            isSystem: true,
            isActive: false,
            threshold: 'x',
            createdAt: 't',
            usageCount: null,
          },
        ],
        totalPages: 1,
        totalElements: 1,
        size: 25,
        number: 1,
      },
    });

    const page = await listRiskTagsPage({ keyword: '  k  ' });
    expect(page.content?.[0]).toMatchObject({
      tenantId: undefined,
      description: undefined,
      threshold: undefined,
      usageCount: 0,
      system: true,
      active: false,
    });

    const u = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u.pathname).toBe('/api/admin/risk-tags');
    expect(u.searchParams.get('keyword')).toBe('k');
    expect(u.searchParams.get('page')).toBe('1');
    expect(u.searchParams.get('pageSize')).toBe('25');
  });

  it('listRiskTagsPage omits blank keyword and throws backend message / fallback on parse failure', async () => {
    const { listRiskTagsPage } = await import('./riskTagService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 25, number: 1 } });
    await listRiskTagsPage({ keyword: '   ' });
    const u = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u.searchParams.has('keyword')).toBe(false);

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(listRiskTagsPage()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(listRiskTagsPage()).rejects.toThrow('加载风险标签失败');
  });

  it('createRiskTag uses trimmed slug, applies defaults, and throws backend message / fallback', async () => {
    const { createRiskTag } = await import('./riskTagService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: {
        id: 1,
        tenantId: 1,
        type: 'RISK',
        name: 'N',
        slug: 's',
        description: null,
        isSystem: false,
        isActive: true,
        threshold: null,
        createdAt: 't',
        usageCount: 0,
      },
    });
    await createRiskTag({ name: 'N', slug: '  s  ' });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('POST');
    expect((info1.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf-token');
    expect(info1.body).toBe(
      JSON.stringify({
        tenantId: 1,
        type: 'RISK',
        name: 'N',
        slug: 's',
        description: null,
        isSystem: false,
        isActive: true,
        threshold: undefined,
      }),
    );

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(createRiskTag({ name: 'N2' })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(createRiskTag({ name: 'N3' })).rejects.toThrow('创建风险标签失败');
  });

  it('updateRiskTag conditionally includes fields and throws backend message / fallback', async () => {
    const { updateRiskTag } = await import('./riskTagService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: {
        id: 1,
        type: 'RISK',
        name: 'N',
        slug: 's',
        description: null,
        isSystem: false,
        isActive: false,
        threshold: 0,
        createdAt: 't',
        usageCount: 0,
      },
    });
    await updateRiskTag(1, { name: null as any, slug: 's', description: null, active: false, threshold: 0 });
    const body1 = JSON.parse(String(getFetchCallInfo(lastCall())!.body));
    expect(body1).toMatchObject({ slug: 's', description: null, isActive: false, threshold: 0 });
    expect(body1).not.toHaveProperty('name');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateRiskTag(1, { name: 'x' })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateRiskTag(1, { name: 'x' })).rejects.toThrow('更新风险标签失败');
  });

  it('deleteRiskTag throws backend message and falls back on json parse failure', async () => {
    const { deleteRiskTag } = await import('./riskTagService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(deleteRiskTag(1)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(deleteRiskTag(1)).rejects.toThrow('删除风险标签失败');
  });
});

