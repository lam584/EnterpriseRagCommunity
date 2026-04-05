import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
export type PublicUserProfileDTO = {
  id: number;
  username: string;
  avatarUrl?: string | null;
  bio?: string | null;
  location?: string | null;
  website?: string | null;
};

const apiUrl = serviceApiUrl;


export async function getPublicUserProfile(userId: number): Promise<PublicUserProfileDTO> {
  const res = await fetch(apiUrl(`/api/portal/users/${userId}/profile`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '加载用户资料失败');
  }
  return data as PublicUserProfileDTO;
}

