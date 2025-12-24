// src/services/uploadService.ts

import { getCsrfToken } from '../utils/csrfUtils';

export interface UploadResult {
  id: number; // file_assets.id
  fileName: string;
  fileUrl: string;
  fileSize: number;
  mimeType: string;
}

export async function uploadFile(file: File): Promise<UploadResult> {
  const csrfToken = await getCsrfToken();
  const form = new FormData();
  form.append('file', file);

  const res = await fetch('/api/uploads', {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: form,
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || '上传失败');
  }

  // backend returns UploadResultDTO: { id, fileName, fileUrl, fileSize, mimeType }
  return {
    id: Number(data.id),
    fileName: String(data.fileName ?? file.name),
    fileUrl: String(data.fileUrl),
    fileSize: Number(data.fileSize ?? file.size),
    mimeType: String(data.mimeType ?? file.type ?? 'application/octet-stream'),
  };
}

export async function listUploads(): Promise<UploadResult[]> {
  // 后端暂未提供列表接口；保留函数以兼容现有调用。
  return [];
}
