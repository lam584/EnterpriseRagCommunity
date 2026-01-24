// src/components/login/AdminSetup.tsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { registerInitialAdmin, InitialAdminRegisterRequest, type TotpMasterKeySetupResult } from '../../services/authService';

type AdminSetupProps = {
  onGoLogin?: (email: string) => void;
};

const AdminSetup: React.FC<AdminSetupProps> = ({ onGoLogin }) => {
  const navigate = useNavigate();
  const [form, setForm] = useState<InitialAdminRegisterRequest>({
    email: '',
    password: '',
    username: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [totpSetup, setTotpSetup] = useState<TotpMasterKeySetupResult | null>(null);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // 简单前端校验
    if (!form.email || !form.password || !form.username) {
      setError('请填写邮箱、密码和显示名称');
      return;
    }

    try {
      setLoading(true);
      const res = await registerInitialAdmin({
        email: form.email.trim(),
        password: form.password,
        username: form.username.trim()
      });
      setTotpSetup(res?.totpMasterKeySetup ?? null);
      setSuccess('初始化管理员注册成功，请前往登录（如已自动写入 TOTP 主密钥，请重启后端后再启用二次验证）');
    } catch (err) {
      setError(err instanceof Error ? err.message : '初始化失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGoLogin = () => {
    const email = form.email.trim();
    if (onGoLogin) {
      onGoLogin(email);
      return;
    }
    navigate('/login', { state: { email, setupJustCompleted: true } });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white p-8 rounded shadow w-full max-w-md">
        <h1 className="text-2xl font-bold mb-4">初始化管理员</h1>
        {error && <div className="text-red-600 mb-2">{error}</div>}
        {success && (
          <div className="bg-green-100 text-green-800 p-3 rounded mb-4 flex items-start justify-between gap-3">
            <div className="flex-1 space-y-2">
              <div>{success}</div>
              {totpSetup ? (
                <div className="rounded border border-green-200 bg-white/60 p-2 text-xs text-green-900 space-y-1">
                  <div>APP_TOTP_MASTER_KEY：{totpSetup.succeeded ? '已写入' : '未写入'}</div>
                  {totpSetup.message ? <div>{totpSetup.message}</div> : null}
                  {totpSetup.command ? (
                    <div className="font-mono break-all">命令：{totpSetup.command}</div>
                  ) : null}
                  {totpSetup.keyBase64 ? (
                    <div className="font-mono break-all">Key(Base64)：{totpSetup.keyBase64}</div>
                  ) : null}
                  {totpSetup.restartRequired ? <div>提示：需要重启后端进程后才会生效</div> : null}
                </div>
              ) : null}
            </div>
            <button
              type="button"
              onClick={handleGoLogin}
              className="shrink-0 inline-flex items-center px-3 py-1.5 rounded-md text-sm font-medium bg-green-600 text-white hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500"
            >
              去登录
            </button>
          </div>
        )}
        {!success && (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div>
              <label className="block mb-1">邮箱</label>
              <input type="email" name="email" value={form.email} onChange={handleChange} className="w-full border p-2 rounded" required />
            </div>
            <div>
              <label className="block mb-1">显示名称</label>
              <input name="username" value={form.username} onChange={handleChange} className="w-full border p-2 rounded" required />
            </div>
            <div>
              <label className="block mb-1">密码</label>
              <input type="password" name="password" value={form.password} onChange={handleChange} className="w-full border p-2 rounded" required />
            </div>
            <button type="submit" disabled={loading} className="w-full bg-blue-600 text-white py-2 rounded">
              {loading ? '提交中...' : '提交'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default AdminSetup;
