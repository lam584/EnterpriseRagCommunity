import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ImageLightbox from './ImageLightbox';

afterEach(() => {
  cleanup();
});

describe('ImageLightbox', () => {
  it('renders null when closed or src missing', () => {
    const { container: c1 } = render(<ImageLightbox open={false} src={'/a.png'} onClose={() => {}} />);
    expect(c1.firstChild).toBeNull();

    const { container: c2 } = render(<ImageLightbox open={true} src={null} onClose={() => {}} />);
    expect(c2.firstChild).toBeNull();
  });

  it('closes on Escape when open', async () => {
    const onClose = vi.fn();
    const addSpy = vi.spyOn(window, 'addEventListener');
    render(<ImageLightbox open={true} src={'/a.png'} alt="photo" onClose={onClose} />);
    await waitFor(() => {
      expect(addSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
    });
    const handler = addSpy.mock.calls.find((c) => c[0] === 'keydown')?.[1] as ((e: KeyboardEvent) => void) | undefined;
    handler?.(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    addSpy.mockRestore();
  });

  it('does not close on non-Escape keys', async () => {
    const onClose = vi.fn();
    const addSpy = vi.spyOn(window, 'addEventListener');
    render(<ImageLightbox open={true} src={'/a.png'} alt="photo" onClose={onClose} />);
    await waitFor(() => {
      expect(addSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
    });
    const handler = addSpy.mock.calls.find((c) => c[0] === 'keydown')?.[1] as ((e: KeyboardEvent) => void) | undefined;
    handler?.(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(onClose).toHaveBeenCalledTimes(0);
    addSpy.mockRestore();
  });

  it('uses fallback alt text when alt is missing', () => {
    const onClose = vi.fn();
    render(<ImageLightbox open={true} src={'/a.png'} onClose={onClose} />);
    expect(screen.getByRole('img', { name: 'image' })).toBeTruthy();
  });

  it('closes when clicking backdrop', () => {
    const onClose = vi.fn();
    render(<ImageLightbox open={true} src={'/a.png'} alt="photo" onClose={onClose} />);
    const dialog = screen.getByRole('dialog');
    fireEvent.mouseDown(dialog);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not close when clicking image', () => {
    const onClose = vi.fn();
    render(<ImageLightbox open={true} src={'/a.png'} alt="photo" onClose={onClose} />);
    const img = screen.getByRole('img', { name: 'photo' });
    fireEvent.mouseDown(img);
    expect(onClose).toHaveBeenCalledTimes(0);
  });
});
