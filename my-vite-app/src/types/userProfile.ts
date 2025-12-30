export type UserProfile = {
  id: number;
  email: string;
  username: string;
  /** optional fields stored in users.metadata.profile.* */
  avatarUrl?: string;
  bio?: string;
  location?: string;
  website?: string;
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
