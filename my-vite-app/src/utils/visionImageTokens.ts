export type VisionModelProfile = {
  side: 32 | 28;
  tokenPixels: number;
  tokenLimit: number;
  defaultMaxPixels: number;
};

const TOKEN_LIMIT = 16_384;

const QWEN_VL_32_DEFAULT_MAX_PIXELS = 1_310_720;
const QWEN3_VL_DEFAULT_MAX_PIXELS = 2_621_440;
const QWEN_VL_28_DEFAULT_MAX_PIXELS = 1_003_520;

const QWEN_VL_32_EXACT = new Set([
  'qwen-vl-max',
  'qwen-vl-max-latest',
  'qwen-vl-max-2025-08-13',
  'qwen-vl-plus',
  'qwen-vl-plus-latest',
  'qwen-vl-plus-2025-08-15',
  'qwen-vl-plus-2025-07-10'
]);

export function inferVisionModelProfile(model?: string | null): VisionModelProfile {
  const m = (model ?? '').trim().toLowerCase();
  if (m.includes('qwen3-vl')) {
    return { side: 32, tokenPixels: 32 * 32, tokenLimit: TOKEN_LIMIT, defaultMaxPixels: QWEN3_VL_DEFAULT_MAX_PIXELS };
  }
  if (QWEN_VL_32_EXACT.has(m)) {
    return { side: 32, tokenPixels: 32 * 32, tokenLimit: TOKEN_LIMIT, defaultMaxPixels: QWEN_VL_32_DEFAULT_MAX_PIXELS };
  }
  if (m.includes('qvq') || m.includes('qwen2.5-vl') || m.startsWith('qwen2-') || m.includes('qwen2-vl')) {
    return { side: 28, tokenPixels: 28 * 28, tokenLimit: TOKEN_LIMIT, defaultMaxPixels: QWEN_VL_28_DEFAULT_MAX_PIXELS };
  }
  if (m.startsWith('qwen-vl-max') || m.startsWith('qwen-vl-plus')) {
    return { side: 28, tokenPixels: 28 * 28, tokenLimit: TOKEN_LIMIT, defaultMaxPixels: QWEN_VL_28_DEFAULT_MAX_PIXELS };
  }
  return { side: 32, tokenPixels: 32 * 32, tokenLimit: TOKEN_LIMIT, defaultMaxPixels: QWEN3_VL_DEFAULT_MAX_PIXELS };
}

function toPositiveNumber(v: number | null | undefined): number | null {
  if (v == null) return null;
  if (!Number.isFinite(v)) return null;
  if (v <= 0) return null;
  return v;
}

export type VisionTokenCalcInput = {
  model?: string | null;
  width?: number | null;
  height?: number | null;
  vlHighResolutionImages?: boolean | null;
  maxPixelsOverride?: number | null;
};

export function estimateVisionImageTokens(input: VisionTokenCalcInput): number | null {
  const width = toPositiveNumber(input.width);
  const height = toPositiveNumber(input.height);
  if (width == null || height == null) return null;

  const profile = inferVisionModelProfile(input.model);
  const side = profile.side;
  const tokenPixels = profile.tokenPixels;
  const minPixels = 4 * tokenPixels;

  const maxPixels =
    Boolean(input.vlHighResolutionImages) ? profile.tokenLimit * tokenPixels : Math.max(1, Math.floor(toPositiveNumber(input.maxPixelsOverride) ?? profile.defaultMaxPixels));

  let hBar = Math.round(height / side) * side;
  let wBar = Math.round(width / side) * side;

  const barPixels = hBar * wBar;
  if (barPixels > maxPixels) {
    const beta = Math.sqrt((height * width) / maxPixels);
    hBar = Math.floor(height / beta / side) * side;
    wBar = Math.floor(width / beta / side) * side;
  } else if (barPixels < minPixels) {
    const beta = Math.sqrt(minPixels / (height * width));
    hBar = Math.ceil((height * beta) / side) * side;
    wBar = Math.ceil((width * beta) / side) * side;
  }

  const scaledPixels = hBar * wBar;
  return Math.trunc(scaledPixels / tokenPixels) + 2;
}
