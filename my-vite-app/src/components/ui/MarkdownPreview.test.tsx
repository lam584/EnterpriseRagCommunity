import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MarkdownPreview from './MarkdownPreview';

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
});
