import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BeianFooter from './BeianFooter';

const { getPublicSiteConfigMock } = vi.hoisted(() => ({
  getPublicSiteConfigMock: vi.fn(),
}));

vi.mock('../../services/publicSiteConfigService', () => ({
  getPublicSiteConfig: getPublicSiteConfigMock,
}));

describe('BeianFooter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders null when beian text is empty after trim', async () => {
    getPublicSiteConfigMock.mockResolvedValue({ beianText: '   ', beianHref: null, copyrightText: null });

    const { container } = render(<BeianFooter />);

    await waitFor(() => {
      expect(container.firstChild).toBeNull();
    });
  });

  it('renders with default href when service returns beian text without href', async () => {
    getPublicSiteConfigMock.mockResolvedValue({ beianText: '京ICP备12345678号', beianHref: null, copyrightText: null });

    render(<BeianFooter />);

    const text = await screen.findByText('京ICP备12345678号');
    const link = text.closest('a') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toBe('https://beian.miit.gov.cn/');
    expect(link.className.includes('inline-flex')).toBe(true);

    const icon = screen.getByRole('img', { name: '备案编号' });
    expect(icon.className.includes('w-4')).toBe(true);
  });

  it('uses custom class names and custom href', async () => {
    getPublicSiteConfigMock.mockResolvedValue({
      beianText: '粤ICP备00000000号',
      beianHref: 'https://example.com/beian',
      copyrightText: null,
    });

    const { container } = render(
      <BeianFooter className="outer" linkClassName="link" iconClassName="icon" />,
    );

    const text = await screen.findByText('粤ICP备00000000号');
    const link = text.closest('a') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toBe('https://example.com/beian');
    expect(link.className.includes('link')).toBe(true);
    expect((container.firstElementChild as HTMLElement).className.includes('outer')).toBe(true);

    const icon = screen.getByRole('img', { name: '备案编号' });
    expect(icon.className.includes('icon')).toBe(true);
  });

  it('renders null when service request fails', async () => {
    getPublicSiteConfigMock.mockRejectedValue(new Error('network'));

    const { container } = render(<BeianFooter />);

    await waitFor(() => {
      expect(container.firstChild).toBeNull();
    });
  });
});
