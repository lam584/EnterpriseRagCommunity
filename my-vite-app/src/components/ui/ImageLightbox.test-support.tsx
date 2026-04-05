import { render, waitFor } from '@testing-library/react';
import { expect, vi } from 'vitest';
import ImageLightbox from './ImageLightbox';

export async function renderLightboxAndGetKeyHandler(onClose = vi.fn(), alt = 'photo') {
  const addSpy = vi.spyOn(window, 'addEventListener');
  render(<ImageLightbox open={true} src={'/a.png'} alt={alt} onClose={onClose} />);
  await waitFor(() => {
    expect(addSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
  });
  const handler = addSpy.mock.calls.find((call) => call[0] === 'keydown')?.[1] as ((e: KeyboardEvent) => void) | undefined;
  return { addSpy, handler, onClose };
}
