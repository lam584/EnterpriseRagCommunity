import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { Badge } from './badge';

afterEach(() => {
  cleanup();
});

describe('Badge', () => {
  it('renders children and merges className', () => {
    render(
      <Badge className="extra-class" data-testid="badge">
        hello
      </Badge>,
    );

    expect(screen.getByText('hello')).not.toBeNull();
    const el = screen.getByTestId('badge') as HTMLDivElement;
    expect(el.className.includes('extra-class')).toBe(true);
  });

  it('applies variant classes', () => {
    const { rerender } = render(
      <Badge data-testid="badge" variant="default">
        t
      </Badge>,
    );

    const el = screen.getByTestId('badge') as HTMLDivElement;
    expect(el.className.includes('bg-primary')).toBe(true);

    rerender(
      <Badge data-testid="badge" variant="secondary">
        t
      </Badge>,
    );
    expect(el.className.includes('bg-secondary')).toBe(true);

    rerender(
      <Badge data-testid="badge" variant="destructive">
        t
      </Badge>,
    );
    expect(el.className.includes('bg-destructive')).toBe(true);

    rerender(
      <Badge data-testid="badge" variant="outline">
        t
      </Badge>,
    );
    expect(el.className.includes('text-foreground')).toBe(true);
  });
});

