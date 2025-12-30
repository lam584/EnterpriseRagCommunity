export interface ChangePasswordForm {
  oldPwd: string;
  newPwd: string;
  confirmNewPwd: string;
}

export function validateChangePasswordForm(form: ChangePasswordForm): string | null {
  const oldPwd = form.oldPwd.trim();
  const newPwd = form.newPwd;
  const confirmNewPwd = form.confirmNewPwd;

  if (!oldPwd) return '请输入旧密码';
  if (!newPwd) return '请输入新密码';
  if (!confirmNewPwd) return '请再次输入新密码';
  if (newPwd !== confirmNewPwd) return '两次输入的新密码不一致';
  if (newPwd.length < 6) return '新密码长度至少 6 位';
  if (newPwd === oldPwd) return '新密码不能与旧密码相同';

  return null;
}

