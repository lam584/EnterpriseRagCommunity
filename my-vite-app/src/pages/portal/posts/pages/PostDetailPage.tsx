import {JSX, useEffect, useMemo, useRef, useState} from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import ImageLightbox from '../../../../components/ui/ImageLightbox';
import { getPost, togglePostFavorite, togglePostLike, type PostDTO } from '../../../../services/postService';
import { formatPostTime, getPostCoverThumbUrl } from '../../../../utils/postMeta';
import * as StatButtonModule from '../../../../components/ui/StatButton';
import HotScoreBadge from '../../../../components/post/HotScoreBadge';
import { createPostComment, listPostComments, type CommentDTO } from '../../../../services/commentService';
import { listTags, type TagDTO } from '../../../../services/tagService';
import { getTranslateConfig, translateComment, translatePost, type TranslateResultDTO } from '../../../../services/translateService';
import { getMyTranslatePreferences, type TranslatePreferencesDTO } from '../../../../services/accountPreferencesService';

function clamp0(n: number) {
  return n < 0 ? 0 : n;
}

function normalizeLangCode(lang: string | null | undefined): string {
  const raw = String(lang ?? '').trim().toLowerCase();
  if (!raw) return '';
  const base = raw.split(/[-_]/)[0] ?? raw;
  if (base === 'zh') return 'zh';
  return base;
}

function extractLanguagesFromMetadata(metadata: unknown): string[] {
  if (!metadata || typeof metadata !== 'object') return [];
  const m = metadata as Record<string, unknown>;
  const v = m.languages;
  if (!Array.isArray(v)) return [];
  return v.map((x) => String(x ?? '').trim()).filter((x) => x.length > 0);
}

type StatButtonProps = {
  label: string;
  count: number;
  onClick?: () => void;
  tone?: string;
  active?: boolean;
  disabled?: boolean;
};

type StatButtonComponent = (props: StatButtonProps) => JSX.Element;

function pickStatButton(mod: unknown): StatButtonComponent {
  if (mod && typeof mod === 'object') {
    const m = mod as Record<string, unknown>;

    const candidate =
      (m.default as unknown) ??
      (m.StatButton as unknown) ??
      Object.values(m).find((v) => typeof v === 'function');

    if (typeof candidate === 'function') return candidate as StatButtonComponent;
  }

  // Fallback so the page won't crash if the module shape changes.
  return (() => <span />) as StatButtonComponent;
}

const StatButton = pickStatButton(StatButtonModule);

export default function PostDetailPage() {
  const navigate = useNavigate();
  const { postId } = useParams();

  const id = useMemo(() => {
    const n = Number(postId);
    return Number.isFinite(n) ? n : null;
  }, [postId]);

  const [post, setPost] = useState<PostDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cover = post ? getPostCoverThumbUrl(post) : undefined;
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  const [likePending, setLikePending] = useState(false);
  const [favPending, setFavPending] = useState(false);

  const [likedByMe, setLikedByMe] = useState<boolean>(false);
  const [favoritedByMe, setFavoritedByMe] = useState<boolean>(false);
  const [reactionCount, setReactionCount] = useState<number>(0);
  const [favoriteCount, setFavoriteCount] = useState<number>(0);
  const [commentCount, setCommentCount] = useState<number>(0);

  const commentsAnchorRef = useRef<HTMLDivElement | null>(null);

  const [commentsPage, setCommentsPage] = useState(1);
  const [comments, setComments] = useState<CommentDTO[]>([]);
  const [commentsTotalPages, setCommentsTotalPages] = useState<number>(0);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState<string | null>(null);

  const [newComment, setNewComment] = useState('');
  const [commentPending, setCommentPending] = useState(false);

  const [tagDict, setTagDict] = useState<TagDTO[]>([]);

  const [translateEnabled, setTranslateEnabled] = useState(false);
  const [translateConfigError, setTranslateConfigError] = useState<string | null>(null);
  const [prefs, setPrefs] = useState<TranslatePreferencesDTO>({
    targetLanguage: 'zh',
    autoTranslatePosts: false,
    autoTranslateComments: false,
  });
  const [prefsLoaded, setPrefsLoaded] = useState(false);

  const [postTranslatePending, setPostTranslatePending] = useState(false);
  const [postTranslateError, setPostTranslateError] = useState<string | null>(null);
  const [postTranslation, setPostTranslation] = useState<TranslateResultDTO | null>(null);

  const [commentTranslatePending, setCommentTranslatePending] = useState<Record<number, boolean>>({});
  const [commentTranslateErrors, setCommentTranslateErrors] = useState<Record<number, string>>({});
  const [commentTranslations, setCommentTranslations] = useState<Record<number, TranslateResultDTO>>({});

  useEffect(() => {
    if (!post) return;
    setLikedByMe(!!post.likedByMe);
    setFavoritedByMe(!!post.favoritedByMe);
    setReactionCount(typeof post.reactionCount === 'number' ? post.reactionCount : 0);
    setFavoriteCount(typeof post.favoriteCount === 'number' ? post.favoriteCount : 0);
    setCommentCount(typeof post.commentCount === 'number' ? post.commentCount : 0);
  }, [post]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const ts = await listTags({ page: 1, pageSize: 200, type: 'TOPIC', isActive: true, sortBy: 'createdAt', sortOrder: 'desc' });
        if (!mounted) return;
        setTagDict(ts);
      } catch {
        if (!mounted) return;
        setTagDict([]);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const cfg = await getTranslateConfig();
        if (!mounted) return;
        setTranslateEnabled(cfg.enabled !== false);
        setTranslateConfigError(null);
      } catch (e) {
        if (!mounted) return;
        setTranslateEnabled(false);
        setTranslateConfigError(e instanceof Error ? e.message : String(e));
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const p = await getMyTranslatePreferences();
        if (!mounted) return;
        setPrefs(p);
        setPrefsLoaded(true);
      } catch {
        if (!mounted) return;
        setPrefsLoaded(true);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const translatedPostMarkdown = useMemo(() => {
    if (!postTranslation) return '';
    const t = (postTranslation.translatedTitle ?? '').trim();
    const md = (postTranslation.translatedMarkdown ?? '').trim();
    if (t && md) return `# ${t}\n\n${md}`;
    if (t) return `# ${t}`;
    return md;
  }, [postTranslation]);

  const handleTranslatePost = async (silent?: boolean) => {
    if (!id || !translateEnabled || postTranslatePending || !prefsLoaded) return;
    const targetLang = prefs.targetLanguage || 'zh';

    setPostTranslatePending(true);
    if (!silent) setPostTranslateError(null);
    try {
      const resp = await translatePost(id, targetLang);
      setPostTranslation(resp);
    } catch (e) {
      if (!silent) setPostTranslateError(e instanceof Error ? e.message : String(e));
    } finally {
      setPostTranslatePending(false);
    }
  };

  const handleTranslateComment = async (commentId: number) => {
    if (!translateEnabled || !prefsLoaded) return;
    if (!commentId) return;
    if (commentTranslatePending[commentId]) return;
    const targetLang = prefs.targetLanguage || 'zh';

    setCommentTranslatePending((p) => ({ ...p, [commentId]: true }));
    setCommentTranslateErrors((p) => {
      const next = { ...p };
      delete next[commentId];
      return next;
    });
    try {
      const resp = await translateComment(commentId, targetLang);
      setCommentTranslations((p) => ({ ...p, [commentId]: resp }));
    } catch (e) {
      setCommentTranslateErrors((p) => ({ ...p, [commentId]: e instanceof Error ? e.message : String(e) }));
    } finally {
      setCommentTranslatePending((p) => ({ ...p, [commentId]: false }));
    }
  };

  const loadComments = async (pageToLoad: number) => {
    if (!id) return;
    setCommentsLoading(true);
    setCommentsError(null);
    try {
      const resp = await listPostComments(id, pageToLoad, 20);
      setComments(resp.content ?? []);
      setCommentsPage(pageToLoad);
      setCommentsTotalPages(resp.totalPages ?? 0);
    } catch (e) {
      setCommentsError(e instanceof Error ? e.message : String(e));
    } finally {
      setCommentsLoading(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    loadComments(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const scrollToComments = () => {
    commentsAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const shouldShowCommentPager = commentCount >= 15;

  const onToggleLike = async () => {
    if (!id || likePending) return;
    setLikePending(true);

    const nextLiked = !likedByMe;
    setLikedByMe(nextLiked);
    setReactionCount((c) => clamp0(c + (nextLiked ? 1 : -1)));

    try {
      const resp = await togglePostLike(id);
      if (resp) {
        if (typeof resp.reactionCount === 'number') setReactionCount(resp.reactionCount);
        if (typeof resp.likedByMe === 'boolean') setLikedByMe(resp.likedByMe);
      }
    } catch {
      setLikedByMe(!nextLiked);
      setReactionCount((c) => clamp0(c + (nextLiked ? -1 : 1)));
    } finally {
      setLikePending(false);
    }
  };

  const onToggleFavorite = async () => {
    if (!id || favPending) return;
    setFavPending(true);

    const nextFav = !favoritedByMe;
    setFavoritedByMe(nextFav);
    setFavoriteCount((c) => clamp0(c + (nextFav ? 1 : -1)));

    try {
      const resp = await togglePostFavorite(id);
      if (resp) {
        if (typeof resp.favoriteCount === 'number') setFavoriteCount(resp.favoriteCount);
        if (typeof resp.favoritedByMe === 'boolean') setFavoritedByMe(resp.favoritedByMe);
      }
    } catch {
      setFavoritedByMe(!nextFav);
      setFavoriteCount((c) => clamp0(c + (nextFav ? -1 : 1)));
    } finally {
      setFavPending(false);
    }
  };

  const onSubmitComment = async () => {
    if (!id || commentPending) return;
    const content = newComment.trim();
    if (!content) return;

    setCommentPending(true);
    setCommentsError(null);
    try {
      await createPostComment(id, { content });
      setNewComment('');
      // 评论默认待审核，详情页只展示已审核(VISIBLE)评论；因此这里不直接插入列表
      // 重新拉取第一页，保持 UI 与后端可见性一致
      await loadComments(1);
    } catch (e) {
      setCommentsError(e instanceof Error ? e.message : String(e));
    } finally {
      setCommentPending(false);
    }
  };

  useEffect(() => {
    if (!id) {
      setPost(null);
      setError('帖子ID无效');
      return;
    }

    let cancelled = false;

    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getPost(id);
        if (!cancelled) setPost(data ?? null);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!post) return;
    if (!translateEnabled) return;
    if (!prefsLoaded) return;
    if (!prefs.autoTranslatePosts) return;
    if (postTranslation || postTranslatePending) return;

    const postLangs = extractLanguagesFromMetadata(post.metadata);
    if (postLangs.length !== 1) return;
    const src = normalizeLangCode(postLangs[0]);
    const dst = normalizeLangCode(prefs.targetLanguage);
    if (!src || !dst) return;
    if (src === dst) return;

    void handleTranslatePost(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [post, translateEnabled, prefsLoaded, prefs.autoTranslatePosts, prefs.targetLanguage]);

  // Reserve space for the left sidebar so the fixed detail layer isn't covered.
  // Sidebar width is defined by the portal layout as a CSS variable.

  return (
    // Use a fixed, full-width page layer for the detail route so it won't shrink with parent layout.
    <div className="w-full min-w-0 space-y-4">
      <div className="flex items-start justify-between gap-3">
            <div>
              <h3 className="text-lg font-semibold">帖子详情</h3>
              <p className="text-gray-600 text-sm">只读详情页（来自后端 /api/posts/{'{id}'}）</p>
            </div>
            <button
              type="button"
              className="px-3 py-2 rounded-md border border-gray-300 bg-white hover:bg-gray-50"
              onClick={() => navigate(-1)}
            >
              返回
            </button>
          </div>

          {error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 flex-none">加载失败：{error}</div>
          ) : null}
          {loading ? <div className="text-sm text-gray-600 flex-none">加载中...</div> : null}

          {post ? (
            <>
              {/* Post content */}
              <article className="rounded-xl border border-gray-200 bg-white p-4 w-full min-w-0">
                <div className="flex items-center gap-2 text-sm text-gray-500">
                  <span className="font-medium text-gray-900">{post.authorName || (post.authorId ? `用户#${post.authorId}` : '匿名')}</span>
                  {formatPostTime(post) ? <span>· {formatPostTime(post)}</span> : null}
                  {post.boardName ? <span className="ml-auto text-gray-400">#{post.boardName}</span> : null}
                </div>

                <h1 className="mt-2 text-xl font-semibold text-gray-900">{post.title}</h1>

                {(post.tags ?? []).length ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {(post.tags ?? []).map((slugValue) => {
                      const t = tagDict.find((x) => x.slug === slugValue);
                      const label = t?.name ?? slugValue;
                      return (
                        <span
                          key={slugValue}
                          className="px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm text-gray-700"
                          title={slugValue}
                        >
                          {label}
                        </span>
                      );
                    })}
                  </div>
                ) : null}

                <div className="mt-4">
                  <MarkdownPreview markdown={post.content || ''} />
                </div>

                {cover ? (
                  <div className="mt-4">
                    <button
                      type="button"
                      className="block w-full"
                      onClick={() => setLightboxSrc(cover)}
                      title="点击查看大图"
                    >
                      <div className="w-full rounded-lg border border-gray-200 bg-gray-50 p-2">
                        <img
                          src={cover}
                          alt="cover"
                          className="max-h-[50vh] w-full object-contain rounded-md"
                          loading="lazy"
                        />
                      </div>
                    </button>
                  </div>
                ) : null}

                <div className="mt-4 flex flex-wrap gap-2 text-sm text-gray-600">
                  <StatButton label="评论" count={commentCount} onClick={scrollToComments} />

                  <StatButton
                    label="点赞"
                    count={reactionCount}
                    tone="blue"
                    active={likedByMe}
                    disabled={likePending}
                    onClick={onToggleLike}
                  />

                  <StatButton
                    label="收藏"
                    count={favoriteCount}
                    tone="amber"
                    active={favoritedByMe}
                    disabled={favPending}
                    onClick={onToggleFavorite}
                  />

                  <HotScoreBadge value={post.hotScore} variant="badge" />

                  {translateEnabled ? (
                    <button
                      type="button"
                      className="px-3 py-1.5 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50"
                      onClick={() => void handleTranslatePost(false)}
                      disabled={!prefsLoaded || postTranslatePending}
                      title="翻译标题与正文"
                    >
                      {postTranslatePending ? '翻译中...' : '翻译'}
                    </button>
                  ) : null}
                </div>
              </article>

              {translateConfigError ? (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 flex-none">
                  翻译功能不可用：{translateConfigError}
                </div>
              ) : null}

              {postTranslateError ? (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 flex-none">
                  翻译失败：{postTranslateError}
                </div>
              ) : null}

              {postTranslation ? (
                <div className="rounded-xl border border-gray-200 bg-white p-4 w-full min-w-0 space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <h4 className="text-base font-semibold text-gray-900">翻译</h4>
                    <div className="text-xs text-gray-500">
                      模型：{postTranslation.model || '（未知）'}
                      {typeof postTranslation.cached === 'boolean' ? (postTranslation.cached ? ' · 缓存' : ' · 实时') : null}
                    </div>
                  </div>
                  <MarkdownPreview markdown={translatedPostMarkdown} />
                </div>
              ) : null}

              {/* Comments: vertically stacked under the post */}
              <div
                ref={commentsAnchorRef}
                className="rounded-xl border border-gray-200 bg-white p-4 flex flex-col w-full min-w-0"
              >
                <div className="flex items-center justify-between gap-3">
                  <h4 className="text-base font-semibold text-gray-900">评论</h4>
                  <div className="text-xs text-gray-500">共 {commentCount} 条</div>
                </div>

                {commentsError ? <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{commentsError}</div> : null}

                <div className="mt-3 flex gap-2">
                  <textarea
                    className="flex-1 min-h-[90px] rounded-lg border border-gray-300 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-200"
                    placeholder="写下你的评论…"
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    disabled={commentPending}
                  />
                  <button
                    type="button"
                    className="shrink-0 h-fit px-4 py-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
                    onClick={onSubmitComment}
                    disabled={commentPending || !newComment.trim()}
                  >
                    发布
                  </button>
                </div>

                <div className="mt-3 space-y-3">
                  {commentsLoading ? <div className="text-sm text-gray-600">评论加载中...</div> : null}

                  {!commentsLoading && comments.length === 0 ? (
                    <div className="rounded-lg border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600 min-h-[120px] flex items-center justify-center">
                      暂无评论，来抢沙发吧
                    </div>
                  ) : null}

                  {comments.map((c) => (
                    <div key={c.id} className="rounded-lg border border-gray-200 p-3">
                      <div className="flex items-center justify-between gap-3 text-xs text-gray-500">
                        <div className="min-w-0">
                          <span className="font-medium text-gray-800">
                            {c.authorName || (c.authorId ? `用户#${c.authorId}` : '匿名')}
                          </span>
                          {c.createdAt ? <span className="ml-2">· {new Date(c.createdAt).toLocaleString()}</span> : null}
                        </div>
                        {translateEnabled ? (
                          <button
                            type="button"
                            className="px-2 py-1 rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50"
                            onClick={() => void handleTranslateComment(Number(c.id))}
                            disabled={!prefsLoaded || commentTranslatePending[Number(c.id)]}
                            title="翻译评论"
                          >
                            {commentTranslatePending[Number(c.id)] ? '翻译中...' : '翻译'}
                          </button>
                        ) : null}
                      </div>
                      <div className="mt-2 whitespace-pre-wrap break-words text-sm text-gray-800">{c.content}</div>

                      {commentTranslateErrors[Number(c.id)] ? (
                        <div className="mt-2 text-sm text-red-700">翻译失败：{commentTranslateErrors[Number(c.id)]}</div>
                      ) : null}

                      {commentTranslations[Number(c.id)] ? (
                        <div className="mt-2 rounded-md border border-gray-200 bg-gray-50 p-3">
                          <div className="text-xs text-gray-500 mb-2">
                            翻译内容（模型：{commentTranslations[Number(c.id)].model || '（未知）'}
                            {typeof commentTranslations[Number(c.id)].cached === 'boolean'
                              ? commentTranslations[Number(c.id)].cached
                                ? ' · 缓存'
                                : ' · 实时'
                              : null}
                            ）
                          </div>
                          <MarkdownPreview markdown={commentTranslations[Number(c.id)].translatedMarkdown || ''} />
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>

                {shouldShowCommentPager ? (
                  <div className="flex items-center justify-between pt-3 flex-none">
                    <button
                      type="button"
                      className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
                      onClick={() => loadComments(Math.max(1, commentsPage - 1))}
                      disabled={commentsLoading || commentsPage <= 1}
                    >
                      上一页
                    </button>
                    <div className="text-xs text-gray-500">
                      第 {commentsPage} 页 / 共 {commentsTotalPages || 0} 页
                    </div>
                    <button
                      type="button"
                      className="px-3 py-1 rounded-md border border-gray-300 bg-white disabled:opacity-50"
                      onClick={() => loadComments(commentsPage + 1)}
                      disabled={commentsLoading || (commentsTotalPages > 0 && commentsPage >= commentsTotalPages)}
                    >
                      下一页
                    </button>
                  </div>
                ) : null}
              </div>
            </>
          ) : null}

          <ImageLightbox open={!!lightboxSrc} src={lightboxSrc} onClose={() => setLightboxSrc(null)} />
    </div>
  );
}

