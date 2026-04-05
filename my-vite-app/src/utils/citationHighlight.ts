export function normalizeCitationContext(text: string): string {
  return String(text ?? '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/[#>*_~]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export type CitationSnippetLike = {
  index?: number | string | null;
  snippet?: string | null;
};

const FENCED_CODE_RE = /```[\s\S]*?```/g;
const INLINE_CODE_RE = /`[^`\n]+`/g;
const QUOTED_CITATION_RE = /\\?([“"「『])([^“"”「」『』\n]{2,120}?)\\?([”"」』])((?:\s*\[(\d{1,3})](?!\())+)/g;
const ANSWER_HIGHLIGHT_CLASS = 'rounded bg-yellow-200 px-0.5';

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function transformSkippingPattern(source: string, pattern: RegExp, mapper: (segment: string) => string): string {
  const src = String(source ?? '');
  if (!src) return '';
  let out = '';
  let lastIndex = 0;
  pattern.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(src)) !== null) {
    out += mapper(src.slice(lastIndex, match.index));
    out += match[0];
    lastIndex = match.index + match[0].length;
  }
  out += mapper(src.slice(lastIndex));
  pattern.lastIndex = 0;
  return out;
}

function extractCitationIndexesFromTail(text: string): number[] {
  const out: number[] = [];
  for (const m of String(text ?? '').matchAll(/\[(\d{1,3})](?!\()/g)) {
    const idx = Number(m[1]);
    if (!Number.isFinite(idx) || idx <= 0) continue;
    out.push(idx);
  }
  return out;
}

function normalizeTerm(text: string): string {
  return normalizeCitationContext(text).toLowerCase();
}

function normalizeEscapedCitationQuotes(md: string): string {
  return transformSkippingPattern(md, FENCED_CODE_RE, (nonCode) =>
    transformSkippingPattern(nonCode, INLINE_CODE_RE, (segment) => segment.replace(/\\"/g, '"'))
  );
}

export function highlightExactCitationQuotes(md: string, sources: CitationSnippetLike[]): string {
  const src = normalizeEscapedCitationQuotes(String(md ?? ''));
  if (!src) return '';

  const exactMap = buildCitationExactQuoteTerms(src);
  if (exactMap.size === 0) return src;

  const matchedExactTerms = new Map<number, Set<string>>();
  const citedIndexes = new Set<number>();
  for (const source of sources ?? []) {
    const idx = Number(source?.index);
    if (Number.isFinite(idx) && idx > 0) citedIndexes.add(idx);
    const snippet = String(source?.snippet ?? '').trim();
    if (!Number.isFinite(idx) || idx <= 0 || !snippet) continue;
    const picked = pickCitationHighlightTerms(idx, snippet, exactMap, new Map());
    if (!picked.exact || picked.terms.length === 0) continue;
    matchedExactTerms.set(idx, new Set(picked.terms.map((term) => normalizeTerm(term)).filter(Boolean)));
  }
  if (citedIndexes.size === 0) return src;

  const highlightQuotes = (segment: string): string =>
    segment.replace(QUOTED_CITATION_RE, (full, openQuote, quoteText, closeQuote, citationTail) => {
      const normalizedQuote = normalizeTerm(quoteText);
      if (!normalizedQuote) return full;
      const indexes = extractCitationIndexesFromTail(citationTail);
      const hit = indexes.some((idx) => {
        const strictHit = matchedExactTerms.get(idx)?.has(normalizedQuote);
        if (strictHit) return true;
        const quotedByAnswer = (exactMap.get(idx) ?? []).some((term) => normalizeTerm(term) === normalizedQuote);
        const citationExists = citedIndexes.has(idx);
        return quotedByAnswer && citationExists;
      });
      if (!hit) return full;
      return `${openQuote}<span class="${ANSWER_HIGHLIGHT_CLASS}">${quoteText}</span>${closeQuote}${citationTail}`;
    });

  return transformSkippingPattern(src, FENCED_CODE_RE, (nonCode) =>
    transformSkippingPattern(nonCode, INLINE_CODE_RE, highlightQuotes)
  );
}

function extractHighlightTerms(text: string): string[] {
  const src = normalizeCitationContext(text);
  if (!src) return [];
  const matches = src.match(/[\u4e00-\u9fa5]{2,12}|[a-zA-Z][a-zA-Z0-9_-]{2,}|[0-9]{2,}/g) ?? [];
  const out: string[] = [];
  const seen = new Set<string>();
  for (const raw of matches) {
    const t = raw.trim();
    if (!t) continue;
    if (t.length > 28) continue;
    const k = t.toLowerCase();
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(t);
    if (out.length >= 12) break;
  }
  return out;
}

export function buildCitationFallbackTerms(md: string): Map<number, string[]> {
  const out = new Map<number, string[]>();
  if (!md) return out;
  const collect = (txt: string) => {
    for (const m of txt.matchAll(/\[(\d{1,3})](?!\()/g)) {
      const idx = Number(m[1]);
      if (!Number.isFinite(idx) || idx <= 0) continue;
      const pos = m.index ?? 0;
      const before = txt.slice(Math.max(0, pos - 180), pos);
      const cleaned = normalizeCitationContext(before);
      if (!cleaned) continue;
      const segments = cleaned.split(/[。！？!?；;\n]/).map((s) => s.trim()).filter(Boolean);
      const context = segments.length > 0 ? segments[segments.length - 1] : cleaned;
      const terms = extractHighlightTerms(context);
      if (terms.length === 0) continue;
      const merged = [...(out.get(idx) ?? [])];
      const seen = new Set(merged.map((x) => x.toLowerCase()));
      for (const t of terms) {
        const key = t.toLowerCase();
        if (seen.has(key)) continue;
        seen.add(key);
        merged.push(t);
        if (merged.length >= 12) break;
      }
      out.set(idx, merged);
    }
  };
  collectOutsideCodeBlocks(md, collect);
  return out;
}

export function buildCitationExactQuoteTerms(md: string): Map<number, string[]> {
  const out = new Map<number, string[]>();
  if (!md) return out;
  const quoteRe = /[“"「『]([^“"”「」『』\n]{2,120}?)[”"」』]/g;
  const collect = (txt: string) => {
    for (const m of txt.matchAll(/\[(\d{1,3})](?!\()/g)) {
      const idx = Number(m[1]);
      if (!Number.isFinite(idx) || idx <= 0) continue;
      const pos = m.index ?? 0;
      const before = txt.slice(Math.max(0, pos - 260), pos);
      const candidates: string[] = [];
      let qm: RegExpExecArray | null;
      while ((qm = quoteRe.exec(before)) !== null) {
        const q = normalizeCitationContext(qm[1] ?? '');
        if (!q) continue;
        if (q.length > 80) continue;
        candidates.push(q);
      }
      quoteRe.lastIndex = 0;
      if (candidates.length === 0) continue;
      const merged = [...(out.get(idx) ?? [])];
      const seen = new Set(merged.map((x) => x.toLowerCase()));
      for (let i = candidates.length - 1; i >= 0; i--) {
        const t = candidates[i]!.trim();
        if (!t) continue;
        const key = t.toLowerCase();
        if (seen.has(key)) continue;
        seen.add(key);
        merged.push(t);
        if (merged.length >= 8) break;
      }
      if (merged.length > 0) out.set(idx, merged);
    }
  };
  collectOutsideCodeBlocks(md, collect);
  return out;
}

function collectOutsideCodeBlocks(md: string, collect: (txt: string) => void): void {
  const reCode = /```[\s\S]*?```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = reCode.exec(md)) !== null) {
    collect(md.slice(lastIndex, match.index));
    lastIndex = match.index + match[0].length;
  }
  collect(md.slice(lastIndex));
}

export function pickCitationHighlightTerms(
  citationIndex: number,
  snippet: string,
  exactMap: Map<number, string[]>,
  fallbackMap: Map<number, string[]>,
): { terms: string[]; exact: boolean } {
  const rawSnippet = String(snippet ?? '').trim();
  if (!rawSnippet) return { terms: [], exact: false };
  const exact = filterTermsBySnippet(exactMap.get(citationIndex) ?? [], rawSnippet);
  if (exact.length > 0) return { terms: exact, exact: true };
  const fallback = filterTermsBySnippet(fallbackMap.get(citationIndex) ?? [], rawSnippet);
  return { terms: fallback, exact: false };
}

function filterTermsBySnippet(terms: string[], snippet: string): string[] {
  if (!terms || terms.length === 0) return [];
  const src = snippet.toLowerCase();
  const out: string[] = [];
  const seen = new Set<string>();
  for (const t of terms) {
    const k = String(t ?? '').trim();
    if (k.length < 2) continue;
    const lower = k.toLowerCase();
    if (seen.has(lower)) continue;
    if (!src.includes(lower)) continue;
    seen.add(lower);
    out.push(k);
    if (out.length >= 8) break;
  }
  return out.sort((a, b) => b.length - a.length);
}

export function buildHighlightedParts(text: string, terms: string[]): Array<{ text: string; hit: boolean }> {
  const src = String(text ?? '');
  if (!src) return [{ text: '', hit: false }];
  const picked = terms
    .map((t) => t.trim())
    .filter((t) => t.length >= 2)
    .sort((a, b) => b.length - a.length)
    .slice(0, 8);
  if (picked.length === 0) return [{ text: src, hit: false }];
  const re = new RegExp(`(${picked.map((x) => escapeRegExp(x)).join('|')})`, 'ig');
  const out: Array<{ text: string; hit: boolean }> = [];
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    if (m.index > last) out.push({ text: src.slice(last, m.index), hit: false });
    out.push({ text: m[0], hit: true });
    last = m.index + m[0].length;
  }
  if (last < src.length) out.push({ text: src.slice(last), hit: false });
  return out.length > 0 ? out : [{ text: src, hit: false }];
}
