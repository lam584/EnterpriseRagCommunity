import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getSamplesIndexStatus, triggerReindexSamples } from './moderationService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('moderationService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('triggerReindexSamples posts query params and csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { success: true, processedCount: 3 } });

    const res = await triggerReindexSamples({ onlyEnabled: true, batchSize: 10, fromId: 5 });

    expect(res.success).toBe(true);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    const url = fetchMock.mock.calls[0]?.[0] as string;
    expect(url).toContain('/api/admin/moderation/embed/reindex?');
    expect(url).toContain('onlyEnabled=true');
    expect(url).toContain('batchSize=10');
    expect(url).toContain('fromId=5');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
    });
  });

  it('triggerReindexSamples throws backend message when not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(triggerReindexSamples()).rejects.toThrow('bad');
  });

  it('triggerReindexSamples throws fallback when backend message missing and json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(triggerReindexSamples()).rejects.toThrow('Reindex failed');
  });

  it('getSamplesIndexStatus returns json on success', async () => {
    mockFetchResponseOnce({ ok: true, json: { indexName: 'x', exists: true, embeddingDimsConfigured: 1, available: true } });

    const res = await getSamplesIndexStatus();

    expect(res.indexName).toBe('x');
    expect(res.available).toBe(true);
  });

  it('getSamplesIndexStatus throws default message when not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(getSamplesIndexStatus()).rejects.toThrow('Failed to load index status');
  });
});
