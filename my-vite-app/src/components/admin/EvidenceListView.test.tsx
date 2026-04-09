import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

const chunkServiceMocks = vi.hoisted(() => ({
  adminGetModerationChunkLogContent: vi.fn(),
}));

const tokenizerMocks = vi.hoisted(() => ({
  tokenizeText: vi.fn(),
}));

vi.mock('../../services/moderationChunkReviewLogsService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/moderationChunkReviewLogsService')>();
  return {
    ...actual,
    adminGetModerationChunkLogContent: chunkServiceMocks.adminGetModerationChunkLogContent,
  };
});

vi.mock('../../services/opensearchTokenService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/opensearchTokenService')>();
  return {
    ...actual,
    tokenizeText: tokenizerMocks.tokenizeText,
  };
});

vi.mock('../common/DetailDialog', () => ({
  default: () => null,
}));

vi.mock('../ui/ImageLightbox', () => ({
  default: () => null,
}));

import EvidenceListView from './EvidenceListView';

afterEach(() => {
  cleanup();
});

describe('EvidenceListView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('expands context with one sentence on each side and truncates additions by tokenizer', async () => {
    chunkServiceMocks.adminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: '前一句非常非常非常非常非常非常非常非常长的上下文。RNG: ALT SEL - Show or hide the range select altitude “banana.”蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\n\n[[IMAGE_15]]后一句也是非常非常非常非常非常非常非常非常长的上下文。',
      source: null,
      images: [],
    });
    tokenizerMocks.tokenizeText
      .mockResolvedValueOnce({ result: { tokens: Array.from({ length: 26 }, () => '前') } })
      .mockResolvedValueOnce({ result: { tokens: Array.from({ length: 26 }, () => '后') } });

    render(
      <EvidenceListView
        stepEvidenceGroups={[]}
        chunkEvidenceByChunkIndex={{
          '4': [JSON.stringify({
            text: '蟑螂药、老鼠药、蒙汗药、迷情药+q231456154',
            before_context: 'RNG: ALT SEL - Show or hide the range select altitude “banana.”',
            after_context: '[[IMAGE_15]]',
          })],
        }}
        chunkIdByChunkIndex={{ '4': 5 }}
        chunkIndexFilter={null}
      />,
    );

    await waitFor(() => {
      expect(chunkServiceMocks.adminGetModerationChunkLogContent).toHaveBeenCalledWith(5, expect.any(AbortSignal));
    });

    await waitFor(() => {
      expect(tokenizerMocks.tokenizeText).toHaveBeenCalledTimes(2);
    });

    expect(screen.getByText('RNG: ALT SEL - Show or hide the range select altitude “banana.”')).toBeTruthy();
    expect(screen.getAllByText('蟑螂药、老鼠药、蒙汗药、迷情药+q231456154').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('[[IMAGE_15]]')).toBeTruthy();
    expect(screen.getByText((text) => text === `${'前'.repeat(25)}…`)).toBeTruthy();
    expect(screen.getByText((text) => text === `${'后'.repeat(25)}…`)).toBeTruthy();
  });

  it('falls back to original context when tokenizer fails', async () => {
    chunkServiceMocks.adminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: '前一句。before anchor命中after anchor后一句。',
      source: null,
      images: [],
    });
    tokenizerMocks.tokenizeText
      .mockRejectedValueOnce(new Error('boom'))
      .mockRejectedValueOnce(new Error('boom'));

    render(
      <EvidenceListView
        stepEvidenceGroups={[]}
        chunkEvidenceByChunkIndex={{
          '2': [JSON.stringify({ text: '命中', before_context: 'before anchor', after_context: 'after anchor' })],
        }}
        chunkIdByChunkIndex={{ '2': 8 }}
        chunkIndexFilter={null}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('前一句。')).toBeTruthy();
    });
    expect(screen.getByText('后一句。')).toBeTruthy();
  });

  it('does not render structured JSON fallback when evidence object has no valid text', async () => {
    render(
      <EvidenceListView
        stepEvidenceGroups={[]}
        chunkEvidenceByChunkIndex={{
          '9': [JSON.stringify({ before_context: 'Fig 8.3 - Lighting Panel', after_context: 'DIMMING PFD1/2 controls the screen brightness' })],
        }}
        chunkIdByChunkIndex={{ '9': 21 }}
        chunkIndexFilter={null}
      />,
    );

    expect(screen.queryByText('{"before_context":"Fig 8.3 - Lighting Panel","after_context":"DIMMING PFD1/2 controls the screen brightness"}')).toBeNull();
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
    expect(screen.getByText('Fig 8.3 - Lighting Panel')).toBeTruthy();
    expect(screen.getByText('DIMMING PFD1/2 controls the screen brightness')).toBeTruthy();
  });

  it('filters broken field-fragment text from evidence and context highlight', async () => {
    render(
      <EvidenceListView
        stepEvidenceGroups={[
          {
            title: 'LLM#7 · id=6 · REJECT · 17336ms',
            order: 7,
            stage: 'LLM',
            evidence: [JSON.stringify({ before_context: '⬤ TERR - Displays relative terrain overlay on HSI.', after_context: '⬤ WX - Displays weather overlay on HSI.', text: '","after_context":"' })],
          },
        ]}
        chunkEvidenceByChunkIndex={{}}
        chunkIdByChunkIndex={{}}
        chunkIndexFilter={null}
      />,
    );

    expect(screen.queryByText('","after_context":"')).toBeNull();
    expect(screen.getByText('⬤ TERR - Displays relative terrain overlay on HSI.')).toBeTruthy();
    expect(screen.getByText('⬤ WX - Displays weather overlay on HSI.')).toBeTruthy();
  });

  it('renders image preview when imageId can be resolved to an image url', () => {
    render(
      <EvidenceListView
        stepEvidenceGroups={[
          {
            title: 'VISION#4 · id=11 · REJECT · 11411ms',
            order: 4,
            stage: 'VISION',
            evidence: [JSON.stringify({ image_id: 'img_1', text: '命中证据' })],
          },
        ]}
        chunkEvidenceByChunkIndex={{}}
        chunkIdByChunkIndex={{}}
        chunkIndexFilter={null}
        imageUrlByImageId={{ img_1: 'https://example.com/evidence.png' }}
      />,
    );

    const preview = screen.getByAltText('img_1') as HTMLImageElement;
    expect(preview).toBeTruthy();
    expect(preview.getAttribute('src')).toBe('https://example.com/evidence.png');
    expect(screen.queryByText('img_1')).toBeNull();
  });

  it('falls back to chunk preview images when imageId is not in the global map', async () => {
    chunkServiceMocks.adminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: 'x',
      source: null,
      images: [
        { index: 18, placeholder: '[[IMAGE_18]]', url: 'https://example.com/chunk-18.png' },
        { index: 19, placeholder: '[[IMAGE_19]]', url: 'https://example.com/chunk-19.png' },
        { index: 20, placeholder: '[[IMAGE_20]]', url: 'https://example.com/chunk-20.png' },
      ],
    });

    render(
      <EvidenceListView
        stepEvidenceGroups={[]}
        chunkEvidenceByChunkIndex={{
          '6': [JSON.stringify({ image_id: 'img_2', text: '命中证据' })],
        }}
        chunkIdByChunkIndex={{ '6': 18 }}
        chunkIndexFilter={null}
        imageUrlByImageId={{}}
      />,
    );

    await waitFor(() => {
      expect(chunkServiceMocks.adminGetModerationChunkLogContent).toHaveBeenCalledWith(18, expect.any(AbortSignal));
    });

    const preview = await screen.findByAltText('img_2');
    expect(preview.getAttribute('src')).toBe('https://example.com/chunk-19.png');
    expect(screen.queryByText('img_2')).toBeNull();
  });
});