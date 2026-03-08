import { useEffect, useMemo, useState } from 'react';
import { tokenizeText } from '../services/opensearchTokenService';

type TokenizedTextState = {
  text: string;
  resolved: boolean;
};

const cache = new Map<string, TokenizedTextState>();
const inflight = new Map<string, Promise<TokenizedTextState>>();

function normalizeText(v: string | null | undefined): string {
  return (v ?? '').trim();
}

function cacheKey(text: string, maxTokens: number): string {
  return `${maxTokens}::${text}`;
}

function truncateWithTokenizerTokens(text: string, tokens: unknown[], maxTokens: number): string {
  if (tokens.length <= maxTokens) return text;
  const joined = tokens.slice(0, maxTokens).map((token) => String(token ?? '')).join('').trimEnd();
  return joined ? `${joined}…` : text;
}

async function resolveTokenLimitedText(text: string, maxTokens: number): Promise<TokenizedTextState> {
  const normalized = normalizeText(text);
  if (!normalized) return { text: '', resolved: true };
  const key = cacheKey(normalized, maxTokens);
  const cached = cache.get(key);
  if (cached) return cached;

  const pending = inflight.get(key) ?? (async () => {
    try {
      const response = await tokenizeText(normalized);
      const tokens = Array.isArray(response.result?.tokens) ? response.result.tokens : [];
      const value = {
        text: tokens.length ? truncateWithTokenizerTokens(normalized, tokens, maxTokens) : normalized,
        resolved: true,
      };
      cache.set(key, value);
      return value;
    } catch {
      const fallback = { text: normalized, resolved: false };
      cache.set(key, fallback);
      return fallback;
    } finally {
      inflight.delete(key);
    }
  })();

  inflight.set(key, pending);
  return pending;
}

export function useTokenizerLimitedText(text: string | null | undefined, maxTokens: number): TokenizedTextState {
  const normalized = useMemo(() => normalizeText(text), [text]);
  const key = useMemo(() => cacheKey(normalized, maxTokens), [maxTokens, normalized]);
  const [state, setState] = useState<TokenizedTextState>(() => cache.get(key) ?? { text: normalized, resolved: !normalized });

  useEffect(() => {
    if (!normalized) {
      setState({ text: '', resolved: true });
      return;
    }
    const cached = cache.get(key);
    if (cached) {
      setState(cached);
      return;
    }
    setState({ text: normalized, resolved: false });
    let cancelled = false;
    void resolveTokenLimitedText(normalized, maxTokens).then((result) => {
      if (cancelled) return;
      setState(result);
    });
    return () => {
      cancelled = true;
    };
  }, [key, maxTokens, normalized]);

  return state;
}