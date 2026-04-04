type ExpandContextInput = {
  beforeText?: string | null;
  afterText?: string | null;
  sourceText?: string | null;
  maxExtraTokens?: number;
};

export type ResolvedEvidenceContext = {
  beforeText: string;
  afterText: string;
  extraBeforeText: string;
  extraAfterText: string;
};

function normalizeText(v: string | null | undefined): string {
  return (v ?? '').trim();
}

function splitSentences(text: string): string[] {
  const t = normalizeText(text);
  if (!t) return [];
  const out: string[] = [];
  let buf = '';
  for (let i = 0; i < t.length; i += 1) {
    const ch = t[i];
    buf += ch;
    if (ch === '\n' || ch === '。' || ch === '！' || ch === '？' || ch === ';' || ch === '；' || ch === '!' || ch === '?' || ch === '.') {
      const part = normalizeText(buf);
      if (part) out.push(part);
      buf = '';
    }
  }
  const tail = normalizeText(buf);
  if (tail) out.push(tail);
  return out;
}

function countApproxTokens(text: string): number {
  const t = normalizeText(text);
  if (!t) return 0;
  const matches = t.match(/\p{Script=Han}|[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*/gu);
  return matches ? matches.length : t.length;
}

function truncateSentenceByTokens(text: string, maxTokens: number): string {
  const t = normalizeText(text);
  if (!t) return '';
  if (maxTokens <= 0) return '';
  if (countApproxTokens(t) <= maxTokens) return t;
  const matches = Array.from(t.matchAll(/\p{Script=Han}|[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*/gu));
  if (matches.length <= maxTokens) return t;
  const cutAt = matches[maxTokens]?.index ?? t.length;
  return `${t.slice(0, Math.max(0, cutAt)).trimEnd()}…`;
}

function collapseWhitespaceWithMap(text: string): { collapsed: string; map: number[] } {
  const src = text ?? '';
  let out = '';
  const map: number[] = [];
  let prevSpace = false;
  for (let i = 0; i < src.length; i += 1) {
    const ch = src[i];
    const isSpace = /\s/.test(ch);
    if (isSpace) {
      if (!prevSpace) {
        out += ' ';
        map.push(i);
      }
      prevSpace = true;
      continue;
    }
    out += ch;
    map.push(i);
    prevSpace = false;
  }
  return { collapsed: out, map };
}

function mapCollapsedIndexToOriginal(map: number[], collapsedIndex: number): number {
  if (!map.length) return 0;
  if (collapsedIndex <= 0) return map[0] ?? 0;
  if (collapsedIndex >= map.length) return (map[map.length - 1] ?? 0) + 1;
  return map[collapsedIndex] ?? 0;
}

function findAnchorRange(sourceText: string, anchor: string): { start: number; end: number } | null {
  const source = normalizeText(sourceText);
  const key = normalizeText(anchor);
  if (!source || !key) return null;

  const exact = source.indexOf(key);
  if (exact >= 0) return { start: exact, end: exact + key.length };

  const srcCollapsed = collapseWhitespaceWithMap(source);
  const keyCollapsed = collapseWhitespaceWithMap(key).collapsed;
  const compactIdx = srcCollapsed.collapsed.indexOf(keyCollapsed);
  if (compactIdx >= 0) {
    const start = mapCollapsedIndexToOriginal(srcCollapsed.map, compactIdx);
    const end = mapCollapsedIndexToOriginal(srcCollapsed.map, compactIdx + keyCollapsed.length);
    if (end > start) return { start, end };
  }

  const head = key.slice(0, Math.min(24, key.length)).trim();
  const tail = key.slice(Math.max(0, key.length - 24)).trim();
  if (!head) return null;
  const start = source.indexOf(head);
  if (start < 0) return null;
  if (!tail || tail === head) return { start, end: start + head.length };
  const tailPos = source.indexOf(tail, start + head.length);
  if (tailPos < 0) return { start, end: start + head.length };
  return { start, end: tailPos + tail.length };
}

function pickPrevSentence(sourceText: string, anchor: string, maxExtraTokens: number): string {
  const source = normalizeText(sourceText);
  const key = normalizeText(anchor);
  if (!source || !key) return '';
  const range = findAnchorRange(source, key);
  if (!range || range.start <= 0) return '';
  const left = source.slice(0, range.start);
  const list = splitSentences(left);
  if (!list.length) return '';
  return truncateSentenceByTokens(list[list.length - 1] ?? '', maxExtraTokens);
}

function pickNextSentence(sourceText: string, anchor: string, maxExtraTokens: number): string {
  const source = normalizeText(sourceText);
  const key = normalizeText(anchor);
  if (!source || !key) return '';
  const range = findAnchorRange(source, key);
  if (!range) return '';
  const start = range.end;
  if (start >= source.length) return '';
  const right = source.slice(start);
  const list = splitSentences(right);
  if (!list.length) return '';
  return truncateSentenceByTokens(list[0] ?? '', maxExtraTokens);
}

export function resolveEvidenceContextSentences({ beforeText, afterText, sourceText }: Omit<ExpandContextInput, 'maxExtraTokens'>): ResolvedEvidenceContext {
  const b = normalizeText(beforeText);
  const a = normalizeText(afterText);
  const src = normalizeText(sourceText);
  return {
    beforeText: b,
    afterText: a,
    extraBeforeText: b && src ? pickPrevSentence(src, b, Number.MAX_SAFE_INTEGER) : '',
    extraAfterText: a && src ? pickNextSentence(src, a, Number.MAX_SAFE_INTEGER) : '',
  };
}

export function expandEvidenceContext({ beforeText, afterText, sourceText, maxExtraTokens = 20 }: ExpandContextInput): { beforeText: string; afterText: string } {
  const resolved = resolveEvidenceContextSentences({ beforeText, afterText, sourceText });
  let beforeExpanded = resolved.beforeText;
  let afterExpanded = resolved.afterText;
  const prev = truncateSentenceByTokens(resolved.extraBeforeText, maxExtraTokens);
  const next = truncateSentenceByTokens(resolved.extraAfterText, maxExtraTokens);
  if (prev && !beforeExpanded.startsWith(prev)) beforeExpanded = `${prev}\n${beforeExpanded}`;
  if (next && !afterExpanded.endsWith(next)) afterExpanded = `${afterExpanded}\n${next}`;
  return { beforeText: beforeExpanded, afterText: afterExpanded };
}
