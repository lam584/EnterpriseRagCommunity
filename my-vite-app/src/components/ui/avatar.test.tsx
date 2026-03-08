import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { Avatar, AvatarFallback, AvatarImage } from './avatar';

describe('avatar ui components', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders avatar root with default and custom classes', () => {
    const { container } = render(
      <Avatar className="custom-root">
        <AvatarFallback>U</AvatarFallback>
      </Avatar>,
    );

    const root = container.firstElementChild as HTMLElement;
    expect(root.className.includes('rounded-full')).toBe(true);
    expect(root.className.includes('custom-root')).toBe(true);
    expect(screen.getByText('U')).not.toBeNull();
  });

  it('renders image and fallback with merged class names', () => {
    const { container } = render(
      <Avatar>
        <AvatarImage src="https://example.com/a.png" alt="avatar" className="img-x" />
        <AvatarFallback className="fallback-y">FB</AvatarFallback>
      </Avatar>,
    );

    const img = container.querySelector('img.img-x') as HTMLImageElement | null;
    if (img) {
      expect(img.className.includes('aspect-square')).toBe(true);
    }

    const fallback = screen.getByText('FB');
    expect(fallback.className.includes('bg-muted')).toBe(true);
    expect(fallback.className.includes('fallback-y')).toBe(true);
    expect(container.firstElementChild).not.toBeNull();
  });
});
