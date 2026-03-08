import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mockConsole } from './mockConsole';

describe('mockConsole', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('mocks specified console methods and can restore', () => {
    const c = mockConsole(['error']);
    console.error('x');
    expect(c.error).toHaveBeenCalledTimes(1);
    c.restore();
  });

  it('dedupes methods and returns spies by name', () => {
    const c = mockConsole(['warn', 'warn', 'log']);
    console.warn('w');
    console.log('l');
    expect(c.warn).toHaveBeenCalledTimes(1);
    expect(c.log).toHaveBeenCalledTimes(1);
    expect(c.error).toBeUndefined();
    expect(c.info).toBeUndefined();
    expect(c.debug).toBeUndefined();
    c.restore();
  });
});
