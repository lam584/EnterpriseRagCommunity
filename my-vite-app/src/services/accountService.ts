// src/services/accountService.ts
import { getCsrfToken } from '../utils/csrfUtils';
import type { UpdateUserProfileRequest, UserProfile } from '../types/userProfile';

export interface AdminAccountInfo {
  id: number;
  account: string;
  email: string;
  phone: string;
  sex: string;
}

export interface UpdateAccountRequest {
  phone?: string;
  email?: string;
  sex?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  totpCode?: string;
  emailCode?: string;
}

const BASE_URL = '/api/account';

export async function getAccountInfo(): Promise<AdminAccountInfo> {
  const res = await fetch(`${BASE_URL}/me`, {
    method: 'GET',
    credentials: 'include',
  });
  if (!res.ok) {
    throw new Error('获取账户信息失败');
  }
  return res.json();
}

export async function updateAccountInfo(body: UpdateAccountRequest): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${BASE_URL}/me`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || '更新账户信息失败');
  }
}

export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${BASE_URL}/password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || '修改密码失败');
  }
}

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  if (data && typeof data === 'object' && 'error' in data && typeof (data as { error?: unknown }).error === 'string') {
    return (data as { error: string }).error;
  }
  return undefined;
}

function toUserProfileFromCurrentAdmin(data: unknown): UserProfile {
  const d = data as Record<string, unknown> | null;
  const metadata = isPlainObject(d?.metadata) ? (d!.metadata as Record<string, unknown>) : undefined;
  const profilePublic = metadata && isPlainObject(metadata.profile) ? (metadata.profile as Record<string, unknown>) : undefined;
  const profilePending = metadata && isPlainObject((metadata as Record<string, unknown>).profilePending)
    ? ((metadata as Record<string, unknown>).profilePending as Record<string, unknown>)
    : undefined;
  const effectiveProfile = profilePending || profilePublic;

  const publicUsername = String(d?.username ?? '');
  const pendingUsername = profilePending && 'username' in profilePending ? (profilePending.username == null ? undefined : String(profilePending.username)) : undefined;
  const effectiveUsername = pendingUsername ?? publicUsername;

  const profileModeration =
    metadata && isPlainObject((metadata as Record<string, unknown>).profileModeration)
      ? ((metadata as Record<string, unknown>).profileModeration as Record<string, unknown>)
      : undefined;

  return {
    id: Number(d?.id),
    email: String(d?.email ?? ''),
    username: effectiveUsername,
    // don't use truthy checks here; empty string is a valid persisted value
    avatarUrl:
      effectiveProfile && 'avatarUrl' in effectiveProfile
        ? (effectiveProfile.avatarUrl == null ? undefined : String(effectiveProfile.avatarUrl))
        : undefined,
    bio: effectiveProfile && 'bio' in effectiveProfile ? (effectiveProfile.bio == null ? undefined : String(effectiveProfile.bio)) : undefined,
    location:
      effectiveProfile && 'location' in effectiveProfile ? (effectiveProfile.location == null ? undefined : String(effectiveProfile.location)) : undefined,
    website:
      effectiveProfile && 'website' in effectiveProfile ? (effectiveProfile.website == null ? undefined : String(effectiveProfile.website)) : undefined,
    publicProfile: {
      username: publicUsername,
      avatarUrl:
        profilePublic && 'avatarUrl' in profilePublic ? (profilePublic.avatarUrl == null ? undefined : String(profilePublic.avatarUrl)) : undefined,
      bio: profilePublic && 'bio' in profilePublic ? (profilePublic.bio == null ? undefined : String(profilePublic.bio)) : undefined,
      location: profilePublic && 'location' in profilePublic ? (profilePublic.location == null ? undefined : String(profilePublic.location)) : undefined,
      website: profilePublic && 'website' in profilePublic ? (profilePublic.website == null ? undefined : String(profilePublic.website)) : undefined,
    },
    profileModeration: profileModeration
      ? {
          caseType: 'caseType' in profileModeration ? (profileModeration.caseType == null ? undefined : String(profileModeration.caseType)) : undefined,
          queueId: 'queueId' in profileModeration ? (profileModeration.queueId == null ? undefined : Number(profileModeration.queueId)) : undefined,
          status: 'status' in profileModeration ? (profileModeration.status == null ? undefined : String(profileModeration.status)) : undefined,
          stage: 'stage' in profileModeration ? (profileModeration.stage == null ? undefined : String(profileModeration.stage)) : undefined,
          updatedAt:
            'updatedAt' in profileModeration ? (profileModeration.updatedAt == null ? undefined : String(profileModeration.updatedAt)) : undefined,
          reason: 'reason' in profileModeration ? (profileModeration.reason == null ? undefined : String(profileModeration.reason)) : undefined,
        }
      : undefined,
  };
}

export async function getMyProfile(): Promise<UserProfile> {
  const res = await fetch(`${BASE_URL}/profile`, {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '加载个人资料失败');
  }
  return toUserProfileFromCurrentAdmin(data);
}

export async function updateMyProfile(body: UpdateUserProfileRequest): Promise<UserProfile> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${BASE_URL}/profile`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && (data.message || data.error)) || '更新个人资料失败');
  }

  // backend returns UsersDTO-like safe dto
  return toUserProfileFromCurrentAdmin(data);
}
