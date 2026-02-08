import type { PostDTO } from '../../../../services/postService';
import { formatPostTime, getPostCoverThumbUrl, getPostExcerpt } from '../../../../utils/postMeta';
import { resolveAssetUrl } from '../../../../utils/urlUtils';
import { Avatar, AvatarFallback, AvatarImage } from '../../../../components/ui/avatar';
import { useNavigate } from 'react-router-dom';

export default function PostFeedItem({ post }: { post: PostDTO }) {
  const navigate = useNavigate();
  const cover = getPostCoverThumbUrl(post);
  const authorLabel = post.authorName || (post.authorId ? `用户#${post.authorId}` : '匿名');
  const authorAvatarUrl = resolveAssetUrl(post.authorAvatarUrl);
  const timeLabel = formatPostTime(post);
  const excerpt = getPostExcerpt(post.content);

  const goAuthorProfile = () => {
    if (!post.authorId) return;
    navigate(`/portal/users/${post.authorId}`);
  };

  return (
    <article className="flex gap-3 rounded-xl border border-gray-200 bg-white p-4 hover:bg-gray-50 transition-colors">
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

        <h4 className="mt-1 text-base font-semibold text-gray-900 line-clamp-2">{post.title}</h4>
        {excerpt ? <p className="mt-1 text-sm text-gray-700 line-clamp-3">{excerpt}</p> : null}

        <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
          {typeof post.commentCount === 'number' ? <span>评论 {post.commentCount}</span> : null}
          {typeof post.reactionCount === 'number' ? <span>赞 {post.reactionCount}</span> : null}
          {post.status ? <span className="ml-auto">{post.status}</span> : <span className="ml-auto" />}
        </div>
      </div>

      {cover ? (
        <div className="shrink-0">
          <img
            src={cover}
            alt="cover"
            className="h-20 w-28 rounded-lg object-cover border border-gray-200"
            loading="lazy"
          />
        </div>
      ) : null}
    </article>
  );
}
