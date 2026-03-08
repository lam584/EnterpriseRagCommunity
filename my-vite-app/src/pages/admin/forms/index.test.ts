import React, { Suspense } from 'react';
import { render, screen, cleanup } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

async function importFresh() {
  vi.resetModules();
  return await import('./index');
}

describe('admin forms lazy registry', () => {
  afterEach(() => {
    cleanup();
  });

  it('contains expected keys', async () => {
    const { formsLoaders } = await importFresh();
    expect(typeof formsLoaders.metrics).toBe('function');
    expect(typeof formsLoaders.index).toBe('function');
    expect(typeof formsLoaders.logs).toBe('function');
  });

  it('returns undefined for blank ids', async () => {
    const { getLazyForm } = await importFresh();
    expect(getLazyForm(undefined)).toBeUndefined();
    expect(getLazyForm('')).toBeUndefined();
    expect(getLazyForm('   ')).toBeUndefined();
  });

  it('caches lazy components for a known form id', async () => {
    const { getLazyForm } = await importFresh();
    const C = getLazyForm('metrics');
    const C2 = getLazyForm(' metrics ');
    expect(C).toBeTruthy();
    expect(C).toBe(C2);
  });

  it('returns a cached lazy component for an unknown form id (safe fallback)', async () => {
    const { getLazyForm } = await importFresh();
    const C = getLazyForm('__unknown__');
    const C2 = getLazyForm(' __unknown__ ');
    expect(C).toBeTruthy();
    expect(C).toBe(C2);
  });

  it('renders unknown form fallback content', async () => {
    const { getLazyForm } = await importFresh();
    const C = getLazyForm('__unknown__');
    render(
      React.createElement(
        Suspense,
        { fallback: React.createElement('div', null, 'loading') },
        C ? React.createElement(C, { id: '__unknown__' }) : null,
      ),
    );
    expect(await screen.findByText('未找到表单')).not.toBeNull();
    expect(screen.getByText('active=__unknown__')).not.toBeNull();
  });

  it('preloadForm ignores unknown ids', async () => {
    const { preloadForm } = await importFresh();
    expect(preloadForm('__unknown__')).toBeUndefined();
    expect(preloadForm(undefined)).toBeUndefined();
    expect(preloadForm('   ')).toBeUndefined();
  });

  it('preloadForm avoids duplicate dynamic imports for the same id', async () => {
    const { formsLoaders, preloadForm } = await importFresh();
    const loader = vi.fn(async () => ({ default: (() => null) as unknown as React.ComponentType<Record<string, unknown>> }));
    const mutable = formsLoaders as unknown as Record<
      string,
      () => Promise<{ default: React.ComponentType<Record<string, unknown>> }>
    >;
    mutable.__test__ = loader;
    preloadForm('__test__');
    preloadForm('__test__');
    expect(loader).toHaveBeenCalledTimes(1);
  });
});

