import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Modal from './Modal';

describe('Modal', () => {
  afterEach(() => {
    cleanup();
  });

  it('does not render when closed', () => {
    render(
      <Modal isOpen={false} onClose={() => {}} title="标题">
        <div>正文</div>
      </Modal>,
    );

    expect(screen.queryByText('标题')).toBeNull();
    expect(screen.queryByText('正文')).toBeNull();
  });

  it('renders title and content when open', () => {
    render(
      <Modal isOpen={true} onClose={() => {}} title="标题">
        <div>正文</div>
      </Modal>,
    );

    expect(screen.getByText('标题')).not.toBeNull();
    expect(screen.getByText('正文')).not.toBeNull();
    expect(screen.getByRole('button', { name: '关闭' })).not.toBeNull();
  });

  it('calls onClose when clicking overlay', () => {
    const onClose = vi.fn();
    const { container } = render(
      <Modal isOpen={true} onClose={onClose} title="标题">
        <div>正文</div>
      </Modal>,
    );

    const overlay = container.querySelector('.fixed.inset-0.bg-black.opacity-50') as HTMLElement;
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when clicking header close button and footer close button', () => {
    const onClose = vi.fn();
    const { container } = render(
      <Modal isOpen={true} onClose={onClose} title="标题">
        <div>正文</div>
      </Modal>,
    );

    const xButton = container.querySelector('button.text-3xl') as HTMLElement;
    fireEvent.click(xButton);
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('hides footer close button when showFooterClose is false', () => {
    render(
      <Modal isOpen={true} onClose={() => {}} title="标题" showFooterClose={false}>
        <div>正文</div>
      </Modal>,
    );

    expect(screen.queryByRole('button', { name: '关闭' })).toBeNull();
  });

  it('calls onClose on Escape only when open', () => {
    const onClose = vi.fn();
    const view = render(
      <Modal isOpen={true} onClose={onClose} title="标题">
        <div>正文</div>
      </Modal>,
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);

    view.rerender(
      <Modal isOpen={false} onClose={onClose} title="标题">
        <div>正文</div>
      </Modal>,
    );
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('applies custom container/body className props', () => {
    const { container } = render(
      <Modal
        isOpen={true}
        onClose={() => {}}
        title="标题"
        containerClassName="container-x"
        bodyClassName="body-y"
      >
        <div>正文</div>
      </Modal>,
    );

    const modalContainer = container.querySelector('.container-x') as HTMLElement;
    expect(modalContainer).not.toBeNull();
    const body = container.querySelector('.body-y') as HTMLElement;
    expect(body).not.toBeNull();
  });
});
