import type { PostDTO } from '../services/postService';
import { resolveAssetUrl } from './urlUtils';

function getNested(obj: unknown, path: string[]): unknown {
  let cur: unknown = obj;
  for (const k of path) {
    if (!cur || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[k];
  }
  return cur;
}

function asString(v: unknown): string | undefined {
  if (typeof v === 'string') return v;
  return undefined;
}

function firstItem(arr: unknown): unknown {
  return Array.isArray(arr) && arr.length > 0 ? arr[0] : undefined;
}

/**
 * 封面缩略图 URL 解析（多键兜底）：
 * 1) metadata.cover.thumbUrl
 * 2) metadata.cover.thumbnailUrl
 * 3) metadata.cover.url
 * 4) metadata.coverImage / metadata.coverUrl / metadata.thumbnailUrl
 * 5) metadata.attachments[0].thumbUrl
 * 6) metadata.attachments[0].url
 */
export function getPostCoverThumbUrl(post: Pick<PostDTO, 'metadata'>): string | undefined {
  const meta = post?.metadata as unknown;
  const candidates: unknown[] = [
    getNested(meta, ['cover', 'thumbUrl']),
    getNested(meta, ['cover', 'thumbnailUrl']),
    getNested(meta, ['cover', 'url']),
    getNested(meta, ['coverImage']),
    getNested(meta, ['coverUrl']),
    getNested(meta, ['thumbnailUrl']),
    getNested(firstItem(getNested(meta, ['attachments'])), ['thumbUrl']),
    getNested(firstItem(getNested(meta, ['attachments'])), ['url']),
  ];

  for (const c of candidates) {
    const s = asString(c);
    const resolved = resolveAssetUrl(s);
    if (resolved) return resolved;
  }
  return undefined;
}

export function formatPostTime(post: Pick<PostDTO, 'publishedAt' | 'createdAt'>): string {
  const raw = post.publishedAt || post.createdAt;
  if (!raw) return '';
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString();
}

export function getPostExcerpt(content: string | undefined, maxLen: number = 140): string {
  if (!content) return '';
  const plain = content
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`[^`]*`/g, '')
    .replace(/[#>*_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (plain.length <= maxLen) return plain;
  return plain.slice(0, maxLen).trimEnd() + '…';
}
