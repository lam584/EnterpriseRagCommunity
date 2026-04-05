import type { AiChatStreamRequest } from '../../../../services/aiChatService';

type UploadImage = { fileUrl: string; mimeType?: string; id?: number };
type UploadFile = { fileUrl: string; mimeType?: string; id?: number; fileName?: string };

export function buildAssistantChatRequest(params: {
  sessionId?: number | null;
  message: string;
  deepThink: boolean;
  useRag: boolean;
  ragTopK?: number;
  temperature?: number | null;
  topP?: number | null;
  providerId?: string;
  model?: string;
  images: UploadImage[];
  files: UploadFile[];
}): AiChatStreamRequest {
  const { sessionId, message, deepThink, useRag, ragTopK, temperature, topP, providerId, model, images, files } = params;
  return {
    sessionId: sessionId && sessionId > 0 ? sessionId : undefined,
    message,
    deepThink,
    useRag,
    ragTopK,
    temperature: temperature ?? undefined,
    topP: topP ?? undefined,
    providerId,
    model,
    images: images.length ? images.map((x) => ({ url: x.fileUrl, mimeType: x.mimeType, fileAssetId: x.id })) : undefined,
    files: files.length
      ? files.map((x) => ({ url: x.fileUrl, mimeType: x.mimeType, fileAssetId: x.id, fileName: x.fileName }))
      : undefined,
  };
}
