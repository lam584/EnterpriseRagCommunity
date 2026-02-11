export type ParsedTranslateMarkdown = {
  title?: string;
  markdown: string;
};

function stripCodeFenceBlock(input: string): string {
  const s = input.trim();
  const m = s.match(/^```[^\r\n]*\r?\n([\s\S]*?)\r?\n```$/);
  return m ? m[1] : input;
}

function tryDecodeQuotedString(input: string): string | null {
  const s = input.trim();
  if (s.length < 2) return null;
  const q = s[0];
  if ((q !== '"' && q !== "'") || s[s.length - 1] !== q) return null;
  try {
    const parsed = JSON.parse(s);
    return typeof parsed === 'string' ? parsed : null;
  } catch {
    return null;
  }
}

function parseKeyValueBlock(input: string): ParsedTranslateMarkdown | null {
  const s = input.trim();
  const m = s.match(/^\s*title\s*[:：]\s*(.+?)\s*(?:\r?\n)+\s*markdown\s*[:：]\s*([\s\S]*)$/i);
  if (!m) return null;
  const title = (m[1] || '').trim();
  const markdown = (m[2] || '').trim();
  if (!title && !markdown) return null;
  return { title: title || undefined, markdown };
}

function findKeyMatch(input: string, key: string): { start: number; end: number } | null {
  const re = new RegExp(`(?:^|[,{\\s])(?:"${key}"|'${key}'|${key})\\s*:`, 'i');
  const m = re.exec(input);
  if (!m || m.index == null) return null;
  return { start: m.index, end: m.index + m[0].length };
}

function decodeEscapedChar(ch: string): string {
  switch (ch) {
    case 'n':
      return '\n';
    case 'r':
      return '\r';
    case 't':
      return '\t';
    case '"':
      return '"';
    case "'":
      return "'";
    case '\\':
      return '\\';
    case '/':
      return '/';
    case 'b':
      return '\b';
    case 'f':
      return '\f';
    default:
      return ch;
  }
}

function extractJsonLikeValue(input: string, key: string): string | null {
  const match = findKeyMatch(input, key);
  if (!match) return null;
  let i = match.end;
  while (i < input.length && /\s/.test(input[i])) i += 1;

  const quote = input[i];
  if (quote === '"' || quote === "'") {
    i += 1;
    let out = '';
    while (i < input.length) {
      const ch = input[i];
      if (ch === '\\') {
        const next = input[i + 1];
        if (next === 'u') {
          const hex = input.slice(i + 2, i + 6);
          if (/^[0-9a-fA-F]{4}$/.test(hex)) {
            out += String.fromCharCode(parseInt(hex, 16));
            i += 6;
            continue;
          }
        }
        if (next != null) {
          out += decodeEscapedChar(next);
          i += 2;
          continue;
        }
        i += 1;
        continue;
      }
      if (ch === quote) return out;
      out += ch;
      i += 1;
    }
    return out;
  }

  let end = i;
  while (end < input.length && input[end] !== ',' && input[end] !== '}') end += 1;
  const raw = input.slice(i, end).trim();
  return raw ? raw : null;
}

function hasJsonLikeKey(input: string, key: string): boolean {
  const re = new RegExp(`(?:^|[,{\\s])(?:"${key}"|'${key}'|${key})\\s*:`, 'i');
  return re.test(input);
}

export function parseTranslateMarkdown(rawInput: string | null | undefined): ParsedTranslateMarkdown {
  const raw = typeof rawInput === 'string' ? rawInput : '';
  if (!raw.trim()) return { markdown: '' };

  const decoded = tryDecodeQuotedString(raw);
  const base = decoded ?? raw;
  const unfenced = stripCodeFenceBlock(base);

  const kv = parseKeyValueBlock(unfenced);
  if (kv) return kv;

  const s = unfenced.trim();
  if (s.startsWith('{') && (hasJsonLikeKey(s, 'markdown') || hasJsonLikeKey(s, 'title'))) {
    try {
      const parsed = JSON.parse(s) as unknown;
      if (parsed && typeof parsed === 'object') {
        const obj = parsed as Record<string, unknown>;
        const title = typeof obj.title === 'string' ? obj.title : undefined;
        const markdown =
          typeof obj.markdown === 'string'
            ? obj.markdown
            : typeof obj.translatedMarkdown === 'string'
              ? obj.translatedMarkdown
              : '';
        if ((title && title.trim()) || (markdown && markdown.trim())) {
          return { title: title?.trim() || undefined, markdown: (markdown ?? '').trim() };
        }
      }
    } catch {
      // ignore
    }

    const title = extractJsonLikeValue(s, 'title')?.trim() || undefined;
    const markdown = extractJsonLikeValue(s, 'markdown')?.trim() || '';
    if (title || markdown) return { title, markdown };
  }

  return { markdown: s };
}

