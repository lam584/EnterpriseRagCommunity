import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

const serviceMocks = vi.hoisted(() => {
  return {
    adminGetModerationChunkLogContent: vi.fn(),
  };
});

vi.mock('../../services/moderationChunkReviewLogsService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/moderationChunkReviewLogsService')>();
  return {
    ...actual,
    adminGetModerationChunkLogContent: serviceMocks.adminGetModerationChunkLogContent,
  };
});

vi.mock('../ui/ImageLightbox', () => {
  return {
    default: (props: { open: boolean; src: string | null; onClose: () => void }) => {
      if (!props.open || !props.src) return null;
      return (
        <div data-testid="mock-lightbox" data-src={props.src}>
          <button type="button" onClick={props.onClose}>
            close
          </button>
        </div>
      );
    },
  };
});

import ChunkEvidenceView from './ChunkEvidenceView';
import { adminGetModerationChunkLogContent } from '../../services/moderationChunkReviewLogsService';

afterEach(() => {
  cleanup();
});

describe('ChunkEvidenceView', () => {
  const mockAdminGetModerationChunkLogContent = vi.mocked(adminGetModerationChunkLogContent);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('filters thumbnails by referenced IMAGE_ placeholders', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: null,
      source: null,
      images: [
        { placeholder: '[[IMAGE_1]]', url: 'https://example.com/1.png', index: 1 },
        { placeholder: '[[IMAGE_2]]', url: 'https://example.com/2.png', index: 2 },
      ],
    });

    render(<ChunkEvidenceView chunkId={101} evidence={['some [[IMAGE_1]] text']} />);

    await waitFor(() => {
      expect(screen.getByRole('img', { name: '[[IMAGE_1]]' })).toBeTruthy();
    });
    expect(screen.queryByRole('img', { name: '[[IMAGE_2]]' })).toBeNull();
  });

  it('limits shown thumbnails using compact default and maxThumbnails override', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: null,
      source: null,
      images: Array.from({ length: 5 }).map((_, i) => ({
        placeholder: `[[IMAGE_${i + 1}]]`,
        url: `https://example.com/${i + 1}.png`,
        index: i + 1,
      })),
    });

    const evidence = Array.from({ length: 5 }).map((_, i) => `[[IMAGE_${i + 1}]]`);

    const { rerender } = render(<ChunkEvidenceView chunkId={102} evidence={evidence} compact={true} />);

    await waitFor(() => {
      expect(screen.getAllByRole('img').length).toBe(2);
    });

    rerender(<ChunkEvidenceView chunkId={102} evidence={evidence} compact={false} />);
    await waitFor(() => {
      expect(screen.getAllByRole('img').length).toBe(5);
    });

    rerender(<ChunkEvidenceView chunkId={102} evidence={evidence} compact={false} maxThumbnails={3} />);
    await waitFor(() => {
      expect(screen.getAllByRole('img').length).toBe(3);
    });
  });

  it('opens and closes lightbox when clicking a thumbnail', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: null,
      source: null,
      images: [{ placeholder: '[[IMAGE_1]]', url: 'https://example.com/1.png', index: 1 }],
    });

    render(<ChunkEvidenceView chunkId={103} evidence={['[[IMAGE_1]]']} />);

    const thumbButton = await screen.findByTitle('[[IMAGE_1]]');
    fireEvent.click(thumbButton);

    await waitFor(() => {
      const lb = screen.getByTestId('mock-lightbox');
      expect(lb.getAttribute('data-src')).toBe('https://example.com/1.png');
    });

    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    await waitFor(() => {
      expect(screen.queryByTestId('mock-lightbox')).toBeNull();
    });
  });

  it('renders span snippet from fetched preview (with source offsets)', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: 'abcdefghijklmnopqrstuvwxyz',
      source: { startOffset: 100, endOffset: 126 },
      images: [],
    });

    const evidence = [JSON.stringify({ start: 110, end: 115, text: 'fallback-text' })];
    render(<ChunkEvidenceView chunkId={104} evidence={evidence} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 110-115')).toBeTruthy();
    });
    expect(screen.getByText('klmno')).toBeTruthy();
    expect(screen.queryByText('fallback-text')).toBeNull();
  });

  it('renders dash when evidence has no spans or image placeholders', () => {
    render(<ChunkEvidenceView chunkId={null} evidence={['{"foo":1}', '', '   ']} />);
    expect(screen.getByText('—')).toBeTruthy();
    expect(serviceMocks.adminGetModerationChunkLogContent).toHaveBeenCalledTimes(0);
  });

  it('renders placeholder text when span is invalid but IMAGE_ placeholders exist', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: null,
      source: null,
      images: [{ placeholder: '[[IMAGE_1]]', url: 'https://example.com/1.png', index: 1 }],
    });

    const evidence = [JSON.stringify({ start: -1, end: 2, placeholder: '[[IMAGE_1]]' })];
    render(<ChunkEvidenceView chunkId={105} evidence={evidence} />);

    await waitFor(() => {
      expect(screen.getByText('[[IMAGE_1]]')).toBeTruthy();
    });
  });

  it('renders span snippet using absolute offsets when preview.source is missing', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: '0123456789',
      source: null,
      images: [],
    });

    const evidence = [JSON.stringify({ start: 3, end: 7 })];
    render(<ChunkEvidenceView chunkId={106} evidence={evidence} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 3-7')).toBeTruthy();
    });
    expect(screen.getByText('3456')).toBeTruthy();
  });

  it('falls back to evidence text when snippet is out of range', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: 'short',
      source: null,
      images: [],
    });

    const evidence = [JSON.stringify({ start: 99, end: 100, text: 'fallback-text' })];
    render(<ChunkEvidenceView chunkId={107} evidence={evidence} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 99-100')).toBeTruthy();
    });
    expect(screen.getByText('fallback-text')).toBeTruthy();
  });

  it('clips long snippets to the max length', async () => {
    const long = Array.from({ length: 520 }).map((_, i) => String(i % 10)).join('');
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: long,
      source: null,
      images: [],
    });

    const evidence = [JSON.stringify({ start: 0, end: 500 })];
    render(<ChunkEvidenceView chunkId={108} evidence={evidence} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 0-500')).toBeTruthy();
    });
    const clipped = screen.getByText((t) => typeof t === 'string' && t.endsWith('…'));
    expect(clipped).toBeTruthy();
  });

  it('dedupes inflight requests across concurrent mounts and reuses cache on remount', async () => {
    let resolve!: (v: any) => void;
    const pending = new Promise((r) => {
      resolve = r as any;
    });
    mockAdminGetModerationChunkLogContent.mockReturnValueOnce(pending as never);

    const evidence = ['[[IMAGE_1]]'];
    const { unmount } = render(
      <div>
        <ChunkEvidenceView chunkId={109} evidence={evidence} />
        <ChunkEvidenceView chunkId={109} evidence={evidence} />
      </div>,
    );

    await waitFor(() => {
      expect(mockAdminGetModerationChunkLogContent).toHaveBeenCalledTimes(1);
    });

    resolve({
      text: null,
      source: null,
      images: [{ placeholder: '[[IMAGE_1]]', url: 'https://example.com/1.png', index: 1 }],
    });

    await waitFor(() => {
      expect(screen.getAllByRole('img').length).toBeGreaterThan(0);
    });

    unmount();

    render(<ChunkEvidenceView chunkId={109} evidence={evidence} />);

    await waitFor(() => {
      expect(screen.getByRole('img', { name: '[[IMAGE_1]]' })).toBeTruthy();
    });
    expect(mockAdminGetModerationChunkLogContent).toHaveBeenCalledTimes(1);
  });

  it('handles rejected fetch without crashing (error branch coverage)', async () => {
    mockAdminGetModerationChunkLogContent.mockRejectedValueOnce('boom');
    render(<ChunkEvidenceView chunkId={110} evidence={['[[IMAGE_1]]']} />);

    await waitFor(() => {
      expect(mockAdminGetModerationChunkLogContent).toHaveBeenCalledTimes(1);
    });
  });

  it('covers non-array evidence, null items, and object evidence parsing branches', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: '0123456789',
      source: null,
      images: [
        { placeholder: '[[IMAGE_9]]', url: 'https://example.com/9.png', index: '9' as any },
        { placeholder: 123 as any, url: null as any, index: null as any },
      ],
    });

    render(
      <ChunkEvidenceView
        chunkId={111}
        evidence={
          [
            null,
            { start: 1, end: 3, text: 't', image: '[[IMAGE_9]]', placeholder: '[[IMAGE_9]]', images: ['[[IMAGE_9]]', '  ', '[[IMAGE_9]]'] },
          ] as any
        }
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('span: 1-3')).toBeTruthy();
    });
    expect(screen.getByText('12')).toBeTruthy();
    expect(screen.getByRole('img', { name: '[[IMAGE_9]]' })).toBeTruthy();
  });

  it('falls back when preview.text is not a string and when span slice is whitespace', async () => {
    mockAdminGetModerationChunkLogContent
      .mockResolvedValueOnce({
        text: null,
        source: null,
        images: [],
      })
      .mockResolvedValueOnce({
        text: 'aa   bb',
        source: { startOffset: 0, endOffset: 1000 },
        images: [],
      });

    const { rerender } = render(<ChunkEvidenceView chunkId={112} evidence={[JSON.stringify({ start: 0, end: 2, text: 'fallback-text' })]} compact={false} />);
    await waitFor(() => {
      expect(screen.getByText('span: 0-2')).toBeTruthy();
    });
    expect(screen.getByText('fallback-text')).toBeTruthy();

    rerender(<ChunkEvidenceView chunkId={113} evidence={[JSON.stringify({ start: 2, end: 5, text: 'fallback-2' })]} compact={false} />);
    await waitFor(() => {
      expect(screen.getByText('span: 2-5')).toBeTruthy();
    });
    expect(screen.getByText('fallback-2')).toBeTruthy();
  });

  it('uses absolute offsets when source offsets do not cover the span', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: 'abcdefghijklmnopqrstuvwxyz',
      source: { startOffset: 100, endOffset: 126 },
      images: [],
    });

    render(<ChunkEvidenceView chunkId={114} evidence={[JSON.stringify({ start: 1, end: 4 })]} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 1-4')).toBeTruthy();
    });
    expect(screen.getByText('bcd')).toBeTruthy();
  });

  it('parses legacy chunk-prefixed evidence JSON and renders snippet', async () => {
    mockAdminGetModerationChunkLogContent.mockResolvedValueOnce({
      text: '0123456789',
      source: null,
      images: [],
    });

    render(<ChunkEvidenceView chunkId={116} evidence={['chunk-24: {"start":2,"end":6}']} compact={false} />);

    await waitFor(() => {
      expect(screen.getByText('span: 2-6')).toBeTruthy();
    });
    expect(screen.getByText('2345')).toBeTruthy();
  });

  it('renders dash in compact mode when no evidence is usable', () => {
    render(<ChunkEvidenceView chunkId={null} evidence={null as any} compact={true} />);
    expect(screen.getByText('—')).toBeTruthy();
  });

  it('covers Error instance message branch in fetch error handling', async () => {
    mockAdminGetModerationChunkLogContent.mockRejectedValueOnce(new Error('err-msg'));
    render(<ChunkEvidenceView chunkId={115} evidence={['[[IMAGE_1]]']} />);
    await waitFor(() => {
      expect(mockAdminGetModerationChunkLogContent).toHaveBeenCalledTimes(1);
    });
  });
});
