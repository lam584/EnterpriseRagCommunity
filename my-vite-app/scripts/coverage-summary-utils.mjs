import fs from 'node:fs';
import path from 'node:path';

export const DEFAULT_COVERAGE_SUMMARY_PATH = path.resolve(process.cwd(), 'test-reports/vitest-coverage/coverage-summary.json');

export const toPosixPath = (value) => value.replaceAll('\\', '/');

export const normalizeKey = (value) => {
  const normalized = toPosixPath(path.normalize(value));
  return normalized.startsWith('./') ? normalized.slice(2) : normalized;
};

export const resolveCoverageSummaryPath = (arg) => {
  if (!arg) return DEFAULT_COVERAGE_SUMMARY_PATH;
  const candidate = path.resolve(process.cwd(), arg);
  if (fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) {
    return path.join(candidate, 'coverage-summary.json');
  }
  if (candidate.toLowerCase().endsWith('.json')) {
    return candidate;
  }
  return DEFAULT_COVERAGE_SUMMARY_PATH;
};

export const readCoverageSummary = (filePath) => {
  const raw = fs.readFileSync(filePath, 'utf8');
  return JSON.parse(raw);
};

export const buildCoverageIndex = (summary) => {
  const index = new Map();
  for (const [key, value] of Object.entries(summary)) {
    if (key === 'total' || !value || typeof value !== 'object') continue;
    const normalized = normalizeKey(key);
    index.set(normalized, value);
    index.set(normalizeKey(`./${normalized}`), value);
    if (path.isAbsolute(key)) {
      const rel = normalizeKey(path.relative(process.cwd(), key));
      index.set(rel, value);
      index.set(normalizeKey(`./${rel}`), value);
    }
  }
  return index;
};
