import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../../contexts/AuthContext';
import { logout } from '../../../../services/authService';
import { getMyProfile } from '../../../../services/accountService';
import {
  changeEmail,
  sendChangeEmailVerificationCode,
  sendOldEmailVerificationCode,
  verifyEmailChangePassword,
  verifyOldEmailOrTotp,
} from '../../../../services/emailChangeService';
import { getTotpStatus } from '../../../../services/totpAccountService';

export default function AccountConnectionsPage() {
  const navigate = useNavigate();
  const { setCurrentUser, setIsAuthenticated } = useAuth();

  const [currentEmail, setCurrentEmail] = useState<string>('');
  const [newEmail, setNewEmail] = useState('');
  const [newEmailCode, setNewEmailCode] = useState('');

  const [totpEnabled, setTotpEnabled] = useState(false);
  const [oldVerifyMethod, setOldVerifyMethod] = useState<'totp' | 'email'>('email');
  const [totpCode, setTotpCode] = useState('');
  const [oldEmailCode, setOldEmailCode] = useState('');

  const [password, setPassword] = useState('');
  const [passwordVerified, setPasswordVerified] = useState(false);
  const [verifyingPassword, setVerifyingPassword] = useState(false);

  const [oldVerified, setOldVerified] = useState(false);
  const [verifyingOld, setVerifyingOld] = useState(false);

  const [sendingOldCode, setSendingOldCode] = useState(false);
  const [oldCountdown, setOldCountdown] = useState(0);
  const [sendingNewCode, setSendingNewCode] = useState(false);
  const [newCountdown, setNewCountdown] = useState(0);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const trimmedNewEmail = useMemo(() => newEmail.trim(), [newEmail]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const [profile, totpStatus] = await Promise.all([
          getMyProfile(),
          getTotpStatus(),
        ]);
        if (!mounted) return;
        setCurrentEmail(profile.email);
        const enabled = Boolean(totpStatus?.enabled);
        setTotpEnabled(enabled);
        setOldVerifyMethod(enabled ? 'totp' : 'email');
      } catch (e) {
        const msg = e instanceof Error ? e.message : '加载失败';
        if (!mounted) return;
        setErrorMsg(msg);
      }
    })();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (oldCountdown <= 0) return;
    const t = window.setInterval(() => setOldCountdown((v) => Math.max(0, v - 1)), 1000);
    return () => window.clearInterval(t);
  }, [oldCountdown]);

  useEffect(() => {
    if (newCountdown <= 0) return;
    const t = window.setInterval(() => setNewCountdown((v) => Math.max(0, v - 1)), 1000);
    return () => window.clearInterval(t);
  }, [newCountdown]);

  useEffect(() => {
    setNewEmailCode('');
    setNewCountdown(0);
  }, [trimmedNewEmail]);

  const handleVerifyPassword = async () => {
    setErrorMsg(null);
    if (!password.trim()) {
      toast.error('请输入密码');
      return;
    }
    try {
      setVerifyingPassword(true);
      await verifyEmailChangePassword(password);
      setPasswordVerified(true);
      setOldVerified(false);
      setTotpCode('');
      setOldEmailCode('');
      setOldCountdown(0);
      setNewEmailCode('');
      setNewCountdown(0);
      toast.success('密码验证通过');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '密码验证失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setVerifyingPassword(false);
    }
  };

  const handleSendOldCode = async () => {
    setErrorMsg(null);
    if (!passwordVerified) return;
    try {
      setSendingOldCode(true);
      await sendOldEmailVerificationCode();
      setOldCountdown(180);
      toast.success('验证码已发送');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '发送失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSendingOldCode(false);
    }
  };

  const handleVerifyOld = async () => {
    setErrorMsg(null);
    try {
      if (!passwordVerified) return;
      setVerifyingOld(true);
      if (oldVerifyMethod === 'email') {
        if (!oldEmailCode.trim()) {
          toast.error('请输入旧邮箱验证码');
          return;
        }
        await verifyOldEmailOrTotp({ method: 'email', emailCode: oldEmailCode.trim() });
      } else {
        if (!totpCode.trim()) {
          toast.error('请输入动态验证码');
          return;
        }
        await verifyOldEmailOrTotp({ method: 'totp', totpCode: totpCode.trim() });
      }
      setOldVerified(true);
      toast.success('旧邮箱验证通过');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '验证失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setVerifyingOld(false);
    }
  };

  const handleSendNewCode = async () => {
    setErrorMsg(null);
    if (!oldVerified) return;
    if (!trimmedNewEmail) {
      toast.error('请输入新邮箱');
      return;
    }
    if (trimmedNewEmail === currentEmail) {
      toast.error('新邮箱不能与当前邮箱相同');
      return;
    }
    try {
      setSendingNewCode(true);
      await sendChangeEmailVerificationCode(trimmedNewEmail);
      setNewCountdown(180);
      toast.success('验证码已发送');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '发送失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSendingNewCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    if (!passwordVerified) {
      toast.error('请先验证密码');
      return;
    }
    if (!oldVerified) {
      toast.error('请先验证旧邮箱或动态验证码');
      return;
    }
    if (!trimmedNewEmail) {
      toast.error('请输入新邮箱');
      return;
    }
    if (trimmedNewEmail === currentEmail) {
      toast.error('新邮箱不能与当前邮箱相同');
      return;
    }
    if (!newEmailCode.trim()) {
      toast.error('请输入新邮箱验证码');
      return;
    }

    try {
      setSaving(true);
      await changeEmail({
        newEmail: trimmedNewEmail,
        newEmailCode: newEmailCode.trim(),
      });
      toast.success('邮箱更换成功，请重新登录');
      await new Promise((resolve) => window.setTimeout(resolve, 800));
      try {
        await logout();
      } catch {
      } finally {
        try {
          localStorage.removeItem('userData');
        } catch {
        }
        setCurrentUser(null);
        setIsAuthenticated(false);
        navigate('/login', { replace: true });
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : '更换邮箱失败';
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">更换邮箱</h3>

      <div className="border rounded-md p-4 space-y-3">
        <div className="text-sm text-gray-700">
          当前邮箱：<span className="font-medium">{currentEmail || '-'}</span>
        </div>

        {errorMsg ? <div className="text-sm text-red-600">{errorMsg}</div> : null}

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">步骤 1：验证密码</label>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">登录密码</label>
                <input
                  type="password"
                  value={password}
                  onChange={(ev) => setPassword(ev.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入登录密码"
                  autoComplete="current-password"
                />
              </div>
              <button
                type="button"
                disabled={verifyingPassword || saving}
                onClick={handleVerifyPassword}
                className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
              >
                {verifyingPassword ? '验证中...' : passwordVerified ? '已验证' : '验证密码'}
              </button>
            </div>
          </div>

          {passwordVerified ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">步骤 2：验证旧邮箱或动态验证码（二选一）</label>

              {totpEnabled ? (
                <div className="space-y-3">
                  <div className="flex gap-6">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="oldVerifyMethod"
                        checked={oldVerifyMethod === 'totp'}
                        onChange={() => setOldVerifyMethod('totp')}
                        className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">动态验证码</span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="oldVerifyMethod"
                        checked={oldVerifyMethod === 'email'}
                        onChange={() => setOldVerifyMethod('email')}
                        className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">旧邮箱验证码</span>
                    </label>
                  </div>

                  {oldVerifyMethod === 'totp' ? (
                    <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                        <input
                          type="text"
                          value={totpCode}
                          onChange={(ev) => setTotpCode(ev.target.value)}
                          inputMode="numeric"
                          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                          placeholder="请输入 6 或 8 位数字"
                        />
                      </div>
                      <button
                        type="button"
                        disabled={verifyingOld || oldVerified || saving}
                        onClick={handleVerifyOld}
                        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                      >
                        {verifyingOld ? '验证中...' : oldVerified ? '已验证' : '验证'}
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">旧邮箱验证码</label>
                          <input
                            type="text"
                            value={oldEmailCode}
                            onChange={(ev) => setOldEmailCode(ev.target.value)}
                            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            placeholder="请输入验证码"
                          />
                        </div>
                        <button
                          type="button"
                          disabled={sendingOldCode || saving || oldCountdown > 0}
                          onClick={handleSendOldCode}
                          className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                        >
                          {sendingOldCode ? '发送中...' : oldCountdown > 0 ? `${oldCountdown}s` : '发送验证码'}
                        </button>
                      </div>

                      <div className="flex justify-end">
                        <button
                          type="button"
                          disabled={verifyingOld || oldVerified || saving}
                          onClick={handleVerifyOld}
                          className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                        >
                          {verifyingOld ? '验证中...' : oldVerified ? '已验证' : '验证'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">旧邮箱验证码</label>
                      <input
                        type="text"
                        value={oldEmailCode}
                        onChange={(ev) => setOldEmailCode(ev.target.value)}
                        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="请输入验证码"
                      />
                    </div>
                    <button
                      type="button"
                      disabled={sendingOldCode || saving || oldCountdown > 0}
                      onClick={handleSendOldCode}
                      className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      {sendingOldCode ? '发送中...' : oldCountdown > 0 ? `${oldCountdown}s` : '发送验证码'}
                    </button>
                  </div>

                  <div className="flex justify-end">
                    <button
                      type="button"
                      disabled={verifyingOld || oldVerified || saving}
                      onClick={handleVerifyOld}
                      className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                    >
                      {verifyingOld ? '验证中...' : oldVerified ? '已验证' : '验证'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : null}

          {passwordVerified && oldVerified ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">步骤 3：验证新邮箱并完成更换</label>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">新邮箱</label>
                <input
                  type="email"
                  value={newEmail}
                  onChange={(ev) => setNewEmail(ev.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入要绑定的新邮箱"
                  autoComplete="email"
                />
              </div>

              <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">新邮箱验证码</label>
                  <input
                    type="text"
                    value={newEmailCode}
                    onChange={(ev) => setNewEmailCode(ev.target.value)}
                    className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="请输入验证码"
                  />
                </div>
                <button
                  type="button"
                  disabled={sendingNewCode || saving || newCountdown > 0}
                  onClick={handleSendNewCode}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                >
                  {sendingNewCode ? '发送中...' : newCountdown > 0 ? `${newCountdown}s` : '发送验证码'}
                </button>
              </div>
            </div>
          ) : null}

          <div className="flex gap-2 pt-1">
            {passwordVerified && oldVerified && (
              <button
                type="submit"
                disabled={saving}
                className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {saving ? '更换中...' : '确认更换'}
              </button>
            )}
            <button
              type="button"
              disabled={saving}
              onClick={() => navigate('/portal/account/security')}
              className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
            >
              去账号安全
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
