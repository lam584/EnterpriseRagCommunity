import { describe, expect, it } from 'vitest';
import { splitThinkText, stripThinkBlocks } from './thinkTags';

describe('thinkTags', () => {
  it('stripThinkBlocks removes closed <think> blocks', () => {
    expect(stripThinkBlocks('<think>\n\n</think>\n\nok')).toBe('ok');
    expect(stripThinkBlocks('a<think>xx</think>b')).toBe('ab');
  });

  it('stripThinkBlocks removes multiple blocks', () => {
    expect(stripThinkBlocks('a<think>x</think>b<think>y</think>c')).toBe('abc');
  });

  it('stripThinkBlocks removes unterminated <think> tail', () => {
    expect(stripThinkBlocks('a<think>incomplete')).toBe('a');
  });

  it('stripThinkBlocks supports attribute-style open tag', () => {
    expect(stripThinkBlocks('a<think class="t">x</think>b')).toBe('ab');
  });

  it('stripThinkBlocks removes escaped tags', () => {
    expect(stripThinkBlocks('a&lt;think&gt;x&lt;/think&gt;b')).toBe('ab');
  });

  it('splitThinkText returns main without tags and combined think', () => {
    const r = splitThinkText('a<think>x</think>b<think>y</think>c');
    expect(r.hasThink).toBe(true);
    expect(r.thinkClosed).toBe(true);
    expect(r.think).toBe('x\n\ny');
    expect(r.main).toBe('abc');
  });

  it('splitThinkText marks unclosed think', () => {
    const r = splitThinkText('a<think>x');
    expect(r.hasThink).toBe(true);
    expect(r.thinkClosed).toBe(false);
    expect(r.main).toBe('a');
    expect(r.think).toBe('x');
  });
});
