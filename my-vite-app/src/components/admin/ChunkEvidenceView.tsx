import { useEffect, useMemo, useState } from 'react';
import ImageLightbox from '../ui/ImageLightbox';
import { adminGetModerationChunkLogContent, type ModerationChunkContentPreview } from '../../services/moderationChunkReviewLogsService';
import { expandEvidenceContext } from '../../utils/evidence-context-display';

type EvidenceAnchor = { beforeContext: string; afterContext?: string };
type ParsedEvidence = { raw: string; text: string; anchor?: EvidenceAnchor; imagePlaceholders: string[]; imageId?: string };

const EVIDENCE_POLLUTION_MARKERS = [
  '[output_requirements]',
  '[global_memory]',
  '[prev_chunk_summary]',
  '[images]',
  '[text]',
  '只输出严格 json',
  '只输出严格json',
  '不要 markdown 代码块',
  '不要额外解释',
  'decision_suggestion',
  'risk_score',
  'label_taxonomy',
  'label_taxono',
];

const cache = new Map<number, ModerationChunkContentPreview>();
const inflight = new Map<number, Promise<ModerationChunkContentPreview>>();

function toInt(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.floor(v);
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    if (Number.isFinite(n)) return Math.floor(n);
  }
  return null;
}

function toRecord(v: unknown): Record<string, unknown> | null {
  if (!v || typeof v !== 'object') return null;
  return v as Record<string, unknown>;
}

function parseAnchorFromObject(o: Record<string, unknown>): EvidenceAnchor | undefined {
  const bc = o.before_context;
  if (typeof bc !== 'string' || !bc.trim()) return undefined;
  const ac = o.after_context;
  return { beforeContext: bc.trim(), afterContext: typeof ac === 'string' && ac.trim() ? ac.trim() : undefined };
}

function extractImagePlaceholders(text: string): string[] {
  const out = new Set<string>();
  const re = /\[\[IMAGE_(\d+)\]\]/g;
  for (;;) {
    const m = re.exec(text);
    if (!m) break;
    out.add(`[[IMAGE_${m[1]}]]`);
  }
  return Array.from(out.values());
}

function parseEvidenceItem(v: unknown): ParsedEvidence | null {
  const raw = v == null ? '' : typeof v === 'string' ? v : JSON.stringify(v);
  const t0 = raw.trim();
  if (!t0) return null;
  const cleaned = t0.replace(/^chunk-\d+\s*:\s*/i, '').trim();
  try {
    const parsed = JSON.parse(cleaned) as unknown;
    const o = toRecord(parsed);
    if (o) {
      const anchor = parseAnchorFromObject(o);
      const text = typeof o.text === 'string' && o.text.trim() ? o.text.trim() : '';
      const imgs: string[] = [];
      if (typeof o.image === 'string' && o.image.trim()) imgs.push(o.image.trim());
      if (typeof o.placeholder === 'string' && o.placeholder.trim()) imgs.push(o.placeholder.trim());
      const arr = o.images;
      if (Array.isArray(arr)) {
        for (const it of arr) {
          if (typeof it === 'string' && it.trim()) imgs.push(it.trim());
        }
      }
      const fromText = extractImagePlaceholders(cleaned);
      for (const x of fromText) imgs.push(x);
      const imagePlaceholders = Array.from(new Set(imgs)).filter((x) => x.includes('IMAGE_'));
      const imageId = typeof o.image_id === 'string' && o.image_id.trim() ? o.image_id.trim() : undefined;
      return { raw: t0, text, anchor, imagePlaceholders, imageId };
    }
  } catch {
  }
  const imagePlaceholders = extractImagePlaceholders(cleaned);
  return { raw: t0, text: cleaned || t0, imagePlaceholders, imageId: undefined };
}

function clipText(s: string, max: number): string {
  const t = s ?? '';
  if (t.length <= max) return t;
  return `${t.slice(0, max)}…`;
}

function isSuspiciousEvidenceText(text: string | null | undefined): boolean {
  const t = (text ?? '').trim();
  if (!t) return false;
  if (t.length > 320) return true;
  const lower = t.toLowerCase();
  return EVIDENCE_POLLUTION_MARKERS.some((m) => lower.includes(m));
}

function pickEvidenceByAnchor(anchor: EvidenceAnchor, preview: ModerationChunkContentPreview | null): string | null {
  if (!preview) return null;
  const text = typeof preview.text === 'string' ? preview.text : '';
  if (!text) return null;
  const bIdx = text.indexOf(anchor.beforeContext);
  if (bIdx < 0) return null;
  const vStart = bIdx + anchor.beforeContext.length;
  let vEnd: number;
  if (anchor.afterContext) {
    const aIdx = text.indexOf(anchor.afterContext, vStart);
    vEnd = aIdx >= 0 ? aIdx : Math.min(vStart + 100, text.length);
  } else {
    vEnd = Math.min(vStart + 100, text.length);
  }
  if (vEnd <= vStart) return null;
  const snippet = text.slice(vStart, vEnd).trim();
  return snippet ? clipText(snippet, 240) : null;
}

function useChunkContentPreview(chunkId: number | null | undefined, enabled: boolean) {
  const [data, setData] = useState<ModerationChunkContentPreview | null>(() => (chunkId && cache.has(chunkId) ? (cache.get(chunkId) as ModerationChunkContentPreview) : null));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled || !chunkId) {
      setData(null);
      return;
    }
    if (cache.has(chunkId)) {
      setData(cache.get(chunkId) as ModerationChunkContentPreview);
      return;
    }
    setData(null);
    setError(null);
    const controller = new AbortController();
    const run = async () => {
      try {
        const p = inflight.get(chunkId) ?? adminGetModerationChunkLogContent(chunkId, controller.signal);
        inflight.set(chunkId, p);
        const res = await p;
        cache.set(chunkId, res);
        inflight.delete(chunkId);
        setData(res);
      } catch (e) {
        inflight.delete(chunkId);
        if (controller.signal.aborted) return;
        setError(e instanceof Error ? e.message : String(e));
      }
    };
    void run();
    return () => controller.abort();
  }, [chunkId, enabled]);

  return { data, error };
}

export type ChunkEvidenceViewProps = {
  chunkId?: number | null;
  evidence?: unknown[] | null;
  compact?: boolean;
  maxThumbnails?: number;
};

export default function ChunkEvidenceView({ chunkId, evidence, compact, maxThumbnails }: ChunkEvidenceViewProps) {
  const enabled = Boolean(chunkId);
  const { data: preview } = useChunkContentPreview(chunkId ?? null, enabled);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  const items = useMemo(() => {
    const arr = Array.isArray(evidence) ? evidence : [];
    return arr
      .map(parseEvidenceItem)
      .filter((x): x is ParsedEvidence => Boolean(x))
      .filter((x) => Boolean(x.anchor) || x.imagePlaceholders.length > 0 || Boolean(x.text && x.text.trim()));
  }, [evidence]);

  const referenced = useMemo(() => {
    const s = new Set<string>();
    for (const it of items) {
      for (const ph of it.imagePlaceholders) s.add(ph);
    }
    return Array.from(s.values());
  }, [items]);

  const images = useMemo(() => {
    const imgs = Array.isArray(preview?.images) ? preview?.images : [];
    const normalized = imgs
      .map((x) => ({
        placeholder: typeof x.placeholder === 'string' ? x.placeholder.trim() : '',
        url: typeof x.url === 'string' ? x.url.trim() : '',
        index: toInt(x.index),
      }))
      .filter((x) => x.url);
    if (!referenced.length) return [];
    const refSet = new Set(referenced);
    return normalized.filter((x) => x.placeholder && refSet.has(x.placeholder));
  }, [preview?.images, referenced]);

  const maxThumbs = maxThumbnails ?? (compact ? 2 : 6);
  const shownImages = images.slice(0, Math.max(0, maxThumbs));

  return (
    <div className={compact ? 'space-y-1' : 'space-y-2'}>
      {shownImages.length ? (
        <div className="flex flex-wrap gap-2">
          {shownImages.map((img, i) => {
            const label = `${img.placeholder || `IMAGE_${img.index ?? i}`}`;
            return (
              <button
                key={`${img.url}-${i}`}
                type="button"
                className="border rounded p-1 bg-white hover:bg-gray-50"
                onClick={() => {
                  setLightboxSrc(img.url);
                  setLightboxOpen(true);
                }}
                title={label}
              >
                <img src={img.url} alt={label} className={compact ? 'w-[88px] h-[60px] object-cover rounded' : 'w-[140px] h-[96px] object-cover rounded'} />
                <div className="text-[10px] text-gray-500 mt-1 max-w-[140px] truncate">{label}</div>
              </button>
            );
          })}
        </div>
      ) : null}

      {items.length ? (
        <div className={compact ? 'space-y-1' : 'space-y-1.5'}>
          {items.map((it, i) => {
            const anchorSnippet = it.anchor ? pickEvidenceByAnchor(it.anchor, preview ?? null) : null;
            const snippet = anchorSnippet;
            const modelText = it.text ? it.text.trim() : '';
            const suspiciousText = isSuspiciousEvidenceText(modelText);
            const expandedContext = expandEvidenceContext({
              beforeText: it.anchor?.beforeContext ?? null,
              afterText: it.anchor?.afterContext ?? null,
              sourceText: typeof preview?.text === 'string' ? preview.text : null,
            });
            const anchorText = [expandedContext.beforeText, expandedContext.afterText].filter(Boolean).join('\n');
            const head = it.imageId ? `图片: ${it.imageId}` : '';
            const body = (suspiciousText ? (snippet || modelText || anchorText) : (modelText || snippet || anchorText)) || it.imagePlaceholders.join(' ');
            return (
              <div key={`${i}-${it.raw}`} className={compact ? 'text-xs font-mono break-words whitespace-pre-wrap' : 'rounded border bg-white p-2'}>
                {!compact && head ? <div className="text-xs text-gray-500">{head}</div> : null}
                {!compact && suspiciousText && snippet ? <div className="text-[11px] text-amber-700 mb-1">文本疑似污染，已优先显示复核片段</div> : null}
                <div className={compact ? '' : 'text-xs font-mono break-words whitespace-pre-wrap'}>{body}</div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className={compact ? 'text-xs text-gray-500' : 'text-xs text-gray-400'}>—</div>
      )}

      <ImageLightbox open={lightboxOpen} src={lightboxSrc} onClose={() => setLightboxOpen(false)} />
    </div>
  );
}
