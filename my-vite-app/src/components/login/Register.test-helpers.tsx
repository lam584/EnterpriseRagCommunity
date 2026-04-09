/* eslint-disable react-refresh/only-export-components */
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import Register from './Register';

export function RegisterLoginPage() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  return <div>LOGIN:{state?.email ?? ''}</div>;
}

export function renderRegisterRoutes(options?: { includeLogin?: boolean }) {
  render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<Register />} />
        {options?.includeLogin ? <Route path="/login" element={<RegisterLoginPage />} /> : null}
      </Routes>
    </MemoryRouter>
  );
}

export function submitRegisterForm(fields?: {
  username?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
}) {
  fireEvent.change(screen.getByLabelText(/用户名/), {
    target: { name: 'username', value: fields?.username ?? 'testuser' },
  });
  fireEvent.change(screen.getByLabelText(/邮箱/), {
    target: { name: 'email', value: fields?.email ?? 'test@example.com' },
  });
  fireEvent.change(screen.getByLabelText(/^密码/), {
    target: { name: 'password', value: fields?.password ?? '123456' },
  });
  fireEvent.change(screen.getByLabelText(/确认密码/), {
    target: { name: 'confirmPassword', value: fields?.confirmPassword ?? fields?.password ?? '123456' },
  });
  fireEvent.click(screen.getByRole('button', { name: '注册' }));
}
