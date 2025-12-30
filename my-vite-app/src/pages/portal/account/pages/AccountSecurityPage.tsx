import {useState} from "react";
import { toast } from 'react-hot-toast';
import { changePassword } from '../../../../services/accountService';
import { validateChangePasswordForm } from './accountSecurity.validation';


export default function AccountSecurityPage() {
  const [oldPwd, setOldPwd] = useState('');
  const [newPwd, setNewPwd] = useState('');
  const [confirmNewPwd, setConfirmNewPwd] = useState('');
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const err = validateChangePasswordForm({ oldPwd, newPwd, confirmNewPwd });
    if (err) {
      setErrorMsg(err);
      toast.error(err);
      return;
    }

    try {
      setErrorMsg(null);
      setSaving(true);
      await changePassword({ currentPassword: oldPwd, newPassword: newPwd });
      toast.success('密码更新成功');
      setOldPwd('');
      setNewPwd('');
      setConfirmNewPwd('');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '密码更新失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">安全</h3>
        <p className="text-gray-600">这里放修改密码、设备管理等安全设置。</p>
      </div>

      {errorMsg && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {errorMsg}
        </div>
      )}

      <form className="space-y-3" onSubmit={handleSubmit}>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">旧密码</label>
          <input
            type="password"
            value={oldPwd}
            onChange={(e) => setOldPwd(e.target.value)}
            autoComplete="current-password"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">新密码</label>
          <input
            type="password"
            value={newPwd}
            onChange={(e) => setNewPwd(e.target.value)}
            autoComplete="new-password"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">确认新密码</label>
          <input
            type="password"
            value={confirmNewPwd}
            onChange={(e) => setConfirmNewPwd(e.target.value)}
            autoComplete="new-password"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <button
          type="submit"
          disabled={saving}
          className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {saving ? '更新中...' : '更新密码'}
        </button>
      </form>
    </div>
  );
}
