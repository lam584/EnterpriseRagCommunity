import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listFavoriteQaMessages, type QaMessageDTO } from '../../../../services/qaHistoryService';
import { Heart, MessageSquare, Clock, ArrowRight } from 'lucide-react';

export default function AssistantCollectionsPage() {
  const [favorites, setFavorites] = useState<QaMessageDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadFavorites();
  }, []);

  const loadFavorites = async () => {
    try {
      setLoading(true);
      const res = await listFavoriteQaMessages(0, 100);
      setFavorites(res.content);
    } catch (err) {
      console.error('Failed to load favorites:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleGoToMessage = (fav: QaMessageDTO) => {
    navigate(`/portal/assistant/chat?sessionId=${fav.sessionId}&highlightMessageId=${fav.id}`);
  };

  const formatDateTime = (iso?: string) => {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleString();
  };

  return (
    <div className="max-w-4xl mx-auto py-6 px-4">
      <div className="flex items-center gap-2 mb-6">
        <Heart className="text-red-500 fill-red-500" size={24} />
        <h3 className="text-2xl font-bold text-gray-900">收藏夹</h3>
      </div>
      
      <p className="text-gray-500 mb-8">
        这里展示您在与 AI 助手对话中收藏的所有精彩回答。点击任意项即可回到当时的对话场景。
      </p>

      {loading ? (
        <div className="flex justify-center py-20">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        </div>
      ) : favorites.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gray-50 mb-4">
            <Heart className="text-gray-300" size={32} />
          </div>
          <h4 className="text-lg font-medium text-gray-900 mb-1">暂无收藏</h4>
          <p className="text-gray-500">在对话页面点击心形图标即可收藏消息</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {favorites.map((fav) => (
            <div
              key={fav.id}
              onClick={() => handleGoToMessage(fav)}
              className="group bg-white rounded-xl border border-gray-200 p-5 hover:border-blue-400 hover:shadow-md transition-all cursor-pointer relative overflow-hidden"
            >
              <div className="absolute top-0 left-0 w-1 h-full bg-blue-500 opacity-0 group-hover:opacity-100 transition-opacity" />
              
              <div className="flex items-start justify-between gap-4 mb-3">
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <Clock size={14} />
                  <span>{formatDateTime(fav.createdAt)}</span>
                  <span className="mx-1">·</span>
                  <MessageSquare size={14} />
                  <span>会话 #{fav.sessionId}</span>
                </div>
                <ArrowRight className="text-gray-300 group-hover:text-blue-500 group-hover:translate-x-1 transition-all" size={18} />
              </div>

              <div className="text-gray-800 text-sm line-clamp-3 leading-relaxed mb-2">
                {fav.content}
              </div>

              <div className="flex items-center gap-2 mt-4 pt-4 border-t border-gray-50">
                <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-600 text-[10px] font-medium uppercase tracking-wider">
                  {fav.model || 'ASSISTANT'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
