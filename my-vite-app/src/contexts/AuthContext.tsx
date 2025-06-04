import React, { createContext, useState, useContext, useEffect, ReactNode } from 'react';
import { AdminDTO, getCurrentAdmin } from '../services/authService';

interface AuthContextType {
  currentUser: AdminDTO | null;
  isAuthenticated: boolean;
  loading: boolean;
  setCurrentUser: (user: AdminDTO | null) => void;
  setIsAuthenticated: (isAuthenticated: boolean) => void;
  refreshAuth: () => Promise<boolean>; // 添加刷新认证状态的方法
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<AdminDTO | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);

  // 添加刷新认证状态的函数
  const refreshAuth = async (): Promise<boolean> => {
    try {
      const user = await getCurrentAdmin();
      setCurrentUser(user);
      setIsAuthenticated(true);
      console.log("认证刷新成功，当前用户:", user.username);
      return true;
    } catch (error) {
      console.error("认证刷新失败:", error);
      setCurrentUser(null);
      setIsAuthenticated(false);
      return false;
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const checkAuthStatus = async () => {
      setLoading(true);
      try {
        // 尝试获取当前用户信息
        await refreshAuth();
      } catch (error) {
        console.error("初始认证检查失败:", error);
        setCurrentUser(null);
        setIsAuthenticated(false);
      } finally {
        setLoading(false);
      }
    };

    checkAuthStatus();
  }, []);

  return (
    <AuthContext.Provider value={{
      currentUser,
      isAuthenticated,
      loading,
      setCurrentUser,
      setIsAuthenticated,
      refreshAuth
    }}>
      {children}
    </AuthContext.Provider>
  );
};
