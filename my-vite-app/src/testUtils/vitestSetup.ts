import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const dirs = ['test-reports', 'test-reports/vitest-coverage', 'test-reports/vitest-coverage/.tmp'];

for (const d of dirs) {
  mkdirSync(resolve(process.cwd(), d), { recursive: true });
}

