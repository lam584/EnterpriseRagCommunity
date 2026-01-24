import { getCsrfToken } from '../utils/csrfUtils';

export type RegistrationSettingsDTO = {
  defaultRegisterRoleId: number;
};

const API_BASE_URL = '/api/admin/settings';

export async function getRegistrationSettings(): Promise<RegistrationSettingsDTO> {
  const res = await fetch(`${API_BASE_URL}/registration`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to load registration settings');
  }

  return res.json();
}

export async function updateRegistrationSettings(dto: RegistrationSettingsDTO): Promise<RegistrationSettingsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/registration`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(dto)
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to update registration settings');
  }

  return res.json();
}

