/**
 * 共享 evidence 解析工具函数，供 queue.tsx 和 review-trace.tsx 使用。
 */

function toRecord(v: unknown): Record<string, unknown> | null {
  if (!v || typeof v !== 'object') return null;
  return v as Record<string, unknown>;
}

function normalizeEvidenceArray(v: unknown): unknown[] {
  if (Array.isArray(v)) return v;
  if (v == null) return [];
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return [];
    try {
      const parsed = JSON.parse(t) as unknown;
      if (Array.isArray(parsed)) return parsed;
      if (parsed && typeof parsed === 'object') return [parsed];
    } catch {
    }
    return [t];
  }
  if (typeof v === 'object') return [v];
  return [String(v)];
}

function tryParseFirstJson(text: string): unknown | null {
  const s = (text ?? '').trim();
  if (!s) return null;
  let start = -1;
  for (let i = 0; i < s.length; i += 1) {
    const c = s[i];
    if (c === '{' || c === '[') {
      start = i;
      break;
    }
  }
  if (start < 0) return null;
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

/**
 * 从 LLM step details 中提取 evidence 数组。
 * 支持三种路径：
 *   1. details.evidence
 *   2. details.full_response.evidence
 *   3. details.rawModelOutput / details.raw_output (JSON 字符串解析)
 */
export function extractEvidenceFromDetails(details: Record<string, unknown> | null): unknown[] {
  if (!details) return [];

  const direct = normalizeEvidenceArray(details.evidence);
  if (direct.length) return direct;

  const fullResponse = toRecord(details.full_response ?? details.fullResponse ?? null);
  const nested = normalizeEvidenceArray(fullResponse?.evidence);
  if (nested.length) return nested;

  for (const key of ['rawModelOutput', 'raw_output']) {
    const raw = details[key];
    if (typeof raw !== 'string') continue;
    const t = raw.trim();
    if (!t) continue;
    try {
      const parsed = JSON.parse(t) as unknown;
      const parsedObj = toRecord(parsed);
      if (!parsedObj) continue;
      const fromParsed = normalizeEvidenceArray(parsedObj.evidence);
      if (fromParsed.length) return fromParsed;
      const nestedParsed = toRecord(parsedObj.full_response ?? parsedObj.fullResponse ?? null);
      const fromNestedParsed = normalizeEvidenceArray(nestedParsed?.evidence);
      if (fromNestedParsed.length) return fromNestedParsed;
    } catch {
    }
  }
  return [];
}

export function shouldSkipStepEvidenceForChunkedReview(args: {
  stage: string | null | undefined;
  details: Record<string, unknown> | null;
  hasChunkSet: boolean;
  chunkIndex?: number | null;
}): boolean {
  const stage = String(args.stage ?? '').trim();
  if (!args.hasChunkSet || stage !== 'LLM') return false;
  if (args.chunkIndex != null) return true;

  const details = args.details;
  if (!details) return false;
  const meta = toRecord(details.meta ?? null);
  const metaKind = typeof meta?.kind === 'string' ? meta.kind.trim() : '';
  const req = toRecord(details.request ?? null);
  const reqText = typeof req?.text === 'string' ? req.text : '';
  const prompt = typeof details.prompt === 'string' ? details.prompt : '';
  const looksLikeChunkReview = metaKind === 'MODERATION_CHUNK' || reqText.includes('[CHUNK_REVIEW]') || prompt.includes('[CHUNK_REVIEW]');
  if (looksLikeChunkReview) return true;

  const scope = typeof details.scope === 'string' ? details.scope.trim() : '';
  const chunked = details.chunked === true;
  const hasChunkedFinalMarkers =
    details.chunkSetId != null ||
    details.chunkedFinal != null ||
    details.chunkProgressFinal != null;
  return scope === 'finalReview' && chunked && hasChunkedFinalMarkers;
}

/**
 * 从 latestRun 的 steps 中收集所有非空 evidence 条目。
 */
export function collectEvidenceFromSteps(latestRun: Record<string, unknown> | null): unknown[] {
  if (!latestRun) return [];
  const steps = latestRun.steps;
  if (!Array.isArray(steps)) return [];
  const all: unknown[] = [];
  for (const s of steps) {
    const details = toRecord(s);
    if (!details) continue;
    const stepDetails = toRecord(details.details ?? null);
    const evItems = extractEvidenceFromDetails(stepDetails);
    for (const ev of evItems) all.push(ev);
  }
  return all;
}

function readNonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t ? t : null;
}

function extractEvidenceFingerprintFields(o: Record<string, unknown>) {
  return {
    imageId: readNonEmptyString(o.image_id ?? o.imageId ?? o.image),
    type: readNonEmptyString(o.type ?? o.source_type ?? o.sourceType),
    quote: readNonEmptyString(o.quote),
    text: readNonEmptyString(o.text),
    before: readNonEmptyString(o.before_context ?? o.beforeContext),
    after: readNonEmptyString(o.after_context ?? o.afterContext),
  };
}

export function fingerprintEvidenceItem(v: unknown): string | null {
  if (v == null) return null;
  if (typeof v === 'string') {
    const t0 = v.trim();
    if (!t0) return null;
    const m = /^chunk-(\d+)\s*:\s*/i.exec(t0);
    const cleaned = m ? t0.slice(m[0].length).trim() : t0;
    try {
      const parsed = JSON.parse(cleaned) as unknown;
      const o = toRecord(parsed);
      if (o) {
        return JSON.stringify(extractEvidenceFingerprintFields(o));
      }
    } catch {
    }
    const parsed2 = tryParseFirstJson(cleaned);
    const o2 = toRecord(parsed2);
    if (o2) {
      return JSON.stringify(extractEvidenceFingerprintFields(o2));
    }
    return JSON.stringify({ raw: cleaned });
  }
  if (typeof v === 'object') {
    const o = toRecord(v);
    if (!o) return JSON.stringify({ raw: String(v) });
    return JSON.stringify(extractEvidenceFingerprintFields(o));
  }
  return JSON.stringify({ raw: String(v) });
}

export function countUniqueEvidence(items: unknown[]): number {
  const set = new Set<string>();
  for (const it of items) {
    const fp = fingerprintEvidenceItem(it);
    if (!fp) continue;
    set.add(fp);
  }
  return set.size;
}
