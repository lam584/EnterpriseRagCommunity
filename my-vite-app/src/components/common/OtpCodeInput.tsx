import { useEffect, useMemo, useRef } from 'react';

export interface OtpCodeInputProps {
  digits: number;
  value: string;
  onChange: (value: string) => void;
  onComplete?: (value: string) => void;
  disabled?: boolean;
  autoFocus?: boolean;
  placeholder?: string;
  containerClassName?: string;
  inputClassName?: string;
}

function normalizeDigits(value: string, maxLen: number) {
  const onlyDigits = (value || '').replace(/\D/g, '');
  return onlyDigits.slice(0, Math.max(0, maxLen));
}

export default function OtpCodeInput(props: OtpCodeInputProps) {
  const {
    digits,
    value,
    onChange,
    onComplete,
    disabled,
    autoFocus,
    placeholder,
    containerClassName,
    inputClassName,
  } = props;

  const safeDigits = Number.isFinite(Number(digits)) ? Math.max(1, Math.min(12, Math.trunc(Number(digits)))) : 6;
  const normalized = useMemo(() => normalizeDigits(value, safeDigits), [value, safeDigits]);
  const lastCompletedRef = useRef<string | null>(null);
  const inputRefs = useRef<Array<HTMLInputElement | null>>([]);

  const cells = useMemo(() => {
    const arr = new Array(safeDigits).fill('');
    for (let i = 0; i < safeDigits; i++) arr[i] = normalized[i] ?? '';
    return arr;
  }, [normalized, safeDigits]);

  useEffect(() => {
    if (!autoFocus) return;
    if (disabled) return;
    const firstEmpty = cells.findIndex((c) => !c);
    const idx = firstEmpty === -1 ? Math.max(0, safeDigits - 1) : firstEmpty;
    inputRefs.current[idx]?.focus();
  }, [autoFocus, disabled, cells, safeDigits]);

  useEffect(() => {
    if (disabled) return;
    if (!onComplete) return;
    if (normalized.length !== safeDigits) {
      lastCompletedRef.current = null;
      return;
    }
    if (lastCompletedRef.current === normalized) return;
    lastCompletedRef.current = normalized;
    onComplete(normalized);
  }, [disabled, normalized, onComplete, safeDigits]);

  const setFocus = (idx: number) => {
    const next = Math.max(0, Math.min(safeDigits - 1, idx));
    inputRefs.current[next]?.focus();
  };

  const updateAt = (idx: number, nextChar: string) => {
    const nextCells = cells.slice();
    nextCells[idx] = nextChar;
    const nextValue = nextCells.join('');
    onChange(normalizeDigits(nextValue, safeDigits));
  };

  const fillFrom = (startIndex: number, chunk: string) => {
    const normalizedChunk = normalizeDigits(chunk, safeDigits);
    if (!normalizedChunk) return;
    const nextCells = cells.slice();
    let j = 0;
    for (let i = startIndex; i < safeDigits; i++) {
      if (j >= normalizedChunk.length) break;
      nextCells[i] = normalizedChunk[j];
      j += 1;
    }
    const nextValue = normalizeDigits(nextCells.join(''), safeDigits);
    onChange(nextValue);
    const nextFocus = Math.min(safeDigits - 1, startIndex + normalizedChunk.length);
    if (nextValue.length >= safeDigits) {
      inputRefs.current[safeDigits - 1]?.blur();
    } else {
      setFocus(nextFocus);
    }
  };

  const baseInputClassName =
    'w-9 h-10 sm:w-10 flex-none text-center border border-gray-300 rounded-md bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed';
  const mergedInputClassName = inputClassName ? `${baseInputClassName} ${inputClassName}` : baseInputClassName;
  const baseContainerClassName = 'flex flex-nowrap gap-2 overflow-x-auto max-w-full';
  const mergedContainerClassName = containerClassName ? `${baseContainerClassName} ${containerClassName}` : baseContainerClassName;

  return (
    <div className={mergedContainerClassName}>
      {cells.map((cell, idx) => (
        <input
          key={idx}
          ref={(el) => {
            inputRefs.current[idx] = el;
          }}
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          className={mergedInputClassName}
          value={cell}
          placeholder={idx === 0 ? placeholder : undefined}
          disabled={disabled}
          onChange={(e) => {
            const raw = e.target.value ?? '';
            const digitsOnly = raw.replace(/\D/g, '');
            if (!digitsOnly) {
              updateAt(idx, '');
              return;
            }
            if (digitsOnly.length === 1) {
              updateAt(idx, digitsOnly);
              setFocus(idx + 1);
              return;
            }
            fillFrom(idx, digitsOnly);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace') {
              if (cells[idx]) {
                updateAt(idx, '');
                e.preventDefault();
                return;
              }
              if (idx > 0) {
                updateAt(idx - 1, '');
                setFocus(idx - 1);
                e.preventDefault();
              }
              return;
            }
            if (e.key === 'ArrowLeft') {
              setFocus(idx - 1);
              e.preventDefault();
              return;
            }
            if (e.key === 'ArrowRight') {
              setFocus(idx + 1);
              e.preventDefault();
              return;
            }
            if (e.key === 'Enter') {
              if (normalized.length === safeDigits && onComplete && !disabled) onComplete(normalized);
              return;
            }
          }}
          onPaste={(e) => {
            const text = e.clipboardData.getData('text');
            if (!text) return;
            e.preventDefault();
            fillFrom(idx, text);
          }}
          onFocus={(e) => {
            e.currentTarget.select();
          }}
        />
      ))}
    </div>
  );
}
