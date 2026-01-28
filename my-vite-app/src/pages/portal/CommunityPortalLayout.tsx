import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Compass, FileText, MessageCircle, Bot, User, LogOut, Search, PencilLine } from 'lucide-react';
import { portalSections } from './portalMenu';
import { useAuth } from '../../contexts/AuthContext';
import { logout } from '../../services/authService';
import { getMyProfile } from '../../services/accountService';
import { Avatar, AvatarFallback, AvatarImage } from '../../components/ui/avatar';
import { useEffect, useMemo, useRef, useState } from 'react';
import HotSidebar from './discover/components/HotSidebar';

export type PortalOutletContext = {
  composePreviewOpen: boolean;
  setComposePreviewOpen: (v: boolean) => void;
};

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
    return { id: s.id, to: s.basePath, label: s.label, icon: Icon };
  });

  // 将“发帖”从列表里拆出来，方便在结构上插入稳定的分隔间距
  const composeItem = navItems.find((n) => n.id === 'compose');
  const topNavItems = navItems.filter((n) => n.id !== 'compose');

  const location = useLocation();

  const { currentUser, isAuthenticated, setCurrentUser, setIsAuthenticated, refreshAuth } = useAuth();
  const [profileAvatarUrl, setProfileAvatarUrl] = useState<string | undefined>(undefined);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement | null>(null);

  const composePreviewOpenKey = 'portal.posts.compose.previewPaneOpen';
  const [composePreviewOpen, setComposePreviewOpen] = useState<boolean>(() => {
    try {
      const raw = localStorage.getItem(composePreviewOpenKey);
      if (!raw) return false;
      return raw === '1' || raw.toLowerCase() === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(composePreviewOpenKey, composePreviewOpen ? '1' : '0');
    } catch {
      // ignore
    }
  }, [composePreviewOpen]);

  const displayUsername = useMemo(() => {
    const name = currentUser?.username?.trim();
    return name && name.length > 0 ? name : '未登录';
  }, [currentUser?.username]);

  const avatarFallbackText = useMemo(() => {
    const name = currentUser?.username?.trim();
    if (!name) return 'U';
    return name.slice(0, 1).toUpperCase();
  }, [currentUser?.username]);

  // React 18 StrictMode(dev) 会让 effect 挂载/卸载/再挂载一次，
  // 如果这里每次都 refreshAuth，可能导致 isAuthenticated 短时间抖动，引发重定向风暴。
  const didInitialRefreshRef = useRef(false);

  useEffect(() => {
    if (!didInitialRefreshRef.current) {
      didInitialRefreshRef.current = true;
      void refreshAuth?.();
    }

    // 兼容其它页面（例如登录页）写入 localStorage 后，同步 portal UI
    const onStorage = (e: StorageEvent) => {
      if (e.key !== 'userData') return;
      void refreshAuth?.();
    };

    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [refreshAuth]);

  useEffect(() => {
    let cancelled = false;

    async function loadProfileAvatar() {
      if (!isAuthenticated) {
        setProfileAvatarUrl(undefined);
        return;
      }

      try {
        const p = await getMyProfile();
        if (!cancelled) setProfileAvatarUrl(p.avatarUrl);
      } catch {
        // ignore; avatar is optional and we have fallbacks
        if (!cancelled) setProfileAvatarUrl(undefined);
      }
    }

    loadProfileAvatar();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  useEffect(() => {
    function onDocumentMouseDown(e: MouseEvent) {
      if (!userMenuOpen) return;
      const el = userMenuRef.current;
      if (!el) return;
      if (e.target instanceof Node && !el.contains(e.target)) {
        setUserMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', onDocumentMouseDown);
    return () => document.removeEventListener('mousedown', onDocumentMouseDown);
  }, [userMenuOpen]);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      // 无论退出接口成功与否，都清理本地态，避免 UI 卡在已登录
      try {
        localStorage.removeItem('userData');
      } catch {
        // ignore
      }
      setCurrentUser(null);
      setIsAuthenticated(false);
      setUserMenuOpen(false);
      window.location.href = '/login';
    }
  };

  const isTopNavActive = (navId: string, pathname: string, isActiveFromNavLink: boolean) => {
    // 约束一级菜单 active 规则：
    // - compose：/portal/posts/create|edit 也算“发帖”
    // - posts：/portal/posts 下除 create/edit 外都算“帖子”（含 detail/drafts/mine/bookmarks 等）
    const isComposeActive =
      navId === 'compose' &&
      (pathname.startsWith('/portal/posts/create') || pathname.startsWith('/portal/posts/edit'));

    const isPostsActive =
      navId === 'posts' &&
      pathname.startsWith('/portal/posts') &&
      !pathname.startsWith('/portal/posts/create') &&
      !pathname.startsWith('/portal/posts/edit');

    return isComposeActive || isPostsActive || (isActiveFromNavLink && navId !== 'compose' && navId !== 'posts');
  };

  const isComposeRoute =
    location.pathname.startsWith('/portal/posts/create') || location.pathname.startsWith('/portal/posts/edit');
  const isComposeWide = isComposeRoute && composePreviewOpen;
  const containerClassName = isComposeWide ? 'w-full max-w-none' : 'max-w-7xl';
  const showDiscoverHotSidebar = location.pathname.startsWith('/portal/discover');

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
            {topNavItems.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) => {
                  const finalActive = isTopNavActive(n.id, location.pathname, isActive);

                  // 其他一级菜单：补充 hover 灰底圆角阴影；激活态文字加粗
                  return `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors shadow-transparent active:shadow-sm ${
                    finalActive
                      ? 'bg-gray-100 text-gray-900'
                      : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900 hover:shadow-sm'
                  }`;
                }}
              >
                {({ isActive }) => {
                  const finalActive = isTopNavActive(n.id, location.pathname, isActive);

                  const Icon = n.icon;
                  return (
                    <>
                      <Icon
                        className={`w-5 h-5 ${finalActive ? 'text-gray-900' : 'text-gray-500'}`}
                        strokeWidth={finalActive ? 2.8 : 2}
                      />
                      <span className={finalActive ? 'font-semibold' : 'font-normal'}>{n.label}</span>
                    </>
                  );
                }}
              </NavLink>
            ))}

            {/* 分隔：用结构化 spacer 保证“发帖”与上方菜单拉开距离（不依赖按钮自身 margin） */}
            {composeItem && (
              <div className="my-6">
                <div className="h-2" />
              </div>
            )}

            {/* “发帖”按钮单独渲染 */}
            {composeItem && (
              <NavLink
                key={composeItem.to}
                to={composeItem.to}
                className={({ isActive }) => {
                  const finalActive = isTopNavActive(composeItem.id, location.pathname, isActive);
                  return `flex items-center justify-center gap-2 px-3 py-3 rounded-3xl transition-colors focus:outline-none focus:ring-2 focus:ring-black focus:ring-offset-2 ${
                    finalActive
                      ? 'bg-black text-white font-semibold'
                      : 'bg-black text-white hover:bg-gray-900 font-medium'
                  }`;
                }}
              >
                {({ isActive }) => {
                  const finalActive = isTopNavActive(composeItem.id, location.pathname, isActive);
                  const Icon = composeItem.icon;
                  return (
                    <>
                      <Icon className="w-5 h-5 text-white" strokeWidth={finalActive ? 2.8 : 2} />
                      <span className={finalActive ? 'font-semibold' : 'font-medium'}>{composeItem.label}</span>
                    </>
                  );
                }}
              </NavLink>
            )}
          </nav>

          {/* Bottom User Area + Menu */}
          <div className="p-4 border-t border-gray-200 relative" ref={userMenuRef}>
            {isAuthenticated ? (
              <>
                <button
                  type="button"
                  onClick={() => setUserMenuOpen((v) => !v)}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-gray-700 transition-colors text-sm"
                  aria-expanded={userMenuOpen}
                  aria-haspopup="menu"
                >
                  <Avatar className="h-9 w-9">
                    <AvatarImage src={profileAvatarUrl} alt={displayUsername} />
                    <AvatarFallback className="text-xs">{avatarFallbackText}</AvatarFallback>
                  </Avatar>
                  <div className="flex-1 min-w-0 text-left">
                    <div className="font-medium truncate">{displayUsername}</div>
                  </div>
                </button>

                {userMenuOpen && (
                  <div
                    role="menu"
                    className="absolute left-4 right-4 bottom-[calc(100%+8px)] rounded-xl border border-gray-200 bg-white shadow-lg overflow-hidden"
                  >
                    <div className="px-3 py-2 text-xs text-gray-400 border-b border-gray-100">账户</div>

                    <a
                      href="http://127.0.0.1:8099/admin"
                      role="menuitem"
                      className="flex items-center gap-3 px-3 py-2.5 text-gray-700 hover:bg-gray-50 transition-colors text-sm"
                      onClick={() => setUserMenuOpen(false)}
                    >
                      <User className="w-5 h-5 text-gray-500" />
                      进入管理员后台
                    </a>

                    <button
                      type="button"
                      role="menuitem"
                      onClick={handleLogout}
                      className="w-full flex items-center gap-3 px-3 py-2.5 text-red-600 hover:bg-red-50 transition-colors text-sm"
                    >
                      <LogOut className="w-5 h-5" />
                      退出登录
                    </button>
                  </div>
                )}
              </>
            ) : (
              <a
                href="/login"
                className="w-full inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors text-sm font-medium"
              >
                <User className="w-5 h-5" />
                登录
              </a>
            )}

            <div className="mt-4 text-xs text-center text-gray-400">© {new Date().getFullYear()} RAG Community</div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 min-w-0 bg-white">
          <div className="w-full min-w-0">
            <Outlet context={{ composePreviewOpen, setComposePreviewOpen } satisfies PortalOutletContext} />
          </div>
        </main>
      </div>

      {showDiscoverHotSidebar ? (
        <aside className="hidden lg:block fixed top-0 right-0 min-[1920px]:right-[calc((100vw-80rem)/2-20rem)] w-80 bg-white border-l border-gray-200 h-screen shrink-0 p-4 overflow-y-auto z-20">
          <HotSidebar />
        </aside>
      ) : null}
    </div>
  );
}
