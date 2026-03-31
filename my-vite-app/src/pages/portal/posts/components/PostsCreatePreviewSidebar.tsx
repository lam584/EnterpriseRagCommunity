import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';
import {readJsonFromStorage} from '../../../../utils/storage';

/**
 * 右侧实时预览（放在发帖卡片之外）。
 * 通过 localStorage + 自定义事件接收 PostsCreatePage 的内容快照。
 */
export default function PostsCreatePreviewSidebar() {
  const location = useLocation();
  const [markdown, setMarkdown] = useState<string>('');

  useEffect(() => {
    const key = 'portal.posts.compose.preview';

    const read = () => {
        const parsed = readJsonFromStorage<{ markdown?: unknown }>(key);
        setMarkdown(parsed && typeof parsed.markdown === 'string' ? parsed.markdown : '');
    };

    read();

    const onStorage = (e: StorageEvent) => {
      if (e.key !== key) return;
      read();
    };

    const onCustom = () => read();

    window.addEventListener('storage', onStorage);
    window.addEventListener('posts-compose-preview-update', onCustom as EventListener);

    return () => {
      window.removeEventListener('storage', onStorage);
      window.removeEventListener('posts-compose-preview-update', onCustom as EventListener);
    };
  }, [location.pathname]);

  return (
    <aside className="h-full min-h-0 flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium text-gray-700">实时预览</div>
      </div>
      <div className="w-full flex-1 min-h-0 border border-gray-300 rounded-md px-3 py-2 bg-white overflow-auto">
        {markdown.trim() ? <MarkdownPreview markdown={markdown} /> : <div className="text-sm text-gray-400">（这里会显示预览）</div>}
      </div>
    </aside>
  );
}
