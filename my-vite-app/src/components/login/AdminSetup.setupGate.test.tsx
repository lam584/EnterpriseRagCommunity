import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';

import { LoginPage, getAdminSetupServiceMocks } from './AdminSetup.test-fixtures';
import AdminSetup from './AdminSetup';
import { completeAdminSetupForm, resetAdminSetupMocks } from './AdminSetup.test-helpers';

const setupServiceMocks = getAdminSetupServiceMocks();

function SetupGateRoutes() {
  const setupRequired = true;

  return (
    <Routes>
      <Route path="/admin-setup" element={<AdminSetup />} />
      <Route
        path="/login"
        element={
          setupRequired ? (
            <LoginBypassGate />
          ) : (
            <LoginPage />
          )
        }
      />
    </Routes>
  );
}

function LoginBypassGate() {
  const location = useLocation();
  const state = location.state as { setupJustCompleted?: boolean } | null;
  if (state?.setupJustCompleted) return <LoginPage />;
  return <Navigate to="/admin-setup" replace />;
}

describe('AdminSetup (with setup gate)', () => {
  beforeEach(() => {
    resetAdminSetupMocks(setupServiceMocks);
  });

  afterEach(() => {
    cleanup();
  });

  it('navigates to /login even when /login is guarded by setupRequired', async () => {
    render(
      <MemoryRouter initialEntries={['/admin-setup']}>
        <SetupGateRoutes />
      </MemoryRouter>
    );

    await completeAdminSetupForm();
    fireEvent.click(screen.getByText('完成设置'));

    expect(await screen.findByText('LOGIN:BYPASS')).not.toBeNull();
  });
});
