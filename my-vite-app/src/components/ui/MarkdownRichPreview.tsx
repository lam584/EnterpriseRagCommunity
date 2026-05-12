import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { MarkdownPreviewContainer, type MarkdownPreviewProps, useMarkdownPreviewSetup } from './markdownPreviewShared';

export default function MarkdownRichPreview(props: MarkdownPreviewProps) {
  const { markdown, className, components } = props;
  const { mergedComponents, normalizedMarkdown, sanitizeSchema } = useMarkdownPreviewSetup(markdown, components);

  return (
    <MarkdownPreviewContainer className={className}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[[rehypeRaw, { passThrough: ['element'] }], [rehypeSanitize, sanitizeSchema]]}
        components={mergedComponents}
      >
        {normalizedMarkdown}
      </ReactMarkdown>
    </MarkdownPreviewContainer>
  );
}