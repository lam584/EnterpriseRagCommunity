import React, { useEffect } from 'react';
import { Navigate, Outlet, useLocation, useOutletContext } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useAccess } from '../../contexts/AccessContext';

export type RequireAccessProps = {
  /** If true, requires the user to be logged in (session valid). */
  requiresAuth?: boolean;

  /** Optional RBAC permission requirement, `resource:action`. */
  resource?: string;
  action?: string;

  /** Optional escape hatch: allow access if user has any of these roles. */
  allowRoles?: string[];

  /** Where to send user when not authenticated. Default: /login */
  loginPath?: string;

  /** Where to send user when authenticated but forbidden. Default: /forbidden */
  forbiddenPath?: string;
};

/**
 * Shared route guard for both portal and admin.
 *
 * Rules:
 * - if requiresAuth and not authenticated => redirect to /login
 * - if permission configured and user lacks it (and not allowed by allowRoles) => redirect to /forbidden
 * - otherwise => render children routes
 */
export const RequireAccess: React.FC<RequireAccessProps> = ({
  requiresAuth = true,
  resource,
  action,
  allowRoles,
  loginPath = '/login',
  forbiddenPath = '/forbidden'
}) => {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const { hasPerm, hasRole, loading: accessLoading, refresh } = useAccess();
  const location = useLocation();
  const parentOutletContext = useOutletContext<unknown>();

  useEffect(() => {
    if (!isAuthenticated) return;
    void refresh();
  }, [isAuthenticated, refresh]);

  if (authLoading || accessLoading) {
    return <div className="flex justify-center items-center h-screen">加载中...</div>;
  }

  if (requiresAuth && !isAuthenticated) {
    return <Navigate to={loginPath} replace state={{ from: location }} />;
  }

  const hasPermRequirement = Boolean(resource && action);
  const roleOk = Array.isArray(allowRoles) && allowRoles.length > 0
    ? allowRoles.some(r => hasRole(r))
    : false;

  if (hasPermRequirement && !hasPerm(resource!, action!) && !roleOk) {
    return <Navigate to={forbiddenPath} replace state={{ from: location }} />;
  }

  return <Outlet context={parentOutletContext} />;
};
