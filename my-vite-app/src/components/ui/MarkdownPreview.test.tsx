import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MarkdownPreview from './MarkdownPreview';

const originalClipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  delete (globalThis as unknown as { __VITE_API_BASE_URL__?: unknown }).__VITE_API_BASE_URL__;
  if (originalClipboardDescriptor) {
    Object.defineProperty(navigator, 'clipboard', originalClipboardDescriptor);
  } else {
    delete (navigator as unknown as { clipboard?: unknown }).clipboard;
  }
});

describe('MarkdownPreview', () => {
  it('renders headings with visible sizing styles', () => {
    render(<MarkdownPreview markdown={'## Quickstart'} />);
    const h2 = screen.getByRole('heading', { level: 2, name: 'Quickstart' });
    expect(h2.className.includes('text-xl')).toBe(true);
  });

  it('renders lists with bullets', () => {
    render(<MarkdownPreview markdown={['- a', '- b'].join('\n')} />);
    const ul = screen.getByRole('list');
    expect(ul.className.includes('list-disc')).toBe(true);
  });

  it('renders inline code with background styles', () => {
    render(<MarkdownPreview markdown={'the ecosystem. `transformers` is the pivot'} />);
    const text = screen.getByText('transformers');
    const code = text.closest('code');
    expect(code).not.toBeNull();
    expect(code?.className.includes('bg-gray-100')).toBe(true);
  });

  it('does not apply inline code background to fenced code blocks', () => {
    const { container } = render(<MarkdownPreview markdown={['```js', 'const x = 1', '```'].join('\n')} />);
    const code = container.querySelector('pre code.language-js');
    expect(code).not.toBeNull();
    expect(code?.className.includes('bg-gray-100')).toBe(false);
  });

  it('renders a copy button for fenced code blocks', () => {
    const { container } = render(<MarkdownPreview markdown={['```py', '# pip', 'pip install "transformers[torch]"', '```'].join('\n')} />);
    expect(within(container).getByRole('button', { name: '复制代码' })).not.toBeNull();
  });

  it('resolves /uploads links and images using __VITE_API_BASE_URL__ when present', () => {
    (globalThis as unknown as { __VITE_API_BASE_URL__?: unknown }).__VITE_API_BASE_URL__ = 'https://api.example.test';
    render(<MarkdownPreview markdown={'[Download](/uploads/a.epub)\n\n![diagram](/uploads/x.png)'} />);

    const a = screen.getByRole('link', { name: 'Download' });
    expect(a.getAttribute('href')).toBe('https://api.example.test/uploads/a.epub');

    const img = screen.getByRole('img', { name: 'diagram' });
    expect(img.getAttribute('src')).toBe('https://api.example.test/uploads/x.png');
  });

  it('sets target/rel for non-hash links, but not for hash links', () => {
    render(
      <MarkdownPreview
        markdown={['[Hash](#section-a)', '[External](https://example.com/docs)'].join('\n\n')}
      />,
    );

    const hash = screen.getByRole('link', { name: 'Hash' });
    expect(hash.getAttribute('href')).toBe('#section-a');
    expect(hash.getAttribute('target')).toBeNull();
    expect(hash.getAttribute('rel')).toBeNull();

    const external = screen.getByRole('link', { name: 'External' });
    expect(external.getAttribute('href')).toBe('https://example.com/docs');
    expect(external.getAttribute('target')).toBe('_blank');
    expect(external.getAttribute('rel')).toBe('noreferrer');
  });

  it('does not apply inline code styles to fenced code blocks without language', () => {
    const { container } = render(<MarkdownPreview markdown={['```', 'raw', '```'].join('\n')} />);
    const code = container.querySelector('pre code');
    expect(code).not.toBeNull();
    expect(code?.className.includes('bg-gray-100')).toBe(false);
    expect(code?.className.includes('px-1')).toBe(false);
  });

  it('renders admonition callouts like [!TIP] with a background', () => {
    const md = [
      '> [!TIP]',
      '> You can also chat with a model directly from the command line, as long as `https://huggingface.co/docs/transformers/main/en/serving`.',
      '> ```shell',
      '> transformers chat Qwen/Qwen2.5-0.5B-Instruct',
      '> ```',
    ].join('\n');

    render(<MarkdownPreview markdown={md} />);

    expect(screen.queryByText('[!TIP]')).toBeNull();
    expect(screen.getByText('TIP')).not.toBeNull();
    expect(
      screen.getByText('TIP').closest('blockquote')?.className.includes('bg-blue-50'),
    ).toBe(true);
    expect(screen.getByText(/You can also chat with a model/i)).not.toBeNull();
  });

  it('parses admonition title when the marker is the only line of the first paragraph', () => {
    const md = ['> [!WARNING] Read this first', '>', '> Body line'].join('\n');
    render(<MarkdownPreview markdown={md} />);

    expect(screen.queryByText('[!WARNING] Read this first')).toBeNull();
    expect(screen.getByText('Read this first')).not.toBeNull();
    expect(screen.getByText('Read this first').closest('blockquote')?.className.includes('bg-amber-50')).toBe(true);
    expect(screen.getByText('Body line')).not.toBeNull();
  });

  it('renders normal blockquotes without admonition framing styles', () => {
    render(<MarkdownPreview markdown={['> normal quote line', '>', '> second line'].join('\n')} />);
    const quote = screen.getByText(/normal quote line/i).closest('blockquote');
    expect(quote).not.toBeNull();
    expect(quote?.className.includes('border-l-4')).toBe(true);
    expect(quote?.className.includes('border-gray-200')).toBe(true);
    expect(quote?.className.includes('rounded-md')).toBe(false);
  });

  it('falls back to alt="image" when markdown image alt is empty', () => {
    render(<MarkdownPreview markdown={['![](/x.png)', '![diagram](/y.png)'].join('\n')} />);

    const fallback = screen.getByRole('img', { name: 'image' });
    expect(fallback.getAttribute('alt')).toBe('image');

    const named = screen.getByRole('img', { name: 'diagram' });
    expect(named.getAttribute('alt')).toBe('diagram');
  });

  it('disables copy button when fenced code is empty, enables when non-empty', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    const empty = render(<MarkdownPreview markdown={['```', '   ', '```'].join('\n')} />);
    const emptyBtn = within(empty.container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    expect(emptyBtn.disabled).toBe(true);
    fireEvent.click(emptyBtn);
    expect(writeText).not.toHaveBeenCalled();

    empty.unmount();

    const nonEmpty = render(<MarkdownPreview markdown={['```js', 'const x = 1', '```'].join('\n')} />);
    const nonEmptyBtn = within(nonEmpty.container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    expect(nonEmptyBtn.disabled).toBe(false);
    nonEmptyBtn.click();
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('const x = 1'));
  });

  it('sets copied state and schedules reset when copy succeeds', async () => {
    vi.useFakeTimers();
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    const { container } = render(<MarkdownPreview markdown={['```js', 'const y = 2', '```'].join('\n')} />);
    const btn = within(container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    btn.click();

    await Promise.resolve();
    expect(writeText).toHaveBeenCalledTimes(1);

    expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 1500);
    vi.runOnlyPendingTimers();
  });

  it('does not set copied state when clipboard.writeText rejects', async () => {
    vi.useFakeTimers();
    const writeText = vi.fn().mockRejectedValueOnce(new Error('nope'));
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    const { container } = render(<MarkdownPreview markdown={['```js', 'const z = 3', '```'].join('\n')} />);
    const btn = within(container).getByRole('button', { name: '复制代码' }) as HTMLButtonElement;
    btn.click();

    await Promise.resolve();
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(btn.querySelector('svg.lucide-check')).toBeNull();
  });

  it('renders attachment markdown links even when filename contains brackets', () => {
    render(<MarkdownPreview markdown={'[\\[书名\\] by a.epub](/uploads/x.epub)'} />);
    const link = screen.getByRole('link', { name: '[书名] by a.epub' });
    expect(link.getAttribute('href')).toBe('/uploads/x.epub');
  });

  it('renders IMPORTANT and CAUTION admonitions with expected colors', () => {
    const md = ['> [!IMPORTANT] Keep this', '>', '> details', '', '> [!CAUTION]', '> be careful'].join('\n');
    render(<MarkdownPreview markdown={md} />);

    const important = screen.getByText('Keep this').closest('blockquote');
    expect(important?.className.includes('bg-purple-50')).toBe(true);

    const caution = screen.getByText('CAUTION').closest('blockquote');
    expect(caution?.className.includes('bg-red-50')).toBe(true);
  });

  it('applies container className and allows component overrides', () => {
    const { container } = render(
      <MarkdownPreview
        markdown={'# Title'}
        className="preview-x"
        components={{
          h1: ({ children }) => <h1 data-testid="custom-h1">X:{children}</h1>,
        }}
      />,
    );

    expect(container.firstElementChild?.className.includes('preview-x')).toBe(true);
    expect(screen.getByTestId('custom-h1').textContent).toBe('X:Title');
  });

  it('renders empty markdown using fallback normalization', () => {
    const { container } = render(<MarkdownPreview markdown={''} />);
    expect(container.firstElementChild).not.toBeNull();
  });

  it('handles raw HTML anchors without href', () => {
    render(<MarkdownPreview markdown={'<a>Plain</a>'} />);
    const a = screen.getByText('Plain').closest('a');
    expect(a).toBeTruthy();
    expect(a.getAttribute('href')).toBeNull();
    expect(a.getAttribute('target')).toBe('_blank');
    expect(a.getAttribute('rel')).toBe('noreferrer');
  });

  it('keeps empty link href and still applies external link target/rel', () => {
    render(<MarkdownPreview markdown={'[Empty]()'} />);
    const a = screen.getByText('Empty').closest('a');
    expect(a).toBeTruthy();
    expect(a.getAttribute('href')).toBe('');
    expect(a.getAttribute('target')).toBe('_blank');
    expect(a.getAttribute('rel')).toBe('noreferrer');
  });

  it('keeps empty image src and falls back alt to "image"', () => {
    render(<MarkdownPreview markdown={'![]()'} />);
    const img = screen.getByRole('img', { name: 'image' });
    expect(img.getAttribute('src')).toBe('');
  });

  it('extends sanitize schema even if structuredClone drops attributes', () => {
    const original = globalThis.structuredClone;
    globalThis.structuredClone = (input: unknown) => {
      const cloned = original(input) as { attributes?: unknown };
      delete cloned.attributes;
      return cloned;
    };

    try {
      render(<MarkdownPreview markdown={'`x`'} />);
      expect(screen.getByText('x')).not.toBeNull();
    } finally {
      globalThis.structuredClone = original;
    }
  });

  it('renders additional markdown blocks to cover component functions', () => {
    const md = [
      '# H1',
      '###### H6',
      '',
      '1. one',
      '',
      '---',
      '',
      '| a | b |',
      '|---|---|',
      '| 1 | 2 |',
    ].join('\n');
    render(<MarkdownPreview markdown={md} />);
    expect(screen.getByRole('heading', { level: 1, name: 'H1' })).not.toBeNull();
    expect(screen.getByRole('heading', { level: 6, name: 'H6' })).not.toBeNull();
    expect(screen.getByRole('list')).not.toBeNull();
    expect(screen.getByRole('table')).not.toBeNull();
  });

  it('renders raw pre/code blocks and supports copy button', () => {
    const { container } = render(
      <MarkdownPreview markdown={'<pre><code class="language-js">const q = 1</code></pre>'} />,
    );
    expect(within(container).getByRole('button', { name: '复制代码' })).not.toBeNull();
  });

  it('falls back to normal blockquote when raw HTML blockquote is empty', () => {
    const { container } = render(<MarkdownPreview markdown={'<blockquote></blockquote>'} />);
    const quote = container.querySelector('blockquote');
    expect(quote).not.toBeNull();
  });

  it('falls back to normal blockquote when raw HTML blockquote starts with plain text', () => {
    render(<MarkdownPreview markdown={'<blockquote>hello</blockquote>'} />);
    const quote = screen.getByText('hello').closest('blockquote');
    expect(quote).not.toBeNull();
    expect(quote?.className.includes('border-l-4')).toBe(true);
  });
});
