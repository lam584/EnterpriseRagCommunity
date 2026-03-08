import { describe, expect, it } from 'vitest';
import { escapeMarkdownLinkDestination, escapeMarkdownLinkText, normalizeMarkdownForPreview } from './markdownUtils';

describe('normalizeMarkdownForPreview', () => {
  it('inserts a space after ATX heading markers when missing', () => {
    expect(normalizeMarkdownForPreview('#标题')).toBe('# 标题');
    expect(normalizeMarkdownForPreview('##标题')).toBe('## 标题');
    expect(normalizeMarkdownForPreview('   ###标题')).toBe('   ### 标题');
  });

  it('does not touch already-correct headings', () => {
    expect(normalizeMarkdownForPreview('# 标题')).toBe('# 标题');
    expect(normalizeMarkdownForPreview('## 标题')).toBe('## 标题');
  });

  it('does not modify fenced code blocks', () => {
    const input = ['```ts', '#标题', 'const x = 1;', '```', '', '#标题2'].join('\n');
    const output = normalizeMarkdownForPreview(input);
    expect(output).toBe(['```ts', '#标题', 'const x = 1;', '```', '', '# 标题2'].join('\n'));
  });

  it('supports ~~~ fenced code blocks', () => {
    const input = ['~~~', '#标题', '~~~', '#标题2'].join('\n');
    expect(normalizeMarkdownForPreview(input)).toBe(['~~~', '#标题', '~~~', '# 标题2'].join('\n'));
  });
});

describe('escapeMarkdownLinkText', () => {
  it('returns empty string as-is', () => {
    expect(escapeMarkdownLinkText('')).toBe('');
  });

  it('escapes square brackets to keep markdown links valid', () => {
    expect(escapeMarkdownLinkText('[书名] by a.epub')).toBe('\\[书名\\] by a.epub');
  });

  it('escapes backslashes before other escapes', () => {
    expect(escapeMarkdownLinkText(String.raw`a\[b\]c`)).toBe(String.raw`a\\\[b\\\]c`);
  });
});

describe('escapeMarkdownLinkDestination', () => {
  it('encodes urls and tolerates invalid input', () => {
    expect(escapeMarkdownLinkDestination('')).toBe('');
    expect(escapeMarkdownLinkDestination('https://example.com/a b')).toBe('https://example.com/a%20b');
    expect(escapeMarkdownLinkDestination('\uD800')).toBe('\uD800');
  });
});
