import { NavLink, Outlet } from 'react-router-dom';
import { FaClipboardList, FaCheckCircle, FaMagic, FaSearch, FaChartLine, FaUsersCog, FaTools } from 'react-icons/fa';

/**
 * 后台管理布局（统一风格）：采用与 NewsSystemLayout 相同的左侧紫色渐变侧边栏
 * 菜单：内容管理、审核中心、语义增强、检索与 RAG、评估与监控、用户与权限
 */
export default function AdminDashboardLayout() {
  const linkBase = '/admin';
  const navItems = [
    { to: `${linkBase}/content`, label: '内容管理', icon: <FaClipboardList /> },
    { to: `${linkBase}/review`, label: '审核中心', icon: <FaCheckCircle /> },
    { to: `${linkBase}/semantic`, label: '语义增强', icon: <FaMagic /> },
    { to: `${linkBase}/retrieval`, label: '检索与 RAG', icon: <FaSearch /> },
    { to: `${linkBase}/metrics`, label: '评估与监控', icon: <FaChartLine /> },
    { to: `${linkBase}/users`, label: '用户与权限', icon: <FaUsersCog /> },
  ];

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
        <div className="p-4 border-t border-purple-800 text-sm text-purple-200">
            © {new Date().getFullYear()} 后台管理
            <div className="pt-2">
                <a
                    href="http://127.0.0.1:8099"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-blue-600 hover:underline"
                >
                    点击进入前台
                </a>
            </div>
        </div>

      </aside>

      {/* Main Content */}
      <div className="flex-1 overflow-auto">
        <Outlet />
      </div>
    </div>
  );
}
