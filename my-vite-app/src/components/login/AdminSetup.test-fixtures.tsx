/* eslint-disable react-refresh/only-export-components */
import { useLocation } from 'react-router-dom';
import { vi } from 'vitest';

const setupServiceMocks = vi.hoisted(() => {
  return {
    saveSetupConfig: vi.fn(),
    initIndices: vi.fn(),
    completeSetup: vi.fn(),
    checkEnvFile: vi.fn(async () => ({ exists: false })),
  };
});

export function getAdminSetupServiceMocks() {
  return setupServiceMocks;
}

vi.mock('react-hot-toast', () => {
  return {
    default: {
      success: vi.fn(),
      error: vi.fn(),
    },
  };
});

vi.mock('../../services/setupService', () => {
  return {
    checkEnvFile: setupServiceMocks.checkEnvFile,
    generateTotpKey: vi.fn(),
    saveSetupConfig: setupServiceMocks.saveSetupConfig,
    testEsConnection: vi.fn(),
    initIndices: setupServiceMocks.initIndices,
    completeSetup: setupServiceMocks.completeSetup,
    checkIndicesStatus: vi.fn(),
    encryptValue: vi.fn(),
  };
});

export function LoginPage() {
  const location = useLocation();
  const state = location.state as { setupJustCompleted?: boolean } | null;
  return <div>LOGIN:{state?.setupJustCompleted ? 'BYPASS' : ''}</div>;
}
