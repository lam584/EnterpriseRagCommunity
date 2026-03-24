import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

const siteConfigMocks = vi.hoisted(() => {
  return {
    getPublicSiteConfig: vi.fn(),
  };
});

vi.mock('../../services/publicSiteConfigService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/publicSiteConfigService')>();
  return {
    ...actual,
    getPublicSiteConfig: siteConfigMocks.getPublicSiteConfig,
  };
});

import AuthFooter from './AuthFooter';
import { getPublicSiteConfig } from '../../services/publicSiteConfigService';

const mockGetPublicSiteConfig = vi.mocked(getPublicSiteConfig);

describe('AuthFooter', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders beian link when config returns non-empty beianText', async () => {
    mockGetPublicSiteConfig.mockResolvedValue({
      beianText: '京ICP备12345678号',
      beianHref: null,
      copyrightText: '©2026 Demo 版权所有。',
    } as never);

    render(<AuthFooter />);

    expect(await screen.findByText('京ICP备12345678号')).not.toBeNull();
    const link = screen.getByRole('link', { name: /京ICP备12345678号/ }) as HTMLAnchorElement;
    expect(link.href).toBe('https://beian.miit.gov.cn/');
  });

  it('does not render beian area when beianText is empty/whitespace', async () => {
    mockGetPublicSiteConfig.mockResolvedValue({
      beianText: '   ',
      beianHref: 'https://example.com',
      copyrightText: '©2026 Demo 版权所有。',
    } as never);

    render(<AuthFooter />);

    expect(await screen.findByText('©2026 Demo 版权所有。')).not.toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('falls back gracefully when config load fails', async () => {
    mockGetPublicSiteConfig.mockRejectedValue(new Error('fail'));

    render(<AuthFooter />);

    expect(await screen.findByText('©2026 版权所有。')).not.toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('does not update state after unmount when request resolves later', async () => {
    let resolveFn: ((v: { beianText: string | null; beianHref: string | null; copyrightText: string | null } | PromiseLike<{ beianText: string | null; beianHref: string | null; copyrightText: string | null }>) => void) | null =
      null;
    const p = new Promise<{ beianText: string | null; beianHref: string | null; copyrightText: string | null }>((r) => {
      resolveFn = r;
    });
    mockGetPublicSiteConfig.mockReturnValue(p as never);

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { unmount } = render(<AuthFooter />);
    unmount();

    (resolveFn as unknown as ((v: { beianText: string | null; beianHref: string | null; copyrightText: string | null }) => void) | null)?.({
      beianText: '京ICP备12345678号',
      beianHref: null,
      copyrightText: null,
    });
    await Promise.resolve();
    await Promise.resolve();

    const calls = consoleError.mock.calls.map((c) => String(c[0] ?? ''));
    expect(calls.some((s) => s.includes('unmounted component'))).toBe(false);
    consoleError.mockRestore();
  });
});
