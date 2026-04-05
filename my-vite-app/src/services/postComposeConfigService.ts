import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


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

