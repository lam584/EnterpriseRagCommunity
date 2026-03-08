export type UserProfile = {
  id: number;
  email: string;
  username: string;
  avatarUrl?: string;
  bio?: string;
  location?: string;
  website?: string;
  publicProfile?: {
    username: string;
    avatarUrl?: string;
    bio?: string;
    location?: string;
    website?: string;
  };
  profileModeration?: {
    caseType?: string;
    queueId?: number;
    status?: string;
    stage?: string;
    updatedAt?: string;
    reason?: string;
  };
};

export type UpdateUserProfileRequest = {
  username?: string;
  /**
   * Optional fields are PATCH-like:
   * - omitted/undefined: no change
   * - null: clear the field
   * - string: set/update
   */
  avatarUrl?: string | null;
  bio?: string | null;
  location?: string | null;
  website?: string | null;
};
