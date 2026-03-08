import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

type Rect = { x: number; y: number; width: number; height: number };
type ResizeDir = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw';
type Anchor = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

type FloatingWindowProps = {
  storageKey: string;
  title: string;
  titleRight?: ReactNode | ((collapsed: boolean) => ReactNode);
  children: ReactNode;
  defaultRect?: Partial<Rect>;
  defaultAnchor?: Anchor;
  snapAnchorOnMount?: boolean;
  anchorMargin?: number;
  collapsedWidth?: number;
  initialCollapsed?: boolean;
  defaultCollapsed?: boolean;
  minWidth?: number;
  minHeight?: number;
  zIndexClassName?: string;
  onClose?: () => void;
};

function clamp(n: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, n));
}

function safeParseJson<T>(s: string | null): T | null {
  if (!s) return null;
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}

function computeAnchoredXY(args: {
  anchor: Anchor;
  width: number;
  height: number;
  margin: number;
  vw: number;
  vh: number;
}): Pick<Rect, 'x' | 'y'> {
  const { anchor, width, height, margin, vw, vh } = args;
  const xBase = anchor.includes('right') ? vw - width - margin : margin;
  const yBase = anchor.includes('bottom') ? vh - height - margin : margin;
  return {
    x: clamp(xBase, 8, Math.max(8, vw - width - 8)),
    y: clamp(yBase, 8, Math.max(8, vh - height - 8)),
  };
}

export default function FloatingWindow(props: FloatingWindowProps) {
  const {
    storageKey,
    title,
    titleRight,
    children,
    defaultRect,
    defaultAnchor,
    snapAnchorOnMount,
    anchorMargin = 16,
    collapsedWidth,
    initialCollapsed,
    defaultCollapsed,
    minWidth = 320,
    minHeight = 240,
    zIndexClassName = 'z-50',
    onClose,
  } = props;

  const headerHeight = 44;

  const initialRect = useMemo<Rect>(() => {
    const vw = typeof window !== 'undefined' ? window.innerWidth : 1200;
    const vh = typeof window !== 'undefined' ? window.innerHeight : 800;
    const w = clamp(Number(defaultRect?.width ?? 420), minWidth, Math.max(minWidth, vw - 16));
    const h = clamp(Number(defaultRect?.height ?? 520), minHeight, Math.max(minHeight, vh - 16));
    const anchored =
      defaultAnchor && defaultRect?.x == null && defaultRect?.y == null
        ? computeAnchoredXY({ anchor: defaultAnchor, width: w, height: h, margin: anchorMargin, vw, vh })
        : null;
    const x = clamp(Number(defaultRect?.x ?? anchored?.x ?? vw - w - 16), 8, Math.max(8, vw - w - 8));
    const y = clamp(Number(defaultRect?.y ?? anchored?.y ?? vh - h - 80), 8, Math.max(8, vh - h - 8));
    return { x, y, width: w, height: h };
  }, [anchorMargin, defaultAnchor, defaultRect?.height, defaultRect?.width, defaultRect?.x, defaultRect?.y, minHeight, minWidth]);

  const [rect, setRect] = useState<Rect>(initialRect);
  const [collapsed, setCollapsed] = useState<boolean>(Boolean(defaultCollapsed));

  const expandedRectRef = useRef<Rect | null>(null);

  const draggingRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    startRect: Rect;
  } | null>(null);

  const resizingRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    startRect: Rect;
    dir: ResizeDir;
  } | null>(null);

  useEffect(() => {
    const saved = safeParseJson<{ rect?: Rect; collapsed?: boolean; expandedRect?: Rect }>(localStorage.getItem(storageKey));
    const nextCollapsed =
      typeof initialCollapsed === 'boolean'
        ? initialCollapsed
        : typeof saved?.collapsed === 'boolean'
          ? saved.collapsed
          : Boolean(defaultCollapsed);
    const mergedRect = { ...initialRect, ...(saved?.rect ?? {}) };

    if (saved?.expandedRect) expandedRectRef.current = saved.expandedRect;
    if (!nextCollapsed) expandedRectRef.current = mergedRect;

    if (snapAnchorOnMount && defaultAnchor) {
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const anchored = computeAnchoredXY({
        anchor: defaultAnchor,
        width: mergedRect.width,
        height: nextCollapsed ? headerHeight : mergedRect.height,
        margin: anchorMargin,
        vw,
        vh,
      });
      setRect({ ...mergedRect, ...anchored });
      setCollapsed(nextCollapsed);
      return;
    }

    setRect(mergedRect);
    setCollapsed(nextCollapsed);
  }, [anchorMargin, defaultAnchor, defaultCollapsed, headerHeight, initialCollapsed, initialRect, snapAnchorOnMount, storageKey]);

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ rect, collapsed, expandedRect: expandedRectRef.current }));
    } catch {
    }
  }, [collapsed, rect, storageKey]);

  useEffect(() => {
    if (!collapsed) expandedRectRef.current = rect;
  }, [collapsed, rect]);

  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      const drag = draggingRef.current;
      const resize = resizingRef.current;
      const vh = window.innerHeight;
      const vw = window.innerWidth;

      if (drag && e.pointerId === drag.pointerId) {
        const dx = e.clientX - drag.startX;
        const dy = e.clientY - drag.startY;
        setRect((prev) => ({
          ...prev,
          x: clamp(drag.startRect.x + dx, 8, Math.max(8, vw - drag.startRect.width - 8)),
          y: clamp(drag.startRect.y + dy, 8, Math.max(8, vh - (collapsed ? headerHeight : drag.startRect.height) - 8)),
        }));
        return;
      }

      if (resize && e.pointerId === resize.pointerId) {
        const dx = e.clientX - resize.startX;
        const dy = e.clientY - resize.startY;

        const start = resize.startRect;
        const right = start.x + start.width;
        const bottom = start.y + start.height;

        let nextX = start.x;
        let nextY = start.y;
        let nextW = start.width;
        let nextH = start.height;

        if (resize.dir.includes('e')) {
          nextW = clamp(start.width + dx, minWidth, Math.max(minWidth, vw - start.x - 8));
        }
        if (resize.dir.includes('s')) {
          nextH = clamp(start.height + dy, minHeight, Math.max(minHeight, vh - start.y - 8));
        }
        if (resize.dir.includes('w')) {
          const rawW = start.width - dx;
          const maxW = Math.max(minWidth, right - 8);
          nextW = clamp(rawW, minWidth, maxW);
          nextX = right - nextW;
        }
        if (resize.dir.includes('n')) {
          const rawH = start.height - dy;
          const maxH = Math.max(minHeight, bottom - 8);
          nextH = clamp(rawH, minHeight, maxH);
          nextY = bottom - nextH;
        }

        nextX = clamp(nextX, 8, Math.max(8, vw - nextW - 8));
        nextY = clamp(nextY, 8, Math.max(8, vh - (collapsed ? headerHeight : nextH) - 8));
        nextW = clamp(nextW, minWidth, Math.max(minWidth, vw - nextX - 8));
        nextH = clamp(nextH, minHeight, Math.max(minHeight, vh - nextY - 8));

        setRect(() => ({
          x: nextX,
          y: nextY,
          width: nextW,
          height: nextH,
        }));
        return;
      }
    };

    const onUp = (e: PointerEvent) => {
      const drag = draggingRef.current;
      if (drag && e.pointerId === drag.pointerId) draggingRef.current = null;
      const resize = resizingRef.current;
      if (resize && e.pointerId === resize.pointerId) resizingRef.current = null;
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };
  }, [collapsed, headerHeight, minHeight, minWidth, rect.height, rect.width]);

  useEffect(() => {
    const onResize = () => {
      const vh = window.innerHeight;
      const vw = window.innerWidth;
      setRect((prev) => {
        const w = clamp(prev.width, minWidth, Math.max(minWidth, vw - 16));
        const h = clamp(prev.height, minHeight, Math.max(minHeight, vh - 16));
        const x = clamp(prev.x, 8, Math.max(8, vw - w - 8));
        const y = clamp(prev.y, 8, Math.max(8, vh - (collapsed ? headerHeight : h) - 8));
        return x === prev.x && y === prev.y && w === prev.width && h === prev.height ? prev : { x, y, width: w, height: h };
      });
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [collapsed, headerHeight, minHeight, minWidth]);

  const height = collapsed ? headerHeight : rect.height;
  const resolvedTitleRight = typeof titleRight === 'function' ? titleRight(collapsed) : titleRight;

  return (
    <div
      className={`fixed ${zIndexClassName} rounded-lg border border-gray-200 bg-white shadow-lg overflow-hidden`}
      style={{ left: rect.x, top: rect.y, width: rect.width, height }}
    >
      <div
        data-no-drag
        className="absolute left-0 top-0 h-1 w-full cursor-n-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'n' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute left-0 bottom-0 h-1 w-full cursor-s-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 's' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute left-0 top-0 w-1 h-full cursor-w-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'w' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute right-0 top-0 w-1 h-full cursor-e-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'e' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute left-0 top-0 h-3 w-3 cursor-nw-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'nw' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute right-0 top-0 h-3 w-3 cursor-ne-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'ne' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute left-0 bottom-0 h-3 w-3 cursor-sw-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'sw' };
          e.preventDefault();
        }}
      />
      <div
        data-no-drag
        className="absolute right-0 bottom-0 h-3 w-3 cursor-se-resize z-20"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          resizingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect, dir: 'se' };
          e.preventDefault();
        }}
      />
      <div
        className="h-11 px-3 flex items-center justify-between gap-2 border-b border-gray-200 bg-gray-50 select-none cursor-grab active:cursor-grabbing"
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          if ((e.target as HTMLElement | null)?.closest?.('[data-no-drag]')) return;
          draggingRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startRect: rect };
        }}
      >
        <div className="min-w-0 flex items-center gap-2">
          <div className="text-sm font-medium text-gray-900 truncate">{title}</div>
          {resolvedTitleRight ? (
            <div className="shrink-0" data-no-drag>
              {resolvedTitleRight}
            </div>
          ) : null}
        </div>
        <div className="flex items-center gap-2" data-no-drag>
          {onClose ? (
            <button
              type="button"
              className="text-xs px-2 py-1 rounded-md border border-gray-300 bg-white hover:bg-gray-50"
              onClick={() => onClose()}
            >
              关闭
            </button>
          ) : null}
          <button
            type="button"
            className="text-xs px-2 py-1 rounded-md border border-gray-300 bg-white hover:bg-gray-50"
            onClick={() => {
              const vw = window.innerWidth;
              const vh = window.innerHeight;

              if (!collapsed) {
                expandedRectRef.current = rect;
                const right = rect.x + rect.width;
                const bottom = rect.y + rect.height;
                const nextW = clamp(Number(collapsedWidth ?? rect.width), minWidth, Math.max(minWidth, vw - 16));
                const nextX = clamp(right - nextW, 8, Math.max(8, vw - nextW - 8));
                const nextY = clamp(bottom - headerHeight, 8, Math.max(8, vh - headerHeight - 8));
                setRect((prev) => ({ ...prev, x: nextX, y: nextY, width: nextW }));
                setCollapsed(true);
                return;
              }

              const expanded = expandedRectRef.current ?? rect;
              const right = rect.x + rect.width;
              const bottom = rect.y + headerHeight;
              const nextW = clamp(expanded.width, minWidth, Math.max(minWidth, vw - 16));
              const nextH = clamp(expanded.height, minHeight, Math.max(minHeight, vh - 16));
              const nextX = clamp(right - nextW, 8, Math.max(8, vw - nextW - 8));
              const nextY = clamp(bottom - nextH, 8, Math.max(8, vh - nextH - 8));
              setRect((prev) => ({ ...prev, x: nextX, y: nextY, width: nextW, height: nextH }));
              setCollapsed(false);
            }}
          >
            {collapsed ? '展开' : '折叠'}
          </button>
        </div>
      </div>

      {!collapsed ? (
        <div className="h-[calc(100%-44px)] overflow-hidden">
          {children}
        </div>
      ) : null}
    </div>
  );
}
