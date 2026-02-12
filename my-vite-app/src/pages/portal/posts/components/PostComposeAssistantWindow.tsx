import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import FloatingWindow from '../../../../components/ui/FloatingWindow';
import { upsertDraft, type PostDraftDTO } from '../../../../services/draftService';
import { postComposeEditStream, type PostComposeAiStreamEvent } from '../../../../services/aiPostComposeService';
import { getAiChatOptions, type AiChatOptionsDTO, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { tokenizeText } from '../../../../services/opensearchTokenService';
import { uploadFile, type UploadResult } from '../../../../services/uploadService';
import {
  applyPostComposeAiSnapshot,
  createPostComposeAiSnapshot,
  getPendingPostComposeAiSnapshot,
  revertPostComposeAiSnapshot,
  type PostComposeAiSnapshotDTO,
  type PostComposeAiSnapshotTargetType,
} from '../../../../services/postComposeAiSnapshotService';
import { stripThinkBlocks } from '../../../../utils/thinkTags';
import { createAiComposeChannelRouterState, routeAiComposeDelta } from '../../../../utils/aiComposeChannelRouter';

const MAX_VISION_IMAGES = 10;

type Props = {
  draft: PostDraftDTO;
  setDraft: (next: (prev: PostDraftDTO) => PostDraftDTO) => void;
  draftId: string | null;
  postId: number | null;
  busy: boolean;
  setComposeLocked: (locked: boolean) => void;
  setSearchParams: (next: Record<string, string>) => void;
};

function isNumericId(id: string | null | undefined): boolean {
  if (!id) return false;
  const n = Number(id);
  return Number.isFinite(n) && n > 0;
}

function parseIsoMs(s: string | null | undefined): number | null {
  if (!s) return null;
  const t = Date.parse(s);
  return Number.isFinite(t) ? t : null;
}

function buildProviderModelValue(providerId: string, model: string): string {
  const p = String(providerId ?? '').trim();
  const m = String(model ?? '').trim();
  if (!p || !m) return '';
  return `${encodeURIComponent(p)}|${encodeURIComponent(m)}`;
}

function parseProviderModelValue(value: string): { providerId: string; model: string } | null {
  const v = String(value ?? '').trim();
  if (!v) return null;
  const idx = v.indexOf('|');
  if (idx <= 0) return null;
  const p = v.slice(0, idx);
  const m = v.slice(idx + 1);
  try {
    const providerId = decodeURIComponent(p).trim();
    const model = decodeURIComponent(m).trim();
    if (!providerId || !model) return null;
    return { providerId, model };
  } catch {
    return null;
  }
}

function isLikelyImageUrl(url: string): boolean {
  const u = String(url ?? '').trim();
  if (!u) return false;
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(u);
}

function extractMarkdownImageUrls(md: string, maxCount: number): string[] {
  const text = String(md ?? '');
  const out: string[] = [];
  const seen = new Set<string>();
  for (const m of text.matchAll(/!\[[^\]]*]\(([^)]+)\)/g)) {
    const raw = String(m[1] ?? '').trim();
    if (!raw) continue;
    const first = raw.split(/\s+/)[0] ?? '';
    const cleaned = first.replace(/^<|>$/g, '').trim();
    if (!cleaned) continue;
    if (!isLikelyImageUrl(cleaned) && !cleaned.startsWith('/uploads/')) continue;
    if (seen.has(cleaned)) continue;
    seen.add(cleaned);
    out.push(cleaned);
    if (out.length >= maxCount) break;
  }
  return out;
}

export default function PostComposeAssistantWindow(props: Props) {
  const { draft, setDraft, draftId, postId, busy, setComposeLocked, setSearchParams } = props;

  const [input, setInput] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [hasPostOutput, setHasPostOutput] = useState(false);
  const [contextTokens, setContextTokens] = useState<number | null>(null);
  const [contextTokensLoading, setContextTokensLoading] = useState(false);
  const [contextTokensError, setContextTokensError] = useState<string | null>(null);
  const [pendingSnapshot, setPendingSnapshot] = useState<{
    snapshotId: number;
    beforeDraft: PostDraftDTO;
    expiresAtMs: number | null;
  } | null>(null);

  const [log, setLog] = useState<Array<{ role: 'user' | 'assistant'; content: string }>>([]);

  const [chatOptions, setChatOptions] = useState<AiChatOptionsDTO | null>(null);
  const [chatOptionsLoading, setChatOptionsLoading] = useState(false);
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [selectedModel, setSelectedModel] = useState('');

  const [pendingImages, setPendingImages] = useState<UploadResult[]>([]);
  const [ignoredEditorImageUrls, setIgnoredEditorImageUrls] = useState<string[]>([]);
  const [imageUploading, setImageUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const routerRef = useRef(createAiComposeChannelRouterState());
  const autoRevertTimerRef = useRef<number | null>(null);

  useEffect(() => {
    const raw = localStorage.getItem('portal.posts.compose.aiModelPick') ?? '';
    const parsed = parseProviderModelValue(raw);
    if (parsed) {
      setSelectedProviderId(parsed.providerId);
      setSelectedModel(parsed.model);
    }
  }, []);

  useEffect(() => {
    if (!selectedProviderId || !selectedModel) {
      localStorage.setItem('portal.posts.compose.aiModelPick', '');
      return;
    }
    localStorage.setItem('portal.posts.compose.aiModelPick', buildProviderModelValue(selectedProviderId, selectedModel));
  }, [selectedModel, selectedProviderId]);

  useEffect(() => {
    void (async () => {
      setChatOptionsLoading(true);
      try {
        const dto = await getAiChatOptions();
        setChatOptions(dto);
      } catch {
        setChatOptions(null);
      } finally {
        setChatOptionsLoading(false);
      }
    })();
  }, []);

  const providerOptions = useMemo(() => {
    const providers = (chatOptions?.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
    const enabled = providers.filter((p) => Boolean(String(p.id ?? '').trim()));
    const copy = [...enabled];
    copy.sort((a, b) => {
      const la = String(a.name ?? '').trim() ? `${String(a.name).trim()} (${String(a.id ?? '').trim()})` : String(a.id ?? '').trim();
      const lb = String(b.name ?? '').trim() ? `${String(b.name).trim()} (${String(b.id ?? '').trim()})` : String(b.id ?? '').trim();
      return la.localeCompare(lb, 'zh-Hans-CN');
    });
    return copy;
  }, [chatOptions]);

  const flatModelOptions = useMemo(() => {
    const uniq: { providerId: string; providerLabel: string; model: string; value: string }[] = [];
    const seen = new Set<string>();
    for (const p of providerOptions) {
      const providerId = String(p.id ?? '').trim();
      if (!providerId) continue;
      const providerName = String(p.name ?? '').trim();
      const providerLabel = providerName || providerId;
      const rows = Array.isArray(p.chatModels) ? p.chatModels.filter(Boolean) : [];
      for (const m of rows) {
        const modelName = String((m as { name?: unknown }).name ?? '').trim();
        if (!modelName) continue;
        const key = `${providerId}::${modelName}`;
        if (seen.has(key)) continue;
        seen.add(key);
        uniq.push({
          providerId,
          providerLabel,
          model: modelName,
          value: buildProviderModelValue(providerId, modelName),
        });
      }
    }
    uniq.sort((a, b) => {
      const pa = `${a.providerLabel} (${a.providerId})`;
      const pb = `${b.providerLabel} (${b.providerId})`;
      const pCmp = pa.localeCompare(pb, 'zh-Hans-CN');
      if (pCmp !== 0) return pCmp;
      return a.model.localeCompare(b.model, 'zh-Hans-CN');
    });
    return uniq;
  }, [providerOptions]);

  const selectedProviderModelValue = useMemo(
    () => buildProviderModelValue(selectedProviderId, selectedModel),
    [selectedModel, selectedProviderId]
  );

  const contextBaseDraft = pendingSnapshot?.beforeDraft ?? draft;

  const editorImageUrls = useMemo(() => {
    const urls: string[] = [];
    const seen = new Set<string>();
    for (const a of contextBaseDraft.attachments ?? []) {
      const fileUrl = String((a as any)?.fileUrl ?? '').trim();
      if (!fileUrl) continue;
      const mimeType = String((a as any)?.mimeType ?? '').toLowerCase();
      const isImg = mimeType.startsWith('image/') || isLikelyImageUrl(fileUrl);
      if (!isImg) continue;
      if (seen.has(fileUrl)) continue;
      seen.add(fileUrl);
      urls.push(fileUrl);
    }
    for (const u of extractMarkdownImageUrls(String(contextBaseDraft.content ?? ''), MAX_VISION_IMAGES)) {
      if (urls.length >= MAX_VISION_IMAGES) break;
      if (seen.has(u)) continue;
      seen.add(u);
      urls.push(u);
    }
    return urls.slice(0, MAX_VISION_IMAGES);
  }, [contextBaseDraft.attachments, contextBaseDraft.content]);

  const effectiveEditorImageUrls = useMemo(() => {
    const ignored = new Set(ignoredEditorImageUrls.map((x) => String(x ?? '').trim()).filter(Boolean));
    return editorImageUrls.filter((u) => !ignored.has(u));
  }, [editorImageUrls, ignoredEditorImageUrls]);

  const pendingImageUrls = useMemo(() => {
    const urls: string[] = [];
    const seen = new Set<string>();
    for (const it of pendingImages) {
      const u = String(it.fileUrl ?? '').trim();
      if (!u) continue;
      if (seen.has(u)) continue;
      seen.add(u);
      urls.push(u);
    }
    return urls.slice(0, MAX_VISION_IMAGES);
  }, [pendingImages]);

  const visionImageCount = useMemo(() => {
    const seen = new Set<string>();
    for (const u of effectiveEditorImageUrls) seen.add(u);
    for (const u of pendingImageUrls) seen.add(u);
    return Math.min(MAX_VISION_IMAGES, seen.size);
  }, [effectiveEditorImageUrls, pendingImageUrls]);

  const handlePickImages = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleUploadFiles = useCallback(async (files: File[]) => {
    const list = files.filter((f) => String(f.type ?? '').toLowerCase().startsWith('image/'));
    if (list.length === 0) return;
    setImageUploading(true);
    setError(null);
    try {
      const uploaded = await Promise.all(list.slice(0, MAX_VISION_IMAGES).map((f) => uploadFile(f)));
      setPendingImages((prev) => {
        const seen = new Set(prev.map((x) => String(x.fileUrl ?? '').trim()).filter(Boolean));
        const merged = [...prev];
        for (const u of uploaded) {
          const url = String(u.fileUrl ?? '').trim();
          if (!url) continue;
          if (seen.has(url)) continue;
          seen.add(url);
          merged.push(u);
        }
        return merged.slice(0, MAX_VISION_IMAGES);
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setImageUploading(false);
    }
  }, []);

  const target = useMemo(() => {
    if (postId !== null) {
      return { targetType: 'POST' as const, postId };
    }
    const did = isNumericId(draftId) ? Number(draftId) : (isNumericId(draft.id) ? Number(draft.id) : null);
    return { targetType: 'DRAFT' as const, draftId: did };
  }, [draft.id, draftId, postId]);

  useEffect(() => {
    if (busy) return;
    if (streaming) return;
    if (pendingSnapshot) return;
    if (target.targetType === 'DRAFT' && !target.draftId) return;

    void (async () => {
      try {
        const dto = await getPendingPostComposeAiSnapshot({
          targetType: target.targetType,
          draftId: target.targetType === 'DRAFT' ? target.draftId ?? undefined : undefined,
          postId: target.targetType === 'POST' ? target.postId : undefined,
        });
        if (!dto) return;
        await revertAndRestore(dto);
      } catch {
      }
    })();
  }, [busy, pendingSnapshot, streaming, target]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      abortRef.current = null;
      if (autoRevertTimerRef.current != null) {
        window.clearTimeout(autoRevertTimerRef.current);
        autoRevertTimerRef.current = null;
      }
    };
  }, []);

  const refreshContextTokens = useCallback(async () => {
    const title = String(contextBaseDraft.title ?? '');
    const content = String(contextBaseDraft.content ?? '');
    const text = `${title}\n\n${content}`.trim();
    if (!text) {
      setContextTokens(null);
      setContextTokensError(null);
      return;
    }

    setContextTokensLoading(true);
    setContextTokensError(null);
    try {
      const res = await tokenizeText(text.length > 50000 ? text.slice(0, 50000) : text);
      const raw = (res.usage as any)?.input_tokens ?? (res.usage as any)?.inputTokens;
      const n = Number(raw);
      setContextTokens(Number.isFinite(n) ? n : null);
    } catch (e) {
      setContextTokens(null);
      const msg = e instanceof Error ? e.message : String(e);
      setContextTokensError(msg || '不可用');
    } finally {
      setContextTokensLoading(false);
    }
  }, [contextBaseDraft.content, contextBaseDraft.title]);

  useEffect(() => {
    if (streaming) return;
    void refreshContextTokens();
  }, [draft.id, draftId, pendingSnapshot?.snapshotId, postId, refreshContextTokens, streaming]);

  const appendRoutedDelta = useCallback(
    (delta: string): { postChanged: boolean; chatChanged: boolean } => {
      const r = routeAiComposeDelta(routerRef.current, delta);
      if (!hasPostOutput && routerRef.current.hasPost) setHasPostOutput(true);
      return r;
    },
    [hasPostOutput, setHasPostOutput]
  );

  async function ensureDraftIdForSnapshot(): Promise<number> {
    if (postId !== null) throw new Error('编辑帖子模式不需要草稿ID');
    const idFromUrl = isNumericId(draftId) ? Number(draftId) : null;
    if (idFromUrl) return idFromUrl;
    if (isNumericId(draft.id)) return Number(draft.id);

    const saved = await upsertDraft(draft);
    setDraft(() => saved);
    setSearchParams({ draftId: saved.id });
    return Number(saved.id);
  }

  async function revertAndRestore(dto: PostComposeAiSnapshotDTO) {
    setComposeLocked(false);
    setDraft((prev) => {
      const next: PostDraftDTO = {
        ...prev,
        title: dto.beforeTitle ?? '',
        content: dto.beforeContent ?? '',
        boardId: Number(dto.beforeBoardId ?? prev.boardId),
        tags: Array.isArray((dto.beforeMetadata as any)?.tags) ? (dto.beforeMetadata as any).tags.map(String) : prev.tags,
        attachments: Array.isArray((dto.beforeMetadata as any)?.attachments)
          ? (dto.beforeMetadata as any).attachments
          : prev.attachments,
        updatedAt: new Date().toISOString(),
      };
      return next;
    });
    await revertPostComposeAiSnapshot(dto.id);
  }

  async function handleSend() {
    const prompt = input.trim();
    if (!prompt) return;
    if (streaming) return;
    setError(null);

    if (pendingSnapshot) {
      try {
        await handleRevert();
      } catch {
      }
    }

    const nextLog = [...log, { role: 'user' as const, content: prompt }, { role: 'assistant' as const, content: '' }];
    setLog(nextLog);

    try {
      const providerIdToSend = selectedProviderId.trim() ? selectedProviderId.trim() : null;
      const modelToSend = selectedModel.trim() ? selectedModel.trim() : null;

      const targetType: PostComposeAiSnapshotTargetType = postId !== null ? 'POST' : 'DRAFT';
      const ensuredDraftId = targetType === 'DRAFT' ? await ensureDraftIdForSnapshot() : null;

      const beforeDraft = { ...draft };
      const snapshot = await createPostComposeAiSnapshot({
        targetType,
        draftId: targetType === 'DRAFT' ? ensuredDraftId : null,
        postId: targetType === 'POST' ? postId : null,
        beforeTitle: draft.title ?? '',
        beforeContent: draft.content ?? '',
        beforeBoardId: draft.boardId,
        beforeMetadata: {
          tags: draft.tags ?? [],
          attachments: draft.attachments ?? [],
        },
        instruction: prompt,
        providerId: providerIdToSend,
        model: modelToSend,
      });

      routerRef.current = createAiComposeChannelRouterState();
      setHasPostOutput(false);
      setInput('');
      setStreaming(true);
      setComposeLocked(true);

      const expiresAtMs = parseIsoMs(snapshot.expiresAt);
      setPendingSnapshot({ snapshotId: snapshot.id, beforeDraft, expiresAtMs });
      if (autoRevertTimerRef.current != null) {
        window.clearTimeout(autoRevertTimerRef.current);
        autoRevertTimerRef.current = null;
      }
      if (expiresAtMs != null) {
        const waitMs = Math.max(0, expiresAtMs - Date.now() + 50);
        autoRevertTimerRef.current = window.setTimeout(() => {
          void handleRevert();
        }, waitMs);
      }

      abortRef.current?.abort();
      abortRef.current = new AbortController();
      const signal = abortRef.current.signal;

      const visionUrls: Array<{ url: string; mimeType?: string; fileAssetId?: number }> = (() => {
        const urls: Array<{ url: string; mimeType?: string; fileAssetId?: number }> = [];
        const seen = new Set<string>();
        const ignored = new Set(ignoredEditorImageUrls.map((x) => String(x ?? '').trim()).filter(Boolean));
        const take = (u: string, mt?: string, fileAssetId?: number) => {
          const url = String(u ?? '').trim();
          if (!url) return;
          if (ignored.has(url)) return;
          if (seen.has(url)) return;
          seen.add(url);
          urls.push({ url, mimeType: mt ? String(mt) : undefined, fileAssetId });
        };
        for (const a of beforeDraft.attachments ?? []) {
          const fileUrl = String((a as any)?.fileUrl ?? '').trim();
          if (!fileUrl) continue;
          const mimeType = String((a as any)?.mimeType ?? '').trim();
          const isImg = mimeType.toLowerCase().startsWith('image/') || isLikelyImageUrl(fileUrl);
          if (!isImg) continue;
          const fileAssetId = Number((a as any)?.id);
          take(fileUrl, mimeType, Number.isFinite(fileAssetId) ? fileAssetId : undefined);
          if (urls.length >= MAX_VISION_IMAGES) break;
        }
        if (urls.length < MAX_VISION_IMAGES) {
          for (const u of extractMarkdownImageUrls(String(beforeDraft.content ?? ''), MAX_VISION_IMAGES)) {
            take(u, undefined, undefined);
            if (urls.length >= MAX_VISION_IMAGES) break;
          }
        }
        if (urls.length < MAX_VISION_IMAGES) {
          for (const it of pendingImages) {
            take(String(it.fileUrl ?? '').trim(), String(it.mimeType ?? '').trim() || undefined, it.id);
            if (urls.length >= MAX_VISION_IMAGES) break;
          }
        }
        return urls.slice(0, MAX_VISION_IMAGES);
      })();

      await postComposeEditStream(
        {
          snapshotId: snapshot.id,
          instruction: prompt,
          currentTitle: String(beforeDraft.title ?? ''),
          currentContent: String(beforeDraft.content ?? ''),
          chatHistory: nextLog
            .filter((m) => Boolean(String(m.content ?? '').trim()))
            .slice(-20)
            .map((m) => ({ role: m.role, content: String(m.content ?? '') })),
          providerId: providerIdToSend ?? undefined,
          model: modelToSend ?? undefined,
          images: visionUrls.length ? visionUrls : undefined,
        },
        (ev: PostComposeAiStreamEvent) => {
          if (ev.type === 'delta') {
            const { postChanged, chatChanged } = appendRoutedDelta(ev.content);
            if (postChanged) {
              const nextPost = stripThinkBlocks(routerRef.current.post);
              setDraft((prev) => ({ ...prev, content: nextPost, updatedAt: new Date().toISOString() }));
            }
            if (chatChanged) {
              const nextChat = stripThinkBlocks(routerRef.current.chat);
              setLog((prev) => {
                const copy = [...prev];
                for (let i = copy.length - 1; i >= 0; i -= 1) {
                  if (copy[i].role === 'assistant') {
                    copy[i] = { role: 'assistant', content: nextChat };
                    return copy;
                  }
                }
                copy.push({ role: 'assistant', content: nextChat });
                return copy;
              });
            }
          } else if (ev.type === 'error') {
            setError(ev.message);
          }
        },
        signal
      );

      if (!routerRef.current.hasPost) {
        try {
          await handleRevert();
        } catch {
        }
      } else if (!String(routerRef.current.chat ?? '').trim()) {
        setLog((prev) => {
          const copy = [...prev];
          for (let i = copy.length - 1; i >= 0; i -= 1) {
            if (copy[i].role === 'assistant' && !String(copy[i].content ?? '').trim()) {
              copy.splice(i, 1);
              break;
            }
          }
          return copy;
        });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg || '请求失败');
      try {
        await handleRevert();
      } catch {
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  }

  async function handleApply() {
    if (!pendingSnapshot) return;
    if (!routerRef.current.hasPost) return;
    setError(null);
    try {
      const raw = routerRef.current.post || draft.content || '';
      await applyPostComposeAiSnapshot(pendingSnapshot.snapshotId, stripThinkBlocks(raw));
      setPendingSnapshot(null);
      setComposeLocked(false);
      routerRef.current = createAiComposeChannelRouterState();
      setHasPostOutput(false);
      if (autoRevertTimerRef.current != null) {
        window.clearTimeout(autoRevertTimerRef.current);
        autoRevertTimerRef.current = null;
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleClearContext() {
    abortRef.current?.abort();
    abortRef.current = null;
    if (pendingSnapshot) {
      try {
        await handleRevert();
      } catch {
      }
    }
    routerRef.current = createAiComposeChannelRouterState();
    setHasPostOutput(false);
    setError(null);
    setInput('');
    setLog([]);
    setContextTokens(null);
    setContextTokensError(null);
    setPendingImages([]);
    setIgnoredEditorImageUrls([]);
    void refreshContextTokens();
  }

  async function handleRevert() {
    if (!pendingSnapshot) return;
    setError(null);
    try {
      setComposeLocked(false);
      setDraft(() => ({ ...pendingSnapshot.beforeDraft, updatedAt: new Date().toISOString() }));
      await revertPostComposeAiSnapshot(pendingSnapshot.snapshotId);
    } finally {
      setPendingSnapshot(null);
      routerRef.current = createAiComposeChannelRouterState();
      setHasPostOutput(false);
      if (autoRevertTimerRef.current != null) {
        window.clearTimeout(autoRevertTimerRef.current);
        autoRevertTimerRef.current = null;
      }
    }
  }

  const canSend = Boolean(input.trim()) && !streaming;

  return (
    <FloatingWindow
      storageKey="portal.posts.compose.aiWindow"
      title="AI 发帖助手"
      defaultRect={{ width: 440, height: 560 }}
      defaultAnchor="bottom-right"
      snapAnchorOnMount
      collapsedWidth={320}
    >
      <div className="h-full flex flex-col">
        <div className="p-3 border-b border-gray-200 space-y-2">
          <div className="text-xs text-gray-600">
            让 AI 直接改写正文为 Markdown。生成期间会锁定编辑区；未采纳将自动回滚。
          </div>
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              <button
                type="button"
                className="px-2 py-1 rounded-md border border-gray-300 bg-white text-xs hover:bg-gray-50 disabled:opacity-60"
                onClick={() => void handleClearContext()}
              >
                清空上下文
              </button>
              <select
                className="min-w-0 max-w-[240px] px-2 py-1 rounded-md border border-gray-300 bg-white text-xs disabled:opacity-60"
                value={selectedProviderModelValue}
                disabled={streaming || chatOptionsLoading}
                onChange={(e) => {
                  const parsed = parseProviderModelValue(String(e.target.value ?? ''));
                  if (!parsed) {
                    setSelectedProviderId('');
                    setSelectedModel('');
                    return;
                  }
                  setSelectedProviderId(parsed.providerId);
                  setSelectedModel(parsed.model);
                }}
              >
                <option value="">
                  {chatOptionsLoading
                    ? '模型加载中…'
                    : visionImageCount
                      ? '模型：自动(图片聊天/视觉模型池)'
                      : '模型：自动(文本聊天/均衡负载)'}
                </option>
                {flatModelOptions.map((o) => (
                  <option key={`${o.providerId}::${o.model}`} value={o.value}>
                    {o.providerLabel} / {o.model}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-center gap-2 text-xs text-gray-600">
              <span className="whitespace-nowrap" title={contextTokensError ?? undefined}>
                上下文用量：
                {contextTokensLoading ? '计算中…' : contextTokens != null ? `${contextTokens} tok` : contextTokensError ? '不可用' : '-'}
              </span>
            </div>
          </div>
          {error ? <div className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-md px-2 py-1">{error}</div> : null}
          {pendingSnapshot ? (
            <div className="flex items-center gap-2">
              {hasPostOutput ? (
                <>
                  <button
                    type="button"
                    className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-60"
                    disabled={streaming}
                    onClick={() => void handleApply()}
                  >
                    采纳
                  </button>
                  <button
                    type="button"
                    className="px-3 py-1.5 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50 disabled:opacity-60"
                    disabled={streaming}
                    onClick={() => void handleRevert()}
                  >
                    撤回
                  </button>
                </>
              ) : null}
              {streaming ? (
                <button
                  type="button"
                  className="ml-auto px-3 py-1.5 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50"
                  onClick={() => {
                    abortRef.current?.abort();
                    void handleRevert();
                  }}
                >
                  停止
                </button>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="flex-1 min-h-0 overflow-auto p-3 space-y-3 bg-white">
          {log.length === 0 ? (
            <div className="text-sm text-gray-500">输入你的要求开始，例如：把这段内容整理成“背景/问题/方案/步骤”的结构。</div>
          ) : (
            log.map((m, idx) => (
              <div key={`${idx}-${m.role}`} className={m.role === 'user' ? 'text-right' : 'text-left'}>
                <div
                  className={
                    'inline-block max-w-[92%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap break-words ' +
                    (m.role === 'user' ? 'bg-blue-600 text-white' : 'bg-gray-50 border border-gray-200 text-gray-900')
                  }
                >
                  {m.content || (m.role === 'assistant' && streaming ? '…' : '')}
                </div>
              </div>
            ))
          )}
        </div>

        <div className="p-3 border-t border-gray-200 bg-white">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []);
              e.target.value = '';
              void handleUploadFiles(files);
            }}
            disabled={streaming}
          />
          {visionImageCount ? (
            <div className="mb-2 text-xs text-gray-600 flex items-center justify-between gap-2">
              <span className="min-w-0">
                图片：正文 {effectiveEditorImageUrls.length} + 已附加 {pendingImages.length}（最多 {MAX_VISION_IMAGES}）
              </span>
              <button
                type="button"
                className="shrink-0 px-2 py-1 rounded-md border border-gray-300 bg-white text-xs hover:bg-gray-50 disabled:opacity-60"
                onClick={() => {
                  setPendingImages([]);
                  setIgnoredEditorImageUrls((prev) => {
                    const seen = new Set(prev.map((x) => String(x ?? '').trim()).filter(Boolean));
                    const merged = [...prev];
                    for (const u of editorImageUrls) {
                      if (seen.has(u)) continue;
                      seen.add(u);
                      merged.push(u);
                    }
                    return merged;
                  });
                }}
                disabled={streaming || imageUploading}
              >
                清空附加图片
              </button>
            </div>
          ) : null}
          {(pendingImages.length || effectiveEditorImageUrls.length) ? (
            <div className="mb-2 flex gap-2 overflow-x-auto">
              {effectiveEditorImageUrls.slice(0, MAX_VISION_IMAGES).map((url) => (
                <div key={`editor-${url}`} className="shrink-0 relative w-16 h-16 rounded border overflow-hidden bg-gray-50">
                  <a href={url} target="_blank" rel="noreferrer" className="block w-full h-full" title="来自正文/附件">
                    <img src={url} alt="image" className="w-full h-full object-cover" loading="lazy" />
                  </a>
                  <button
                    type="button"
                    className="absolute top-0 right-0 px-1 py-0.5 text-[10px] bg-white/90 border border-gray-200 rounded-bl"
                    onClick={() => setIgnoredEditorImageUrls((prev) => (prev.includes(url) ? prev : [...prev, url]))}
                    disabled={streaming}
                  >
                    删除
                  </button>
                </div>
              ))}
              {pendingImages.slice(0, MAX_VISION_IMAGES).map((it) => (
                <div key={`pending-${it.id}-${it.fileUrl}`} className="shrink-0 relative w-16 h-16 rounded border overflow-hidden bg-gray-50">
                  <img src={it.fileUrl} alt={it.fileName} className="w-full h-full object-cover" loading="lazy" />
                  <button
                    type="button"
                    className="absolute top-0 right-0 px-1 py-0.5 text-[10px] bg-white/90 border border-gray-200 rounded-bl"
                    onClick={() => setPendingImages((prev) => prev.filter((x) => x.fileUrl !== it.fileUrl))}
                    disabled={streaming}
                  >
                    删除
                  </button>
                </div>
              ))}
            </div>
          ) : null}
          <div className="flex gap-2">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              rows={2}
              className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              placeholder="告诉 AI 你想怎么改写正文…"
              disabled={streaming}
              onPaste={(e) => {
                const items = Array.from(e.clipboardData?.items ?? []);
                const files: File[] = [];
                for (const it of items) {
                  if (it.kind !== 'file') continue;
                  if (!String(it.type ?? '').toLowerCase().startsWith('image/')) continue;
                  const f = it.getAsFile();
                  if (f) files.push(f);
                }
                if (files.length === 0) return;
                void handleUploadFiles(files);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                  e.preventDefault();
                  void handleSend();
                }
              }}
            />
            <button
              type="button"
              disabled={!canSend}
              className="px-3 py-2 rounded-md bg-gray-900 text-white text-sm hover:bg-gray-800 disabled:opacity-60"
              onClick={() => void handleSend()}
            >
              {streaming ? '生成中…' : '发送'}
            </button>
          </div>
          <div className="mt-2 text-xs text-gray-500 flex items-center justify-between gap-2">
            <span className="min-w-0">快捷键：Ctrl/⌘ + Enter 发送 · Ctrl/⌘ + V 粘贴图片</span>
            <button
              type="button"
              className="shrink-0 px-2 py-1 rounded-md border border-gray-300 bg-white text-xs hover:bg-gray-50 disabled:opacity-60"
              onClick={handlePickImages}
              disabled={streaming || imageUploading}
            >
              {imageUploading ? '上传中…' : '添加图片'}
            </button>
          </div>
        </div>
      </div>
    </FloatingWindow>
  );
}
