import type { JSX } from 'react';

export type StatButtonProps = {
  label: string;
  count: number;
  onClick?: () => void;
  tone?: string;
  active?: boolean;
  disabled?: boolean;
};

export type StatButtonComponent = (props: StatButtonProps) => JSX.Element;

export function pickStatButton(mod: unknown): StatButtonComponent {
  if (mod && typeof mod === 'object') {
    const m = mod as Record<string, unknown>;
    const candidate =
      (m.default as unknown) ??
      (m.StatButton as unknown) ??
      Object.values(m).find((value) => typeof value === 'function');

    if (typeof candidate === 'function') {
      return candidate as StatButtonComponent;
    }
  }

  // Fallback so the page won't crash if the module shape changes.
  return (() => <span />) as StatButtonComponent;
}
