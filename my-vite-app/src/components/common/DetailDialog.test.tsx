import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DetailDialog from './DetailDialog';

describe('DetailDialog', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  afterEach(() => {
    cleanup();
    document.body.style.overflow = '';
  });

  it('does not render when open is false', () => {
    render(
      <DetailDialog open={false} onClose={() => {}} title="标题">
        <div>内容</div>
      </DetailDialog>,
    );

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('renders title, subtitle, default hint and closes via close button', () => {
    const onClose = vi.fn();
    render(
      <DetailDialog open={true} onClose={onClose} title="标题" subtitle="副标题">
        <div>内容</div>
      </DetailDialog>,
    );

    expect(screen.getByRole('dialog')).not.toBeNull();
    expect(screen.getByText('标题')).not.toBeNull();
    expect(screen.getByText('副标题')).not.toBeNull();
    expect(screen.getByText('Esc / 点击遮罩 关闭')).not.toBeNull();
    expect(screen.getByText('内容')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('hides hint when hintText is null', () => {
    render(
      <DetailDialog open={true} onClose={() => {}} title="标题" hintText={null}>
        <div>内容</div>
      </DetailDialog>,
    );

    expect(screen.queryByText('Esc / 点击遮罩 关闭')).toBeNull();
  });

  it('closes on escape key', () => {
    const onClose = vi.fn();
    render(
      <DetailDialog open={true} onClose={onClose} title="标题">
        <div>内容</div>
      </DetailDialog>,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on backdrop mouse down but not on content mouse down', () => {
    const onClose = vi.fn();
    const { container } = render(
      <DetailDialog open={true} onClose={onClose} title="标题">
        <div>内容</div>
      </DetailDialog>,
    );

    const backdrop = screen.getByRole('dialog');
    fireEvent.mouseDown(backdrop);
    expect(onClose).toHaveBeenCalledTimes(1);

    const panel = container.querySelector('.bg-white.shadow-2xl') as HTMLElement;
    fireEvent.mouseDown(panel);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders drawerRight variant classes', () => {
    const { container } = render(
      <DetailDialog open={true} onClose={() => {}} title="标题" variant="drawerRight">
        <div>内容</div>
      </DetailDialog>,
    );

    const backdrop = screen.getByRole('dialog');
    expect(backdrop.className.includes('items-stretch')).toBe(true);

    const panel = container.querySelector('.shadow-xl') as HTMLElement;
    expect(panel.className.includes('max-w-2xl')).toBe(true);
  });

  it('renders tabs and blocks disabled tab click', () => {
    const onTabChange = vi.fn();
    render(
      <DetailDialog
        open={true}
        onClose={() => {}}
        title="标题"
        tabs={[
          { id: 'a', label: 'A' },
          { id: 'b', label: 'B', disabled: true },
        ]}
        activeTabId="a"
        onTabChange={onTabChange}
      >
        <div>内容</div>
      </DetailDialog>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'A' }));
    expect(onTabChange).toHaveBeenCalledWith('a');

    fireEvent.click(screen.getByRole('button', { name: 'B' }));
    expect(onTabChange).toHaveBeenCalledTimes(1);
  });

  it('locks body scroll by default and restores on unmount', async () => {
    const view = render(
      <DetailDialog open={true} onClose={() => {}} title="标题">
        <div>内容</div>
      </DetailDialog>,
    );

    await waitFor(() => {
      expect(document.body.style.overflow).toBe('hidden');
    });

    view.unmount();
    expect(document.body.style.overflow).toBe('');
  });

  it('does not lock body scroll when lockBodyScroll is false', () => {
    render(
      <DetailDialog open={true} onClose={() => {}} title="标题" lockBodyScroll={false}>
        <div>内容</div>
      </DetailDialog>,
    );

    expect(document.body.style.overflow).toBe('');
  });
});
