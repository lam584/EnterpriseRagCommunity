const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export type PostComposeConfigDTO = {
  requireTitle?: boolean;
  requireTags?: boolean;
  maxAttachments?: number;
  maxContentChars?: number;
  chunkThresholdChars?: number | null;
  bypassAttachmentLimitWhenChunked?: boolean;
};

export async function getPostComposeConfig(): Promise<PostComposeConfigDTO> {
  const res = await fetch(apiUrl('/api/public/posts/compose-config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取发帖配置失败');
  return data as PostComposeConfigDTO;
}

