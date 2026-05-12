import { Fragment, useMemo } from 'react';
import type { MarkdownPreviewProps } from './markdownPreviewShared';

export default function PlainTextPreview(props: MarkdownPreviewProps) {
  const { markdown, className } = props;
  const paragraphs = useMemo(
    () =>
      markdown
        .split(/\r?\n\s*\r?\n/)
        .map((part) => part.trim())
        .filter(Boolean),
    [markdown],
  );

  return (
    <div className={`max-w-none text-sm text-gray-900 ${className ?? ''}`}>
      {paragraphs.map((paragraph, index) => {
        const lines = paragraph.split(/\r?\n/);
        return (
          <p key={`${index}-${paragraph.slice(0, 12)}`} className="my-2 whitespace-pre-wrap leading-7 text-gray-800">
            {lines.map((line, lineIndex) => (
              <Fragment key={`${index}-${lineIndex}`}>
                {line}
                {lineIndex < lines.length - 1 ? <br /> : null}
              </Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}