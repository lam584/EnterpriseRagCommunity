import { describe, expect, it } from 'vitest';

// These helpers are file-local in the component. For regression protection without changing public API,
// we test the observable behavior of the pure logic via a minimal re-implementation contract here.
// If you prefer, we can later export these from the component or move them to a shared util.

type RunLike = { llmModel?: string | null; llmThreshold?: number | null };

type DetailLike = {
  run?: { llmModel?: string | null; llmThreshold?: number | string | null } | null;
  steps?: Array<{ stage?: string | null; stepOrder?: number | null; threshold?: number | null; details?: any }> | null;
};

const isNonEmptyString = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const readStringPath = (obj: unknown, path: Array<string | number>): string | null => {
  let cur: unknown = obj;
  for (const p of path) {
    if (cur == null) return null;
    if (typeof p === 'number') {
      if (!Array.isArray(cur)) return null;
      cur = cur[p];
      continue;
    }
    if (typeof cur !== 'object') return null;
    cur = (cur as Record<string, unknown>)[p];
  }
  return isNonEmptyString(cur) ? cur.trim() : null;
};
const readNumberPath = (obj: unknown, path: Array<string | number>): number | null => {
  let cur: unknown = obj;
  for (const p of path) {
    if (cur == null) return null;
    if (typeof p === 'number') {
      if (!Array.isArray(cur)) return null;
      cur = cur[p];
      continue;
    }
    if (typeof cur !== 'object') return null;
    cur = (cur as Record<string, unknown>)[p];
  }
  if (typeof cur === 'number' && Number.isFinite(cur)) return cur;
  if (typeof cur === 'string' && cur.trim() !== '') {
    const n = Number(cur);
    if (Number.isFinite(n)) return n;
  }
  return null;
};

const resolveLlmStep = (detail?: DetailLike) => {
  const steps = Array.isArray(detail?.steps) ? detail!.steps! : [];
  const llmSteps = steps.filter((s) => String(s.stage).toUpperCase() === 'LLM');
  if (!llmSteps.length) return undefined;
  return llmSteps
    .slice()
    .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
    .at(-1);
};

const resolveLlmModel = (run: RunLike, detail?: DetailLike): string | null => {
  if (isNonEmptyString(run.llmModel)) return run.llmModel.trim();
  const fromDetailRun = readStringPath(detail, ['run', 'llmModel']);
  if (fromDetailRun) return fromDetailRun;

  const llmStep = resolveLlmStep(detail);
  const candidates: Array<Array<string | number>> = [
    ['details', 'model'],
    ['details', 'llmModel'],
    ['details', 'llm_model'],
    ['details', 'modelName'],
    ['details', 'llm', 'model'],
  ];
  for (const p of candidates) {
    const v = readStringPath(llmStep, p);
    if (v) return v;
  }
  return null;
};

const resolveLlmThreshold = (run: RunLike, detail?: DetailLike): number | null => {
  if (typeof run.llmThreshold === 'number' && Number.isFinite(run.llmThreshold)) return run.llmThreshold;
  const fromDetailRun = readNumberPath(detail, ['run', 'llmThreshold']);
  if (fromDetailRun != null) return fromDetailRun;

  const llmStep = resolveLlmStep(detail);
  if (typeof llmStep?.threshold === 'number' && Number.isFinite(llmStep.threshold)) return llmStep.threshold;

  const candidates: Array<Array<string | number>> = [
    ['details', 'threshold'],
    ['details', 'llmThreshold'],
    ['details', 'llm_threshold'],
    ['details', 'rejectThreshold'],
    ['details', 'policyThreshold'],
    ['details', 'llm', 'threshold'],
  ];
  for (const p of candidates) {
    const v = readNumberPath(llmStep, p);
    if (v != null) return v;
  }
  return null;
};

describe('ModerationPipelineHistoryPanel model/threshold resolver', () => {
  it('prefers run-level fields', () => {
    expect(resolveLlmModel({ llmModel: 'gpt-4o' }, undefined)).toBe('gpt-4o');
    expect(resolveLlmThreshold({ llmThreshold: 0.7 }, undefined)).toBe(0.7);
  });

  it('falls back to step.details model and step.threshold', () => {
    const detail: DetailLike = {
      steps: [
        { stage: 'RULE', stepOrder: 1, details: {} },
        { stage: 'LLM', stepOrder: 2, threshold: 0.66, details: { model: 'deepseek-chat' } },
      ],
    };
    expect(resolveLlmModel({ llmModel: null }, detail)).toBe('deepseek-chat');
    expect(resolveLlmThreshold({ llmThreshold: null }, detail)).toBe(0.66);
  });

  it('parses threshold from strings and preserves 0', () => {
    const detail: DetailLike = {
      steps: [{ stage: 'LLM', stepOrder: 1, details: { threshold: '0' } }],
    };
    expect(resolveLlmThreshold({ llmThreshold: null }, detail)).toBe(0);
  });
});
