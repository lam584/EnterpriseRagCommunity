import { describe, expect, it } from 'vitest';
import { estimateVisionImageTokens, inferVisionModelProfile } from './visionImageTokens';

describe('visionImageTokens', () => {
  it('returns null when width/height missing', () => {
    expect(estimateVisionImageTokens({ width: null, height: 100 })).toBeNull();
    expect(estimateVisionImageTokens({ width: 100, height: undefined })).toBeNull();
  });

  it('handles qwen3-vl default 32x32 with downscale by max_pixels', () => {
    const t = estimateVisionImageTokens({
      model: 'qwen3-vl-plus',
      width: 4000,
      height: 4000,
      vlHighResolutionImages: false,
      maxPixelsOverride: null
    });
    expect(t).toBe(2502);
  });

  it('handles min_pixels upscale for tiny images', () => {
    const t = estimateVisionImageTokens({
      model: 'qwen3-vl-plus',
      width: 10,
      height: 10,
      vlHighResolutionImages: false,
      maxPixelsOverride: 2_621_440
    });
    expect(t).toBe(6);
  });

  it('uses high-resolution upper bound when enabled', () => {
    const t = estimateVisionImageTokens({
      model: 'qwen3-vl-plus',
      width: 4096,
      height: 4096,
      vlHighResolutionImages: true
    });
    expect(t).toBe(16386);
  });

  it('uses model-specific default max_pixels for qwen-vl-plus', () => {
    const t = estimateVisionImageTokens({
      model: 'qwen-vl-plus',
      width: 1024,
      height: 1024,
      vlHighResolutionImages: false,
      maxPixelsOverride: null
    });
    expect(t).toBe(1026);
  });

  it('infers 28x28 profile for qvq and applies min_pixels', () => {
    const p = inferVisionModelProfile('qvq-72b-preview');
    expect(p.side).toBe(28);
    const t = estimateVisionImageTokens({
      model: 'qvq-72b-preview',
      width: 28,
      height: 28,
      vlHighResolutionImages: false,
      maxPixelsOverride: null
    });
    expect(t).toBe(6);
  });

  it('infers 28x28 profile for prefixed qwen-vl models that are not in exact set', () => {
    const p = inferVisionModelProfile('qwen-vl-plus-custom');
    expect(p.side).toBe(28);
  });

  it('falls back to qwen3-vl defaults for unknown models', () => {
    const p = inferVisionModelProfile('unknown');
    expect(p.side).toBe(32);
  });

  it('infers defaults when model is nullish or blank', () => {
    expect(inferVisionModelProfile(null).side).toBe(32);
    expect(inferVisionModelProfile(undefined).side).toBe(32);
    expect(inferVisionModelProfile('   ').side).toBe(32);
  });

  it('returns null for non-finite or non-positive dimensions', () => {
    expect(estimateVisionImageTokens({ width: NaN, height: 100 })).toBeNull();
    expect(estimateVisionImageTokens({ width: Infinity, height: 100 })).toBeNull();
    expect(estimateVisionImageTokens({ width: 0, height: 100 })).toBeNull();
    expect(estimateVisionImageTokens({ width: -1, height: 100 })).toBeNull();
    expect(estimateVisionImageTokens({ width: 100, height: NaN })).toBeNull();
    expect(estimateVisionImageTokens({ width: 100, height: Infinity })).toBeNull();
    expect(estimateVisionImageTokens({ width: 100, height: 0 })).toBeNull();
    expect(estimateVisionImageTokens({ width: 100, height: -1 })).toBeNull();
  });

  it('falls back to default maxPixels when override is invalid', () => {
    const base = estimateVisionImageTokens({
      model: 'qwen3-vl-plus',
      width: 1024,
      height: 1024,
      vlHighResolutionImages: false,
      maxPixelsOverride: null
    });
    expect(base).not.toBeNull();

    const cases = [NaN, Infinity, 0, -1];
    for (const v of cases) {
      const t = estimateVisionImageTokens({
        model: 'qwen3-vl-plus',
        width: 1024,
        height: 1024,
        vlHighResolutionImages: false,
        maxPixelsOverride: v
      });
      expect(t).toBe(base);
    }
  });
});
