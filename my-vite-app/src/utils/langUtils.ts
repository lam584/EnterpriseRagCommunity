export function normalizeLangBase(lang: string | null | undefined): string {
  const raw = String(lang ?? '').trim().toLowerCase();
  if (!raw) return '';
  const base = raw.split(/[-_]/)[0] ?? raw;
  if (base === 'zh') return 'zh';
  return base;
}

export function extractLanguagesFromMetadata(metadata: unknown): string[] {
  if (!metadata || typeof metadata !== 'object') return [];
  const m = metadata as Record<string, unknown>;
  const v = m.languages;
  if (!Array.isArray(v)) return [];
  return v.map((x) => String(x ?? '').trim()).filter((x) => x.length > 0);
}

export function normalizeTargetLanguageBase(targetLanguage: string | null | undefined): string {
  const raw = String(targetLanguage ?? '').trim();
  if (!raw) return '';
  const lower = raw.toLowerCase();

  if (lower === 'zh' || lower.startsWith('zh-') || lower.startsWith('zh_')) return 'zh';
  if (lower === 'en' || lower.startsWith('en-') || lower.startsWith('en_')) return 'en';
  if (lower === 'ja' || lower.startsWith('ja-') || lower.startsWith('ja_')) return 'ja';
  if (lower === 'ko' || lower.startsWith('ko-') || lower.startsWith('ko_')) return 'ko';
  if (lower === 'fr' || lower.startsWith('fr-') || lower.startsWith('fr_')) return 'fr';
  if (lower === 'de' || lower.startsWith('de-') || lower.startsWith('de_')) return 'de';
  if (lower === 'es' || lower.startsWith('es-') || lower.startsWith('es_')) return 'es';
  if (lower === 'ru' || lower.startsWith('ru-') || lower.startsWith('ru_')) return 'ru';

  const inParen = lower.match(/\(([^)]+)\)/)?.[1]?.trim() ?? '';
  const tokens = `${lower} ${inParen}`;

  if (tokens.includes('中文') || tokens.includes('chinese') || tokens.includes('cantonese')) return 'zh';
  if (tokens.includes('英语') || tokens.includes('english')) return 'en';
  if (tokens.includes('日语') || tokens.includes('japanese')) return 'ja';
  if (tokens.includes('韩') || tokens.includes('朝鲜') || tokens.includes('korean')) return 'ko';
  if (tokens.includes('法语') || tokens.includes('french')) return 'fr';
  if (tokens.includes('德语') || tokens.includes('german')) return 'de';
  if (tokens.includes('西班牙') || tokens.includes('spanish')) return 'es';
  if (tokens.includes('俄语') || tokens.includes('russian')) return 'ru';

  return normalizeLangBase(lower);
}
