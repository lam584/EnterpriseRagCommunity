export type ThinkSplit = { hasThink: boolean; thinkClosed: boolean; think: string; main: string };

const THINK_BLOCK_RE = /<think\b[^>]*>([\s\S]*?)<\/think>\s*/gi;
const THINK_OPEN_RE = /<think\b[^>]*>/i;
const THINK_CLOSE_RE = /<\/think>/i;
const THINK_BLOCK_ESC_RE = /&lt;think&gt;([\s\S]*?)&lt;\/think&gt;\s*/gi;
const THINK_OPEN_ESC_RE = /&lt;think&gt;/i;

const REASONING_MARKER_RE = /\b(reasoning_content)\b/gi;

export function stripThinkBlocks(raw: string): string {
  let s = raw ?? '';
  s = s.replace(THINK_BLOCK_RE, '');
  s = s.replace(THINK_BLOCK_ESC_RE, '');
  s = s.replace(REASONING_MARKER_RE, '');

  const open = THINK_OPEN_RE.exec(s);
  if (open) return s.slice(0, open.index);
  const openEsc = THINK_OPEN_ESC_RE.exec(s);
  if (openEsc) return s.slice(0, openEsc.index);
  return s;
}

export function splitThinkText(raw: string): ThinkSplit {
  let s = raw ?? '';
  s = s.replace(REASONING_MARKER_RE, '');

  const open = THINK_OPEN_RE.exec(s);
  if (!open) return { hasThink: false, thinkClosed: true, think: '', main: s };

  const thinkStart = open.index + open[0].length;
  const close = THINK_CLOSE_RE.exec(s.slice(thinkStart));
  if (!close) return { hasThink: true, thinkClosed: false, think: s.slice(thinkStart), main: s.slice(0, open.index) };

  const thinkParts: string[] = [];
  const main = s.replace(THINK_BLOCK_RE, (_m, inner: string) => {
    thinkParts.push(inner);
    return '';
  });
  return { hasThink: true, thinkClosed: true, think: thinkParts.join('\n\n'), main };
}

