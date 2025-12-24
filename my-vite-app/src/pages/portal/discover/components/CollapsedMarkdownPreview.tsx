import { useMemo, useState } from 'react';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';

export type CollapsedMarkdownPreviewProps = {
  markdown: string;
  /** Collapsed height in px. */
  collapsedHeight?: number;
};

export default function CollapsedMarkdownPreview({ markdown, collapsedHeight = 220 }: CollapsedMarkdownPreviewProps) {
  const [expanded, setExpanded] = useState(false);

  const shouldShowToggle = useMemo(() => {
    // Rough heuristic: if very short, don't show toggle.
    return (markdown || '').trim().length > 400;
  }, [markdown]);

  return (
    <div>
      <div className={expanded ? '' : 'relative'}>
        <div style={expanded ? undefined : { maxHeight: collapsedHeight, overflow: 'hidden' }}>
          <MarkdownPreview markdown={markdown || ''} />
        </div>

        {!expanded && shouldShowToggle ? (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-white to-transparent" />
        ) : null}
      </div>

      {shouldShowToggle ? (
        <button
          type="button"
          className="mt-2 text-sm text-blue-600 hover:underline"
          onClick={(e) => {
            e.stopPropagation();
            setExpanded((v) => !v);
          }}
        >
          {expanded ? '收起' : '显示更多'}
        </button>
      ) : null}
    </div>
  );
}
