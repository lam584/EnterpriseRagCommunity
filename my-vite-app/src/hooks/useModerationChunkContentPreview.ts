import { useEffect, useState } from 'react';
import { adminGetModerationChunkLogContent, type ModerationChunkContentPreview } from '../services/moderationChunkReviewLogsService';

const cache = new Map<number, ModerationChunkContentPreview>();
const inflight = new Map<number, Promise<ModerationChunkContentPreview>>();

export function useModerationChunkContentPreview(chunkId: number | null | undefined, enabled: boolean) {
  const [data, setData] = useState<ModerationChunkContentPreview | null>(() => (chunkId && cache.has(chunkId) ? (cache.get(chunkId) as ModerationChunkContentPreview) : null));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled || !chunkId) {
      setData(null);
      setError(null);
      return;
    }
    if (cache.has(chunkId)) {
      setData(cache.get(chunkId) as ModerationChunkContentPreview);
      setError(null);
      return;
    }
    setData(null);
    setError(null);
    const controller = new AbortController();
    const run = async () => {
      try {
        const pending = inflight.get(chunkId) ?? adminGetModerationChunkLogContent(chunkId, controller.signal);
        inflight.set(chunkId, pending);
        const result = await pending;
        cache.set(chunkId, result);
        inflight.delete(chunkId);
        if (controller.signal.aborted) return;
        setData(result);
      } catch (e) {
        inflight.delete(chunkId);
        if (controller.signal.aborted) return;
        setError(e instanceof Error ? e.message : String(e));
      }
    };
    void run();
    return () => controller.abort();
  }, [chunkId, enabled]);

  return { data, error };
}