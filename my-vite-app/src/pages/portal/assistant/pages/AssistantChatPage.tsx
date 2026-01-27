import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { RefreshCw, Pencil, Trash2, User, Bot, Send, Square, Copy, Check, Languages } from 'lucide-react';

import { chatStream, regenerateStream, type AiCitationSource, type AiStreamEvent } from '../../../../services/aiChatService';
import { deleteQaMessage, getQaSessionMessages, type QaMessageDTO, updateQaMessage } from '../../../../services/qaHistoryService';

type ChatMsg = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt?: string;
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

function uid(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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

export default function AssistantChatPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();

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
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showSources, setShowSources] = useState<boolean>(() => {
    try {
      const v = localStorage.getItem('assistant.showSources');
      return v === null ? true : v === 'true';
    } catch {
      return true;
    }
  });
  const [deepThink, setDeepThink] = useState<boolean>(() => {
    try {
      const v = localStorage.getItem('assistant.deepThink');
      return v === null ? false : v === 'true';
    } catch {
      return false;
    }
  });
  const [sourcesByMsgId, setSourcesByMsgId] = useState<Record<string, AiCitationSource[]>>({});
  const [editing, setEditing] = useState<{ id: string; draft: string } | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [perfByMsgId, setPerfByMsgId] = useState<Record<string, MsgPerf>>({});

  const abortRef = useRef<AbortController | null>(null);

  const canSend = useMemo(() => !isStreaming && question.trim().length > 0, [isStreaming, question]);

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

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

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
      }
    }
  }

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

  async function sendText(text: string) {
    const trimmed = text.trim();
    if (!trimmed || isStreaming) return;

    setError(null);

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

    setIsStreaming(true);
    const ac = new AbortController();
    abortRef.current = ac;

    try {
      await chatStream(
        { sessionId: sessionId && sessionId > 0 ? sessionId : undefined, message: trimmed, deepThink },
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
            setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + ev.content } : m)));
          } else if (ev.type === 'sources') {
            setSourcesByMsgId((prev) => ({ ...prev, [assistantId]: ev.sources ?? [] }));
          } else if (ev.type === 'error') {
            setError(ev.message || '生成失败');
          } else if (ev.type === 'done') {
            markDone(assistantId, ev.latencyMs);
          }
        },
        ac.signal
      );
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
      if (abortRef.current === ac) abortRef.current = null;
      setIsStreaming(false);
    }
  }

  async function handleSend() {
    const text = question.trim();
    if (!text || isStreaming) return;
    setQuestion('');
    await sendText(text);
  }

  async function handleTranslate(m: ChatMsg) {
    if (isStreaming) return;
    const src = (m.content ?? '').trim();
    if (!src) return;
    const prompt = `请将以下内容翻译成中文，保持原意，保留 Markdown/代码块格式：\n\n${src}`;
    await sendText(prompt);
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
    const ac = new AbortController();
    abortRef.current = ac;
    setPerfByMsgId((prev) => ({ ...prev, [assistantMessageId]: { startAtMs: Date.now() } }));
    try {
      await regenerateStream(
        questionMessageId,
        { deepThink },
        (ev: AiStreamEvent) => {
          if (ev.type === 'delta') {
            if (!ev.content) return;
            markFirstDelta(assistantMessageId);
            setMessages((prev) =>
              prev.map((m) => (m.id === assistantMessageId ? { ...m, content: m.content + ev.content } : m))
            );
          } else if (ev.type === 'sources') {
            setSourcesByMsgId((prev) => ({ ...prev, [assistantMessageId]: ev.sources ?? [] }));
          } else if (ev.type === 'error') {
            setError(ev.message || '生成失败');
          } else if (ev.type === 'done') {
            markDone(assistantMessageId, ev.latencyMs);
          }
        },
        ac.signal
      );
      if (!ac.signal.aborted) {
        await reloadSessionMessages(sessionId);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (!ac.signal.aborted) setError(msg || '请求失败');
    } finally {
      if (abortRef.current === ac) abortRef.current = null;
      setIsStreaming(false);
    }
  }


  function stopGenerating() {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
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
            <p className="text-gray-600">直连百炼 Qwen3（流式输出）。已支持历史会话继续对话。</p>
            {sessionId !== undefined && <div className="mt-1 text-xs text-gray-500">SessionId：{sessionId}</div>}
          </div>
          <button
            type="button"
            onClick={startNewChat}
            className="px-3 py-2 rounded-md bg-gray-100 hover:bg-gray-200 text-sm"
          >
            新对话
          </button>
        </div>
      </div>

      {error && (
        <div className="border border-red-200 bg-red-50 text-red-700 rounded-md px-3 py-2 text-sm">{error}</div>
      )}

      <div className="flex-1 overflow-y-auto border border-gray-200 rounded-md p-3 space-y-3 bg-white">
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
              const dt = formatDateTime(m.createdAt) ?? '';
              const perf = perfByMsgId[m.id];
              const firstTokenLatencyMs =
                m.role === 'assistant'
                  ? typeof m.firstTokenLatencyMs === 'number'
                    ? m.firstTokenLatencyMs
                    : typeof perf?.firstDeltaAtMs === 'number'
                      ? Math.max(0, perf.firstDeltaAtMs - perf.startAtMs)
                      : null
                  : null;
              
              return (
                <div key={m.id} className={`group flex items-start gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
                  {/* Avatar */}
                  <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${isUser ? 'bg-blue-500 text-white' : 'bg-green-600 text-white'}`}>
                    {isUser ? <User size={18} /> : <Bot size={18} />}
                  </div>

                  {/* Content Wrapper */}
                  <div className={`flex flex-col max-w-[85%] ${isUser ? 'items-end' : 'items-start'}`}>
                    {/* Name */}
                    <div className="flex items-center gap-2 text-xs text-gray-500 mb-1 px-1 flex-wrap">
                      <span>{isUser ? 'User' : (m.model || 'Qwen3')}</span>
                      {dt ? <span>· {dt}</span> : null}
                    </div>

                    {/* Message Bubble */}
                    <div
                      className={
                        'rounded-lg px-4 py-3 text-sm whitespace-pre-wrap shadow-sm ' +
                        (isUser ? 'bg-blue-600 text-white' : 'bg-white border border-gray-200 text-gray-900')
                      }
                    >
                      {isEditing ? (
                        <div className="space-y-2 min-w-[300px]">
                          <textarea
                            value={editing?.draft ?? ''}
                            onChange={(e) => setEditing((prev) => (prev && prev.id === m.id ? { ...prev, draft: e.target.value } : prev))}
                            rows={10}
                            className="w-full border border-gray-300 rounded-md px-2 py-1 text-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              className="px-3 py-1 text-xs rounded bg-blue-600 text-white hover:bg-blue-700"
                              onClick={() => void handleSaveEdit(m.id, editing?.draft ?? '')}
                            >
                              保存
                            </button>
                            <button
                              type="button"
                              className="px-3 py-1 text-xs rounded bg-gray-200 hover:bg-gray-300 text-gray-800"
                              onClick={() => setEditing(null)}
                            >
                              取消
                            </button>
                          </div>
                        </div>
                      ) : m.role === 'assistant' ? (
                        <ReactMarkdown
                          remarkPlugins={[remarkGfm, remarkBreaks]}
                          rehypePlugins={[rehypeRaw, rehypeSanitize]}
                          components={{
                            a: ({ href, children, ...props }) => {
                              const h = href ?? '';
                              if (h.startsWith('#cite-')) {
                                const n = Number(h.slice('#cite-'.length));
                                const cls = colorClassForCitationIndex(n);
                                return (
                                  <a
                                    href={h}
                                    className={`${cls} font-semibold hover:underline`}
                                    {...props}
                                  >
                                    {children}
                                  </a>
                                );
                              }
                              return (
                                <a className="text-blue-600 hover:underline" href={h} {...props}>
                                  {children}
                                </a>
                              );
                            }
                          }}
                        >
                          {linkifyCitations(m.content || (isStreaming ? '…' : ''))}
                        </ReactMarkdown>
                      ) : (
                        m.content
                      )}
                    </div>

                    <div className={`mt-1 px-1 text-xs text-gray-500 ${isUser ? 'text-right' : 'text-left'}`}>
                      <span>{formatMsgTokensInfo(m)}</span>
                      {!isUser && firstTokenLatencyMs != null ? (
                        <span className="ml-2">首字 {Math.round(firstTokenLatencyMs)}ms</span>
                      ) : null}
                    </div>

                    {/* Sources (Assistant only) */}
                    {(() => {
                      if (m.role !== 'assistant') return null;
                      if (!showSources) return null;
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

                    {/* Actions Bar (Below message) */}
                    <div className={`mt-1 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity ${isUser ? 'justify-end' : 'justify-start'}`}>
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
                      {persisted && !isStreaming && (
                        <>
                          <button
                            type="button"
                            className="p-1.5 rounded-md text-gray-500 hover:text-blue-600 hover:bg-gray-100 transition-colors"
                            title="复制"
                            onClick={() => void handleCopy(m.id, m.content)}
                          >
                            {copiedId === m.id ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
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
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <form
        className="space-y-2"
        onSubmit={(e) => {
          e.preventDefault();
          void handleSend();
        }}
      >
        <textarea
          value={question}
          onChange={(e) => {
            setQuestion(e.target.value);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void handleSend();
            }
          }}
          rows={4}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="输入你的问题..."
          disabled={isStreaming}
        />

        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-4">
            <label className="inline-flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={deepThink}
                onChange={(e) => {
                  const next = e.target.checked;
                  setDeepThink(next);
                  try {
                    localStorage.setItem('assistant.deepThink', String(next));
                  } catch {
                  }
                }}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              深度思考
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={showSources}
                onChange={(e) => {
                  const next = e.target.checked;
                  setShowSources(next);
                  try {
                    localStorage.setItem('assistant.showSources', String(next));
                  } catch {
                  }
                }}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              显示来源（sources）
            </label>
          </div>
          <div className="flex items-center gap-2">
            <div className="text-xs text-gray-600">
              Token：in {tokensTotals.inStr} / out {tokensTotals.outStr} / total {tokensTotals.totalStr}
            </div>
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
  );
}
