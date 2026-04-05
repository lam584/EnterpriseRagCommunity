import { describe, expect, it } from 'vitest';
import {
  buildProviderNameMap,
  clampMetricInt,
  computeMaxMetricChartValue,
  createDefaultMetricsRangeState,
  dayEnd,
  dayStart,
  resolveRangePresetDates,
} from './metricsTimeUtils';

describe('metricsTimeUtils', () => {
  it('createDefaultMetricsRangeState builds last-7-day custom range', () => {
    const now = new Date('2026-04-05T15:16:17');
    const state = createDefaultMetricsRangeState(now);

    expect(state.rangePreset).toBe('CUSTOM');
    expect(state.startDate.getTime()).toBe(dayStart(new Date('2026-03-29T15:16:17')).getTime());
    expect(state.endDate.getTime()).toBe(dayEnd(now).getTime());
  });

  it('clampMetricInt clamps numeric and string values with fallback', () => {
    expect(clampMetricInt(12.8, 1, 10, 5)).toBe(10);
    expect(clampMetricInt(' 7 ', 1, 10, 5)).toBe(7);
    expect(clampMetricInt('bad', 1, 10, 5)).toBe(5);
  });

  it('buildProviderNameMap ignores blanks and falls back to id', () => {
    expect(
      buildProviderNameMap([
        { id: ' p1 ', name: ' OpenAI ' },
        { id: 'p2', name: ' ' },
        { id: ' ', name: 'x' },
      ]),
    ).toEqual({
      p1: 'OpenAI',
      p2: 'p2',
    });
  });

  it('resolveRangePresetDates and computeMaxMetricChartValue cover shared metrics helpers', () => {
    const end = new Date('2026-04-05T15:16:17');
    const yesterday = resolveRangePresetDates('YESTERDAY', end);
    expect(yesterday?.startDate.getTime()).toBe(dayStart(new Date('2026-04-04T15:16:17')).getTime());
    expect(yesterday?.endDate.getTime()).toBe(dayEnd(new Date('2026-04-04T15:16:17')).getTime());
    expect(resolveRangePresetDates('CUSTOM', end)).toBeNull();

    const items = [
      { totalTokens: 5, cost: 0.2 },
      { totalTokens: 8, cost: 0.1 },
    ];
    expect(computeMaxMetricChartValue(items, 'tokens', (x) => x.totalTokens, (x) => x.cost)).toBe(8);
    expect(computeMaxMetricChartValue(items, 'cost', (x) => x.totalTokens, (x) => x.cost)).toBe(0.2);
    expect(computeMaxMetricChartValue([], 'tokens', () => 0, () => 0)).toBe(1);
  });
});
