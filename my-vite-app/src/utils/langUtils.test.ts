import { describe, it, expect } from 'vitest';
import { normalizeLangBase, normalizeTargetLanguageBase } from './langUtils';

describe('langUtils', () => {
  it('normalizes BCP-47 tags to base language', () => {
    expect(normalizeLangBase('en-US')).toBe('en');
    expect(normalizeLangBase('zh-CN')).toBe('zh');
    expect(normalizeLangBase('ZH_cn')).toBe('zh');
  });

  it('normalizes target language display labels', () => {
    expect(normalizeTargetLanguageBase('英语（English）')).toBe('en');
    expect(normalizeTargetLanguageBase('简体中文（Simplified Chinese）')).toBe('zh');
    expect(normalizeTargetLanguageBase('繁体中文（Traditional Chinese）')).toBe('zh');
  });
});

