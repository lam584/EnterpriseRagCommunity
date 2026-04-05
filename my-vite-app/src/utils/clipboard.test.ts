import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyTextWithFallback } from './clipboard';

describe('clipboard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses navigator clipboard when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });

    await expect(copyTextWithFallback('abc')).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith('abc');
  });

  it('falls back to execCommand when clipboard api fails', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('boom'));
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    const execSpy = vi.spyOn(document, 'execCommand').mockReturnValue(true);

    await expect(copyTextWithFallback('xyz')).resolves.toBe(true);
    expect(execSpy).toHaveBeenCalledWith('copy');
  });
});
