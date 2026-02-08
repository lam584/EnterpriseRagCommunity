export type AiComposeChannel = 'chat' | 'post';

export type AiComposeChannelRouterState = {
  mode: AiComposeChannel | null;
  remainder: string;
  post: string;
  chat: string;
  hasPost: boolean;
};

export function createAiComposeChannelRouterState(): AiComposeChannelRouterState {
  return { mode: null, remainder: '', post: '', chat: '', hasPost: false };
}

const TAG_NAMES = ['post', 'chat'] as const;
type TagName = (typeof TAG_NAMES)[number];

function isTagName(x: string): x is TagName {
  return (TAG_NAMES as readonly string[]).includes(x);
}

type TagMatch = { idx: number; len: number; name: TagName; close: boolean };

function startsWithIgnoreCase(text: string, prefix: string, at: number): boolean {
  return text.slice(at, at + prefix.length).toLowerCase() === prefix.toLowerCase();
}

function readTagName(text: string, startAt: number): { name: TagName; next: number } | null {
  let j = startAt;
  while (j < text.length && /\s/.test(text[j] ?? '')) j += 1;
  let k = j;
  while (k < text.length && /[A-Za-z]/.test(text[k] ?? '')) k += 1;
  if (k <= j) return null;
  const name = text.slice(j, k).toLowerCase();
  if (!isTagName(name)) return null;
  return { name, next: k };
}

function tryParseTagAt(text: string, idx: number): TagMatch | null {
  const t = String(text ?? '');
  if (!t) return null;

  if (startsWithIgnoreCase(t, '&lt;', idx)) {
    let j = idx + 4;
    let close = false;
    if ((t[j] ?? '') === '/') {
      close = true;
      j += 1;
    }
    const nameRes = readTagName(t, j);
    if (!nameRes) return null;
    const end = t.toLowerCase().indexOf('&gt;', nameRes.next);
    if (end < 0) return null;
    return { idx, len: end + 4 - idx, name: nameRes.name, close };
  }

  const ch = t[idx] ?? '';
  if (ch === '＜') {
    let j = idx + 1;
    let close = false;
    if ((t[j] ?? '') === '/') {
      close = true;
      j += 1;
    }
    const nameRes = readTagName(t, j);
    if (!nameRes) return null;
    const end = t.indexOf('＞', nameRes.next);
    if (end < 0) return null;
    return { idx, len: end + 1 - idx, name: nameRes.name, close };
  }

  if (ch === '<') {
    let j = idx + 1;
    let close = false;
    if ((t[j] ?? '') === '/') {
      close = true;
      j += 1;
    }
    const nameRes = readTagName(t, j);
    if (!nameRes) return null;
    const end = t.indexOf('>', nameRes.next);
    if (end < 0) return null;
    return { idx, len: end + 1 - idx, name: nameRes.name, close };
  }

  return null;
}

function findNextTag(text: string, opts: { close?: boolean; name?: TagName }): TagMatch | null {
  const t = String(text ?? '');
  if (!t) return null;
  for (let i = 0; i < t.length; i += 1) {
    const c = t[i] ?? '';
    if (c !== '<' && c !== '＜' && c !== '&') continue;
    const m = tryParseTagAt(t, i);
    if (!m) continue;
    if (opts.name && m.name !== opts.name) continue;
    if (opts.close != null && m.close !== opts.close) continue;
    return m;
  }
  return null;
}

function buildPrefixes(): { open: string[]; close: string[] } {
  const openStarts = ['<', '＜', '&lt;'] as const;
  const closeStarts = ['</', '＜/', '&lt;/'] as const;
  const open: string[] = [];
  const close: string[] = [];
  for (const s of openStarts) {
    for (let i = 1; i <= s.length; i += 1) open.push(s.slice(0, i));
    for (const n of TAG_NAMES) {
      const base = `${s}${n}`;
      for (let i = 1; i <= base.length; i += 1) open.push(base.slice(0, i));
    }
  }
  for (const s of closeStarts) {
    for (let i = 1; i <= s.length; i += 1) close.push(s.slice(0, i));
    for (const n of TAG_NAMES) {
      const base = `${s}${n}`;
      for (let i = 1; i <= base.length; i += 1) close.push(base.slice(0, i));
    }
  }
  return { open, close };
}

const PREFIXES = buildPrefixes();

function keepPossiblePrefix(text: string, prefixes: string[], maxLen: number): number {
  const t = String(text ?? '');
  if (!t) return 0;
  const max = Math.min(maxLen, t.length);
  const lowerPrefixes = prefixes.map((p) => p.toLowerCase());
  for (let k = max; k >= 1; k -= 1) {
    const suffix = t.slice(-k).toLowerCase();
    for (const p of lowerPrefixes) {
      if (p.startsWith(suffix)) return k;
    }
  }
  return 0;
}

export function routeAiComposeDelta(
  state: AiComposeChannelRouterState,
  delta: string
): { postChanged: boolean; chatChanged: boolean } {
  const d = String(delta ?? '');
  if (!d) return { postChanged: false, chatChanged: false };
  state.remainder += d;

  let remainder = state.remainder;
  let mode = state.mode;
  let postChanged = false;
  let chatChanged = false;

  const appendTo = (target: AiComposeChannel, chunk: string) => {
    if (!chunk) return;
    if (target === 'post') {
      state.post += chunk;
      postChanged = true;
      state.hasPost = true;
    } else {
      state.chat += chunk;
      chatChanged = true;
    }
  };

  while (remainder) {
    if (!mode) {
      const openTag = findNextTag(remainder, { close: false });
      if (!openTag) {
        const keep = keepPossiblePrefix(remainder, PREFIXES.open, 16);
        const flushLen = Math.max(0, remainder.length - keep);
        if (flushLen > 0) appendTo('chat', remainder.slice(0, flushLen));
        remainder = keep > 0 ? remainder.slice(-keep) : '';
        break;
      }

      if (openTag.idx > 0) {
        appendTo('chat', remainder.slice(0, openTag.idx));
        remainder = remainder.slice(openTag.idx);
      }

      const m = tryParseTagAt(remainder, 0);
      if (m && !m.close) {
        mode = m.name === 'chat' ? 'chat' : 'post';
        remainder = remainder.slice(m.len);
        continue;
      }
      appendTo('chat', remainder.slice(0, 1));
      remainder = remainder.slice(1);
      continue;
    }

    const closeTag = findNextTag(remainder, { close: true, name: mode === 'chat' ? 'chat' : 'post' });
    if (!closeTag) {
      const keep = keepPossiblePrefix(remainder, PREFIXES.close, 16);
      const flushLen = Math.max(0, remainder.length - keep);
      if (flushLen > 0) appendTo(mode, remainder.slice(0, flushLen));
      remainder = keep > 0 ? remainder.slice(-keep) : '';
      break;
    }

    appendTo(mode, remainder.slice(0, closeTag.idx));
    remainder = remainder.slice(closeTag.idx + closeTag.len);
    mode = null;
  }

  state.remainder = remainder;
  state.mode = mode;
  return { postChanged, chatChanged };
}
