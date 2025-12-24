import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import ImageLightbox from '../../../../components/ui/ImageLightbox';
import { getPost, togglePostFavorite, togglePostLike, type PostDTO } from '../../../../services/postService';
import { formatPostTime, getPostCoverThumbUrl } from '../../../../utils/postMeta';
import * as StatButtonModule from '../../../../components/ui/StatButton';
import { createPostComment, listPostComments, type CommentDTO } from '../../../../services/commentService';

function clamp0(n: number) {
  return n < 0 ? 0 : n;
}

const StatButton =
  (StatButtonModule as any).default ??
  (StatButtonModule as any).StatButton ??
  (Object.values(StatButtonModule as any).find((v: any) => typeof v === 'function') as any);

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

  useEffect(() => {
    if (!post) return;
    setLikedByMe(!!post.likedByMe);
    setFavoritedByMe(!!post.favoritedByMe);
    setReactionCount(typeof post.reactionCount === 'number' ? post.reactionCount : 0);
    setFavoriteCount(typeof post.favoriteCount === 'number' ? post.favoriteCount : 0);
    setCommentCount(typeof post.commentCount === 'number' ? post.commentCount : 0);
  }, [post]);

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
    try {
      const created = await createPostComment(id, { content });
      setNewComment('');
      setComments((prev) => [created, ...prev]);
      setCommentCount((c) => c + 1);
      // stay on first page to show new one
      setCommentsPage(1);
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

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
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

      {error ? <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">加载失败：{error}</div> : null}
      {loading ? <div className="text-sm text-gray-600">加载中...</div> : null}

      {post ? (
        <>
          <article className="rounded-xl border border-gray-200 bg-white p-4">
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <span className="font-medium text-gray-900">{post.authorName || (post.authorId ? `用户#${post.authorId}` : '匿名')}</span>
              {formatPostTime(post) ? <span>· {formatPostTime(post)}</span> : null}
              {post.boardName ? <span className="ml-auto text-gray-400">#{post.boardName}</span> : null}
            </div>

            <h1 className="mt-2 text-xl font-semibold text-gray-900">{post.title}</h1>

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
                      className="max-h-[70vh] w-full object-contain rounded-md"
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

              {typeof post.hotScore === 'number' ? (
                <span className="px-2 py-1 rounded-md bg-gray-50 border border-gray-200">
                  热度 {Number.isFinite(post.hotScore) ? post.hotScore.toFixed(1) : String(post.hotScore)}
                </span>
              ) : null}
            </div>
          </article>

          <div ref={commentsAnchorRef} className="rounded-xl border border-gray-200 bg-white p-4 space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h4 className="text-base font-semibold text-gray-900">评论</h4>
              <div className="text-xs text-gray-500">共 {commentCount} 条</div>
            </div>

            {commentsError ? <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{commentsError}</div> : null}

            <div className="flex gap-2">
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

            <div className="space-y-3">
              {commentsLoading ? <div className="text-sm text-gray-600">评论加载中...</div> : null}

              {!commentsLoading && comments.length === 0 ? (
                <div className="rounded-lg border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600">暂无评论，来抢沙发吧</div>
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
                  </div>
                  <div className="mt-2 whitespace-pre-wrap text-sm text-gray-800">{c.content}</div>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between pt-1">
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
          </div>
        </>
      ) : null}

      <ImageLightbox open={!!lightboxSrc} src={lightboxSrc} onClose={() => setLightboxSrc(null)} />
    </div>
  );
}

