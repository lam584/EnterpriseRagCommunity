import type {Dispatch, SetStateAction} from 'react';

import type {BoardDTO} from '../../../../services/boardService';
import type {PostDraftDTO} from '../../../../services/draftService';
import type {PostTagGenPublicConfigDTO} from '../../../../services/tagGenPublicService';
import type {TagDTO} from '../../../../services/tagService';
import type {PostTitleGenPublicConfigDTO} from '../../../../services/titleGenPublicService';

export type PostsBasicSectionProps = {
    composeLocked: boolean;
    draft: PostDraftDTO;
    setDraft: Dispatch<SetStateAction<PostDraftDTO>>;
    titleCandidates: string[];
    titleDropdownOpen: boolean;
    setTitleDropdownOpen: Dispatch<SetStateAction<boolean>>;
    titleSuggesting: boolean;
    useAiTitle: boolean;
    titleGenConfig: PostTitleGenPublicConfigDTO | null;
    titleGenConfigError: string | null;
    titleSuggestError: string | null;
    onSuggestTitles: () => void;
    loadingBoards: boolean;
    boards: BoardDTO[];
    loadingTags: boolean;
    tagsError: string | null;
    availableTags: TagDTO[];
    onRemoveTagSlug: (slug: string) => void;
    tagQuery: string;
    setTagQuery: Dispatch<SetStateAction<string>>;
    onEnsureTagExistsAndAdd: (name: string) => Promise<string | null>;
    useAiTags: boolean;
    tagGenConfig: PostTagGenPublicConfigDTO | null;
    tagSuggesting: boolean;
    onSuggestTags: () => void;
    filteredTagOptions: TagDTO[];
    onAddTagSlug: (slug: string) => void;
    tagGenConfigError: string | null;
    tagSuggestError: string | null;
};

export function PostsBasicSection(props: PostsBasicSectionProps) {
    const {
        composeLocked,
        draft,
        setDraft,
        titleCandidates,
        titleDropdownOpen,
        setTitleDropdownOpen,
        titleSuggesting,
        useAiTitle,
        titleGenConfig,
        titleGenConfigError,
        titleSuggestError,
        onSuggestTitles,
        loadingBoards,
        boards,
        loadingTags,
        tagsError,
        availableTags,
        onRemoveTagSlug,
        tagQuery,
        setTagQuery,
        onEnsureTagExistsAndAdd,
        useAiTags,
        tagGenConfig,
        tagSuggesting,
        onSuggestTags,
        filteredTagOptions,
        onAddTagSlug,
        tagGenConfigError,
        tagSuggestError,
    } = props;

    return (
        <fieldset disabled={composeLocked} className="space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="md:col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">标题</label>

                    <div className="flex items-center gap-2">
                        <div className="relative flex-1 min-w-0">
                            <input
                                value={draft.title}
                                onChange={(e) => setDraft((p) => ({...p, title: e.target.value}))}
                                className="w-full border border-gray-300 rounded-md px-3 py-2 pr-9 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="输入标题..."
                            />
                            <button
                                type="button"
                                className="absolute right-0 top-0 h-full px-2 border-l border-gray-300 text-gray-500 hover:text-gray-800 disabled:opacity-50"
                                onClick={() => setTitleDropdownOpen((v) => !v)}
                                disabled={titleCandidates.length === 0}
                                title={titleCandidates.length ? '选择一个候选标题' : '先生成候选标题'}
                                aria-label="选择候选标题"
                            >
                                <svg viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4">
                                    <path
                                        fillRule="evenodd"
                                        d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.24a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08Z"
                                        clipRule="evenodd"
                                    />
                                </svg>
                            </button>

                            {titleDropdownOpen && titleCandidates.length ? (
                                <div
                                    className="absolute z-10 mt-2 w-full rounded-md border border-gray-200 bg-white shadow-sm max-h-[260px] overflow-auto">
                                    {titleCandidates.map((t, idx) => (
                                        <button
                                            key={`${idx}-${t}`}
                                            type="button"
                                            className="w-full text-left px-3 py-2 hover:bg-gray-50"
                                            onMouseDown={(e) => e.preventDefault()}
                                            onClick={() => {
                                                setDraft((p) => ({...p, title: t}));
                                                setTitleDropdownOpen(false);
                                            }}
                                            title="点击使用该标题"
                                        >
                                            <div className="text-sm text-gray-900">{t}</div>
                                        </button>
                                    ))}
                                </div>
                            ) : null}
                        </div>

                        <button
                            type="button"
                            disabled={useAiTitle !== true || titleGenConfig?.enabled === false || titleSuggesting}
                            onClick={onSuggestTitles}
                            className="px-3 py-2 rounded-md bg-white border border-gray-300 hover:bg-gray-50 text-sm disabled:opacity-60"
                        >
                            {titleSuggesting ? '生成中...' : '生成标题'}
                        </button>
                    </div>

                    {titleGenConfig?.enabled === false && (
                        <div
                            className="mt-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                            标题生成已被管理员关闭。
                        </div>
                    )}
                    {titleGenConfigError && (
                        <div
                            className="mt-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                            {titleGenConfigError}
                        </div>
                    )}
                    {titleSuggestError && (
                        <div className="mt-2 text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                            {titleSuggestError}
                        </div>
                    )}
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">版块</label>
                    <select
                        value={draft.boardId}
                        onChange={(e) => setDraft((p) => ({...p, boardId: Number(e.target.value)}))}
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
                        <div className="text-xs text-gray-500 mt-1">未能加载版块列表，将使用当前
                            boardId：{draft.boardId}</div>
                    )}
                </div>
            </div>

            <div className="space-y-2">
                {(draft.tags ?? []).length ? (
                    <>
                        <div className="flex items-center justify-between gap-3">
                            <label className="block text-sm font-medium text-gray-700">标签</label>
                            <div className="flex items-center gap-2 text-xs text-gray-600">
                                {loadingTags ? <span>加载中...</span> : null}
                                {tagsError ? <span className="text-red-600">{tagsError}</span> : null}
                            </div>
                        </div>

                        <div className="flex flex-wrap gap-2">
                            {(draft.tags ?? []).map((slugValue) => {
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
                        onClick={() => onRemoveTagSlug(slugValue)}
                        title="移除"
                    >
                      ×
                    </button>
                  </span>
                                );
                            })}
                        </div>
                    </>
                ) : null}

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
                                    void onEnsureTagExistsAndAdd(v);
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
                                void onEnsureTagExistsAndAdd(v);
                                setTagQuery('');
                            }}
                        >
                            添加标签
                        </button>
                        <button
                            type="button"
                            disabled={useAiTags !== true || tagGenConfig?.enabled === false || tagSuggesting}
                            onClick={onSuggestTags}
                            className="px-3 py-2 rounded-md bg-white border border-gray-300 hover:bg-gray-50 text-sm disabled:opacity-60"
                        >
                            {tagSuggesting ? '生成中...' : '生成标签'}
                        </button>
                    </div>

                    {tagQuery.trim() && filteredTagOptions.length ? (
                        <div
                            className="absolute z-10 mt-2 w-full rounded-md border border-gray-200 bg-white shadow-sm max-h-[260px] overflow-auto">
                            {filteredTagOptions.map((t) => (
                                <button
                                    key={t.id}
                                    type="button"
                                    className="w-full text-left px-3 py-2 hover:bg-gray-50"
                                    onClick={() => {
                                        onAddTagSlug(t.slug);
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
            </div>
        </fieldset>
    );
}
