import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    exclude: ['src/**/postService.test.ts'],
    reporters: ['default', 'junit'],
    outputFile: {
      junit: 'test-reports/vitest-junit.xml',
    },
    coverage: {
      provider: 'v8',
      reportsDirectory: 'test-reports/vitest-coverage',
      reporter: ['text', 'json-summary', 'html'],
      enabled: process.env.CI === 'true' || process.env.VITEST_COVERAGE === 'true',
    },
  },
});
