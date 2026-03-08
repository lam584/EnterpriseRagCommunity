import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { renderToString } from 'react-dom/server';
import FloatingWindow from './FloatingWindow';

type PointerInit = {
  pointerId?: number;
  clientX?: number;
  clientY?: number;
  button?: number;
};

class MockPointerEvent extends MouseEvent {
  pointerId: number;
  constructor(type: string, init: PointerEventInit & { pointerId?: number } = {}) {
    super(type, init);
    this.pointerId = init.pointerId ?? 1;
  }
}

function ensurePointerEvent() {
  if (typeof (window as any).PointerEvent === 'undefined') {
    Object.defineProperty(window, 'PointerEvent', { value: MockPointerEvent, configurable: true });
  }
}

function setViewport(width: number, height: number) {
  Object.defineProperty(window, 'innerWidth', { value: width, configurable: true });
  Object.defineProperty(window, 'innerHeight', { value: height, configurable: true });
}

function dispatchPointer(target: EventTarget, type: string, init: PointerInit) {
  ensurePointerEvent();
  const ev = new (window as any).PointerEvent(type, {
    bubbles: true,
    cancelable: true,
    pointerId: init.pointerId ?? 1,
    clientX: init.clientX ?? 0,
    clientY: init.clientY ?? 0,
    button: init.button ?? 0,
  });
  target.dispatchEvent(ev);
}

function getRoot(container: HTMLElement): HTMLElement {
  const root = container.firstElementChild as HTMLElement | null;
  if (!root) throw new Error('FloatingWindow root not found');
  return root;
}

function renderWithFragment(ui: React.ReactElement) {
  return render(<React.Fragment>{ui}</React.Fragment>);
}

describe('FloatingWindow', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    setViewport(1000, 800);
  });

  afterEach(() => {
    cleanup();
    (vi as any).unstubAllGlobals?.();
  });

  it('renders on server when window is undefined', () => {
    vi.stubGlobal('window', undefined as any);
    const html = renderToString(
      <FloatingWindow storageKey="fw:test:ssr" title="标题" defaultCollapsed={false}>
        <div>内容</div>
      </FloatingWindow>,
    );
    expect(html.includes('left:764px')).toBe(true);
    expect(html.includes('top:200px')).toBe(true);
  });

  it('restores rect/collapsed from localStorage and saves updated state', async () => {
    const storageKey = 'fw:test:restore';
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem');
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');

    localStorage.setItem(
      storageKey,
      JSON.stringify({
        rect: { x: 111, y: 222, width: 333, height: 444 },
        collapsed: false,
        expandedRect: { x: 1, y: 2, width: 3, height: 4 },
      })
    );

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题">
        <div>内容</div>
      </FloatingWindow>
    );

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('111px');
      expect(root.style.top).toBe('222px');
      expect(root.style.width).toBe('333px');
      expect(root.style.height).toBe('444px');
    });

    expect(getItemSpy).toHaveBeenCalledWith(storageKey);

    await waitFor(() => {
      expect(setItemSpy).toHaveBeenCalled();
    });
    const savedCalls = setItemSpy.mock.calls.filter((c) => c[0] === storageKey);
    const lastSaved = savedCalls[savedCalls.length - 1]?.[1];
    expect(lastSaved).not.toBeUndefined();
    const parsed = JSON.parse(String(lastSaved)) as { rect?: any; collapsed?: any };
    expect(parsed.collapsed).toBe(false);
    expect(parsed.rect).toEqual({ x: 111, y: 222, width: 333, height: 444 });
  });

  it('falls back when localStorage contains invalid JSON (no crash, uses clamped defaults)', async () => {
    const storageKey = 'fw:test:invalid-json';
    localStorage.setItem(storageKey, '{not-json');
    setViewport(800, 600);

    const { container } = renderWithFragment(
      <FloatingWindow
        storageKey={storageKey}
        title="标题"
        defaultCollapsed={false}
        defaultRect={{ x: 9999, y: 9999, width: 9000, height: 9000 }}
      >
        <div>内容</div>
      </FloatingWindow>
    );

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('8px');
      expect(root.style.top).toBe('8px');
      expect(root.style.width).toBe('784px');
      expect(root.style.height).toBe('584px');
    });
  });

  it('falls back when localStorage is missing (null) and saves defaults', async () => {
    const storageKey = 'fw:test:missing';
    setViewport(800, 600);
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem');
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false}>
        <div>内容</div>
      </FloatingWindow>
    );

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('364px');
      expect(root.style.top).toBe('8px');
      expect(root.style.width).toBe('420px');
      expect(root.style.height).toBe('520px');
    });

    expect(getItemSpy).toHaveBeenCalledWith(storageKey);
    await waitFor(() => expect(setItemSpy).toHaveBeenCalled());
  });

  it('toggles collapse/expand and hides/shows children', async () => {
    const storageKey = 'fw:test:collapse';
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false}>
        <div>内容</div>
      </FloatingWindow>
    );

    expect(screen.getByText('内容')).not.toBeNull();
    expect(screen.getByText('折叠')).not.toBeNull();

    fireEvent.click(screen.getByText('折叠'));
    await waitFor(() => {
      expect(screen.queryByText('内容')).toBeNull();
      expect(screen.getByText('展开')).not.toBeNull();
    });

    fireEvent.click(screen.getByText('展开'));
    await waitFor(() => {
      expect(screen.getByText('内容')).not.toBeNull();
      expect(screen.getByText('折叠')).not.toBeNull();
      expect(getRoot(container).style.height).not.toBe('44px');
    });
  });

  it('expands using current rect when expandedRectRef is empty', async () => {
    const storageKey = 'fw:test:expand-fallback';
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={true} defaultRect={{ x: 50, y: 50, width: 300, height: 260 }}>
        <div>内容</div>
      </FloatingWindow>,
    );

    await waitFor(() => {
      expect(screen.queryByText('内容')).toBeNull();
      expect(screen.getByText('展开')).not.toBeNull();
      expect(getRoot(container).style.height).toBe('44px');
    });

    fireEvent.click(screen.getByText('展开'));
    await waitFor(() => {
      expect(screen.getByText('内容')).not.toBeNull();
      expect(getRoot(container).style.height).not.toBe('44px');
    });
  });

  it('calls onClose callback when clicking close button', async () => {
    const storageKey = 'fw:test:close';
    const onClose = vi.fn();
    renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" onClose={onClose}>
        <div>内容</div>
      </FloatingWindow>
    );

    fireEvent.click(screen.getByText('关闭'));
    await waitFor(() => {
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  it('drags with pointer events and clamps position within viewport', async () => {
    const storageKey = 'fw:test:drag';
    setViewport(800, 600);

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} defaultRect={{ x: 100, y: 100, width: 420, height: 300 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const header = container.querySelector('.cursor-grab') as HTMLElement | null;
    expect(header).not.toBeNull();

    dispatchPointer(header as HTMLElement, 'pointerdown', { pointerId: 1, button: 0, clientX: 200, clientY: 200 });
    dispatchPointer(window, 'pointermove', { pointerId: 1, clientX: -10000, clientY: -10000 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('8px');
      expect(root.style.top).toBe('8px');
    });

    dispatchPointer(window, 'pointerup', { pointerId: 1 });
  });

  it('does not start dragging when pointerdown originates from data-no-drag controls', async () => {
    const storageKey = 'fw:test:no-drag-control';
    setViewport(800, 600);
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} defaultRect={{ x: 120, y: 120, width: 320, height: 240 }}>
        <div>内容</div>
      </FloatingWindow>,
    );

    const rootBefore = getRoot(container);
    const toggle = screen.getByText('折叠');
    dispatchPointer(toggle, 'pointerdown', { pointerId: 31, button: 0, clientX: 10, clientY: 10 });
    dispatchPointer(window, 'pointermove', { pointerId: 31, clientX: 500, clientY: 500 });
    dispatchPointer(window, 'pointerup', { pointerId: 31 });

    await waitFor(() => {
      const rootAfter = getRoot(container);
      expect(rootAfter.style.left).toBe(rootBefore.style.left);
      expect(rootAfter.style.top).toBe(rootBefore.style.top);
    });
  });

  it('drags while collapsed and clamps y using headerHeight', async () => {
    const storageKey = 'fw:test:drag-collapsed';
    setViewport(300, 200);

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={true} defaultRect={{ x: 100, y: 50, width: 200, height: 300 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const header = container.querySelector('.cursor-grab') as HTMLElement | null;
    expect(header).not.toBeNull();

    dispatchPointer(header as HTMLElement, 'pointerdown', { pointerId: 1, button: 0, clientX: 10, clientY: 10 });
    dispatchPointer(window, 'pointermove', { pointerId: 1, clientX: 10, clientY: 10000 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.top).toBe('148px');
      expect(root.style.height).toBe('44px');
    });

    dispatchPointer(window, 'pointerup', { pointerId: 1 });
  });

  it('resizes while collapsed and clamps y using headerHeight', async () => {
    const storageKey = 'fw:test:resize-collapsed';
    setViewport(300, 200);

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={true} minWidth={120} minHeight={120} defaultRect={{ x: 50, y: 50, width: 200, height: 180 }}>
        <div>内容</div>
      </FloatingWindow>,
    );

    const south = container.querySelector('.cursor-s-resize') as HTMLElement;
    dispatchPointer(south, 'pointerdown', { pointerId: 41, button: 0, clientX: 0, clientY: 0 });
    dispatchPointer(window, 'pointermove', { pointerId: 41, clientX: 0, clientY: 10000 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.height).toBe('44px');
      expect(Number.parseInt(root.style.top, 10)).toBeGreaterThanOrEqual(8);
    });

    dispatchPointer(window, 'pointerup', { pointerId: 41 });
  });

  it('resizes from east edge and clamps width to viewport', async () => {
    const storageKey = 'fw:test:resize-east';
    setViewport(800, 600);

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} minWidth={100} minHeight={100} defaultRect={{ x: 100, y: 100, width: 420, height: 300 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const east = container.querySelector('.cursor-e-resize') as HTMLElement | null;
    expect(east).not.toBeNull();

    dispatchPointer(east as HTMLElement, 'pointerdown', { pointerId: 2, button: 0, clientX: 0, clientY: 0 });
    dispatchPointer(window, 'pointermove', { pointerId: 2, clientX: 10000, clientY: 0 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('100px');
      expect(root.style.width).toBe('692px');
    });

    dispatchPointer(window, 'pointerup', { pointerId: 2 });
    dispatchPointer(window, 'pointermove', { pointerId: 2, clientX: 0, clientY: 0 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.width).toBe('692px');
    });
  });

  it('resizes from west edge and clamps x/width to viewport', async () => {
    const storageKey = 'fw:test:resize-west';
    setViewport(800, 600);

    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} minWidth={100} minHeight={100} defaultRect={{ x: 50, y: 100, width: 200, height: 300 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const west = container.querySelector('.cursor-w-resize') as HTMLElement | null;
    expect(west).not.toBeNull();

    dispatchPointer(west as HTMLElement, 'pointerdown', { pointerId: 3, button: 0, clientX: 100, clientY: 0 });
    dispatchPointer(window, 'pointermove', { pointerId: 3, clientX: -10000, clientY: 0 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('8px');
      expect(root.style.width).toBe('242px');
    });

    dispatchPointer(window, 'pointerup', { pointerId: 3 });
  });

  it('snaps to defaultAnchor on mount when enabled', async () => {
    const storageKey = 'fw:test:snap-anchor';
    setViewport(800, 600);

    const { container } = renderWithFragment(
      <FloatingWindow
        storageKey={storageKey}
        title="标题"
        defaultCollapsed={false}
        initialCollapsed={true}
        snapAnchorOnMount={true}
        defaultAnchor="top-left"
        anchorMargin={16}
        minWidth={100}
        minHeight={100}
        defaultRect={{ width: 200, height: 200 }}
      >
        <div>内容</div>
      </FloatingWindow>
    );

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('16px');
      expect(root.style.top).toBe('16px');
      expect(root.style.height).toBe('44px');
    });
  });

  it('renders titleRight function result and no close button when onClose is missing', async () => {
    const storageKey = 'fw:test:title-right';
    renderWithFragment(
      <FloatingWindow
        storageKey={storageKey}
        title="标题"
        defaultCollapsed={false}
        titleRight={(collapsed) => <span>{collapsed ? 'COLLAPSED' : 'EXPANDED'}</span>}
      >
        <div>内容</div>
      </FloatingWindow>
    );

    expect(screen.getByText('EXPANDED')).not.toBeNull();
    expect(screen.queryByText('关闭')).toBeNull();
    fireEvent.click(screen.getByText('折叠'));
    await waitFor(() => {
      expect(screen.getByText('COLLAPSED')).not.toBeNull();
    });
  });

  it('ignores non-left-button drag and resize pointerdown', async () => {
    const storageKey = 'fw:test:ignore-right-button';
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} defaultRect={{ x: 100, y: 100, width: 300, height: 250 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const rootBefore = getRoot(container);
    const header = container.querySelector('.cursor-grab') as HTMLElement;
    const east = container.querySelector('.cursor-e-resize') as HTMLElement;

    dispatchPointer(header, 'pointerdown', { pointerId: 11, button: 2, clientX: 100, clientY: 100 });
    dispatchPointer(window, 'pointermove', { pointerId: 11, clientX: 500, clientY: 500 });

    dispatchPointer(east, 'pointerdown', { pointerId: 12, button: 2, clientX: 100, clientY: 100 });
    dispatchPointer(window, 'pointermove', { pointerId: 12, clientX: 9999, clientY: 9999 });

    await waitFor(() => {
      const rootAfter = getRoot(container);
      expect(rootAfter.style.left).toBe(rootBefore.style.left);
      expect(rootAfter.style.top).toBe(rootBefore.style.top);
      expect(rootAfter.style.width).toBe(rootBefore.style.width);
    });
  });

  it('ignores non-left-button pointerdown for all resize handles', async () => {
    const storageKey = 'fw:test:ignore-nonleft-all-handles';
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} defaultRect={{ x: 100, y: 100, width: 300, height: 250 }}>
        <div>内容</div>
      </FloatingWindow>,
    );

    const rootBefore = getRoot(container);
    const selectors = [
      '.cursor-n-resize',
      '.cursor-s-resize',
      '.cursor-w-resize',
      '.cursor-e-resize',
      '.cursor-nw-resize',
      '.cursor-ne-resize',
      '.cursor-sw-resize',
      '.cursor-se-resize',
    ];

    for (const sel of selectors) {
      const handle = container.querySelector(sel) as HTMLElement | null;
      expect(handle).not.toBeNull();
      dispatchPointer(handle as HTMLElement, 'pointerdown', { pointerId: 90, button: 2, clientX: 10, clientY: 10 });
      dispatchPointer(window, 'pointermove', { pointerId: 90, clientX: 9999, clientY: 9999 });
      dispatchPointer(window, 'pointerup', { pointerId: 90 });
    }

    await waitFor(() => {
      const rootAfter = getRoot(container);
      expect(rootAfter.style.left).toBe(rootBefore.style.left);
      expect(rootAfter.style.top).toBe(rootBefore.style.top);
      expect(rootAfter.style.width).toBe(rootBefore.style.width);
      expect(rootAfter.style.height).toBe(rootBefore.style.height);
    });
  });

  it('resizes from north edge and clamps min height with y adjustment', async () => {
    const storageKey = 'fw:test:resize-north';
    setViewport(800, 600);
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} minHeight={120} defaultRect={{ x: 120, y: 120, width: 300, height: 260 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const north = container.querySelector('.cursor-n-resize') as HTMLElement;
    dispatchPointer(north, 'pointerdown', { pointerId: 21, button: 0, clientX: 120, clientY: 120 });
    dispatchPointer(window, 'pointermove', { pointerId: 21, clientX: 120, clientY: 1000 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.height).toBe('120px');
      expect(root.style.top).toBe('260px');
    });
  });

  it('resizes from south-west corner and clamps to viewport/min bounds', async () => {
    const storageKey = 'fw:test:resize-sw';
    setViewport(500, 400);
    const { container } = renderWithFragment(
      <FloatingWindow storageKey={storageKey} title="标题" defaultCollapsed={false} minWidth={150} minHeight={120} defaultRect={{ x: 120, y: 120, width: 220, height: 180 }}>
        <div>内容</div>
      </FloatingWindow>
    );

    const sw = container.querySelector('.cursor-sw-resize') as HTMLElement;
    dispatchPointer(sw, 'pointerdown', { pointerId: 22, button: 0, clientX: 120, clientY: 300 });
    dispatchPointer(window, 'pointermove', { pointerId: 22, clientX: -9999, clientY: 9999 });

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('8px');
      expect(root.style.width).toBe('332px');
      expect(root.style.height).toBe('272px');
    });
  });

  it('snaps to bottom-right anchor on mount', async () => {
    const storageKey = 'fw:test:snap-br';
    setViewport(900, 700);

    const { container } = renderWithFragment(
      <FloatingWindow
        storageKey={storageKey}
        title="标题"
        defaultCollapsed={false}
        snapAnchorOnMount={true}
        defaultAnchor="bottom-right"
        anchorMargin={20}
        defaultRect={{ width: 300, height: 200 }}
      >
        <div>内容</div>
      </FloatingWindow>
    );

    await waitFor(() => {
      const root = getRoot(container);
      expect(root.style.left).toBe('560px');
      expect(root.style.top).toBe('440px');
    });
  });
});
