import { describe, expect, it } from 'vitest';
import {
  buildCitationExactQuoteTerms,
  buildCitationFallbackTerms,
  buildHighlightedParts,
  highlightExactCitationQuotes,
  pickCitationHighlightTerms
} from './citationHighlight';

describe('citationHighlight', () => {
  it('prefers exact quoted terms near citation index', () => {
    const answer = '根据资料，“混合检索支持 BM25 与向量融合”[1]，可以提升召回。';
    const exact = buildCitationExactQuoteTerms(answer);
    const fallback = buildCitationFallbackTerms(answer);
    const picked = pickCitationHighlightTerms(1, '系统说明：混合检索支持 BM25 与向量融合，并可重排。', exact, fallback);
    expect(picked.exact).toBe(true);
    expect(picked.terms[0]).toContain('混合检索支持 BM25 与向量融合');
  });

  it('falls back to keyword terms when exact quote is unavailable', () => {
    const answer = '该方案吞吐稳定，时延较低[2]。';
    const exact = buildCitationExactQuoteTerms(answer);
    const fallback = buildCitationFallbackTerms(answer);
    const picked = pickCitationHighlightTerms(2, '吞吐性能稳定，时延较低。', exact, fallback);
    expect(picked.exact).toBe(false);
    expect(picked.terms.length).toBeGreaterThan(0);
  });

  it('builds highlighted parts with matched terms', () => {
    const parts = buildHighlightedParts('混合检索支持 BM25 与向量融合', ['BM25', '向量融合']);
    expect(parts.some((p) => p.hit && p.text.includes('BM25'))).toBe(true);
    expect(parts.some((p) => p.hit && p.text.includes('向量融合'))).toBe(true);
  });

  it('wraps exact quoted citation text in assistant markdown', () => {
    const md = '结论：引用“混合检索支持 BM25 与向量融合”[1] 来说明召回增强。';
    const highlighted = highlightExactCitationQuotes(md, [
      { index: 1, snippet: '系统说明：混合检索支持 BM25 与向量融合，并可重排。' },
    ]);

    expect(highlighted).toContain('<span class="rounded bg-yellow-200 px-0.5">混合检索支持 BM25 与向量融合</span>');
  });

  it('skips inline code while allowing cited body quotes to highlight', () => {
    const md = [
      '代码示例：`“混合检索支持 BM25 与向量融合”[1]`',
      '',
      '正文引用“不是原文”[1]。',
    ].join('\n');
    const highlighted = highlightExactCitationQuotes(md, [
      { index: 1, snippet: '系统说明：混合检索支持 BM25 与向量融合，并可重排。' },
    ]);

    expect(highlighted).toContain('`“混合检索支持 BM25 与向量融合”[1]`');
    expect(highlighted).toContain('<span class="rounded bg-yellow-200 px-0.5">不是原文</span>');
  });

  it('matches quoted citations even when the model escapes straight quotes', () => {
    const md = '资料指出，\\"Qwen3-VL 拥有更加复杂的视觉融合机制和更大的模型容量\\"[1]。';
    const highlighted = highlightExactCitationQuotes(md, [
      { index: 1, snippet: 'Qwen3-VL 拥有更加复杂的视觉融合机制和更大的模型容量，在需要极致精度和长上下文的任务中仍有优势。' },
    ]);

    expect(highlighted).toContain('<span class="rounded bg-yellow-200 px-0.5">Qwen3-VL 拥有更加复杂的视觉融合机制和更大的模型容量</span>');
  });

  it('highlights cited quotes when the source has no snippet', () => {
    const md = '资料指出：“最新评测表明，Qwen3.5 小模型系列（尤其是 9B）在视觉理解任务上整体优于同级别的 Qwen3-VL 版本”[1]。';
    const highlighted = highlightExactCitationQuotes(md, [
      { index: 1, snippet: null },
    ]);

    expect(highlighted).toContain('<span class="rounded bg-yellow-200 px-0.5">最新评测表明，Qwen3.5 小模型系列（尤其是 9B）在视觉理解任务上整体优于同级别的 Qwen3-VL 版本</span>');
  });
});
