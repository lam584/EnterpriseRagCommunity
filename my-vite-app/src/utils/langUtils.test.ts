import { describe, it, expect } from 'vitest';
import { extractLanguagesFromMetadata, normalizeLangBase, normalizeTargetLanguageBase } from './langUtils';

describe('langUtils', () => {
  it('normalizes BCP-47 tags to base language', () => {
    expect(normalizeLangBase('en-US')).toBe('en');
    expect(normalizeLangBase('zh-CN')).toBe('zh');
    expect(normalizeLangBase('ZH_cn')).toBe('zh');
    expect(normalizeLangBase(null)).toBe('');
  });

  it('normalizes target language display labels', () => {
    expect(normalizeTargetLanguageBase('英语（English）')).toBe('en');
    expect(normalizeTargetLanguageBase('简体中文（Simplified Chinese）')).toBe('zh');
    expect(normalizeTargetLanguageBase('繁体中文（Traditional Chinese）')).toBe('zh');
    expect(normalizeTargetLanguageBase('日语（Japanese）')).toBe('ja');
    expect(normalizeTargetLanguageBase('한국어 (Korean)')).toBe('ko');
    expect(normalizeTargetLanguageBase('Français (French)')).toBe('fr');
    expect(normalizeTargetLanguageBase('Deutsch (German)')).toBe('de');
    expect(normalizeTargetLanguageBase('Español (Spanish)')).toBe('es');
    expect(normalizeTargetLanguageBase('Русский (Russian)')).toBe('ru');
    expect(normalizeTargetLanguageBase('ja_JP')).toBe('ja');
    expect(normalizeTargetLanguageBase('unknown')).toBe('unknown');
    expect(normalizeTargetLanguageBase('')).toBe('');
  });

  it('normalizes direct language codes', () => {
    expect(normalizeTargetLanguageBase(undefined)).toBe('');
    expect(normalizeTargetLanguageBase(null)).toBe('');

    expect(normalizeTargetLanguageBase('zh')).toBe('zh');
    expect(normalizeTargetLanguageBase('zh-CN')).toBe('zh');
    expect(normalizeTargetLanguageBase('zh_CN')).toBe('zh');

    expect(normalizeTargetLanguageBase('en')).toBe('en');
    expect(normalizeTargetLanguageBase('en-US')).toBe('en');
    expect(normalizeTargetLanguageBase('en_US')).toBe('en');

    expect(normalizeTargetLanguageBase('ja')).toBe('ja');
    expect(normalizeTargetLanguageBase('ja-JP')).toBe('ja');
    expect(normalizeTargetLanguageBase('ja_JP')).toBe('ja');

    expect(normalizeTargetLanguageBase('ko')).toBe('ko');
    expect(normalizeTargetLanguageBase('ko-KR')).toBe('ko');
    expect(normalizeTargetLanguageBase('ko_KR')).toBe('ko');

    expect(normalizeTargetLanguageBase('fr')).toBe('fr');
    expect(normalizeTargetLanguageBase('fr-FR')).toBe('fr');
    expect(normalizeTargetLanguageBase('fr_FR')).toBe('fr');

    expect(normalizeTargetLanguageBase('de')).toBe('de');
    expect(normalizeTargetLanguageBase('de-DE')).toBe('de');
    expect(normalizeTargetLanguageBase('de_DE')).toBe('de');

    expect(normalizeTargetLanguageBase('es')).toBe('es');
    expect(normalizeTargetLanguageBase('es-ES')).toBe('es');
    expect(normalizeTargetLanguageBase('es_ES')).toBe('es');

    expect(normalizeTargetLanguageBase('ru')).toBe('ru');
    expect(normalizeTargetLanguageBase('ru-RU')).toBe('ru');
    expect(normalizeTargetLanguageBase('ru_RU')).toBe('ru');
  });

  it('extracts languages from metadata', () => {
    expect(extractLanguagesFromMetadata(null)).toEqual([]);
    expect(extractLanguagesFromMetadata({ languages: 'en' })).toEqual([]);
    expect(extractLanguagesFromMetadata({ languages: [' en ', null, ''] })).toEqual(['en']);
  });
});
