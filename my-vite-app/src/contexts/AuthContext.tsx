//my-vite-app/src/contexts/AuthContext.tsx
import React, { createContext, useState, useContext, useEffect, ReactNode, useRef } from 'react';
import { AdminDTO, getCurrentAdmin } from '../services/authService';
import { getMySecurity2faPolicy } from '../services/security2faPolicyAccountService';
import { getTotpStatus } from '../services/totpAccountService';

interface AuthContextType {
  currentUser: AdminDTO | null;
  isAuthenticated: boolean;
  loading: boolean;
  totpSetupRequired: boolean;
  setCurrentUser: (user: AdminDTO | null) => void;
  setIsAuthenticated: (isAuthenticated: boolean) => void;
  refreshAuth: () => Promise<boolean>; // 添加刷新认证状态的方法
  refreshSecurityGate: () => Promise<void>;
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
  const [totpSetupRequired, setTotpSetupRequired] = useState<boolean>(false);

  // Dedupe concurrent refreshes to avoid auth "flapping" causing redirect storms.
  const inFlightRefreshRef = useRef<Promise<boolean> | null>(null);

  // 添加刷新认证状态的函数
  const refreshAuth = (): Promise<boolean> => {
    if (inFlightRefreshRef.current) return inFlightRefreshRef.current;

    setLoading(true);

    const p: Promise<boolean> = (async () => {
      try {
        const user = await getCurrentAdmin();
        setCurrentUser(user);
        setIsAuthenticated(true);
        try {
          const [policy, status] = await Promise.all([getMySecurity2faPolicy(), getTotpStatus()]);
          const required = Boolean(policy?.totpRequired) && !Boolean(status?.enabled);
          setTotpSetupRequired(required);
        } catch {
          setTotpSetupRequired(false);
        }
        console.log("认证刷新成功，当前用户:", user.username);
        return true;
      } catch (error) {
        console.error("认证刷新失败:", error);
        setCurrentUser(null);
        setIsAuthenticated(false);
        setTotpSetupRequired(false);
        return false;
      } finally {
        setLoading(false);
        inFlightRefreshRef.current = null;
      }
    })();

    inFlightRefreshRef.current = p;
    return p;
  };

  useEffect(() => {
    // Initial auth check. refreshAuth already manages loading.
    void refreshAuth();
  }, []);

  const refreshSecurityGate = async (): Promise<void> => {
    try {
      if (!isAuthenticated) {
        setTotpSetupRequired(false);
        return;
      }
      const [policy, status] = await Promise.all([getMySecurity2faPolicy(), getTotpStatus()]);
      const required = Boolean(policy?.totpRequired) && !Boolean(status?.enabled);
      setTotpSetupRequired(required);
    } catch {
      setTotpSetupRequired(false);
    }
  };

  return (
    <AuthContext.Provider value={{
      currentUser,
      isAuthenticated,
      loading,
      totpSetupRequired,
      setCurrentUser,
      setIsAuthenticated,
      refreshAuth,
      refreshSecurityGate
    }}>
      {children}
    </AuthContext.Provider>
  );
};
