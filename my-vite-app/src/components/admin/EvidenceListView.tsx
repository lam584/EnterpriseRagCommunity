import { useMemo, useState } from 'react';
import DetailDialog from '../common/DetailDialog';
import ImageLightbox from '../ui/ImageLightbox';
import { expandEvidenceContext } from '../../utils/evidence-context-display';
import EvidenceContextCell from './EvidenceContextCell';

type EvidenceRow = {
  sources: Array<{ kind: '阶段' | '分片'; stage?: string; title: string; order: number }>;
  chunkIndex?: number;
  chunkIndexes?: number[];
  imageId?: string;
  type?: string;
  quote?: string;
  text?: string;
  description?: string;
  beforeContext?: string;
  afterContext?: string;
  rawText: string;
};

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
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null;
  return v as Record<string, unknown>;
}

function readNonEmptyString(v: unknown): string | undefined {
  if (typeof v !== 'string') return undefined;
  const t = v.trim();
  return t ? t : undefined;
}

function isEvidenceFieldFragment(v: string | undefined): boolean {
  const t = (v ?? '').trim();
  if (!t) return false;
  const compact = t.replace(/\s+/g, '');
  if (
    (compact.includes('before_context') ||
      compact.includes('after_context') ||
      compact.includes('beforeContext') ||
      compact.includes('afterContext')) &&
    !/[\u4e00-\u9fff\d]/.test(compact)
  ) {
    return true;
  }
  return /^"?[,}\]]?"?[A-Za-z_][A-Za-z0-9_]*"?:"?$/.test(compact);
}

function readEvidenceText(v: unknown): string | undefined {
  const text = readNonEmptyString(v);
  if (!text) return undefined;
  return isEvidenceFieldFragment(text) ? undefined : text;
}

function safeJson(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}

function tryParseFirstJson(text: string): unknown | null {
  const s = (text ?? '').trim();
  if (!s) return null;
  const starts: number[] = [];
  for (let i = 0; i < s.length; i += 1) {
    const c = s[i];
    if (c === '{' || c === '[') {
      starts.push(i);
      break;
    }
  }
  if (!starts.length) return null;

  const start = starts[0];
  let inStr = false;
  let esc = false;
  const stack: string[] = [];
  for (let i = start; i < s.length; i += 1) {
    const c = s[i];
    if (inStr) {
      if (esc) {
        esc = false;
        continue;
      }
      if (c === '\\') {
        esc = true;
        continue;
      }
      if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') {
      inStr = true;
      continue;
    }
    if (c === '{' || c === '[') {
      stack.push(c);
      continue;
    }
    if (c === '}' || c === ']') {
      const top = stack[stack.length - 1];
      const ok = (c === '}' && top === '{') || (c === ']' && top === '[');
      if (!ok) return null;
      stack.pop();
      if (stack.length === 0) {
        const candidate = s.slice(start, i + 1);
        try {
          return JSON.parse(candidate) as unknown;
        } catch {
          return null;
        }
      }
    }
  }
  return null;
}

function imageIdLookupKeys(imageId: string): string[] {
  const id0 = (imageId ?? '').trim();
  if (!id0) return [];
  const out = new Set<string>();
  const add = (s: string) => {
    const t = (s ?? '').trim();
    if (t) out.add(t);
  };
  add(id0);
  add(id0.toLowerCase());

  const mImg = /^img[\s_-]*(\d+)$/i.exec(id0);
  const mPh = /^\[\[image_(\d+)\]\]$/i.exec(id0);
  const n = mImg?.[1] ?? mPh?.[1] ?? null;
  if (n) {
    add(`img_${n}`);
    add(`img ${n}`);
    add(`img-${n}`);
    add(`img${n}`);
  }

  add(id0.replace(/\s+/g, ''));
  add(id0.replace(/\s+/g, '_'));
  add(id0.replace(/[\s-]+/g, '_'));
  add(id0.replace(/[\s_]+/g, '-'));
  add(id0.replace(/[_-]+/g, ' '));
  add(id0.replace(/[_-]+/g, ' ').toLowerCase());
  add(id0.replace(/[\s-]+/g, '_').toLowerCase());
  add(id0.replace(/[\s_]+/g, '-').toLowerCase());

  return Array.from(out.values());
}

function normalizeEvidenceValue(v: unknown): { obj: Record<string, unknown> | null; rawText: string; chunkIndex?: number } {
  if (v == null) return { obj: null, rawText: '' };
  if (typeof v === 'string') {
    const t0 = v.trim();
    if (!t0) return { obj: null, rawText: '' };
    let chunkIndex: number | undefined;
    const m = /^chunk-(\d+)\s*:\s*/i.exec(t0);
    const cleaned = m ? t0.slice(m[0].length).trim() : t0;
    if (m) chunkIndex = toInt(m[1]) ?? undefined;
    try {
      const parsed = JSON.parse(cleaned) as unknown;
      const o = toRecord(parsed);
      if (o) return { obj: o, rawText: cleaned, chunkIndex };
    } catch {
    }
    const parsed2 = tryParseFirstJson(cleaned);
    const o2 = toRecord(parsed2);
    if (o2) return { obj: o2, rawText: safeJson(o2), chunkIndex };
    return { obj: null, rawText: cleaned || t0, chunkIndex };
  }
  const o = toRecord(v);
  if (o) return { obj: o, rawText: safeJson(o) };
  return { obj: null, rawText: String(v) };
}

function parseEvidenceFields(
  v: unknown,
): { chunkIndex?: number; imageId?: string; type?: string; quote?: string; text?: string; description?: string; beforeContext?: string; afterContext?: string; rawText: string } | null {
  const { obj, rawText, chunkIndex } = normalizeEvidenceValue(v);
  if (!rawText.trim() && !obj) return null;
  if (!obj) return { rawText, chunkIndex };
  const imageId = readNonEmptyString(obj.image_id ?? obj.imageId ?? obj.image);
  const type = readNonEmptyString(obj.type ?? obj.source_type ?? obj.sourceType);
  const quote = readNonEmptyString(obj.quote);
  const text = readEvidenceText(obj.text);
  const description = readNonEmptyString(obj.description ?? obj.reason);
  const beforeContext = readNonEmptyString(obj.before_context ?? obj.beforeContext);
  const afterContext = readNonEmptyString(obj.after_context ?? obj.afterContext);
  const chunkIndex2 =
    chunkIndex ??
    toInt(obj.chunkIndex ?? obj.chunk_index ?? obj.chunkNo ?? obj.chunk_no ?? obj.index ?? obj.seq ?? obj.no) ??
    undefined;
  return { chunkIndex: chunkIndex2, imageId, type, quote, text, description, beforeContext, afterContext, rawText };
}

function fingerprintParsed(p: {
  chunkIndex?: number;
  imageId?: string;
  type?: string;
  quote?: string;
  text?: string;
  beforeContext?: string;
  afterContext?: string;
  rawText: string;
}): string {
  const main = (p.quote ?? p.text ?? '').trim();
  const before = (p.beforeContext ?? '').trim();
  const after = (p.afterContext ?? '').trim();
  const imageId = (p.imageId ?? '').trim();
  const type = (p.type ?? '').trim();
  if (main || before || after || imageId || type) {
    return JSON.stringify({ imageId, type, main, before, after });
  }
  return JSON.stringify({ raw: (p.rawText ?? '').trim() });
}

function rowEvidenceText(r: EvidenceRow): string {
  const main = (r.quote ?? r.text ?? '').trim();
  if (main) return main;
  const hasStructuredEvidence = Boolean(
    cleanText(r.beforeContext) ||
      cleanText(r.afterContext) ||
      cleanText(r.description) ||
      cleanText(r.imageId) ||
      cleanText(r.type),
  );
  if (hasStructuredEvidence) return '—';
  const raw = r.rawText.trim();
  if (!raw || isEvidenceFieldFragment(raw)) return '—';
  if ((raw.startsWith('{') && raw.endsWith('}')) || (raw.startsWith('[') && raw.endsWith(']'))) return '—';
  return raw;
}

function cleanText(v: string | undefined): string {
  return (v ?? '').trim();
}

function sourceTextFromRaw(rawText: string): string | null {
  const t = rawText.trim();
  if (!t) return null;
  try {
    const parsed = JSON.parse(t) as unknown;
    const o = toRecord(parsed);
    if (!o) return null;
    return (
      readNonEmptyString(o.source_text ?? o.sourceText) ??
      readNonEmptyString(o.chunk_text ?? o.chunkText) ??
      readNonEmptyString(o.full_text ?? o.fullText) ??
      readNonEmptyString(o.original_text ?? o.originalText) ??
      readNonEmptyString(o.content) ??
      null
    );
  } catch {
    return null;
  }
}

function isSuspiciousEvidenceText(text: string): boolean {
  const t = (text ?? '').trim();
  if (!t) return false;
  if (t.length > 320) return true;
  const lower = t.toLowerCase();
  return EVIDENCE_POLLUTION_MARKERS.some((m) => lower.includes(m));
}

function anchorPreviewText(beforeText: string, afterText: string): string {
  const b = beforeText || '';
  const a = afterText || '';
  return [b, a].filter(Boolean).join('\n');
}

async function copyText(text: string): Promise<boolean> {
  const t = text ?? '';
  try {
    await navigator.clipboard.writeText(t);
    return true;
  } catch {
    try {
      const el = document.createElement('textarea');
      el.value = t;
      el.style.position = 'fixed';
      el.style.left = '-10000px';
      el.style.top = '0';
      document.body.appendChild(el);
      el.focus();
      el.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(el);
      return ok;
    } catch {
      return false;
    }
  }
}

export type EvidenceListViewProps = {
  stepEvidenceGroups: Array<{ title: string; order: number; stage: string; evidence: unknown[] }>;
  chunkEvidenceByChunkIndex?: Record<string, unknown[]>;
  chunkIdByChunkIndex?: Record<string, number>;
  chunkIndexFilter?: number | null;
  imageUrlByImageId?: Record<string, string>;
};

export default function EvidenceListView({ stepEvidenceGroups, chunkEvidenceByChunkIndex, chunkIdByChunkIndex, chunkIndexFilter, imageUrlByImageId }: EvidenceListViewProps) {
  const [jsonOpen, setJsonOpen] = useState(false);
  const [jsonTitle, setJsonTitle] = useState('');
  const [jsonText, setJsonText] = useState('');
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  const rows = useMemo(() => {
    const merged = new Map<string, EvidenceRow>();
    for (const g of stepEvidenceGroups) {
      for (const ev of g.evidence) {
        const parsed = parseEvidenceFields(ev);
        if (!parsed) continue;
        const fp = fingerprintParsed(parsed);
        const prev = merged.get(fp);
        const src = { kind: '阶段' as const, stage: g.stage, title: g.title, order: g.order };
        if (prev) {
          if (!prev.sources.some((s) => s.kind === src.kind && s.title === src.title)) prev.sources.push(src);
          if (parsed.chunkIndex != null) {
            const list = prev.chunkIndexes ?? (prev.chunkIndex != null ? [prev.chunkIndex] : []);
            if (!list.includes(parsed.chunkIndex)) list.push(parsed.chunkIndex);
            list.sort((a, b) => a - b);
            prev.chunkIndexes = list;
            if (prev.chunkIndex == null) prev.chunkIndex = list[0];
          }
          if (!prev.description && parsed.description) prev.description = parsed.description;
          if (!prev.beforeContext && parsed.beforeContext) prev.beforeContext = parsed.beforeContext;
          if (!prev.afterContext && parsed.afterContext) prev.afterContext = parsed.afterContext;
          if (!prev.text && parsed.text) prev.text = parsed.text;
          if (!prev.quote && parsed.quote) prev.quote = parsed.quote;
          if (!prev.imageId && parsed.imageId) prev.imageId = parsed.imageId;
          if (!prev.type && parsed.type) prev.type = parsed.type;
          continue;
        }
        merged.set(fp, {
          sources: [src],
          chunkIndex: parsed.chunkIndex,
          chunkIndexes: parsed.chunkIndex != null ? [parsed.chunkIndex] : undefined,
          imageId: parsed.imageId,
          type: parsed.type,
          quote: parsed.quote,
          text: parsed.text,
          description: parsed.description,
          beforeContext: parsed.beforeContext,
          afterContext: parsed.afterContext,
          rawText: parsed.rawText,
        });
      }
    }

    const dict = chunkEvidenceByChunkIndex ?? {};
    for (const [k, list] of Object.entries(dict)) {
      const ci = toInt(k) ?? undefined;
      if (chunkIndexFilter != null && ci != null && ci !== chunkIndexFilter) continue;
      const evList = Array.isArray(list) ? list : [];
      for (const ev of evList) {
        const parsed = parseEvidenceFields(ev);
        if (!parsed) continue;
        const parsed2 = { ...parsed, chunkIndex: ci ?? parsed.chunkIndex };
        const fp = fingerprintParsed(parsed2);
        const prev = merged.get(fp);
        const src = { kind: '分片' as const, stage: undefined, title: '分片审核', order: 10_000 };
        if (prev) {
          if (!prev.sources.some((s) => s.kind === src.kind && s.title === src.title)) prev.sources.push(src);
          if (parsed2.chunkIndex != null) {
            const list = prev.chunkIndexes ?? (prev.chunkIndex != null ? [prev.chunkIndex] : []);
            if (!list.includes(parsed2.chunkIndex)) list.push(parsed2.chunkIndex);
            list.sort((a, b) => a - b);
            prev.chunkIndexes = list;
            if (prev.chunkIndex == null) prev.chunkIndex = list[0];
          }
          if (prev.chunkIndex == null && parsed2.chunkIndex != null) prev.chunkIndex = parsed2.chunkIndex;
          if (!prev.description && parsed2.description) prev.description = parsed2.description;
          if (!prev.beforeContext && parsed2.beforeContext) prev.beforeContext = parsed2.beforeContext;
          if (!prev.afterContext && parsed2.afterContext) prev.afterContext = parsed2.afterContext;
          if (!prev.text && parsed2.text) prev.text = parsed2.text;
          if (!prev.quote && parsed2.quote) prev.quote = parsed2.quote;
          if (!prev.imageId && parsed2.imageId) prev.imageId = parsed2.imageId;
          if (!prev.type && parsed2.type) prev.type = parsed2.type;
          continue;
        }
        merged.set(fp, {
          sources: [src],
          chunkIndex: parsed2.chunkIndex,
          chunkIndexes: parsed2.chunkIndex != null ? [parsed2.chunkIndex] : undefined,
          imageId: parsed2.imageId,
          type: parsed2.type,
          quote: parsed2.quote,
          text: parsed2.text,
          description: parsed2.description,
          beforeContext: parsed2.beforeContext,
          afterContext: parsed2.afterContext,
          rawText: parsed2.rawText,
        });
      }
    }

    const out = Array.from(merged.values());
    for (const r of out) r.sources.sort((a, b) => a.order - b.order);
    out.sort((a, b) => {
      const ao = a.sources.length ? a.sources[0].order : 0;
      const bo = b.sources.length ? b.sources[0].order : 0;
      if (ao !== bo) return ao - bo;
      const ac = (a.chunkIndexes && a.chunkIndexes.length ? a.chunkIndexes[0] : a.chunkIndex) ?? 1_000_000;
      const bc = (b.chunkIndexes && b.chunkIndexes.length ? b.chunkIndexes[0] : b.chunkIndex) ?? 1_000_000;
      if (ac !== bc) return ac - bc;
      return rowEvidenceText(a).localeCompare(rowEvidenceText(b));
    });
    return out;
  }, [chunkEvidenceByChunkIndex, chunkIndexFilter, stepEvidenceGroups]);

  const hasChunkCol = useMemo(() => rows.some((r) => r.sources.some((s) => s.kind === '分片') || r.chunkIndex != null || (r.chunkIndexes?.length ?? 0) > 0), [rows]);

  const stageLabel = (r: EvidenceRow): string => {
    if (r.sources.some((s) => s.kind === '分片')) return '分片';
    const set = new Set<string>();
    for (const s of r.sources) {
      if (s.stage && s.stage.trim()) set.add(s.stage.trim());
    }
    const list = Array.from(set.values());
    return list.length ? list.join('/') : '—';
  };

  const stepTitles = (r: EvidenceRow): string => {
    const list = r.sources
      .filter((s) => s.kind === '阶段')
      .map((s) => s.title)
      .filter(Boolean);
    return list.length ? Array.from(new Set(list)).join('\n') : '—';
  };

  const resolveImageUrl = (imageId: string | undefined): string | null => {
    const dict = imageUrlByImageId ?? null;
    if (!dict) return null;
    const id = (imageId ?? '').trim();
    if (!id) return null;
    for (const k of imageIdLookupKeys(id)) {
      const url = dict[k];
      const t = typeof url === 'string' ? url.trim() : '';
      if (t) return t;
    }
    return null;
  };

  return (
    <div className="overflow-auto">
      <table className="min-w-[1100px] w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-gray-500">
            <th className="py-2 pr-3 w-[90px]">来源</th>
            <th className="py-2 pr-3 w-[360px]">阶段/步骤</th>
            {hasChunkCol ? <th className="py-2 pr-3 w-[90px]">分片</th> : null}
            <th className="py-2 pr-3 w-[120px]">图片</th>
            <th className="py-2 pr-3 w-[140px]">类型</th>
            <th className="py-2 pr-3 w-[360px]">证据</th>
            <th className="py-2 pr-3 w-[420px]">上下文</th>
            <th className="py-2 pr-3 w-[110px]">操作</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, idx) => {
            const evidenceText0 = rowEvidenceText(r);
            const mainText = cleanText(r.text ?? r.quote);
            const rawBeforeText = cleanText(r.beforeContext);
            const rawAfterText = cleanText(r.afterContext);
            const expandedContext = expandEvidenceContext({
              beforeText: rawBeforeText,
              afterText: rawAfterText,
              sourceText: sourceTextFromRaw(r.rawText),
            });
            const beforeText = expandedContext.beforeText;
            const afterText = expandedContext.afterText;
            const descText = cleanText(r.description);
            const suspiciousMain = mainText ? isSuspiciousEvidenceText(mainText) : false;
            const anchorText = anchorPreviewText(beforeText, afterText);
            const evidenceText = suspiciousMain && anchorText ? anchorText : evidenceText0;
            const chunkList = r.chunkIndexes && r.chunkIndexes.length ? r.chunkIndexes : (r.chunkIndex != null ? [r.chunkIndex] : []);
            const chunkLabel = chunkList.length ? chunkList.join(',') : '—';
            const stepTitle = stepTitles(r);
            const imageId = r.imageId ?? '—';
            const type = r.type ?? '—';
            const chunkId = chunkList.length === 1 ? (chunkIdByChunkIndex ?? {})[String(chunkList[0])] : undefined;
            const imageUrl = resolveImageUrl(r.imageId);
            const rowKey = `${stageLabel(r)}-${r.chunkIndex ?? 'x'}-${idx}`;
            return (
              <tr key={rowKey} className="border-t align-top">
                <td className="py-2 pr-3 whitespace-nowrap">{stageLabel(r)}</td>
                <td className="py-2 pr-3">
                  <pre className="text-xs whitespace-pre-wrap break-words">{stepTitle}</pre>
                </td>
                {hasChunkCol ? <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{chunkLabel}</td> : null}
                <td className="py-2 pr-3 whitespace-nowrap">
                  {imageUrl ? (
                    <button
                      type="button"
                      className="border rounded bg-white hover:bg-gray-50 p-1"
                      onClick={() => {
                        setLightboxSrc(imageUrl);
                        setLightboxOpen(true);
                      }}
                      title={r.imageId ?? 'image'}
                    >
                      <img src={imageUrl} alt={r.imageId ?? 'image'} className="w-[64px] h-[44px] object-cover rounded" loading="lazy" />
                    </button>
                  ) : (
                    <span className="font-mono text-xs">{imageId}</span>
                  )}
                </td>
                <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{type}</td>
                <td className="py-2 pr-3">
                  {chunkId != null ? (
                    <div className="text-[11px] text-gray-500 mb-1">
                      chunkId：<span className="font-mono">{String(chunkId)}</span>
                    </div>
                  ) : null}
                  {suspiciousMain && anchorText ? <div className="text-[11px] text-amber-700 mb-1">文本疑似污染，已回退锚点展示</div> : null}
                  <pre className="text-xs font-mono whitespace-pre-wrap break-words bg-gray-50 rounded p-2 max-h-[180px] overflow-auto">{evidenceText}</pre>
                </td>
                <td className="py-2 pr-3">
                  <EvidenceContextCell
                    description={descText}
                    beforeText={rawBeforeText}
                    mainText={mainText}
                    afterText={rawAfterText}
                    sourceText={sourceTextFromRaw(r.rawText)}
                    chunkId={chunkId ?? null}
                  />
                </td>
                <td className="py-2 pr-3 whitespace-nowrap">
                  <div className="flex flex-col gap-1">
                    <button
                      type="button"
                      className="rounded border px-2 py-1 text-xs hover:bg-gray-50"
                      onClick={() => void copyText(evidenceText)}
                      title="复制证据文本"
                    >
                      复制
                    </button>
                    <button
                      type="button"
                      className="rounded border px-2 py-1 text-xs hover:bg-gray-50"
                      onClick={() => {
                        setJsonTitle(`${stageLabel(r)}${r.chunkIndex == null ? '' : ` · chunkIndex=${r.chunkIndex}`}${r.imageId ? ` · image_id=${r.imageId}` : ''}`);
                        setJsonText(r.rawText || evidenceText);
                        setJsonOpen(true);
                      }}
                      title="查看证据 JSON"
                    >
                      查看JSON
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
          {rows.length === 0 ? (
            <tr>
              <td className="py-6 text-center text-gray-500" colSpan={hasChunkCol ? 8 : 7}>
                暂无 evidence
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>

      <DetailDialog
        open={jsonOpen}
        onClose={() => setJsonOpen(false)}
        title="Evidence JSON"
        subtitle={jsonTitle}
        hintText={null}
        variant="center"
        containerClassName="max-w-3xl"
        bodyClassName="flex-1 overflow-auto p-4"
      >
        <pre className="text-xs whitespace-pre-wrap break-words bg-gray-50 rounded p-3 overflow-auto max-h-[70vh]">{jsonText || '—'}</pre>
      </DetailDialog>

      <ImageLightbox open={lightboxOpen} src={lightboxSrc} onClose={() => setLightboxOpen(false)} />
    </div>
  );
}
