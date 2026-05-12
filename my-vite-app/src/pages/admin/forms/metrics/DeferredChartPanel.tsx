import { useEffect, useRef, useState, type ReactNode } from 'react';

type DeferredChartPanelProps = {
  children: ReactNode;
  minHeight?: number;
  rootMargin?: string;
  placeholder?: ReactNode;
  className?: string;
};

export default function DeferredChartPanel({
  children,
  minHeight = 240,
  rootMargin = '0px',
  placeholder,
  className,
}: DeferredChartPanelProps) {
  const [active, setActive] = useState(false);
  const hostRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (active) {
      return;
    }

    const host = hostRef.current;
    if (!host) {
      return;
    }

    if (typeof IntersectionObserver === 'undefined') {
      setActive(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting || entry.intersectionRatio > 0)) {
          setActive(true);
          observer.disconnect();
        }
      },
      { rootMargin },
    );

    observer.observe(host);
    return () => observer.disconnect();
  }, [active, rootMargin]);

  return (
    <div ref={hostRef} className={className}>
      {active
        ? children
        : (placeholder ?? (
            <div
              className="rounded border bg-white p-3 text-sm text-gray-500"
              style={{ minHeight: `${minHeight}px` }}
            >
              图表进入视口后加载...
            </div>
          ))}
    </div>
  );
}