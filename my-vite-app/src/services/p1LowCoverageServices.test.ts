import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p1LowCoverageServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('rolePermissionsService covers adminReason header and error conversion', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const {
      listRoleIds,
      listRolePermissionsByRole,
      createRoleWithMatrix,
      replaceRolePermissions,
      upsertRolePermission,
      deleteRolePermission,
      clearRolePermissions,
      listRoleSummaries,
    } = await import('./rolePermissionsService');

    replyJsonOnce({ ok: true, json: [1, 2] });
    await expect(listRoleIds()).resolves.toEqual([1, 2]);
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/role-permissions/roles');

    replyJsonOnce({ ok: true, json: [{ roleId: 1, permissionId: 2, allow: true }] });
    await expect(listRolePermissionsByRole(1)).resolves.toMatchObject([{ roleId: 1 }]);

    replyJsonOnce({ ok: true, json: [{ roleId: 9, permissionId: 2, allow: true }] });
    await expect(createRoleWithMatrix([{ permissionId: 2, allow: true }], { adminReason: 'r' })).resolves.toMatchObject([{ roleId: 9 }]);
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-Admin-Reason': 'r', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: [{ roleId: 9, permissionId: 2, allow: false }] });
    await expect(replaceRolePermissions(9, [{ roleId: 9, permissionId: 2, allow: false }])).resolves.toMatchObject([{ allow: false }]);

    replyJsonOnce({ ok: true, json: { roleId: 1, permissionId: 2, allow: true } });
    await expect(upsertRolePermission({ roleId: 1, permissionId: 2, allow: true })).resolves.toMatchObject({ roleId: 1 });

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(deleteRolePermission(1, 2)).resolves.toBeUndefined();
    expect(getFetchCallInfo(lastCall())?.url).toContain('roleId=1');
    expect(getFetchCallInfo(lastCall())?.url).toContain('permissionId=2');

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(clearRolePermissions(9)).resolves.toBeUndefined();

    replyJsonOnce({ ok: true, json: [{ roleId: 1, roleName: 'r' }] });
    await expect(listRoleSummaries()).resolves.toMatchObject([{ roleId: 1 }]);

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(listRoleIds()).rejects.toThrow('bad');
  });

  it('rolePermissionsService covers omitted adminReason and additional error branches', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const {
      listRolePermissionsByRole,
      createRoleWithMatrix,
      replaceRolePermissions,
      upsertRolePermission,
      deleteRolePermission,
      clearRolePermissions,
      listRoleSummaries,
    } = await import('./rolePermissionsService');

    replyJsonOnce({ ok: true, json: [] });
    await expect(createRoleWithMatrix([{ permissionId: 1, allow: true }], { adminReason: '' })).resolves.toEqual([]);
    expect(getFetchCallInfo(lastCall())?.headers?.['X-Admin-Reason']).toBeUndefined();

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'bad' } });
    await expect(listRolePermissionsByRole(1)).rejects.toMatchObject({ name: 'ApiError', message: 'bad', status: 400 });

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'bad2' } });
    await expect(replaceRolePermissions(1, [])).rejects.toMatchObject({ name: 'ApiError', message: 'bad2', status: 400 });

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: {} });
    await expect(upsertRolePermission({ roleId: 1, permissionId: 2, allow: true })).rejects.toMatchObject({
      name: 'ApiError',
      message: '更新角色权限失败',
      status: 400,
    });

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { error: 'e' } });
    await expect(deleteRolePermission(1, 2)).rejects.toMatchObject({ name: 'ApiError', message: 'e', status: 400 });

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'x' } });
    await expect(clearRolePermissions(1)).rejects.toMatchObject({ name: 'ApiError', message: 'x', status: 400 });

    replyJsonOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'y' } });
    await expect(listRoleSummaries()).rejects.toMatchObject({ name: 'ApiError', message: 'y', status: 400 });
  });

  it('rolePermissionsService covers adminReason-absent branch and non-json toApiError branch', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { createRoleWithMatrix, clearRolePermissions } = await import('./rolePermissionsService');

    replyJsonOnce({ ok: true, json: [] });
    await expect(createRoleWithMatrix([{ permissionId: 1, allow: true }])).resolves.toEqual([]);
    const headers = getFetchCallInfo(lastCall())?.headers as any;
    expect(headers?.['X-Admin-Reason']).toBeUndefined();

    replyOnce({ ok: false, status: 500, headers: { 'content-type': 'text/plain; charset=utf-8' }, text: 'bad text' });
    await expect(clearRolePermissions(9, { adminReason: 'r' })).rejects.toMatchObject({ name: 'ApiError', status: 500, message: 'bad text' });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-Admin-Reason': 'r', 'X-XSRF-TOKEN': 'csrf' });
  });

  it('emailAdminService covers get/update/test and inbox list error fallback', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const {
      getEmailAdminSettings,
      updateEmailAdminSettings,
      sendEmailAdminTest,
      getEmailInboxAdminSettings,
      updateEmailInboxAdminSettings,
      listEmailInboxMessages,
      listEmailSentMessages,
    } = await import('./emailAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true, host: 'h' } });
    await expect(getEmailAdminSettings()).resolves.toMatchObject({ enabled: true });
    expect(getFetchCallInfo(lastCall())?.method).toBe('GET');

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(updateEmailAdminSettings({ enabled: false })).resolves.toMatchObject({ enabled: false });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(sendEmailAdminTest('a@b.com')).resolves.toBeUndefined();
    const testInfo = getFetchCallInfo(lastCall());
    expect(testInfo?.url).toContain('/api/admin/settings/email/test');
    expect(JSON.parse(String(testInfo?.body ?? '{}'))).toEqual({ to: 'a@b.com' });

    replyJsonOnce({ ok: true, json: { protocol: 'IMAP', host: 'h' } });
    await expect(getEmailInboxAdminSettings()).resolves.toMatchObject({ protocol: 'IMAP' });

    replyJsonOnce({ ok: true, json: { protocol: 'IMAP', folder: 'INBOX' } });
    await expect(updateEmailInboxAdminSettings({ folder: 'INBOX' })).resolves.toMatchObject({ folder: 'INBOX' });

    replyJsonOnce({ ok: true, json: [{ id: '1', subject: 's' }] });
    await expect(listEmailInboxMessages(10)).resolves.toMatchObject([{ id: '1' }]);
    expect(getFetchCallInfo(lastCall())?.url).toContain('limit=10');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(listEmailSentMessages()).rejects.toThrow('加载发件箱失败');
  });

  it('emailChangeService covers success mapping and error fallbacks', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const {
      verifyEmailChangePassword,
      sendOldEmailVerificationCode,
      verifyOldEmailOrTotp,
      sendChangeEmailVerificationCode,
      changeEmail,
    } = await import('./emailChangeService');

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(verifyEmailChangePassword('p')).resolves.toBeUndefined();
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/account/email-change/verify-password');

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 'x' } });
    await expect(sendOldEmailVerificationCode()).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: undefined });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(verifyOldEmailOrTotp({ method: 'totp', totpCode: '1' })).rejects.toThrow('bad');

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 2, codeTtlSeconds: 3 } });
    await expect(sendChangeEmailVerificationCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 2, codeTtlSeconds: 3 });

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(changeEmail({ newEmail: 'a@b.com', newEmailCode: 'c' })).rejects.toThrow('更换邮箱失败');
  });

  it('emailChangeService covers method/code combinations and json-parse-failure fallbacks', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { verifyOldEmailOrTotp, sendOldEmailVerificationCode, sendChangeEmailVerificationCode, verifyEmailChangePassword } =
      await import('./emailChangeService');

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(verifyOldEmailOrTotp({ method: 'email', emailCode: '123' })).resolves.toBeUndefined();
    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body ?? '{}'))).toEqual({ method: 'email', emailCode: '123' });

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(verifyOldEmailOrTotp({ method: 'totp', totpCode: '000000' })).resolves.toBeUndefined();
    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body ?? '{}'))).toEqual({ method: 'totp', totpCode: '000000' });

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(verifyOldEmailOrTotp({ method: 'email', emailCode: 'x' })).rejects.toThrow('验证失败');

    replyJsonOnce({ ok: true, json: { message: 1, resendWaitSeconds: '2', codeTtlSeconds: 3 } });
    await expect(sendOldEmailVerificationCode()).resolves.toEqual({ message: undefined, resendWaitSeconds: undefined, codeTtlSeconds: 3 });

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(sendChangeEmailVerificationCode('a@b.com')).rejects.toThrow('发送验证码失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('密码验证失败');
  });

  it('draftService covers mapping, 404 null, upsert branches, validation errors, and delete error', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
    try {
      const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
      const { createEmptyDraft, listDrafts, getDraft, upsertDraft, deleteDraft, draftToPostCreateDTO } = await import('./draftService');

      const d0 = createEmptyDraft({ boardId: 2, tags: ['a'] });
      expect(d0.boardId).toBe(2);
      expect(d0.createdAt).toBe('2026-01-01T00:00:00.000Z');

      replyJsonOnce({
        ok: true,
        json: {
          content: [
            {
              id: 9,
              boardId: 1,
              title: 't',
              content: 'c',
              contentFormat: 'MARKDOWN',
              metadata: { tags: ['x'], attachments: [{ id: 1, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' }] },
              createdAt: 'c1',
              updatedAt: 'c2',
            },
          ],
        },
      });
      const drafts = await listDrafts();
      expect(drafts[0]).toMatchObject({ id: '9', tags: ['x'] });

      replyJsonOnce({ ok: false, status: 404, json: { message: 'not found' } });
      await expect(getDraft('9')).resolves.toBeNull();

      replyJsonOnce({ ok: true, json: { id: 10, boardId: 1, title: 't', content: 'c', contentFormat: 'MARKDOWN', metadata: {}, createdAt: 'c1', updatedAt: 'c2' } });
      await expect(upsertDraft({ ...createEmptyDraft({ boardId: 1 }), title: 't', content: 'c' })).resolves.toMatchObject({ id: '10' });
      const createInfo = getFetchCallInfo(lastCall());
      expect(createInfo?.method).toBe('POST');
      expect(createInfo?.url).toContain('/api/post-drafts');

      replyJsonOnce({ ok: true, json: { id: 11, boardId: 1, title: 't', content: 'c', contentFormat: 'MARKDOWN', metadata: {}, createdAt: 'c1', updatedAt: 'c2' } });
      await expect(upsertDraft({ ...createEmptyDraft({ boardId: 1 }), id: '11', title: 't', content: 'c' })).resolves.toMatchObject({ id: '11' });
      const updateInfo = getFetchCallInfo(lastCall());
      expect(updateInfo?.method).toBe('PUT');
      expect(updateInfo?.url).toContain('/api/post-drafts/11');

      replyJsonOnce({ ok: false, status: 400, json: { title: 'required', content: 'required' } });
      await upsertDraft({ ...createEmptyDraft({ boardId: 1 }), id: '11', title: '', content: '' }).catch((e: any) => {
        expect(e?.message).toBe('Validation failed');
        expect(e?.fieldErrors).toMatchObject({ title: 'required' });
      });

      replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
      await expect(deleteDraft('1')).rejects.toThrow('删除草稿失败');

      const post = draftToPostCreateDTO({
        ...createEmptyDraft({ boardId: 1 }),
        attachments: [{ id: 3, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' } as any],
      });
      expect(post).toMatchObject({ attachmentIds: [3] });
    } finally {
      vi.useRealTimers();
    }
  });

  it('moderationLlmService covers config get/upsert and test signal variants', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { adminGetLlmModerationConfig, adminUpsertLlmModerationConfig, adminTestLlmModeration } = await import('./moderationLlmService');

    replyJsonOnce({ ok: true, json: { textPromptCode: 'p' } });
    await expect(adminGetLlmModerationConfig()).resolves.toMatchObject({ textPromptCode: 'p' });

    replyJsonOnce({ ok: true, json: { textPromptCode: 'p2' } });
    await expect(adminUpsertLlmModerationConfig({ textPromptCode: 'p2' })).resolves.toMatchObject({ textPromptCode: 'p2' });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 'x' })).resolves.toMatchObject({ decision: 'APPROVE' });
    expect(getFetchCallInfo(lastCall())?.init?.signal).toBeUndefined();

    replyJsonOnce({ ok: true, json: { decision: 'HUMAN' } });
    await expect(adminTestLlmModeration({ text: 'x' }, { timeoutMs: 1 })).resolves.toMatchObject({ decision: 'HUMAN' });
    expect(Boolean(getFetchCallInfo(lastCall())?.init?.signal)).toBe(true);

    const outer = new AbortController();
    outer.abort();
    replyJsonOnce({ ok: true, json: { decision: 'REJECT' } });
    await expect(adminTestLlmModeration({ text: 'x' }, { signal: outer.signal })).resolves.toMatchObject({ decision: 'REJECT' });
    expect((getFetchCallInfo(lastCall())?.init?.signal as AbortSignal | undefined)?.aborted).toBe(true);
  });

  it('riskTagService covers list/create/update/delete mapping and payload shaping', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { listRiskTagsPage, createRiskTag, updateRiskTag, deleteRiskTag } = await import('./riskTagService');

    replyJsonOnce({
      ok: true,
      json: {
        content: [
          {
            id: 1,
            tenantId: null,
            type: 'RISK',
            name: 'Hello World',
            slug: 'hello-world',
            description: null,
            isSystem: true,
            isActive: false,
            threshold: null,
            createdAt: 't',
            usageCount: null,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        size: 25,
        number: 0,
      },
    });
    const page = await listRiskTagsPage({ keyword: '  k  ' });
    expect(page.content[0]).toMatchObject({ tenantId: undefined, system: true, active: false, threshold: undefined, usageCount: 0 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('keyword=k');

    replyJsonOnce({
      ok: true,
      json: {
        id: 2,
        tenantId: 1,
        type: 'RISK',
        name: 'Hello World',
        slug: 'hello-world',
        description: null,
        isSystem: false,
        isActive: true,
        threshold: 0.5,
        createdAt: 't',
        usageCount: 1,
      },
    });
    await expect(createRiskTag({ name: 'Hello World', slug: '   ' })).resolves.toMatchObject({ slug: 'hello-world' });
    const createBody = JSON.parse(String(getFetchCallInfo(lastCall())?.body ?? '{}'));
    expect(createBody).toMatchObject({ tenantId: 1, name: 'Hello World', slug: 'hello-world', description: null, isActive: true, isSystem: false });

    replyJsonOnce({
      ok: true,
      json: {
        id: 3,
        tenantId: 1,
        type: 'RISK',
        name: 'n',
        slug: 's',
        description: null,
        isSystem: false,
        isActive: true,
        threshold: null,
        createdAt: 't',
        usageCount: 0,
      },
    });
    await expect(updateRiskTag(3, { name: 'n', description: null, active: true })).resolves.toMatchObject({ id: 3 });
    const updateBody = JSON.parse(String(getFetchCallInfo(lastCall())?.body ?? '{}'));
    expect(updateBody).toMatchObject({ name: 'n', description: null, isActive: true });
    expect('slug' in updateBody).toBe(false);

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(deleteRiskTag(1)).rejects.toThrow('删除风险标签失败');
  });

  it('chatContextGovernanceService covers get/update/list/detail and url params', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { adminGetChatContextConfig, adminUpdateChatContextConfig, adminListChatContextLogs, adminGetChatContextLog } = await import(
      './chatContextGovernanceService',
    );

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminGetChatContextConfig()).resolves.toMatchObject({ enabled: true });

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateChatContextConfig({ enabled: false })).resolves.toMatchObject({ enabled: false });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } });
    await expect(adminListChatContextLogs({ from: 'a', to: 'b' })).resolves.toMatchObject({ totalElements: 0 });
    const listInfo = getFetchCallInfo(lastCall());
    expect(listInfo?.url).toContain('/api/admin/ai/chat-context/logs?');
    expect(listInfo?.url).toContain('page=0');
    expect(listInfo?.url).toContain('size=20');
    expect(listInfo?.url).toContain('from=a');
    expect(listInfo?.url).toContain('to=b');

    replyJsonOnce({ ok: true, json: { id: 1 } });
    await expect(adminGetChatContextLog(1)).resolves.toMatchObject({ id: 1 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/ai/chat-context/logs/1');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetChatContextConfig()).rejects.toThrow('bad');
  });

  it('chatContextGovernanceService covers api base prefix, empty from/to filtering, and json parse fallback', async () => {
    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: 'http://x' };
    vi.resetModules();

    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();
    const { adminGetChatContextConfig, adminListChatContextLogs } = await import('./chatContextGovernanceService');

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminGetChatContextConfig()).resolves.toMatchObject({ enabled: true });
    expect(String(lastCall()?.[0])).toContain('/api/admin/ai/chat-context/config');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } });
    await expect(adminListChatContextLogs({ from: '', to: '' })).resolves.toMatchObject({ totalElements: 0 });
    const url = String(lastCall()?.[0]);
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).not.toContain('from=');
    expect(url).not.toContain('to=');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetChatContextConfig()).rejects.toThrow('获取对话上下文治理配置失败');

    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: '' };
    vi.resetModules();
  });
});
