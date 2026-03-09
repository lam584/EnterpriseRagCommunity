import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import type { SpringPage } from '../../../../types/page';
import { portalSearch, type PortalSearchHitDTO } from '../../../../services/portalSearchService';

export default function DiscoverSearchPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const qFromUrl = searchParams.get('q') ?? '';

  const [q, setQ] = useState(qFromUrl);
  const [submittedQ, setSubmittedQ] = useState(qFromUrl);
  const [page, setPage] = useState<number>(1);
  const [data, setData] = useState<SpringPage<PortalSearchHitDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSearch = useMemo(() => submittedQ.trim().length > 0, [submittedQ]);
  const queryTerms = useMemo(() => extractSearchTerms(submittedQ), [submittedQ]);

  const load = useCallback(
    async (nextPage: number, queryText: string) => {
      const kw = queryText.trim();
      if (!kw) {
        setData(null);
        setError(null);
        setLoading(false);
        setPage(1);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const resp = await portalSearch(kw, nextPage, 20);
        setData(resp);
        setPage(nextPage);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setLoading(false);
      }
    },
    [setData, setPage]
  );

  useEffect(() => {
    // URL -> state 同步（例如：从首页跳转、或浏览器前进后退）
    setQ(qFromUrl);
    setSubmittedQ(qFromUrl);
    setPage(1);
  }, [qFromUrl]);

  useEffect(() => {
    if (!canSearch) return;
    load(1, submittedQ);
  }, [canSearch, submittedQ, load]);

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">搜索</h3>
        <p className="text-gray-600 text-sm">向量检索帖子、评论与附件文件</p>
      </div>

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          const next = q.trim();
          setSubmittedQ(next);
          setPage(1);
          // state -> URL 同步
          if (next) setSearchParams({ q: next }, { replace: false });
          else setSearchParams({}, { replace: false });
        }}
      >
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="搜索帖子 / 评论 / 文件..."
          className="flex-1 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          disabled={loading}
        >
          搜索
        </button>
      </form>

      {!submittedQ.trim() ? <div className="text-sm text-gray-500">输入关键词并点击搜索</div> : null}

      {error ? (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          搜索失败：{error}
          <button
            type="button"
            className="ml-3 rounded bg-red-700 px-3 py-1.5 text-white"
            onClick={() => load(page, submittedQ)}
            disabled={loading}
          >
            重试
          </button>
        </div>
      ) : null}

      {loading ? <div className="text-sm text-gray-600">加载中...</div> : null}

      {data && (data.content?.length ?? 0) === 0 && !loading && !error ? (
        <div className="text-sm text-gray-500">未找到相关结果</div>
      ) : null}

      <div className="space-y-2">
        {(data?.content ?? []).map((hit, idx) => {
          const type = (hit.type ?? '').toUpperCase();
          const isComment = type === 'COMMENT';
          const isFile = type === 'FILE';
          const title = String(hit.title ?? '').trim() || (isFile ? '命中文件' : isComment ? '命中评论' : '');
          const highlightedTitle = String(hit.highlightedTitle ?? '').trim();
          const snippet = String(hit.snippet ?? '').trim();
          const highlightedSnippet = String(hit.highlightedSnippet ?? '').trim();
          const titleNode = buildHighlightNode(highlightedTitle, title, queryTerms);
          const snippetNode = buildHighlightNode(highlightedSnippet, snippet, queryTerms);
          const when = hit.createdAt ? new Date(hit.createdAt).toLocaleString() : '';
          const score = typeof hit.score === 'number' ? hit.score : null;

          return (
            <button
              key={`${type}-${hit.postId ?? 'p'}-${hit.commentId ?? 'c'}-${hit.fileAssetId ?? 'f'}-${idx}`}
              type="button"
              className="w-full text-left rounded-lg border border-gray-200 bg-white p-3 hover:bg-gray-50"
              onClick={() => {
                const url = String(hit.url ?? '').trim();
                if (url) navigate(url, { state: { from: location } });
                else if (hit.postId) navigate(`/portal/posts/detail/${hit.postId}`, { state: { from: location } });
              }}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 min-w-0">
                    <span
                      className={`shrink-0 rounded px-2 py-0.5 text-xs ${
                        isFile
                          ? 'bg-purple-50 text-purple-700 border border-purple-200'
                          : isComment
                            ? 'bg-green-50 text-green-700 border border-green-200'
                            : 'bg-blue-50 text-blue-700 border border-blue-200'
                      }`}
                    >
                      {isFile ? '文件' : isComment ? '评论' : '帖子'}
                    </span>
                    {title ? <div className="font-medium text-gray-900 truncate">{titleNode}</div> : null}
                  </div>
                  {snippet ? <div className="mt-1 text-sm text-gray-700 break-words">{snippetNode}</div> : null}
                  <div className="mt-2 text-xs text-gray-500 flex items-center gap-3">
                    {when ? <span>{when}</span> : null}
                    {score != null ? <span>score {score.toFixed(4)}</span> : null}
                  </div>
                </div>
                <div className="shrink-0 text-xs text-gray-400">打开</div>
              </div>
            </button>
          );
        })}
      </div>

      {data ? (
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-50"
            disabled={loading || page <= 1}
            onClick={() => load(Math.max(1, page - 1), submittedQ)}
          >
            上一页
          </button>
          <div className="text-xs text-gray-500">
            第 {page} 页
          </div>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-50"
            disabled={loading || (data.last ?? false)}
            onClick={() => load(page + 1, submittedQ)}
          >
            下一页
          </button>
        </div>
      ) : null}
    </div>
  );
}

function buildHighlightNode(highlighted: string, fallback: string, queryTerms: string[]): ReactNode {
  if (!highlighted) {
    const fallbackParts = splitByTerms(fallback, queryTerms);
    if (!fallbackParts.some((part) => part.hit)) return fallback;
    return fallbackParts.map((part, idx) =>
      part.hit ? (
        <mark key={`fh-${idx}`} className="bg-yellow-200 rounded px-0.5">
          {part.text}
        </mark>
      ) : (
        <span key={`ft-${idx}`}>{part.text}</span>
      ),
    );
  }
  const src = highlighted.replace(/[\r\n\t]+/g, ' ').trim();
  if (!src) return fallback;
  const parts = splitByEmTag(src);
  if (parts.length === 0) return fallback;
  return parts.map((part, idx) =>
    part.hit ? (
      <mark key={`h-${idx}`} className="bg-yellow-200 rounded px-0.5">
        {part.text}
      </mark>
    ) : (
      <span key={`t-${idx}`}>{part.text}</span>
    ),
  );
}

function splitByEmTag(input: string): Array<{ text: string; hit: boolean }> {
  if (!input) return [];
  const parts: Array<{ text: string; hit: boolean }> = [];
  const re = /<em>([\s\S]*?)<\/em>/gi;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(input)) !== null) {
    if (m.index > last) {
      const text = input.slice(last, m.index);
      if (text) parts.push({ text, hit: false });
    }
    const hitText = m[1] ?? '';
    if (hitText) parts.push({ text: hitText, hit: true });
    last = m.index + m[0].length;
  }
  if (last < input.length) {
    const text = input.slice(last);
    if (text) parts.push({ text, hit: false });
  }
  return parts;
}

function extractSearchTerms(input: string): string[] {
  const src = String(input ?? '').trim();
  if (!src) return [];
  const matches = src.match(/[\u4e00-\u9fa5]{2,}|[a-zA-Z0-9][a-zA-Z0-9._-]{1,}/g) ?? [];
  const deduped = new Map<string, string>();
  for (const raw of matches) {
    const term = raw.trim();
    if (term.length < 2) continue;
    const key = term.toLowerCase();
    if (!deduped.has(key)) deduped.set(key, term);
  }
  return [...deduped.values()].sort((a, b) => b.length - a.length).slice(0, 8);
}

function escapeRegExp(input: string): string {
  return input.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function splitByTerms(input: string, terms: string[]): Array<{ text: string; hit: boolean }> {
  const src = String(input ?? '');
  if (!src || terms.length === 0) return [{ text: src, hit: false }];
  const pattern = terms.map(escapeRegExp).join('|');
  if (!pattern) return [{ text: src, hit: false }];
  const re = new RegExp(`(${pattern})`, 'ig');
  const parts: Array<{ text: string; hit: boolean }> = [];
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(src)) !== null) {
    if (match.index > last) {
      parts.push({ text: src.slice(last, match.index), hit: false });
    }
    parts.push({ text: match[0], hit: true });
    last = match.index + match[0].length;
  }
  if (last < src.length) {
    parts.push({ text: src.slice(last), hit: false });
  }
  return parts.length > 0 ? parts : [{ text: src, hit: false }];
}
