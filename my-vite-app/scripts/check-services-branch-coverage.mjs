import fs from 'node:fs';
import path from 'node:path';

const STAGE = String(process.env.SERVICES_BRANCH_STAGE ?? '1');
const DEFAULT_MIN_BRANCHES = STAGE === '2' ? 78 : 70;
const MIN_BRANCHES = Number(process.env.SERVICES_BRANCH_MIN ?? DEFAULT_MIN_BRANCHES);

const DEFAULT_COVERAGE_SUMMARY_PATH = path.resolve(process.cwd(), 'test-reports/vitest-coverage/coverage-summary.json');

const resolveCoverageSummaryPath = (arg) => {
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

const coverageSummaryPath = resolveCoverageSummaryPath(process.argv[2]);

const toPosixPath = (p) => p.replaceAll('\\', '/');

const normalizeKey = (p) => {
  const normalized = toPosixPath(path.normalize(p));
  return normalized.startsWith('./') ? normalized.slice(2) : normalized;
};

const isServicesSourceFile = (p) => {
  const n = normalizeKey(p);
  if (!n.startsWith('src/services/')) return false;
  if (!/\.ts$/.test(n)) return false;
  if (/\.test\./.test(n)) return false;
  if (n.endsWith('.d.ts')) return false;
  return true;
};

const readCoverageSummary = (filePath) => {
  const raw = fs.readFileSync(filePath, 'utf8');
  return JSON.parse(raw);
};

const main = () => {
  if (!Number.isFinite(MIN_BRANCHES) || MIN_BRANCHES < 0 || MIN_BRANCHES > 100) {
    console.error(`SERVICES_BRANCH_MIN 非法：${String(process.env.SERVICES_BRANCH_MIN)}`);
    process.exit(1);
  }

  if (!fs.existsSync(coverageSummaryPath)) {
    console.error(`覆盖率摘要文件不存在：${toPosixPath(coverageSummaryPath)}`);
    process.exit(1);
  }

  const summary = readCoverageSummary(coverageSummaryPath);
  let covered = 0;
  let total = 0;

  for (const [key, value] of Object.entries(summary)) {
    if (key === 'total' || !value || typeof value !== 'object') continue;
    if (!isServicesSourceFile(key)) continue;
    const t = Number(value?.branches?.total ?? 0);
    const c = Number(value?.branches?.covered ?? 0);
    if (!Number.isFinite(t) || !Number.isFinite(c)) continue;
    total += t;
    covered += c;
  }

  const pct = total > 0 ? (covered / total) * 100 : 100;
  const pctStr = Number.isFinite(pct) ? pct.toFixed(2) : '0.00';

  if (pct < MIN_BRANCHES) {
    console.error(`src/services Branches 未达标：${pctStr}%（${covered}/${total}），要求 ≥ ${MIN_BRANCHES}%`);
    process.exit(1);
  }

  console.log(`src/services Branches 达标：${pctStr}%（${covered}/${total}），要求 ≥ ${MIN_BRANCHES}%`);
};

main();
