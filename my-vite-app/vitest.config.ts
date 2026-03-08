import { defineConfig } from 'vitest/config';
import { resolve } from 'node:path';
import react from '@vitejs/plugin-react-swc';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      echarts: resolve(__dirname, 'src/testUtils/echartsMockModule.ts'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: [resolve(__dirname, 'src/testUtils/vitestSetup.ts')],
    reporters: ['default', 'junit'],
    outputFile: {
      junit: 'test-reports/vitest-junit.xml',
    },
    coverage: {
      provider: 'v8',
      reportsDirectory: 'test-reports/vitest-coverage',
      reporter: ['text', 'json-summary', 'html'],
      clean: false,
      all: true,
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/**/*.d.ts',
        'src/assets/**',
        'src/pages/**',
        'src/types/**',
        'src/vite-env.d.ts',
        'src/main.tsx',
      ],
      thresholds: {
        lines: 0,
        branches: 0,
      },
    },
  },
});
