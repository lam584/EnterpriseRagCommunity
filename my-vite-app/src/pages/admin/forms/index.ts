import type { ComponentType, LazyExoticComponent } from 'react';
import { createElement, lazy } from 'react';

export type AnyPropsComponent = ComponentType<Record<string, unknown>>;
export type FormLoader = () => Promise<{ default: AnyPropsComponent }>;

// Lazy loaders: keep paths static so Vite can create separate chunks.
// Note: Vite ignores webpackChunkName by default, but keeping the comment doesn't hurt and helps future migration.
export const formsLoaders: Partial<Record<string, FormLoader>> = {
  'board': () => import('./content/board'),
  'board-management': () => import('./content/BoardManagement'),
  'post': () => import('./content/post'),
  'comment': () => import('./content/comment'),
  'tags': () => import('./content/tags'),

  'queue': () => import('./review/queue'),
  'rules': () => import('./review/rules'),
  'embed': () => import('./review/embed'),
  'llm': () => import('./review/llm'),
  'fallback': () => import('./review/fallback'),
  'logs': () => import('./review/logs'),
  'risk-tags': () => import('./review/risk-tags'),

  'title-gen': () => import('./semantic/title-gen'),
  'multi-label': () => import('./semantic/multi-label'),
  'summary': () => import('./semantic/summary'),
  'translate': () => import('./semantic/translate'),

  'index': () => import('./retrieval/vector-index'),
  'hybrid': () => import('./retrieval/hybrid'),
  'context': () => import('./retrieval/context'),
  'citation': () => import('./retrieval/citation'),

  'metrics': () => import('./metrics/metrics'),
  'abtest': () => import('./metrics/abtest'),
  'token': () => import('./metrics/token'),
  'label-quality': () => import('./metrics/label-quality'),
  'cost': () => import('./metrics/cost'),

  'user-role': () => import('./users/user-role'),
  'roles': () => import('./users/roles'),
  'matrix': () => import('./users/matrix'),
  '2fa': () => import('./users/2fa'),
};

function UnknownForm({ id }: { id?: string }) {
  return createElement(
    'div',
    { className: 'bg-white rounded-lg shadow p-4 space-y-2' },
    createElement('h3', { className: 'text-lg font-semibold' }, '未找到表单'),
    createElement('div', { className: 'text-sm text-gray-600' }, `active=${id ?? '—'}`)
  );
}

const lazyCache = new Map<string, LazyExoticComponent<AnyPropsComponent>>();
const preloadCache = new Map<string, Promise<unknown>>();

export function getLazyForm(id: string | undefined) {
  const key = (id ?? '').trim();
  if (!key) return undefined;

  const cached = lazyCache.get(key);
  if (cached) return cached;

  const loader = formsLoaders[key];
  const fallbackLoader: FormLoader = async () => ({
    default: UnknownForm as AnyPropsComponent,
  });

  const Lazy = lazy(loader ?? fallbackLoader);

  lazyCache.set(key, Lazy);
  return Lazy;
}

export function preloadForm(id: string | undefined) {
  const key = (id ?? '').trim();
  if (!key) return;
  const loader = formsLoaders[key];
  if (!loader) return;

  // Avoid spamming duplicate dynamic imports on rapid hover / repeated renders.
  const existing = preloadCache.get(key);
  if (existing) return;
  preloadCache.set(key, loader());
}
