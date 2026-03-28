import React, {useEffect, useState, useCallback} from 'react';
import {useNavigate} from 'react-router-dom';
import {listMyComments, deleteMyComment, type CommentDTO} from '../../../../services/commentService';
import {getStoredUserId, resolvePortalAuthState} from '../../../../services/portalAuthService';
import toast from 'react-hot-toast';

function formatDateTime(iso?: string) {
    if (!iso) return '-';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}

/**
 * 评论内容组件，支持 3 行截断和展开/收起。
 */
const CommentContent: React.FC<{ content: string }> = ({content}) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const [isOverflowing, setIsOverflowing] = useState(false);

    const contentRef = useCallback((node: HTMLDivElement | null) => {
        if (node !== null) {
            // 检查是否超过 3 行。可以通过 scrollHeight > clientHeight (设置了 max-height)
            // 或者计算 line-height。这里简单使用 line-height * 3 比较。
            const lineHeight = parseInt(window.getComputedStyle(node).lineHeight || '24', 10);
            if (node.scrollHeight > lineHeight * 3.1) { // 3.1 留一点余量
                setIsOverflowing(true);
            }
        }
    }, []);

    return (
        <div className="mt-2">
            <div
                ref={contentRef}
                className={`text-sm text-gray-700 whitespace-pre-wrap break-words leading-6 ${
                    !isExpanded ? 'line-clamp-3' : ''
                }`}
                style={!isExpanded ? {
                    display: '-webkit-box',
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden'
                } : {}}
            >
                {content}
            </div>
            {isOverflowing && (
                <button
                    type="button"
                    onClick={() => setIsExpanded(!isExpanded)}
                    className="mt-1 text-xs text-blue-600 hover:underline"
                >
                    {isExpanded ? '收起' : '展开'}
                </button>
            )}
        </div>
    );
};

export default function MyCommentsPage() {
    const navigate = useNavigate();

    const [authorId, setAuthorId] = useState<number | undefined>(() => getStoredUserId());
    const [keyword, setKeyword] = useState('');
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [items, setItems] = useState<CommentDTO[]>([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const fetchComments = useCallback(async (p = page, ps = pageSize, k = keyword) => {
        setLoading(true);
        setError(null);
        try {
            const resp = await listMyComments(p, ps, k || undefined);
            setItems(resp.content || []);
            setTotalElements(resp.totalElements);
            setTotalPages(resp.totalPages);
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : '获取评论失败';
            setError(msg);
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, [page, pageSize, keyword]);

    useEffect(() => {
        const load = async () => {
            const st = await resolvePortalAuthState();
            setAuthorId(st.userId);
        };
        void load();
    }, []);

    useEffect(() => {
        if (authorId) {
            void fetchComments();
        }
    }, [authorId, page, pageSize, fetchComments]);

    const onSearch = () => {
        setPage(1);
        void fetchComments(1, pageSize, keyword);
    };

    const onReset = () => {
        setKeyword('');
        setPage(1);
        void fetchComments(1, pageSize, '');
    };

    const handleDelete = async (id: number) => {
        if (!window.confirm('确定要删除这条评论吗？')) return;
        try {
            await deleteMyComment(id);
            toast.success('删除成功');
            void fetchComments();
        } catch (e: unknown) {
            toast.error(e instanceof Error ? e.message : '删除失败');
        }
    };

    const handleJump = (postId: number, commentId: number) => {
        navigate(`/portal/posts/detail/${postId}#comment-${commentId}`);
    };

    const showNoUserHint = !authorId;

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-lg font-semibold">我的评论</h3>
                    <p className="text-gray-600 text-sm">管理我发布过的所有评论。</p>
                </div>
            </div>

            {/* 筛选与搜索 */}
            <div className="bg-white rounded-lg shadow p-6">
                <div className="flex flex-wrap gap-3">
                    <input
                        className="flex-1 min-w-[200px] rounded border px-3 py-2 border-gray-300 text-sm"
                        placeholder="搜索评论内容..."
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && onSearch()}
                    />
                    <button
                        onClick={onSearch}
                        className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
                        disabled={loading || showNoUserHint}
                    >
                        搜索
                    </button>
                    <button
                        onClick={onReset}
                        className="px-4 py-2 border border-gray-300 rounded text-sm hover:bg-gray-50"
                        disabled={loading}
                    >
                        重置
                    </button>
                </div>
            </div>

            {showNoUserHint && (
                <div className="text-sm text-gray-600">
                    未获取到用户信息，请先登录。
                </div>
            )}

            {error && (
                <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
                    {error}
                </div>
            )}

            {/* 评论列表 */}
            <div className="bg-white rounded-lg shadow overflow-hidden">
                <div className="p-4 border-b bg-gray-50 flex justify-between items-center">
                    <h4 className="text-sm font-semibold text-gray-700">评论列表 ({totalElements})</h4>
                    <div className="flex items-center gap-2 text-sm text-gray-500">
                        <span>每页显示:</span>
                        <select
                            value={pageSize}
                            onChange={(e) => {
                                setPageSize(Number(e.target.value));
                                setPage(1);
                            }}
                            className="border rounded px-1 py-0.5 bg-white"
                        >
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>
                    </div>
                </div>

                <div className="divide-y divide-gray-100">
                    {items.map((item) => (
                        <div key={item.id} className="p-4 hover:bg-gray-50 transition-colors">
                            <div className="flex justify-between items-start gap-4">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 text-xs text-gray-500 mb-1">
                                        <span>发布于 {formatDateTime(item.createdAt)}</span>
                                        <span>•</span>
                                        <button
                                            onClick={() => item.postId && handleJump(item.postId, item.id)}
                                            className="text-blue-600 hover:underline truncate max-w-[300px]"
                                        >
                                            在帖子《{String(item.metadata?.postTitle || item.postId || '未知')}》下
                                        </button>
                                    </div>
                                    <CommentContent content={item.content}/>
                                </div>
                                <button
                                    onClick={() => handleDelete(item.id)}
                                    className="px-3 py-1 text-xs text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors shrink-0"
                                >
                                    删除
                                </button>
                            </div>
                        </div>
                    ))}

                    {!loading && items.length === 0 && (
                        <div className="p-8 text-center text-gray-500 text-sm">
                            暂无评论
                        </div>
                    )}

                    {loading && (
                        <div className="p-8 text-center text-gray-500 text-sm">
                            加载中...
                        </div>
                    )}
                </div>

                {/* 分页 */}
                {totalPages > 1 && (
                    <div className="p-4 border-t bg-gray-50 flex items-center justify-between gap-4">
                        <div className="text-sm text-gray-600">
                            第 {page} / {totalPages} 页
                        </div>
                        <div className="flex gap-2">
                            <button
                                disabled={page <= 1 || loading}
                                onClick={() => setPage(page - 1)}
                                className="px-3 py-1 border rounded text-sm hover:bg-white disabled:opacity-50"
                            >
                                上一页
                            </button>
                            <button
                                disabled={page >= totalPages || loading}
                                onClick={() => setPage(page + 1)}
                                className="px-3 py-1 border rounded text-sm hover:bg-white disabled:opacity-50"
                            >
                                下一页
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
