import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../../hooks/useModerationChunkContentPreview', () => ({
  useModerationChunkContentPreview: () => ({ data: null }),
}));

vi.mock('../../hooks/useTokenizerLimitedText', () => ({
  useTokenizerLimitedText: (text: string) => ({ text }),
}));

import EvidenceContextCell from './EvidenceContextCell';

describe('EvidenceContextCell', () => {
  it('renders main anchor text with wrapping styles', () => {
    render(
      <EvidenceContextCell
        beforeText="before"
        mainText="averylongtokenwithoutmanualbreaksaverylongtokenwithoutmanualbreaks"
        afterText="after"
      />,
    );

    const main = screen.getByText('averylongtokenwithoutmanualbreaksaverylongtokenwithoutmanualbreaks');
    expect(main.className).toContain('whitespace-pre-wrap');
    expect(main.className).toContain('break-words');
    expect(main.className).not.toContain('whitespace-nowrap');
  });
});