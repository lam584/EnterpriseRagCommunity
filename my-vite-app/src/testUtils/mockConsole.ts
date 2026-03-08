import { vi } from 'vitest';

export type ConsoleMethod = 'log' | 'info' | 'warn' | 'error' | 'debug';

export function mockConsole(methods: ConsoleMethod[] = ['error', 'warn']) {
  const uniq = Array.from(new Set(methods));
  const spies = uniq.map((m) => vi.spyOn(console, m).mockImplementation(() => {}));
  const restore = () => {
    for (const s of spies) s.mockRestore();
  };
  return {
    restore,
    log: uniq.includes('log') ? spies[uniq.indexOf('log')] : undefined,
    info: uniq.includes('info') ? spies[uniq.indexOf('info')] : undefined,
    warn: uniq.includes('warn') ? spies[uniq.indexOf('warn')] : undefined,
    error: uniq.includes('error') ? spies[uniq.indexOf('error')] : undefined,
    debug: uniq.includes('debug') ? spies[uniq.indexOf('debug')] : undefined,
  };
}
