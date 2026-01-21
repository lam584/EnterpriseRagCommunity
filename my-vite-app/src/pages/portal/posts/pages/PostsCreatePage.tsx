import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams, useParams } from 'react-router-dom';
import MarkdownEditor from '../../../../components/ui/MarkdownEditor';
import { createPost, getPost, updatePost, type PostDTO } from '../../../../services/postService';
import { uploadFile, type UploadResult } from '../../../../services/uploadService';
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

function getErrorMessage(e: unknown, fallback: string) {
  if (e && typeof e === 'object' && 'message' in e) {
    const m = (e as { message?: unknown }).message;
    if (typeof m === 'string') return m;
  }
  return fallback;
}

function getFieldErrors(e: unknown): Record<string, string> | undefined {
  if (!e || typeof e !== 'object') return undefined;
  if (!('fieldErrors' in e)) return undefined;
  const fe = (e as { fieldErrors?: unknown }).fieldErrors;
  if (!fe || typeof fe !== 'object') return undefined;
  // fieldErrors is expected like: { title: '...', content: '...' }
  return fe as Record<string, string>;
}

export default function PostsCreatePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { postId: postIdParam } = useParams();

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

  const [boards, setBoards] = useState<BoardDTO[]>([]);

  const [draft, setDraft] = useState<PostDraftDTO>(() => createEmptyDraft());

  const [useAiTitle, setUseAiTitle] = useState(false);
  const [titleSuggesting, setTitleSuggesting] = useState(false);
  const [titleSuggestError, setTitleSuggestError] = useState<string | null>(null);
  const [titleCandidates, setTitleCandidates] = useState<string[]>([]);
  const [titleGenConfig, setTitleGenConfig] = useState<PostTitleGenPublicConfigDTO | null>(null);
  const [titleGenConfigError, setTitleGenConfigError] = useState<string | null>(null);
  const [titleGenCount, setTitleGenCount] = useState<number>(5);

  const isEditingDraft = useMemo(() => Boolean(draftId), [draftId]);
  // const isEditingPost = useMemo(() => postId !== null, [postId]);

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
    const load = async () => {
      setTitleGenConfigError(null);
      try {
        const cfg = await getPostTitleGenPublicConfig();
        if (!mounted) return;
        setTitleGenConfig(cfg);
        setTitleGenCount(cfg?.defaultCount ?? 5);
        if (cfg && cfg.enabled === false) {
          setUseAiTitle(false);
          setTitleCandidates([]);
          setTitleSuggestError(null);
        }
      } catch (e: unknown) {
        if (!mounted) return;
        setTitleGenConfig(null);
        setTitleGenConfigError(getErrorMessage(e, '获取标题生成配置失败'));
        setTitleGenCount(5);
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
    // When boards loaded, ensure draft.boardId is valid; otherwise default to first.
    if (!boards.length) return;
    setDraft((prev) => {
      const exists = boards.some((b) => b.id === prev.boardId);
      if (exists) return prev;
      return { ...prev, boardId: boards[0].id };
    });
  }, [boards]);

  const canPublish = draft.title.trim().length > 0 && draft.content.trim().length > 0;

  const uploadAndGetMarkdown = async (file: File, kind: 'image' | 'attachment') => {
    const uploaded: UploadResult = await uploadFile(file);
    // Keep attachments inside draft so drafts list can show them.
    setDraft((prev) => ({
      ...prev,
      attachments: [...(prev.attachments ?? []), uploaded],
      updatedAt: new Date().toISOString(),
    }));

    if (kind === 'image') {
      return `![](${uploaded.fileUrl})`;
    }
    return `[${uploaded.fileName}](${uploaded.fileUrl})`;
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
      navigate('/portal/posts/drafts', { replace: false });
    } catch (e: unknown) {
      setError(getErrorMessage(e, '保存草稿失败'));
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!canPublish) {
      setError('请先填写标题和内容后再发布。');
      return;
    }

    setPublishing(true);
    setError(null);
    try {
      if (postId !== null) {
        await updatePost(postId, {
          boardId: draft.boardId,
          title: draft.title,
          content: draft.content,
          contentFormat: 'MARKDOWN',
          tags: draft.tags,
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
        tags: draft.tags,
        attachmentIds: draft.attachments?.map((a) => a.id),
      });

      if (draftId) {
        await deleteDraft(draftId);
      }

      // 新帖默认进入“待审核”，不会出现在首页/热榜等公共区域；引导用户去“我的帖子”查看
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

  useEffect(() => {
    // Push live preview snapshot to right sidebar (PostsLayout) via localStorage.
    // Using both localStorage and a custom event so same-tab updates are instant.
    const key = 'portal.posts.compose.preview';
    try {
      localStorage.setItem(
        key,
        JSON.stringify({ markdown: draft.content ?? '', updatedAt: new Date().toISOString() })
      );
      window.dispatchEvent(new Event('posts-compose-preview-update'));
    } catch {
      // Ignore quota/security errors; editor should still work.
    }
  }, [draft.content]);

  useEffect(() => {
    // 切换草稿/帖子时，清理标题候选避免跨帖子串数据
    setTitleCandidates([]);
    setTitleSuggestError(null);
    setTitleSuggesting(false);
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

    try {
      const boardName = boards.find((b) => b.id === draft.boardId)?.name;
      const resp = await suggestPostTitles({
        content,
        count: titleGenCount,
        boardName,
        tags: draft.tags,
      });
      setTitleCandidates(Array.isArray(resp.titles) ? resp.titles : []);
      if (!resp.titles?.length) {
        setTitleSuggestError('没有生成到可用标题，请稍后重试。');
      }
    } catch (e: unknown) {
      setTitleSuggestError(getErrorMessage(e, '生成标题失败'));
    } finally {
      setTitleSuggesting(false);
    }
  }

  return (
    <div className="space-y-4">
      {/* Left: existing page content */}
      <div className="space-y-4">
        <div>
          <h3 className="text-lg font-semibold">{postId !== null ? '编辑帖子' : '发帖'}</h3>
          <p className="text-gray-600">
            使用 Markdown 编写内容。你可以插入图片与附件（先模拟上传）
            {postId !== null ? '，保存修改后会回到“我的帖子”。' : '，并保存到草稿箱后继续编辑。'}
          </p>
        </div>

        {error && (
          <div className="p-3 rounded-md border border-red-200 bg-red-50 text-red-700 text-sm">{error}</div>
        )}

        {busy ? (
          <div className="text-sm text-gray-600">正在加载{postId !== null ? '帖子' : '草稿'}...</div>
        ) : (
          <form className="space-y-3" onSubmit={(e) => e.preventDefault()}>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2">
                <div className="flex items-center justify-between gap-3">
                  <label className="block text-sm font-medium text-gray-700 mb-1">标题</label>

                  <label className="flex items-center gap-2 text-xs text-gray-600 select-none">
                    <input
                      type="checkbox"
                      checked={useAiTitle}
                      disabled={titleGenConfig?.enabled === false}
                      onChange={(e) => {
                        const v = e.target.checked;
                        setUseAiTitle(v);
                        setTitleSuggestError(null);
                        if (!v) setTitleCandidates([]);
                      }}
                    />
                    使用AI生成标题
                  </label>
                </div>

                <input
                  value={draft.title}
                  onChange={(e) => setDraft((p) => ({ ...p, title: e.target.value }))}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="输入标题..."
                />

                {useAiTitle && (
                  <div className="mt-2 space-y-2">
                    <div className="flex items-center gap-2">
                      <div className="flex items-center gap-2">
                        <div className="text-xs text-gray-600">生成数量</div>
                        <select
                          value={titleGenCount}
                          onChange={(e) => setTitleGenCount(Number(e.target.value))}
                          className="border border-gray-300 rounded-md px-2 py-1 text-xs bg-white"
                        >
                          {Array.from(
                            { length: Math.max(1, Math.min(titleGenConfig?.maxCount ?? 10, 50)) },
                            (_, i) => i + 1
                          ).map((n) => (
                            <option key={n} value={n}>
                              {n}
                            </option>
                          ))}
                        </select>
                      </div>
                      <button
                        type="button"
                        disabled={titleSuggesting}
                        onClick={() => void handleSuggestTitles()}
                        className="px-3 py-1.5 rounded-md bg-gray-100 hover:bg-gray-200 text-sm disabled:opacity-60"
                      >
                        {titleSuggesting ? '生成中...' : '生成候选标题'}
                      </button>
                      <div className="text-xs text-gray-500">根据正文内容生成 {titleGenCount} 个候选标题，点击即可填充。</div>
                    </div>

                    {titleGenConfig?.enabled === false && (
                      <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                        标题生成已被管理员关闭。
                      </div>
                    )}
                    {titleGenConfigError && (
                      <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                        {titleGenConfigError}
                      </div>
                    )}

                    {titleSuggestError && (
                      <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                        {titleSuggestError}
                      </div>
                    )}

                    {titleCandidates.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {titleCandidates.map((t, idx) => (
                          <button
                            key={`${idx}-${t}`}
                            type="button"
                            onClick={() => setDraft((p) => ({ ...p, title: t }))}
                            className="px-3 py-1.5 rounded-full border border-gray-300 bg-white hover:bg-gray-50 text-sm"
                            title="点击使用该标题"
                          >
                            {t}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">版块</label>
                <select
                  value={draft.boardId}
                  onChange={(e) => setDraft((p) => ({ ...p, boardId: Number(e.target.value) }))}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {loadingBoards && !boards.length ? (
                    <option value={draft.boardId}>加载中...</option>
                  ) : boards.length ? (
                    boards.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name} (#{b.id})
                      </option>
                    ))
                  ) : (
                    <option value={draft.boardId}>（暂无版块）</option>
                  )}
                </select>
                {!boards.length && !loadingBoards && (
                  <div className="text-xs text-gray-500 mt-1">未能加载版块列表，将使用当前 boardId：{draft.boardId}</div>
                )}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">内容（Markdown）</label>
              <MarkdownEditor
                value={{ markdown: draft.content }}
                onChange={(v) => setDraft((p) => ({ ...p, content: v.markdown }))}
                onInsertImage={(file) => uploadAndGetMarkdown(file, 'image')}
                onInsertAttachment={(file) => uploadAndGetMarkdown(file, 'attachment')}
                placeholder="写点什么..."
              />
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                disabled={publishing || saving}
                onClick={handlePublish}
                className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {publishing ? (postId !== null ? '保存中...' : '发布中...') : postId !== null ? '保存修改' : '发布'}
              </button>

              {postId === null && (
                <button
                  type="button"
                  disabled={publishing || saving}
                  onClick={handleSaveDraft}
                  className="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                >
                  {saving ? '保存中...' : isEditingDraft ? '更新草稿' : '保存草稿'}
                </button>
              )}

              {draftId && postId === null && (
                <button
                  type="button"
                  disabled={publishing || saving}
                  onClick={async () => {
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
                  disabled={publishing || saving}
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
        )}
      </div>
    </div>
  );
}
