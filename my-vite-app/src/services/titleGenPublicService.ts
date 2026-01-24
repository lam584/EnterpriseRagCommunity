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

export type PostTitleGenPublicConfigDTO = {
  enabled: boolean;
  defaultCount: number;
  maxCount: number;
};

export async function getPostTitleGenPublicConfig(): Promise<PostTitleGenPublicConfigDTO> {
  const res = await fetch(apiUrl('/api/ai/posts/title-gen/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取标题生成配置失败');
  return data as PostTitleGenPublicConfigDTO;
}

