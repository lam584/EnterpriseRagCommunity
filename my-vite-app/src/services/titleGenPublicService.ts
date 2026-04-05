import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


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

