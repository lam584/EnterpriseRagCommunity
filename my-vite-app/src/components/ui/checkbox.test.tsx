import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Checkbox } from './checkbox';

afterEach(() => {
  cleanup();
});

describe('Checkbox', () => {
  it('renders unchecked by default and toggles on click', () => {
    render(<Checkbox />);

    const el = screen.getByRole('checkbox') as HTMLButtonElement;
    expect(el.getAttribute('data-state') === 'unchecked' || el.getAttribute('aria-checked') === 'false').toBe(true);

    fireEvent.click(el);
    expect(el.getAttribute('data-state') === 'checked' || el.getAttribute('aria-checked') === 'true').toBe(true);
  });

  it('supports defaultChecked and disabled', () => {
    const { rerender } = render(<Checkbox defaultChecked />);
    let el = screen.getByRole('checkbox') as HTMLButtonElement;
    expect(el.getAttribute('data-state') === 'checked' || el.getAttribute('aria-checked') === 'true').toBe(true);

    rerender(<Checkbox disabled />);
    el = screen.getByRole('checkbox') as HTMLButtonElement;
    expect(el.disabled).toBe(true);
    const before = el.getAttribute('data-state') ?? '';
    fireEvent.click(el);
    expect(el.getAttribute('data-state') ?? '').toBe(before);
  });
});

