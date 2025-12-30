import {useMemo} from "react";

export type HotScoreBadgeProps = {
  value: unknown;
  /**
   * - text: list inline text for PostCard action row
   * - badge: small pill for detail page
   */
  variant?: 'text' | 'badge';
  className?: string;
};

function formatHotScore(value: unknown): string | null {
  if (typeof value !== 'number') return null;
  if (!Number.isFinite(value)) return String(value);
  return value.toFixed(1);
}

export default function HotScoreBadge({ value, variant = 'text', className }: HotScoreBadgeProps) {
  const label = useMemo(() => formatHotScore(value), [value]);
  if (!label) return null;

  if (variant === 'badge') {
    return (
      <span
        className={['px-2 py-1 rounded-md bg-gray-50 border border-gray-200 tabular-nums', className]
          .filter(Boolean)
          .join(' ')}
      >
        热度 {label}
      </span>
    );
  }

  return (
    <span className={['text-xs text-gray-500 tabular-nums', className].filter(Boolean).join(' ')}>
      热度 {label}
    </span>
  );
}
