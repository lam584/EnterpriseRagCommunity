import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type DependencyCircuitBreakerConfigDTO = {
  dependency?: string | null;
  failureThreshold?: number | null;
  cooldownSeconds?: number | null;
};

export async function adminGetDependencyCircuitBreakerConfig(dependency: string): Promise<DependencyCircuitBreakerConfigDTO> {
  const dep = String(dependency || '').trim();
  const res = await fetch(apiUrl(`/api/admin/safety/dependency-circuit-breakers/${encodeURIComponent(dep)}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载依赖熔断配置失败');
  return data as DependencyCircuitBreakerConfigDTO;
}

export async function adminUpdateDependencyCircuitBreakerConfig(dependency: string, payload: DependencyCircuitBreakerConfigDTO, reason: string): Promise<DependencyCircuitBreakerConfigDTO> {
  const dep = String(dependency || '').trim();
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/safety/dependency-circuit-breakers/${encodeURIComponent(dep)}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ config: payload ?? {}, reason: reason ?? '' }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存依赖熔断配置失败');
  return data as DependencyCircuitBreakerConfigDTO;
}
