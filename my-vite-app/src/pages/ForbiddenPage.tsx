import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

type FromLocationState = {
  from?: {
    pathname?: string;
  };
};

export default function ForbiddenPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { setCurrentUser, setIsAuthenticated } = useAuth();
  const state = (location.state ?? null) as FromLocationState | null;
  const attemptedPath = state?.from?.pathname || location.pathname;

  const handleSwitchAccount = async () => {
    try {
      // Lazy import avoids circular deps if any and keeps this page lightweight.
      const [{ logout }] = await Promise.all([
        import('../services/authService'),
      ]);

      await logout();

      // Clear local auth state so route guards treat user as logged out.
      setCurrentUser(null);
      setIsAuthenticated(false);
    } catch (e) {
      // Even if backend logout fails, still try to let user re-login.
       
      console.warn('logout failed, force navigate to /login', e);
    } finally {
      navigate('/login', { replace: true });
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-gray-900">没有权限（403）</h1>
        <p className="mt-2 text-sm text-gray-600">
          你已登录，但当前账号没有访问该页面的权限。
        </p>
        <div className="mt-3 rounded-md bg-gray-50 p-3 text-xs text-gray-700">
          <div className="font-medium">尝试访问：</div>
          <div className="break-all">{attemptedPath}</div>
        </div>

        <div className="mt-5 flex gap-3">
          <Link
            to="/portal/discover"
            className="inline-flex items-center justify-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            返回前台首页
          </Link>
          <button
            type="button"
            onClick={handleSwitchAccount}
            className="inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            切换账号
          </button>
        </div>

        <p className="mt-4 text-xs text-gray-500">
          如果你认为这是误判，请联系管理员为你授予相应权限（例如：admin_ui:access）。
        </p>
      </div>
    </div>
  );
}
