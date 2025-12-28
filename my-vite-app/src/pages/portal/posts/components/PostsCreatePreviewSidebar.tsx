import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import MarkdownPreview from '../../../../components/ui/MarkdownPreview';

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
      try {
        const raw = localStorage.getItem(key);
        if (!raw) {
          setMarkdown('');
          return;
        }
        const parsed = JSON.parse(raw) as { markdown?: unknown };
        setMarkdown(typeof parsed.markdown === 'string' ? parsed.markdown : '');
      } catch {
        setMarkdown('');
      }
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
    <aside className="hidden lg:block sticky top-4 self-start">
      <div className="border border-gray-200 rounded-md bg-white p-3">
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium text-gray-700">实时预览</div>
          <div className="text-xs text-gray-500">跟随编辑内容自动更新</div>
        </div>
        <div className="mt-2 max-h-[calc(100vh-120px)] overflow-auto">
          {markdown.trim() ? (
            <MarkdownPreview markdown={markdown} />
          ) : (
            <div className="text-sm text-gray-400">（这里会显示预览）</div>
          )}
        </div>
      </div>
    </aside>
  );
}
