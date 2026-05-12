import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '/testUtils/serviceTestHarness';

vi.mock('/utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p2ZeroCoverageServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('covers requested services happy paths', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { email: 'e', roles: ['ROLE_ADMIN'], permissions: ['P'] } });
    const { fetchAccessContext } = await import('/services/permissions/accessContextService');
    await expect(fetchAccessContext()).resolves.toEqual({ email: 'e', roles: ['ROLE_ADMIN'], permissions: ['P'] });

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } });
    const { adminListAccessLogs } = await import('/services/admin/platform/accessLogService');
    await expect(adminListAccessLogs({ page: 2, pageSize: 10, keyword: 'k' })).resolves.toMatchObject({ totalElements: 0 });
    expect(String(lastCall()?.[0] || '')).toContain('/api/admin/access-logs');

    replyJsonOnce({ ok: true, json: [] });
    const { fetchAdministrators } = await import('/services/admin/core/adminService');
    await expect(fetchAdministrators()).resolves.toEqual([]);
    expect(String(lastCall()?.[0] || '')).toContain('/administrators');

    replyJsonOnce({ ok: true, json: { defaultRegisterRoleId: 1, registrationEnabled: true } });
    const { getRegistrationSettings } = await import('/services/admin/core/adminSettingsService');
    await expect(getRegistrationSettings()).resolves.toMatchObject({ defaultRegisterRoleId: 1 });

    replyJsonOnce({ ok: true, json: { enabled: true } });
    const { adminGetChatContextConfig } = await import('/services/ai/chat/chatContextGovernanceService');
    await expect(adminGetChatContextConfig()).resolves.toMatchObject({ enabled: true });

    replyJsonOnce({ ok: true, json: { enabled: true } });
    const { adminGetContentFormatsConfig } = await import('/services/admin/content/contentFormatsAdminService');
    await expect(adminGetContentFormatsConfig()).resolves.toMatchObject({ enabled: true });

    replyJsonOnce({ ok: true, json: { persisted: true } });
    const { adminGetContentSafetyCircuitBreakerStatus } = await import('/services/admin/platform/contentSafetyCircuitBreakerAdminService');
    await expect(adminGetContentSafetyCircuitBreakerStatus()).resolves.toMatchObject({ persisted: true });

    replyJsonOnce({ ok: true, json: { dependency: 'mysql', failureThreshold: 1, cooldownSeconds: 1 } });
    const { adminGetDependencyCircuitBreakerConfig } = await import('/services/admin/platform/dependencyCircuitBreakerAdminService');
    await expect(adminGetDependencyCircuitBreakerConfig(' mysql ')).resolves.toMatchObject({ dependency: 'mysql' });
    expect(decodeURIComponent(String(lastCall()?.[0] || ''))).toContain('dependency-circuit-breakers/mysql');

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
            metadata: { tags: ['a'], attachments: [{ id: 1, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' }] },
            createdAt: 'c1',
            updatedAt: 'c2',
          },
        ],
      },
    });
    const { listDrafts } = await import('/services/content/draftService');
    const drafts = await listDrafts();
    expect(drafts[0]?.id).toBe('9');
    expect(drafts[0]?.tags).toEqual(['a']);

    replyJsonOnce({ ok: true, json: { enabled: true } });
    const { getEmailAdminSettings } = await import('/services/admin/platform/emailAdminService');
    await expect(getEmailAdminSettings()).resolves.toMatchObject({ enabled: true });

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', maxContentChars: 1 } });
    const { adminGetPostLangLabelGenConfig } = await import('/services/admin/ai/langLabelAdminService');
    await expect(adminGetPostLangLabelGenConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({ ok: true, json: { maxConcurrent: 1, maxQueueSize: 2, keepCompleted: 3 } });
    const { adminGetLlmQueueConfig } = await import('/services/admin/ai/llmQueueAdminService');
    await expect(adminGetLlmQueueConfig()).resolves.toMatchObject({ maxConcurrent: 1 });

    replyJsonOnce({ ok: true, json: { scenarios: [], policies: [], targets: [] } });
    const { adminGetLlmRoutingConfig } = await import('/services/admin/ai/llmRoutingAdminService');
    await expect(adminGetLlmRoutingConfig()).resolves.toMatchObject({ scenarios: [] });

    replyJsonOnce({ ok: true, json: { checkedAtMs: 1, items: [] } });
    const { adminGetLlmRoutingDecisions, adminOpenLlmRoutingEventSource } = await import('/services/admin/ai/llmRoutingMonitorAdminService');
    await expect(adminGetLlmRoutingDecisions({ taskType: ' chat ', limit: 1 })).resolves.toMatchObject({ checkedAtMs: 1, items: [] });

    const prevEventSource = (window as any).EventSource;
    const eventSourceCtorSpy = vi.fn();
    function MockEventSource(this: any, url: string) {
      eventSourceCtorSpy(url);
      this.close = vi.fn();
    }
    (window as any).EventSource = MockEventSource as any;
    const es = adminOpenLlmRoutingEventSource({ taskType: 'chat' });
    expect(es).toBeTruthy();
    expect(eventSourceCtorSpy).toHaveBeenCalledTimes(1);
    (window as any).EventSource = prevEventSource;

    replyJsonOnce({ ok: true, json: { enabled: true, keepDays: 1, mode: 'DELETE' } });
    const { adminGetLogRetentionConfig } = await import('/services/admin/platform/logRetentionService');
    await expect(adminGetLogRetentionConfig()).resolves.toMatchObject({ keepDays: 1 });

    replyJsonOnce({ ok: true, json: { assistantChat: { providerId: 'p' } } });
    const { adminGetPortalChatConfig } = await import('/services/admin/ai/portalChatAdminService');
    await expect(adminGetPortalChatConfig()).resolves.toMatchObject({ assistantChat: { providerId: 'p' } });

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } });
    const { adminListPostFiles } = await import('/services/admin/content/postFilesAdminService');
    await expect(adminListPostFiles({ page: 1, pageSize: 10, keyword: 'k' })).resolves.toMatchObject({ totalElements: 0 });

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', maxContentChars: 1 } });
    const { adminGetPostSummaryConfig } = await import('/services/admin/ai/postSummaryAdminService');
    await expect(adminGetPostSummaryConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({ ok: true, json: { prompts: [], missingCodes: [] } });
    const { adminBatchGetPrompts } = await import('/services/admin/ai/promptsAdminService');
    await expect(adminBatchGetPrompts(['a'])).resolves.toMatchObject({ prompts: [] });

    replyJsonOnce({
      ok: true,
      json: { enabled: true, promptCode: 'p', maxContentChars: 1, defaultCount: 1, maxCount: 1, historyEnabled: false },
    });
    const { adminGetPostTagGenConfig } = await import('/services/admin/ai/tagGenAdminService');
    await expect(adminGetPostTagGenConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({
      ok: true,
      json: { enabled: true, promptCode: 'p', maxContentChars: 1, defaultCount: 1, maxCount: 1, historyEnabled: false },
    });
    const { adminGetPostTitleGenConfig } = await import('/services/admin/ai/titleGenAdminService');
    await expect(adminGetPostTitleGenConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({
      ok: true,
      json: {
        enabled: true,
        promptCode: 'p',
        maxContentChars: 1,
        historyEnabled: false,
        allowedTargetLanguages: ['zh'],
      },
    });
    const { adminGetTranslateConfig } = await import('/services/admin/ai/translateAdminService');
    await expect(adminGetTranslateConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 10, number: 0 } });
    const { userAccessService } = await import('/services/users/userAccessService');
    await expect(userAccessService.queryUsers({ page: 1, size: 10 } as any)).resolves.toMatchObject({ content: [] });

    const call = lastCall();
    const info = getFetchCallInfo(call);
    expect(info?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('covers requested services error paths', async () => {
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, json: { message: 'x' } });
    const { fetchAccessContext } = await import('/services/permissions/accessContextService');
    await expect(fetchAccessContext()).rejects.toThrow('Failed to fetch access context');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminListAccessLogs } = await import('/services/admin/platform/accessLogService');
    await expect(adminListAccessLogs({})).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: {} });
    const { searchAdministrators } = await import('/services/admin/core/adminService');
    await expect(searchAdministrators({ account: 'a' })).rejects.toThrow('搜索管理员失败');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { getRegistrationSettings } = await import('/services/admin/core/adminSettingsService');
    await expect(getRegistrationSettings()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetChatContextConfig } = await import('/services/ai/chat/chatContextGovernanceService');
    await expect(adminGetChatContextConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetContentFormatsConfig } = await import('/services/admin/content/contentFormatsAdminService');
    await expect(adminGetContentFormatsConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetContentSafetyCircuitBreakerStatus } = await import('/services/admin/platform/contentSafetyCircuitBreakerAdminService');
    await expect(adminGetContentSafetyCircuitBreakerStatus()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetDependencyCircuitBreakerConfig } = await import('/services/admin/platform/dependencyCircuitBreakerAdminService');
    await expect(adminGetDependencyCircuitBreakerConfig('mysql')).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { listDrafts } = await import('/services/content/draftService');
    await expect(listDrafts()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { getEmailAdminSettings } = await import('/services/admin/platform/emailAdminService');
    await expect(getEmailAdminSettings()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetPostLangLabelGenConfig } = await import('/services/admin/ai/langLabelAdminService');
    await expect(adminGetPostLangLabelGenConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminUpdateLlmQueueConfig } = await import('/services/admin/ai/llmQueueAdminService');
    await expect(adminUpdateLlmQueueConfig({ maxConcurrent: 1 })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 409, headers: { 'content-type': 'application/json' }, json: { message: 'bad' } });
    const { adminUpdateLlmRoutingConfig } = await import('/services/admin/ai/llmRoutingAdminService');
    await expect(adminUpdateLlmRoutingConfig({})).rejects.toThrow('刷新');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetLlmRoutingDecisions } = await import('/services/admin/ai/llmRoutingMonitorAdminService');
    await expect(adminGetLlmRoutingDecisions({})).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminUpdateLogRetentionConfig } = await import('/services/admin/platform/logRetentionService');
    await expect(
      adminUpdateLogRetentionConfig({
        enabled: true,
        keepDays: 1,
        mode: 'DELETE',
        maxPerRun: 5000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      })
    ).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { error: 'bad' } });
    const { adminUpsertPortalChatConfig } = await import('/services/admin/ai/portalChatAdminService');
    await expect(adminUpsertPortalChatConfig({})).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminGetPostFileDetail } = await import('/services/admin/content/postFilesAdminService');
    await expect(adminGetPostFileDetail(1)).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminRegeneratePostSummary } = await import('/services/admin/ai/postSummaryAdminService');
    await expect(adminRegeneratePostSummary(1)).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminUpdatePromptContent } = await import('/services/admin/ai/promptsAdminService');
    await expect(adminUpdatePromptContent('p', {})).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminListPostTagGenHistory } = await import('/services/admin/ai/tagGenAdminService');
    await expect(adminListPostTagGenHistory({})).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminUpsertPostTitleGenConfig } = await import('/services/admin/ai/titleGenAdminService');
    await expect(
      adminUpsertPostTitleGenConfig({ enabled: true, promptCode: 'p', maxContentChars: 1, defaultCount: 1, maxCount: 1, historyEnabled: false }),
    ).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, json: { message: 'bad' } });
    const { adminListTranslateHistory } = await import('/services/admin/ai/translateAdminService');
    await expect(adminListTranslateHistory({})).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'bad' } });
    const { userAccessService } = await import('/services/users/userAccessService');
    await expect(userAccessService.deleteUser(1)).rejects.toThrow('bad');
  });
});
