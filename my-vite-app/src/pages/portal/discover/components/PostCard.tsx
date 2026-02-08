import { JSX, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { PostDTO } from '../../../../services/postService';
import { togglePostFavorite, togglePostLike } from '../../../../services/postService';
import { formatPostTime, getPostCoverThumbUrl } from '../../../../utils/postMeta';
import { resolveAssetUrl } from '../../../../utils/urlUtils';
import CollapsedMarkdownPreview from './CollapsedMarkdownPreview';
import ImageLightbox from '../../../../components/ui/ImageLightbox';
import * as StatButtonModule from '../../../../components/ui/StatButton';
import HotScoreBadge from '../../../../components/post/HotScoreBadge';
import { Avatar, AvatarFallback, AvatarImage } from '../../../../components/ui/avatar';

function clamp0(n: number) {
  return n < 0 ? 0 : n;
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

  // Fallback so the list won't crash if the module shape changes.
  return (() => <span />) as StatButtonComponent;
}

// 兼容：模块可能是 default 导出，也可能是各种具名导出；这里选到一个可用的 React 组件即可
const StatButton = pickStatButton(StatButtonModule);

export type PostCardProps = {
  post: PostDTO;
  showStatus?: boolean;
  /** Optional actions area (e.g. edit/delete buttons on "My Posts" page). */
  renderActions?: (post: PostDTO) => JSX.Element | null;
};

function getPostStatusMeta(status?: PostDTO['status']): { label: string; className: string } | null {
  if (!status) return null;

  switch (status) {
    case 'DRAFT':
      return { label: '草稿', className: 'bg-gray-100 text-gray-700 border-gray-200' };
    case 'PENDING':
      return { label: '待审核', className: 'bg-amber-50 text-amber-700 border-amber-200' };
    case 'PUBLISHED':
      return { label: '已发布', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' };
    case 'REJECTED':
      return { label: '已拒绝', className: 'bg-red-50 text-red-700 border-red-200' };
    case 'ARCHIVED':
      return { label: '已归档', className: 'bg-slate-50 text-slate-700 border-slate-200' };
    default:
      return { label: String(status), className: 'bg-gray-100 text-gray-700 border-gray-200' };
  }
}

export default function PostCard({ post, showStatus, renderActions }: PostCardProps) {
  const navigate = useNavigate();

  const authorLabel = post.authorName || (post.authorId ? `用户#${post.authorId}` : '匿名');
  const authorAvatarUrl = resolveAssetUrl(post.authorAvatarUrl);
  const timeLabel = formatPostTime(post);
  const statusMeta = getPostStatusMeta(post.status);

  const cover = getPostCoverThumbUrl(post);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  const [likePending, setLikePending] = useState(false);
  const [favPending, setFavPending] = useState(false);

  const [likedByMe, setLikedByMe] = useState<boolean>(!!post.likedByMe);
  const [favoritedByMe, setFavoritedByMe] = useState<boolean>(!!post.favoritedByMe);
  const [reactionCount, setReactionCount] = useState<number>(typeof post.reactionCount === 'number' ? post.reactionCount : 0);
  const [favoriteCount, setFavoriteCount] = useState<number>(typeof post.favoriteCount === 'number' ? post.favoriteCount : 0);

  const commentCount = typeof post.commentCount === 'number' ? post.commentCount : 0;

  const goDetail = () => {
    navigate(`/portal/posts/detail/${post.id}`);
  };

  const goAuthorProfile = (e?: { stopPropagation: () => void }) => {
    if (e) e.stopPropagation();
    if (!post.authorId) return;
    navigate(`/portal/users/${post.authorId}`);
  };

  const onToggleLike = async () => {
    if (likePending) return;
    setLikePending(true);

    const nextLiked = !likedByMe;
    // optimistic
    setLikedByMe(nextLiked);
    setReactionCount((c) => clamp0(c + (nextLiked ? 1 : -1)));

    try {
      const resp = await togglePostLike(post.id);
      if (resp) {
        if (typeof resp.reactionCount === 'number') setReactionCount(resp.reactionCount);
        if (typeof resp.likedByMe === 'boolean') setLikedByMe(resp.likedByMe);
      }
    } catch {
      // revert
      setLikedByMe(!nextLiked);
      setReactionCount((c) => clamp0(c + (nextLiked ? -1 : 1)));
    } finally {
      setLikePending(false);
    }
  };

  const onToggleFavorite = async () => {
    if (favPending) return;
    setFavPending(true);

    const nextFav = !favoritedByMe;
    setFavoritedByMe(nextFav);
    setFavoriteCount((c) => clamp0(c + (nextFav ? 1 : -1)));

    try {
      const resp = await togglePostFavorite(post.id);
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

  return (
    <article
      className="border border-gray-200 bg-white p-4 sm:p-5 hover:bg-gray-50 transition-colors cursor-pointer"
      role="button"
      tabIndex={0}
      onClick={goDetail}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          goDetail();
        }
      }}
    >
      <div className="flex items-start gap-3">
        <button type="button" className="shrink-0" onClick={goAuthorProfile}>
          <Avatar className="h-10 w-10">
            <AvatarImage src={authorAvatarUrl} alt={authorLabel} />
            <AvatarFallback className="font-semibold">
              {authorLabel.slice(0, 1).toUpperCase()}
            </AvatarFallback>
          </Avatar>
        </button>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <button type="button" className="font-medium text-gray-900 truncate hover:underline" onClick={goAuthorProfile}>
              {authorLabel}
            </button>
            {timeLabel ? <span className="shrink-0">· {timeLabel}</span> : null}
            {post.boardName ? <span className="ml-auto text-gray-400 truncate">#{post.boardName}</span> : null}
          </div>

          <button type="button" className="mt-1 block text-left w-full" onClick={goDetail}>
            <div className="flex items-center justify-between gap-2">
              <h4 className="min-w-0 text-base font-semibold text-gray-900 truncate">{post.title}</h4>
              {showStatus && statusMeta ? (
                <span
                  className={`shrink-0 inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${statusMeta.className}`}
                  title={`状态：${statusMeta.label}`}
                >
                  {statusMeta.label}
                </span>
              ) : null}
            </div>
          </button>

          <div
            className="mt-3"
            onClick={(e) => {
              // 点击正文区域跳转详情，但排除链接/图片/按钮/表单控件
              const t = e.target as HTMLElement | null;
              if (!t) return;
              if (t.closest('a')) return;
              if (t.closest('img')) return;
              if (t.closest('button')) return;
              if (t.closest('input,textarea,select,label')) return;
              goDetail();
            }}
          >
            <CollapsedMarkdownPreview markdown={post.content || ''} />
          </div>

          {cover ? (
            <div className="mt-4">
              <button
                type="button"
                className="group relative block w-full overflow-hidden rounded-lg border border-gray-200 bg-gray-50"
                onClick={(e) => {
                  e.stopPropagation();
                  setLightboxSrc(cover);
                }}
                title="点击查看大图"
              >
                <img
                  src={cover}
                  alt="封面"
                  className="mx-auto h-auto w-auto max-h-[70vh] max-w-full object-contain"
                  loading="lazy"
                />
                <div className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/5 pointer-events-none" />
              </button>
            </div>
          ) : null}

          <div
            className="mt-3 flex flex-wrap items-center justify-between gap-2"
            onClick={(e) => {
              // bottom action row shouldn't trigger card navigation
              e.stopPropagation();
            }}
          >
            <div className="inline-flex w-fit max-w-full flex-wrap items-center gap-2 align-top">
              <StatButton label="评论" count={commentCount} onClick={goDetail} />

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

              <HotScoreBadge value={post.hotScore} variant="text" />
            </div>

            {renderActions ? (
              <div
                className="inline-flex shrink-0 items-center gap-3 text-sm"
                onClick={(e) => {
                  // actions area shouldn't trigger card navigation
                  e.stopPropagation();
                }}
              >
                {renderActions(post)}
              </div>
            ) : null}
          </div>
        </div>
      </div>

      <ImageLightbox open={!!lightboxSrc} src={lightboxSrc} onClose={() => setLightboxSrc(null)} />
    </article>
  );
}
