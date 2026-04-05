import { render, screen, fireEvent, within, cleanup, waitFor, createEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MarkdownEditor, { type MarkdownEditorValue } from './MarkdownEditor';
import * as urlUtils from '../../utils/urlUtils';

let boundingRectSpy: ReturnType<typeof vi.spyOn> | null = null;

function installClipboardMock() {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  return { writeText };
}

function installRafMock() {
  const raf = vi.fn((cb: FrameRequestCallback) => {
    cb(0);
    return 1;
  });
  (globalThis as any).requestAnimationFrame = raf;
  return { raf };
}

function installResizeObserverMock() {
  class MockResizeObserver {
    private cb: ResizeObserverCallback;
    constructor(cb: ResizeObserverCallback) {
      this.cb = cb;
    }
    observe() {
      this.cb([], this as any);
    }
    disconnect() {}
    unobserve() {}
  }
  (globalThis as any).ResizeObserver = MockResizeObserver;
  return { MockResizeObserver };
}

function mockTextareaHeight(height = 200) {
  return vi.spyOn(HTMLTextAreaElement.prototype, 'getBoundingClientRect').mockImplementation(() => {
    return {
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      width: 0,
      height,
      toJSON: () => ({}),
    } as any;
  });
}

async function flushMicrotasks(times = 3) {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve();
  }
}

function renderPreviewCodeCopy(markdown: string) {
  const view = render(<MarkdownEditor value={{ markdown }} onChange={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: '预览' }));
  const button = within(view.container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
  return { ...view, button };
}

function renderEditorWithUploadHandlers() {
  const onChange = vi.fn();
  const onInsertImage = vi.fn(async () => '![](https://img.example/p.png)');
  const onInsertAttachment = vi.fn(async () => '[p.txt](https://file.example/p.txt)');
  render(
    <MarkdownEditor
      value={{ markdown: 'x' }}
      onChange={onChange}
      onInsertImage={onInsertImage}
      onInsertAttachment={onInsertAttachment}
    />,
  );
  const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
  textarea.focus();
  textarea.setSelectionRange(1, 1);
  return { onChange, onInsertImage, onInsertAttachment, textarea };
}

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('MarkdownEditor', () => {
  beforeEach(() => {
    installClipboardMock();
    installRafMock();
    installResizeObserverMock();
    boundingRectSpy = mockTextareaHeight(240);
  });

  it('支持编辑/预览切换，并在预览中渲染最新内容', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<MarkdownEditor value={{ markdown: 'hello' }} onChange={onChange} />);

    expect(screen.getByRole('textbox')).not.toBeNull();

    await user.click(screen.getByRole('button', { name: '预览' }));
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.getByText('hello')).not.toBeNull();

    await user.click(screen.getByRole('button', { name: '编辑' }));
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.value).toBe('hello');

    await user.clear(textarea);
    await user.type(textarea, 'hello world');
    expect(onChange).toHaveBeenLastCalledWith({ markdown: 'hello world' });

    await user.click(screen.getByRole('button', { name: '预览' }));
    expect(screen.getByText('hello world')).not.toBeNull();
  });
    it('preview tab reuses MarkdownPreview heading and table styles', async () => {
        const user = userEvent.setup();
        const markdown = ['# Title', '', '| A | B |', '| --- | --- |', '| 1 | 2 |'].join('\n');
        const {container} = render(<MarkdownEditor value={{markdown}} onChange={vi.fn()}/>);

        await user.click(screen.getByRole('button', {name: '预览'}));

        const heading = screen.getByRole('heading', {level: 1, name: 'Title'});
        expect(heading.className.includes('text-2xl')).toBe(true);

        const table = container.querySelector('table');
        expect(table).not.toBeNull();
        expect(table!.className.includes('border-collapse')).toBe(true);

        const header = within(table as HTMLTableElement).getByRole('columnheader', {name: 'A'});
        expect(header.className.includes('bg-gray-50')).toBe(true);
    });

    it('readOnly 模式允许切换预览但禁止编辑与文件插入', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        const {container} = render(<MarkdownEditor value={{markdown: 'locked'}} onChange={onChange} readOnly/>);

        const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
        expect(textarea.readOnly).toBe(true);

        fireEvent.change(textarea, {target: {value: 'changed'}});
        expect(onChange).not.toHaveBeenCalled();
        expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('locked');

        const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
        expect(fileInput.disabled).toBe(true);

        await user.click(screen.getByRole('button', {name: '预览'}));
        expect(screen.getByText('locked')).not.toBeNull();

        await user.click(screen.getByRole('button', {name: '编辑'}));
        expect(screen.getByRole('textbox')).not.toBeNull();
    });

  it('会从外部 value.markdown 同步到内部输入框值', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { rerender } = render(<MarkdownEditor value={{ markdown: 'a' }} onChange={onChange} />);
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.value).toBe('a');

    await user.type(textarea, 'b');
    expect(onChange).toHaveBeenLastCalledWith({ markdown: 'ab' });

    rerender(<MarkdownEditor value={{ markdown: 'server' }} onChange={onChange} />);
    expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('server');
  });

  it('文件选择：图片走 onInsertImage，附件走 onInsertAttachment，并插入到光标位置', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertImage = vi.fn(async () => '![](https://img.example/a.png)');
    const onInsertAttachment = vi.fn(async () => '[a.txt](https://file.example/a.txt)');

    const { container } = render(
      <MarkdownEditor
        value={{ markdown: 'hi' }}
        onChange={onChange}
        onInsertImage={onInsertImage}
        onInsertAttachment={onInsertAttachment}
      />,
    );

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    textarea.focus();
    textarea.setSelectionRange(2, 2);

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).not.toBeNull();

    const img = new File(['x'], 'a.png', { type: 'image/png' });
    await user.upload(fileInput, img);

    await waitFor(() => {
      expect(onInsertImage).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'hi![](https://img.example/a.png)\n' });
    });
    expect(onInsertImage).toHaveBeenCalledWith(img);
    expect(document.activeElement).toBe(textarea);
    expect(textarea.selectionStart).toBe(2 + '![](https://img.example/a.png)\n'.length);
    expect(textarea.selectionEnd).toBe(2 + '![](https://img.example/a.png)\n'.length);

    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    const txt = new File(['t'], 'a.txt', { type: 'text/plain' });
    await user.upload(fileInput, txt);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'hi![](https://img.example/a.png)\n[a.txt](https://file.example/a.txt)\n' });
    });
    expect(onInsertAttachment).toHaveBeenCalledWith(txt);
  });

  it('上传抛错时展示错误消息，并允许后续成功上传清除错误', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertImage = vi.fn(async () => {
      throw {};
    });
    const onInsertAttachment = vi.fn(async () => '[a.txt](/u/a.txt)');

    const { container } = render(
      <MarkdownEditor
        value={{ markdown: '' }}
        onChange={onChange}
        onInsertImage={onInsertImage}
        onInsertAttachment={onInsertAttachment}
      />,
    );

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const img = new File(['x'], 'a.png', { type: 'image/png' });
    await user.upload(fileInput, img);

    await waitFor(() => {
      expect(screen.getByText('上传失败')).not.toBeNull();
    });

    const txt = new File(['t'], 'a.txt', { type: 'text/plain' });
    await user.upload(fileInput, txt);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(screen.queryByText('上传失败')).toBeNull();
    });
    expect(onChange).toHaveBeenLastCalledWith({ markdown: '[a.txt](/u/a.txt)\n' });
  });

  it('上传抛错时优先展示 Error.message', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => {
      throw new Error('boom');
    });
    const { container } = render(<MarkdownEditor value={{ markdown: '' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />);

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['t'], 'a.txt', { type: 'text/plain' });
    await user.upload(fileInput, f);

    await waitFor(() => {
      expect(screen.getByText('boom')).not.toBeNull();
    });
  });

  it('粘贴上传：clipboardData.files 存在时拦截默认粘贴并逐个处理', async () => {
    const { onChange, onInsertImage, onInsertAttachment, textarea } = renderEditorWithUploadHandlers();

    const pastedImg = new File(['img'], 'p.png', { type: 'image/png' });
    const eFiles = createEvent.paste(textarea, { clipboardData: { files: [pastedImg], items: [] } });
    fireEvent(textarea, eFiles);

    await waitFor(() => {
      expect(onInsertImage).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'x![](https://img.example/p.png)\n' });
    });
    expect(eFiles.defaultPrevented).toBe(true);

    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    const pastedTxt = new File(['t'], 'p.txt', { type: 'text/plain' });
    const eFilesTxt = createEvent.paste(textarea, { clipboardData: { files: [pastedTxt], items: [] } });
    fireEvent(textarea, eFilesTxt);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({
        markdown: 'x![](https://img.example/p.png)\n[p.txt](https://file.example/p.txt)\n',
      });
    });
    expect(eFilesTxt.defaultPrevented).toBe(true);
  });

  it('粘贴上传：优先使用 clipboardData.files，其次使用 clipboardData.items；空集合 early return', async () => {
    const { onChange, onInsertImage, onInsertAttachment, textarea } = renderEditorWithUploadHandlers();

    const empty = createEvent.paste(textarea, { clipboardData: { files: [], items: [] } });
    fireEvent(textarea, empty);
    expect(empty.defaultPrevented).toBe(false);
    expect(onInsertImage).toHaveBeenCalledTimes(0);
    expect(onInsertAttachment).toHaveBeenCalledTimes(0);

    const pastedTxt = new File(['t'], 'p.txt', { type: 'text/plain' });
    const eItems = createEvent.paste(textarea, {
      clipboardData: {
        files: [],
        items: [
          {
            kind: 'file',
            getAsFile: () => pastedTxt,
          },
        ],
      },
    });
    fireEvent(textarea, eItems);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({
        markdown: 'x[p.txt](https://file.example/p.txt)\n',
      });
    });
    expect(eItems.defaultPrevented).toBe(true);
  });

  it('ResizeObserver 缺失时不会监听尺寸变化（仍可正常渲染/测量一次）', async () => {
    (globalThis as any).ResizeObserver = undefined;

    const onBoxHeightChange = vi.fn();
    render(<MarkdownEditor value={{ markdown: 'x' }} onChange={vi.fn()} onBoxHeightChange={onBoxHeightChange} />);

    await waitFor(() => {
      expect(boundingRectSpy).not.toBeNull();
      expect(boundingRectSpy!).toHaveBeenCalled();
    });
    expect(onBoxHeightChange).toHaveBeenCalledTimes(1);
  });

  it('getBoundingClientRect().height 非有限或 <=0 时会 early return（不触发 onBoxHeightChange）', async () => {
    const onBoxHeightChange = vi.fn();
    expect(boundingRectSpy).not.toBeNull();
    boundingRectSpy!.mockImplementation(() => {
      return {
        x: 0,
        y: 0,
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        width: 0,
        height: 0,
        toJSON: () => ({}),
      } as any;
    });

    render(<MarkdownEditor value={{ markdown: 'x' }} onChange={vi.fn()} onBoxHeightChange={onBoxHeightChange} />);

    await waitFor(() => {
      expect(boundingRectSpy!).toHaveBeenCalled();
    });
    expect(onBoxHeightChange).toHaveBeenCalledTimes(0);
  });

  it('插入片段：预览态上传时 textareaRef 为 null，回退为追加到末尾', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[f.txt](/u/f.txt)');

    const { container } = render(
      <MarkdownEditor value={{ markdown: 'hi' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '预览' }));
    expect(screen.queryByRole('textbox')).toBeNull();

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['t'], 'f.txt', { type: 'text/plain' });
    await user.upload(fileInput, f);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'hi[f.txt](/u/f.txt)\n' });
    });
  });

  it('插入片段：selectionStart/selectionEnd 为 null 时回退到末尾插入', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[a](/a)');

    const { container } = render(
      <MarkdownEditor value={{ markdown: 'hello' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />,
    );

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    textarea.focus();
    Object.defineProperty(textarea, 'selectionStart', { value: null, writable: true, configurable: true });
    Object.defineProperty(textarea, 'selectionEnd', { value: null, writable: true, configurable: true });

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['t'], 'a.txt', { type: 'text/plain' });
    await user.upload(fileInput, f);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'hello[a](/a)' });
    });
  });

  it('needsNewline 为 false 时插入不会附加换行', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[x](/x)');

    const { container } = render(
      <MarkdownEditor value={{ markdown: 'hello' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />,
    );

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    textarea.focus();
    textarea.setSelectionRange(1, 1);

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['t'], 'x.txt', { type: 'text/plain' });
    await user.upload(fileInput, f);

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith({ markdown: 'h[x](/x)ello' });
    });
  });

  it('toolbarAfterTabs 非空时会渲染扩展工具条', () => {
    render(
      <MarkdownEditor
        value={{ markdown: '' }}
        onChange={vi.fn()}
        toolbarAfterTabs={<div>after-tabs</div>}
      />,
    );
    expect(screen.getByText('after-tabs')).not.toBeNull();
  });

  it('预览态能将带 [!TIP] 的 blockquote 渲染为 admonition 并保留正文', async () => {
    const user = userEvent.setup();
    render(
      <MarkdownEditor
        value={{
          markdown: ['> [!TIP] 自定义标题', '> 这里是正文'].join('\n'),
        }}
        onChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: '预览' }));

    const title = screen.getByText('自定义标题');
    expect(title.className.includes('uppercase')).toBe(true);

    const body = screen.getByText('这里是正文');
    const wrapper = body.closest('blockquote');
    expect(wrapper).not.toBeNull();
    expect(wrapper!.className.includes('border-l-4')).toBe(true);
  });

  it('预览态在 [!WARNING] 只有标签时仍渲染 admonition 并回退 title=kind', async () => {
    const user = userEvent.setup();
    render(<MarkdownEditor value={{ markdown: '> [!WARNING]' }} onChange={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: '预览' }));

    const title = screen.getByText('WARNING');
    expect(title.className.includes('text-amber-900')).toBe(true);
  });

  it('预览态链接/图片会通过 resolveAssetUrl 解析并在为空时回退原值', async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(urlUtils, 'resolveAssetUrl').mockImplementation((raw) => {
      if (raw === '/uploads/b.txt') return 'https://cdn.example/b.txt';
      if (raw === '/uploads/x.png') return 'https://cdn.example/x.png';
      return undefined;
    });

    const { container } = render(
      <MarkdownEditor
        value={{
          markdown: [
            '[外链](https://example.com/a)',
            '[附件](/uploads/b.txt)',
            '',
            '![](/uploads/x.png)',
            '![](/other/y.png)',
          ].join('\n'),
        }}
        onChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: '预览' }));

    const external = screen.getByRole('link', { name: '外链' });
    expect(external.getAttribute('href')).toBe('https://example.com/a');
    expect(external.getAttribute('target')).toBe('_blank');
    expect(external.getAttribute('rel')).toBe('noreferrer');

    const attachment = screen.getByRole('link', { name: '附件' });
    expect(attachment.getAttribute('href')).toBe('https://cdn.example/b.txt');

    const imgs = Array.from(container.querySelectorAll('img'));
    expect(imgs).toHaveLength(2);
    expect(imgs[0]?.getAttribute('src')).toBe('https://cdn.example/x.png');
    expect(imgs[1]?.getAttribute('src')).toBe('/other/y.png');

    spy.mockRestore();
  });

  it('预览区代码块复制：非空可复制且会写入剪贴板，空内容禁用，且会定时回退', async () => {
    const { writeText } = installClipboardMock();

    vi.useFakeTimers();

    const emptyMd: MarkdownEditorValue = { markdown: ['```', '   ', '```'].join('\n') };
    const empty = render(<MarkdownEditor value={emptyMd} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '预览' }));
    const emptyBtn = within(empty.container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    expect(emptyBtn.disabled).toBe(true);
    fireEvent.click(emptyBtn);
    await flushMicrotasks();
    expect(writeText).not.toHaveBeenCalled();
    empty.unmount();

    const { button: btn } = renderPreviewCodeCopy(['```js', 'const x = 1', '```'].join('\n'));
    expect(btn.disabled).toBe(false);

    fireEvent.click(btn);
    await flushMicrotasks();

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('const x = 1'));
    expect(btn.querySelector('svg.lucide-check')).not.toBeNull();

    vi.advanceTimersByTime(1500);
    await flushMicrotasks(1);
    expect(btn.querySelector('svg.lucide-check')).toBeNull();
  });

  it('预览区代码块复制：clipboard.writeText 抛错时不会进入 copied 状态', async () => {
    const { writeText } = installClipboardMock();
    writeText.mockRejectedValueOnce(new Error('nope'));

    vi.useFakeTimers();

    const { button: btn } = renderPreviewCodeCopy(['```js', 'const x = 1', '```'].join('\n'));
    expect(btn.disabled).toBe(false);

    fireEvent.click(btn);
    await flushMicrotasks();

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(btn.querySelector('svg.lucide-check')).toBeNull();
  });

  it('粘贴事件缺失 clipboardData 时会直接返回', () => {
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[x](/x)');
    render(<MarkdownEditor value={{ markdown: 'x' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />);

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    const ev = createEvent.paste(textarea);
    Object.defineProperty(ev, 'clipboardData', { value: null, configurable: true });
    fireEvent(textarea, ev);

    expect(ev.defaultPrevented).toBe(false);
    expect(onInsertAttachment).toHaveBeenCalledTimes(0);
  });

  it('粘贴上传：items 非 file 或 getAsFile 为空时不处理', () => {
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[x](/x)');
    render(<MarkdownEditor value={{ markdown: 'x' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />);

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    const ev = createEvent.paste(textarea, {
      clipboardData: {
        files: [],
        items: [
          { kind: 'string', getAsFile: () => null },
          { kind: 'file', getAsFile: () => null },
        ],
      },
    });
    fireEvent(textarea, ev);

    expect(ev.defaultPrevented).toBe(false);
    expect(onInsertAttachment).toHaveBeenCalledTimes(0);
  });

  it('粘贴图片但缺少 onInsertImage 时会走 onInsertAttachment', async () => {
    const onChange = vi.fn();
    const onInsertAttachment = vi.fn(async () => '[img.png](/u/img.png)');
    render(
      <MarkdownEditor value={{ markdown: '' }} onChange={onChange} onInsertAttachment={onInsertAttachment} />,
    );

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    textarea.focus();
    textarea.setSelectionRange(0, 0);

    const pastedImg = new File(['img'], 'img.png', { type: 'image/png' });
    const ev = createEvent.paste(textarea, { clipboardData: { files: [pastedImg], items: [] } });
    fireEvent(textarea, ev);

    await waitFor(() => {
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith({ markdown: '[img.png](/u/img.png)\n' });
    });
  });

  it('缺少上传 handler 时选择文件与粘贴文件不应抛错且不插入', async () => {
    const onChange = vi.fn();
    const { container } = render(<MarkdownEditor value={{ markdown: 'x' }} onChange={onChange} />);

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['t'], 'a.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [f] } });
    await flushMicrotasks(2);
    expect(onChange).toHaveBeenCalledTimes(0);

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    const pasted = new File(['img'], 'p.png', { type: 'image/png' });
    const ev = createEvent.paste(textarea, { clipboardData: { files: [pasted], items: [] } });
    fireEvent(textarea, ev);
    await flushMicrotasks(2);
    expect(onChange).toHaveBeenCalledTimes(0);
  });

  it('预览态普通 blockquote 不走 admonition 分支', async () => {
    const user = userEvent.setup();
    render(<MarkdownEditor value={{ markdown: ['> normal quote', '>', '> second'].join('\n') }} onChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: '预览' }));

    const quote = screen.getByText('normal quote').closest('blockquote');
    expect(quote).not.toBeNull();
    expect(quote!.className.includes('not-prose')).toBe(false);
  });

  it('文件选择：无文件时 early return', async () => {
    const onInsertAttachment = vi.fn(async () => '[x](/x)');
    const { container } = render(
      <MarkdownEditor value={{ markdown: '' }} onChange={vi.fn()} onInsertAttachment={onInsertAttachment} />,
    );

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [] } });
    await flushMicrotasks(1);
    expect(onInsertAttachment).toHaveBeenCalledTimes(0);
  });

  it('文件选择：文件类型为空时按附件处理', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onInsertImage = vi.fn(async () => '![](x)');
    const onInsertAttachment = vi.fn(async () => '[f](/f)');

    const { container } = render(
      <MarkdownEditor value={{ markdown: '' }} onChange={onChange} onInsertImage={onInsertImage} onInsertAttachment={onInsertAttachment} />,
    );

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const f = new File(['x'], 'f.bin', { type: '' });
    await user.upload(fileInput, f);

    await waitFor(() => {
      expect(onInsertImage).toHaveBeenCalledTimes(0);
      expect(onInsertAttachment).toHaveBeenCalledTimes(1);
    });
  });

  it('editorHeightPx 存在时禁用 resize 并设置固定高度', () => {
    render(<MarkdownEditor value={{ markdown: '' }} onChange={vi.fn()} editorHeightPx={300} />);
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.className.includes('resize-none')).toBe(true);
    expect(textarea.style.height).toBe('300px');
  });

  it('预览容器在未测量高度前可保持 style=undefined', () => {
    render(<MarkdownEditor value={{ markdown: '' }} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '预览' }));
    const preview = screen.getByText(/支持 Markdown/).closest('div')?.previousElementSibling as HTMLElement | null;
    if (preview) {
      const style = preview.getAttribute('style');
      expect(style === null || /height:\s*\d+px/.test(style)).toBe(true);
    }
  });

  it('预览区空 pre/code 不可复制且不会写入剪贴板', async () => {
    const { writeText } = installClipboardMock();
    vi.useFakeTimers();
    render(<MarkdownEditor value={{ markdown: '<pre><code></code></pre>' }} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '预览' }));

    const btn = screen.getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    btn.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushMicrotasks(1);
    expect(writeText).toHaveBeenCalledTimes(0);
  });

  it('sanitizeSchema 在 structuredClone 缺失 attributes 时仍可渲染预览', async () => {
    const original = globalThis.structuredClone;
    globalThis.structuredClone = (input: unknown) => {
      const cloned = original(input) as { attributes?: unknown };
      delete cloned.attributes;
      return cloned;
    };

    try {
      const user = userEvent.setup();
      render(<MarkdownEditor value={{ markdown: '`x`' }} onChange={vi.fn()} />);
      await user.click(screen.getByRole('button', { name: '预览' }));
      expect(screen.getByText('x')).not.toBeNull();
    } finally {
      globalThis.structuredClone = original;
    }
  });
});
