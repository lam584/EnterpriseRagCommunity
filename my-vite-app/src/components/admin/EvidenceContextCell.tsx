import { useMemo } from 'react';
import { useModerationChunkContentPreview } from '../../hooks/useModerationChunkContentPreview';
import { useTokenizerLimitedText } from '../../hooks/useTokenizerLimitedText';
import { resolveEvidenceContextSentences } from '../../utils/evidence-context-display';

type EvidenceContextCellProps = {
  description?: string;
  beforeText?: string;
  mainText?: string;
  afterText?: string;
  sourceText?: string | null;
  chunkId?: number | null;
};

function cleanText(v: string | null | undefined): string {
  return (v ?? '').trim();
}

export default function EvidenceContextCell({ description, beforeText, mainText, afterText, sourceText, chunkId }: EvidenceContextCellProps) {
  const sourceText0 = cleanText(sourceText);
  const { data: preview } = useModerationChunkContentPreview(chunkId ?? null, !sourceText0 && chunkId != null);
  const previewText = typeof preview?.text === 'string' ? preview.text : '';
  const resolved = useMemo(
    () =>
      resolveEvidenceContextSentences({
        beforeText,
        afterText,
        sourceText: sourceText0 || previewText,
      }),
    [afterText, beforeText, previewText, sourceText0],
  );
  const extraBefore = useTokenizerLimitedText(resolved.extraBeforeText, 25);
  const extraAfter = useTokenizerLimitedText(resolved.extraAfterText, 25);
  const descriptionText = cleanText(description);
  const beforeAnchorText = cleanText(resolved.beforeText);
  const afterAnchorText = cleanText(resolved.afterText);
  const mainAnchorText = cleanText(mainText);
  const beforeExtraText = cleanText(extraBefore.text);
  const afterExtraText = cleanText(extraAfter.text);

  if (!descriptionText && !beforeAnchorText && !mainAnchorText && !afterAnchorText && !beforeExtraText && !afterExtraText) {
    return <span className="text-xs text-gray-400">—</span>;
  }

  return (
    <div className="space-y-1">
      {descriptionText ? <div className="text-xs text-gray-700 whitespace-pre-wrap break-words">{descriptionText}</div> : null}
      {beforeAnchorText || mainAnchorText || afterAnchorText || beforeExtraText || afterExtraText ? (
        <div className="rounded border bg-white p-2 space-y-2 max-h-[220px] overflow-auto">
          {beforeExtraText ? <div className="text-xs text-gray-500 whitespace-pre-wrap break-words">{beforeExtraText}</div> : null}
          {beforeAnchorText ? <div className="text-xs whitespace-pre-wrap break-words">{beforeAnchorText}</div> : null}
          {mainAnchorText ? (
            <div className="rounded border border-amber-200 bg-amber-50 p-2 overflow-auto">
              <div className="text-xs whitespace-pre-wrap break-words">{mainAnchorText}</div>
            </div>
          ) : null}
          {afterAnchorText ? <div className="text-xs whitespace-pre-wrap break-words">{afterAnchorText}</div> : null}
          {afterExtraText ? <div className="text-xs text-gray-500 whitespace-pre-wrap break-words">{afterExtraText}</div> : null}
        </div>
      ) : null}
    </div>
  );
}