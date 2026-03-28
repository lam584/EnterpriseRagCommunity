import {useEffect, useRef, useState, type ReactNode} from 'react';
import MarkdownPreview from './MarkdownPreview';

export type MarkdownEditorValue = {
  markdown: string;
};

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
      const needsTrailingNewline = !el || el.selectionStart === live.length || live.endsWith('\n');
      insertSnippetAtCursor(`${snippet}${needsTrailingNewline ? '\n' : ''}`);
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

    const dt = e.clipboardData;
    if (!dt) return;

    const files: File[] = [];

    if (dt.files && dt.files.length > 0) {
      for (const f of Array.from(dt.files)) files.push(f);
    }

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
      if (isImage && onInsertImage) {
        await insertUploaded(f, 'image');
      } else if (onInsertAttachment) {
        await insertUploaded(f, 'attachment');
      }
    }
  };

  const effectiveBoxHeightPx = boxHeightPx ?? editorHeightPx ?? null;

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
          <MarkdownPreview markdown={live}/>
        </div>
      )}

      <div className="text-xs text-gray-500">
        支持 Markdown（GFM）。提示：标题用 <code># 标题</code>（`#` 后建议加空格），图片用 <code>![](...)</code>
        ，附件用 <code>[name](url)</code>。也支持直接在编辑框里 <code>Ctrl+V</code> 粘贴图片/文件自动上传并插入。
      </div>
    </div>
  );
}
