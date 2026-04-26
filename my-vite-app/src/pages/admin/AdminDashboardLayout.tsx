import { NavLink, Outlet } from 'react-router-dom';
import { FaClipboardList, FaCheckCircle, FaMagic, FaSearch, FaChartLine, FaUsersCog, FaTools, FaLink } from 'react-icons/fa';
import { useAccess } from '../../contexts/AccessContext';
import { useAuth } from '../../contexts/AuthContext';
import { logout } from '../../services/authService';
import { Avatar, AvatarFallback, AvatarImage } from '../../components/ui/avatar';
import { useEffect, useMemo, useRef, useState } from 'react';
import BeianFooter from '../../components/common/BeianFooter';
import { getAvatarFallbackText, getDisplayUsername } from '../../utils/userDisplay';
import { useProfileAvatarUrl } from '../../hooks/useProfileAvatarUrl';

/**
 * 后台管理布局（统一风格）：采用与 NewsSystemLayout 相同的左侧紫色渐变侧边栏
 * 菜单：内容管理、审核中心、语义增强、检索与 RAG、评估与监控、用户与权限
 */
export default function AdminDashboardLayout() {
  const { hasPerm, loading } = useAccess();
  const { currentUser, isAuthenticated, setCurrentUser, setIsAuthenticated, refreshAuth } = useAuth();

  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement | null>(null);

  const displayUsername = useMemo(() => getDisplayUsername(currentUser?.username), [currentUser?.username]);
  const avatarFallbackText = useMemo(() => getAvatarFallbackText(currentUser?.username), [currentUser?.username]);

  const profileAvatarUrl = useProfileAvatarUrl(isAuthenticated);

  // 避免 React 18 StrictMode(dev) 下重复 refreshAuth 造成短时间抖动。
  const didInitialRefreshRef = useRef(false);

  useEffect(() => {
    // 只在"未认证"或"已认证但用户信息缺失"时尝试刷新一次即可；
    // 如果每次 userData 变更/或其它地方频繁 refreshAuth，可能导致循环请求。
    if (didInitialRefreshRef.current) return;
    if (isAuthenticated && currentUser) return;

    didInitialRefreshRef.current = true;
    if (!isAuthenticated || (isAuthenticated && !currentUser)) {
      void refreshAuth?.();
    }
  }, [isAuthenticated, currentUser, refreshAuth]);

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

  const linkBase = '/admin';
  const navItemsAll = [
    { to: `${linkBase}/content`, label: '内容管理', icon: <FaClipboardList />, perm: { resource: 'admin_content', action: 'access' } },
    { to: `${linkBase}/review`, label: '审核中心', icon: <FaCheckCircle />, perm: { resource: 'admin_review', action: 'access' } },
    { to: `${linkBase}/semantic`, label: '语义增强', icon: <FaMagic />, perm: { resource: 'admin_semantic', action: 'access' } },
    { to: `${linkBase}/retrieval`, label: '检索与 RAG', icon: <FaSearch />, perm: { resource: 'admin_retrieval', action: 'access' } },
    { to: `${linkBase}/metrics`, label: '评估与监控', icon: <FaChartLine />, perm: { resource: 'admin_metrics', action: 'access' } },
    { to: `${linkBase}/users`, label: '用户与权限', icon: <FaUsersCog />, perm: { resource: 'admin_users', action: 'access' } },
    { to: `${linkBase}/llm-config`, label: 'LLM 接入配置', icon: <FaLink />, perm: { resource: 'admin_semantic', action: 'access' } },
  ];

  // During loading, keep menu stable (avoid flicker). We'll show all items, but routes are still guarded.
  const navItems = loading ? navItemsAll : navItemsAll.filter(n => hasPerm(n.perm.resource, n.perm.action));

  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <aside className="w-64 bg-gradient-to-b from-purple-700 to-purple-500 text-white shadow-xl flex flex-col">
        <div className="flex items-center justify-center h-20 border-b border-purple-800">
          <FaTools className="text-3xl" />
          <span className="ml-2 text-2xl font-bold">后台管理</span>
        </div>
        <nav className="flex-1 mt-4">
          <ul>
            {navItems.map((n) => (
              <li key={n.to} className="mx-3">
                <NavLink
                  to={n.to}
                  className={({ isActive }) => `px-6 py-3 my-1 flex items-center rounded-lg cursor-pointer duration-200 ${
                    isActive ? 'bg-purple-800 shadow-inner' : 'hover:bg-purple-500 hover:shadow-lg'
                  }`}
                >
                  <div className="text-xl">{n.icon}</div>
                  <span className="ml-4 font-medium">{n.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        {/* Bottom User Area + Menu */}
        <div className="p-4 border-t border-purple-800 text-sm text-purple-200 relative" ref={userMenuRef}>
          {isAuthenticated ? (
            <>
              <button
                type="button"
                onClick={() => setUserMenuOpen((v) => !v)}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-purple-50 transition-colors text-sm hover:bg-purple-600/40"
                aria-expanded={userMenuOpen}
                aria-haspopup="menu"
              >
                <Avatar className="h-9 w-9">
                  <AvatarImage src={profileAvatarUrl} alt={displayUsername} />
                  <AvatarFallback className="text-xs bg-purple-800 text-white">{avatarFallbackText}</AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0 text-left">
                  <div className="font-medium truncate">{displayUsername}</div>
                </div>
              </button>

              {userMenuOpen && (
                <div
                  role="menu"
                  className="absolute left-4 right-4 bottom-[calc(100%+8px)] rounded-xl border border-purple-900/40 bg-white text-gray-800 shadow-lg overflow-hidden"
                >
                  <div className="px-3 py-2 text-xs text-gray-400 border-b border-gray-100">账户</div>

                  <a
                    href="/portal/discover"
                    role="menuitem"
                    className="flex items-center gap-3 px-3 py-2.5 text-gray-700 hover:bg-gray-50 transition-colors text-sm"
                    onClick={() => setUserMenuOpen(false)}
                  >
                    进入前台
                  </a>

                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleLogout}
                    className="w-full flex items-center gap-3 px-3 py-2.5 text-red-600 hover:bg-red-50 transition-colors text-sm"
                  >
                    退出登录
                  </button>
                </div>
              )}
            </>
          ) : (
            <a
              href="/login"
              className="w-full inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-white/90 text-purple-700 hover:bg-white transition-colors text-sm font-medium"
            >
              去登录
            </a>
          )}

          <BeianFooter className="mt-4 text-xs text-center text-purple-200/80" />
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 overflow-auto">
        <Outlet />
      </div>
    </div>
  );
}
