export function normalizeMarkdownForPreview(markdown: string): string {
  if (!markdown) return markdown;

  const lines = markdown.split(/\r?\n/);
  let inFencedCodeBlock = false;
  let fenceChar: '`' | '~' | null = null;

  const normalized = lines.map((line) => {
    const fenceMatch = line.match(/^\s*([`~]{3,})/);
    if (fenceMatch) {
      const ch = fenceMatch[1][0] as '`' | '~';
      if (!inFencedCodeBlock) {
        inFencedCodeBlock = true;
        fenceChar = ch;
      } else if (fenceChar === ch) {
        inFencedCodeBlock = false;
        fenceChar = null;
      }
      return line;
    }

    if (inFencedCodeBlock) return line;

    return line.replace(/^(\s{0,3})(#{1,6})(?=[^\s#])/u, '$1$2 ');
  });

  return normalized.join('\n');
}

export function escapeMarkdownLinkText(text: string): string {
  if (!text) return text;
  return text.replace(/\\/g, '\\\\').replace(/\[/g, '\\[').replace(/\]/g, '\\]');
}

export function escapeMarkdownLinkDestination(url: string): string {
  if (!url) return url;
  try {
    return encodeURI(url);
  } catch {
    return url;
  }
}
