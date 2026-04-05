import { describe, expect, it } from 'vitest';
import { normTaskType } from './llmRoutingUiUtils';

describe('normTaskType', () => {
  it('trims and uppercases values', () => {
    expect(normTaskType(' chat_route ')).toBe('CHAT_ROUTE');
  });

  it('falls back to empty string for nullish values', () => {
    expect(normTaskType(null)).toBe('');
    expect(normTaskType(undefined)).toBe('');
  });
});
