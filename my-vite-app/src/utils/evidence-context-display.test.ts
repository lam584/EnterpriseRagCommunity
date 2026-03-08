import { describe, expect, it } from 'vitest';
import { expandEvidenceContext, resolveEvidenceContextSentences } from './evidence-context-display';

describe('evidence-context-display', () => {
  it('extracts one previous and next sentence around anchors', () => {
    const sourceText = '第一句。第二句 before anchor 命中词 after anchor 第三句。第四句。';
    const result = resolveEvidenceContextSentences({
      beforeText: '第二句 before anchor',
      afterText: 'after anchor 第三句。',
      sourceText,
    });

    expect(result.extraBeforeText).toBe('第一句。');
    expect(result.beforeText).toBe('第二句 before anchor');
    expect(result.afterText).toBe('after anchor 第三句。');
    expect(result.extraAfterText).toBe('第四句。');
  });

  it('keeps original anchors when source text cannot be matched', () => {
    const result = resolveEvidenceContextSentences({
      beforeText: 'before anchor',
      afterText: 'after anchor',
      sourceText: 'completely unrelated source',
    });

    expect(result.extraBeforeText).toBe('');
    expect(result.extraAfterText).toBe('');
    expect(result.beforeText).toBe('before anchor');
    expect(result.afterText).toBe('after anchor');
  });

  it('preserves legacy expandEvidenceContext behaviour with approximate truncation', () => {
    const sourceText = 'alpha beta gamma delta epsilon. before anchor target after anchor zeta eta theta iota kappa lambda mu nu xi omicron.';
    const result = expandEvidenceContext({
      beforeText: 'before anchor',
      afterText: 'after anchor',
      sourceText,
      maxExtraTokens: 3,
    });

    expect(result.beforeText).toBe('alpha beta gamma…\nbefore anchor');
    expect(result.afterText).toBe('after anchor\nzeta eta theta…');
  });
});