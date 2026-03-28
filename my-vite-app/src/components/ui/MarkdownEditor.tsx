import { Children, cloneElement, isValidElement, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import { Check, Copy } from 'lucide-react';
import { resolveAssetUrl } from '../../utils/urlUtils';
import { normalizeMarkdownForPreview } from '../../utils/markdownUtils';

export type MarkdownEditorValue = {
  markdown: string;
};

function nodeText(node: ReactNode): string {
  if (node == null) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map((x) => nodeText(x)).join('');
  if (isValidElement<{ children?: ReactNode }>(node)) return nodeText(node.props.children);
  return '';
}

function extractCodeTextFromPreChildren(children: ReactNode): string {
  if (children == null) return '';
  const maybeOnlyChild = Array.isArray(children) && children.length === 1 ? children[0] : children;
  if (isValidElement<{ children?: ReactNode }>(maybeOnlyChild) && maybeOnlyChild.type === 'code') {
    return nodeText(maybeOnlyChild.props.children);
  }
  return nodeText(children);
}

function trimLeadingWhitespaceNodes(nodes: ReactNode[]): ReactNode[] {
  let i = 0;
  while (i < nodes.length) {
    const n = nodes[i];
    if (typeof n === 'string' && n.trim().length === 0) {
      i += 1;
      continue;
    }
    break;
  }
  return nodes.slice(i);
}

function parseAdmonitionFromBlockquoteChildren(
  children: ReactNode,
): { kind: string; title: string; content: ReactNode } | null {
  const nodes = Children.toArray(children);
  const firstIndex = nodes.findIndex((n) => typeof n !== 'string' || n.trim().length > 0);
  if (firstIndex < 0) return null;

  const first = nodes[firstIndex];
  if (!isValidElement<{ children?: ReactNode }>(first)) return null;

  const firstParts = Children.toArray(first.props.children);
  const brIndex = firstParts.findIndex((n) => isValidElement(n) && n.type === 'br');
  const lineParts = brIndex >= 0 ? firstParts.slice(0, brIndex) : firstParts;
  const lineText = nodeText(lineParts).trim();

  const match = lineText.match(/^\[!(TIP|NOTE|IMPORTANT|WARNING|CAUTION)\](?:\s+(.*))?$/i);
  if (!match) return null;

  const kind = match[1].toUpperCase();
  const title = (match[2] ?? '').trim() || kind;

  const restParts = brIndex >= 0 ? trimLeadingWhitespaceNodes(firstParts.slice(brIndex + 1)) : [];
  const out = [...nodes];
  if (restParts.length > 0) {
    out[firstIndex] = cloneElement(first, { children: restParts });
  } else {
    out.splice(firstIndex, 1);
  }

  const contentNodes = trimLeadingWhitespaceNodes(out);
  return { kind, title, content: contentNodes };
}

function CodeBlockPre(props: React.ComponentPropsWithoutRef<'pre'>) {
  const { children, className, ...p } = props;
  const [copied, setCopied] = useState(false);
  const codeText = extractCodeTextFromPreChildren(children);
  const canCopy = codeText.trim().length > 0;

  const handleCopy = async () => {
    if (!canCopy) return;
    try {
      await navigator.clipboard.writeText(codeText);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
    }
  };

  return (
    <div className="relative group rounded bg-gray-50 border border-gray-200 overflow-hidden">
      <button
        type="button"
        aria-label="复制代码"
        title="复制"
        onClick={() => void handleCopy()}
        disabled={!canCopy}
        className="absolute top-2 right-2 inline-flex items-center justify-center rounded-md border border-gray-200 bg-white/90 p-1.5 text-gray-600 hover:bg-white hover:text-gray-900 disabled:cursor-not-allowed disabled:opacity-50 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity"
      >
        {copied ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
      </button>
      <pre {...p} className={`${className ?? ''} p-3 pr-12 overflow-auto bg-transparent`}>
        {children}
      </pre>
    </div>
  );
}

export default function MarkdownEditor(props: {
  value: MarkdownEditorValue;
  onChange: (next: MarkdownEditorValue) => void;
  onInsertImage?: (file: File) => Promise<string>; // returns markdown snippet, e.g. ![](url)
  onInsertAttachment?: (file: File) => Promise<string>; // returns markdown snippet, e.g. [name](url)
  fileAccept?: string;
  placeholder?: string;
  onBoxHeightChange?: (heightPx: number) => void;
  editorHeightPx?: number;
  toolbarAfterTabs?: ReactNode;
    readOnly?: boolean;
}) {
  const {
    value,
    onChange,
    onInsertImage,
    onInsertAttachment,
    fileAccept,
    placeholder,
    onBoxHeightChange,
    editorHeightPx,
    toolbarAfterTabs,
      readOnly,
  } = props;
  const [tab, setTab] = useState<'edit' | 'preview'>('edit');

  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const [boxHeightPx, setBoxHeightPx] = useState<number | null>(null);

  const [live, setLive] = useState(value.markdown);
  const [uploadError, setUploadError] = useState<string | null>(null);

  useEffect(() => {
    setLive(value.markdown);
  }, [value.markdown]);

  useEffect(() => {
    if (tab !== 'edit') return;
    const el = textareaRef.current;
    if (!el) return;

    const update = () => {
      const next = Math.round(el.getBoundingClientRect().height);
      if (!Number.isFinite(next) || next <= 0) return;
      setBoxHeightPx(next);
      onBoxHeightChange?.(next);
    };

    update();

    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => update());
    ro.observe(el);
    return () => ro.disconnect();
  }, [tab, onBoxHeightChange]);

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

    setUploadError(null);
    try {
      const snippet = await handler(file);
      const el = textareaRef.current;
      const needsNewline = !el || el.selectionStart === live.length || live.endsWith('\n');
      insertSnippetAtCursor(`${needsNewline ? '' : ''}${snippet}${needsNewline ? '\n' : ''}`);
    } catch (e: unknown) {
      const msg =
        e && typeof e === 'object' && 'message' in e && typeof (e as { message?: unknown }).message === 'string'
          ? (e as { message: string }).message
          : '上传失败';
      setUploadError(msg);
    }
  };

  const handlePaste = async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
      if (readOnly) return;
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

  const effectiveBoxHeightPx = boxHeightPx ?? editorHeightPx ?? null;
  const normalizedLiveForPreview = useMemo(() => normalizeMarkdownForPreview(live), [live]);

  return (
    <div className="space-y-2">
      {uploadError ? (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{uploadError}</div>
      ) : null}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 flex-wrap">
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
          {toolbarAfterTabs ? <div className="flex items-center gap-3">{toolbarAfterTabs}</div> : null}
        </div>

        <div className="flex items-center gap-2">
            <label
                className={`px-3 py-1.5 text-sm rounded-md border border-gray-200 bg-white ${
                    readOnly ? 'cursor-not-allowed opacity-60' : 'hover:bg-gray-50 cursor-pointer'
                }`}
            >
            插入文件
            <input
              type="file"
              className="hidden"
              accept={fileAccept}
              disabled={readOnly}
              onChange={async (e) => {
                const f = e.target.files?.[0];
                e.target.value = '';
                if (!f) return;
                const isImage = (f.type || '').startsWith('image/');
                if (isImage && onInsertImage) {
                  await insertUploaded(f, 'image');
                } else {
                  await insertUploaded(f, 'attachment');
                }
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
          readOnly={readOnly}
          onChange={(e) => {
              if (readOnly) return;
            setLive(e.target.value);
            onChange({ markdown: e.target.value });
          }}
          placeholder={placeholder ?? '使用 Markdown 编写内容...'}
          rows={12}
          className={`w-full font-mono text-sm border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            editorHeightPx ? 'resize-none' : ''
          }`}
          style={editorHeightPx ? { height: `${editorHeightPx}px` } : undefined}
        />
      ) : (
        <div
          className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white overflow-auto"
          style={effectiveBoxHeightPx ? { height: `${effectiveBoxHeightPx}px` } : undefined}
        >
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
                blockquote: ({ children, ...p }) => {
                  const parsed = parseAdmonitionFromBlockquoteChildren(children);
                  if (!parsed) return <blockquote {...p}>{children}</blockquote>;

                  const { kind, title, content } = parsed;
                  const stylesByKind: Record<
                    string,
                    { bg: string; border: string; titleText: string; bodyText: string }
                  > = {
                    TIP: {
                      bg: 'bg-blue-50',
                      border: 'border-blue-200',
                      titleText: 'text-blue-900',
                      bodyText: 'text-blue-900',
                    },
                    NOTE: { bg: 'bg-gray-50', border: 'border-gray-200', titleText: 'text-gray-900', bodyText: 'text-gray-800' },
                    IMPORTANT: {
                      bg: 'bg-purple-50',
                      border: 'border-purple-200',
                      titleText: 'text-purple-900',
                      bodyText: 'text-purple-900',
                    },
                    WARNING: {
                      bg: 'bg-amber-50',
                      border: 'border-amber-200',
                      titleText: 'text-amber-900',
                      bodyText: 'text-amber-900',
                    },
                    CAUTION: { bg: 'bg-red-50', border: 'border-red-200', titleText: 'text-red-900', bodyText: 'text-red-900' },
                  };

                  const s = stylesByKind[kind] ?? stylesByKind.NOTE;

                  return (
                    <blockquote
                      {...p}
                      className={`flow-root not-prose my-3 rounded-md border ${s.border} border-l-4 ${s.bg} px-4 py-3`}
                    >
                      <div className={`mb-2 text-xs font-semibold tracking-wide uppercase ${s.titleText}`}>{title}</div>
                      <div className={`${s.bodyText} [&>*:first-child]:mt-0 [&>*:last-child]:mb-0`}>{content}</div>
                    </blockquote>
                  );
                },
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
                  <CodeBlockPre {...p}>{children}</CodeBlockPre>
                ),
              }}
            >
              {normalizedLiveForPreview}
            </ReactMarkdown>
          </div>
        </div>
      )}

      <div className="text-xs text-gray-500">
        支持 Markdown（GFM）。提示：标题用 <code># 标题</code>（# 后建议加空格），图片用 <code>![](...)</code>，附件用 <code>[name](url)</code>。
        也支持直接在编辑框里 <code>Ctrl+V</code> 粘贴图片/文件自动上传并插入。
      </div>
    </div>
  );
}
