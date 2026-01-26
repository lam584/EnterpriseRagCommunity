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
import { createTag, listTags, slugify, type TagDTO } from '../../../../services/tagService';
import { suggestPostTags } from '../../../../services/aiTagService';
import { getPostTagGenPublicConfig, type PostTagGenPublicConfigDTO } from '../../../../services/tagGenPublicService';
import { getLangLabelGenConfig, suggestPostLangLabels, type LangLabelGenPublicConfigDTO } from '../../../../services/aiLangLabelService';

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

  const [availableTags, setAvailableTags] = useState<TagDTO[]>([]);
  const [loadingTags, setLoadingTags] = useState(false);
  const [tagsError, setTagsError] = useState<string | null>(null);
  const [tagQuery, setTagQuery] = useState('');

  const [useAiTags, setUseAiTags] = useState(true);
  const [tagSuggesting, setTagSuggesting] = useState(false);
  const [tagSuggestError, setTagSuggestError] = useState<string | null>(null);
  const [tagCandidates, setTagCandidates] = useState<string[]>([]);
  const [tagGenConfig, setTagGenConfig] = useState<PostTagGenPublicConfigDTO | null>(null);
  const [tagGenConfigError, setTagGenConfigError] = useState<string | null>(null);
  const [tagGenCount, setTagGenCount] = useState<number>(5);

  const [langLabelGenConfig, setLangLabelGenConfig] = useState<LangLabelGenPublicConfigDTO | null>(null);
  const [langLabelGenConfigError, setLangLabelGenConfigError] = useState<string | null>(null);
  const [basePostMetadata, setBasePostMetadata] = useState<Record<string, unknown> | null>(null);

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
    const loadTags = async () => {
      setLoadingTags(true);
      setTagsError(null);
      try {
        const ts = await listTags({ page: 1, pageSize: 200, type: 'TOPIC', isActive: true, sortBy: 'createdAt', sortOrder: 'desc' });
        if (!mounted) return;
        setAvailableTags(ts);
      } catch (e: unknown) {
        if (!mounted) return;
        setAvailableTags([]);
        setTagsError(getErrorMessage(e, '加载标签列表失败'));
      } finally {
        if (mounted) setLoadingTags(false);
      }
    };
    void loadTags();
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
    let mounted = true;
    const load = async () => {
      setTagGenConfigError(null);
      try {
        const cfg = await getPostTagGenPublicConfig();
        if (!mounted) return;
        setTagGenConfig(cfg);
        setTagGenCount(cfg?.defaultCount ?? 5);
        if (cfg && cfg.enabled === false) {
          setUseAiTags(false);
          setTagCandidates([]);
          setTagSuggestError(null);
        }
      } catch (e: unknown) {
        if (!mounted) return;
        setTagGenConfig(null);
        setTagGenConfigError(getErrorMessage(e, '获取主题标签生成配置失败'));
        setTagGenCount(5);
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setLangLabelGenConfigError(null);
      try {
        const cfg = await getLangLabelGenConfig();
        if (!mounted) return;
        setLangLabelGenConfig(cfg);
      } catch (e: unknown) {
        if (!mounted) return;
        setLangLabelGenConfig(null);
        setLangLabelGenConfigError(getErrorMessage(e, '获取语言标签配置失败'));
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
        setBasePostMetadata((p && typeof p.metadata === 'object' && p.metadata !== null ? (p.metadata as Record<string, unknown>) : null) ?? null);
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
      let tagsToPublish = Array.isArray(draft.tags) ? draft.tags : [];
      const shouldAutoGenTags =
        useAiTags === true && tagGenConfig?.enabled !== false && tagsToPublish.length === 0;

      const ensureTagSlugs = async (names: string[]): Promise<string[]> => {
        const requested = names
          .map((x) => String(x || '').trim())
          .filter((x) => x.length > 0)
          .slice(0, Math.max(1, tagGenCount));
        if (!requested.length) return [];

        setLoadingTags(true);
        setTagsError(null);
        try {
          let localAvailable = availableTags;
          const slugs: string[] = [];

          for (const n of requested) {
            const targetSlug = slugify(n);
            const exists =
              localAvailable.find((t) => t.slug === targetSlug) ??
              localAvailable.find((t) => t.name === n);
            if (exists) {
              slugs.push(exists.slug);
              continue;
            }

            try {
              const created = await createTag({
                tenantId: 1,
                type: 'TOPIC',
                name: n,
                slug: targetSlug,
                system: false,
                active: true,
              });
              localAvailable = [created, ...localAvailable.filter((x) => x.id !== created.id)];
              slugs.push(created.slug);
            } catch (e: unknown) {
              setTagsError(getErrorMessage(e, '新增标签失败'));
            }
          }

          setAvailableTags(localAvailable);
          return slugs;
        } finally {
          setLoadingTags(false);
        }
      };

      const fetchSuggestedTags = async (): Promise<string[]> => {
        const content = (draft.content ?? '').trim();
        if (content.length < 10) return [];

        const boardName = boards.find((b) => b.id === draft.boardId)?.name;
        const current = (tagsToPublish ?? [])
          .map((s) => availableTags.find((t) => t.slug === s)?.name ?? String(s))
          .filter((s) => s && s.trim());

        const resp = await suggestPostTags({
          title: draft.title?.trim() || undefined,
          content,
          count: tagGenCount,
          boardName,
          tags: current,
        });
        const out = Array.isArray(resp.tags) ? resp.tags : [];
        return out.map((x) => String(x || '').trim()).filter((x) => x.length > 0).slice(0, tagGenCount);
      };

      if (shouldAutoGenTags) {
        const fromCandidates = tagCandidates.map((x) => String(x || '').trim()).filter((x) => x.length > 0);
        const suggested = fromCandidates.length ? fromCandidates.slice(0, tagGenCount) : await fetchSuggestedTags();
        if (!suggested.length) {
          setError('未选择标签，且未能自动生成标签。请手动选择标签，或写多一点内容后重试。');
          return;
        }

        const slugs = await ensureTagSlugs(suggested);
        if (!slugs.length) {
          setError('未选择标签，且未能自动创建/选择生成的标签。请稍后重试或手动选择。');
          return;
        }

        tagsToPublish = Array.from(new Set([...tagsToPublish, ...slugs]));
        setDraft((p) => ({ ...p, tags: tagsToPublish, updatedAt: new Date().toISOString() }));
        setTagCandidates(suggested);
        setTagSuggestError(null);
      }

      if (postId !== null) {
        let languagesToPublish: string[] = [];
        if (langLabelGenConfig?.enabled !== false) {
          try {
            const resp = await suggestPostLangLabels({
              title: draft.title?.trim() || undefined,
              content: (draft.content ?? '').trim(),
            });
            languagesToPublish = Array.isArray(resp.languages)
              ? resp.languages.map((x) => String(x || '').trim()).filter((x) => x.length > 0).slice(0, 3)
              : [];
          } catch {
            languagesToPublish = [];
          }
        }

        await updatePost(postId, {
          boardId: draft.boardId,
          title: draft.title,
          content: draft.content,
          contentFormat: 'MARKDOWN',
          tags: tagsToPublish,
          metadata: {
            ...(basePostMetadata ?? {}),
            ...(languagesToPublish.length ? { languages: languagesToPublish } : {}),
          },
          attachmentIds: draft.attachments?.map((a) => a.id),
        });

        navigate('/portal/posts/mine');
        return;
      }

      let languagesToPublish: string[] = [];
      if (langLabelGenConfig?.enabled !== false) {
        try {
          const resp = await suggestPostLangLabels({
            title: draft.title?.trim() || undefined,
            content: (draft.content ?? '').trim(),
          });
          languagesToPublish = Array.isArray(resp.languages)
            ? resp.languages.map((x) => String(x || '').trim()).filter((x) => x.length > 0).slice(0, 3)
            : [];
        } catch {
          languagesToPublish = [];
        }
      }

      await createPost({
        boardId: draft.boardId,
        title: draft.title,
        content: draft.content,
        contentFormat: 'MARKDOWN',
        tags: tagsToPublish,
        metadata: languagesToPublish.length ? { languages: languagesToPublish } : undefined,
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

  const filteredTagOptions = useMemo(() => {
    const k = tagQuery.trim().toLowerCase();
    const selected = new Set((draft.tags ?? []).map(String));
    const base = availableTags.filter((t) => !selected.has(t.slug));
    if (!k) return base.slice(0, 30);
    return base
      .filter((t) => t.name.toLowerCase().includes(k) || t.slug.toLowerCase().includes(k))
      .slice(0, 30);
  }, [availableTags, draft.tags, tagQuery]);

  const addTagSlug = (slugValue: string) => {
    const s = String(slugValue || '').trim();
    if (!s) return;
    setDraft((p) => {
      const prev = Array.isArray(p.tags) ? p.tags : [];
      if (prev.includes(s)) return p;
      return { ...p, tags: [...prev, s], updatedAt: new Date().toISOString() };
    });
  };

  const removeTagSlug = (slugValue: string) => {
    const s = String(slugValue || '').trim();
    if (!s) return;
    setDraft((p) => {
      const prev = Array.isArray(p.tags) ? p.tags : [];
      return { ...p, tags: prev.filter((x) => x !== s), updatedAt: new Date().toISOString() };
    });
  };

  const ensureTagSlugs = async (names: string[]): Promise<string[]> => {
    const requested = names.map((x) => String(x || '').trim()).filter((x) => x.length > 0);
    if (!requested.length) return [];

    setLoadingTags(true);
    setTagsError(null);
    try {
      let localAvailable = availableTags;
      const slugs: string[] = [];

      for (const n of requested) {
        const targetSlug = slugify(n);
        const exists =
          localAvailable.find((t) => t.slug === targetSlug) ?? localAvailable.find((t) => t.name === n);
        if (exists) {
          slugs.push(exists.slug);
          continue;
        }

        try {
          const created = await createTag({
            tenantId: 1,
            type: 'TOPIC',
            name: n,
            slug: targetSlug,
            system: false,
            active: true,
          });
          localAvailable = [created, ...localAvailable.filter((x) => x.id !== created.id)];
          slugs.push(created.slug);
        } catch (e: unknown) {
          setTagsError(getErrorMessage(e, '新增标签失败'));
        }
      }

      setAvailableTags(localAvailable);
      return slugs;
    } finally {
      setLoadingTags(false);
    }
  };

  const ensureTagExistsAndAdd = async (name: string): Promise<string | null> => {
    const slugs = await ensureTagSlugs([name]);
    const slugValue = slugs[0];
    if (!slugValue) return null;
    addTagSlug(slugValue);
    return slugValue;
  };

  async function handleSuggestTags(silent?: boolean) {
    if (tagSuggesting) return;

    const content = (draft.content ?? '').trim();
    if (content.length < 10) {
      if (!silent) setTagSuggestError('内容太短了，先写一点内容再生成标签。');
      return;
    }

    setTagSuggesting(true);
    if (!silent) setTagSuggestError(null);

    try {
      const boardName = boards.find((b) => b.id === draft.boardId)?.name;
      const current = (draft.tags ?? [])
        .map((s) => availableTags.find((t) => t.slug === s)?.name ?? String(s))
        .filter((s) => s && s.trim());

      const resp = await suggestPostTags({
        title: draft.title?.trim() || undefined,
        content,
        count: tagGenCount,
        boardName,
        tags: current,
      });
      const out = Array.isArray(resp.tags) ? resp.tags : [];
      setTagCandidates(out);
      if (!silent && out.length === 0) {
        setTagSuggestError('没有生成到可用标签，请稍后重试。');
      }
    } catch (e: unknown) {
      if (!silent) setTagSuggestError(getErrorMessage(e, '生成标签失败'));
    } finally {
      setTagSuggesting(false);
    }
  }

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
    setTagCandidates([]);
    setTagSuggestError(null);
    setTagSuggesting(false);
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
        {langLabelGenConfigError && (
          <div className="p-3 rounded-md border border-amber-200 bg-amber-50 text-amber-900 text-sm">
            语言标签配置加载失败：{langLabelGenConfigError}
          </div>
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

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <label className="block text-sm font-medium text-gray-700">标签</label>
                <div className="flex items-center gap-2 text-xs text-gray-600">
                  {loadingTags ? <span>加载中...</span> : null}
                  {tagsError ? <span className="text-red-600">{tagsError}</span> : null}
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                {(draft.tags ?? []).length ? (
                  (draft.tags ?? []).map((slugValue) => {
                    const t = availableTags.find((x) => x.slug === slugValue);
                    const label = t?.name ?? slugValue;
                    return (
                      <span
                        key={slugValue}
                        className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-gray-300 bg-white text-sm"
                        title={slugValue}
                      >
                        <span>{label}</span>
                        <button
                          type="button"
                          className="text-gray-500 hover:text-gray-800"
                          onClick={() => removeTagSlug(slugValue)}
                          title="移除"
                        >
                          ×
                        </button>
                      </span>
                    );
                  })
                ) : (
                  <span className="text-sm text-gray-500">（未选择标签）</span>
                )}
              </div>

              <div className="relative">
                <div className="flex gap-2">
                  <input
                    className="flex-1 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="搜索已有标签或输入新标签后回车"
                    value={tagQuery}
                    onChange={(e) => setTagQuery(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        const v = tagQuery.trim();
                        if (!v) return;
                        void ensureTagExistsAndAdd(v);
                        setTagQuery('');
                      }
                    }}
                  />
                  <button
                    type="button"
                    className="px-3 py-2 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50"
                    disabled={!tagQuery.trim()}
                    onClick={() => {
                      const v = tagQuery.trim();
                      if (!v) return;
                      void ensureTagExistsAndAdd(v);
                      setTagQuery('');
                    }}
                  >
                    新增
                  </button>
                </div>

                {tagQuery.trim() && filteredTagOptions.length ? (
                  <div className="absolute z-10 mt-2 w-full rounded-md border border-gray-200 bg-white shadow-sm max-h-[260px] overflow-auto">
                    {filteredTagOptions.map((t) => (
                      <button
                        key={t.id}
                        type="button"
                        className="w-full text-left px-3 py-2 hover:bg-gray-50"
                        onClick={() => {
                          addTagSlug(t.slug);
                          setTagQuery('');
                        }}
                        title={t.slug}
                      >
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-sm text-gray-900">{t.name}</span>
                          <span className="text-xs text-gray-500">{t.slug}</span>
                        </div>
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>

              <div className="rounded-md border border-gray-200 bg-gray-50 p-3 space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <label className="flex items-center gap-2 text-xs text-gray-600 select-none">
                    <input
                      type="checkbox"
                      checked={useAiTags}
                      disabled={tagGenConfig?.enabled === false}
                      onChange={(e) => {
                        const v = e.target.checked;
                        setUseAiTags(v);
                        setTagSuggestError(null);
                        if (!v) setTagCandidates([]);
                      }}
                    />
                    使用AI生成主题标签
                  </label>

                  <div className="flex items-center gap-2">
                    <div className="text-xs text-gray-600">生成数量</div>
                    <select
                      value={tagGenCount}
                      onChange={(e) => setTagGenCount(Number(e.target.value))}
                      className="border border-gray-300 rounded-md px-2 py-1 text-xs bg-white"
                      disabled={!useAiTags}
                    >
                      {Array.from(
                        { length: Math.max(1, Math.min(tagGenConfig?.maxCount ?? 10, 50)) },
                        (_, i) => i + 1
                      ).map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      disabled={!useAiTags || tagSuggesting}
                      onClick={() => void handleSuggestTags(false)}
                      className="px-3 py-1.5 rounded-md bg-white border border-gray-300 hover:bg-gray-50 text-sm disabled:opacity-60"
                    >
                      {tagSuggesting ? '生成中...' : '生成候选标签'}
                    </button>
                  </div>
                </div>

                {tagGenConfig?.enabled === false ? (
                  <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                    主题标签生成已被管理员关闭。
                  </div>
                ) : null}
                {tagGenConfigError ? (
                  <div className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                    {tagGenConfigError}
                  </div>
                ) : null}
                {tagSuggestError ? (
                  <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                    {tagSuggestError}
                  </div>
                ) : null}

                {useAiTags && tagCandidates.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {tagCandidates.map((t, idx) => (
                      <button
                        key={`${idx}-${t}`}
                        type="button"
                        onClick={() => void ensureTagExistsAndAdd(t)}
                        className="px-3 py-1.5 rounded-full border border-gray-300 bg-white hover:bg-gray-50 text-sm"
                        title="点击添加该标签"
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                ) : null}
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
