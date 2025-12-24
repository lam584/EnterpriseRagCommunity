import { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import { resolveAssetUrl } from '../../utils/urlUtils';

export default function MarkdownPreview(props: { markdown: string }) {
  const { markdown } = props;

  const sanitizeSchema = useMemo(() => {
    // Align with MarkdownEditor preview rules.
    const schema = structuredClone(defaultSchema);
    // rehype-sanitize schema type is intentionally flexible; we only extend known fields.
    const s = schema as unknown as {
      attributes?: Record<string, Array<unknown>>;
    };

    s.attributes = s.attributes || {};
    s.attributes.code = [...(s.attributes.code || []), ['className']];
    s.attributes.span = [...(s.attributes.span || []), ['className']];
    return schema;
  }, []);

  return (
    <div className="prose prose-sm max-w-none">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[
          rehypeHighlight,
          // Allow HTML in markdown but sanitize it.
          [rehypeRaw, { passThrough: ['element'] }],
          [rehypeSanitize, sanitizeSchema],
        ]}
        components={{
          a: ({ href, ...p }) => (
            <a
              {...p}
              href={resolveAssetUrl(href) ?? href}
              target="_blank"
              rel="noreferrer"
              className="text-blue-600 hover:underline"
            />
          ),
          img: ({ alt, src, ...p }) => (
            <img
              {...p}
              src={resolveAssetUrl(src) ?? src}
              alt={typeof alt === 'string' && alt.trim() ? alt : 'image'}
              className="max-w-full rounded border border-gray-200"
            />
          ),
          code: ({ className, children, ...p }) => (
            <code {...p} className={`${className ?? ''} px-1 py-0.5 bg-gray-100 rounded border border-gray-200`}>
              {children}
            </code>
          ),
          pre: ({ children, ...p }) => (
            <pre {...p} className="rounded bg-gray-50 border border-gray-200 p-3 overflow-auto">
              {children}
            </pre>
          ),
        }}
      >
        {markdown || ''}
      </ReactMarkdown>
    </div>
  );
}
