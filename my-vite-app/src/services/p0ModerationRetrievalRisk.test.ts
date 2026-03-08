import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

vi.mock('./tagService', () => {
  return {
    slugify: (name: string) => `slug-${name}`,
  };
});

describe('p0ModerationRetrievalRisk', () => {
  beforeEach(() => {
    resetServiceTest();
    localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.useRealTimers();
  });

  it('riskTagService createRiskTag uses slugify when slug missing', async () => {
    const { createRiskTag } = await import('./riskTagService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: {
        id: 1,
        type: 'RISK',
        name: 'N',
        slug: 'slug-N',
        description: null,
        isSystem: false,
        isActive: true,
        threshold: null,
        createdAt: 't',
        usageCount: null,
      },
    });

    const res = await createRiskTag({ name: 'N' });
    expect(res.slug).toBe('slug-N');
    expect(String(lastCall()?.[0] || '')).toContain('/api/admin/risk-tags');
  });

  it('moderationRulesService falls back to local storage on network error', async () => {
    const { adminListModerationRules } = await import('./moderationRulesService');
    const { fetchMock } = installFetchMock();
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([
        { id: 1, name: 'A', pattern: 'a', type: 'TEXT', severity: 'HIGH', enabled: true, createdAt: '2020-01-01T00:00:00.000Z' },
        { id: 2, name: 'B', pattern: 'bbb', type: 'TEXT', severity: 'LOW', enabled: true, createdAt: '2020-01-02T00:00:00.000Z' },
      ]),
    );

    const res = await adminListModerationRules({ q: 'bbb', enabled: true });
    expect(res.map((x) => x.id)).toEqual([2]);
  });

  it('moderationRulesService create falls back to local storage on network error and persists', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2020-01-03T00:00:00.000Z'));

    const { adminCreateModerationRule } = await import('./moderationRulesService');
    const { fetchMock } = installFetchMock();
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    localStorage.setItem(
      'admin.moderation.rules.v1',
      JSON.stringify([{ id: 5, name: 'old', pattern: 'x', type: 'TEXT', severity: 'LOW', enabled: true, createdAt: '2020-01-01T00:00:00.000Z' }]),
    );

    const created = await adminCreateModerationRule({ name: 'n', pattern: 'p', type: 'TEXT', severity: 'HIGH', enabled: true } as any);
    expect(created.id).toBe(6);

    const persisted = JSON.parse(localStorage.getItem('admin.moderation.rules.v1') || '[]');
    expect(persisted[0].id).toBe(6);
    expect(persisted[0].createdAt).toBe('2020-01-03T00:00:00.000Z');
  });

  it('covers moderation* and retrieval* services happy paths', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();

    const okJson = () => replyJsonOnce({ ok: true, json: {} });
    const okArr = () => replyJsonOnce({ ok: true, json: [] });
    const okPage = () => replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 0, number: 0 } });

    okJson();
    okJson();
    const { getModerationChunkReviewConfig, updateModerationChunkReviewConfig } = await import('./moderationChunkReviewConfigService');
    await getModerationChunkReviewConfig();
    await updateModerationChunkReviewConfig({ enabled: true } as any);

    okJson();
    okJson();
    const { getFallbackConfig, updateFallbackConfig } = await import('./moderationFallbackService');
    await getFallbackConfig();
    await updateFallbackConfig({ enabled: true } as any);

    okJson();
    okJson();
    const { adminGetLlmModerationConfig, adminUpsertLlmModerationConfig } = await import('./moderationLlmService');
    await adminGetLlmModerationConfig();
    await adminUpsertLlmModerationConfig({ enabled: true } as any);

    okPage();
    okJson();
    const { adminListModerationQueue, adminGetModerationQueueDetail } = await import('./moderationQueueService');
    await adminListModerationQueue({ page: 0, size: 10 } as any);
    await adminGetModerationQueueDetail(1);

    okPage();
    okJson();
    const { adminListPipelineHistory, adminGetPipelineByRunId } = await import('./moderationPipelineService');
    await adminListPipelineHistory({ page: 0, size: 10 } as any);
    await adminGetPipelineByRunId(1);

    const { noopCsrfPing } = await import('./moderationPipelineService');
    await noopCsrfPing();

    okJson();
    const { getSamplesIndexStatus } = await import('./moderationService');
    await getSamplesIndexStatus();

    okArr();
    okJson();
    okJson();
    okJson();
    okJson();
    okJson();
    const { listSamples, createSample, updateSample, deleteSample, syncSample, syncSamplesIncremental } = await import('./moderationEmbedSamplesService');
    await listSamples({ page: 1, pageSize: 10 } as any);
    await createSample({ content: 'x', embeddingText: 'x', enabled: true } as any);
    await updateSample(1, { enabled: true } as any);
    await deleteSample(1);
    await syncSample(1);
    await syncSamplesIncremental({ onlyEnabled: true } as any);

    okArr();
    okJson();
    okJson();
    const { adminListModerationChunkLogs, adminGetModerationChunkLogDetail, adminGetModerationChunkLogContent } = await import(
      './moderationChunkReviewLogsService',
    );
    await adminListModerationChunkLogs({ page: 0, size: 10 } as any);
    await adminGetModerationChunkLogDetail(1);
    await adminGetModerationChunkLogContent(1);

    okJson();
    okJson();
    const { adminGetModerationPolicyConfig, adminUpsertModerationPolicyConfig } = await import('./moderationPolicyService');
    await adminGetModerationPolicyConfig('POST' as any);
    await adminUpsertModerationPolicyConfig({ contentType: 'POST' } as any);

    okPage();
    okJson();
    const { adminListModerationReviewTraceTasks, adminGetModerationReviewTraceTaskDetail } = await import('./moderationReviewTraceService');
    await adminListModerationReviewTraceTasks({ page: 0, size: 10 } as any);
    await adminGetModerationReviewTraceTaskDetail(1);

    okJson();
    okJson();
    okJson();
    okJson();
    okJson();
    const { adminGetCitationConfig, adminUpdateCitationConfig, adminTestCitation } = await import('./retrievalCitationService');
    await adminGetCitationConfig();
    await adminUpdateCitationConfig({ enabled: true } as any);
    await adminTestCitation({ queryText: 'q', citationEnabled: true } as any);

    okJson();
    okJson();
    okJson();
    okPage();
    okJson();
    const { adminGetContextClipConfig, adminUpdateContextClipConfig, adminTestContextClip, adminListContextWindows, adminGetContextWindow } =
      await import('./retrievalContextService');
    await adminGetContextClipConfig();
    await adminUpdateContextClipConfig({ enabled: true } as any);
    await adminTestContextClip({ queryText: 'q', debug: true } as any);
    await adminListContextWindows({ page: 0, size: 20 } as any);
    await adminGetContextWindow(1);

    okJson();
    okJson();
    okJson();
    okJson();
    okPage();
    okArr();
    const { adminGetHybridRetrievalConfig, adminUpdateHybridRetrievalConfig, adminTestHybridRetrieval, adminTestHybridRerank, adminListHybridRetrievalEvents, adminListHybridRetrievalHits } =
      await import('./retrievalHybridService');
    await adminGetHybridRetrievalConfig();
    await adminUpdateHybridRetrievalConfig({ enabled: true } as any);
    await adminTestHybridRetrieval({ queryText: 'q', debug: true } as any);
    await adminTestHybridRerank({ queryText: 'q', documents: [{ docId: '1', text: 't' }] } as any);
    await adminListHybridRetrievalEvents({ page: 0, size: 20 } as any);
    await adminListHybridRetrievalHits(1);

    okPage();
    okJson();
    okJson();
    okJson();
    okJson();
    okJson();
    okJson();
    okJson();
    const { adminListVectorIndices, adminCreateVectorIndex, adminUpdateVectorIndex, adminDeleteVectorIndex, adminBuildPostRagIndex, adminRebuildPostRagIndex, adminSyncPostRagIndex, adminGetRagAutoSyncConfig, adminUpdateRagAutoSyncConfig, adminTestQueryPostRagIndex } =
      await import('./retrievalVectorIndexService');
    await adminListVectorIndices({ page: 0, size: 20 });
    await adminCreateVectorIndex({ name: 'n', embeddingProviderId: 'p', embeddingModel: 'm', dims: 1 } as any);
    await adminUpdateVectorIndex({ id: 1, name: 'n' } as any);
    await adminDeleteVectorIndex(1);
    await adminBuildPostRagIndex({ indexId: 1, boardIds: [1] } as any);
    await adminRebuildPostRagIndex({ indexId: 1, boardIds: [1] } as any);
    await adminSyncPostRagIndex({ indexId: 1, onlyEnabled: true } as any);
    await adminGetRagAutoSyncConfig();
    await adminUpdateRagAutoSyncConfig({ enabled: true } as any);
    await adminTestQueryPostRagIndex({ id: 1, payload: { queryText: 'q' } as any });

    expect(String(lastCall()?.[0] || '')).not.toEqual('');
  });
});
