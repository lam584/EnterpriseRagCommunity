// src/services/accountService.ts
import { getCsrfToken } from '../utils/csrfUtils';
import { getCurrentAdmin } from './authService';
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

function toUserProfileFromCurrentAdmin(data: unknown): UserProfile {
  const d = data as Record<string, unknown> | null;
  const metadata = isPlainObject(d?.metadata) ? (d!.metadata as Record<string, unknown>) : undefined;
  const profile = metadata && isPlainObject(metadata.profile) ? (metadata.profile as Record<string, unknown>) : undefined;

  return {
    id: Number(d?.id),
    email: String(d?.email ?? ''),
    username: String(d?.username ?? ''),
    // don't use truthy checks here; empty string is a valid persisted value
    avatarUrl: profile && 'avatarUrl' in profile ? (profile.avatarUrl == null ? undefined : String(profile.avatarUrl)) : undefined,
    bio: profile && 'bio' in profile ? (profile.bio == null ? undefined : String(profile.bio)) : undefined,
    location: profile && 'location' in profile ? (profile.location == null ? undefined : String(profile.location)) : undefined,
    website: profile && 'website' in profile ? (profile.website == null ? undefined : String(profile.website)) : undefined,
  };
}

export async function getMyProfile(): Promise<UserProfile> {
  const admin = await getCurrentAdmin();
  return toUserProfileFromCurrentAdmin(admin);
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
