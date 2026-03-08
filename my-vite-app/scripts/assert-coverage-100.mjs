import fs from 'node:fs';
import path from 'node:path';

const coverageSummaryPath = process.argv[2]
  ? path.resolve(process.cwd(), process.argv[2])
  : path.resolve(process.cwd(), 'test-reports/vitest-coverage/coverage-summary.json');

const toPosixPath = (p) => p.replaceAll('\\', '/');

const normalizeKey = (p) => {
  const normalized = toPosixPath(path.normalize(p));
  return normalized.startsWith('./') ? normalized.slice(2) : normalized;
};

const readCoverageSummary = (filePath) => {
  const raw = fs.readFileSync(filePath, 'utf8');
  return JSON.parse(raw);
};

const buildCoverageIndex = (summary) => {
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

const isRelevantSourceFile = (n) => {
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

const listFilesRecursive = (dirAbs) => {
  const out = [];
  const entries = fs.readdirSync(dirAbs, { withFileTypes: true });
  for (const e of entries) {
    const abs = path.join(dirAbs, e.name);
    if (e.isDirectory()) {
      out.push(...listFilesRecursive(abs));
      continue;
    }
    if (e.isFile()) out.push(abs);
  }
  return out;
};

const getRelevantFiles = () => {
  const srcAbs = path.resolve(process.cwd(), 'src');
  const allAbs = listFilesRecursive(srcAbs);
  const rel = allAbs.map((p) => normalizeKey(path.relative(process.cwd(), p)));
  return rel.filter(isRelevantSourceFile).sort();
};

const pctNumber = (entry, key) => Number(entry?.[key]?.pct ?? 0);

const main = () => {
  if (!fs.existsSync(coverageSummaryPath)) {
    console.error(`覆盖率摘要文件不存在：${toPosixPath(coverageSummaryPath)}`);
    process.exit(1);
  }

  const relevantFiles = getRelevantFiles();
  if (relevantFiles.length === 0) {
    console.log('未检测到需要校验的源文件（src 下 ts/tsx，排除 *.test.*、d.ts、assets、pages、types、main）。');
    return;
  }

  const summary = readCoverageSummary(coverageSummaryPath);
  const index = buildCoverageIndex(summary);

  const failures = [];
  for (const file of relevantFiles) {
    const entry = index.get(file);
    const linesPct = pctNumber(entry, 'lines');
    const branchesPct = pctNumber(entry, 'branches');
    const functionsPct = pctNumber(entry, 'functions');
    const statementsPct = pctNumber(entry, 'statements');

    const ok =
      entry &&
      linesPct === 100 &&
      branchesPct === 100 &&
      functionsPct === 100 &&
      statementsPct === 100;

    if (!ok) failures.push({ file, linesPct, branchesPct, functionsPct, statementsPct });
  }

  if (failures.length > 0) {
    console.error(`全量逐文件覆盖率未达标：要求 Lines/Branches/Functions/Statements 均为 100%`);
    for (const f of failures) {
      console.error(
        `- ${f.file}  Lines=${f.linesPct}%  Branches=${f.branchesPct}%  Functions=${f.functionsPct}%  Statements=${f.statementsPct}%`,
      );
    }
    process.exit(1);
  }

  console.log('全量逐文件覆盖率达标：Lines/Branches/Functions/Statements 均为 100%');
};

main();
