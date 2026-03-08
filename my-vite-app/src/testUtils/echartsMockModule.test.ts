import { describe, expect, it } from 'vitest';
import { init } from './echartsMockModule';

describe('echartsMockModule', () => {
  it('creates a chart instance and records last setOption', () => {
    const dom = document.createElement('div');
    const chart = init(dom);
    expect(chart.__dom).toBe(dom);
    expect(chart.__disposed).toBe(false);
    expect(chart.__lastSetOption).toBeNull();

    chart.setOption({ a: 1 }, { notMerge: true });
    expect(chart.__lastSetOption).toEqual({ option: { a: 1 }, opts: { notMerge: true } });

    chart.clear();
    expect(chart.__lastSetOption).toBeNull();

    chart.resize();
    chart.dispose();
    expect(chart.__disposed).toBe(true);
  });
});

