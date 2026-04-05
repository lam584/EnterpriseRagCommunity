import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { LoginPage, getAdminSetupServiceMocks } from './AdminSetup.test-fixtures';
import AdminSetup from './AdminSetup';
import { completeAdminSetupForm, resetAdminSetupMocks } from './AdminSetup.test-helpers';

const setupServiceMocks = getAdminSetupServiceMocks();

describe('AdminSetup (success navigation)', () => {
  beforeEach(() => {
    resetAdminSetupMocks(setupServiceMocks);
  });

  afterEach(() => {
    cleanup();
  });

  it('navigates to /login with setupJustCompleted state', async () => {
    render(
      <MemoryRouter initialEntries={['/admin-setup']}>
        <Routes>
          <Route path="/admin-setup" element={<AdminSetup />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    );

    await completeAdminSetupForm();
    fireEvent.click(screen.getByText('完成设置'));

    expect(await screen.findByText('LOGIN:BYPASS')).not.toBeNull();
    expect(setupServiceMocks.completeSetup).toHaveBeenCalledWith({
      email: 'admin@example.com',
      username: 'Admin',
      password: '123456',
    });
  });
});
