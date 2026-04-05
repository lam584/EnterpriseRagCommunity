import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


export type PostTagGenPublicConfigDTO = {
  enabled: boolean;
  defaultCount: number;
  maxCount: number;
  maxContentChars: number;
};

export async function getPostTagGenPublicConfig(): Promise<PostTagGenPublicConfigDTO> {
  const res = await fetch(apiUrl('/api/ai/posts/tag-gen/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取主题标签生成配置失败');
  return data as PostTagGenPublicConfigDTO;
}

