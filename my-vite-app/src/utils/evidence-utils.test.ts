import { describe, expect, it } from 'vitest';
import { shouldSkipStepEvidenceForChunkedReview } from './evidence-utils';

describe('shouldSkipStepEvidenceForChunkedReview', () => {
  it('skips explicit chunk review steps when chunk set exists', () => {
    expect(
      shouldSkipStepEvidenceForChunkedReview({
        stage: 'LLM',
        hasChunkSet: true,
        chunkIndex: null,
        details: {
          request: {
            text: '[CHUNK_REVIEW] evidence',
          },
        },
      }),
    ).toBe(true);
  });

  it('skips chunked final review evidence because it duplicates chunk evidence', () => {
    expect(
      shouldSkipStepEvidenceForChunkedReview({
        stage: 'LLM',
        hasChunkSet: true,
        chunkIndex: null,
        details: {
          scope: 'finalReview',
          chunked: true,
          chunkSetId: 2,
          chunkedFinal: 'REJECT',
          chunkProgressFinal: { total: 11 },
          evidence: ['foo'],
        },
      }),
    ).toBe(true);
  });

  it('keeps normal llm step evidence when not chunk-derived', () => {
    expect(
      shouldSkipStepEvidenceForChunkedReview({
        stage: 'LLM',
        hasChunkSet: true,
        chunkIndex: null,
        details: {
          scope: 'finalReview',
          chunked: false,
          evidence: ['foo'],
        },
      }),
    ).toBe(false);
  });
});