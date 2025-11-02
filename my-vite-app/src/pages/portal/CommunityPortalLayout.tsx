import { NavLink, Outlet } from 'react-router-dom';

/**
 * 前台门户布局：包含一级菜单（浏览与发现、帖子、互动、智能助手、账户）
 * 作为路由嵌套的容器，子路由在 <Outlet /> 中渲染。
 */
export default function CommunityPortalLayout() {
  const linkBase = '/portal';
  const navItems = [
    { to: `${linkBase}/discover`, label: '浏览与发现' },
    { to: `${linkBase}/posts`, label: '帖子' },
    { to: `${linkBase}/interact`, label: '互动' },
    { to: `${linkBase}/assistant`, label: '智能助手' },
    { to: `${linkBase}/account`, label: '账户' },
  ];

  return (
    <div className="min-h-screen flex flex-col">
      <header className="shadow bg-white">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="text-lg font-semibold">企业 RAG 社区</div>
          <nav className="flex gap-4">
            {navItems.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) =>
                  `px-2 py-1 rounded ${isActive ? 'text-blue-600 font-semibold' : 'text-gray-700 hover:text-blue-600'}`
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t bg-gray-50 text-center text-sm text-gray-500 py-4">
        © {new Date().getFullYear()} RAG Community
      </footer>
    </div>
  );
}
