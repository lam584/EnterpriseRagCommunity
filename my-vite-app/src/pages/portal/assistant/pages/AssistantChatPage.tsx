import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import { splitThinkText, stripThinkBlocks } from '../../../../utils/thinkTags';
import { RefreshCw, Pencil, Trash2, Bot, Send, Square, Copy, Check, Languages, X, ChevronDown, Heart } from 'lucide-react';

import { chatOnce, chatStream, regenerateOnce, regenerateStream, type AiCitationSource, type AiStreamEvent } from '../../../../services/aiChatService';
import { getAiChatOptions, type AiChatOptionsDTO, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { getMyAssistantPreferences, updateMyAssistantPreferences } from '../../../../services/assistantPreferencesService';
import { uploadFile, type UploadResult } from '../../../../services/uploadService';
import { useAuth } from '../../../../contexts/AuthContext';
import { getMyProfile } from '../../../../services/accountService';
import { Avatar, AvatarFallback, AvatarImage } from '../../../../components/ui/avatar';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import {
  deleteQaMessage,
  getQaSessionMessages,
  listQaSessions,
  type QaMessageDTO,
  updateQaMessage,
  toggleQaMessageFavorite
} from '../../../../services/qaHistoryService';

const MAX_VISION_IMAGES = 10;

type ChatMsg = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt?: string;
  isFavorite?: boolean;
  model?: string | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  latencyMs?: number | null;
  firstTokenLatencyMs?: number | null;
};

type MsgPerf = {
  startAtMs: number;
  firstDeltaAtMs?: number;
  doneAtMs?: number;
  backendLatencyMs?: number;
};

type ThinkPerf = {
  startedAtMs?: number;
  endedAtMs?: number;
};

type ThinkUi = {
  collapsed: boolean;
};

function uid(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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

function isPersistedId(id: string): boolean {
  return /^\d+$/.test(id);
}

function formatDateTime(iso?: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return null;
  return d.toLocaleString();
}

function formatMsgTokensInfo(msg: Pick<ChatMsg, 'role' | 'tokensIn' | 'tokensOut'>): string {
  if (msg.role === 'user') {
    const inStr = typeof msg.tokensIn === 'number' ? String(msg.tokensIn) : '-';
    return `tokens in ${inStr}`;
  }
  const outStr = typeof msg.tokensOut === 'number' ? String(msg.tokensOut) : '-';
  return `tokens out ${outStr}`;
}

function colorClassForCitationIndex(index: number): string {
  const palette = [
    'text-blue-600',
    'text-emerald-600',
    'text-purple-600',
    'text-amber-600',
    'text-rose-600',
    'text-cyan-600'
  ];
  const i = Number.isFinite(index) ? Math.abs(index) : 0;
  return palette[i % palette.length] ?? 'text-blue-600';
}

function linkifyCitations(md: string): string {
  if (!md) return md;
  const parts: string[] = [];
  const re = /```[\s\S]*?```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(md)) !== null) {
    const before = md.slice(lastIndex, match.index);
    parts.push(before.replace(/\[(\d{1,3})\](?!\()/g, (_m, n) => `[[${n}]](#cite-${n})`));
    parts.push(match[0]);
    lastIndex = match.index + match[0].length;
  }
  const tail = md.slice(lastIndex);
  parts.push(tail.replace(/\[(\d{1,3})\](?!\()/g, (_m, n) => `[[${n}]](#cite-${n})`));
  return parts.join('');
}

function extractCitationIndexes(md: string): Set<number> {
  const out = new Set<number>();
  if (!md) return out;
  const reCode = /```[\s\S]*?```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  const extractFromText = (txt: string) => {
    for (const m of txt.matchAll(/\[(\d{1,3})\](?!\()/g)) {
      const n = Number(m[1]);
      if (Number.isFinite(n) && n > 0) out.add(n);
    }
  };
  while ((match = reCode.exec(md)) !== null) {
    extractFromText(md.slice(lastIndex, match.index));
    lastIndex = match.index + match[0].length;
  }
  extractFromText(md.slice(lastIndex));
  return out;
}

function isThinkingOnlyModel(model: string): boolean {
  const m = String(model ?? '').trim().toLowerCase();
  if (!m) return false;
  return m.includes("-thinking") || m.includes("thinking-") || m.endsWith("thinking");
}

function formatDurationMs(ms: number): string {
  const safe = Number.isFinite(ms) ? Math.max(0, ms) : 0;
  const totalSeconds = safe / 1000;
  if (totalSeconds < 10) return `${totalSeconds.toFixed(1)}s`;
  if (totalSeconds < 60) return `${Math.round(totalSeconds)}s`;
  const mins = Math.floor(totalSeconds / 60);
  const secs = Math.round(totalSeconds - mins * 60);
  return `${mins}m${secs}s`;
}

export default function AssistantChatPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, isAuthenticated } = useAuth();

  // 防止流式回调重复 replace 同一个 URL，触发 Chromium 的 navigation throttling
  const lastSyncedSessionIdRef = useRef<number | undefined>(undefined);

  // Avoid capturing stale location/searchParams inside stream callbacks.
  const latestLocationRef = useRef(location);
  useEffect(() => {
    latestLocationRef.current = location;
  }, [location]);

  // Coalesce multiple meta events into at most 1 navigation.
  const pendingUrlSyncRef = useRef<number | null>(null);
  const urlSyncScheduledRef = useRef(false);

  const initialSessionId = useMemo(() => {
    const raw = searchParams.get('sessionId');
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? n : undefined;
  }, [searchParams]);

  const [sessionId, setSessionId] = useState<number | undefined>(undefined);
  const [question, setQuestion] = useState('');
  const [pendingImages, setPendingImages] = useState<UploadResult[]>([]);
  const [imageUploading, setImageUploading] = useState(false);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [optionsError, setOptionsError] = useState<string | null>(null);
  const [chatOptions, setChatOptions] = useState<AiChatOptionsDTO | null>(null);
  const [selectedProviderId, setSelectedProviderId] = useState<string>('');
  const [selectedModel, setSelectedModel] = useState<string>('');
  const [deepThink, setDeepThink] = useState(false);
  const [useRag, setUseRag] = useState(true);
  const [ragTopK, setRagTopK] = useState(6);
  const [autoLoadLastSession, setAutoLoadLastSession] = useState(false);
  const [streamOutput, setStreamOutput] = useState(true);
  const [sourcesByMsgId, setSourcesByMsgId] = useState<Record<string, AiCitationSource[]>>({});
  const [editing, setEditing] = useState<{ id: string; draft: string } | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [perfByMsgId, setPerfByMsgId] = useState<Record<string, MsgPerf>>({});
  const [deepThinkByMsgId, setDeepThinkByMsgId] = useState<Record<string, boolean>>({});
  const [thinkPerfByMsgId, setThinkPerfByMsgId] = useState<Record<string, ThinkPerf>>({});
  const [thinkUiByMsgId, setThinkUiByMsgId] = useState<Record<string, ThinkUi>>({});
  const [streamingAssistantId, setStreamingAssistantId] = useState<string | null>(null);
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);
  const [profileAvatarUrl, setProfileAvatarUrl] = useState<string | undefined>(undefined);
  const [inputHeight, setInputHeight] = useState<number>(120);
  const [isResizing, setIsResizing] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleResizeMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
  }, []);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizing) return;
      // 向上拖动（movementY 为负）增加高度
      setInputHeight((prev) => {
        const next = prev - e.movementY;
        return Math.max(80, Math.min(600, next));
      });
    };

    const handleMouseUp = () => {
      setIsResizing(false);
    };

    if (isResizing) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'row-resize';
      document.body.style.userSelect = 'none';
    } else {
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isResizing]);

  const displayUsername = useMemo(() => {
    const name = currentUser?.username?.trim();
    return name && name.length > 0 ? name : '未登录';
  }, [currentUser?.username]);

  const avatarFallbackText = useMemo(() => {
    const name = currentUser?.username?.trim();
    if (!name) return 'U';
    return name.slice(0, 1).toUpperCase();
  }, [currentUser?.username]);

  const abortRef = useRef<AbortController | null>(null);
  const editingTextareaRef = useRef<HTMLTextAreaElement | null>(null);
  const autoLoadAttemptedRef = useRef(false);
  const streamRawByMsgIdRef = useRef<Record<string, string>>({});
  const chatScrollRef = useRef<HTMLDivElement | null>(null);
  const chatBottomRef = useRef<HTMLDivElement | null>(null);
  const scrollRafRef = useRef<number | null>(null);
  const autoScrollEnabledRef = useRef(true);
  const autoScrollingRef = useRef(false);
  const streamAutoScrollDisabledRef = useRef(false);

  const scheduleScrollToBottom = (behavior: ScrollBehavior = 'auto') => {
    if (scrollRafRef.current != null) {
      cancelAnimationFrame(scrollRafRef.current);
    }
    scrollRafRef.current = requestAnimationFrame(() => {
      scrollRafRef.current = null;
      autoScrollingRef.current = true;
      requestAnimationFrame(() => {
        autoScrollingRef.current = false;
      });
      const anchor = chatBottomRef.current;
      if (anchor) {
        anchor.scrollIntoView({ block: 'end', behavior });
        return;
      }
      const el = chatScrollRef.current;
      if (!el) return;
      el.scrollTop = el.scrollHeight;
    });
  };

  const syncAutoScrollEnabled = () => {
    const el = chatScrollRef.current;
    if (!el) {
      autoScrollEnabledRef.current = true;
      setShowScrollToBottom(false);
      return;
    }
    const thresholdPx = 120;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const isAtBottom = distanceToBottom <= thresholdPx;
    autoScrollEnabledRef.current = isAtBottom;
    setShowScrollToBottom((prev) => (prev === !isAtBottom ? prev : !isAtBottom));
  };

  const handleChatScroll = () => {
    syncAutoScrollEnabled();
    if (!isStreaming) return;
    if (autoScrollingRef.current) return;
    if (streamAutoScrollDisabledRef.current) return;
    if (!autoScrollEnabledRef.current) {
      streamAutoScrollDisabledRef.current = true;
    }
  };

  const handleScrollToBottomClick = () => {
    streamAutoScrollDisabledRef.current = false;
    autoScrollEnabledRef.current = true;
    setShowScrollToBottom(false);
    scheduleScrollToBottom('smooth');
  };

  const thinkingOnly = useMemo(() => isThinkingOnlyModel(selectedModel), [selectedModel]);
  const effectiveDeepThink = thinkingOnly ? true : deepThink;

  const canSend = useMemo(() => !isStreaming && (question.trim().length > 0 || pendingImages.length > 0), [isStreaming, pendingImages, question]);

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

  const providerOptions = useMemo(() => {
    const providers = (chatOptions?.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
    const enabled = providers.filter((p) => {
      const id = String(p.id ?? '').trim();
      return Boolean(id);
    });
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
          value: buildProviderModelValue(providerId, modelName)
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
    [selectedProviderId, selectedModel]
  );

  const tokensTotals = useMemo(() => {
    let tokensInSum = 0;
    let tokensOutSum = 0;
    let hasIn = false;
    let hasOut = false;
    for (const m of messages) {
      if (typeof m.tokensIn === 'number') {
        tokensInSum += m.tokensIn;
        hasIn = true;
      }
      if (typeof m.tokensOut === 'number') {
        tokensOutSum += m.tokensOut;
        hasOut = true;
      }
    }
    const hasAny = hasIn || hasOut;
    return {
      hasAny,
      inStr: hasIn ? String(tokensInSum) : '-',
      outStr: hasOut ? String(tokensOutSum) : '-',
      totalStr: hasAny ? String(tokensInSum + tokensOutSum) : '-'
    };
  }, [messages]);

  const handleCopy = async (id: string, content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const handleToggleFavorite = async (id: string) => {
    if (!isPersistedId(id)) return;
    try {
      const isFav = await toggleQaMessageFavorite(Number(id));
      setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, isFavorite: isFav } : m)));
    } catch (err) {
      console.error('Failed to toggle favorite:', err);
    }
  };

  const highlightMessageId = useMemo(() => searchParams.get('highlightMessageId'), [searchParams]);

  useEffect(() => {
    if (highlightMessageId && messages.length > 0) {
      const timer = setTimeout(() => {
        const el = document.getElementById(`msg-${highlightMessageId}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          el.classList.add('ring-2', 'ring-blue-400', 'ring-offset-2', 'rounded-lg', 'transition-all', 'duration-1000');
          setTimeout(() => {
            el.classList.remove('ring-2', 'ring-blue-400', 'ring-offset-2');
          }, 3000);
        }
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [highlightMessageId, messages]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadProfileAvatar() {
      if (!isAuthenticated) {
        setProfileAvatarUrl(undefined);
        return;
      }

      try {
        const p = await getMyProfile();
        if (!cancelled) setProfileAvatarUrl(p.avatarUrl);
      } catch {
        if (!cancelled) setProfileAvatarUrl(undefined);
      }
    }

    loadProfileAvatar();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  useEffect(() => {
    return () => {
      if (scrollRafRef.current != null) {
        cancelAnimationFrame(scrollRafRef.current);
        scrollRafRef.current = null;
      }
    };
  }, []);

  useLayoutEffect(() => {
    if (isStreaming) {
      if (!streamAutoScrollDisabledRef.current) {
        scheduleScrollToBottom('auto');
      }
      syncAutoScrollEnabled();
      return;
    }
    if (autoScrollEnabledRef.current) {
      scheduleScrollToBottom('auto');
    }
    syncAutoScrollEnabled();
  }, [messages, isStreaming]);

  useEffect(() => {
    if (!isStreaming) {
      streamAutoScrollDisabledRef.current = false;
    }
  }, [isStreaming]);

  useEffect(() => {
    let cancelled = false;
    setOptionsError(null);
    void (async () => {
      try {
        const [opt, prefs] = await Promise.all([getAiChatOptions(), getMyAssistantPreferences().catch(() => null)]);
        if (cancelled) return;
        setChatOptions(opt);
        const providers = (opt.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
        const providerIds = new Set(providers.map((p) => String(p.id ?? '').trim()).filter(Boolean));

        const norm = (v: unknown) => (typeof v === 'string' ? v.trim() : '');
        const storedProvider = prefs ? norm(prefs.defaultProviderId) : '';
        const storedModel = prefs ? norm(prefs.defaultModel) : '';

        if (prefs) {
          setDeepThink(!!prefs.defaultDeepThink);
          setUseRag(!!prefs.defaultUseRag);
          setRagTopK(Number.isFinite(prefs.ragTopK) ? Math.max(1, Math.min(50, Number(prefs.ragTopK))) : 6);
          setAutoLoadLastSession(!!prefs.autoLoadLastSession);
          setStreamOutput(typeof prefs.stream === 'boolean' ? prefs.stream : true);
        }

        if (prefs && !storedProvider && !storedModel) {
          setSelectedProviderId('');
          setSelectedModel('');
          return;
        }

        let nextProviderId = '';
        if (storedProvider && providerIds.has(storedProvider)) {
          nextProviderId = storedProvider;
        } else {
          const active = norm(opt.activeProviderId);
          if (active && providerIds.has(active)) {
            nextProviderId = active;
          } else {
            nextProviderId = norm(providers[0]?.id);
          }
        }

        const p = providers.find((x) => norm(x.id) === nextProviderId) ?? null;
        const models = Array.isArray(p?.chatModels) ? p!.chatModels!.filter(Boolean) : [];
        const modelNames = new Set(models.map((m) => norm((m as { name?: unknown }).name)).filter(Boolean));
        const nextModel = (() => {
          if (storedModel && modelNames.has(storedModel)) return storedModel;
          const directDefault = norm(p?.defaultChatModel);
          if (directDefault && modelNames.has(directDefault)) return directDefault;
          const flagged = models.find((m) => Boolean((m as { isDefault?: unknown }).isDefault));
          const flaggedName = norm((flagged as { name?: unknown })?.name);
          if (flaggedName && modelNames.has(flaggedName)) return flaggedName;
          return norm((models[0] as { name?: unknown })?.name);
        })();

        setSelectedProviderId(nextProviderId);
        setSelectedModel(nextModel);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!cancelled) setOptionsError(msg || '获取模型选项失败');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (initialSessionId !== undefined) return;
    if (!autoLoadLastSession) return;
    if (autoLoadAttemptedRef.current) return;
    autoLoadAttemptedRef.current = true;
    void (async () => {
      try {
        const page = await listQaSessions(0, 1);
        const first = (page?.content ?? [])[0];
        if (!first || !Number.isFinite(first.id)) return;
        const loc = latestLocationRef.current;
        if (loc.pathname !== '/portal/assistant/chat') return;
        navigate(`/portal/assistant/chat?sessionId=${first.id}`, { replace: true });
      } catch {
      }
    })();
  }, [initialSessionId, autoLoadLastSession, navigate]);

  useEffect(() => {
    const el = editingTextareaRef.current;
    if (!editing || !el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [editing?.id, editing?.draft]);

  useEffect(() => {
    // when url sessionId changes, load that session
    if (initialSessionId === undefined) return;
    if (isStreaming) return;

    let cancelled = false;
    setError(null);
    setIsStreaming(false);
    setSessionId(undefined);
    setMessages([]);
    setSourcesByMsgId({});
    setDeepThinkByMsgId({});
    setThinkPerfByMsgId({});
    setThinkUiByMsgId({});
    setStreamingAssistantId(null);

    void (async () => {
      try {
        const list = await getQaSessionMessages(initialSessionId);
        if (cancelled) return;
        const mapped: ChatMsg[] = list
          .filter((m) => m.role === 'USER' || m.role === 'ASSISTANT')
          .map((m: QaMessageDTO) => ({
            id: String(m.id),
            role: m.role === 'USER' ? 'user' : 'assistant',
            content: m.content,
            createdAt: m.createdAt,
            model: m.model ?? null,
            tokensIn: m.tokensIn ?? null,
            tokensOut: m.tokensOut ?? null,
            latencyMs: m.latencyMs ?? null,
            isFavorite: m.isFavorite,
            firstTokenLatencyMs: m.firstTokenLatencyMs ?? null
          }));
        const nextSources: Record<string, AiCitationSource[]> = {};
        for (const m of list) {
          if (m.role !== 'ASSISTANT') continue;
          const src = m.sources;
          if (Array.isArray(src) && src.length > 0) {
            nextSources[String(m.id)] = src;
          }
        }
        setSessionId(initialSessionId);
        setMessages(mapped);
        setSourcesByMsgId(nextSources);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!cancelled) {
          setSessionId(undefined);
          setError(msg || '加载会话失败');
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [initialSessionId]);

  async function reloadSessionMessages(id: number, transferPerfFromAssistantId?: string) {
    const list = await getQaSessionMessages(id);
    const mapped: ChatMsg[] = list
      .filter((m) => m.role === 'USER' || m.role === 'ASSISTANT')
      .map((m: QaMessageDTO) => ({
        id: String(m.id),
        role: m.role === 'USER' ? 'user' : 'assistant',
        content: m.content,
        createdAt: m.createdAt,
        model: m.model ?? null,
        tokensIn: m.tokensIn ?? null,
        tokensOut: m.tokensOut ?? null,
        latencyMs: m.latencyMs ?? null,
        isFavorite: m.isFavorite,
        firstTokenLatencyMs: m.firstTokenLatencyMs ?? null
      }));
    const nextSources: Record<string, AiCitationSource[]> = {};
    for (const m of list) {
      if (m.role !== 'ASSISTANT') continue;
      const src = m.sources;
      if (Array.isArray(src) && src.length > 0) {
        nextSources[String(m.id)] = src;
      }
    }
    setMessages(mapped);
    setSourcesByMsgId(nextSources);

    if (transferPerfFromAssistantId) {
      const lastAssistant = [...mapped].reverse().find((m) => m.role === 'assistant');
      if (lastAssistant) {
        setPerfByMsgId((prev) => {
          const from = prev[transferPerfFromAssistantId];
          if (!from) return prev;
          const already = prev[lastAssistant.id];
          const next = { ...prev };
          if (!already) next[lastAssistant.id] = from;
          delete next[transferPerfFromAssistantId];
          return next;
        });
        setThinkPerfByMsgId((prev) => {
          const from = prev[transferPerfFromAssistantId];
          if (!from) return prev;
          const already = prev[lastAssistant.id];
          const next = { ...prev };
          if (!already) next[lastAssistant.id] = from;
          delete next[transferPerfFromAssistantId];
          return next;
        });
        setThinkUiByMsgId((prev) => {
          const from = prev[transferPerfFromAssistantId];
          if (!from) return prev;
          const already = prev[lastAssistant.id];
          const next = { ...prev };
          if (!already) next[lastAssistant.id] = from;
          delete next[transferPerfFromAssistantId];
          return next;
        });
        setDeepThinkByMsgId((prev) => {
          if (!(transferPerfFromAssistantId in prev)) return prev;
          const already = prev[lastAssistant.id];
          const next = { ...prev };
          if (already === undefined) next[lastAssistant.id] = prev[transferPerfFromAssistantId]!;
          delete next[transferPerfFromAssistantId];
          return next;
        });
      }
    }
  }

  useEffect(() => {
    setThinkUiByMsgId((prev) => {
      let changed = false;
      const next: Record<string, ThinkUi> = { ...prev };
      const idsInUse = new Set<string>();
      for (const m of messages) {
        idsInUse.add(m.id);
        if (m.role !== 'assistant') continue;
        const parsed = splitThinkText(m.content || '');
        if (!parsed.hasThink) continue;
        if (!next[m.id]) {
          next[m.id] = { collapsed: parsed.thinkClosed };
          changed = true;
        }
      }
      for (const id of Object.keys(next)) {
        if (!idsInUse.has(id)) {
          delete next[id];
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [messages]);

  function markFirstDelta(messageId: string) {
    setPerfByMsgId((prev) => {
      const now = Date.now();
      const cur = prev[messageId];
      if (cur?.firstDeltaAtMs) return prev;
      return { ...prev, [messageId]: { ...(cur ?? { startAtMs: now }), firstDeltaAtMs: now } };
    });
  }

  function markDone(messageId: string, latencyMs?: number) {
    setPerfByMsgId((prev) => {
      const now = Date.now();
      const cur = prev[messageId];
      if (!cur) return { ...prev, [messageId]: { startAtMs: now, doneAtMs: now, backendLatencyMs: latencyMs } };
      return { ...prev, [messageId]: { ...cur, doneAtMs: now, backendLatencyMs: latencyMs ?? cur.backendLatencyMs } };
    });
  }

  async function sendText(text: string, images: UploadResult[]) {
    const raw = String(text ?? '');
    const trimmed = raw.trim() || (images.length ? '请分析这些图片。' : '');
    if (!trimmed || isStreaming) return;

    setError(null);
    const providerIdToSend = selectedProviderId.trim() ? selectedProviderId.trim() : undefined;
    const modelToSend = selectedModel.trim() ? selectedModel.trim() : undefined;
    const requestDeepThink = effectiveDeepThink;

    const userId = uid();
    const assistantId = uid();
    const nowIso = new Date().toISOString();

    setMessages((prev) => [
      ...prev,
      {
        id: userId,
        role: 'user',
        content: trimmed,
        createdAt: nowIso,
        tokensIn: null,
        tokensOut: null,
        model: null,
        latencyMs: null,
        firstTokenLatencyMs: null
      },
      {
        id: assistantId,
        role: 'assistant',
        content: '',
        createdAt: nowIso,
        tokensIn: null,
        tokensOut: null,
        model: null,
        latencyMs: null,
        firstTokenLatencyMs: null
      }
    ]);
    setSourcesByMsgId((prev) => {
      const next = { ...prev };
      delete next[assistantId];
      return next;
    });
    setPerfByMsgId((prev) => ({ ...prev, [assistantId]: { startAtMs: Date.now() } }));
    setStreamingAssistantId(assistantId);
    streamRawByMsgIdRef.current[assistantId] = '';
    setDeepThinkByMsgId((prev) => ({ ...prev, [assistantId]: requestDeepThink }));

    setIsStreaming(true);
    streamAutoScrollDisabledRef.current = false;
    const ac = new AbortController();
    abortRef.current = ac;

    try {
      if (streamOutput) {
        await chatStream(
          {
            sessionId: sessionId && sessionId > 0 ? sessionId : undefined,
            message: trimmed,
            deepThink: requestDeepThink,
            useRag,
            ragTopK,
            providerId: providerIdToSend,
            model: modelToSend,
            images: images.length ? images.map((x) => ({ url: x.fileUrl, mimeType: x.mimeType, fileAssetId: x.id })) : undefined,
          },
          (ev: AiStreamEvent) => {
            if (ev.type === 'meta') {
              if (Number.isFinite(ev.sessionId)) {
                const nextId = ev.sessionId as number;
                setSessionId(nextId);

                if (lastSyncedSessionIdRef.current !== nextId) {
                  lastSyncedSessionIdRef.current = nextId;

                  pendingUrlSyncRef.current = nextId;
                  if (!urlSyncScheduledRef.current) {
                    urlSyncScheduledRef.current = true;
                    queueMicrotask(() => {
                      urlSyncScheduledRef.current = false;
                      const idToSync = pendingUrlSyncRef.current;
                      pendingUrlSyncRef.current = null;
                      if (idToSync == null) return;

                      const loc = latestLocationRef.current;
                      const currentParams = new URLSearchParams(loc.search);
                      const currentUrlId = currentParams.get('sessionId');
                      const nextUrl = `/portal/assistant/chat?sessionId=${idToSync}`;
                      const currentUrl = `${loc.pathname}${loc.search}`;

                      if (currentUrl === nextUrl) return;
                      if (loc.pathname !== '/portal/assistant/chat') {
                        return;
                      }
                      if (currentUrlId !== String(idToSync)) {
                        navigate(nextUrl, { replace: true });
                      }
                    });
                  }
                }
              }
            } else if (ev.type === 'delta') {
              if (!ev.content) return;
              markFirstDelta(assistantId);
              const prevRaw = streamRawByMsgIdRef.current[assistantId] ?? '';
              const nextRaw = prevRaw + ev.content;
              streamRawByMsgIdRef.current[assistantId] = nextRaw;
              setMessages((prev) => {
                const nextContent = requestDeepThink ? nextRaw : stripThinkBlocks(nextRaw);
                return prev.map((m) => (m.id === assistantId ? { ...m, content: nextContent } : m));
              });
              if (requestDeepThink) {
                const before = splitThinkText(prevRaw);
                const after = splitThinkText(nextRaw);
                if (after.hasThink) {
                  const now = Date.now();
                  setThinkUiByMsgId((p) => (p[assistantId] ? p : { ...p, [assistantId]: { collapsed: after.thinkClosed } }));
                  if (!before.hasThink) {
                    setThinkPerfByMsgId((p) => {
                      const cur = p[assistantId];
                      if (cur?.startedAtMs) return p;
                      return { ...p, [assistantId]: { ...(cur ?? {}), startedAtMs: now } };
                    });
                  }
                  if (!before.thinkClosed && after.thinkClosed) {
                    setThinkUiByMsgId((p) => ({ ...p, [assistantId]: { collapsed: true } }));
                    setThinkPerfByMsgId((p) => {
                      const cur = p[assistantId];
                      if (cur?.endedAtMs) return p;
                      const startedAtMs = cur?.startedAtMs ?? now;
                      return { ...p, [assistantId]: { ...(cur ?? {}), startedAtMs, endedAtMs: now } };
                    });
                  }
                }
              }
            } else if (ev.type === 'sources') {
              setSourcesByMsgId((prev) => ({ ...prev, [assistantId]: ev.sources ?? [] }));
            } else if (ev.type === 'error') {
              setError(ev.message || '生成失败');
            } else if (ev.type === 'done') {
              markDone(assistantId, ev.latencyMs);
              if (requestDeepThink) {
                setThinkPerfByMsgId((p) => {
                  const cur = p[assistantId];
                  if (!cur?.startedAtMs || cur.endedAtMs) return p;
                  return { ...p, [assistantId]: { ...cur, endedAtMs: Date.now() } };
                });
              }
            }
          },
          ac.signal
        );
      } else {
        const res = await chatOnce(
          {
            sessionId: sessionId && sessionId > 0 ? sessionId : undefined,
            message: trimmed,
            deepThink: requestDeepThink,
            useRag,
            ragTopK,
            providerId: providerIdToSend,
            model: modelToSend,
            images: images.length ? images.map((x) => ({ url: x.fileUrl, mimeType: x.mimeType, fileAssetId: x.id })) : undefined,
          },
          ac.signal
        );
        if (!ac.signal.aborted && Number.isFinite(res.sessionId)) {
          const nextId = res.sessionId as number;
          setSessionId(nextId);

          if (lastSyncedSessionIdRef.current !== nextId) {
            lastSyncedSessionIdRef.current = nextId;

            pendingUrlSyncRef.current = nextId;
            if (!urlSyncScheduledRef.current) {
              urlSyncScheduledRef.current = true;
              queueMicrotask(() => {
                urlSyncScheduledRef.current = false;
                const idToSync = pendingUrlSyncRef.current;
                pendingUrlSyncRef.current = null;
                if (idToSync == null) return;

                const loc = latestLocationRef.current;
                const currentParams = new URLSearchParams(loc.search);
                const currentUrlId = currentParams.get('sessionId');
                const nextUrl = `/portal/assistant/chat?sessionId=${idToSync}`;
                const currentUrl = `${loc.pathname}${loc.search}`;

                if (currentUrl === nextUrl) return;
                if (loc.pathname !== '/portal/assistant/chat') {
                  return;
                }
                if (currentUrlId !== String(idToSync)) {
                  navigate(nextUrl, { replace: true });
                }
              });
            }
          }
        }
        if (!ac.signal.aborted) {
          const raw = String(res.content ?? '');
          streamRawByMsgIdRef.current[assistantId] = raw;
          markFirstDelta(assistantId);
          setMessages((prev) => {
            const nextContent = requestDeepThink ? raw : stripThinkBlocks(raw);
            return prev.map((m) => (m.id === assistantId ? { ...m, content: nextContent } : m));
          });
          if (res.sources) {
            setSourcesByMsgId((prev) => ({ ...prev, [assistantId]: res.sources ?? [] }));
          }
          markDone(assistantId, res.latencyMs);
        }
      }
      if (!ac.signal.aborted) {
        const id = sessionId && sessionId > 0 ? sessionId : lastSyncedSessionIdRef.current;
        if (typeof id === 'number' && id > 0) {
          await reloadSessionMessages(id, assistantId);
        }
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (!ac.signal.aborted) setError(msg || '请求失败');
    } finally {
      delete streamRawByMsgIdRef.current[assistantId];
      if (abortRef.current === ac) abortRef.current = null;
      setIsStreaming(false);
      setStreamingAssistantId(null);
    }
  }

  async function handleSend() {
    const text = question.trim();
    if ((!text && pendingImages.length === 0) || isStreaming) return;
    setQuestion('');
    const imgs = pendingImages;
    setPendingImages([]);
    await sendText(text, imgs);
  }

  async function handleTranslate(m: ChatMsg) {
    if (isStreaming) return;
    const src = (m.content ?? '').trim();
    if (!src) return;
    const prompt = `请将以下内容翻译成中文，保持原意，保留 Markdown/代码块格式：\n\n${src}`;
    await sendText(prompt, []);
  }

  async function handleSaveEdit(messageId: string, draft: string) {
    if (!isPersistedId(messageId)) return;
    if (!sessionId || sessionId <= 0) return;
    const trimmed = draft.trim();
    if (!trimmed) return;
    setError(null);
    setEditing(null);
    await updateQaMessage(Number(messageId), { content: trimmed });
    await reloadSessionMessages(sessionId);
  }

  async function handleResendEditedUserMessage(questionMessageId: string, draft: string, assistantMessageId: string) {
    if (isStreaming) return;
    if (!isPersistedId(questionMessageId)) return;
    if (!isPersistedId(assistantMessageId)) return;
    if (!sessionId || sessionId <= 0) return;
    const trimmed = draft.trim();
    if (!trimmed) return;

    setError(null);
    setEditing(null);
    await updateQaMessage(Number(questionMessageId), { content: trimmed });
    setMessages((prev) => prev.map((m) => (m.id === questionMessageId ? { ...m, content: trimmed } : m)));
    await handleRegenerate(Number(questionMessageId), assistantMessageId);
  }

  async function handleDeleteMessage(messageId: string) {
    if (!isPersistedId(messageId)) return;
    if (!sessionId || sessionId <= 0) return;
    setError(null);
    setEditing(null);
    await deleteQaMessage(Number(messageId));
    await reloadSessionMessages(sessionId);
  }

  async function handleRegenerate(questionMessageId: number, assistantMessageId: string) {
    if (isStreaming) return;
    if (!sessionId || sessionId <= 0) return;
    if (!isPersistedId(assistantMessageId)) return;

    setError(null);
    const providerIdToSend = selectedProviderId.trim() ? selectedProviderId.trim() : undefined;
    const modelToSend = selectedModel.trim() ? selectedModel.trim() : undefined;
    const requestDeepThink = effectiveDeepThink;
    setDeepThinkByMsgId((prev) => ({ ...prev, [assistantMessageId]: requestDeepThink }));
    setEditing(null);
    setSourcesByMsgId((prev) => {
      const next = { ...prev };
      delete next[assistantMessageId];
      return next;
    });
    setMessages((prev) =>
      prev.map((x) =>
        x.id === assistantMessageId ? { ...x, content: '', tokensIn: null, tokensOut: null, latencyMs: null, firstTokenLatencyMs: null } : x
      )
    );

    setIsStreaming(true);
    streamAutoScrollDisabledRef.current = false;
    const ac = new AbortController();
    abortRef.current = ac;
    setPerfByMsgId((prev) => ({ ...prev, [assistantMessageId]: { startAtMs: Date.now() } }));
    setStreamingAssistantId(assistantMessageId);
    streamRawByMsgIdRef.current[assistantMessageId] = '';
    try {
      if (streamOutput) {
        await regenerateStream(
          questionMessageId,
          { deepThink: requestDeepThink, useRag, ragTopK, providerId: providerIdToSend, model: modelToSend },
          (ev: AiStreamEvent) => {
            if (ev.type === 'delta') {
              if (!ev.content) return;
              markFirstDelta(assistantMessageId);
              const prevRaw = streamRawByMsgIdRef.current[assistantMessageId] ?? '';
              const nextRaw = prevRaw + ev.content;
              streamRawByMsgIdRef.current[assistantMessageId] = nextRaw;
              setMessages((prev) => {
                const nextContent = requestDeepThink ? nextRaw : stripThinkBlocks(nextRaw);
                return prev.map((m) => (m.id === assistantMessageId ? { ...m, content: nextContent } : m));
              });
              if (requestDeepThink) {
                const before = splitThinkText(prevRaw);
                const after = splitThinkText(nextRaw);
                if (after.hasThink) {
                  const now = Date.now();
                  setThinkUiByMsgId((p) =>
                    p[assistantMessageId] ? p : { ...p, [assistantMessageId]: { collapsed: after.thinkClosed } }
                  );
                  if (!before.hasThink) {
                    setThinkPerfByMsgId((p) => {
                      const cur = p[assistantMessageId];
                      if (cur?.startedAtMs) return p;
                      return { ...p, [assistantMessageId]: { ...(cur ?? {}), startedAtMs: now } };
                    });
                  }
                  if (!before.thinkClosed && after.thinkClosed) {
                    setThinkUiByMsgId((p) => ({ ...p, [assistantMessageId]: { collapsed: true } }));
                    setThinkPerfByMsgId((p) => {
                      const cur = p[assistantMessageId];
                      if (cur?.endedAtMs) return p;
                      const startedAtMs = cur?.startedAtMs ?? now;
                      return { ...p, [assistantMessageId]: { ...(cur ?? {}), startedAtMs, endedAtMs: now } };
                    });
                  }
                }
              }
            } else if (ev.type === 'sources') {
              setSourcesByMsgId((prev) => ({ ...prev, [assistantMessageId]: ev.sources ?? [] }));
            } else if (ev.type === 'error') {
              setError(ev.message || '生成失败');
            } else if (ev.type === 'done') {
              markDone(assistantMessageId, ev.latencyMs);
              if (requestDeepThink) {
                setThinkPerfByMsgId((p) => {
                  const cur = p[assistantMessageId];
                  if (!cur?.startedAtMs || cur.endedAtMs) return p;
                  return { ...p, [assistantMessageId]: { ...cur, endedAtMs: Date.now() } };
                });
              }
            }
          },
          ac.signal
        );
      } else {
        const res = await regenerateOnce(
          questionMessageId,
          { deepThink: requestDeepThink, useRag, ragTopK, providerId: providerIdToSend, model: modelToSend },
          ac.signal
        );
        if (!ac.signal.aborted) {
          const raw = String(res.content ?? '');
          streamRawByMsgIdRef.current[assistantMessageId] = raw;
          markFirstDelta(assistantMessageId);
          setMessages((prev) => {
            const nextContent = requestDeepThink ? raw : stripThinkBlocks(raw);
            return prev.map((m) => (m.id === assistantMessageId ? { ...m, content: nextContent } : m));
          });
          if (res.sources) {
            setSourcesByMsgId((prev) => ({ ...prev, [assistantMessageId]: res.sources ?? [] }));
          }
          markDone(assistantMessageId, res.latencyMs);
        }
      }
      if (!ac.signal.aborted) {
        await reloadSessionMessages(sessionId);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (!ac.signal.aborted) setError(msg || '请求失败');
    } finally {
      delete streamRawByMsgIdRef.current[assistantMessageId];
      if (abortRef.current === ac) abortRef.current = null;
      setIsStreaming(false);
      setStreamingAssistantId(null);
    }
  }


  function stopGenerating() {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    setStreamingAssistantId(null);
    streamRawByMsgIdRef.current = {};
  }

  function startNewChat() {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    setError(null);
    setSessionId(undefined);
    setMessages([]);
    setSourcesByMsgId({});
    setPerfByMsgId({});
    setDeepThinkByMsgId({});
    setThinkPerfByMsgId({});
    setThinkUiByMsgId({});
    setStreamingAssistantId(null);
    streamRawByMsgIdRef.current = {};
    lastSyncedSessionIdRef.current = undefined;
    pendingUrlSyncRef.current = null;
    urlSyncScheduledRef.current = false;
    navigate('/portal/assistant/chat', { replace: false });
  }

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] gap-4">
      <div className="flex-none">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">对话</h3>
            {sessionId !== undefined && <div className="mt-1 text-xs text-gray-500">SessionId：{sessionId}</div>}
          </div>
          <div className="flex items-start gap-2">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <label className="text-xs text-gray-600 whitespace-nowrap">模型:</label>
                <select
                  className="w-80 border rounded px-3 py-1.5 text-sm bg-white"
                  value={selectedProviderModelValue}
                  onChange={(e) => {
                    const parsed = parseProviderModelValue(String(e.target.value ?? ''));
                    if (!parsed) {
                      setSelectedProviderId('');
                      setSelectedModel('');
                      void updateMyAssistantPreferences({ defaultProviderId: null, defaultModel: null }).catch(() => {});
                      return;
                    }
                    setSelectedProviderId(parsed.providerId);
                    setSelectedModel(parsed.model);
                    void updateMyAssistantPreferences({ defaultProviderId: parsed.providerId, defaultModel: parsed.model }).catch(() => {});
                  }}
                  disabled={isStreaming}
                >
                  <option value="">
                    {pendingImages.length ? '自动（图片聊天/视觉模型池）' : '自动（文本聊天/均衡负载）'}
                  </option>
                  {flatModelOptions.map((it) => (
                    <option key={it.value} value={it.value}>
                      {it.providerLabel}：{it.model}
                    </option>
                  ))}
                </select>
              </div>
              {pendingImages.length ? (
                <div className="text-xs text-gray-500">
                  已附加 {pendingImages.length} 张图片：自动会路由到视觉模型；若所选模型不支持图片，会提示你切换为视觉模型（图片聊天）。
                </div>
              ) : null}
              {optionsError ? <div className="text-xs text-red-600">{optionsError}</div> : null}
            </div>
            <button type="button" onClick={startNewChat} className="px-3 py-2 rounded-md bg-gray-100 hover:bg-gray-200 text-sm">
              新对话
            </button>
          </div>
        </div>
      </div>

      {error && (
        <div className="border border-red-200 bg-red-50 text-red-700 rounded-md px-3 py-2 text-sm">{error}</div>
      )}

      <div className="flex-1 min-h-0 w-full flex flex-col gap-4">
          <div className="relative flex-1 min-h-0">
            <div
              ref={chatScrollRef}
              className="h-full overflow-y-auto overflow-x-auto border border-gray-200 rounded-md p-3 space-y-3 bg-white"
              onScroll={handleChatScroll}
            >
              {messages.length === 0 ? (
                <div className="text-sm text-gray-500">还没有消息，输入问题开始对话。</div>
              ) : (
                <div className="space-y-3">
                  {messages.map((m, idx) => {
              const persisted = isPersistedId(m.id);
              const isEditing = editing?.id === m.id;
              const canRegenerate =
                m.role === 'assistant' &&
                idx > 0 &&
                messages[idx - 1]?.role === 'user' &&
                isPersistedId(messages[idx - 1]!.id) &&
                persisted &&
                !isStreaming;
              
              const isUser = m.role === 'user';
              const canResendEditedUser =
                isUser &&
                isEditing &&
                (editing?.draft ?? '').trim() !== (m.content ?? '').trim() &&
                messages[idx + 1]?.role === 'assistant' &&
                isPersistedId(messages[idx + 1]!.id) &&
                !isStreaming;
              const isThisStreaming = isStreaming && streamingAssistantId === m.id;
              const dt = formatDateTime(m.createdAt) ?? '';
              const perf = perfByMsgId[m.id];
              const allowThink = (deepThinkByMsgId[m.id] ?? effectiveDeepThink) && m.role === 'assistant';
              const thinkMeta = (() => {
                if (m.role !== 'assistant') return null;
                if (!allowThink) return null;
                const parsed = splitThinkText(m.content || '');
                if (!parsed.hasThink) return null;
                const tp = thinkPerfByMsgId[m.id];
                if (!tp?.startedAtMs) return null;
                const elapsedMs = Math.max(0, (tp.endedAtMs ?? Date.now()) - tp.startedAtMs);
                const dur = formatDurationMs(elapsedMs);
                return isThisStreaming && !tp.endedAtMs ? `思考中 ${dur}` : `思考 ${dur}`;
              })();
              const firstTokenLatencyMs =
                m.role === 'assistant'
                  ? typeof m.firstTokenLatencyMs === 'number'
                    ? m.firstTokenLatencyMs
                    : typeof perf?.firstDeltaAtMs === 'number'
                      ? Math.max(0, perf.firstDeltaAtMs - perf.startAtMs)
                      : null
                  : null;
              const tokensOutSpeedTokPerSec = (() => {
                if (m.role !== 'assistant') return null;
                if (typeof m.tokensOut !== 'number') return null;
                const latencyMs =
                  typeof m.latencyMs === 'number'
                    ? m.latencyMs
                    : typeof perf?.backendLatencyMs === 'number'
                      ? perf.backendLatencyMs
                      : typeof perf?.startAtMs === 'number'
                        ? Math.max(0, Date.now() - perf.startAtMs)
                        : null;
                if (typeof latencyMs !== 'number' || !Number.isFinite(latencyMs) || latencyMs <= 0) return null;
                const secs = latencyMs / 1000;
                if (secs <= 0) return null;
                return m.tokensOut / secs;
              })();
              
              return (
                <div key={m.id} id={`msg-${m.id}`} className={`group flex items-start gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
                  {/* Avatar */}
                  {isUser ? (
                    <Avatar className="h-8 w-8">
                      <AvatarImage src={profileAvatarUrl} alt={displayUsername} />
                      <AvatarFallback className="bg-blue-500 text-white text-xs">{avatarFallbackText}</AvatarFallback>
                    </Avatar>
                  ) : (
                    <div className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-green-600 text-white">
                      <Bot size={18} />
                    </div>
                  )}

                  {/* Content Wrapper */}
                  <div
                    className={`min-w-0 flex flex-col ${isEditing ? 'flex-1' : 'max-w-[78%]'} ${isUser ? 'items-end' : 'items-start'}`}
                  >
                    {/* Name */}
                    <div className="flex items-center gap-2 text-xs text-gray-500 mb-1 px-1 flex-wrap">
                      {isUser ? (
                        <>
                          {dt ? <span>{dt}</span> : null}
                          {dt ? <span>·</span> : null}
                          <span>{displayUsername}</span>
                        </>
                      ) : (
                        <>
                          <span>{m.model || 'Qwen3'}</span>
                          {dt ? <span>· {dt}</span> : null}
                        </>
                      )}
                    </div>

                    {/* Message Bubble */}
                    <div
                      className={
                        'min-w-0 max-w-full rounded-lg px-4 py-3 text-sm break-words overflow-x-auto shadow-sm ' +
                        (isUser || isEditing ? 'whitespace-pre-wrap ' : 'whitespace-normal ') +
                        (isEditing ? 'w-full ' : '') +
                        (isUser ? 'bg-blue-600 text-white' : 'bg-white border border-gray-200 text-gray-900')
                      }
                    >
                      {isEditing ? (
                        <textarea
                          ref={editingTextareaRef}
                          value={editing?.draft ?? ''}
                          onChange={(e) => setEditing((prev) => (prev && prev.id === m.id ? { ...prev, draft: e.target.value } : prev))}
                          onKeyDown={(e) => {
                            if (e.key === 'Escape') {
                              e.preventDefault();
                              setEditing(null);
                              return;
                            }
                            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                              e.preventDefault();
                              void handleSaveEdit(m.id, editing?.draft ?? '');
                            }
                          }}
                          autoFocus
                          rows={1}
                          className={`w-full bg-transparent border-0 p-0 m-0 text-sm focus:outline-none focus:ring-0 resize-none ${
                            isUser ? 'text-white placeholder:text-white/70' : 'text-gray-900'
                          }`}
                        />
                      ) : m.role === 'assistant' ? (
                        (() => {
                          const parsed = splitThinkText(m.content || '');
                          const ui = thinkUiByMsgId[m.id];
                          const collapsed = ui?.collapsed ?? parsed.thinkClosed;
                          const isThisStreaming = isStreaming && streamingAssistantId === m.id;
                          const thinkingNow = Boolean(isThisStreaming && parsed.hasThink && !parsed.thinkClosed);
                          const thinkPerf = thinkPerfByMsgId[m.id];
                          const thinkLabel = (() => {
                            if (!allowThink) return null;
                            if (!thinkPerf?.startedAtMs) return null;
                            const elapsedMs = Math.max(0, (thinkPerf.endedAtMs ?? Date.now()) - thinkPerf.startedAtMs);
                            const dur = formatDurationMs(elapsedMs);
                            return thinkingNow && !thinkPerf.endedAtMs ? `已用 ${dur}` : `用时 ${dur}`;
                          })();
                          const renderMd = (md: string) => (
                            <MarkdownPreview
                              markdown={linkifyCitations(md)}
                              components={{
                                a: ({ href, children, ...props }) => {
                                  const h = href ?? '';
                                  if (h.startsWith('#cite-')) {
                                    const n = Number(h.slice('#cite-'.length));
                                    const cls = colorClassForCitationIndex(n);
                                    return (
                                      <a href={h} className={`${cls} font-semibold hover:underline`} {...props}>
                                        {children}
                                      </a>
                                    );
                                  }
                                  const isHashLink = typeof h === 'string' && h.startsWith('#');
                                  return (
                                    <a
                                      className="text-blue-600 hover:underline"
                                      href={h}
                                      target={isHashLink ? undefined : '_blank'}
                                      rel={isHashLink ? undefined : 'noreferrer'}
                                      {...props}
                                    >
                                      {children}
                                    </a>
                                  );
                                },
                              }}
                            />
                          );

                          const mainMd =
                            !allowThink && parsed.hasThink && !parsed.thinkClosed
                              ? '…'
                              : parsed.hasThink && !parsed.thinkClosed
                                ? ''
                                : parsed.main || (isThisStreaming ? '…' : '');
                          const toggle = () => {
                            setThinkUiByMsgId((prev) => {
                              const cur = prev[m.id]?.collapsed ?? true;
                              return { ...prev, [m.id]: { collapsed: !cur } };
                            });
                          };

                          return (
                            <div className="space-y-2">
                              {allowThink && parsed.hasThink ? (
                                <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2">
                                  <div className="flex items-center justify-between gap-2">
                                    <span className="text-xs text-amber-900 whitespace-nowrap">
                                      {thinkingNow ? '模型正在思考…' : '模型思考'}
                                      {thinkLabel ? ` · ${thinkLabel}` : ''}
                                    </span>
                                    {parsed.thinkClosed ? (
                                      <button
                                        type="button"
                                        className="text-xs text-amber-900 hover:underline whitespace-nowrap"
                                        onClick={toggle}
                                      >
                                        {collapsed ? '展开' : '收起'}
                                      </button>
                                    ) : null}
                                  </div>
                                  {!collapsed ? (
                                    <div className="mt-2 text-sm text-gray-900">{renderMd(parsed.think || (thinkingNow ? '…' : ''))}</div>
                                  ) : null}
                                </div>
                              ) : null}

                              {renderMd(mainMd)}
                            </div>
                          );
                        })()
                      ) : (
                        m.content
                      )}
                    </div>

                    <div className={`mt-1 px-1 text-xs text-gray-500 flex items-center gap-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
                      {!isUser ? <span className="whitespace-nowrap">{formatMsgTokensInfo(m)}</span> : null}
                      {!isUser && thinkMeta ? <span className="whitespace-nowrap">{thinkMeta}</span> : null}
                      {!isUser && firstTokenLatencyMs != null ? (
                        <span className="whitespace-nowrap">首字 {Math.round(firstTokenLatencyMs)}ms</span>
                      ) : null}
                      {!isUser && tokensOutSpeedTokPerSec != null ? (
                        <span className="whitespace-nowrap">速度 {tokensOutSpeedTokPerSec.toFixed(1)} tok/s</span>
                      ) : null}

                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        {!isStreaming && !isEditing && m.content.trim().length > 0 && (
                          <button
                            type="button"
                            className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                            title="翻译为中文"
                            onClick={() => void handleTranslate(m)}
                          >
                            <Languages size={14} />
                          </button>
                        )}
                        {persisted && !isStreaming ? (
                          isEditing ? (
                            <>
                              <button
                                type="button"
                                className="p-1.5 rounded-md text-gray-500 hover:text-green-700 hover:bg-green-50 transition-colors"
                                title="保存（Ctrl/⌘+Enter）"
                                onClick={() => void handleSaveEdit(m.id, editing?.draft ?? '')}
                              >
                                <Check size={14} />
                              </button>
                              {canResendEditedUser ? (
                                <button
                                  type="button"
                                  className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                                  title="重新发送"
                                  onClick={() =>
                                    void handleResendEditedUserMessage(m.id, editing?.draft ?? '', messages[idx + 1]!.id)
                                  }
                                >
                                  <Send size={14} />
                                </button>
                              ) : null}
                              <button
                                type="button"
                                className="p-1.5 rounded-md text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors"
                                title="取消（Esc）"
                                onClick={() => setEditing(null)}
                              >
                                <X size={14} />
                              </button>
                            </>
                          ) : (
                            <>
                              <button
                                type="button"
                                className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                                title="复制"
                                onClick={() => void handleCopy(m.id, m.content)}
                              >
                                {copiedId === m.id ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
                              </button>
                              <button
                                type="button"
                                className={`p-1.5 rounded-md transition-colors ${
                                  m.isFavorite ? 'text-red-500 hover:text-red-600' : 'text-gray-500 hover:text-red-500 hover:bg-gray-100'
                                }`}
                                title={m.isFavorite ? '取消收藏' : '收藏'}
                                onClick={() => void handleToggleFavorite(m.id)}
                              >
                                <Heart size={14} fill={m.isFavorite ? 'currentColor' : 'none'} />
                              </button>
                              {canRegenerate && (
                                <button
                                  type="button"
                                  className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                                  title="重新生成"
                                  onClick={() => void handleRegenerate(Number(messages[idx - 1]!.id), m.id)}
                                >
                                  <RefreshCw size={14} />
                                </button>
                              )}
                              <button
                                type="button"
                                className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                                title="编辑"
                                onClick={() => setEditing({ id: m.id, draft: m.content })}
                              >
                                <Pencil size={14} />
                              </button>
                              <button
                                type="button"
                                className="p-1.5 rounded-md text-gray-500 hover:text-red-600 hover:bg-red-50 transition-colors"
                                title="删除"
                                onClick={() => void handleDeleteMessage(m.id)}
                              >
                                <Trash2 size={14} />
                              </button>
                            </>
                          )
                        ) : null}
                      </div>

                      {isUser ? <span className="whitespace-nowrap">{formatMsgTokensInfo(m)}</span> : null}
                    </div>

                    {/* Sources (Assistant only) */}
                    {(() => {
                      if (m.role !== 'assistant') return null;
                      if (!useRag) return null;
                      const cited = extractCitationIndexes(m.content || '');
                      if (cited.size === 0) return null;
                      const shownSources = (sourcesByMsgId[m.id] ?? []).filter((s) => cited.has(Number(s.index))).slice(0, 20);
                      if (shownSources.length === 0) return null;
                      return (
                      <div className="mt-2 w-full rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-700">
                        <div className="font-medium text-gray-900 mb-1">来源</div>
                        <div className="space-y-1">
                          {shownSources.map((s) => {
                            const idx = Number(s.index);
                            const cls = colorClassForCitationIndex(idx);
                            return (
                              <div
                                key={`${s.index}-${s.postId ?? 'x'}`}
                                className="break-words"
                                id={Number.isFinite(idx) ? `cite-${idx}` : undefined}
                              >
                                <span className={`${cls} font-semibold`}>[{s.index}] </span>
                                {s.title ? <span className="mr-2">{s.title}</span> : null}
                                {s.url ? (
                                  <a className="text-blue-600 hover:underline" href={s.url} target="_blank" rel="noreferrer">
                                    {s.url}
                                  </a>
                                ) : null}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                      );
                    })()}

                  </div>
                </div>
              );
                  })}
                </div>
              )}
              <div ref={chatBottomRef} />
            </div>

            {showScrollToBottom ? (
              <button
                type="button"
                onClick={handleScrollToBottomClick}
                className="absolute bottom-3 right-3 inline-flex items-center gap-1 rounded-full border border-gray-200 bg-white/95 px-3 py-2 text-xs text-gray-700 shadow-sm hover:bg-white"
              >
                <ChevronDown size={16} />
                回到底部
              </button>
            ) : null}
          </div>

      <form
        className="space-y-2"
        onSubmit={(e) => {
          e.preventDefault();
          void handleSend();
        }}
      >
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
          disabled={isStreaming}
        />
        <div
          onMouseDown={handleResizeMouseDown}
          className="h-1.5 w-full cursor-row-resize hover:bg-blue-400/30 transition-colors flex items-center justify-center group"
          title="拖动调整高度"
        >
          <div className="w-12 h-0.5 rounded-full bg-gray-300 group-hover:bg-blue-400 transition-colors" />
        </div>
        <textarea
          value={question}
          onChange={(e) => {
            setQuestion(e.target.value);
          }}
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
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void handleSend();
            }
          }}
          style={{ height: inputHeight }}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          placeholder="输入你的问题..."
          disabled={isStreaming}
        />
        {pendingImages.length ? (
          <div className="flex gap-2 overflow-x-auto">
            {pendingImages.slice(0, MAX_VISION_IMAGES).map((it) => (
              <div key={`${it.id}-${it.fileUrl}`} className="shrink-0 relative w-16 h-16 rounded border overflow-hidden bg-gray-50">
                <img src={it.fileUrl} alt={it.fileName} className="w-full h-full object-cover" loading="lazy" />
                <button
                  type="button"
                  className="absolute top-0 right-0 px-1 py-0.5 text-[10px] bg-white/90 border border-gray-200 rounded-bl"
                  onClick={() => setPendingImages((prev) => prev.filter((x) => x.fileUrl !== it.fileUrl))}
                  disabled={isStreaming}
                >
                  删除
                </button>
              </div>
            ))}
          </div>
        ) : null}

        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-4">
            <label className="inline-flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={effectiveDeepThink}
                disabled={thinkingOnly}
                onChange={(e) => {
                  const next = e.target.checked;
                  setDeepThink(next);
                  void updateMyAssistantPreferences({ defaultDeepThink: next }).catch(() => {});
                }}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              深度思考{thinkingOnly ? <span className="text-xs text-gray-500"></span> : null}
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={useRag}
                onChange={(e) => {
                  const next = e.target.checked;
                  setUseRag(next);
                  void updateMyAssistantPreferences({ defaultUseRag: next }).catch(() => {});
                }}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              使用RAG功能
            </label>
          </div>
          <div className="flex items-center gap-2">
            <div className="text-xs text-gray-600">
              Token：in {tokensTotals.inStr} / out {tokensTotals.outStr} / total {tokensTotals.totalStr}
            </div>
            <button
              type="button"
              className="inline-flex items-center gap-2 px-3 py-2 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50 disabled:opacity-60"
              onClick={handlePickImages}
              disabled={isStreaming || imageUploading}
            >
              {imageUploading ? '上传中…' : '添加图片'}
            </button>
            {isStreaming ? (
              <button
                type="button"
                onClick={stopGenerating}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-gray-200 hover:bg-gray-300 transition-colors"
              >
                <Square size={16} fill="currentColor" />
                停止输出
              </button>
            ) : (
              <button
                type="submit"
                disabled={!canSend}
                className={
                  'inline-flex items-center gap-2 px-4 py-2 rounded-md text-white transition-colors ' +
                  (canSend ? 'bg-blue-600 hover:bg-blue-700' : 'bg-blue-300 cursor-not-allowed')
                }
              >
                <Send size={16} />
                发送
              </button>
            )}
          </div>
        </div>
      </form>
      </div>
    </div>
  );
}
