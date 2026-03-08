import { describe, expect, it } from 'vitest';
import { parseTranslateMarkdown } from './translateOutputUtils';

describe('parseTranslateMarkdown', () => {
  it('returns empty markdown for nullish/blank input', () => {
    expect(parseTranslateMarkdown(null)).toEqual({ markdown: '' });
    expect(parseTranslateMarkdown(undefined)).toEqual({ markdown: '' });
    expect(parseTranslateMarkdown('')).toEqual({ markdown: '' });
    expect(parseTranslateMarkdown('   \n\t  ')).toEqual({ markdown: '' });
  });

  it('strips a fenced code block wrapper', () => {
    const res = parseTranslateMarkdown(['```md', 'hello', '```'].join('\n'));
    expect(res).toEqual({ markdown: 'hello' });
  });

  it('decodes a quoted JSON string then parses as normal', () => {
    const raw = JSON.stringify(['```md', 'hello', '```'].join('\n'));
    const res = parseTranslateMarkdown(raw);
    expect(res).toEqual({ markdown: 'hello' });
  });

  it('ignores invalid quoted json strings', () => {
    const res = parseTranslateMarkdown('"\\uZZZZ"');
    expect(res).toEqual({ markdown: '"\\uZZZZ"' });
  });

  it('treats too-short or non-string quoted json as plain text', () => {
    expect(parseTranslateMarkdown('"')).toEqual({ markdown: '"' });
    const orig = JSON.parse;
    try {
      (JSON as any).parse = () => 123;
      expect(parseTranslateMarkdown('"hello"')).toEqual({ markdown: '"hello"' });
    } finally {
      (JSON as any).parse = orig;
    }
  });

  it('parses key-value blocks with title and markdown', () => {
    const res = parseTranslateMarkdown(['title： 标题 ', 'markdown:  正文  '].join('\n'));
    expect(res).toEqual({ title: '标题', markdown: '正文' });
  });

  it('returns undefined title when key-value title is blank but markdown exists', () => {
    const res = parseTranslateMarkdown(['title:   ', 'markdown: ok'].join('\n'));
    expect(res).toEqual({ title: undefined, markdown: 'ok' });
  });

  it('treats empty key-value blocks as plain markdown', () => {
    const res = parseTranslateMarkdown(['title:   ', '', 'markdown:   '].join('\n'));
    expect(res).toEqual({ markdown: ['title:   ', '', 'markdown:   '].join('\n').trim() });
  });

  it('parses JSON objects and trims title/markdown', () => {
    const res = parseTranslateMarkdown(JSON.stringify({ title: ' T ', markdown: ' M ' }));
    expect(res).toEqual({ title: 'T', markdown: 'M' });
  });

  it('uses translatedMarkdown when markdown is missing but title exists', () => {
    const res = parseTranslateMarkdown(JSON.stringify({ title: 'T', translatedMarkdown: 'M' }));
    expect(res).toEqual({ title: 'T', markdown: 'M' });
  });

  it('falls back to json-like extraction when JSON.parse fails', () => {
    const input = `{title: 'T', markdown: 'a\\n\\u4e2d\\t\\'\\\\'}`;
    const res = parseTranslateMarkdown(input);
    expect(res).toEqual({ title: 'T', markdown: "a\n中\t'\\" });
  });

  it('falls back when JSON.parse succeeds but title/markdown are blank or invalid', () => {
    const res = parseTranslateMarkdown(JSON.stringify({ title: '   ', markdown: '   ' }));
    expect(res).toEqual({ markdown: JSON.stringify({ title: '   ', markdown: '   ' }) });

    const res2 = parseTranslateMarkdown(JSON.stringify({ title: 1, markdown: 2 } as any));
    expect(res2).toEqual({ title: '1', markdown: '2' });
  });

  it('json-like extraction decodes more escapes and supports raw values', () => {
    const input = `{markdown: 'a\\r\\n\\t\\"\\'\\\\\\/\\b\\f\\x'}`;
    const res = parseTranslateMarkdown(input);
    expect(res.markdown).toBe("a\r\n\t\"'\\/\b\fx");

    const res2 = parseTranslateMarkdown('{markdown: 123}');
    expect(res2).toEqual({ markdown: '123' });
  });

  it('json-like extraction handles invalid unicode and empty raw values', () => {
    const res = parseTranslateMarkdown("{markdown: 'a\\\\uZZZZ'}");
    expect(res).toEqual({ title: undefined, markdown: 'a\\uZZZZ' });

    const res2 = parseTranslateMarkdown('{markdown: }');
    expect(res2).toEqual({ markdown: '{markdown: }' });
  });

  it('json-like extraction tolerates trailing backslash', () => {
    const res = parseTranslateMarkdown("{markdown: 'abc" + '\\');
    expect(res).toEqual({ markdown: 'abc' });
  });

  it('falls back to returning trimmed input markdown', () => {
    const res = parseTranslateMarkdown('  plain markdown  ');
    expect(res).toEqual({ markdown: 'plain markdown' });
  });
});
