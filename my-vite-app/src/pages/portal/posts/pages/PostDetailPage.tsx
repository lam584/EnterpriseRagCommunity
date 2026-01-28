import {JSX, useEffect, useMemo, useRef, useState} from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import ImageLightbox from '../../../../components/ui/ImageLightbox';
import { getPost, togglePostFavorite, togglePostLike, type PostDTO } from '../../../../services/postService';
import { getPostAiSummary, type PostAiSummaryDTO } from '../../../../services/aiPostSummaryService';
import { formatPostTime, getPostCoverThumbUrl } from '../../../../utils/postMeta';
import * as StatButtonModule from '../../../../components/ui/StatButton';
import HotScoreBadge from '../../../../components/post/HotScoreBadge';
import { createPostComment, listPostComments, toggleCommentLike, type CommentDTO } from '../../../../services/commentService';
import { listTags, type TagDTO } from '../../../../services/tagService';
import { getTranslateConfig, translateComment, translatePost, type TranslateResultDTO } from '../../../../services/translateService';
import { getMyTranslatePreferences, type TranslatePreferencesDTO } from '../../../../services/accountPreferencesService';
import { listSupportedLanguages, type SupportedLanguageDTO } from '../../../../services/supportedLanguagesService';
import { reportComment, reportPost } from '../../../../services/reportService';
import { extractLanguagesFromMetadata, normalizeLangBase, normalizeTargetLanguageBase } from '../../../../utils/langUtils';

function clamp0(n: number) {
  return n < 0 ? 0 : n;
}

type CommentSortMode = 'HOT' | 'TIME';

type CommentNode = {
  id: number;
  parentId: number | null;
  rootId: number;
  depth: number;
  descendantCount: number;
  hotScore: number;
  comment: CommentDTO;
  children: CommentNode[];
};

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

  const [aiSummary, setAiSummary] = useState<PostAiSummaryDTO | null>(null);
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false);
  const [aiSummaryError, setAiSummaryError] = useState<string | null>(null);

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

  const [comments, setComments] = useState<CommentDTO[]>([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState<string | null>(null);

  const [commentSortMode, setCommentSortMode] = useState<CommentSortMode>('TIME');
  const [expandedRootComments, setExpandedRootComments] = useState<Record<number, boolean>>({});
  const [commentLikePending, setCommentLikePending] = useState<Record<number, boolean>>({});

  const [replyTargetId, setReplyTargetId] = useState<number | null>(null);
  const [replyDraft, setReplyDraft] = useState('');
  const [replyPending, setReplyPending] = useState(false);

  const [threadModalOpen, setThreadModalOpen] = useState(false);
  const [threadModalRootId, setThreadModalRootId] = useState<number | null>(null);
  const [threadSortMode, setThreadSortMode] = useState<CommentSortMode>('TIME');

  const [newComment, setNewComment] = useState('');
  const [commentPending, setCommentPending] = useState(false);

  const [tagDict, setTagDict] = useState<TagDTO[]>([]);

  const [translateEnabled, setTranslateEnabled] = useState(false);
  const [translateConfigError, setTranslateConfigError] = useState<string | null>(null);
  const [supportedLanguages, setSupportedLanguages] = useState<SupportedLanguageDTO[]>([]);
  const [prefs, setPrefs] = useState<TranslatePreferencesDTO>({
    targetLanguage: 'zh-CN',
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

  const [reportOpen, setReportOpen] = useState(false);
  const [reportReasonCode, setReportReasonCode] = useState('SPAM');
  const [reportReasonText, setReportReasonText] = useState('');
  const [reportPending, setReportPending] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [reportDone, setReportDone] = useState(false);

  const [commentReportOpen, setCommentReportOpen] = useState(false);
  const [commentReportTargetId, setCommentReportTargetId] = useState<number | null>(null);
  const [commentReportReasonCode, setCommentReportReasonCode] = useState('SPAM');
  const [commentReportReasonText, setCommentReportReasonText] = useState('');
  const [commentReportPending, setCommentReportPending] = useState(false);
  const [commentReportError, setCommentReportError] = useState<string | null>(null);

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
        const langs = await listSupportedLanguages();
        if (!mounted) return;
        setSupportedLanguages((langs ?? []).filter((x) => x && typeof x.languageCode === 'string' && typeof x.displayName === 'string'));
      } catch {
        if (!mounted) return;
        setSupportedLanguages([]);
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
    const targetLangCode = prefs.targetLanguage || 'zh-CN';
    const targetLang =
      supportedLanguages.find((x) => x.languageCode === targetLangCode)?.displayName || targetLangCode;

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
    const targetLangCode = prefs.targetLanguage || 'zh-CN';
    const targetLang =
      supportedLanguages.find((x) => x.languageCode === targetLangCode)?.displayName || targetLangCode;

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

  const loadAllComments = async () => {
    if (!id) return;
    setCommentsLoading(true);
    setCommentsError(null);
    try {
      const first = await listPostComments(id, 1, 200, true);
      let all = first.content ?? [];
      const totalPages = first.totalPages ?? 0;
      setComments(all);
      if (totalPages > 1) {
        for (let p = 2; p <= totalPages; p += 1) {
          const resp = await listPostComments(id, p, 200, true);
          all = all.concat(resp.content ?? []);
          setComments(all);
        }
      }
    } catch (e) {
      setCommentsError(e instanceof Error ? e.message : String(e));
    } finally {
      setCommentsLoading(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    void loadAllComments();
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
    setCommentsError(null);
    try {
      const created = await createPostComment(id, { content });
      setNewComment('');
      setComments((prev) => (created?.id ? [created, ...prev.filter((c) => Number(c.id) !== Number(created.id))] : prev));
      await loadAllComments();
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
    if (!id) {
      setAiSummary(null);
      setAiSummaryError(null);
      setAiSummaryLoading(false);
      return;
    }

    let cancelled = false;

    (async () => {
      setAiSummaryLoading(true);
      setAiSummaryError(null);
      try {
        const s = await getPostAiSummary(id);
        if (!cancelled) setAiSummary(s ?? null);
      } catch (e) {
        if (!cancelled) {
          setAiSummary(null);
          setAiSummaryError(e instanceof Error ? e.message : String(e));
        }
      } finally {
        if (!cancelled) setAiSummaryLoading(false);
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
    const src = normalizeLangBase(postLangs[0]);
    const dst = normalizeTargetLanguageBase(prefs.targetLanguage || 'zh-CN');
    if (!src || !dst) return;
    if (src === dst) return;

    void handleTranslatePost(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [post, translateEnabled, prefsLoaded, prefs.autoTranslatePosts, prefs.targetLanguage]);

  const shouldShowPostTranslateButton = useMemo(() => {
    if (!translateEnabled || !prefsLoaded || !post) return false;
    const postLangs = extractLanguagesFromMetadata(post.metadata);
    if (postLangs.length !== 1) return true;
    const src = normalizeLangBase(postLangs[0]);
    const dst = normalizeTargetLanguageBase(prefs.targetLanguage || 'zh-CN');
    if (!src || !dst) return true;
    return src !== dst;
  }, [translateEnabled, prefsLoaded, post, prefs.targetLanguage]);

  const shouldShowCommentTranslateButton = (c: CommentDTO): boolean => {
    if (!translateEnabled || !prefsLoaded) return false;
    const langs = extractLanguagesFromMetadata(c.metadata);
    if (langs.length !== 1) return true;
    const src = normalizeLangBase(langs[0]);
    const dst = normalizeTargetLanguageBase(prefs.targetLanguage || 'zh-CN');
    if (!src || !dst) return true;
    return src !== dst;
  };

  const onSubmitReport = async () => {
    if (!id || reportPending || reportDone) return;
    const reasonCode = reportReasonCode.trim();
    if (!reasonCode) {
      setReportError('请选择举报原因');
      return;
    }
    setReportPending(true);
    setReportError(null);
    try {
      await reportPost(id, { reasonCode, reasonText: reportReasonText.trim() || undefined });
      setReportDone(true);
      setReportOpen(false);
    } catch (e) {
      setReportError(e instanceof Error ? e.message : String(e));
    } finally {
      setReportPending(false);
    }
  };

  const onSubmitCommentReport = async () => {
    if (!commentReportTargetId || commentReportPending) return;
    const reasonCode = commentReportReasonCode.trim();
    if (!reasonCode) {
      setCommentReportError('请选择举报原因');
      return;
    }
    setCommentReportPending(true);
    setCommentReportError(null);
    try {
      await reportComment(commentReportTargetId, { reasonCode, reasonText: commentReportReasonText.trim() || undefined });
      setCommentReportOpen(false);
      setCommentReportTargetId(null);
    } catch (e) {
      setCommentReportError(e instanceof Error ? e.message : String(e));
    } finally {
      setCommentReportPending(false);
    }
  };

  const getCommentTimeMs = (c: CommentDTO): number => {
    if (!c.createdAt) return 0;
    const t = new Date(c.createdAt).getTime();
    return Number.isFinite(t) ? t : 0;
  };

  const getCommentDisplayName = (c: CommentDTO): string => {
    const name = String(c.authorName ?? '').trim();
    if (name) return name;
    if (c.authorId) return `用户#${c.authorId}`;
    return '匿名';
  };

  const getAvatarLabel = (c: CommentDTO): string => {
    const n = getCommentDisplayName(c).trim();
    return n ? n.slice(0, 1).toUpperCase() : '?';
  };

  const commentNodes = useMemo(() => {
    const byId = new Map<number, CommentNode>();
    for (const c of comments) {
      const cid = Number(c.id);
      if (!Number.isFinite(cid)) continue;
      const parentId = c.parentId == null ? null : Number(c.parentId);
      byId.set(cid, {
        id: cid,
        parentId: Number.isFinite(parentId as number) ? (parentId as number) : null,
        rootId: cid,
        depth: 0,
        descendantCount: 0,
        hotScore: 0,
        comment: c,
        children: [],
      });
    }

    for (const node of byId.values()) {
      if (node.parentId == null) continue;
      const parent = byId.get(node.parentId);
      if (parent) parent.children.push(node);
    }

    const roots: CommentNode[] = [];
    for (const node of byId.values()) {
      if (node.parentId == null || !byId.has(node.parentId)) roots.push(node);
    }

    const setDepthAndRoot = (node: CommentNode, depth: number, rootId: number) => {
      node.depth = depth;
      node.rootId = rootId;
      for (const child of node.children) setDepthAndRoot(child, depth + 1, rootId);
    };
    for (const r of roots) setDepthAndRoot(r, 0, r.id);

    const countDesc = (node: CommentNode): number => {
      let cnt = 0;
      for (const child of node.children) cnt += 1 + countDesc(child);
      node.descendantCount = cnt;
      return cnt;
    };
    for (const r of roots) countDesc(r);

    for (const node of byId.values()) {
      const likeCount = typeof node.comment.likeCount === 'number' ? node.comment.likeCount : 0;
      node.hotScore = clamp0(likeCount) + node.descendantCount;
    }

    return { byId, roots };
  }, [comments]);

  const compareNodes = (a: CommentNode, b: CommentNode, mode: CommentSortMode) => {
    if (mode === 'HOT') {
      const d = (b.hotScore || 0) - (a.hotScore || 0);
      if (d !== 0) return d;
    }
    const dt = getCommentTimeMs(b.comment) - getCommentTimeMs(a.comment);
    if (dt !== 0) return dt;
    return b.id - a.id;
  };

  const sortNodes = (nodes: CommentNode[], mode: CommentSortMode) => nodes.slice().sort((a, b) => compareNodes(a, b, mode));

  const sortedRootNodes = useMemo(() => sortNodes(commentNodes.roots, commentSortMode), [commentNodes.roots, commentSortMode]);
  const threadRootNode = useMemo(
    () => (threadModalRootId == null ? null : commentNodes.byId.get(threadModalRootId) ?? null),
    [commentNodes.byId, threadModalRootId],
  );

  const handleToggleCommentLike = async (commentId: number) => {
    if (!commentId) return;
    if (commentLikePending[commentId]) return;
    setCommentLikePending((p) => ({ ...p, [commentId]: true }));
    try {
      const resp = await toggleCommentLike(commentId);
      setComments((prev) =>
        prev.map((c) =>
          Number(c.id) === commentId
            ? {
                ...c,
                likedByMe: resp.likedByMe,
                likeCount: resp.likeCount,
              }
            : c,
        ),
      );
    } catch (e) {
      setCommentsError(e instanceof Error ? e.message : String(e));
    } finally {
      setCommentLikePending((p) => ({ ...p, [commentId]: false }));
    }
  };

  const openReplyEditor = (commentId: number) => {
    setReplyTargetId(commentId);
    setReplyDraft('');
  };

  const submitReply = async (commentId: number) => {
    if (!id) return;
    if (replyPending) return;
    const content = replyDraft.trim();
    if (!content) return;
    setReplyPending(true);
    setCommentsError(null);
    try {
      const created = await createPostComment(id, { content, parentId: commentId });
      setReplyTargetId(null);
      setReplyDraft('');
      setComments((prev) => (created?.id ? [created, ...prev.filter((c) => Number(c.id) !== Number(created.id))] : prev));
      await loadAllComments();
    } catch (e) {
      setCommentsError(e instanceof Error ? e.message : String(e));
    } finally {
      setReplyPending(false);
    }
  };

  const openThreadModal = (rootId: number) => {
    setThreadModalRootId(rootId);
    setThreadSortMode('TIME');
    setThreadModalOpen(true);
  };

  const closeThreadModal = () => {
    setThreadModalOpen(false);
    setThreadModalRootId(null);
  };

  const getCommentIndentPx = (nodeDepth: number, rootDepth: number) => {
    const relativeDepth = Math.max(0, nodeDepth - rootDepth);
    const clamped = Math.min(relativeDepth, 3);
    if (clamped <= 0) return 0;
    return clamped * 12;
  };

  const renderCommentCard = (node: CommentNode, mode: CommentSortMode, rootDepth: number, renderChildren = true) => {
    const c = node.comment;
    const displayName = getCommentDisplayName(c);
    const avatarUrl = String(c.authorAvatarUrl ?? '').trim();
    const location = String(c.authorLocation ?? '').trim();
    const timeLabel = c.createdAt ? new Date(c.createdAt).toLocaleString() : '';
    const liked = !!c.likedByMe;
    const likeCount = typeof c.likeCount === 'number' ? c.likeCount : 0;
    const relativeDepth = Math.max(0, node.depth - rootDepth);
    const indent = getCommentIndentPx(node.depth, rootDepth);
    const children = sortNodes(node.children, mode);
    const rootReplyCount = commentNodes.byId.get(node.rootId)?.descendantCount ?? 0;
    const threadActive = threadModalOpen && threadModalRootId === node.rootId;
    const shouldShowThreadToggle = node.rootId !== node.id && rootReplyCount >= 2;
    const parentNode = node.parentId == null ? null : commentNodes.byId.get(node.parentId) ?? null;
    const replyToName = relativeDepth >= 3 && parentNode ? getCommentDisplayName(parentNode.comment) : '';

    return (
      <div key={node.id} style={indent ? { marginLeft: indent } : undefined} className="space-y-2">
        <div className="rounded-lg border border-gray-200 bg-white p-3">
          <div className="flex items-start gap-2 min-w-0">
            {avatarUrl ? (
              <img src={avatarUrl} alt={displayName} className="h-8 w-8 rounded-full object-cover border border-gray-200 shrink-0" />
            ) : (
              <div className="h-8 w-8 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center text-sm text-gray-700 shrink-0">
                {getAvatarLabel(c)}
              </div>
            )}

            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap text-sm">
                    <span className="font-medium text-gray-900 truncate">{displayName}</span>
                    {replyToName ? (
                      <span className="text-xs text-gray-500">
                        回复 <span className="font-medium text-gray-700">@{replyToName}</span>：
                      </span>
                    ) : null}
                    {c.status === 'PENDING' ? (
                      <span className="inline-flex px-2 py-0.5 rounded-full text-xs border border-amber-200 bg-amber-50 text-amber-900">
                        待审核（仅你可见）
                      </span>
                    ) : null}
                    {location ? <span className="text-xs text-gray-500">{location}</span> : null}
                    {timeLabel ? <span className="text-xs text-gray-500">· {timeLabel}</span> : null}
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 flex-wrap justify-end">
                  <button
                    type="button"
                    className={`px-2 py-1 rounded border text-xs disabled:opacity-50 ${
                      liked ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                    }`}
                    onClick={() => void handleToggleCommentLike(node.id)}
                    disabled={!!commentLikePending[node.id]}
                    title="点赞"
                  >
                    {liked ? '已赞' : '点赞'} {likeCount > 0 ? likeCount : ''}
                  </button>
                  <button
                    type="button"
                    className="px-2 py-1 rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50 text-xs"
                    onClick={() => openReplyEditor(node.id)}
                    disabled={replyPending}
                    title="回复"
                  >
                    回复
                  </button>
                  {shouldShowThreadToggle ? (
                    <button
                      type="button"
                      className={`px-2 py-1 rounded border text-xs ${
                        threadActive ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                      }`}
                      onClick={() => (threadActive ? closeThreadModal() : openThreadModal(node.rootId))}
                    >
                      {threadActive ? '收起对话' : '查看全部对话'}
                    </button>
                  ) : null}
              {shouldShowCommentTranslateButton(c) ? (
                    <button
                      type="button"
                      className="px-2 py-1 rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50 text-xs"
                      onClick={() => void handleTranslateComment(node.id)}
                      disabled={!prefsLoaded || commentTranslatePending[node.id]}
                      title="翻译评论"
                    >
                      {commentTranslatePending[node.id] ? '翻译中...' : '翻译'}
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="px-2 py-1 rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50 text-xs"
                    onClick={() => {
                      setCommentReportTargetId(node.id);
                      setCommentReportReasonCode('SPAM');
                      setCommentReportReasonText('');
                      setCommentReportError(null);
                      setCommentReportOpen(true);
                    }}
                    disabled={commentReportPending}
                    title="举报该评论"
                  >
                    举报
                  </button>
                </div>
              </div>

              <div className="mt-1 whitespace-pre-wrap break-words text-sm text-gray-800">{c.content}</div>

              {replyTargetId === node.id ? (
                <div className="mt-3 space-y-2">
                  <textarea
                    className="w-full min-h-[80px] rounded-lg border border-gray-300 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-200"
                    placeholder={`回复 @${displayName}…`}
                    value={replyDraft}
                    onChange={(e) => setReplyDraft(e.target.value)}
                    disabled={replyPending}
                  />
                  <div className="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      className="px-3 py-1 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50 text-sm"
                      onClick={() => setReplyTargetId(null)}
                      disabled={replyPending}
                    >
                      取消
                    </button>
                    <button
                      type="button"
                      className="px-3 py-1 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 text-sm"
                      onClick={() => void submitReply(node.id)}
                      disabled={replyPending || !replyDraft.trim()}
                    >
                      {replyPending ? '提交中...' : '提交回复'}
                    </button>
                  </div>
                </div>
              ) : null}

              {commentTranslateErrors[node.id] ? <div className="mt-2 text-sm text-red-700">翻译失败：{commentTranslateErrors[node.id]}</div> : null}

              {commentTranslations[node.id] ? (
                <div className="mt-2 rounded-md border border-gray-200 bg-gray-50 p-3">
                  <div className="text-xs text-gray-500 mb-2">
                    翻译内容（模型：{commentTranslations[node.id].model || '（未知）'}
                    {typeof commentTranslations[node.id].cached === 'boolean'
                      ? commentTranslations[node.id].cached
                        ? ' · 缓存'
                        : ' · 实时'
                      : null}
                    ）
                  </div>
                  <MarkdownPreview markdown={commentTranslations[node.id].translatedMarkdown || ''} />
                </div>
              ) : null}
            </div>
          </div>
        </div>

        {renderChildren && children.length > 0 ? <div className="space-y-2">{children.map((x) => renderCommentCard(x, mode, rootDepth))}</div> : null}
      </div>
    );
  };

  // Reserve space for the left sidebar so the fixed detail layer isn't covered.
  // Sidebar width is defined by the portal layout as a CSS variable.

  return (
    // Use a fixed, full-width page layer for the detail route so it won't shrink with parent layout.
    <div className="w-full min-w-0 space-y-4">
      <div className="flex items-start justify-between gap-3">
            <div>
              <h3 className="text-lg font-semibold">帖子详情</h3>
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
            <div className="space-y-4 w-full min-w-0">
              <div className="rounded-xl border border-gray-200 bg-white p-4 min-w-0">
                <div className="flex items-center justify-between gap-3">
                  <h4 className="text-base font-semibold text-gray-900">AI摘要</h4>
                  {aiSummary?.enabled && aiSummary?.model ? <div className="text-xs text-gray-500">模型：{aiSummary.model}</div> : null}
                </div>

                {aiSummaryLoading ? <div className="mt-3 text-sm text-gray-600">加载中...</div> : null}
                {aiSummaryError ? <div className="mt-3 text-sm text-red-700">加载失败：{aiSummaryError}</div> : null}

                {!aiSummaryLoading && !aiSummaryError ? (
                  <>
                    {!aiSummary ? (
                      <div className="mt-3 text-sm text-gray-600">暂无摘要信息</div>
                    ) : !aiSummary.enabled || String(aiSummary.status || '').toUpperCase() === 'DISABLED' ? (
                      <div className="mt-3 text-sm text-gray-600">AI 摘要功能未开启</div>
                    ) : String(aiSummary.status || '').toUpperCase() === 'SUCCESS' && (aiSummary.summaryText ?? '').trim() ? (
                      <div className="mt-3 space-y-2">
                        {aiSummary.summaryTitle ? <div className="text-sm text-gray-900 font-medium">{aiSummary.summaryTitle}</div> : null}
                        <div className="text-sm text-gray-700 whitespace-pre-wrap break-words">{aiSummary.summaryText}</div>
                        <div className="text-xs text-gray-500">
                          {aiSummary.generatedAt ? `生成时间：${new Date(aiSummary.generatedAt).toLocaleString()}` : null}
                          {aiSummary.latencyMs ? ` · 耗时：${aiSummary.latencyMs}ms` : null}
                        </div>
                      </div>
                    ) : String(aiSummary.status || '').toUpperCase() === 'FAILED' ? (
                      <div className="mt-3 space-y-1">
                        <div className="text-sm text-gray-700">摘要生成失败</div>
                        <div className="text-xs text-gray-500">请稍后再试</div>
                      </div>
                    ) : (
                      <div className="mt-3 space-y-1">
                        <div className="text-sm text-gray-700">摘要尚未生成</div>
                        <div className="text-xs text-gray-500">系统会在发帖后自动生成摘要</div>
                      </div>
                    )}
                  </>
                ) : null}
              </div>

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

                  {shouldShowPostTranslateButton ? (
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

                  <button
                    type="button"
                    className="px-3 py-1.5 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50"
                    onClick={() => {
                      setReportReasonCode('SPAM');
                      setReportReasonText('');
                      setReportError(null);
                      setReportOpen(true);
                    }}
                    disabled={reportDone}
                    title="举报该帖子"
                  >
                    {reportDone ? '已举报' : '举报'}
                  </button>
                </div>
              </article>

              {reportDone ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900 flex-none">
                  已提交举报，处理进度与结果请在「互动-举报」查看
                </div>
              ) : null}

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
                <div className="flex items-center justify-between gap-3 flex-wrap">
                  <h4 className="text-base font-semibold text-gray-900">评论</h4>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      className={`px-2 py-1 rounded border text-xs ${
                        commentSortMode === 'HOT' ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                      }`}
                      onClick={() => setCommentSortMode('HOT')}
                    >
                      按热度
                    </button>
                    <button
                      type="button"
                      className={`px-2 py-1 rounded border text-xs ${
                        commentSortMode === 'TIME' ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                      }`}
                      onClick={() => setCommentSortMode('TIME')}
                    >
                      按时间
                    </button>
                    <div className="text-xs text-gray-500 ml-2">共 {commentCount} 条</div>
                  </div>
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

                  {!commentsLoading && sortedRootNodes.length === 0 ? (
                    <div className="rounded-lg border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-600 min-h-[120px] flex items-center justify-center">
                      暂无评论，来抢沙发吧
                    </div>
                  ) : null}

                  {sortedRootNodes.map((root) => {
                    const replyCount = root.descendantCount;
                    const expanded = !!expandedRootComments[root.id] || replyCount <= 3;
                    const shouldShowReplyCount = replyCount > 3;
                    const directChildren = sortNodes(root.children, commentSortMode);
                    const childrenToShow = expanded ? directChildren : directChildren.slice(0, 3);
                    const shouldRenderChildren = expanded || replyCount <= 3;
                    const expandInsertIndex = Math.max(0, Math.min(2, childrenToShow.length - 1));

                    return (
                      <div key={root.id} className="space-y-2">
                        {renderCommentCard(root, commentSortMode, root.depth, false)}

                        {childrenToShow.length > 0 ? (
                          <div className="mt-2 rounded-lg border border-gray-200 bg-gray-50 p-3 space-y-2">
                            {childrenToShow.map((child, idx) => (
                              <div key={child.id} className="space-y-2">
                                {renderCommentCard(child, commentSortMode, root.depth, shouldRenderChildren)}
                                {shouldShowReplyCount && !expanded && idx === expandInsertIndex ? (
                                  <div className="flex justify-end">
                                    <button
                                      type="button"
                                      className="text-xs text-blue-700 hover:underline"
                                      onClick={() => setExpandedRootComments((p) => ({ ...p, [root.id]: true }))}
                                    >
                                      共 {replyCount} 条回复 · 展开
                                    </button>
                                  </div>
                                ) : null}
                              </div>
                            ))}

                            {shouldShowReplyCount && expanded ? (
                              <div className="flex justify-end pt-1">
                                <button
                                  type="button"
                                  className="text-xs text-blue-700 hover:underline"
                                  onClick={() => setExpandedRootComments((p) => ({ ...p, [root.id]: false }))}
                                >
                                  共 {replyCount} 条回复 · 收起
                                </button>
                              </div>
                            ) : null}
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          ) : null}

          <ImageLightbox open={!!lightboxSrc} src={lightboxSrc} onClose={() => setLightboxSrc(null)} />

          {threadModalOpen && threadRootNode ? (
            <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[90vh] overflow-hidden">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                  <div className="font-semibold">全部对话</div>
                  <button
                    type="button"
                    className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-50"
                    onClick={closeThreadModal}
                  >
                    关闭
                  </button>
                </div>
                <div className="p-4 space-y-3 overflow-auto max-h-[calc(90vh-56px)]">
                  <div className="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      className={`px-2 py-1 rounded border text-xs ${
                        threadSortMode === 'HOT' ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                      }`}
                      onClick={() => setThreadSortMode('HOT')}
                    >
                      按热度
                    </button>
                    <button
                      type="button"
                      className={`px-2 py-1 rounded border text-xs ${
                        threadSortMode === 'TIME' ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-300 bg-white hover:bg-gray-50'
                      }`}
                      onClick={() => setThreadSortMode('TIME')}
                    >
                      按时间
                    </button>
                  </div>
                  {renderCommentCard(threadRootNode, threadSortMode, threadRootNode.depth, true)}
                </div>
              </div>
            </div>
          ) : null}

          {reportOpen ? (
            <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-lg w-full max-w-lg">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                  <div className="font-semibold">举报帖子</div>
                  <button
                    type="button"
                    className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-50"
                    onClick={() => setReportOpen(false)}
                    disabled={reportPending}
                  >
                    关闭
                  </button>
                </div>

                <div className="p-4 space-y-3">
                  {reportError ? (
                    <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{reportError}</div>
                  ) : null}

                  <div className="space-y-1">
                    <div className="text-sm text-gray-700">举报原因</div>
                    <select
                      className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                      value={reportReasonCode}
                      onChange={(e) => setReportReasonCode(e.target.value)}
                      disabled={reportPending}
                    >
                      <option value="SPAM">垃圾/广告</option>
                      <option value="ABUSE">辱骂/骚扰</option>
                      <option value="HATE">仇恨/歧视</option>
                      <option value="PORN">色情/低俗</option>
                      <option value="ILLEGAL">违法违规</option>
                      <option value="OTHER">其他</option>
                    </select>
                  </div>

                  <div className="space-y-1">
                    <div className="text-sm text-gray-700">补充说明（可选）</div>
                    <textarea
                      className="w-full min-h-[90px] rounded border border-gray-300 px-3 py-2 text-sm"
                      value={reportReasonText}
                      onChange={(e) => setReportReasonText(e.target.value)}
                      disabled={reportPending}
                      placeholder="请描述举报原因，便于审核"
                    />
                  </div>

                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-50"
                      onClick={() => setReportOpen(false)}
                      disabled={reportPending}
                    >
                      取消
                    </button>
                    <button
                      type="button"
                      className="rounded bg-red-600 text-white px-4 py-2 hover:bg-red-700 disabled:opacity-50"
                      onClick={onSubmitReport}
                      disabled={reportPending}
                    >
                      {reportPending ? '提交中...' : '提交举报'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : null}

          {commentReportOpen ? (
            <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-lg w-full max-w-lg">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                  <div className="font-semibold">举报评论</div>
                  <button
                    type="button"
                    className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-50"
                    onClick={() => setCommentReportOpen(false)}
                    disabled={commentReportPending}
                  >
                    关闭
                  </button>
                </div>

                <div className="p-4 space-y-3">
                  {commentReportError ? (
                    <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{commentReportError}</div>
                  ) : null}

                  <div className="space-y-1">
                    <div className="text-sm text-gray-700">举报原因</div>
                    <select
                      className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                      value={commentReportReasonCode}
                      onChange={(e) => setCommentReportReasonCode(e.target.value)}
                      disabled={commentReportPending}
                    >
                      <option value="SPAM">垃圾/广告</option>
                      <option value="ABUSE">辱骂/骚扰</option>
                      <option value="HATE">仇恨/歧视</option>
                      <option value="PORN">色情/低俗</option>
                      <option value="ILLEGAL">违法违规</option>
                      <option value="OTHER">其他</option>
                    </select>
                  </div>

                  <div className="space-y-1">
                    <div className="text-sm text-gray-700">补充说明（可选）</div>
                    <textarea
                      className="w-full min-h-[90px] rounded border border-gray-300 px-3 py-2 text-sm"
                      value={commentReportReasonText}
                      onChange={(e) => setCommentReportReasonText(e.target.value)}
                      disabled={commentReportPending}
                      placeholder="请描述举报原因，便于审核"
                    />
                  </div>

                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-50"
                      onClick={() => setCommentReportOpen(false)}
                      disabled={commentReportPending}
                    >
                      取消
                    </button>
                    <button
                      type="button"
                      className="rounded bg-red-600 text-white px-4 py-2 hover:bg-red-700 disabled:opacity-50"
                      onClick={onSubmitCommentReport}
                      disabled={commentReportPending}
                    >
                      {commentReportPending ? '提交中...' : '提交举报'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : null}
    </div>
  );
}

