import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import { useNavigate, useOutletContext, useParams, useSearchParams } from 'react-router-dom';
import MarkdownEditor from '../../../../components/ui/MarkdownEditor';
import { createPost, getPost, updatePost, type PostDTO } from '../../../../services/postService';
import {
  cancelResumableUpload,
  findUploadBySha256,
  getResumableUploadStatus,
  uploadFileResumable,
  type ResumableUploadHandle,
  type UploadResult,
} from '../../../../services/uploadService';
import {
  createEmptyDraft,
  deleteDraft,
  getDraft,
  upsertDraft,
  type PostDraftDTO,
} from '../../../../services/draftService';
import { listBoards, type BoardDTO } from '../../../../services/boardService';
import { suggestPostTitles } from '../../../../services/aiTitleService';
import { getPostTitleGenPublicConfig, type PostTitleGenPublicConfigDTO } from '../../../../services/titleGenPublicService';
import { createTag, listTags, type TagDTO } from '../../../../services/tagService';
import { suggestPostTags } from '../../../../services/aiTagService';
import { getPostTagGenPublicConfig, type PostTagGenPublicConfigDTO } from '../../../../services/tagGenPublicService';
import { getLangLabelGenConfig, suggestPostLangLabels, type LangLabelGenPublicConfigDTO } from '../../../../services/aiLangLabelService';
import { getPostComposeConfig, type PostComposeConfigDTO } from '../../../../services/postComposeConfigService';
import { getUploadFormatsConfig, type UploadFormatsConfigDTO, type UploadFormatRuleDTO } from '../../../../services/uploadFormatsPublicService';
import { getMyTranslatePreferences } from '../../../../services/accountPreferencesService';
import { escapeMarkdownLinkDestination, escapeMarkdownLinkText } from '../../../../utils/markdownUtils';
import { computeFileSha256 } from '../../../../utils/fileSha256';
import {
  loadDraftUploadSessions,
  saveDraftUploadSessions,
  type DraftUploadSession,
} from '../../../../utils/draftUploadProgressStore';
import type { PostsOutletContext } from '../PostsLayout';
import PostComposeAssistantWindow from '../components/PostComposeAssistantWindow';
import PostUploadTransferWindow, { type UploadItem as TransferUploadItem } from '../components/PostUploadTransferWindow';
import {PostsBasicSection} from './PostsCreatePage.sections';
import {
    clampCount,
    ensureTagSlugsWithAvailableTags,
    formatUploadRetryMessage,
    getErrorMessage,
    getFieldErrors,
    normalizeCount,
    shouldReportThrottledProgress,
    suggestLanguagesToPublish,
    type UploadItem,
    type UploadItemStatus,
} from './PostsCreatePage.shared';
import {useEditorAutoHeight} from './use-editor-auto-height';

export default function PostsCreatePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { postId: postIdParam } = useParams();
  const { composePreviewOpen, setComposePreviewOpen } = useOutletContext<PostsOutletContext>();

  const draftId = searchParams.get('draftId');
  const postId = useMemo(() => {
    if (!postIdParam) return null;
    const n = Number(postIdParam);
    return Number.isFinite(n) ? n : null;
  }, [postIdParam]);

  const [loadingDraft, setLoadingDraft] = useState<boolean>(false);
  const [loadingPost, setLoadingPost] = useState<boolean>(false);
  const [loadingBoards, setLoadingBoards] = useState<boolean>(false);

  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [composeLocked, setComposeLocked] = useState(false);

  const [boards, setBoards] = useState<BoardDTO[]>([]);

  const [draft, setDraft] = useState<PostDraftDTO>(() => createEmptyDraft());
  const contentEditorWrapRef = useRef<HTMLDivElement | null>(null);
    const contentEditorHeightPx = useEditorAutoHeight(contentEditorWrapRef);

  const [uploadItems, setUploadItems] = useState<UploadItem[]>([]);
  const uploadHandleRef = useRef<Map<string, ResumableUploadHandle>>(new Map());
  const uploadHashAbortRef = useRef<Map<string, AbortController>>(new Map());
  const uploadSeqRef = useRef(0);
  const [uploadTransferWindowOpen, setUploadTransferWindowOpen] = useState(false);
  const uploadTransferWindowOpenRef = useRef(false);
  const uploadItemsRef = useRef<UploadItem[]>([]);
  const autoOpenTransferWindowTimerRef = useRef<Map<string, number>>(new Map());
  const mountedRef = useRef(true);
  const preserveServerUploadsOnUnmountRef = useRef(false);
  const resumeFileInputRef = useRef<HTMLInputElement | null>(null);
  const resumeTargetUploadItemIdRef = useRef<string | null>(null);
  const uploadProgressPendingRef = useRef<Map<string, { loaded: number; total: number }>>(new Map());
  const uploadProgressTimerRef = useRef<number | null>(null);
  const verifyPollTimerRef = useRef<number | null>(null);
  const scheduleVerifyPollRef = useRef<() => void>(() => {});
  const [composeAssistantWindowOpen, setComposeAssistantWindowOpen] = useState(false);

  const [useAiTitle, setUseAiTitle] = useState(true);
  const [titleSuggesting, setTitleSuggesting] = useState(false);
  const [titleSuggestError, setTitleSuggestError] = useState<string | null>(null);
  const [titleCandidates, setTitleCandidates] = useState<string[]>([]);
  const [titleDropdownOpen, setTitleDropdownOpen] = useState(false);
  const [titleGenConfig, setTitleGenConfig] = useState<PostTitleGenPublicConfigDTO | null>(null);
  const [titleGenConfigError, setTitleGenConfigError] = useState<string | null>(null);
  const [titleGenCount, setTitleGenCount] = useState<number>(5);

  const [availableTags, setAvailableTags] = useState<TagDTO[]>([]);
  const [loadingTags, setLoadingTags] = useState(false);
  const [tagsError, setTagsError] = useState<string | null>(null);
  const [tagQuery, setTagQuery] = useState('');

  const [useAiTags, setUseAiTags] = useState(true);
  const [tagSuggesting, setTagSuggesting] = useState(false);
  const [tagSuggestError, setTagSuggestError] = useState<string | null>(null);
  const [tagCandidates, setTagCandidates] = useState<string[]>([]);
  const [tagGenConfig, setTagGenConfig] = useState<PostTagGenPublicConfigDTO | null>(null);
  const [tagGenConfigError, setTagGenConfigError] = useState<string | null>(null);
  const [tagGenCount, setTagGenCount] = useState<number>(5);

  const [langLabelGenConfig, setLangLabelGenConfig] = useState<LangLabelGenPublicConfigDTO | null>(null);
  const [langLabelGenConfigError, setLangLabelGenConfigError] = useState<string | null>(null);
  const [basePostMetadata, setBasePostMetadata] = useState<Record<string, unknown> | null>(null);

  const [composeConfig, setComposeConfig] = useState<PostComposeConfigDTO | null>(null);
  const [composeConfigError, setComposeConfigError] = useState<string | null>(null);

  const [uploadFormatsConfig, setUploadFormatsConfig] = useState<UploadFormatsConfigDTO | null>(null);
  const [uploadFormatsConfigError, setUploadFormatsConfigError] = useState<string | null>(null);

  const isEditingDraft = useMemo(() => Boolean(draftId), [draftId]);
  // const isEditingPost = useMemo(() => postId !== null, [postId]);

  useEffect(() => {
    if (!composeLocked) return;
    const el = document.activeElement as HTMLElement | null;
    if (el && typeof el.blur === 'function') el.blur();
  }, [composeLocked]);

  useEffect(() => {
    const autoOpenTransferWindowTimers = autoOpenTransferWindowTimerRef.current;
    const uploadHandles = uploadHandleRef.current;
    return () => {
      mountedRef.current = false;
      const timers = Array.from(autoOpenTransferWindowTimers.values());
      autoOpenTransferWindowTimers.clear();
      for (const t of timers) window.clearTimeout(t);
      const handles = Array.from(uploadHandles.values());
      uploadHandles.clear();
      for (const h of handles) {
        if (preserveServerUploadsOnUnmountRef.current) {
          h.pause();
        } else {
          h.cancel();
        }
      }
    };
  }, []);

  useEffect(() => {
    uploadTransferWindowOpenRef.current = uploadTransferWindowOpen;
    if (uploadTransferWindowOpen) {
      const timers = Array.from(autoOpenTransferWindowTimerRef.current.values());
      autoOpenTransferWindowTimerRef.current.clear();
      for (const t of timers) window.clearTimeout(t);
    }
  }, [uploadTransferWindowOpen]);

  useEffect(() => {
    uploadItemsRef.current = uploadItems;
  }, [uploadItems]);

  useEffect(() => {
    let mounted = true;
    const loadBoards = async () => {
      setLoadingBoards(true);
      try {
        const bs = await listBoards();
        if (!mounted) return;
        setBoards(bs);
      } catch (e: unknown) {
        // don't block editor
        console.warn('加载版块失败', e);
      } finally {
        if (mounted) setLoadingBoards(false);
      }
    };
    loadBoards();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const loadTags = async () => {
      setLoadingTags(true);
      setTagsError(null);
      try {
        const ts = await listTags({ page: 1, pageSize: 200, type: 'TOPIC', isActive: true, sortBy: 'createdAt', sortOrder: 'desc' });
        if (!mounted) return;
        setAvailableTags(ts);
      } catch (e: unknown) {
        if (!mounted) return;
        setAvailableTags([]);
        setTagsError(getErrorMessage(e, '加载标签列表失败'));
      } finally {
        if (mounted) setLoadingTags(false);
      }
    };
    void loadTags();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setTitleGenConfigError(null);
      try {
        const cfg = await getPostTitleGenPublicConfig();
        if (!mounted) return;
        setTitleGenConfig(cfg);
        setTitleGenCount((prev) => clampCount(prev, cfg?.maxCount));
        setUseAiTitle(cfg?.enabled !== false);
        if (cfg && cfg.enabled === false) {
          setTitleCandidates([]);
          setTitleDropdownOpen(false);
          setTitleSuggestError(null);
        }
      } catch (e: unknown) {
        if (!mounted) return;
        setTitleGenConfig(null);
        setTitleGenConfigError(getErrorMessage(e, '获取标题生成配置失败'));
        setTitleGenCount((prev) => clampCount(prev, null));
        setUseAiTitle(true);
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        const p = await getMyTranslatePreferences();
        if (!mounted) return;
        setTitleGenCount((prev) => clampCount(normalizeCount(p.titleGenCount, prev), titleGenConfig?.maxCount));
        setTagGenCount((prev) => clampCount(normalizeCount(p.tagGenCount, prev), tagGenConfig?.maxCount));
      } catch {
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, [tagGenConfig?.maxCount, titleGenConfig?.maxCount]);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setTagGenConfigError(null);
      try {
        const cfg = await getPostTagGenPublicConfig();
        if (!mounted) return;
        setTagGenConfig(cfg);
        setTagGenCount((prev) => clampCount(prev, cfg?.maxCount));
        setUseAiTags(cfg?.enabled !== false);
        if (cfg && cfg.enabled === false) {
          setTagCandidates([]);
          setTagSuggestError(null);
        }
      } catch (e: unknown) {
        if (!mounted) return;
        setTagGenConfig(null);
        setTagGenConfigError(getErrorMessage(e, '获取主题标签生成配置失败'));
        setTagGenCount((prev) => clampCount(prev, null));
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setLangLabelGenConfigError(null);
      try {
        const cfg = await getLangLabelGenConfig();
        if (!mounted) return;
        setLangLabelGenConfig(cfg);
      } catch (e: unknown) {
        if (!mounted) return;
        setLangLabelGenConfig(null);
        setLangLabelGenConfigError(getErrorMessage(e, '获取语言标签配置失败'));
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setComposeConfigError(null);
      try {
        const cfg = await getPostComposeConfig();
        if (!mounted) return;
        setComposeConfig(cfg);
      } catch (e: unknown) {
        if (!mounted) return;
        setComposeConfig(null);
        setComposeConfigError(getErrorMessage(e, '获取发帖配置失败'));
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setUploadFormatsConfigError(null);
      try {
        const cfg = await getUploadFormatsConfig();
        if (!mounted) return;
        setUploadFormatsConfig(cfg);
      } catch (e: unknown) {
        if (!mounted) return;
        setUploadFormatsConfig(null);
        setUploadFormatsConfigError(getErrorMessage(e, '获取上传格式配置失败'));
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    // Edit post mode: load from backend.
    if (postId === null) return;

    let mounted = true;
    const load = async () => {
      setLoadingPost(true);
      setError(null);
      try {
        const p: PostDTO = await getPost(postId);
        if (!mounted) return;
        setBasePostMetadata((p && typeof p.metadata === 'object' && p.metadata !== null ? (p.metadata as Record<string, unknown>) : null) ?? null);
        setDraft((prev) => ({
          ...prev,
          // draftId is irrelevant in edit mode
          title: p.title ?? '',
          content: p.content ?? '',
          boardId: p.boardId,
          tags: p.tags ?? prev.tags,
          attachments: Array.isArray(p.attachments) ? p.attachments : prev.attachments,
          updatedAt: new Date().toISOString(),
        }));
        // If entering edit mode, clean draftId from URL to avoid confusion.
        if (draftId) setSearchParams({});
      } catch (e: unknown) {
        setError(getErrorMessage(e, '加载帖子失败'));
      } finally {
        if (mounted) setLoadingPost(false);
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, [postId, draftId, setSearchParams]);

  useEffect(() => {
    // Draft mode is disabled when editing an existing post.
    if (postId !== null) return;

    let mounted = true;
    const load = async () => {
      if (!draftId) {
        setDraft(createEmptyDraft());
        return;
      }
      setLoadingDraft(true);
      setError(null);
      try {
        const d = await getDraft(draftId);
        if (!mounted) return;
        if (!d) {
          setError('草稿不存在或已被删除。');
          setDraft(createEmptyDraft());
          return;
        }
        setDraft(d);
      } catch (e: unknown) {
        setError(getErrorMessage(e, '加载草稿失败'));
      } finally {
        setLoadingDraft(false);
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, [draftId, postId]);

  useEffect(() => {
    if (postId !== null) return;
    if (!draftId) return;

    let mounted = true;
    const load = async () => {
      const sessions = loadDraftUploadSessions(draftId);
      if (!sessions.length) return;

      const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
      const results = await Promise.all(
        sessions.slice(0, 64).map(async (s) => {
          try {
            const st = await getResumableUploadStatus(s.uploadId, { fileName: s.fileName, fileSize: s.fileSize });
            return { session: s, status: st, error: null as string | null };
          } catch (e: unknown) {
            return { session: s, status: null, error: getErrorMessage(e, '获取上传状态失败') };
          }
        }),
      );
      if (!mounted) return;

      const restored: UploadItem[] = results.map((r) => {
        const fileName = r.status?.fileName ? String(r.status.fileName) : r.session.fileName;
        const fileSize = typeof r.status?.fileSize === 'number' && Number.isFinite(r.status.fileSize) ? r.status.fileSize : r.session.fileSize;
        const uploadedBytesRaw =
          typeof r.status?.uploadedBytes === 'number' && Number.isFinite(r.status.uploadedBytes) ? r.status.uploadedBytes : r.session.loaded;
        const uploadedBytes = Math.min(Math.max(0, uploadedBytesRaw), fileSize || uploadedBytesRaw);
        const id = `resume-${r.session.uploadId}`;
        const statusUpper = String(r.status?.status || '').toUpperCase();
        const nextStatus: UploadItemStatus =
          statusUpper === 'ERROR' ? 'error' : statusUpper === 'VERIFYING' ? 'verifying' : statusUpper === 'FINALIZING' ? 'finalizing' : 'paused';

        return {
          id,
          kind: r.session.kind,
          fileName,
          fileSize,
          status: nextStatus,
          dedupeStatus: null,
          sha256: null,
          hashLoaded: 0,
          hashTotal: fileSize,
          loaded: uploadedBytes,
          total: fileSize,
          speedBps: null,
          etaSeconds: null,
          lastTickAtMs: null,
          lastLoaded: uploadedBytes,
          speedSampleAtMs: null,
          speedSampleLoaded: 0,
          serverUploadId: r.session.uploadId,
          verifyLoaded: typeof r.status?.verifyBytes === 'number' ? r.status.verifyBytes : 0,
          verifyTotal: typeof r.status?.verifyTotalBytes === 'number' ? r.status.verifyTotalBytes : fileSize,
          verifySpeedBps: null,
          verifyEtaSeconds: null,
          verifyLastTickAtMs: statusUpper === 'VERIFYING' || statusUpper === 'FINALIZING' ? now : null,
          verifyLastLoaded: typeof r.status?.verifyBytes === 'number' ? r.status.verifyBytes : 0,
          verifySpeedSampleAtMs: null,
          verifySpeedSampleLoaded: 0,
          errorMessage: r.error ?? (typeof r.status?.errorMessage === 'string' ? r.status.errorMessage : null),
        };
      });

      setUploadItems((prev) => {
        const existing = new Set(prev.map((x) => x.serverUploadId).filter(Boolean) as string[]);
        const filtered = restored.filter((x) => x.serverUploadId && !existing.has(x.serverUploadId));
        if (filtered.length === 0) return prev;
        return [...prev, ...filtered];
      });
      window.setTimeout(() => {
        if (!mounted) return;
        scheduleVerifyPollRef.current();
      }, 0);
    };

    void load();
    return () => {
      mounted = false;
    };
  }, [draftId, postId]);

  useEffect(() => {
    // When boards loaded, ensure draft.boardId is valid; otherwise default to first.
    if (!boards.length) return;
    setDraft((prev) => {
      const exists = boards.some((b) => b.id === prev.boardId);
      if (exists) return prev;
      return { ...prev, boardId: boards[0].id };
    });
  }, [boards]);

  const requireTitle = composeConfig?.requireTitle === true;
  const requireTags = composeConfig?.requireTags === true;
  const canPublish =
    draft.content.trim().length > 0 &&
    (!requireTitle || draft.title.trim().length > 0) &&
    (!requireTags || (draft.tags ?? []).length > 0);

  const extToRule = useMemo(() => {
    const out = new Map<string, UploadFormatRuleDTO>();
    const list = Array.isArray(uploadFormatsConfig?.formats) ? uploadFormatsConfig!.formats! : [];
    for (const r of list) {
      if (!r || r.enabled === false) continue;
      const exts = Array.isArray(r.extensions) ? r.extensions : [];
      for (const raw of exts) {
        const ext = String(raw || '').trim().toLowerCase();
        if (!ext) continue;
        if (!/^[a-z0-9]+$/.test(ext)) continue;
        if (ext.length > 16) continue;
        if (!out.has(ext)) out.set(ext, r);
      }
    }
    return out;
  }, [uploadFormatsConfig]);

  const uploadAccept = useMemo(() => {
    const exts = Array.from(extToRule.keys());
    if (!exts.length) return undefined;
    exts.sort((a, b) => a.localeCompare(b));
    return exts.map((x) => `.${x}`).join(',');
  }, [extToRule]);

  const extLowerOrNull = (fileName: string | undefined | null): string | null => {
    const name = String(fileName || '').trim();
    const idx = name.lastIndexOf('.');
    if (idx < 0 || idx === name.length - 1) return null;
    const ext = name.slice(idx + 1).trim().toLowerCase();
    if (!ext) return null;
    if (!/^[a-z0-9]+$/.test(ext)) return null;
    if (ext.length > 16) return null;
    return ext;
  };

  const flushUploadProgress = (updates: Map<string, { loaded: number; total: number }>) => {
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    setUploadItems((prev) =>
      prev.map((item) => {
        const p = updates.get(item.id);
        if (!p) return item;
        if (item.status !== 'uploading') return item;

        const nextTotal = Number.isFinite(p.total) && p.total > 0 ? p.total : item.total;
        const nextLoaded = Number.isFinite(p.loaded) && p.loaded >= 0 ? Math.min(p.loaded, nextTotal || p.loaded) : item.loaded;
        const nextErrorMessage =
            nextLoaded > item.loaded && item.errorMessage && item.errorMessage.startsWith('重试中（')
            ? null
            : item.errorMessage;
        const shouldVerify = Boolean(item.serverUploadId) && nextTotal > 0 && nextLoaded >= nextTotal;

        let speedBps = item.speedBps;
        let etaSeconds = item.etaSeconds;
        let speedSampleAtMs = item.speedSampleAtMs;
        let speedSampleLoaded = item.speedSampleLoaded;

        if (speedSampleAtMs == null) {
          speedSampleAtMs = now;
          speedSampleLoaded = nextLoaded;
        } else {
          const dt = (now - speedSampleAtMs) / 1000;
          const delta = nextLoaded - speedSampleLoaded;

          if (dt > 0 && delta >= 0) {
            if (dt >= 0.5 && delta > 0 && (delta >= 512 * 1024 || dt >= 2)) {
              const inst = delta / dt;
              speedBps = speedBps == null ? inst : speedBps * 0.8 + inst * 0.2;
              speedSampleAtMs = now;
              speedSampleLoaded = nextLoaded;
            }
          } else if (delta < 0) {
            speedSampleAtMs = now;
            speedSampleLoaded = nextLoaded;
          }
        }

        if (speedBps != null && speedBps > 1 && nextTotal > 0) {
          etaSeconds = Math.max(0, (nextTotal - nextLoaded) / speedBps);
        } else {
          etaSeconds = null;
        }

        return {
          ...item,
          status: shouldVerify ? 'verifying' : item.status,
          loaded: nextLoaded,
          total: nextTotal,
          speedBps: shouldVerify ? null : speedBps,
          etaSeconds: shouldVerify ? null : etaSeconds,
          lastTickAtMs: now,
          lastLoaded: nextLoaded,
          speedSampleAtMs,
          speedSampleLoaded,
          verifyLoaded: shouldVerify ? 0 : item.verifyLoaded,
          verifyTotal: shouldVerify ? (nextTotal > 0 ? nextTotal : item.fileSize) : item.verifyTotal,
          verifySpeedBps: shouldVerify ? null : item.verifySpeedBps,
          verifyEtaSeconds: shouldVerify ? null : item.verifyEtaSeconds,
          verifyLastTickAtMs: shouldVerify ? now : item.verifyLastTickAtMs,
          verifyLastLoaded: shouldVerify ? 0 : item.verifyLastLoaded,
          verifySpeedSampleAtMs: shouldVerify ? null : item.verifySpeedSampleAtMs,
          verifySpeedSampleLoaded: shouldVerify ? 0 : item.verifySpeedSampleLoaded,
          errorMessage: nextErrorMessage,
        };
      }),
    );
  };

  const queueUploadProgress = (id: string, loaded: number, total: number) => {
    uploadProgressPendingRef.current.set(id, { loaded, total });
    if (uploadProgressTimerRef.current != null) return;
    uploadProgressTimerRef.current = window.setTimeout(() => {
      uploadProgressTimerRef.current = null;
      const updates = new Map(uploadProgressPendingRef.current);
      uploadProgressPendingRef.current.clear();
      flushUploadProgress(updates);
      scheduleVerifyPoll();
    }, 250);
  };

  const flushVerifyProgress = (
    updates: Map<
      string,
      { serverStatus: string; verifyBytes: number | null; verifyTotal: number | null; errorMessage: string | null }
    >,
  ) => {
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    setUploadItems((prev) =>
      prev.map((item) => {
        const u = updates.get(item.id);
        if (!u) return item;
        if (item.status === 'done' || item.status === 'canceled') return item;

        const st = String(u.serverStatus || '').toUpperCase();
        const nextStatus =
          st === 'VERIFYING'
            ? 'verifying'
            : st === 'FINALIZING'
              ? 'finalizing'
              : st === 'DONE'
                ? 'finalizing'
                : st === 'ERROR'
                  ? 'error'
                  : item.status;

        const nextVerifyTotal = u.verifyTotal != null && u.verifyTotal > 0 ? u.verifyTotal : item.verifyTotal > 0 ? item.verifyTotal : item.fileSize;
        const nextVerifyLoadedRaw = u.verifyBytes != null && u.verifyBytes >= 0 ? u.verifyBytes : item.verifyLoaded;
        const nextVerifyLoaded = Math.min(Math.max(0, nextVerifyLoadedRaw), nextVerifyTotal || nextVerifyLoadedRaw);

        if (nextStatus !== 'verifying' && nextStatus !== 'finalizing') {
          return {
            ...item,
            status: nextStatus,
            errorMessage: u.errorMessage ?? item.errorMessage,
          };
        }

        let verifySpeedBps = item.verifySpeedBps;
        let verifyEtaSeconds = item.verifyEtaSeconds;
        let verifySpeedSampleAtMs = item.verifySpeedSampleAtMs;
        let verifySpeedSampleLoaded = item.verifySpeedSampleLoaded;

        if (verifySpeedSampleAtMs == null) {
          verifySpeedSampleAtMs = now;
          verifySpeedSampleLoaded = nextVerifyLoaded;
        } else {
          const dt = (now - verifySpeedSampleAtMs) / 1000;
          const delta = nextVerifyLoaded - verifySpeedSampleLoaded;
          if (dt > 0 && delta >= 0) {
            if (dt >= 0.5 && delta > 0 && (delta >= 512 * 1024 || dt >= 2)) {
              const inst = delta / dt;
              verifySpeedBps = verifySpeedBps == null ? inst : verifySpeedBps * 0.8 + inst * 0.2;
              verifySpeedSampleAtMs = now;
              verifySpeedSampleLoaded = nextVerifyLoaded;
            }
          } else if (delta < 0) {
            verifySpeedSampleAtMs = now;
            verifySpeedSampleLoaded = nextVerifyLoaded;
          }
        }

        if (verifySpeedBps != null && verifySpeedBps > 1 && nextVerifyTotal > 0) {
          verifyEtaSeconds = Math.max(0, (nextVerifyTotal - nextVerifyLoaded) / verifySpeedBps);
        } else {
          verifyEtaSeconds = null;
        }

        return {
          ...item,
          status: nextStatus,
          verifyLoaded: nextVerifyLoaded,
          verifyTotal: nextVerifyTotal,
          verifySpeedBps: nextStatus === 'verifying' ? verifySpeedBps : null,
          verifyEtaSeconds: nextStatus === 'verifying' ? verifyEtaSeconds : 0,
          verifyLastTickAtMs: now,
          verifyLastLoaded: nextVerifyLoaded,
          verifySpeedSampleAtMs,
          verifySpeedSampleLoaded,
          errorMessage: u.errorMessage ?? item.errorMessage,
        };
      }),
    );
  };

  const scheduleVerifyPoll = () => {
    if (verifyPollTimerRef.current != null) return;
    verifyPollTimerRef.current = window.setTimeout(async () => {
      verifyPollTimerRef.current = null;
      if (!mountedRef.current) return;

      const targets = uploadItemsRef.current
        .filter((x) => (x.status === 'verifying' || x.status === 'finalizing') && Boolean(x.serverUploadId))
        .slice(0, 20);
      if (targets.length === 0) return;

      const updates = new Map<
        string,
        { serverStatus: string; verifyBytes: number | null; verifyTotal: number | null; errorMessage: string | null }
      >();

      await Promise.all(
        targets.map(async (item) => {
          const id = item.id;
          const uploadId = item.serverUploadId;
          if (!uploadId) return;
          try {
            const s = await getResumableUploadStatus(uploadId, { fileName: item.fileName, fileSize: item.fileSize });
            updates.set(id, {
              serverStatus: String(s.status || ''),
              verifyBytes: typeof s.verifyBytes === 'number' ? s.verifyBytes : null,
              verifyTotal: typeof s.verifyTotalBytes === 'number' ? s.verifyTotalBytes : null,
              errorMessage: typeof s.errorMessage === 'string' ? s.errorMessage : null,
            });
          } catch (e: unknown) {
            updates.set(id, {
              serverStatus: '',
              verifyBytes: null,
              verifyTotal: null,
              errorMessage: getErrorMessage(e, '获取校验进度失败'),
            });
          }
        }),
      );

      if (!mountedRef.current) return;
      if (updates.size > 0) {
        flushVerifyProgress(updates);
      }

      scheduleVerifyPoll();
    }, 1000);
  };
  scheduleVerifyPollRef.current = scheduleVerifyPoll;

  useEffect(() => {
    const uploadHashAbortControllers = uploadHashAbortRef.current;
    return () => {
      if (uploadProgressTimerRef.current != null) {
        window.clearTimeout(uploadProgressTimerRef.current);
        uploadProgressTimerRef.current = null;
      }
      if (verifyPollTimerRef.current != null) {
        window.clearTimeout(verifyPollTimerRef.current);
        verifyPollTimerRef.current = null;
      }
      for (const ac of uploadHashAbortControllers.values()) {
        try {
          ac.abort();
        } catch {
        }
      }
      uploadHashAbortControllers.clear();
    };
  }, []);

  const cancelUpload = (id: string) => {
    const h = uploadHandleRef.current.get(id);
    if (h) h.cancel();
    if (!h) {
      const item = uploadItemsRef.current.find((x) => x.id === id);
      const uploadId = item?.serverUploadId;
      if (uploadId) {
        void cancelResumableUpload(uploadId).catch(() => {});
        if (draftId) {
          const kept = loadDraftUploadSessions(draftId).filter((s) => s.uploadId !== uploadId);
          saveDraftUploadSessions(draftId, kept);
        }
      }
    }
    const ac = uploadHashAbortRef.current.get(id);
    if (ac) {
      uploadHashAbortRef.current.delete(id);
      ac.abort();
    }
    setUploadItems((prev) =>
      prev.map((item) =>
        item.id === id &&
        (item.status === 'uploading' ||
          item.status === 'verifying' ||
          item.status === 'finalizing' ||
          item.status === 'paused' ||
          item.status === 'error')
          ? { ...item, status: 'canceled', dedupeStatus: null, etaSeconds: null, errorMessage: '已取消上传' }
          : item,
      ),
    );
  };

  const pauseUpload = (id: string) => {
    const h = uploadHandleRef.current.get(id);
    if (h) h.pause();
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    setUploadItems((prev) =>
      prev.map((item) =>
        item.id === id && item.status === 'uploading'
          ? { ...item, status: 'paused', etaSeconds: null, lastTickAtMs: now, speedSampleAtMs: null }
          : item,
      ),
    );
  };

  const updateHashProgressThrottled = (
    id: string,
    progress: { loaded: number; total: number },
    lastReportAtMs: number,
  ): number => {
    if (!mountedRef.current) return lastReportAtMs;
    const nowMs = typeof performance !== 'undefined' ? performance.now() : Date.now();
    if (!shouldReportThrottledProgress({ loaded: progress.loaded, total: progress.total, nowMs, lastReportAtMs })) {
      return lastReportAtMs;
    }
    setUploadItems((prev) =>
      prev.map((item) =>
        item.id === id && item.dedupeStatus === 'hashing'
          ? { ...item, hashLoaded: progress.loaded, hashTotal: progress.total }
          : item,
      ),
    );
    return nowMs;
  };

  const resumeUploadWithFile = async (id: string, file: File) => {
    const item = uploadItemsRef.current.find((x) => x.id === id);
    if (!item) throw new Error('上传任务不存在');
    const resumeUploadId = typeof item.serverUploadId === 'string' ? item.serverUploadId.trim() : '';
    const isResume = resumeUploadId.length > 0;
    if (item.fileSize > 0 && file.size > 0 && item.fileSize !== file.size) {
      throw new Error('所选文件大小与待续传任务不一致');
    }

    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const hashAbort = new AbortController();
    const prevAbort = uploadHashAbortRef.current.get(id);
    if (prevAbort) {
      uploadHashAbortRef.current.delete(id);
      prevAbort.abort();
    }
    uploadHashAbortRef.current.set(id, hashAbort);
    setUploadItems((prev) =>
      prev.map((x) =>
        x.id === id
          ? {
              ...x,
              fileName: String(file?.name || x.fileName),
              fileSize: typeof file?.size === 'number' && Number.isFinite(file.size) ? file.size : x.fileSize,
              status: 'uploading',
              dedupeStatus: 'hashing',
              sha256: null,
              hashLoaded: 0,
              hashTotal: typeof file?.size === 'number' && Number.isFinite(file.size) ? file.size : x.hashTotal,
              loaded: isResume ? x.loaded : 0,
              total: typeof file?.size === 'number' && Number.isFinite(file.size) ? file.size : x.total,
              speedBps: null,
              etaSeconds: null,
              lastTickAtMs: now,
              lastLoaded: isResume ? x.loaded : 0,
              speedSampleAtMs: now,
              speedSampleLoaded: isResume ? x.loaded : 0,
              verifyLoaded: 0,
              verifyTotal: typeof file?.size === 'number' && Number.isFinite(file.size) ? file.size : x.verifyTotal,
              verifySpeedBps: null,
              verifyEtaSeconds: null,
              verifyLastTickAtMs: null,
              verifyLastLoaded: 0,
              verifySpeedSampleAtMs: null,
              verifySpeedSampleLoaded: 0,
              errorMessage: null,
            }
          : x,
      ),
    );

    const handle = uploadFileResumable(file, {
      ...(isResume ? { resumeUploadId } : {}),
      onInit: (p) => {
        if (!mountedRef.current) return;
        setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, serverUploadId: p.uploadId } : x)));
      },
      onProgress: (p) => queueUploadProgress(id, p.loaded, p.total),
      onRetry: (p) => {
        if (!mountedRef.current) return;
        const msg = formatUploadRetryMessage(p);
        setUploadItems((prev) => prev.map((x) => (x.id === id && x.status === 'uploading' ? { ...x, errorMessage: msg } : x)));
      },
    });
    uploadHandleRef.current.set(id, handle);

    try {
      let resolveSkipHit: (r: UploadResult) => void = () => {};
      const skipHitPromise = new Promise<UploadResult>((resolve) => {
        resolveSkipHit = resolve as unknown as (r: UploadResult) => void;
      });

      const shouldCheckDedupe = (): boolean => {
        const it = uploadItemsRef.current.find((x) => x.id === id);
        if (!it) return false;
        if (it.status !== 'uploading' && it.status !== 'paused') return false;
        const total = it.total > 0 ? it.total : it.fileSize;
        if (total > 0 && it.loaded >= total) return false;
        return true;
      };

      void (async () => {
        const abortSignal = hashAbort.signal;
        if (abortSignal.aborted) return;

        let lastReportAtMs = 0;
        try {
          const sha = await computeFileSha256(file, {
            signal: abortSignal,
            onProgress: (p) => {
              lastReportAtMs = updateHashProgressThrottled(id, p, lastReportAtMs);
            },
          });

          if (!mountedRef.current) return;
          if (abortSignal.aborted) return;

          setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, sha256: sha, dedupeStatus: 'checking' } : x)));

          if (!shouldCheckDedupe()) {
            setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, dedupeStatus: 'miss' } : x)));
            return;
          }

          let hit: UploadResult | null = null;
          try {
            hit = await findUploadBySha256(sha, file.name);
          } catch {
            hit = null;
            setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, dedupeStatus: 'error' } : x)));
            return;
          }

          if (!mountedRef.current) return;
          if (abortSignal.aborted) return;
          if (!shouldCheckDedupe()) return;

          if (!hit) {
            setUploadItems((prev) => prev.map((x) => (x.id === id ? { ...x, dedupeStatus: 'miss' } : x)));
            return;
          }

          setUploadItems((prev) =>
            prev.map((x) =>
              x.id === id
                ? {
                    ...x,
                    status: 'done',
                    dedupeStatus: 'hit',
                    loaded: hit.fileSize,
                    total: hit.fileSize,
                    speedBps: null,
                    etaSeconds: 0,
                    errorMessage: '命中重复，已跳过上传',
                  }
                : x,
            ),
          );

          resolveSkipHit(hit);
          Promise.resolve().then(() => {
            try {
              handle.cancel();
            } catch {
            }
          });
        } catch {
        }
      })();

      const uploaded: UploadResult = await Promise.race([handle.promise, skipHitPromise]);
      uploadHandleRef.current.delete(id);
      const ac = uploadHashAbortRef.current.get(id);
      if (ac) {
        uploadHashAbortRef.current.delete(id);
        ac.abort();
      }
      setUploadItems((prev) =>
        prev.map((x) =>
          x.id === id
            ? {
                ...x,
                status: 'done',
                loaded: uploaded.fileSize,
                total: uploaded.fileSize,
                etaSeconds: 0,
                errorMessage: x.dedupeStatus === 'hit' ? x.errorMessage : null,
              }
            : x,
        ),
      );

      setDraft((prev) => ({
        ...prev,
        attachments: [...(prev.attachments ?? []), uploaded],
        updatedAt: new Date().toISOString(),
      }));

      if (draftId && isResume) {
        const kept = loadDraftUploadSessions(draftId).filter((s) => s.uploadId !== resumeUploadId);
        saveDraftUploadSessions(draftId, kept);
      }
    } catch (e: unknown) {
      uploadHandleRef.current.delete(id);
      const ac = uploadHashAbortRef.current.get(id);
      if (ac) {
        uploadHashAbortRef.current.delete(id);
        ac.abort();
      }
      const msg = getErrorMessage(e, '上传失败');
      const status: UploadItemStatus = msg.includes('取消') ? 'canceled' : 'error';
      setUploadItems((prev) =>
        prev.map((x) =>
          x.id === id
            ? {
                ...x,
                status,
                etaSeconds: null,
                errorMessage: msg,
              }
            : x,
        ),
      );
      throw new Error(msg);
    }
  };

  const handleResumeFileInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const id = resumeTargetUploadItemIdRef.current;
    resumeTargetUploadItemIdRef.current = null;
    const file = e.target.files && e.target.files.length > 0 ? e.target.files[0] : null;
    if (!id || !file) return;
    void resumeUploadWithFile(id, file).catch(() => {});
  };

  const resumeUpload = (id: string) => {
    const h = uploadHandleRef.current.get(id);
    if (!h) {
      resumeTargetUploadItemIdRef.current = id;
      const el = resumeFileInputRef.current;
      if (el) el.value = '';
      el?.click();
      return;
    }
    h.resume();
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    setUploadItems((prev) =>
      prev.map((item) =>
        item.id === id && item.status === 'paused'
          ? {
              ...item,
              status: 'uploading',
              speedBps: null,
              etaSeconds: null,
              lastTickAtMs: now,
              lastLoaded: item.loaded,
              speedSampleAtMs: now,
              speedSampleLoaded: item.loaded,
            }
          : item,
      ),
    );
  };

  const retryUpload = (id: string) => {
    const item = uploadItemsRef.current.find((x) => x.id === id);
    if (!item) return;
    if (item.status !== 'error') return;
    const h = uploadHandleRef.current.get(id);
    if (h) {
      try {
        h.cancel();
      } catch {
      }
      uploadHandleRef.current.delete(id);
    }
    resumeTargetUploadItemIdRef.current = id;
    const el = resumeFileInputRef.current;
    if (el) el.value = '';
    el?.click();
  };

  const clearAutoOpenTransferWindowTimer = (id: string) => {
    const t = autoOpenTransferWindowTimerRef.current.get(id);
    if (t == null) return;
    window.clearTimeout(t);
    autoOpenTransferWindowTimerRef.current.delete(id);
  };

  const uploadAndGetMarkdown = async (file: File, kind: 'image' | 'attachment') => {
    const cfg = uploadFormatsConfig;
    if (cfg) {
      if (cfg.enabled === false) {
        throw new Error('上传功能已被管理员关闭');
      }
      const ext = extLowerOrNull(file?.name);
      if (!ext) {
        throw new Error(`文件缺少扩展名: ${file?.name || 'file'}`);
      }
      if (extToRule.size === 0) {
        throw new Error('未配置允许上传的文件类型');
      }
      const rule = extToRule.get(ext);
      if (!rule) {
        throw new Error(`不支持的文件类型: ${file?.name || 'file'}`);
      }
      const globalMax =
        typeof cfg.maxFileSizeBytes === 'number' && cfg.maxFileSizeBytes > 0 ? cfg.maxFileSizeBytes : 50 * 1024 * 1024;
      const perMax = typeof rule.maxFileSizeBytes === 'number' && rule.maxFileSizeBytes > 0 ? rule.maxFileSizeBytes : globalMax;
      if (typeof file?.size === 'number' && file.size > perMax) {
        throw new Error(`文件大小超过限制: ${file?.name || 'file'}`);
      }
    }

    uploadSeqRef.current += 1;
    const id = `${Date.now()}-${uploadSeqRef.current}`;
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const fileName = String(file?.name || 'file');
    const fileSize = typeof file?.size === 'number' && Number.isFinite(file.size) ? file.size : 0;

    const hashAbort = new AbortController();
    uploadHashAbortRef.current.set(id, hashAbort);

    setUploadItems((prev) => [
      ...prev,
      {
        id,
        kind,
        fileName,
        fileSize,
        status: 'uploading',
        dedupeStatus: 'hashing',
        sha256: null,
        hashLoaded: 0,
        hashTotal: fileSize,
        loaded: 0,
        total: fileSize,
        speedBps: null,
        etaSeconds: null,
        lastTickAtMs: now,
        lastLoaded: 0,
        speedSampleAtMs: now,
        speedSampleLoaded: 0,
        serverUploadId: null,
        verifyLoaded: 0,
        verifyTotal: fileSize,
        verifySpeedBps: null,
        verifyEtaSeconds: null,
        verifyLastTickAtMs: null,
        verifyLastLoaded: 0,
        verifySpeedSampleAtMs: null,
        verifySpeedSampleLoaded: 0,
        errorMessage: null,
      },
    ]);

    const handle = uploadFileResumable(file, {
      onInit: (p) => {
        if (!mountedRef.current) return;
        setUploadItems((prev) =>
          prev.map((item) =>
            item.id === id
              ? {
                  ...item,
                  serverUploadId: p.uploadId,
                }
              : item,
          ),
        );
      },
      onProgress: (p) => queueUploadProgress(id, p.loaded, p.total),
      onRetry: (p) => {
        if (!mountedRef.current) return;
        const msg = formatUploadRetryMessage(p);
        setUploadItems((prev) =>
          prev.map((item) => (item.id === id && item.status === 'uploading' ? { ...item, errorMessage: msg } : item)),
        );
      },
    });
    uploadHandleRef.current.set(id, handle);

    clearAutoOpenTransferWindowTimer(id);
    if (!uploadTransferWindowOpenRef.current) {
      autoOpenTransferWindowTimerRef.current.set(
        id,
        window.setTimeout(() => {
          autoOpenTransferWindowTimerRef.current.delete(id);
          if (!mountedRef.current) return;
          if (uploadTransferWindowOpenRef.current) return;
          const item = uploadItemsRef.current.find((x) => x.id === id);
          if (!item) return;
          if (item.status === 'done' || item.status === 'canceled' || item.status === 'error') return;
          setUploadTransferWindowOpen(true);
        }, 2000),
      );
    }

    try {
      let resolveSkipHit: (r: UploadResult) => void = () => {};
      const skipHitPromise = new Promise<UploadResult>((resolve) => {
        resolveSkipHit = resolve as unknown as (r: UploadResult) => void;
      });

      const shouldCheckDedupe = (): boolean => {
        const item = uploadItemsRef.current.find((x) => x.id === id);
        if (!item) return false;
        if (item.status !== 'uploading' && item.status !== 'paused') return false;
        const total = item.total > 0 ? item.total : item.fileSize;
        if (total > 0 && item.loaded >= total) return false;
        return true;
      };

      void (async () => {
        const abortSignal = hashAbort.signal;
        if (abortSignal.aborted) return;

        let lastReportAtMs = 0;
        try {
          const sha = await computeFileSha256(file, {
            signal: abortSignal,
            onProgress: (p) => {
              lastReportAtMs = updateHashProgressThrottled(id, p, lastReportAtMs);
            },
          });

          if (!mountedRef.current) return;
          if (abortSignal.aborted) return;

          setUploadItems((prev) =>
            prev.map((item) => (item.id === id ? { ...item, sha256: sha, dedupeStatus: 'checking' } : item)),
          );

          if (!shouldCheckDedupe()) {
            setUploadItems((prev) => prev.map((item) => (item.id === id ? { ...item, dedupeStatus: 'miss' } : item)));
            return;
          }

          let hit: UploadResult | null = null;
          try {
            hit = await findUploadBySha256(sha, fileName);
          } catch {
            hit = null;
            setUploadItems((prev) => prev.map((item) => (item.id === id ? { ...item, dedupeStatus: 'error' } : item)));
            return;
          }

          if (!mountedRef.current) return;
          if (abortSignal.aborted) return;
          if (!shouldCheckDedupe()) return;

          if (!hit) {
            setUploadItems((prev) => prev.map((item) => (item.id === id ? { ...item, dedupeStatus: 'miss' } : item)));
            return;
          }

          setUploadItems((prev) =>
            prev.map((item) =>
              item.id === id
                ? {
                    ...item,
                    status: 'done',
                    dedupeStatus: 'hit',
                    loaded: hit.fileSize,
                    total: hit.fileSize,
                    speedBps: null,
                    etaSeconds: 0,
                    errorMessage: '命中重复，已跳过上传',
                  }
                : item,
            ),
          );

          resolveSkipHit(hit);
          Promise.resolve().then(() => {
            try {
              handle.cancel();
            } catch {
            }
          });
        } catch {
        }
      })();

      const uploaded: UploadResult = await Promise.race([handle.promise, skipHitPromise]);
      clearAutoOpenTransferWindowTimer(id);
      uploadHandleRef.current.delete(id);
      const ac = uploadHashAbortRef.current.get(id);
      if (ac) {
        uploadHashAbortRef.current.delete(id);
        ac.abort();
      }
      setUploadItems((prev) =>
        prev.map((item) =>
          item.id === id
            ? {
                ...item,
                status: 'done',
                loaded: uploaded.fileSize,
                total: uploaded.fileSize,
                etaSeconds: 0,
                errorMessage: item.dedupeStatus === 'hit' ? item.errorMessage : null,
              }
            : item,
        ),
      );

      setDraft((prev) => ({
        ...prev,
        attachments: [...(prev.attachments ?? []), uploaded],
        updatedAt: new Date().toISOString(),
      }));

      if (kind === 'image') {
        return `![](${uploaded.fileUrl})`;
      }
      return `[${escapeMarkdownLinkText(uploaded.fileName)}](${escapeMarkdownLinkDestination(uploaded.fileUrl)})`;
    } catch (e: unknown) {
      clearAutoOpenTransferWindowTimer(id);
      uploadHandleRef.current.delete(id);
      const ac = uploadHashAbortRef.current.get(id);
      if (ac) {
        uploadHashAbortRef.current.delete(id);
        ac.abort();
      }
      const msg = getErrorMessage(e, '上传失败');
      const status: UploadItemStatus = msg.includes('取消') ? 'canceled' : 'error';
      setUploadItems((prev) =>
        prev.map((item) =>
          item.id === id
            ? {
                ...item,
                status,
                etaSeconds: null,
                errorMessage: msg,
              }
            : item,
        ),
      );
      throw new Error(msg);
    }
  };

  const handleSaveDraft = async () => {
    setSaving(true);
    setError(null);
    try {
      const saved = await upsertDraft(draft);
      setDraft(saved);
      if (!draftId) {
        setSearchParams({ draftId: saved.id });
      }
      const sessions: DraftUploadSession[] = uploadItemsRef.current
        .filter((x) => x.status !== 'done' && x.status !== 'canceled' && x.status !== 'error')
        .filter((x) => Boolean(x.serverUploadId))
        .slice(0, 64)
        .map((x): DraftUploadSession => {
          const total = x.total > 0 ? x.total : x.fileSize;
          const loaded = Math.min(Math.max(0, x.loaded), total || x.loaded);
          return {
            uploadId: String(x.serverUploadId || ''),
            kind: x.kind,
            fileName: x.fileName,
            fileSize: x.fileSize,
            loaded,
            total: total || x.fileSize,
            status: 'paused',
            updatedAt: new Date().toISOString(),
          };
        })
        .filter((x) => x.uploadId.length > 0);

      if (saved.id) {
        saveDraftUploadSessions(saved.id, sessions);
        if (sessions.length > 0) {
          preserveServerUploadsOnUnmountRef.current = true;
          for (const item of uploadItemsRef.current) {
            if (item.status === 'uploading') {
              const h = uploadHandleRef.current.get(item.id);
              if (h) h.pause();
            }
          }
          setUploadItems((prev) => prev.map((x) => (x.status === 'uploading' ? { ...x, status: 'paused' } : x)));
        }
      }
      navigate('/portal/posts/drafts', { replace: false });
    } catch (e: unknown) {
      const fieldErrors = getFieldErrors(e);
      if (fieldErrors) {
        setError(Object.values(fieldErrors).join('；'));
      } else {
        setError(getErrorMessage(e, '保存草稿失败'));
      }
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!canPublish) {
      if (draft.content.trim().length === 0) {
        setError('请先填写内容后再发布。');
      } else if (requireTitle && draft.title.trim().length === 0) {
        setError('请先填写标题后再发布。');
      } else if (requireTags && (draft.tags ?? []).length === 0) {
        setError('请至少选择 1 个标签后再发布。');
      } else {
        setError('请完善发帖信息后再发布。');
      }
      return;
    }

    setPublishing(true);
    setError(null);
    try {
      let tagsToPublish = Array.isArray(draft.tags) ? draft.tags : [];
      const shouldAutoGenTags =
        requireTags && useAiTags === true && tagGenConfig?.enabled !== false && tagsToPublish.length === 0;

      const ensureTagSlugs = async (names: string[]): Promise<string[]> => {
        setLoadingTags(true);
        setTagsError(null);
        try {
          const result = await ensureTagSlugsWithAvailableTags({
            names,
            availableTags,
            limit: tagGenCount,
            createTag: (name, slug) =>
              createTag({
                tenantId: 1,
                type: 'TOPIC',
                name,
                slug,
                system: false,
                active: true,
              }),
            onCreateError: (e) => setTagsError(getErrorMessage(e, '新增标签失败')),
          });
          setAvailableTags(result.availableTags);
          return result.slugs;
        } finally {
          setLoadingTags(false);
        }
      };

      const fetchSuggestedTags = async (): Promise<string[]> => {
        const content = (draft.content ?? '').trim();
        if (content.length < 10) return [];

        const boardName = boards.find((b) => b.id === draft.boardId)?.name;
        const current = (tagsToPublish ?? [])
          .map((s) => availableTags.find((t) => t.slug === s)?.name ?? String(s))
          .filter((s) => s && s.trim());

        const resp = await suggestPostTags({
          title: draft.title?.trim() || undefined,
          content,
          count: tagGenCount,
          boardName,
          tags: current,
        });
        const out = Array.isArray(resp.tags) ? resp.tags : [];
        return out.map((x) => String(x || '').trim()).filter((x) => x.length > 0).slice(0, tagGenCount);
      };

      if (shouldAutoGenTags) {
        const fromCandidates = tagCandidates.map((x) => String(x || '').trim()).filter((x) => x.length > 0);
        const suggested = fromCandidates.length ? fromCandidates.slice(0, tagGenCount) : await fetchSuggestedTags();
        if (!suggested.length) {
          setError('未选择标签，且未能自动生成标签。请手动选择标签，或写多一点内容后重试。');
          return;
        }

        const slugs = await ensureTagSlugs(suggested);
        if (!slugs.length) {
          setError('未选择标签，且未能自动创建/选择生成的标签。请稍后重试或手动选择。');
          return;
        }

        tagsToPublish = Array.from(new Set([...tagsToPublish, ...slugs]));
        setDraft((p) => ({ ...p, tags: tagsToPublish, updatedAt: new Date().toISOString() }));
        setTagCandidates(suggested);
        setTagSuggestError(null);
      }

      const languagesToPublish = await suggestLanguagesToPublish({
        enabled: langLabelGenConfig?.enabled !== false,
        title: draft.title,
        content: draft.content,
        suggest: suggestPostLangLabels,
      });

      if (postId !== null) {

        await updatePost(postId, {
          boardId: draft.boardId,
          title: draft.title,
          content: draft.content,
          contentFormat: 'MARKDOWN',
          tags: tagsToPublish,
          metadata: {
            ...(basePostMetadata ?? {}),
            ...(languagesToPublish.length ? { languages: languagesToPublish } : {}),
          },
          attachmentIds: draft.attachments?.map((a) => a.id),
        });

        navigate('/portal/posts/mine');
        return;
      }

      await createPost({
        boardId: draft.boardId,
        title: draft.title,
        content: draft.content,
        contentFormat: 'MARKDOWN',
        tags: tagsToPublish,
        metadata: languagesToPublish.length ? { languages: languagesToPublish } : undefined,
        attachmentIds: draft.attachments?.map((a) => a.id),
      });

      if (draftId) {
        const sessions = loadDraftUploadSessions(draftId);
        saveDraftUploadSessions(draftId, []);
        await Promise.all(sessions.map((s) => cancelResumableUpload(s.uploadId)));
        await deleteDraft(draftId);
      }

        // 新帖默认进入“待审核”，不会出现在首页/热榜等公共区域；引导用户去“我的帖子”查看。
      navigate('/portal/posts/mine');
    } catch (e: unknown) {
      const fieldErrors = getFieldErrors(e);
      if (fieldErrors) {
        setError(Object.values(fieldErrors).join('；'));
      } else {
        setError(getErrorMessage(e, postId !== null ? '保存修改失败' : '发布失败'));
      }
    } finally {
      setPublishing(false);
    }
  };

  const busy = loadingDraft || loadingPost;

  const filteredTagOptions = useMemo(() => {
    const k = tagQuery.trim().toLowerCase();
    const selected = new Set((draft.tags ?? []).map(String));
    const base = availableTags.filter((t) => !selected.has(t.slug));
    if (!k) return base.slice(0, 30);
    return base
      .filter((t) => t.name.toLowerCase().includes(k) || t.slug.toLowerCase().includes(k))
      .slice(0, 30);
  }, [availableTags, draft.tags, tagQuery]);

  const addTagSlug = (slugValue: string) => {
    const s = String(slugValue || '').trim();
    if (!s) return;
    setDraft((p) => {
      const prev = Array.isArray(p.tags) ? p.tags : [];
      if (prev.includes(s)) return p;
      return { ...p, tags: [...prev, s], updatedAt: new Date().toISOString() };
    });
  };

  const removeTagSlug = (slugValue: string) => {
    const s = String(slugValue || '').trim();
    if (!s) return;
    setDraft((p) => {
      const prev = Array.isArray(p.tags) ? p.tags : [];
      return { ...p, tags: prev.filter((x) => x !== s), updatedAt: new Date().toISOString() };
    });
  };

  const ensureTagSlugs = async (names: string[]): Promise<string[]> => {
    setLoadingTags(true);
    setTagsError(null);
    try {
      const result = await ensureTagSlugsWithAvailableTags({
        names,
        availableTags,
        createTag: (name, slug) =>
          createTag({
            tenantId: 1,
            type: 'TOPIC',
            name,
            slug,
            system: false,
            active: true,
          }),
        onCreateError: (e) => setTagsError(getErrorMessage(e, '新增标签失败')),
      });
      setAvailableTags(result.availableTags);
      return result.slugs;
    } finally {
      setLoadingTags(false);
    }
  };

  const ensureTagExistsAndAdd = async (name: string): Promise<string | null> => {
    const slugs = await ensureTagSlugs([name]);
    const slugValue = slugs[0];
    if (!slugValue) return null;
    addTagSlug(slugValue);
    return slugValue;
  };

  async function handleSuggestTags(silent?: boolean) {
    if (tagSuggesting) return;

    const content = (draft.content ?? '').trim();
    if (content.length < 10) {
      if (!silent) setTagSuggestError('内容太短了，先写一点内容再生成标签。');
      return;
    }

    setTagSuggesting(true);
    if (!silent) setTagSuggestError(null);

    try {
      const boardName = boards.find((b) => b.id === draft.boardId)?.name;
      const current = (draft.tags ?? [])
        .map((s) => availableTags.find((t) => t.slug === s)?.name ?? String(s))
        .filter((s) => s && s.trim());

      const resp = await suggestPostTags({
        title: draft.title?.trim() || undefined,
        content,
        count: tagGenCount,
        boardName,
        tags: current,
      });
      const out = (Array.isArray(resp.tags) ? resp.tags : [])
        .map((x) => String(x || '').trim())
        .filter((x) => x.length > 0)
        .slice(0, Math.max(1, tagGenCount));
      setTagCandidates(out);
      if (out.length === 0) {
        if (!silent) setTagSuggestError('没有生成到可用标签，请稍后重试。');
        return;
      }

      const slugs = await ensureTagSlugs(out);
      if (!slugs.length) {
        if (!silent) setTagSuggestError('未能创建/选择生成的标签，请稍后重试。');
        return;
      }

      setDraft((p) => {
        const prev = Array.isArray(p.tags) ? p.tags : [];
        const next = Array.from(new Set([...prev, ...slugs]));
        return { ...p, tags: next, updatedAt: new Date().toISOString() };
      });
    } catch (e: unknown) {
      if (!silent) setTagSuggestError(getErrorMessage(e, '生成标签失败'));
    } finally {
      setTagSuggesting(false);
    }
  }

  useEffect(() => {
    // Push live preview snapshot to right sidebar (PostsLayout) via localStorage.
    // Using both localStorage and a custom event so same-tab updates are instant.
    const key = 'portal.posts.compose.preview';
    try {
      localStorage.setItem(
        key,
        JSON.stringify({
          markdown: draft.content ?? '',
          updatedAt: new Date().toISOString(),
        })
      );
      window.dispatchEvent(new Event('posts-compose-preview-update'));
    } catch {
      // Ignore quota/security errors; editor should still work.
    }
  }, [draft.content]);

  useEffect(() => {
      // 切换草稿/帖子时，清理标题候选，避免跨帖子串数据。
    setTitleCandidates([]);
    setTitleDropdownOpen(false);
    setTitleSuggestError(null);
    setTitleSuggesting(false);
    setTagCandidates([]);
    setTagSuggestError(null);
    setTagSuggesting(false);
  }, [draftId, postId]);

  async function handleSuggestTitles() {
    if (titleSuggesting) return;

    const content = (draft.content ?? '').trim();
    if (content.length < 10) {
      setTitleSuggestError('内容太短了，先写一点内容再生成标题。');
      return;
    }

    setTitleSuggesting(true);
    setTitleSuggestError(null);
    setTitleDropdownOpen(false);

    try {
      const boardName = boards.find((b) => b.id === draft.boardId)?.name;
      const resp = await suggestPostTitles({
        content,
        count: titleGenCount,
        boardName,
        tags: draft.tags,
      });
      const titles = Array.isArray(resp.titles) ? resp.titles : [];
      setTitleCandidates(titles);
      setTitleDropdownOpen(titles.length > 0);
      if (!titles.length) {
        setTitleSuggestError('没有生成到可用标题，请稍后重试。');
      }
    } catch (e: unknown) {
      setTitleSuggestError(getErrorMessage(e, '生成标题失败'));
      setTitleDropdownOpen(false);
    } finally {
      setTitleSuggesting(false);
    }
  }

  return (
    <div className="space-y-4">
      <input ref={resumeFileInputRef} type="file" className="hidden" accept={uploadAccept} onChange={handleResumeFileInputChange} />
      {/* Left: existing page content */}
        <div>
          <h3 className="text-lg font-semibold">{postId !== null ? '编辑帖子' : '发帖'}</h3>
          <p className="text-gray-600">
            使用 Markdown 编写内容。你可以插入图片与附件
            {postId !== null ? '，保存修改后会回到“我的帖子”。' : '，保存到草稿箱后还可以继续编辑。'}
          </p>
        </div>

        {error && (
          <div className="p-3 rounded-md border border-red-200 bg-red-50 text-red-700 text-sm">{error}</div>
        )}
        {langLabelGenConfigError && (
          <div className="p-3 rounded-md border border-amber-200 bg-amber-50 text-amber-900 text-sm">
            语言标签配置加载失败：{langLabelGenConfigError}
          </div>
        )}
        {composeConfigError && (
          <div className="p-3 rounded-md border border-amber-200 bg-amber-50 text-amber-900 text-sm">
            发帖配置加载失败：{composeConfigError}
          </div>
        )}
        {uploadFormatsConfigError && (
          <div className="p-3 rounded-md border border-amber-200 bg-amber-50 text-amber-900 text-sm">
            上传格式配置加载失败：{uploadFormatsConfigError}
          </div>
        )}

        {busy ? (
          <div className="text-sm text-gray-600">正在加载{postId !== null ? '帖子' : '草稿'}...</div>
        ) : (
          <div className="space-y-3">
              <div className={composeLocked ? 'opacity-70' : ''}>
              <form className="space-y-3" onSubmit={(e) => e.preventDefault()}>
                  <PostsBasicSection
                      composeLocked={composeLocked}
                      draft={draft}
                      setDraft={setDraft}
                      titleCandidates={titleCandidates}
                      titleDropdownOpen={titleDropdownOpen}
                      setTitleDropdownOpen={setTitleDropdownOpen}
                      titleSuggesting={titleSuggesting}
                      useAiTitle={useAiTitle}
                      titleGenConfig={titleGenConfig}
                      titleGenConfigError={titleGenConfigError}
                      titleSuggestError={titleSuggestError}
                      onSuggestTitles={() => void handleSuggestTitles()}
                      loadingBoards={loadingBoards}
                      boards={boards}
                      loadingTags={loadingTags}
                      tagsError={tagsError}
                      availableTags={availableTags}
                      onRemoveTagSlug={removeTagSlug}
                      tagQuery={tagQuery}
                      setTagQuery={setTagQuery}
                      onEnsureTagExistsAndAdd={ensureTagExistsAndAdd}
                      useAiTags={useAiTags}
                      tagGenConfig={tagGenConfig}
                      tagSuggesting={tagSuggesting}
                      onSuggestTags={() => void handleSuggestTags(false)}
                      filteredTagOptions={filteredTagOptions}
                      onAddTagSlug={addTagSlug}
                      tagGenConfigError={tagGenConfigError}
                      tagSuggestError={tagSuggestError}
                  />
                <div ref={contentEditorWrapRef}>
                  <MarkdownEditor
                    value={{ markdown: draft.content }}
                    onChange={(v) => setDraft((p) => ({ ...p, content: v.markdown }))}
                    onInsertImage={(file) => uploadAndGetMarkdown(file, 'image')}
                    onInsertAttachment={(file) => uploadAndGetMarkdown(file, 'attachment')}
                    fileAccept={uploadAccept}
                    placeholder="写点什么..."
                    editorHeightPx={contentEditorHeightPx ?? undefined}
                    readOnly={composeLocked}
                    toolbarAfterTabs={
                      <div className="flex items-center gap-3">
                        <label className="inline-flex items-center gap-2 text-sm text-gray-700 select-none">
                          <input
                            type="checkbox"
                            className="h-4 w-4"
                            checked={composePreviewOpen}
                            onChange={(e) => setComposePreviewOpen(e.target.checked)}
                          />
                          实时预览
                        </label>

                        <button
                          type="button"
                          className="px-2 py-1 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50"
                          onClick={() => setUploadTransferWindowOpen(true)}
                        >
                          传输列表
                          {uploadItems.some(
                            (x) =>
                              x.status === 'uploading' || x.status === 'verifying' || x.status === 'finalizing' || x.status === 'paused',
                          )
                            ? ` (${uploadItems.filter(
                                (x) =>
                                  x.status === 'uploading' ||
                                  x.status === 'verifying' ||
                                  x.status === 'finalizing' ||
                                  x.status === 'paused',
                              ).length})`
                            : ''}
                        </button>
                        <button
                          type="button"
                          className="px-2 py-1 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50"
                          onClick={() => setComposeAssistantWindowOpen(true)}
                        >
                          发帖助手
                        </button>
                      </div>
                    }
                  />
                </div>

                <div className="flex gap-2">
                  <button
                    type="button"
                    disabled={composeLocked || publishing || saving}
                    onClick={handlePublish}
                    className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {publishing ? (postId !== null ? '保存中...' : '发布中...') : postId !== null ? '保存修改' : '发布'}
                  </button>

                  {postId === null && (
                    <button
                      type="button"
                      disabled={composeLocked || publishing || saving}
                      onClick={handleSaveDraft}
                      className="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                    >
                      {saving ? '保存中...' : isEditingDraft ? '更新草稿' : '保存草稿'}
                    </button>
                  )}

                  {draftId && postId === null && (
                    <button
                      type="button"
                      disabled={composeLocked || publishing || saving}
                      onClick={async () => {
                        const sessions = loadDraftUploadSessions(draftId);
                        saveDraftUploadSessions(draftId, []);
                        await Promise.all(sessions.map((s) => cancelResumableUpload(s.uploadId)));
                        await deleteDraft(draftId);
                        setSearchParams({});
                        setDraft(createEmptyDraft());
                        navigate('/portal/posts/drafts');
                      }}
                      className="px-4 py-2 rounded-md border border-red-300 text-red-700 hover:bg-red-50 disabled:opacity-50"
                    >
                      删除草稿
                    </button>
                  )}

                  {postId !== null && (
                    <button
                      type="button"
                      disabled={composeLocked || publishing || saving}
                      onClick={() => navigate('/portal/posts/mine')}
                      className="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-opacity-50"
                    >
                      取消
                    </button>
                  )}
                </div>

                <div className="text-sm text-gray-500">
                  信息：{draft.title ? `《${draft.title}》` : '（无标题）'} /{' '}
                  {draft.content ? `${draft.content.length} 字` : '（无内容）'}
                  {draft.attachments?.length ? ` / ${draft.attachments.length} 个附件` : ''}
                </div>
              </form>
            </div>
          </div>
        )}
      {composeAssistantWindowOpen ? (
        <PostComposeAssistantWindow
          draft={draft}
          setDraft={(fn) => setDraft(fn)}
          draftId={draftId}
          postId={postId}
          busy={busy}
          setComposeLocked={setComposeLocked}
          setSearchParams={(next) => setSearchParams(next)}
          onClose={() => setComposeAssistantWindowOpen(false)}
        />
      ) : null}
      {uploadTransferWindowOpen ? (
        <PostUploadTransferWindow
          uploadItems={
            uploadItems.map(
              (u): TransferUploadItem => ({
                id: u.id,
                kind: u.kind,
                fileName: u.fileName,
                fileSize: u.fileSize,
                status: u.status,
                dedupeStatus: u.dedupeStatus,
                hashLoaded: u.hashLoaded,
                hashTotal: u.hashTotal,
                loaded: u.loaded,
                total: u.total,
                speedBps: u.speedBps,
                etaSeconds: u.etaSeconds,
                verifyLoaded: u.verifyLoaded,
                verifyTotal: u.verifyTotal,
                verifySpeedBps: u.verifySpeedBps,
                verifyEtaSeconds: u.verifyEtaSeconds,
                errorMessage: u.errorMessage,
              }),
            )
          }
          onPause={pauseUpload}
          onResume={resumeUpload}
          onRetry={retryUpload}
          onCancel={cancelUpload}
          onClose={() => setUploadTransferWindowOpen(false)}
        />
      ) : null}
    </div>
  );
}

