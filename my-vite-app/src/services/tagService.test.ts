import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('tagService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('slugify converts to kebab-case and falls back to a stable tag-* prefix', async () => {
    const { slugify } = await import('./tagService');
    expect(slugify(' Hello World! ')).toBe('hello-world');
    expect(slugify('***')).toMatch(/^tag-[a-z0-9]+$/);
  });

  it('createTag throws local validation errors with fieldErrors', async () => {
    const { createTag } = await import('./tagService');
    installFetchMock();
    await expect(createTag({ type: 'TOPIC', name: '', slug: 'Bad Slug' } as any)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ name: 'Tag name is required', slug: expect.stringContaining('kebab-case') }),
    });
  });

  it('createTag throws backend field errors map when response is a field->message map', async () => {
    const { createTag } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { name: 'bad name' } });

    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'n' })).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: { name: 'bad name' },
    });
  });

  it('listTagsPage maps backend dto and applies defaults for query params', async () => {
    const { listTagsPage } = await import('./tagService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: {
        content: [
          {
            id: 1,
            tenantId: null,
            type: 'TOPIC',
            name: 'N',
            slug: 'n',
            description: null,
            isSystem: false,
            isActive: true,
            createdAt: 't',
            usageCount: null,
          },
        ],
      },
    });

    const page = await listTagsPage({});
    expect(page.content[0]).toMatchObject({ id: 1, type: 'TOPIC', name: 'N', slug: 'n', system: false, active: true, usageCount: 0 });

    const url = String(lastCall()?.[0] || '');
    expect(url).toContain('/api/tags');
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=25');
    expect(url).toContain('sortBy=createdAt');
    expect(url).toContain('sortOrder=desc');
  });

  it('listTagsPage omits blank keyword and trims non-blank keyword', async () => {
    const { listTagsPage } = await import('./tagService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [] } });
    await listTagsPage({ keyword: '   ' });
    const url1 = new URL(String(lastCall()?.[0] || ''), window.location.origin);
    expect(url1.searchParams.get('keyword')).toBeNull();

    replyJsonOnce({ ok: true, json: { content: [] } });
    await listTagsPage({ keyword: '  hello  ' });
    const url2 = new URL(String(lastCall()?.[0] || ''), window.location.origin);
    expect(url2.searchParams.get('keyword')).toBe('hello');
  });

  it('listTagsPage includes boolean flags and tenantId=0 when provided', async () => {
    const { listTagsPage } = await import('./tagService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { content: [] } });
    await listTagsPage({ tenantId: 0, isSystem: false, isActive: false, type: 'TOPIC' });
    const url = new URL(String(lastCall()?.[0] || ''), window.location.origin);
    expect(url.searchParams.get('tenantId')).toBe('0');
    expect(url.searchParams.get('isSystem')).toBe('false');
    expect(url.searchParams.get('isActive')).toBe('false');
    expect(url.searchParams.get('type')).toBe('TOPIC');
  });

  it('listTagsPage throws backend message first and falls back when message is blank', async () => {
    const { listTagsPage } = await import('./tagService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: '后端错误' } });
    await expect(listTagsPage({})).rejects.toThrow('后端错误');

    replyJsonOnce({ ok: false, status: 400, json: { message: '   ' } });
    await expect(listTagsPage({})).rejects.toThrow('加载失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(listTagsPage({})).rejects.toThrow('加载失败');
  });

  it('incrementUsage is a no-op and does not call fetch', async () => {
    const { incrementUsage } = await import('./tagService');
    const { fetchMock } = installFetchMock();
    await expect(incrementUsage()).resolves.toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('updateTag sends only provided fields and rejects invalid slug', async () => {
    const { updateTag } = await import('./tagService');
    installFetchMock();
    await expect(updateTag(1, { slug: 'Bad Slug' })).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ slug: expect.stringContaining('kebab-case') }),
    });

    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: { id: 1, type: 'TOPIC', name: 'n', slug: 'n', isSystem: false, isActive: true, createdAt: 't', usageCount: 0 },
    });
    await updateTag(1, { name: 'n' });
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ name: 'n' }));
  });

  it('updateTag prefers backend field error map over backend message', async () => {
    const { updateTag } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: '后端提示', name: 'bad name' } });
    await expect(updateTag(1, { name: 'n' })).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ name: 'bad name' }),
    });
  });

  it('createTag throws backend message and falls back when response json throws', async () => {
    const { createTag } = await import('./tagService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: '后端说不行', code: 400 } });
    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'n' })).rejects.toThrow('后端说不行');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'n' })).rejects.toThrow('创建失败');
  });

  it('createTag maps payload defaults and maps backend dto to frontend dto', async () => {
    const { createTag } = await import('./tagService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: {
        id: 1,
        tenantId: null,
        type: 'TOPIC',
        name: 'N',
        slug: 'n',
        description: null,
        isSystem: false,
        isActive: true,
        createdAt: 't',
        usageCount: 5,
      },
    });

    await expect(createTag({ type: 'TOPIC', name: 'N', slug: 'n' })).resolves.toMatchObject({
      id: 1,
      tenantId: undefined,
      system: false,
      active: true,
      usageCount: 5,
    });

    expect(lastCall()?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ tenantId: null, type: 'TOPIC', name: 'N', slug: 'n', description: null, isSystem: false, isActive: true }),
    });
  });

  it('createTag falls back when backend payload is not a field->message map', async () => {
    const { createTag } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { name: 'bad name', code: 400 } });
    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'n' })).rejects.toThrow('创建失败');
  });

  it('createTag local validation covers type/name/slug/description branches', async () => {
    const { createTag } = await import('./tagService');
    installFetchMock();

    await expect(createTag({ type: 'BAD', name: 'n', slug: 'n' } as any)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ type: 'Invalid tag type' }),
    });

    await expect(createTag({ type: 'TOPIC', name: 'a'.repeat(65), slug: 'n' } as any)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ name: expect.stringContaining('64') }),
    });

    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'a'.repeat(97) } as any)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ slug: expect.stringContaining('96') }),
    });

    await expect(createTag({ type: 'TOPIC', name: 'n', slug: 'n', description: 'a'.repeat(256) } as any)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ description: expect.stringContaining('255') }),
    });
  });

  it('deleteTag throws fallback message when backend has no message', async () => {
    const { deleteTag } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(deleteTag(1)).rejects.toThrow('删除失败');
  });

  it('deleteTag falls back when response json throws', async () => {
    const { deleteTag } = await import('./tagService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(deleteTag(1)).rejects.toThrow('删除失败');
  });

  it('deleteTag throws backend message first and falls back when message is blank', async () => {
    const { deleteTag } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: '后端错误' } });
    await expect(deleteTag(1)).rejects.toThrow('后端错误');

    replyJsonOnce({ ok: false, status: 400, json: { message: '   ' } });
    await expect(deleteTag(1)).rejects.toThrow('删除失败');
  });

  it('listTagsPage falls back to empty content when backend content is missing', async () => {
    const { listTagsPage } = await import('./tagService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: true, json: {} });
    await expect(listTagsPage({})).resolves.toMatchObject({ content: [] });
  });

  it('updateTag includes boolean false fields in request body', async () => {
    const { updateTag } = await import('./tagService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: { id: 1, type: 'TOPIC', name: 'n', slug: 'n', isSystem: false, isActive: false, createdAt: 't', usageCount: 0 },
    });

    await expect(updateTag(1, { active: false, system: false, description: '' })).resolves.toMatchObject({ active: false, system: false });
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ description: '', isSystem: false, isActive: false }));
  });
});
