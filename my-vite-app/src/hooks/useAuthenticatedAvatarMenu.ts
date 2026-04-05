import { useCallback, useEffect, useRef, useState } from 'react';
import { logout } from '../services/authService';
import { useProfileAvatarUrl } from './useProfileAvatarUrl';

export function useAuthenticatedAvatarMenu(options: {
  isAuthenticated: boolean;
  setCurrentUser: (value: null) => void;
  setIsAuthenticated: (value: boolean) => void;
}) {
  const profileAvatarUrl = useProfileAvatarUrl(options.isAuthenticated);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function onDocumentMouseDown(e: MouseEvent) {
      if (!userMenuOpen) return;
      const element = userMenuRef.current;
      if (!element) return;
      if (e.target instanceof Node && !element.contains(e.target)) {
        setUserMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', onDocumentMouseDown);
    return () => document.removeEventListener('mousedown', onDocumentMouseDown);
  }, [userMenuOpen]);

  const handleLogout = useCallback(async () => {
    try {
      await logout();
    } finally {
      try {
        localStorage.removeItem('userData');
      } catch {
        // ignore
      }
      options.setCurrentUser(null);
      options.setIsAuthenticated(false);
      setUserMenuOpen(false);
      window.location.href = '/login';
    }
  }, [options]);

  return {
    profileAvatarUrl,
    userMenuOpen,
    userMenuRef,
    setUserMenuOpen,
    handleLogout,
  };
}
