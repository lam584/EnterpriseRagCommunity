import { describe, expect, it } from 'vitest';
import { normalizeMarkdownForPreview } from './markdownUtils';

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
});
