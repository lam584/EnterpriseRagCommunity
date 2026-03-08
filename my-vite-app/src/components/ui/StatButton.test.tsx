import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import StatButton from './StatButton';

afterEach(() => {
  cleanup();
});

describe('StatButton', () => {
  it('renders icon, label, and count', () => {
    render(
      <StatButton
        icon={<span data-testid="icon">i</span>}
        label="Views"
        count={3}
      />,
    );

    expect(screen.getByTestId('icon')).not.toBeNull();
    expect(screen.getByText('Views')).not.toBeNull();
    expect(screen.getByText('3')).not.toBeNull();
  });

  it('renders count when it is 0', () => {
    render(<StatButton label="Likes" count={0} />);
    expect(screen.getByText('0')).not.toBeNull();
  });

  it('does not render optional parts when not provided', () => {
    render(<StatButton />);
    const btn = screen.getByRole('button') as HTMLButtonElement;
    expect(btn.textContent ?? '').toBe('');
    expect(screen.queryByText('0')).toBeNull();
  });

  it('applies active/disabled styling branches', () => {
    const { rerender } = render(<StatButton label="t" active />);
    let btn = screen.getByRole('button') as HTMLButtonElement;
    expect(btn.className.includes('text-blue-600')).toBe(true);

    rerender(<StatButton label="t" active={false} />);
    btn = screen.getByRole('button') as HTMLButtonElement;
    expect(btn.className.includes('text-gray-600')).toBe(true);

    rerender(<StatButton label="t" disabled />);
    btn = screen.getByRole('button') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    expect(btn.className.includes('cursor-not-allowed')).toBe(true);
  });

  it('calls onClick when enabled', () => {
    const onClick = vi.fn();
    render(<StatButton label="t" onClick={onClick} />);
    const btn = screen.getByRole('button') as HTMLButtonElement;
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not call onClick when disabled', () => {
    const onClick = vi.fn();
    render(<StatButton label="t" disabled onClick={onClick} />);
    const btn = screen.getByRole('button') as HTMLButtonElement;
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(0);
  });
});

