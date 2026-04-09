import { getCsrfToken } from '../utils/csrfUtils';

export interface SetupStatusResponse {
  isInitialized: boolean;
}

export interface SetupConfigPayload {
  configs: Record<string, string>;
}

export interface TestEsResponse {
  success: boolean;
  message: string;
}

export interface GenerateTotpResponse {
  key: string;
}

export interface CheckEnvResponse {
  exists: boolean;
  message?: string;
}

export async function getSetupStatus(): Promise<SetupStatusResponse> {
  const res = await fetch('/api/setup/status');
  if (!res.ok) throw new Error('Failed to get status');
  return res.json();
}

export async function checkEnvFile(): Promise<CheckEnvResponse> {
  const res = await fetch('/api/setup/check-env');
  if (!res.ok) throw new Error('Failed to check env file');
  return res.json();
}

export async function saveSetupConfig(configs: Record<string, string>): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/save-config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify({ configs }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to save config');
  }
}

export async function testEsConnection(configs: Record<string, string>): Promise<TestEsResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/test-es', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify(configs),
  });
  // Note: API returns 400 for failed connection but with JSON body
  return res.json();
}

export async function initIndices(indexNames: string[]): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/init-indices', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify({ indexNames }),
  });
  if (!res.ok) throw new Error('Failed to init indices');
}

export async function generateTotpKey(): Promise<string> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/generate-totp', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });
  if (!res.ok) throw new Error('Failed to generate TOTP key');
  const data = await res.json();
  return data.key;
}

export async function encryptValue(value: string): Promise<string> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/encrypt', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify({ value }),
  });
  if (!res.ok) throw new Error('Failed to encrypt value');
  const data = await res.json();
  return data.encrypted;
}

 
export async function completeSetup(data: any): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/complete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to complete setup');
  }
}

export async function checkIndicesStatus(configs: Record<string, string>, indices: string[]): Promise<Record<string, string>> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/setup/check-indices', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
    body: JSON.stringify({ configs, indices }),
  });
  if (!res.ok) {
     throw new Error('Failed to check indices status');
  }
  return res.json();
}
