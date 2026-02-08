import { describe, expect, it } from 'vitest';
import { percentile, toCsv } from './llm-loadtest';

describe('llm-loadtest utils', () => {
  it('calculates percentile with interpolation', () => {
    const xs = [1, 2, 3, 4];
    expect(percentile(xs, 0)).toBe(1);
    expect(percentile(xs, 1)).toBe(4);
    expect(percentile(xs, 0.5)).toBe(2.5);
    expect(percentile(xs, 0.95)).toBeCloseTo(3.85, 6);
  });

  it('returns null for empty input', () => {
    expect(percentile([], 0.5)).toBeNull();
  });

  it('serializes csv with proper escaping', () => {
    const csv = toCsv([
      { a: 'x', b: 'hello,world', c: 'line1\nline2', d: 'he said "hi"' },
    ]);
    expect(csv).toContain('a');
    expect(csv).toContain('b');
    expect(csv).toContain('"hello,world"');
    expect(csv).toContain('"line1\nline2"');
    expect(csv).toContain('"he said ""hi"""');
  });
});

