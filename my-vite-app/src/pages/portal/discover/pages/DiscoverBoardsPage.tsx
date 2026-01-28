import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { listBoards, type BoardDTO } from '../../../../services/boardService';
import { listPostsPage, type PostDTO } from '../../../../services/postService';
import type { SpringPage } from '../../../../types/page';
import PostFeed from '../components/PostFeed';

function sortBoardsForDisplay(boards: BoardDTO[]): BoardDTO[] {
  return [...boards].sort((a, b) => {
    const ao = typeof a.sortOrder === 'number' ? a.sortOrder : 0;
    const bo = typeof b.sortOrder === 'number' ? b.sortOrder : 0;
    if (ao !== bo) return ao - bo;
    return String(a.name ?? '').localeCompare(String(b.name ?? ''), 'zh-Hans-CN');
  });
}

type BoardGroup = {
  parent: BoardDTO | null;
  children: BoardDTO[];
};

function buildBoardGroups(boards: BoardDTO[]): BoardGroup[] {
  const byId = new Map<number, BoardDTO>();
  boards.forEach((b) => byId.set(b.id, b));

  const parents: BoardDTO[] = [];
  const children: BoardDTO[] = [];

  boards.forEach((b) => {
    const pid = b.parentId;

    // Treat invalid/self-referencing parentId as a top-level board.
    // This prevents boards from disappearing in UI when backend data has parentId = id (or missing parent record).
    const isTopLevel = pid == null || pid === b.id || !byId.has(pid);

    if (isTopLevel) parents.push(b);
    else children.push(b);
  });

  const childrenByParentId = new Map<number, BoardDTO[]>();
  children.forEach((c) => {
    const pid = c.parentId as number;
    const arr = childrenByParentId.get(pid) ?? [];
    arr.push(c);
    childrenByParentId.set(pid, arr);
  });

  const parentGroups: BoardGroup[] = sortBoardsForDisplay(parents).map((p) => ({
    parent: p,
    children: sortBoardsForDisplay(childrenByParentId.get(p.id) ?? []),
  }));

  // 没有父板块记录（或父板块不可见/不存在）时，把这些子板块放到“其他”分组
  const orphanChildren = children
    .filter((c) => !byId.has(c.parentId as number))
    .filter((c) => c.parentId != null);

  const othersGroup: BoardGroup | null = orphanChildren.length
    ? { parent: null, children: sortBoardsForDisplay(orphanChildren) }
    : null;

  return othersGroup ? [...parentGroups, othersGroup] : parentGroups;
}

type PostsSortMode = 'HOT' | 'NEW';

export default function DiscoverBoardsPage() {
  const postsAnchorRef = useRef<HTMLDivElement | null>(null);

  const [boards, setBoards] = useState<BoardDTO[]>([]);
  const [boardsLoading, setBoardsLoading] = useState(false);
  const [boardsError, setBoardsError] = useState<string | null>(null);

  const [selectedBoardId, setSelectedBoardId] = useState<number | null>(null);

  const [postsSortMode, setPostsSortMode] = useState<PostsSortMode>('HOT');

  const [postsPage, setPostsPage] = useState<number>(1);
  const [postsData, setPostsData] = useState<SpringPage<PostDTO> | null>(null);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postsError, setPostsError] = useState<string | null>(null);

  const visibleBoards = useMemo(() => boards.filter((b) => b.visible !== false), [boards]);
  const groups = useMemo(() => buildBoardGroups(visibleBoards), [visibleBoards]);

  const flatBoardsInDisplayOrder = useMemo(() => {
    const flat: BoardDTO[] = [];
    groups.forEach((g) => {
      if (g.parent) flat.push(g.parent);
      g.children.forEach((c) => flat.push(c));
    });
    return flat;
  }, [groups]);

  const selectedBoard = useMemo(() => {
    if (!selectedBoardId) return null;
    return visibleBoards.find((b) => b.id === selectedBoardId) ?? null;
  }, [selectedBoardId, visibleBoards]);

  const loadBoards = useCallback(async () => {
    setBoardsLoading(true);
    setBoardsError(null);
    try {
      const data = await listBoards();
      setBoards(data);

      // 默认选择“展示顺序”的第一个可见板块（更符合用户预期）
      const visible = data.filter((b) => b.visible !== false);
      const firstInDisplay = buildBoardGroups(visible)
        .flatMap((g) => (g.parent ? [g.parent, ...g.children] : [...g.children]))
        .at(0);

      setSelectedBoardId((prev) => prev ?? (firstInDisplay ? firstInDisplay.id : null));
    } catch (e) {
      setBoardsError(e instanceof Error ? e.message : String(e));
      setBoards([]);
    } finally {
      setBoardsLoading(false);
    }
  }, []);

  const loadPosts = useCallback(
    async (boardId: number, pageToLoad: number, sortMode: PostsSortMode) => {
      setPostsLoading(true);
      setPostsError(null);
      try {
        // Backend /api/posts currently supports sorting by PostsEntity property names only.
        // There is no PostsEntity.hotScore, so we can't request sortBy=hotScore (would 500).
        const sortBy = sortMode === 'HOT' ? 'createdAt' : 'publishedAt';

        const resp = await listPostsPage({
          boardId,
          page: pageToLoad,
          pageSize: 20,
          status: 'PUBLISHED',
          sortBy,
          sortOrderDirection: 'DESC',
        });
        setPostsData(resp);
        setPostsPage(pageToLoad);
      } catch (e) {
        setPostsError(e instanceof Error ? e.message : String(e));
        setPostsData(null);
      } finally {
        setPostsLoading(false);
      }
    },
    [setPostsData]
  );

  useEffect(() => {
    loadBoards();
  }, [loadBoards]);

  useEffect(() => {
    if (!selectedBoardId) return;
    loadPosts(selectedBoardId, 1, postsSortMode);
  }, [selectedBoardId, postsSortMode, loadPosts]);

  const onSelectBoard = (id: number) => {
    setSelectedBoardId(id);
    // 先把分页重置；切换之后把视图带到帖子区
    setPostsPage(1);
    setTimeout(() => postsAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  };

  const onChangeSort = (mode: PostsSortMode) => {
    setPostsSortMode(mode);
    setPostsPage(1);
    setTimeout(() => postsAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">版块</h3>
          <p className="text-gray-600 text-sm">上方按分组展示版块；下方仅展示该版块下已发布的帖子。</p>
        </div>
      </div>

      {/* Board groups */}
      <div className="bg-white border rounded-lg p-4">
        {boardsLoading ? <div className="text-sm text-gray-500">加载版块中...</div> : null}
        {boardsError ? <div className="text-sm text-red-600">{boardsError}</div> : null}

        {!boardsLoading && !boardsError && flatBoardsInDisplayOrder.length === 0 ? (
          <div className="text-sm text-gray-500">暂无可见版块</div>
        ) : null}

        <div className="space-y-4">
          {groups.map((g) => {
            const groupTitle = g.parent ? g.parent.name : '其他';
            const groupDesc = g.parent?.description;

            return (
              <div key={g.parent ? `p-${g.parent.id}` : 'others'} className="space-y-2">
                <div className="flex items-baseline justify-between gap-3">
                  <div className="min-w-0">
                    <div className="font-medium text-gray-900 truncate">{groupTitle}</div>
                    {groupDesc ? <div className="text-xs text-gray-500 truncate">{groupDesc}</div> : null}
                  </div>
                  {g.parent ? (
                    <button
                      type="button"
                      className={`text-sm px-2 py-1 rounded border ${
                        selectedBoardId === g.parent.id ? 'bg-blue-600 text-white border-blue-600' : 'bg-white'
                      }`}
                      onClick={() => onSelectBoard(g.parent!.id)}
                    >
                      {selectedBoardId === g.parent.id ? '已选中' : '进入'}
                    </button>
                  ) : null}
                </div>

                {g.children.length ? (
                  <div className="flex flex-wrap gap-2">
                    {g.children.map((b) => {
                      const active = selectedBoardId === b.id;
                      return (
                        <button
                          key={b.id}
                          type="button"
                          className={`px-3 py-1.5 rounded-full border text-sm ${
                            active ? 'bg-blue-600 text-white border-blue-600' : 'bg-white hover:bg-gray-50'
                          }`}
                          onClick={() => onSelectBoard(b.id)}
                          title={b.description || b.name}
                        >
                          {b.name}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="text-xs text-gray-500">暂无子版块</div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Posts */}
      <div ref={postsAnchorRef} className="space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h4 className="text-base font-semibold">{selectedBoard ? `帖子 · ${selectedBoard.name}` : '帖子'}</h4>
            <p className="text-sm text-gray-600">仅展示已发布（PUBLISHED）</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* sort toggle */}
            <div className="inline-flex rounded-md border border-gray-200 bg-white p-1">
              <button
                type="button"
                className={`px-3 py-1.5 text-sm rounded ${
                  postsSortMode === 'HOT' ? 'bg-blue-600 text-white' : 'hover:bg-gray-50'
                }`}
                onClick={() => onChangeSort('HOT')}
                disabled={postsLoading}
              >
                默认（按创建时间）
              </button>
              <button
                type="button"
                className={`px-3 py-1.5 text-sm rounded ${
                  postsSortMode === 'NEW' ? 'bg-blue-600 text-white' : 'hover:bg-gray-50'
                }`}
                onClick={() => onChangeSort('NEW')}
                disabled={postsLoading}
              >
                最新发布
              </button>
            </div>

            {selectedBoardId ? (
              <button
                type="button"
                className="mr-2 px-3 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
                disabled={postsLoading}
                onClick={() => loadPosts(selectedBoardId, postsPage, postsSortMode)}
              >
                刷新
              </button>
            ) : null}
          </div>
        </div>

        {selectedBoardId == null ? <div className="text-sm text-gray-500">请先选择一个版块</div> : null}

        {selectedBoardId != null ? (
          <PostFeed
            page={postsData}
            loading={postsLoading}
            error={postsError}
            onRetry={() => loadPosts(selectedBoardId, postsPage, postsSortMode)}
            onPrev={() => loadPosts(selectedBoardId, Math.max(1, postsPage - 1), postsSortMode)}
            onNext={() => loadPosts(selectedBoardId, postsPage + 1, postsSortMode)}
          />
        ) : null}
      </div>
    </div>
  );
}
