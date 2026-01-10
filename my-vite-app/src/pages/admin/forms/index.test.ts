import { describe, expect, it } from 'vitest';
import { formsLoaders, getLazyForm } from './index';

describe('admin forms lazy registry', () => {
  it('contains expected keys', () => {
    expect(typeof formsLoaders.metrics).toBe('function');
    expect(typeof formsLoaders.index).toBe('function');
    expect(typeof formsLoaders.logs).toBe('function');
  });

  it('returns a lazy component for a known form id', () => {
    const C = getLazyForm('metrics');
    expect(C).toBeTruthy();
    expect(typeof C).toBe('object');
  });

  it('returns a lazy component for an unknown form id (safe fallback)', () => {
    const C = getLazyForm('__unknown__');
    expect(C).toBeTruthy();
  });
});

