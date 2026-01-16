// my-vite-app/src/components/auth/RequirePermission.tsx

import React, { useEffect } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useAccess } from '../../contexts/AccessContext';

type RequirePermissionProps = {
  resource: string;
  action: string;
  /**
   * Optional escape hatch: allow access if user has any of these roles.
   * Useful during bootstrap when permissions may not be fully seeded.
   */
  allowRoles?: string[];
  redirectTo?: string;
};

export const RequirePermission: React.FC<RequirePermissionProps> = ({ resource, action, allowRoles, redirectTo }) => {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const { hasPerm, hasRole, loading: accessLoading, refresh } = useAccess();
  const location = useLocation();

  useEffect(() => {
    if (!isAuthenticated) return;
    void refresh();
  }, [isAuthenticated, refresh]);

  if (authLoading || accessLoading) {
    return <div className="flex justify-center items-center h-screen">加载中...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const roleOk = Array.isArray(allowRoles) && allowRoles.length > 0
    ? allowRoles.some(r => hasRole(r))
    : false;

  if (!hasPerm(resource, action) && !roleOk) {
    // Default: go to a dedicated 403 page so users understand what's happening.
    return <Navigate to={redirectTo ?? '/forbidden'} replace state={{ from: location }} />;
  }

  return <Outlet />;
};
