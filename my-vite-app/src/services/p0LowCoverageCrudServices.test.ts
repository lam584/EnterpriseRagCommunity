import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p0LowCoverageCrudServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('postComposeAiSnapshotService covers create/getPending/apply/revert success paths', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const {
      createPostComposeAiSnapshot,
      getPendingPostComposeAiSnapshot,
      applyPostComposeAiSnapshot,
      revertPostComposeAiSnapshot,
    } = await import('./postComposeAiSnapshotService');

    replyJsonOnce({
      ok: true,
      json: {
        id: 1,
        targetType: 'DRAFT',
        beforeTitle: 't',
        beforeContent: 'c',
        beforeBoardId: 1,
        status: 'PENDING',
      },
    });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).resolves.toMatchObject({ id: 1 });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { id: 2, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'PENDING' } });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 9 })).resolves.toMatchObject({ id: 2 });
    const pendingInfo = getFetchCallInfo(lastCall());
    expect(pendingInfo?.url).toContain('/api/post-compose/ai-snapshots/pending?');
    expect(pendingInfo?.url).toContain('targetType=DRAFT');
    expect(pendingInfo?.url).toContain('draftId=9');

    replyJsonOnce({ ok: true, json: { id: 3, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'APPLIED' } });
    await expect(applyPostComposeAiSnapshot(3, 'after')).resolves.toMatchObject({ id: 3 });
    const applyInfo = getFetchCallInfo(lastCall());
    expect(applyInfo?.url).toContain('/api/post-compose/ai-snapshots/3/apply');
    expect(applyInfo?.method).toBe('POST');
    expect(applyInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(JSON.parse(String(applyInfo?.body ?? '{}'))).toEqual({ afterContent: 'after' });

    replyJsonOnce({ ok: true, json: { id: 4, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'REVERTED' } });
    await expect(revertPostComposeAiSnapshot(4)).resolves.toMatchObject({ id: 4 });
    const revertInfo = getFetchCallInfo(lastCall());
    expect(revertInfo?.url).toContain('/api/post-compose/ai-snapshots/4/revert');
    expect(revertInfo?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('postComposeAiSnapshotService covers error paths and 404 pending returns null', async () => {
    const { replyOnce, replyJsonOnce } = installFetchMock();
    const { createPostComposeAiSnapshot, getPendingPostComposeAiSnapshot, applyPostComposeAiSnapshot, revertPostComposeAiSnapshot } =
      await import('./postComposeAiSnapshotService');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(createPostComposeAiSnapshot({ targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 404, json: { message: 'not-found' } });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'POST', postId: 1 })).resolves.toBeNull();

    replyJsonOnce({ ok: false, status: 500, json: {} });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'POST', postId: 1 })).rejects.toThrow('加载待处理快照失败');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'x' } });
    await expect(applyPostComposeAiSnapshot(1, 'a')).rejects.toThrow('x');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(revertPostComposeAiSnapshot(1)).rejects.toThrow('回滚快照失败');
  });

  it('notificationService covers list/unread/mark/batch/delete requests', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { fetchNotifications, fetchUnreadCount, markNotificationRead, markNotificationsRead, deleteNotification } = await import('./notificationService');

    replyJsonOnce({
      ok: true,
      json: { content: [{ id: 1 }], totalElements: 1, totalPages: 1, size: 20, number: 0 },
    });
    await expect(fetchNotifications({ type: 't', unreadOnly: true, page: 2, pageSize: 10 })).resolves.toMatchObject({ totalElements: 1 });
    const listInfo = getFetchCallInfo(lastCall());
    expect(listInfo?.url).toContain('/api/notifications?');
    expect(listInfo?.url).toContain('type=t');
    expect(listInfo?.url).toContain('unreadOnly=true');
    expect(listInfo?.url).toContain('page=2');
    expect(listInfo?.url).toContain('pageSize=10');
    expect(listInfo?.init).toMatchObject({ credentials: 'include' });

    replyJsonOnce({ ok: true, json: { count: '5' } });
    await expect(fetchUnreadCount()).resolves.toBe(5);
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/notifications/unread-count');

    replyJsonOnce({ ok: true, json: { id: 2 } });
    await expect(markNotificationRead(2)).resolves.toMatchObject({ id: 2 });
    const markInfo = getFetchCallInfo(lastCall());
    expect(markInfo?.method).toBe('PATCH');
    expect(markInfo?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { updated: 2 } });
    await expect(markNotificationsRead([1, 2])).resolves.toBe(2);
    const batchInfo = getFetchCallInfo(lastCall());
    expect(batchInfo?.method).toBe('PATCH');
    expect(batchInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(JSON.parse(String(batchInfo?.body ?? '{}'))).toEqual({ ids: [1, 2] });

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(deleteNotification(9)).resolves.toBeUndefined();
    const delInfo = getFetchCallInfo(lastCall());
    expect(delInfo?.method).toBe('DELETE');
    expect(delInfo?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('notificationService covers error message fallback when json parsing fails', async () => {
    const { replyOnce } = installFetchMock();
    const { fetchNotifications, fetchUnreadCount, markNotificationRead, markNotificationsRead, deleteNotification } = await import('./notificationService');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(fetchNotifications()).rejects.toThrow('获取通知失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(fetchUnreadCount()).rejects.toThrow('获取未读数失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(markNotificationRead(1)).rejects.toThrow('标记已读失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(markNotificationsRead([1])).rejects.toThrow('批量标记已读失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(deleteNotification(1)).rejects.toThrow('删除通知失败');
  });

  it('reportService covers success and error branches', async () => {
    const { replyJsonOnce, lastCall, replyOnce } = installFetchMock();
    const { reportPost, reportComment, reportProfile } = await import('./reportService');

    replyJsonOnce({ ok: true, json: { reportId: 1, queueId: 2 } });
    await expect(reportPost(9, { reasonCode: 'R', reasonText: 't' })).resolves.toEqual({ reportId: 1, queueId: 2 });
    const postInfo = getFetchCallInfo(lastCall());
    expect(postInfo?.url).toContain('/api/posts/9/report');
    expect(postInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(JSON.parse(String(postInfo?.body ?? '{}'))).toEqual({ reasonCode: 'R', reasonText: 't' });

    replyJsonOnce({ ok: false, status: 401, json: { message: 'ignored' } });
    await expect(reportComment(1, { reasonCode: 'R' })).rejects.toThrow('请先登录后再举报');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(reportProfile(1, { reasonCode: 'R' })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(reportPost(1, { reasonCode: 'R' })).rejects.toThrow('举报失败');
  });

  it('accountPreferencesService and assistantPreferencesService cover success and error paths', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { getMyTranslatePreferences, updateMyTranslatePreferences } = await import('./accountPreferencesService');
    const { getMyAssistantPreferences, updateMyAssistantPreferences } = await import('./assistantPreferencesService');

    replyJsonOnce({ ok: true, json: { targetLanguage: 'en', autoTranslatePosts: true, autoTranslateComments: false } });
    await expect(getMyTranslatePreferences()).resolves.toMatchObject({ targetLanguage: 'en' });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/account/preferences');

    replyJsonOnce({ ok: true, json: { targetLanguage: 'en', autoTranslatePosts: false, autoTranslateComments: false } });
    await expect(updateMyTranslatePreferences({ autoTranslatePosts: false })).resolves.toMatchObject({ autoTranslatePosts: false });
    const accUpdateInfo = getFetchCallInfo(lastCall());
    expect(accUpdateInfo?.method).toBe('PUT');
    expect(accUpdateInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({
      ok: true,
      json: {
        defaultProviderId: null,
        defaultModel: null,
        defaultDeepThink: false,
        autoLoadLastSession: false,
        defaultUseRag: true,
        ragTopK: 3,
        stream: true,
      },
    });
    await expect(getMyAssistantPreferences()).resolves.toMatchObject({ ragTopK: 3 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/ai/assistant/preferences');

    replyJsonOnce({
      ok: true,
      json: {
        defaultProviderId: null,
        defaultModel: null,
        defaultDeepThink: true,
        autoLoadLastSession: true,
        defaultUseRag: false,
        ragTopK: 2,
        stream: false,
      },
    });
    await expect(updateMyAssistantPreferences({ defaultDeepThink: true })).resolves.toMatchObject({ defaultDeepThink: true });
    const asstUpdateInfo = getFetchCallInfo(lastCall());
    expect(asstUpdateInfo?.method).toBe('PUT');
    expect(asstUpdateInfo?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
  });

  it('accountPreferencesService and assistantPreferencesService throw backend message and fallback messages', async () => {
    const { replyOnce, replyJsonOnce } = installFetchMock();
    const { getMyTranslatePreferences, updateMyTranslatePreferences } = await import('./accountPreferencesService');
    const { getMyAssistantPreferences, updateMyAssistantPreferences } = await import('./assistantPreferencesService');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getMyTranslatePreferences()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(updateMyTranslatePreferences({})).rejects.toThrow('保存偏好失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getMyAssistantPreferences()).rejects.toThrow('获取助手偏好失败');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'x' } });
    await expect(updateMyAssistantPreferences({})).rejects.toThrow('x');
  });
});

