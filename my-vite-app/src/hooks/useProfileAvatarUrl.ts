import { useEffect, useState } from 'react';
import { getMyProfile } from '../services/accountService';

export function useProfileAvatarUrl(isAuthenticated: boolean): string | undefined {
  const [profileAvatarUrl, setProfileAvatarUrl] = useState<string | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;

    async function loadProfileAvatar() {
      if (!isAuthenticated) {
        setProfileAvatarUrl(undefined);
        return;
      }

      try {
        const profile = await getMyProfile();
        if (!cancelled) {
          setProfileAvatarUrl(profile.avatarUrl);
        }
      } catch {
        if (!cancelled) {
          setProfileAvatarUrl(undefined);
        }
      }
    }

    void loadProfileAvatar();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  return profileAvatarUrl;
}
