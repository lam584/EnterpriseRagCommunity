export function getDisplayUsername(username?: string | null): string {
  const name = username?.trim();
  return name && name.length > 0 ? name : '未登录';
}

export function getAvatarFallbackText(username?: string | null): string {
  const name = username?.trim();
  if (!name) return 'U';
  return name.slice(0, 1).toUpperCase();
}
