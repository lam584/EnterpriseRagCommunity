import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

export type PostFileExtractionStatus = 'PENDING' | 'READY' | 'FAILED' | 'NONE' | string;

export type PostFileExtractionAdminListItemDTO = {
  attachmentId: number;
  postId: number;
  fileAssetId?: number | null;
  url?: string | null;
  fileName?: string | null;
  originalName?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
  ext?: string | null;
  extractStatus?: PostFileExtractionStatus | null;
  extractionUpdatedAt?: string | null;
  extractionErrorMessage?: string | null;
  parseDurationMs?: number | null;
  pages?: number | null;
  textCharCount?: number | null;
  textTokenCount?: number | null;
  tokenCountMode?: string | null;
  imageCount?: number | null;
};

export type ExtractedImageItemDTO = {
  index?: number | null;
  placeholder?: string | null;
  url?: string | null;
  fileName?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
};

export type PostFileExtractionAdminDetailDTO = PostFileExtractionAdminListItemDTO & {
  extractedText?: string | null;
  extractedMetadataJson?: string | null;
  extractedMetadata?: Record<string, unknown> | null;
  extractedImages?: ExtractedImageItemDTO[] | null;
  llmInputPreview?: string | null;
};

const apiUrl = serviceApiUrl;

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}


export async function adminListPostFiles(params: {
  page?: number;
  pageSize?: number;
  postId?: number;
  fileAssetId?: number;
  keyword?: string;
  extractStatus?: string;
} = {}): Promise<SpringPage<PostFileExtractionAdminListItemDTO>> {
  const qs = buildQuery(params);
  const res = await fetch(apiUrl(`/api/admin/post-files${qs}`), { method: 'GET', credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取帖子文件解析列表失败');
  return data as SpringPage<PostFileExtractionAdminListItemDTO>;
}

export async function adminGetPostFileDetail(attachmentId: number): Promise<PostFileExtractionAdminDetailDTO> {
  const res = await fetch(apiUrl(`/api/admin/post-files/${attachmentId}`), { method: 'GET', credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取解析详情失败');
  return data as PostFileExtractionAdminDetailDTO;
}

export async function adminReextractPostFile(attachmentId: number): Promise<PostFileExtractionAdminDetailDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/post-files/${attachmentId}/reextract`), {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '重新解析失败');
  return data as PostFileExtractionAdminDetailDTO;
}
