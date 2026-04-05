import type { UserProfile } from '../../../../types/userProfile';

type ProfileDraftFields = {
  username: string;
  bio: string;
  avatarUrl: string;
  location: string;
  website: string;
};

function normalizeProfileField(v: string): string {
  return v.trim();
}

function emptyProfileFieldToUndefined(v: string): string | undefined {
  const trimmed = normalizeProfileField(v);
  return trimmed ? trimmed : undefined;
}

export function hasProfileDraftChanges(
  baseProfile: Pick<UserProfile, 'username' | 'bio' | 'avatarUrl' | 'location' | 'website'> | null | undefined,
  draft: ProfileDraftFields,
): boolean {
  if (!baseProfile) return false;

  const current = {
    username: normalizeProfileField(draft.username),
    bio: emptyProfileFieldToUndefined(draft.bio),
    avatarUrl: emptyProfileFieldToUndefined(draft.avatarUrl),
    location: emptyProfileFieldToUndefined(draft.location),
    website: emptyProfileFieldToUndefined(draft.website),
  };

  const base = {
    username: normalizeProfileField(baseProfile.username ?? ''),
    bio: emptyProfileFieldToUndefined(baseProfile.bio ?? ''),
    avatarUrl: emptyProfileFieldToUndefined(baseProfile.avatarUrl ?? ''),
    location: emptyProfileFieldToUndefined(baseProfile.location ?? ''),
    website: emptyProfileFieldToUndefined(baseProfile.website ?? ''),
  };

  return (
    current.username !== base.username ||
    current.bio !== base.bio ||
    current.avatarUrl !== base.avatarUrl ||
    current.location !== base.location ||
    current.website !== base.website
  );
}

export function isValidWebsite(v: string): boolean {
  if (!v.trim()) return true;
  try {
    const u = new URL(v);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

export function validateProfileFields(params: {
  username: string;
  bio: string;
  location: string;
  website: string;
}): string | null {
  const u = params.username.trim();
  if (!u) return '昵称不能为空';
  if (u.length > 64) return '昵称不能超过 64 个字符';

  const b = params.bio.trim();
  if (b.length > 500) return '简介不能超过 500 个字符';

  const loc = params.location.trim();
  if (loc.length > 64) return '地区不能超过 64 个字符';

  const w = params.website.trim();
  if (w.length > 191) return '网站不能超过 191 个字符';
  if (!isValidWebsite(w)) return '网站链接格式不正确（需要 http/https）';

  return null;
}
