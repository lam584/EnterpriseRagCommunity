// src/services/uploadService.ts

export interface UploadResult {
  id: number; // corresponds to fileAssetId or attachment id
  fileName: string;
  fileUrl: string;
  fileSize: number;
  mimeType: string;
}

let seq = 1;
const files: UploadResult[] = [];

export async function uploadFile(file: File): Promise<UploadResult> {
  // mock upload with data URL
  const id = seq++;
  const fileUrl = URL.createObjectURL(file);
  const result: UploadResult = {
    id,
    fileName: file.name,
    fileUrl,
    fileSize: file.size,
    mimeType: file.type || 'application/octet-stream',
  };
  files.push(result);
  await new Promise(r => setTimeout(r, 300));
  return { ...result };
}

export async function listUploads(): Promise<UploadResult[]> {
  await new Promise(r => setTimeout(r, 100));
  return files.map(f => ({ ...f }));
}

