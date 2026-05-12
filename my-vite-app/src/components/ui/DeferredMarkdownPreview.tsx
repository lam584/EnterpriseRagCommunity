import { startTransition, useEffect, useRef, useState, type ComponentType } from 'react';
import type { Components } from 'react-markdown';
import { getMarkdownPreviewMode, type MarkdownPreviewMode } from '../../utils/markdownUtils';

type MarkdownPreviewProps = {
  markdown: string;
  className?: string;
  components?: Components;
  active?: boolean;
  observeViewport?: boolean;
  rootMargin?: string;
  preloadOnIdle?: boolean;
  preloadRootMargin?: string;
};

type IdleCallbackHandle = number | ReturnType<typeof globalThis.setTimeout>;

function scheduleIdleTask(task: () => void): IdleCallbackHandle {
  if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
    return (window as Window & {
      requestIdleCallback: (callback: () => void, options?: { timeout?: number }) => number;
    }).requestIdleCallback(task, { timeout: 1200 });
  }

  return globalThis.setTimeout(task, 280);
}

function cancelIdleTask(handle: IdleCallbackHandle) {
  if (typeof window !== 'undefined' && 'cancelIdleCallback' in window) {
    (window as Window & { cancelIdleCallback: (id: number) => void }).cancelIdleCallback(Number(handle));
    return;
  }

  globalThis.clearTimeout(handle);
}

const markdownPreviewComponents: Partial<Record<MarkdownPreviewMode, ComponentType<MarkdownPreviewProps>>> = {};
const markdownPreviewPromises: Partial<Record<MarkdownPreviewMode, Promise<ComponentType<MarkdownPreviewProps>>>> = {};

function loadMarkdownPreview(mode: MarkdownPreviewMode): Promise<ComponentType<MarkdownPreviewProps>> {
  const cachedComponent = markdownPreviewComponents[mode];
  if (cachedComponent) {
    return Promise.resolve(cachedComponent);
  }

  const cachedPromise = markdownPreviewPromises[mode];
  if (cachedPromise) {
    return cachedPromise;
  }

  const loader =
    mode === 'plain'
      ? () => import('./PlainTextPreview')
      : mode === 'rich'
        ? () => import('./MarkdownRichPreview')
        : () => import('./MarkdownPreview');

  const promise = loader().then((module) => {
    markdownPreviewComponents[mode] = module.default;
    return module.default;
  });

  markdownPreviewPromises[mode] = promise;
  return promise;
}

export default function DeferredMarkdownPreview(props: MarkdownPreviewProps) {
  const {
    markdown,
    className,
    active = false,
    observeViewport = true,
    rootMargin = '0px',
    preloadOnIdle = true,
    preloadRootMargin = '320px',
  } = props;
  const mode = getMarkdownPreviewMode(markdown, { hasCustomComponents: Boolean(props.components) });
  const [Renderer, setRenderer] = useState<ComponentType<MarkdownPreviewProps> | null>(() => markdownPreviewComponents[mode] ?? null);
  const [isVisible, setIsVisible] = useState(false);
  const [isNearViewport, setIsNearViewport] = useState(false);
  const hostRef = useRef<HTMLDivElement | null>(null);
  const hasContent = markdown.trim().length > 0;

  useEffect(() => {
    setRenderer(() => markdownPreviewComponents[mode] ?? null);
  }, [mode]);

  useEffect(() => {
    if (hasContent) {
      return;
    }

    setIsVisible(false);
    setIsNearViewport(false);
  }, [hasContent]);

  useEffect(() => {
    if (!hasContent || active || !observeViewport || isVisible) {
      return;
    }

    const host = hostRef.current;
    if (!host) {
      return;
    }

    if (typeof IntersectionObserver === 'undefined') {
      setIsVisible(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting || entry.intersectionRatio > 0)) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin },
    );

    observer.observe(host);
    return () => observer.disconnect();
  }, [active, hasContent, isVisible, observeViewport, rootMargin]);

  useEffect(() => {
    if (!hasContent || active || !observeViewport || !preloadOnIdle || isVisible || isNearViewport) {
      return;
    }

    const host = hostRef.current;
    if (!host) {
      return;
    }

    if (typeof IntersectionObserver === 'undefined') {
      setIsNearViewport(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting || entry.intersectionRatio > 0)) {
          setIsNearViewport(true);
          observer.disconnect();
        }
      },
      { rootMargin: preloadRootMargin },
    );

    observer.observe(host);
    return () => observer.disconnect();
  }, [active, hasContent, isNearViewport, isVisible, observeViewport, preloadOnIdle, preloadRootMargin]);

  const shouldLoad = hasContent && (active || isVisible);

  useEffect(() => {
    if (!hasContent || Renderer || shouldLoad || !preloadOnIdle) {
      return;
    }

    if (!observeViewport || !isNearViewport) {
      return;
    }

    const handle = scheduleIdleTask(() => {
      void loadMarkdownPreview(mode);
    });

    return () => cancelIdleTask(handle);
  }, [Renderer, hasContent, isNearViewport, mode, observeViewport, preloadOnIdle, shouldLoad]);

  useEffect(() => {
    if (Renderer || !shouldLoad) {
      return;
    }

    let cancelled = false;
    void loadMarkdownPreview(mode).then((nextRenderer) => {
      if (cancelled) {
        return;
      }

      startTransition(() => {
        setRenderer(() => nextRenderer);
      });
    });

    return () => {
      cancelled = true;
    };
  }, [Renderer, mode, shouldLoad]);

  if (!hasContent) {
    return <div className={`max-w-none text-sm text-gray-900 ${className ?? ''}`} />;
  }

  if (Renderer) {
    return <Renderer {...props} />;
  }

  return (
    <div
      ref={hostRef}
      className={`max-w-none text-sm ${markdown.trim() ? 'text-gray-500' : 'text-gray-900'} ${className ?? ''}`}
    >
      {markdown.trim() ? '加载预览中...' : null}
    </div>
  );
}