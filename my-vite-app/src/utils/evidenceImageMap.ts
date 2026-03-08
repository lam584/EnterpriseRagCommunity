import { resolveAssetUrl } from './urlUtils';

function toRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function toFiniteNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function normalizeString(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return '';
}

function hasImageExtension(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed) return false;
  const withoutQuery = trimmed.split('?')[0] ?? trimmed;
  const withoutHash = withoutQuery.split('#')[0] ?? withoutQuery;
  return /\.(png|jpe?g|gif|webp|bmp|svg|tiff?)$/i.test(withoutHash);
}

function isLikelyImageAttachment(attachment: Record<string, unknown>): boolean {
  const mimeType = normalizeString(attachment.mimeType).toLowerCase();
  if (mimeType.startsWith('image/')) return true;

  const fileName = normalizeString(attachment.fileName);
  if (hasImageExtension(fileName)) return true;

  const url = normalizeString(attachment.url);
  if (hasImageExtension(url)) return true;

  const width = toFiniteNumber(attachment.width);
  const height = toFiniteNumber(attachment.height);
  return (width ?? 0) > 0 && (height ?? 0) > 0;
}

function addLookupKey(map: Record<string, string>, key: unknown, url: string): void {
  const normalized = normalizeString(key);
  if (!normalized) return;
  map[normalized] = url;
}

function parseJsonRecord(value: unknown): Record<string, unknown> | null {
  const record = toRecord(value);
  if (record) return record;
  const raw = normalizeString(value);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return toRecord(parsed);
  } catch {
    return null;
  }
}

function getExtractedImages(attachment: Record<string, unknown>): Array<Record<string, unknown>> {
  const direct = attachment.extractedImages;
  if (Array.isArray(direct)) return direct.map((item) => toRecord(item)).filter((item): item is Record<string, unknown> => Boolean(item));

  const metadata = parseJsonRecord(attachment.extractedMetadata) ?? parseJsonRecord(attachment.extractedMetadataJson) ?? parseJsonRecord(attachment.extractedMetadataJsonSnippet);
  const extracted = metadata?.extractedImages;
  if (!Array.isArray(extracted)) return [];
  return extracted.map((item) => toRecord(item)).filter((item): item is Record<string, unknown> => Boolean(item));
}

function addDerivedImage(map: Record<string, string>, image: Record<string, unknown>, ordinal: number): number {
  const rawUrl = normalizeString(image.url);
  if (!rawUrl) return ordinal;

  const resolvedUrl = resolveAssetUrl(rawUrl) ?? rawUrl;
  const preferredIndex = toFiniteNumber(image.index);
  const nextOrdinal = preferredIndex != null && preferredIndex > 0 ? Math.max(ordinal, Math.floor(preferredIndex)) : ordinal + 1;
  map[`img_${nextOrdinal}`] = resolvedUrl;
  addLookupKey(map, image.placeholder, resolvedUrl);
  addLookupKey(map, image.fileName, resolvedUrl);
  addLookupKey(map, image.fileAssetId, resolvedUrl);
  return nextOrdinal;
}

function isLikelyImageUrl(value: string): boolean {
  const normalized = value.trim();
  if (!normalized) return false;
  if (hasImageExtension(normalized)) return true;
  return normalized.includes('/derived-images/') || normalized.includes('/uploads/');
}

function collectImageUrls(node: unknown, out: string[], seen: Set<string>, depth: number): void {
  if (depth > 6 || node == null) return;
  if (Array.isArray(node)) {
    for (const item of node) collectImageUrls(item, out, seen, depth + 1);
    return;
  }
  const record = toRecord(node);
  if (!record) return;
  for (const [key, value] of Object.entries(record)) {
    if (key === 'images' && Array.isArray(value)) {
      for (const item of value) {
        if (typeof item === 'string') {
          const normalized = item.trim();
          if (!isLikelyImageUrl(normalized) || seen.has(normalized)) continue;
          seen.add(normalized);
          out.push(normalized);
          continue;
        }
        const itemRecord = toRecord(item);
        const url = normalizeString(itemRecord?.url);
        if (!url || !isLikelyImageUrl(url) || seen.has(url)) continue;
        seen.add(url);
        out.push(url);
      }
      continue;
    }
    collectImageUrls(value, out, seen, depth + 1);
  }
}

export function extractLatestRunImageUrls(latestRun: unknown): string[] {
  const out: string[] = [];
  collectImageUrls(latestRun, out, new Set<string>(), 0);
  return out;
}

function addImage(map: Record<string, string>, ordinal: number, attachment: Record<string, unknown>): number {
  const rawUrl = normalizeString(attachment.url);
  if (!rawUrl) return ordinal;
  if (!isLikelyImageAttachment(attachment)) return ordinal;

  const resolvedUrl = resolveAssetUrl(rawUrl) ?? rawUrl;
  const nextOrdinal = ordinal + 1;
  map[`img_${nextOrdinal}`] = resolvedUrl;
  addLookupKey(map, attachment.id, resolvedUrl);
  addLookupKey(map, attachment.fileAssetId, resolvedUrl);
  addLookupKey(map, attachment.fileName, resolvedUrl);
  return nextOrdinal;
}

export function buildEvidenceImageUrlMap(options: {
  attachments?: unknown[] | null;
  extraImageUrls?: Array<string | null | undefined>;
}): Record<string, string> {
  const map: Record<string, string> = {};
  const seenUrls = new Set<string>();
  let ordinal = 0;

  const registerUrl = (url: string) => {
    const resolvedUrl = resolveAssetUrl(url) ?? url;
    if (!resolvedUrl || seenUrls.has(resolvedUrl)) return;
    seenUrls.add(resolvedUrl);
    ordinal += 1;
    map[`img_${ordinal}`] = resolvedUrl;
  };

  const attachments = Array.isArray(options.attachments) ? options.attachments : [];
  for (const attachment of attachments) {
    if (!attachment || typeof attachment !== 'object' || Array.isArray(attachment)) continue;
    const attachmentRecord = attachment as Record<string, unknown>;
    const rawUrl = normalizeString(attachmentRecord.url);
    if (!rawUrl) continue;
    const resolvedUrl = resolveAssetUrl(rawUrl) ?? rawUrl;
    if (seenUrls.has(resolvedUrl)) {
      addLookupKey(map, attachmentRecord.id, resolvedUrl);
      addLookupKey(map, attachmentRecord.fileAssetId, resolvedUrl);
      addLookupKey(map, attachmentRecord.fileName, resolvedUrl);
      continue;
    }
    const nextOrdinal = addImage(map, ordinal, attachmentRecord);
    if (nextOrdinal !== ordinal) {
      ordinal = nextOrdinal;
      seenUrls.add(resolvedUrl);
    }

    const extractedImages = getExtractedImages(attachmentRecord);
    for (const image of extractedImages) {
      const imageUrl = normalizeString(image.url);
      if (!imageUrl) continue;
      const resolvedImageUrl = resolveAssetUrl(imageUrl) ?? imageUrl;
      const nextImageOrdinal = addDerivedImage(map, image, ordinal);
      ordinal = nextImageOrdinal;
      seenUrls.add(resolvedImageUrl);
    }
  }

  const extraImageUrls = Array.isArray(options.extraImageUrls) ? options.extraImageUrls : [];
  for (const url of extraImageUrls) {
    const normalized = normalizeString(url);
    if (!normalized) continue;
    registerUrl(normalized);
  }

  return map;
}