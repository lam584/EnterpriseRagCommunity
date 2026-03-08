import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import OtpCodeInput from './OtpCodeInput';

afterEach(() => {
  cleanup();
});

function getInputs() {
  return screen.getAllByRole('textbox') as HTMLInputElement[];
}

function renderControlled(opts?: { digits?: number; initialValue?: string; autoFocus?: boolean; disabled?: boolean; onComplete?: (v: string) => void }) {
  const digits = opts?.digits ?? 4;
  const initialValue = opts?.initialValue ?? '';
  const autoFocus = opts?.autoFocus ?? false;
  const disabled = opts?.disabled ?? false;
  const onComplete = opts?.onComplete;

  const onChangeSpy = vi.fn();
  const onCompleteSpy = onComplete ? vi.fn(onComplete) : vi.fn();

  function Wrapper() {
    const [value, setValue] = useState(initialValue);
    return (
      <OtpCodeInput
        digits={digits}
        value={value}
        onChange={(next) => {
          onChangeSpy(next);
          setValue(next);
        }}
        onComplete={onCompleteSpy}
        autoFocus={autoFocus}
        disabled={disabled}
      />
    );
  }

  render(<Wrapper />);
  return { onChangeSpy, onCompleteSpy };
}

describe('OtpCodeInput', () => {
  it('moves focus forward on single character input', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.change(inputs[0] as HTMLInputElement, { target: { value: '5' } });

    await waitFor(() => {
      expect(getInputs()[0]?.value).toBe('5');
    });
    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[1]);
    });
    expect(onChangeSpy).toHaveBeenCalledWith('5');
  });

  it('defaults digits when prop is invalid', () => {
    render(<OtpCodeInput digits={Number.NaN} value={''} onChange={() => {}} />);
    expect(getInputs().length).toBe(6);
  });

  it('merges container and input class names', () => {
    const { container } = render(
      <OtpCodeInput
        digits={4}
        value={''}
        onChange={() => {}}
        containerClassName="c1"
        inputClassName="i1"
        placeholder="p"
      />,
    );
    const root = container.querySelector('div');
    expect(root?.className.includes('c1')).toBe(true);
    expect(root?.querySelectorAll('input').length).toBe(4);
    const first = getInputs()[0];
    expect(first.className.includes('i1')).toBe(true);
  });

  it('fills multiple digits on paste and completes once', async () => {
    const { onChangeSpy, onCompleteSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.paste(inputs[0] as HTMLInputElement, {
      clipboardData: {
        getData: () => '1234',
      } as any,
    });

    await waitFor(() => {
      const next = getInputs();
      expect(next.map((i) => i.value)).toEqual(['1', '2', '3', '4']);
    });

    await waitFor(() => {
      expect(onCompleteSpy).toHaveBeenCalledTimes(1);
      expect(onCompleteSpy).toHaveBeenLastCalledWith('1234');
    });

    expect(onChangeSpy).toHaveBeenCalledWith('1234');
    expect(document.activeElement).not.toBe(getInputs()[3]);
  });

  it('fills multiple digits on paste and focuses the next cell when incomplete', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.paste(inputs[0] as HTMLInputElement, {
      clipboardData: {
        getData: () => '12',
      } as any,
    });

    await waitFor(() => {
      const next = getInputs();
      expect(next.map((i) => i.value)).toEqual(['1', '2', '', '']);
    });
    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[2]);
    });
    expect(onChangeSpy).toHaveBeenCalledWith('12');
  });

  it('ignores paste when clipboard text is empty', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.paste(inputs[0] as HTMLInputElement, {
      clipboardData: {
        getData: () => '',
      } as any,
    });
    await waitFor(() => {
      expect(getInputs().map((i) => i.value)).toEqual(['', '', '', '']);
    });
    expect(onChangeSpy).toHaveBeenCalledTimes(0);
  });

  it('ignores paste content when it contains no digits', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.paste(inputs[0] as HTMLInputElement, {
      clipboardData: {
        getData: () => 'abcd',
      } as any,
    });
    await waitFor(() => {
      expect(getInputs().map((i) => i.value)).toEqual(['', '', '', '']);
    });
    expect(onChangeSpy).toHaveBeenCalledTimes(0);
  });

  it('clears current cell on Backspace when cell has value', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '12' });
    const inputs = getInputs();
    inputs[1]?.focus();
    fireEvent.keyDown(inputs[1] as HTMLInputElement, { key: 'Backspace' });

    await waitFor(() => {
      const next = getInputs();
      expect(next[0]?.value).toBe('1');
      expect(next[1]?.value).toBe('');
    });
    expect(document.activeElement).toBe(getInputs()[1]);
    expect(onChangeSpy).toHaveBeenCalledWith('1');
  });

  it('does nothing on Backspace at the first cell when empty', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.keyDown(inputs[0] as HTMLInputElement, { key: 'Backspace' });
    await new Promise((r) => setTimeout(r, 0));
    expect(getInputs().map((i) => i.value)).toEqual(['', '', '', '']);
    expect(document.activeElement).toBe(getInputs()[0]);
    expect(onChangeSpy).toHaveBeenCalledTimes(0);
  });

  it('clears current cell when input becomes empty', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '1' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.change(inputs[0] as HTMLInputElement, { target: { value: '' } });
    await waitFor(() => {
      expect(getInputs()[0]?.value).toBe('');
    });
    expect(onChangeSpy).toHaveBeenCalledWith('');
  });

  it('clears previous cell on Backspace when current is empty', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '1' });
    const inputs = getInputs();
    inputs[1]?.focus();
    fireEvent.keyDown(inputs[1] as HTMLInputElement, { key: 'Backspace' });
    await waitFor(() => {
      const next = getInputs();
      expect(next[0]?.value).toBe('');
      expect(document.activeElement).toBe(next[0]);
    });
    expect(onChangeSpy).toHaveBeenCalledWith('');
  });

  it('moves focus backward and clears previous on Backspace when cell is empty', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '12' });
    const inputs = getInputs();
    inputs[2]?.focus();
    fireEvent.keyDown(inputs[2] as HTMLInputElement, { key: 'Backspace' });

    await waitFor(() => {
      const next = getInputs();
      expect(next[0]?.value).toBe('1');
      expect(next[1]?.value).toBe('');
      expect(document.activeElement).toBe(next[1]);
    });
    expect(onChangeSpy).toHaveBeenCalledWith('1');
  });

  it('calls onComplete on Enter when value is complete and enabled', async () => {
    const { onCompleteSpy } = renderControlled({ digits: 4, initialValue: '1234' });
    const inputs = getInputs();
    inputs[3]?.focus();
    fireEvent.keyDown(inputs[3] as HTMLInputElement, { key: 'Enter' });
    await waitFor(() => {
      expect(onCompleteSpy).toHaveBeenCalledTimes(2);
    });
  });

  it('does not call onComplete on Enter when disabled', async () => {
    const { onCompleteSpy } = renderControlled({ digits: 4, initialValue: '1234', disabled: true });
    const inputs = getInputs();
    inputs[3]?.focus();
    fireEvent.keyDown(inputs[3] as HTMLInputElement, { key: 'Enter' });
    await waitFor(() => {
      expect(onCompleteSpy).toHaveBeenCalledTimes(0);
    });
  });

  it('does not call onComplete on Enter when value is incomplete', async () => {
    const { onCompleteSpy } = renderControlled({ digits: 4, initialValue: '12' });
    const inputs = getInputs();
    inputs[1]?.focus();
    fireEvent.keyDown(inputs[1] as HTMLInputElement, { key: 'Enter' });
    await new Promise((r) => setTimeout(r, 0));
    expect(onCompleteSpy).toHaveBeenCalledTimes(0);
  });

  it('ignores unrelated key presses', async () => {
    const { onChangeSpy } = renderControlled({ digits: 4, initialValue: '1' });
    const inputs = getInputs();
    inputs[0]?.focus();
    fireEvent.keyDown(inputs[0] as HTMLInputElement, { key: 'x' });
    await new Promise((r) => setTimeout(r, 0));
    expect(getInputs()[0]?.value).toBe('1');
    expect(onChangeSpy).toHaveBeenCalledTimes(0);
  });

  it('selects input content on focus', () => {
    renderControlled({ digits: 4, initialValue: '1' });
    const input = getInputs()[0] as HTMLInputElement;
    const selectSpy = vi.spyOn(input, 'select');
    fireEvent.focus(input);
    expect(selectSpy).toHaveBeenCalledTimes(1);
  });

  it('supports ArrowLeft and ArrowRight navigation', async () => {
    renderControlled({ digits: 4, initialValue: '12' });
    const inputs = getInputs();
    inputs[1]?.focus();
    fireEvent.keyDown(inputs[1] as HTMLInputElement, { key: 'ArrowLeft' });

    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[0]);
    });

    fireEvent.keyDown(getInputs()[0] as HTMLInputElement, { key: 'ArrowRight' });

    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[1]);
    });
  });

  it('dedupes onComplete for the same completed value', async () => {
    const onComplete = vi.fn();
    const { rerender } = render(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} onComplete={onComplete} />);

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledTimes(1);
      expect(onComplete).toHaveBeenLastCalledWith('1234');
    });

    rerender(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} onComplete={onComplete} />);
    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    rerender(<OtpCodeInput digits={4} value={'123'} onChange={() => {}} onComplete={onComplete} />);
    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    rerender(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} onComplete={onComplete} />);
    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledTimes(2);
    });
  });

  it('does not call onComplete again when callback identity changes but value is unchanged', async () => {
    const onComplete1 = vi.fn();
    const { rerender } = render(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} onComplete={onComplete1} />);
    await waitFor(() => {
      expect(onComplete1).toHaveBeenCalledTimes(1);
    });

    const onComplete2 = vi.fn();
    rerender(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} onComplete={onComplete2} />);
    await new Promise((r) => setTimeout(r, 0));
    expect(onComplete2).toHaveBeenCalledTimes(0);
  });

  it('autoFocus focuses the first empty cell (or last when complete)', async () => {
    const { rerender } = render(<OtpCodeInput digits={4} value={''} onChange={() => {}} autoFocus={true} />);
    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[0]);
    });

    rerender(<OtpCodeInput digits={4} value={'12'} onChange={() => {}} autoFocus={true} />);
    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[2]);
    });

    rerender(<OtpCodeInput digits={4} value={'1234'} onChange={() => {}} autoFocus={true} />);
    await waitFor(() => {
      expect(document.activeElement).toBe(getInputs()[3]);
    });
  });

  it('does not autoFocus when disabled', async () => {
    render(<OtpCodeInput digits={4} value={''} onChange={() => {}} autoFocus={true} disabled={true} />);
    await waitFor(() => {
      const inputs = getInputs();
      expect(inputs.some((i) => i === document.activeElement)).toBe(false);
    });
  });
});
