import fs from 'node:fs';
import path from 'node:path';

const toPosixPath = (p) => p.replaceAll('\\', '/');

const normalizeRel = (p) => {
  const n = toPosixPath(path.normalize(p));
  return n.startsWith('./') ? n.slice(2) : n;
};

const readJson = (filePath) => JSON.parse(fs.readFileSync(filePath, 'utf8'));

const sumMetric = (items, key) => {
  let covered = 0;
  let total = 0;
  for (const it of items) {
    const v = it[key];
    if (!v) continue;
    covered += Number(v.covered ?? 0);
    total += Number(v.total ?? 0);
  }
  return { covered, total };
};

const pct = (covered, total) => {
  if (!Number.isFinite(covered) || !Number.isFinite(total) || total <= 0) return 100;
  return (covered / total) * 100;
};

const fmtPct = (p) => {
  const n = Number(p);
  if (!Number.isFinite(n)) return '0%';
  if (n <= 0) return '0%';
  if (n >= 100) return '100%';
  return `${n.toFixed(2)}%`;
};

const levelClass = (p) => {
  const n = Number(p);
  if (!Number.isFinite(n)) return 'low';
  if (n < 50) return 'low';
  if (n < 80) return 'medium';
  return 'high';
};

const chartWidth = (p) => {
  const n = Number(p);
  if (!Number.isFinite(n)) return 0;
  if (n <= 0) return 0;
  if (n >= 100) return 100;
  return Math.floor(n);
};

const renderRow = ({ label, href, statements, branches, functions, lines }) => {
  const sp = pct(statements.covered, statements.total);
  const bp = pct(branches.covered, branches.total);
  const fp = pct(functions.covered, functions.total);
  const lp = pct(lines.covered, lines.total);
  const lineClass = levelClass(sp);
  return [
    '<tr>',
    `\t<td class="file ${lineClass}" data-value="${label}"><a href="${href}">${label}</a></td>`,
    `\t<td data-value="${sp}" class="pic ${lineClass}">`,
    `\t<div class="chart"><div class="cover-fill" style="width: ${chartWidth(sp)}%"></div><div class="cover-empty" style="width: ${100 - chartWidth(sp)}%"></div></div>`,
    '\t</td>',
    `\t<td data-value="${sp}" class="pct ${lineClass}">${fmtPct(sp)}</td>`,
    `\t<td data-value="${statements.total}" class="abs ${lineClass}">${statements.covered}/${statements.total}</td>`,
    `\t<td data-value="${bp}" class="pct ${levelClass(bp)}">${fmtPct(bp)}</td>`,
    `\t<td data-value="${branches.total}" class="abs ${levelClass(bp)}">${branches.covered}/${branches.total}</td>`,
    `\t<td data-value="${fp}" class="pct ${levelClass(fp)}">${fmtPct(fp)}</td>`,
    `\t<td data-value="${functions.total}" class="abs ${levelClass(fp)}">${functions.covered}/${functions.total}</td>`,
    `\t<td data-value="${lp}" class="pct ${levelClass(lp)}">${fmtPct(lp)}</td>`,
    `\t<td data-value="${lines.total}" class="abs ${levelClass(lp)}">${lines.covered}/${lines.total}</td>`,
    '\t</tr>',
    '',
  ].join('\n');
};

const renderDirIndexHtml = ({ reportDir, dirPath, titlePath, entries }) => {
  const depth = normalizeRel(dirPath).split('/').length;
  const assetPrefix = '../'.repeat(depth);

  const totals = {
    statements: sumMetric(entries.map((e) => e.metrics).flat(), 'statements'),
    branches: sumMetric(entries.map((e) => e.metrics).flat(), 'branches'),
    functions: sumMetric(entries.map((e) => e.metrics).flat(), 'functions'),
    lines: sumMetric(entries.map((e) => e.metrics).flat(), 'lines'),
  };

  const rows = entries
    .map((e) =>
      renderRow({
        label: e.label,
        href: e.href,
        statements: e.aggregate.statements,
        branches: e.aggregate.branches,
        functions: e.aggregate.functions,
        lines: e.aggregate.lines,
      }),
    )
    .join('\n');

  const html = [
    '',
    '<!doctype html>',
    '<html lang="en">',
    '',
    '<head>',
    `    <title>Code coverage report for ${titlePath}</title>`,
    '    <meta charset="utf-8" />',
    `    <link rel="stylesheet" href="${assetPrefix}prettify.css" />`,
    `    <link rel="stylesheet" href="${assetPrefix}base.css" />`,
    `    <link rel="shortcut icon" type="image/x-icon" href="${assetPrefix}favicon.png" />`,
    '    <meta name="viewport" content="width=device-width, initial-scale=1" />',
    "    <style type='text/css'>",
    '        .coverage-summary .sorter {',
    `            background-image: url(${assetPrefix}sort-arrow-sprite.png);`,
    '        }',
    '    </style>',
    '</head>',
    '    ',
    '<body>',
    "<div class='wrapper'>",
    "    <div class='pad1'>",
    `        <h1><a href="${assetPrefix}index.html">All files</a> ${titlePath}</h1>`,
    "        <div class='clearfix'>",
    '            ',
    "            <div class='fl pad1y space-right2'>",
    `                <span class="strong">${fmtPct(pct(totals.statements.covered, totals.statements.total))} </span>`,
    '                <span class="quiet">Statements</span>',
    `                <span class='fraction'>${totals.statements.covered}/${totals.statements.total}</span>`,
    '            </div>',
    '        ',
    '            ',
    "            <div class='fl pad1y space-right2'>",
    `                <span class="strong">${fmtPct(pct(totals.branches.covered, totals.branches.total))} </span>`,
    '                <span class="quiet">Branches</span>',
    `                <span class='fraction'>${totals.branches.covered}/${totals.branches.total}</span>`,
    '            </div>',
    '        ',
    '            ',
    "            <div class='fl pad1y space-right2'>",
    `                <span class="strong">${fmtPct(pct(totals.functions.covered, totals.functions.total))} </span>`,
    '                <span class="quiet">Functions</span>',
    `                <span class='fraction'>${totals.functions.covered}/${totals.functions.total}</span>`,
    '            </div>',
    '        ',
    '            ',
    "            <div class='fl pad1y space-right2'>",
    `                <span class="strong">${fmtPct(pct(totals.lines.covered, totals.lines.total))} </span>`,
    '                <span class="quiet">Lines</span>',
    `                <span class='fraction'>${totals.lines.covered}/${totals.lines.total}</span>`,
    '            </div>',
    '        ',
    '            ',
    '        </div>',
    '        <p class="quiet">',
    '            Press <em>n</em> or <em>j</em> to go to the next uncovered block, <em>b</em>, <em>p</em> or <em>k</em> for the previous block.',
    '        </p>',
    '        <template id="filterTemplate">',
    '            <div class="quiet">',
    '                Filter:',
    '                <input type="search" id="fileSearch">',
    '            </div>',
    '        </template>',
    '    </div>',
    `    <div class='status-line ${levelClass(pct(totals.statements.covered, totals.statements.total))}'></div>`,
    '    <div class="pad1">',
    '<table class="coverage-summary">',
    '<thead>',
    '<tr>',
    '   <th data-col="file" data-fmt="html" data-html="true" class="file">File</th>',
    '   <th data-col="pic" data-type="number" data-fmt="html" data-html="true" class="pic"></th>',
    '   <th data-col="statements" data-type="number" data-fmt="pct" class="pct">Statements</th>',
    '   <th data-col="statements_raw" data-type="number" data-fmt="html" class="abs"></th>',
    '   <th data-col="branches" data-type="number" data-fmt="pct" class="pct">Branches</th>',
    '   <th data-col="branches_raw" data-type="number" data-fmt="html" class="abs"></th>',
    '   <th data-col="functions" data-type="number" data-fmt="pct" class="pct">Functions</th>',
    '   <th data-col="functions_raw" data-type="number" data-fmt="html" class="abs"></th>',
    '   <th data-col="lines" data-type="number" data-fmt="pct" class="pct">Lines</th>',
    '   <th data-col="lines_raw" data-type="number" data-fmt="html" class="abs"></th>',
    '</tr>',
    '</thead>',
    `<tbody>${rows}</tbody>`,
    '</table>',
    '</div>',
    "                <div class='push'></div><!-- for sticky footer -->",
    '            </div><!-- /wrapper -->',
    "            <div class='footer quiet pad2 space-top1 center small'>",
    '                Code coverage generated by',
    '                <a href="https://istanbul.js.org/" target="_blank" rel="noopener noreferrer">istanbul</a>',
    `                at ${new Date().toISOString()}`,
    '            </div>',
    `        <script src="${assetPrefix}prettify.js"></script>`,
    '        <script>',
    '            window.onload = function () {',
    '                prettyPrint();',
    '            };',
    '        </script>',
    `        <script src="${assetPrefix}sorter.js"></script>`,
    `        <script src="${assetPrefix}block-navigation.js"></script>`,
    '    </body>',
    '</html>',
    '    ',
  ].join('\n');

  const outPath = path.join(reportDir, ...normalizeRel(dirPath).split('/'), 'index.html');
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, html, 'utf8');
};

const main = () => {
  const reportDir = path.resolve(process.cwd(), process.argv[2] ?? 'test-reports/vitest-coverage');
  const summaryPath = path.join(reportDir, 'coverage-summary.json');
  if (!fs.existsSync(summaryPath)) {
    console.error(`覆盖率摘要文件不存在：${toPosixPath(summaryPath)}`);
    process.exit(1);
  }

  const summary = readJson(summaryPath);

  const files = [];
  for (const [key, value] of Object.entries(summary)) {
    if (key === 'total' || !value || typeof value !== 'object') continue;
    const rel = normalizeRel(path.isAbsolute(key) ? path.relative(process.cwd(), key) : key);
    if (!rel.startsWith('src/')) continue;
    const metrics = {
      statements: value.statements,
      branches: value.branches,
      functions: value.functions,
      lines: value.lines,
    };
    files.push({ rel, metrics });
  }

  const byDir = new Map();
  for (const f of files) {
    const dir = normalizeRel(path.posix.dirname(f.rel));
    if (!byDir.has(dir)) byDir.set(dir, []);
    byDir.get(dir).push(f);
  }

  const buildEntries = (dir) => {
    const prefix = dir === 'src' ? 'src/' : `${dir}/`;
    const childDirs = new Set();
    const childFiles = [];

    for (const f of files) {
      if (!f.rel.startsWith(prefix)) continue;
      const rest = f.rel.slice(prefix.length);
      if (!rest || rest.includes('..')) continue;
      const parts = rest.split('/');
      if (parts.length === 1) {
        childFiles.push(f);
        continue;
      }
      childDirs.add(parts[0]);
    }

    const dirEntries = [...childDirs]
      .sort((a, b) => a.localeCompare(b))
      .map((name) => {
        const childPath = `${dir}/${name}`;
        const childPrefix = `${childPath}/`;
        const childFilesUnder = files.filter((f) => f.rel.startsWith(childPrefix));
        const aggregate = {
          statements: sumMetric(childFilesUnder.map((x) => x.metrics), 'statements'),
          branches: sumMetric(childFilesUnder.map((x) => x.metrics), 'branches'),
          functions: sumMetric(childFilesUnder.map((x) => x.metrics), 'functions'),
          lines: sumMetric(childFilesUnder.map((x) => x.metrics), 'lines'),
        };

        const href = `${name}/index.html`;
        return {
          label: name,
          href,
          metrics: childFilesUnder.map((x) => x.metrics),
          aggregate,
        };
      });

    const fileEntries = childFiles
      .sort((a, b) => a.rel.localeCompare(b.rel))
      .map((f) => {
        const label = path.posix.basename(f.rel);
        const href = `${label}.html`;
        const aggregate = {
          statements: sumMetric([f.metrics], 'statements'),
          branches: sumMetric([f.metrics], 'branches'),
          functions: sumMetric([f.metrics], 'functions'),
          lines: sumMetric([f.metrics], 'lines'),
        };
        return { label, href, metrics: [f.metrics], aggregate };
      });

    return [...dirEntries, ...fileEntries];
  };

  const srcEntries = buildEntries('src');
  if (srcEntries.length) {
    renderDirIndexHtml({ reportDir, dirPath: 'src', titlePath: 'src', entries: srcEntries });
  }

  const componentsFiles = files.filter((f) => f.rel.startsWith('src/components/'));
  if (componentsFiles.length) {
    const entries = buildEntries('src/components');
    if (entries.length) {
      renderDirIndexHtml({
        reportDir,
        dirPath: 'src/components',
        titlePath: 'src/components',
        entries,
      });
    }
  }
};

main();
