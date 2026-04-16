import { useCallback, useState } from 'react';
import type { PostDTO } from '../../../../services/postService';
import type { SpringPage } from '../../../../types/page';
import type { UpdateUserProfileRequest, UserProfile } from '../../../../types/userProfile';

export type ProfileDraftFields = {
  username: string;
  bio: string;
  avatarUrl: string;
  location: string;
  website: string;
};

const EMPTY_PROFILE_DRAFT: ProfileDraftFields = {
  username: '',
  bio: '',
  avatarUrl: '',
  location: '',
  website: '',
};

export function toProfileDraft(profile: Pick<UserProfile, 'username' | 'bio' | 'avatarUrl' | 'location' | 'website'>): ProfileDraftFields {
  return {
    username: profile.username ?? '',
    bio: profile.bio ?? '',
    avatarUrl: profile.avatarUrl ?? '',
    location: profile.location ?? '',
    website: profile.website ?? '',
  };
}

function toPatchValue(value: string): string | null | undefined {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  return trimmed;
}

export function toProfileUpdateRequest(draft: ProfileDraftFields): UpdateUserProfileRequest {
  return {
    username: draft.username.trim(),
    bio: toPatchValue(draft.bio),
    avatarUrl: toPatchValue(draft.avatarUrl),
    location: toPatchValue(draft.location),
    website: toPatchValue(draft.website),
  };
}

export function useProfilePageState() {
  const [saveOk, setSaveOk] = useState<string | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [baseProfile, setBaseProfile] = useState<UserProfile | null>(null);
  const [isEditing, setIsEditing] = useState(false);

  const [username, setUsername] = useState(EMPTY_PROFILE_DRAFT.username);
  const [bio, setBio] = useState(EMPTY_PROFILE_DRAFT.bio);
  const [avatarUrl, setAvatarUrl] = useState(EMPTY_PROFILE_DRAFT.avatarUrl);
  const [location, setLocation] = useState(EMPTY_PROFILE_DRAFT.location);
  const [website, setWebsite] = useState(EMPTY_PROFILE_DRAFT.website);

  const [postsLoading, setPostsLoading] = useState(false);
  const [postsErr, setPostsErr] = useState<string | null>(null);
  const [postsPage, setPostsPage] = useState<SpringPage<PostDTO> | null>(null);
  const [postsPageNo, setPostsPageNo] = useState(1);
  const [postsReloadTick, setPostsReloadTick] = useState(0);

  const resetDraftFrom = useCallback((profileDraft: Pick<UserProfile, 'username' | 'bio' | 'avatarUrl' | 'location' | 'website'>) => {
    const nextDraft = toProfileDraft(profileDraft);
    setUsername(nextDraft.username);
    setBio(nextDraft.bio);
    setAvatarUrl(nextDraft.avatarUrl);
    setLocation(nextDraft.location);
    setWebsite(nextDraft.website);
  }, []);

  return {
    saveOk,
    setSaveOk,
    profile,
    setProfile,
    baseProfile,
    setBaseProfile,
    isEditing,
    setIsEditing,
    username,
    setUsername,
    bio,
    setBio,
    avatarUrl,
    setAvatarUrl,
    location,
    setLocation,
    website,
    setWebsite,
    postsLoading,
    setPostsLoading,
    postsErr,
    setPostsErr,
    postsPage,
    setPostsPage,
    postsPageNo,
    setPostsPageNo,
    postsReloadTick,
    setPostsReloadTick,
    resetDraftFrom,
  };
}
