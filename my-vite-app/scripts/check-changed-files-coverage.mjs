import fs from 'node:fs';
import { execSync } from 'node:child_process';
import {
  buildCoverageIndex,
  normalizeKey,
  readCoverageSummary,
  resolveCoverageSummaryPath,
  toPosixPath,
} from './coverage-summary-utils.mjs';

const MIN_LINES = 100;
const MIN_BRANCHES = 100;
const MIN_FUNCTIONS = 100;
const MIN_STATEMENTS = 100;

const coverageSummaryPath = resolveCoverageSummaryPath(process.argv[2]);

const readChangedFilesFromEnv = () => {
  const raw = process.env.COVERAGE_CHANGED_FILES;
  if (!raw) return null;
  const files = raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => normalizeKey(s));
  return files.length ? files : [];
};

const execGit = (args) =>
  execSync(`git ${args}`, {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
  }).trim();

const tryGetChangedFilesFromGit = () => {
  try {
    if (execGit('rev-parse --is-inside-work-tree') !== 'true') return [];
  } catch {
    return [];
  }

  const baseRefs = [];
  if (process.env.GITHUB_BASE_REF) baseRefs.push(`origin/${process.env.GITHUB_BASE_REF}`);
  if (process.env.CI_MERGE_REQUEST_TARGET_BRANCH_NAME)
    baseRefs.push(`origin/${process.env.CI_MERGE_REQUEST_TARGET_BRANCH_NAME}`);
  baseRefs.push('origin/main', 'origin/master');

  for (const base of baseRefs) {
    try {
      execGit(`rev-parse --verify ${base}`);
      const out = execGit(`diff --name-only --diff-filter=ACMR ${base}...HEAD`);
      const files = out
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
        .map((s) => normalizeKey(s));
      return files;
    } catch {
      continue;
    }
  }

  try {
    const out = execGit('diff --name-only --diff-filter=ACMR HEAD~1...HEAD');
    const files = out
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => normalizeKey(s));
    return files;
  } catch {
    return [];
  }
};

const isRelevantSourceFile = (p) => {
  const n = normalizeKey(p);
  if (!n.startsWith('src/')) return false;
  if (!/\.(ts|tsx)$/.test(n)) return false;
  if (/\.test\./.test(n)) return false;
  if (n.endsWith('.d.ts')) return false;
  if (n.startsWith('src/assets/')) return false;
  if (n.startsWith('src/pages/')) return false;
  if (n.startsWith('src/types/')) return false;
  if (n === 'src/main.tsx') return false;
  if (n === 'src/vite-env.d.ts') return false;
  return true;
};


const main = () => {
  if (!fs.existsSync(coverageSummaryPath)) {
    console.error(`覆盖率摘要文件不存在：${toPosixPath(coverageSummaryPath)}`);
    process.exit(1);
  }

  const changedFromEnv = readChangedFilesFromEnv();
  const changedFiles = changedFromEnv ?? tryGetChangedFilesFromGit();
  const relevantFiles = changedFiles.filter(isRelevantSourceFile).map(normalizeKey);

  if (relevantFiles.length === 0) {
    console.log(
      '未检测到需要校验的变更文件（src 下非 *.test.* 的 ts/tsx，排除 d.ts、assets、pages、types、main），跳过增量覆盖门槛校验。',
    );
    return;
  }

  const summary = readCoverageSummary(coverageSummaryPath);
  const index = buildCoverageIndex(summary);

  const failures = [];
  for (const file of relevantFiles) {
    const entry = index.get(file);
    const linesPct = Number(entry?.lines?.pct ?? 0);
    const branchesPct = Number(entry?.branches?.pct ?? 0);
    const functionsPct = Number(entry?.functions?.pct ?? 0);
    const statementsPct = Number(entry?.statements?.pct ?? 0);

    if (
      !entry ||
      Number.isNaN(linesPct) ||
      Number.isNaN(branchesPct) ||
      Number.isNaN(functionsPct) ||
      Number.isNaN(statementsPct)
    ) {
      failures.push({ file, linesPct: 0, branchesPct: 0, functionsPct: 0, statementsPct: 0 });
      continue;
    }

    if (
      linesPct < MIN_LINES ||
      branchesPct < MIN_BRANCHES ||
      functionsPct < MIN_FUNCTIONS ||
      statementsPct < MIN_STATEMENTS
    ) {
      failures.push({ file, linesPct, branchesPct, functionsPct, statementsPct });
    }
  }

  if (failures.length > 0) {
    console.error(
      `变更文件增量覆盖未达标：要求 Lines>=${MIN_LINES}、Branches>=${MIN_BRANCHES}、Functions>=${MIN_FUNCTIONS}、Statements>=${MIN_STATEMENTS}`,
    );
    for (const f of failures) {
      console.error(
        `- ${f.file}  Lines=${Number.isFinite(f.linesPct) ? f.linesPct : 0}%  Branches=${
          Number.isFinite(f.branchesPct) ? f.branchesPct : 0
        }%  Functions=${Number.isFinite(f.functionsPct) ? f.functionsPct : 0}%  Statements=${
          Number.isFinite(f.statementsPct) ? f.statementsPct : 0
        }%`,
      );
    }
    process.exit(1);
  }

  console.log(
    `变更文件增量覆盖达标：Lines>=${MIN_LINES}、Branches>=${MIN_BRANCHES}、Functions>=${MIN_FUNCTIONS}、Statements>=${MIN_STATEMENTS}`,
  );
};

main();
