import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deleteDraft, listDrafts, type PostDraftDTO } from '../../../../services/draftService';

export default function PostsDraftsPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [drafts, setDrafts] = useState<PostDraftDTO[]>([]);

  const refresh = async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listDrafts();
      setDrafts(list);
    } catch (e: any) {
      setError(e?.message ?? '加载草稿失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return drafts;
    return drafts.filter((d) => (d.title || '').toLowerCase().includes(kw) || (d.content || '').toLowerCase().includes(kw));
  }, [drafts, keyword]);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">草稿箱</h3>
          <p className="text-gray-600">支持草稿的增删改查。点击“编辑”会跳到发帖页面并加载草稿内容。</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
            onClick={() => navigate('/portal/posts/create')}
          >
            新建草稿
          </button>
          <button
            type="button"
            className="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50"
            onClick={refresh}
          >
            刷新
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 rounded-md border border-red-200 bg-red-50 text-red-700 text-sm">{error}</div>
      )}

      <div className="flex items-center gap-2">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="搜索标题或内容..."
        />
      </div>

      {loading ? (
        <div className="text-sm text-gray-600">加载中...</div>
      ) : filtered.length === 0 ? (
        <div className="text-sm text-gray-600">
          暂无草稿。去 <button className="text-blue-600 hover:underline" onClick={() => navigate('/portal/posts/create')}>发帖</button> 写一篇，然后保存草稿。
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((d) => (
            <div key={d.id} className="border border-gray-200 rounded-md p-3 bg-white">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="font-medium text-gray-900 truncate">
                    {d.title?.trim() ? d.title : '（无标题）'}
                  </div>
                  <div className="text-xs text-gray-500 mt-1">
                    更新：{new Date(d.updatedAt).toLocaleString()} / 字数：{(d.content ?? '').length}
                    {d.attachments?.length ? ` / 附件：${d.attachments.length}` : ''}
                  </div>
                  {d.content?.trim() && (
                    <div className="text-sm text-gray-600 mt-2 line-clamp-2">
                      {d.content.slice(0, 120)}{d.content.length > 120 ? '…' : ''}
                    </div>
                  )}
                </div>

                <div className="flex shrink-0 gap-2">
                  <button
                    type="button"
                    className="px-3 py-1.5 text-sm rounded-md border border-gray-300 hover:bg-gray-50"
                    onClick={() => navigate(`/portal/posts/create?draftId=${encodeURIComponent(d.id)}`)}
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    className="px-3 py-1.5 text-sm rounded-md border border-red-300 text-red-700 hover:bg-red-50"
                    onClick={async () => {
                      if (!confirm('确定删除该草稿吗？')) return;
                      await deleteDraft(d.id);
                      await refresh();
                    }}
                  >
                    删除
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
