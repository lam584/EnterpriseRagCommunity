// src/components/account-management/Logout.tsx
import React, { useState } from 'react';
import { logout } from '../../services/authService';
import { useAuth } from '../../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';

const Logout: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { setCurrentUser, setIsAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    setLoading(true);
    setError(null);

    try {
      // 调用 authService 中的 logout 方法
      await logout();

      // 清除认证上下文
      setCurrentUser(null);
      setIsAuthenticated(false);

      // 清除本地存储
      localStorage.removeItem('authToken');
      sessionStorage.removeItem('userSession');

      // 导航回登录页面
      navigate('/login');
    } catch (err) {
      setError(err instanceof Error ? err.message : '退出登录失败');
      console.error('退出登录出错:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white shadow-md rounded-md p-6">
      <h2 className="text-xl font-bold mb-4">退出登录</h2>
      <div className="bg-gray-50 p-6 rounded-lg">
        <div className="text-center">
          <p className="text-gray-600 mb-4">您确定要退出登录吗？</p>
          {error && (
            <div className="text-red-500 mb-4">
              {error}
            </div>
          )}
          <button
            className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-md disabled:bg-red-300"
            onClick={handleLogout}
            disabled={loading}
          >
            {loading ? '退出中...' : '退出登录'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Logout;
