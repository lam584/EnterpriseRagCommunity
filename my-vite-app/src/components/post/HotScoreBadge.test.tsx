import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import HotScoreBadge from './HotScoreBadge';

describe('HotScoreBadge', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders nothing when value is not a number', () => {
    const { container, rerender } = render(<HotScoreBadge value={null} />);
    expect(container.firstChild).toBeNull();

    rerender(<HotScoreBadge value={'12.3'} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders text variant by default and formats finite value to one decimal', () => {
    render(<HotScoreBadge value={12.34} className="extra" />);

    const node = screen.getByText('热度 12.3');
    expect(node.className.includes('text-xs')).toBe(true);
    expect(node.className.includes('extra')).toBe(true);
  });

  it('renders badge variant styles', () => {
    render(<HotScoreBadge value={9} variant="badge" className="extra" />);

    const node = screen.getByText('热度 9.0');
    expect(node.className.includes('rounded-md')).toBe(true);
    expect(node.className.includes('extra')).toBe(true);
  });

  it('renders non-finite numbers as string labels', () => {
    render(<HotScoreBadge value={Number.POSITIVE_INFINITY} />);
    expect(screen.getByText('热度 Infinity')).not.toBeNull();
  });
});
