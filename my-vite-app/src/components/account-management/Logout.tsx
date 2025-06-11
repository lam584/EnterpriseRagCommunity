import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { logout } from '../../services/authService';
import { toast } from 'react-hot-toast';

interface LogoutProps {
  onBack: () => void;
}

const Logout: React.FC<LogoutProps> = ({ onBack }) => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      setLoading(true);
      await logout();
      toast.success('您已成功退出登录');

      // 重定向到登录页面
      setTimeout(() => {
        navigate('/login');
      }, 1000);
    } catch (err) {
      console.error('Failed to logout:', err);
      toast.error(err instanceof Error ? err.message : '退出登录失败');
      setLoading(false);
    }
  };


  return (
    <div className="max-w-lg mx-auto bg-white p-6 rounded-lg shadow-md">
      <h2 className="text-xl font-bold mb-4">退出登录</h2>

      <p className="text-gray-600 mb-6">您确定要退出登录吗？</p>

      <div className="flex space-x-4">
        <button
          onClick={handleLogout}
          className="bg-red-500 text-white px-4 py-2 rounded-md hover:bg-red-600 disabled:bg-gray-400"
          disabled={loading}
        >
          {loading ? '处理中...' : '确认退出'}
        </button>
        <button
          onClick={onBack}
          className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600 disabled:bg-gray-400"
        >
          返回
        </button>
      </div>
    </div>
  );
};

export default Logout;