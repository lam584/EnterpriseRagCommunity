import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export interface IndexSyncStatus {
    indexed: boolean;
    docCount: number;
    status?: string;
    reason?: string;
    detail?: string;
    indexName?: string;
}

export interface PostIndexSyncStatus {
    postId: number;
    postIndex: IndexSyncStatus;
    attachmentIndex: IndexSyncStatus;
}

export interface CommentIndexSyncStatus {
    commentId: number;
    commentIndex: IndexSyncStatus;
}

export async function batchPostIndexSyncStatus(postIds: number[]): Promise<PostIndexSyncStatus[]> {
    const csrfToken = await getCsrfToken();
    const res = await fetch(apiUrl('/api/admin/retrieval/index-sync/posts'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken,
        },
        credentials: 'include',
        body: JSON.stringify({ids: postIds}),
    });

    const data: unknown = await res.json().catch(() => ([]));
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取帖子索引状态失败');
    return Array.isArray(data) ? (data as PostIndexSyncStatus[]) : [];
}

export async function batchCommentIndexSyncStatus(commentIds: number[]): Promise<CommentIndexSyncStatus[]> {
    const csrfToken = await getCsrfToken();
    const res = await fetch(apiUrl('/api/admin/retrieval/index-sync/comments'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken,
        },
        credentials: 'include',
        body: JSON.stringify({ids: commentIds}),
    });

    const data: unknown = await res.json().catch(() => ([]));
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取评论索引状态失败');
    return Array.isArray(data) ? (data as CommentIndexSyncStatus[]) : [];
}
