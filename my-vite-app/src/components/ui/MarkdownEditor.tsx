import { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import { resolveAssetUrl } from '../../utils/urlUtils';

export type MarkdownEditorValue = {
  markdown: string;
};

export default function MarkdownEditor(props: {
  value: MarkdownEditorValue;
  onChange: (next: MarkdownEditorValue) => void;
  onInsertImage?: (file: File) => Promise<string>; // returns markdown snippet, e.g. ![](url)
  onInsertAttachment?: (file: File) => Promise<string>; // returns markdown snippet, e.g. [name](url)
  placeholder?: string;
}) {
  const { value, onChange, onInsertImage, onInsertAttachment, placeholder } = props;
  const [tab, setTab] = useState<'edit' | 'preview'>('edit');

  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  const [live, setLive] = useState(value.markdown);

  useEffect(() => {
    setLive(value.markdown);
  }, [value.markdown]);

  const insertSnippetAtCursor = (snippet: string) => {
    const el = textareaRef.current;
    if (!el) {
      const next = `${live}${snippet}`;
      setLive(next);
      onChange({ markdown: next });
      return;
    }

    const start = el.selectionStart ?? live.length;
    const end = el.selectionEnd ?? live.length;
    const next = live.slice(0, start) + snippet + live.slice(end);
    setLive(next);
    onChange({ markdown: next });

    // restore focus + move caret to after inserted content
    requestAnimationFrame(() => {
      el.focus();
      const pos = start + snippet.length;
      el.setSelectionRange(pos, pos);
    });
  };

  const insertUploaded = async (file: File, kind: 'image' | 'attachment') => {
    const handler = kind === 'image' ? onInsertImage : onInsertAttachment;
    if (!handler) return;

    const snippet = await handler(file);
    // If inserting into the middle, avoid forcing newline; just use snippet.
    // Add a trailing newline when caret is at line end or doc end for nicer typing.
    const el = textareaRef.current;
    const needsNewline = !el || el.selectionStart === live.length || live.endsWith('\n');
    insertSnippetAtCursor(`${needsNewline ? '' : ''}${snippet}${needsNewline ? '\n' : ''}`);
  };

  const handlePaste = async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    // Prefer file paste (images/screenshots) and other attachments.
    // If no files, let normal paste happen.
    const dt = e.clipboardData;
    if (!dt) return;

    const files: File[] = [];

    // Chrome/Edge: dt.files works for pasted images
    if (dt.files && dt.files.length > 0) {
      for (const f of Array.from(dt.files)) files.push(f);
    }

    // Some browsers provide items instead
    if (files.length === 0 && dt.items) {
      for (const item of Array.from(dt.items)) {
        if (item.kind === 'file') {
          const f = item.getAsFile();
          if (f) files.push(f);
        }
      }
    }

    if (files.length === 0) return;

    e.preventDefault();

    for (const f of files) {
      const isImage = (f.type || '').startsWith('image/');
      // If no handlers exist, fall back to default paste.
      if (isImage && onInsertImage) {
        await insertUploaded(f, 'image');
      } else if (onInsertAttachment) {
        await insertUploaded(f, 'attachment');
      }
    }
  };

  const sanitizeSchema = useMemo(() => {
    // Allow a safe subset of common HTML tags used in markdown rendering.
    // rehype-sanitize will strip scripts/unsafe attributes.
    const schema = structuredClone(defaultSchema);
    const s = schema as unknown as { attributes?: Record<string, Array<unknown>> };
    s.attributes = s.attributes || {};
    s.attributes.code = [...(s.attributes.code || []), ['className']];
    s.attributes.span = [...(s.attributes.span || []), ['className']];
    return schema;
  }, []);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <div className="inline-flex rounded-md border border-gray-200 overflow-hidden">
          <button
            type="button"
            className={`px-3 py-1.5 text-sm ${tab === 'edit' ? 'bg-gray-100' : 'bg-white'} hover:bg-gray-50`}
            onClick={() => setTab('edit')}
          >
            编辑
          </button>
          <button
            type="button"
            className={`px-3 py-1.5 text-sm ${tab === 'preview' ? 'bg-gray-100' : 'bg-white'} hover:bg-gray-50`}
            onClick={() => setTab('preview')}
          >
            预览
          </button>
        </div>

        <div className="flex items-center gap-2">
          <label className="px-3 py-1.5 text-sm rounded-md border border-gray-200 bg-white hover:bg-gray-50 cursor-pointer">
            插入图片
            <input
              type="file"
              accept="image/*"
              className="hidden"
              onChange={async (e) => {
                const f = e.target.files?.[0];
                e.target.value = '';
                if (!f) return;
                await insertUploaded(f, 'image');
              }}
            />
          </label>
          <label className="px-3 py-1.5 text-sm rounded-md border border-gray-200 bg-white hover:bg-gray-50 cursor-pointer">
            插入附件
            <input
              type="file"
              className="hidden"
              onChange={async (e) => {
                const f = e.target.files?.[0];
                e.target.value = '';
                if (!f) return;
                await insertUploaded(f, 'attachment');
              }}
            />
          </label>
        </div>
      </div>

      {tab === 'edit' ? (
        <textarea
          ref={textareaRef}
          value={live}
          onPaste={handlePaste}
          onChange={(e) => {
            setLive(e.target.value);
            onChange({ markdown: e.target.value });
          }}
          placeholder={placeholder ?? '使用 Markdown 编写内容...'}
          rows={12}
          className="w-full font-mono text-sm border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      ) : (
        <div className="w-full border border-gray-200 rounded-md p-3 bg-white prose prose-sm max-w-none">
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
              img: ({ src, ...p }) => (
                <img
                  {...p}
                  src={resolveAssetUrl(src) ?? src}
                  className="max-w-full rounded border border-gray-200"
                />
              ),
              code: ({ className, children, ...p }) => (
                <code
                  {...p}
                  className={`${className ?? ''} px-1 py-0.5 bg-gray-100 rounded border border-gray-200`}
                >
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
            {live || ''}
          </ReactMarkdown>
        </div>
      )}

      <div className="text-xs text-gray-500">
        支持 Markdown（GFM）。提示：图片用 <code>![](...)</code>，附件用 <code>[name](url)</code>。
        也支持直接在编辑框里 <code>Ctrl+V</code> 粘贴图片/文件自动上传并插入。
      </div>
    </div>
  );
}
