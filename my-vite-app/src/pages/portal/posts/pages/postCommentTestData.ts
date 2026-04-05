export type CommentTestItem = {
  id: number;
  postId: number;
  parentId: number | null;
  content: string;
  authorName: string;
  createdAt: string;
  metadata: { languages: string[] };
};

export function buildCompactReplyComments(): CommentTestItem[] {
  return [
    { id: 100, postId: 1, parentId: null, content: 'root', authorName: 'Alice', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
    { id: 101, postId: 1, parentId: 100, content: 'c1', authorName: 'Bob', createdAt: '2026-01-04T00:00:00Z', metadata: { languages: ['en'] } },
    { id: 102, postId: 1, parentId: 100, content: 'c2', authorName: 'Cat', createdAt: '2026-01-03T00:00:00Z', metadata: { languages: ['en'] } },
    { id: 103, postId: 1, parentId: 100, content: 'c3', authorName: 'Dog', createdAt: '2026-01-02T00:00:00Z', metadata: { languages: ['zh'] } },
    { id: 104, postId: 1, parentId: 100, content: 'c4', authorName: 'Eve', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
  ];
}
