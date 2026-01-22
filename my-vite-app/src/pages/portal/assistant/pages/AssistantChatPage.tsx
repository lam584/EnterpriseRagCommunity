import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';

import { chatStream, type AiCitationSource, type AiStreamEvent } from '../../../../services/aiChatService';
import { getQaSessionMessages, type QaMessageDTO } from '../../../../services/qaHistoryService';

type ChatMsg = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
};

function uid(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
  const [sourcesByMsgId, setSourcesByMsgId] = useState<Record<string, AiCitationSource[]>>({});

  const abortRef = useRef<AbortController | null>(null);

  const canSend = useMemo(() => !isStreaming && question.trim().length > 0, [isStreaming, question]);

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

    void (async () => {
      try {
        const list = await getQaSessionMessages(initialSessionId);
        if (cancelled) return;
        const mapped: ChatMsg[] = list
          .filter((m) => m.role === 'USER' || m.role === 'ASSISTANT')
          .map((m: QaMessageDTO) => ({
            id: String(m.id),
            role: m.role === 'USER' ? 'user' : 'assistant',
            content: m.content
          }));
        setSessionId(initialSessionId);
        setMessages(mapped);
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

  async function handleSend() {
    const text = question.trim();
    if (!text || isStreaming) return;

    setError(null);
    setQuestion('');

    const userId = uid();
    const assistantId = uid();

    setMessages((prev) => [
      ...prev,
      { id: userId, role: 'user', content: text },
      { id: assistantId, role: 'assistant', content: '' }
    ]);
    setSourcesByMsgId((prev) => {
      const next = { ...prev };
      delete next[assistantId];
      return next;
    });

    setIsStreaming(true);
    const ac = new AbortController();
    abortRef.current = ac;

    try {
      await chatStream(
        { sessionId: sessionId && sessionId > 0 ? sessionId : undefined, message: text },
        (ev: AiStreamEvent) => {
          if (ev.type === 'meta') {
            if (Number.isFinite(ev.sessionId)) {
              const nextId = ev.sessionId as number;
              setSessionId(nextId);

              // keep URL in sync so refresh can resume (de-dupe to avoid navigation storms)
              if (lastSyncedSessionIdRef.current !== nextId) {
                lastSyncedSessionIdRef.current = nextId;

                // schedule a single replace based on the latest location
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
                      // Only sync when we're actually on the chat route.
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
            setMessages((prev) => prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + ev.content } : m)));
          } else if (ev.type === 'sources') {
            setSourcesByMsgId((prev) => ({ ...prev, [assistantId]: ev.sources ?? [] }));
          } else if (ev.type === 'error') {
            setError(ev.message || '生成失败');
          } else if (ev.type === 'done') {
            // no-op
          }
        },
        ac.signal
      );
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
    lastSyncedSessionIdRef.current = undefined;
    pendingUrlSyncRef.current = null;
    urlSyncScheduledRef.current = false;
    navigate('/portal/assistant/chat', { replace: false });
  }

  return (
    <div className="space-y-4">
      <div>
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">对话</h3>
            <p className="text-gray-600">直连百炼 Qwen3（流式输出）。已支持历史会话继续对话。</p>
            {sessionId !== undefined && <div className="mt-1 text-xs text-gray-500">SessionId：{sessionId}</div>}
            <label className="mt-2 inline-flex items-center gap-2 text-xs text-gray-600">
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
              />
              显示来源（sources）
            </label>
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

      <div className="border border-gray-200 rounded-md p-3 space-y-3 bg-white">
        {messages.length === 0 ? (
          <div className="text-sm text-gray-500">还没有消息，输入问题开始对话。</div>
        ) : (
          <div className="space-y-3">
            {messages.map((m) => (
              <div key={m.id} className={m.role === 'user' ? 'text-right' : 'text-left'}>
                <div
                  className={
                    'inline-block max-w-[90%] rounded-md px-3 py-2 text-sm whitespace-pre-wrap ' +
                    (m.role === 'user' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-900')
                  }
                >
                  {m.role === 'assistant' ? (
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm, remarkBreaks]}
                      rehypePlugins={[rehypeRaw, rehypeSanitize]}
                    >
                      {m.content || (isStreaming ? '…' : '')}
                    </ReactMarkdown>
                  ) : (
                    m.content
                  )}
                </div>
                {m.role === 'assistant' && showSources && (sourcesByMsgId[m.id]?.length ?? 0) > 0 && (
                  <div className="mt-2 inline-block max-w-[90%] rounded-md border border-gray-200 bg-white px-3 py-2 text-xs text-gray-700">
                    <div className="font-medium text-gray-900 mb-1">来源</div>
                    <div className="space-y-1">
                      {(sourcesByMsgId[m.id] ?? []).slice(0, 20).map((s) => (
                        <div key={`${s.index}-${s.postId ?? 'x'}`} className="break-words">
                          <span className="text-gray-500">[{s.index}] </span>
                          {s.title ? <span className="mr-2">{s.title}</span> : null}
                          {s.url ? (
                            <a className="text-blue-600 hover:underline" href={s.url} target="_blank" rel="noreferrer">
                              {s.url}
                            </a>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
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
          onChange={(e) => setQuestion(e.target.value)}
          rows={4}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="输入你的问题..."
          disabled={isStreaming}
        />

        <div className="flex items-center gap-2">
          <button
            type="submit"
            disabled={!canSend}
            className={
              'px-4 py-2 rounded-md text-white ' +
              (canSend ? 'bg-blue-600 hover:bg-blue-700' : 'bg-blue-300 cursor-not-allowed')
            }
          >
            {isStreaming ? '生成中...' : '发送'}
          </button>

          {isStreaming && (
            <button
              type="button"
              onClick={stopGenerating}
              className="px-4 py-2 rounded-md bg-gray-200 hover:bg-gray-300"
            >
              停止
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
