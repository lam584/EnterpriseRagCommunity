import { Children, cloneElement, isValidElement, useMemo, useState, type ReactNode } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import { Check, Copy } from 'lucide-react';
import { resolveAssetUrl } from '../../utils/urlUtils';
import { normalizeMarkdownForPreview } from '../../utils/markdownUtils';

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

function tryExtractUploadPath(href: string): string | null {
  const raw = String(href ?? '').trim();
  if (!raw) return null;
  if (raw.startsWith('/uploads/')) return raw;
  if (raw.startsWith('uploads/')) return `/${raw}`;
  try {
    const url = new URL(raw, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    const path = String(url.pathname ?? '').trim();
    if (path.startsWith('/uploads/')) return path;
  } catch {
  }
  return null;
}

function inferDownloadFileName(href: string | undefined, children: ReactNode): string | null {
  const uploadPath = tryExtractUploadPath(String(href ?? ''));
  if (!uploadPath) return null;

  const label = nodeText(children).trim();
  if (!label) return null;
  const tail = label.split(/[\\/]/).pop()?.trim() ?? '';
  if (!tail) return null;
  // 浏览器下载建议名：优先使用 markdown 链接文字，并要求包含扩展名。
  if (!/\.[A-Za-z0-9]{1,12}$/.test(tail)) return null;
  return tail;
}

const INLINE_CODE_CLASSES = new Set(['px-1', 'py-0.5', 'bg-gray-100', 'rounded', 'border', 'border-gray-200']);

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

  const match = lineText.match(/^\[!(TIP|NOTE|IMPORTANT|WARNING|CAUTION)](?:\s+(.*))?$/i);
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

  const normalizedChildren = useMemo(() => {
    const maybeOnlyChild = Array.isArray(children) && children.length === 1 ? children[0] : children;
    if (!isValidElement<{ className?: unknown }>(maybeOnlyChild) || maybeOnlyChild.type !== 'code') return children;
    const cls = typeof maybeOnlyChild.props.className === 'string' ? maybeOnlyChild.props.className : '';
    const parts = cls.split(/\s+/).filter(Boolean);
    const filtered = parts.filter((x) => !INLINE_CODE_CLASSES.has(x));
    if (filtered.length === parts.length) return children;
    return cloneElement(maybeOnlyChild, { className: filtered.join(' ') });
  }, [children]);

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
        {normalizedChildren}
      </pre>
    </div>
  );
}

export default function MarkdownPreview(props: { markdown: string; className?: string; components?: Components }) {
  const { markdown, className, components } = props;

  const normalizedMarkdown = useMemo(() => normalizeMarkdownForPreview(markdown), [markdown]);

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

  const mergedComponents = useMemo<Components>(() => {
    const base: Components = {
      h1: ({ children, ...p }) => (
        <h1 {...p} className="mt-5 first:mt-0 mb-3 text-2xl font-semibold">
          {children}
        </h1>
      ),
      h2: ({ children, ...p }) => (
        <h2 {...p} className="mt-5 first:mt-0 mb-3 text-xl font-semibold">
          {children}
        </h2>
      ),
      h3: ({ children, ...p }) => (
        <h3 {...p} className="mt-4 first:mt-0 mb-2 text-lg font-semibold">
          {children}
        </h3>
      ),
      h4: ({ children, ...p }) => (
        <h4 {...p} className="mt-4 first:mt-0 mb-2 text-base font-semibold">
          {children}
        </h4>
      ),
      h5: ({ children, ...p }) => (
        <h5 {...p} className="mt-3 first:mt-0 mb-2 text-sm font-semibold">
          {children}
        </h5>
      ),
      h6: ({ children, ...p }) => (
        <h6 {...p} className="mt-3 first:mt-0 mb-2 text-sm font-medium text-gray-700">
          {children}
        </h6>
      ),
      p: ({ children, ...p }) => (
        <p {...p} className="my-2 leading-7">
          {children}
        </p>
      ),
      ul: ({ children, ...p }) => (
        <ul {...p} className="my-2 pl-6 list-disc">
          {children}
        </ul>
      ),
      ol: ({ children, ...p }) => (
        <ol {...p} className="my-2 pl-6 list-decimal">
          {children}
        </ol>
      ),
      li: ({ children, ...p }) => (
        <li {...p} className="my-1 leading-7">
          {children}
        </li>
      ),
      blockquote: ({ children, ...p }) => {
        const parsed = parseAdmonitionFromBlockquoteChildren(children);
        if (!parsed) {
          return (
            <blockquote {...p} className="my-3 flow-root border-l-4 border-gray-200 pl-4 text-gray-700">
              {children}
            </blockquote>
          );
        }

        const { kind, title, content } = parsed;
        const stylesByKind: Record<string, { bg: string; border: string; titleText: string; bodyText: string }> = {
          TIP: { bg: 'bg-blue-50', border: 'border-blue-200', titleText: 'text-blue-900', bodyText: 'text-blue-900' },
          NOTE: { bg: 'bg-gray-50', border: 'border-gray-200', titleText: 'text-gray-900', bodyText: 'text-gray-800' },
          IMPORTANT: {
            bg: 'bg-purple-50',
            border: 'border-purple-200',
            titleText: 'text-purple-900',
            bodyText: 'text-purple-900',
          },
          WARNING: { bg: 'bg-amber-50', border: 'border-amber-200', titleText: 'text-amber-900', bodyText: 'text-amber-900' },
          CAUTION: { bg: 'bg-red-50', border: 'border-red-200', titleText: 'text-red-900', bodyText: 'text-red-900' },
        };

        const s = stylesByKind[kind] ?? stylesByKind.NOTE;

        return (
          <blockquote
            {...p}
            className={`my-3 flow-root rounded-md border ${s.border} border-l-4 ${s.bg} px-4 py-3 [&>*:first-child]:mt-0 [&>*:last-child]:mb-0`}
          >
            <div className={`mb-2 text-xs font-semibold tracking-wide uppercase ${s.titleText}`}>{title}</div>
            <div className={`${s.bodyText} [&>*:first-child]:mt-0 [&>*:last-child]:mb-0`}>{content}</div>
          </blockquote>
        );
      },
      hr: (p) => <hr {...p} className="my-4 border-gray-200" />,
      table: ({ children, ...p }) => (
        <div className="my-3 w-full overflow-auto">
          <table {...p} className="w-full border-collapse text-sm">
            {children}
          </table>
        </div>
      ),
      th: ({ children, ...p }) => (
        <th {...p} className="border border-gray-200 bg-gray-50 px-3 py-2 text-left font-medium">
          {children}
        </th>
      ),
      td: ({ children, ...p }) => (
        <td {...p} className="border border-gray-200 px-3 py-2 align-top">
          {children}
        </td>
      ),
      a: ({ href, children, ...p }) => {
        const h = href ?? '';
        const isHashLink = typeof h === 'string' && h.startsWith('#');
        const downloadName = inferDownloadFileName(href, children);
        return (
          <a
            {...p}
            children={children}
            href={resolveAssetUrl(href) ?? href}
            target={isHashLink ? undefined : '_blank'}
            rel={isHashLink ? undefined : 'noreferrer'}
            download={downloadName ?? undefined}
            className="text-blue-600 hover:underline"
          />
        );
      },
      img: ({ alt, src, ...p }) => (
        <img
          {...p}
          src={resolveAssetUrl(src) ?? src}
          alt={typeof alt === 'string' && alt.trim() ? alt : 'image'}
          className="max-w-full rounded border border-gray-200"
        />
      ),
      code: (props: React.ComponentPropsWithoutRef<'code'> & { inline?: boolean; node?: unknown }) => {
        const { className: c, children, inline, node, ...p } = props;
        const className = Array.isArray(c) ? c.join(' ') : typeof c === 'string' ? c : '';
        const hasBlockClass = className.includes('language-') || className.includes('hljs');
        const hasNewline = nodeText(children).includes('\n');
        const nodeType = (node as { type?: unknown } | undefined)?.type;
        const isInline =
          nodeType === 'inlineCode' ? true : nodeType === 'code' ? false : typeof inline === 'boolean' ? inline : !(hasBlockClass || hasNewline);
        return isInline ? (
          <code {...p} className="px-1 py-0.5 bg-gray-100 rounded border border-gray-200">
            {children}
          </code>
        ) : (
          <code {...p} className={className}>
            {children}
          </code>
        );
      },
      pre: ({ children, ...p }) => (
        <CodeBlockPre {...p}>{children}</CodeBlockPre>
      ),
    };

    return { ...base, ...(components ?? {}) };
  }, [components]);

  return (
    <div className={`max-w-none text-sm text-gray-900 ${className ?? ''}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[
          rehypeHighlight,
          // Allow HTML in markdown but sanitize it.
          [rehypeRaw, { passThrough: ['element'] }],
          [rehypeSanitize, sanitizeSchema],
        ]}
        components={mergedComponents}
      >
        {normalizedMarkdown}
      </ReactMarkdown>
    </div>
  );
}
