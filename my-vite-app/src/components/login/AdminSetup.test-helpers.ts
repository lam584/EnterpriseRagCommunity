import { fireEvent, screen } from '@testing-library/react';
import { vi } from 'vitest';

export type AdminSetupServiceMocks = {
  checkEnvFile: ReturnType<typeof vi.fn>;
  saveSetupConfig: ReturnType<typeof vi.fn>;
  initIndices: ReturnType<typeof vi.fn>;
  completeSetup: ReturnType<typeof vi.fn>;
};

export function resetAdminSetupMocks(setupServiceMocks: AdminSetupServiceMocks) {
  vi.clearAllMocks();
  setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: false });
  setupServiceMocks.saveSetupConfig.mockResolvedValue(undefined);
  setupServiceMocks.initIndices.mockResolvedValue(undefined);
  setupServiceMocks.completeSetup.mockResolvedValue(undefined);
}

export async function completeAdminSetupForm() {
  fireEvent.click(screen.getByText('跳过'));
  await screen.findByText('创建管理员账户');

  fireEvent.change(screen.getByPlaceholderText('请输入管理员邮箱'), { target: { value: 'admin@example.com' } });
  fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'Admin' } });
  fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: '123456' } });
  fireEvent.change(screen.getByPlaceholderText('请再次输入密码'), { target: { value: '123456' } });
}
