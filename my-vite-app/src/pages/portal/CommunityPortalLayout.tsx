import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Compass, FileText, MessageCircle, Bot, User, LogOut, Search, PencilLine } from 'lucide-react';
import { portalSections } from './portalMenu';

/**
 * 前台门户布局：包含左侧一级菜单（浏览与发现、帖子、互动、智能助手、账户）
 * 作为路由嵌套的容器，子路由在 <Outlet /> 中渲染。
 */
export default function CommunityPortalLayout() {
  const iconMap = {
    discover: Compass,
    search: Search,
    compose: PencilLine,
    posts: FileText,
    interact: MessageCircle,
    assistant: Bot,
    account: User,
  } as const;

  const navItems = portalSections.map((s) => {
    const Icon = iconMap[s.id as keyof typeof iconMap] ?? Compass;
    return { to: s.basePath, label: s.label, icon: Icon };
  });

  const location = useLocation();
  const isPostDetail = /^\/portal\/posts\/\d+\/?$/.test(location.pathname);
  const isCompose =
    location.pathname.startsWith('/portal/posts/create') ||
    location.pathname.startsWith('/portal/posts/edit') ||
    location.pathname.startsWith('/portal/compose');

  let containerClassName = 'max-w-7xl';
  if (isPostDetail) {
    containerClassName = 'w-full';
  } else if (isCompose) {
    containerClassName = 'max-w-screen-2xl';
  }

  return (
    <div className="min-h-screen bg-white">
      <div className={`${containerClassName} mx-auto flex border-gray-200 min-h-screen`}>
        {/* Left Sidebar */}
        <aside className="w-64 bg-white border-gray-200 h-screen sticky top-0 flex flex-col shrink-0 z-10">
          <div className="p-6 border-b border-gray-200">
            <div className="text-xl font-bold text-blue-600 flex items-center gap-2">
              <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white">
                R
              </div>
              RAG Community
            </div>
          </div>

          <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
            {navItems.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${
                    isActive
                      ? 'bg-gray-100 text-gray-900 font-medium'
                      : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                  }`
                }
              >
                <n.icon className="w-5 h-5" />
                {n.label}
              </NavLink>
            ))}
          </nav>

          <div className="p-4 border-t border-gray-200">
            <a
              href="http://127.0.0.1:8099/admin"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-colors text-sm"
            >
              <LogOut className="w-5 h-5" />
              进入管理员后台
            </a>
            <div className="mt-4 text-xs text-center text-gray-400">
              © {new Date().getFullYear()} RAG Community
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="min-w-0 bg-white flex-1">
          <div>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
