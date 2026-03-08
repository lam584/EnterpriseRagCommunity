import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadBlob } from './download';

describe('download', () => {
  const createUrl = vi.fn<() => string>();
  const revokeUrl = vi.fn<(url: string) => void>();
  const clickSpy = vi.fn<() => void>();
  const removeSpy = vi.fn<() => void>();

  beforeEach(() => {
    const origCreateElement = document.createElement.bind(document);
    createUrl.mockReset();
    revokeUrl.mockReset();
    clickSpy.mockReset();
    removeSpy.mockReset();

    createUrl.mockReturnValue('blob:mock');
    vi.spyOn(URL, 'createObjectURL').mockImplementation(createUrl);
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(revokeUrl);

    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(clickSpy);
    vi.spyOn(Element.prototype, 'remove').mockImplementation(removeSpy);
    vi.spyOn(document, 'createElement').mockImplementation(origCreateElement);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates an object url, clicks anchor, and always revokes url', () => {
    const appendSpy = vi.spyOn(document.body, 'appendChild');
    downloadBlob(new Blob(['x']), 'a.txt');
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(removeSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock');
    expect(appendSpy).toHaveBeenCalledTimes(1);
    const a = appendSpy.mock.calls[0]?.[0] as HTMLAnchorElement | undefined;
    expect(a?.tagName).toBe('A');
    expect(a?.download).toBe('a.txt');
    expect(a?.href).toContain('blob:mock');
  });
});
