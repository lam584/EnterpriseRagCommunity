import {useEffect, useMemo, useState} from "react";
import { toast } from 'react-hot-toast';
import { QRCodeSVG } from 'qrcode.react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../../contexts/AuthContext';
import { logout } from '../../../../services/authService';
import { changePassword } from '../../../../services/accountService';
import { sendAccountEmailVerificationCode } from '../../../../services/emailVerificationService';
import { validateChangePasswordForm } from './accountSecurity.validation';
import {
  disableTotp,
  enrollTotp,
  getTotpPolicy,
  getTotpStatus,
  type TotpEnrollResponse,
  type TotpStatusResponse,
  verifyTotp,
} from '../../../../services/totpAccountService';
import type { TotpAdminSettingsDTO } from '../../../../services/totpAdminService';


export default function AccountSecurityPage() {
  const navigate = useNavigate();
  const { setCurrentUser, setIsAuthenticated } = useAuth();

  const [oldPwd, setOldPwd] = useState('');
  const [newPwd, setNewPwd] = useState('');
  const [confirmNewPwd, setConfirmNewPwd] = useState('');
  const [changeTotpCode, setChangeTotpCode] = useState('');
  const [changeEmailCode, setChangeEmailCode] = useState('');
  const [sendingChangeEmailCode, setSendingChangeEmailCode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [totpPolicy, setTotpPolicy] = useState<TotpAdminSettingsDTO | null>(null);
  const [totpStatus, setTotpStatus] = useState<TotpStatusResponse | null>(null);
  const [totpLoading, setTotpLoading] = useState(false);
  const [totpErrorMsg, setTotpErrorMsg] = useState<string | null>(null);
  const [totpEnrollSaving, setTotpEnrollSaving] = useState(false);
  const [totpVerifySaving, setTotpVerifySaving] = useState(false);
  const [totpDisableSaving, setTotpDisableSaving] = useState(false);

  const [enrollAlg, setEnrollAlg] = useState('SHA1');
  const [enrollDigits, setEnrollDigits] = useState(6);
  const [enrollPeriod, setEnrollPeriod] = useState(30);
  const [enrollSkew, setEnrollSkew] = useState(1);

  const [enrollResult, setEnrollResult] = useState<TotpEnrollResponse | null>(null);
  const [verifyCode, setVerifyCode] = useState('');
  const [verifyPassword, setVerifyPassword] = useState('');
  const [totpEmailCode, setTotpEmailCode] = useState('');
  const [sendingTotpEmailCode, setSendingTotpEmailCode] = useState(false);
  const [disableCode, setDisableCode] = useState('');

  const [showDisableInput, setShowDisableInput] = useState(false);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [pwdStep, setPwdStep] = useState(0);
  const [verifyMethod, setVerifyMethod] = useState<'totp' | 'email'>('totp');

  // Countdown state for email verification codes
  const [totpEmailCountdown, setTotpEmailCountdown] = useState(0);
  const [pwdEmailCountdown, setPwdEmailCountdown] = useState(0);

  useEffect(() => {
    let timer: number | undefined;
    if (totpEmailCountdown > 0) {
      timer = window.setInterval(() => {
        setTotpEmailCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [totpEmailCountdown]);

  useEffect(() => {
    let timer: number | undefined;
    if (pwdEmailCountdown > 0) {
      timer = window.setInterval(() => {
        setPwdEmailCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [pwdEmailCountdown]);

  useEffect(() => {
    // If TOTP is enabled, default to TOTP, otherwise Email
    if (totpStatus?.enabled) {
      setVerifyMethod('totp');
    } else {
      setVerifyMethod('email');
    }
  }, [totpStatus?.enabled]);

  const enabled = Boolean(totpStatus?.enabled);
  const masterKeyConfigured = totpStatus?.masterKeyConfigured !== false;

  const maxSkew = useMemo(() => {
    const v = typeof totpPolicy?.maxSkew === 'number' ? totpPolicy.maxSkew : 2;
    if (!Number.isFinite(v)) return 2;
    return Math.max(0, Math.min(10, Math.trunc(v)));
  }, [totpPolicy?.maxSkew]);

  useEffect(() => {
    void (async () => {
      setTotpLoading(true);
      try {
        const [policy, status] = await Promise.all([getTotpPolicy(), getTotpStatus()]);
        setTotpPolicy(policy);
        setTotpStatus(status);
        setEnrollAlg(String(policy.defaultAlgorithm ?? 'SHA1'));
        setEnrollDigits(Number(policy.defaultDigits ?? 6) || 6);
        setEnrollPeriod(Number(policy.defaultPeriodSeconds ?? 30) || 30);
        setEnrollSkew(Number(policy.defaultSkew ?? 1) || 1);
      } catch (e) {
        const msg = e instanceof Error ? e.message : '加载 TOTP 信息失败';
        setTotpErrorMsg(msg);
        toast.error(msg);
      } finally {
        setTotpLoading(false);
      }
    })();
  }, []);

  const refreshTotpStatus = async () => {
    setTotpLoading(true);
    try {
      setTotpErrorMsg(null);
      const status = await getTotpStatus();
      setTotpStatus(status);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '刷新失败';
      setTotpErrorMsg(msg);
      toast.error(msg);
    } finally {
      setTotpLoading(false);
    }
  };

  const copyText = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success('已复制');
    } catch {
      toast.error('复制失败');
    }
  };

  const handleNext = () => {
    const err = validateChangePasswordForm({ oldPwd, newPwd, confirmNewPwd });
    if (err) {
      setErrorMsg(err);
      toast.error(err);
      return;
    }
    setErrorMsg(null);
    setPwdStep(1);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const err = validateChangePasswordForm({ oldPwd, newPwd, confirmNewPwd });
    if (err) {
      setErrorMsg(err);
      toast.error(err);
      return;
    }

    // Check verification code
    if (enabled) {
      if (verifyMethod === 'totp') {
        if (!changeTotpCode.trim()) {
          setErrorMsg('请输入动态验证码');
          toast.error('请输入动态验证码');
          return;
        }
      } else {
        if (!changeEmailCode.trim()) {
          setErrorMsg('请输入邮箱验证码');
          toast.error('请输入邮箱验证码');
          return;
        }
      }
    } else {
      // TOTP not enabled, must use email
      if (!changeEmailCode.trim()) {
        setErrorMsg('请输入邮箱验证码');
        toast.error('请输入邮箱验证码');
        return;
      }
    }

    try {
      setErrorMsg(null);
      setSaving(true);
      await changePassword({
        currentPassword: oldPwd,
        newPassword: newPwd,
        totpCode: (enabled && verifyMethod === 'totp') ? changeTotpCode.trim() : undefined,
        emailCode: (!enabled || verifyMethod === 'email') ? changeEmailCode.trim() : undefined,
      });
      toast.success('密码修改成功，请重新登录');
      setOldPwd('');
      setNewPwd('');
      setConfirmNewPwd('');
      setChangeTotpCode('');
      setChangeEmailCode('');
      setShowPasswordForm(false);
      setPwdStep(0);

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

      <div className="rounded-lg border bg-white p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-lg font-semibold">二次验证（TOTP）</div>
            <div className="text-sm text-gray-600">
              使用认证器应用（Google Authenticator / Microsoft Authenticator 等）生成动态验证码
            </div>
          </div>
          <button
            type="button"
            onClick={refreshTotpStatus}
            disabled={totpLoading}
            className="px-3 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {totpLoading ? '刷新中...' : '刷新'}
          </button>
        </div>

        <div className="text-sm">
          当前状态：{enabled ? '已启用' : '未启用'}
          {enabled && totpStatus?.verifiedAt ? <span className="text-gray-500">（verifiedAt={totpStatus.verifiedAt}）</span> : null}
        </div>

        {totpStatus?.masterKeyConfigured === false ? (
          <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            管理员尚未配置 TOTP 主密钥（app.security.totp.master-key），暂不可生成密钥。
          </div>
        ) : null}

        {totpErrorMsg ? (
          <div
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            role="alert"
            data-testid="totp-error"
          >
            {totpErrorMsg}
          </div>
        ) : null}

        {!enabled ? (
          <div className="space-y-3">
            {(String(enrollAlg).toUpperCase() !== 'SHA1' || Number(enrollDigits) !== 6 || Number(enrollPeriod) !== 30) ? (
              <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                提示：当使用 SHA256/SHA512/8 位时，请优先用“扫码”绑定。注：Microsoft Authenticator只支持SHA1；如需60秒步长请使用支持自定义算法的认证器（例如Aegis / 2FAS）。
              </div>
            ) : null}
            <div className="grid grid-cols-1 gap-3 md:grid-cols-4 md:items-end">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">算法</label>
                <select
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                  value={enrollAlg}
                  onChange={(e) => setEnrollAlg(e.target.value)}
                  disabled={totpEnrollSaving || totpLoading}
                >
                  {(totpPolicy?.allowedAlgorithms?.length ? totpPolicy.allowedAlgorithms : ['SHA1', 'SHA256', 'SHA512']).map(a => (
                    <option key={a} value={String(a)}>
                      {String(a)}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">位数</label>
                <select
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                  value={String(enrollDigits)}
                  onChange={(e) => setEnrollDigits(Number(e.target.value))}
                  disabled={totpEnrollSaving || totpLoading}
                >
                  {(totpPolicy?.allowedDigits?.length ? totpPolicy.allowedDigits : [6, 8]).map(d => (
                    <option key={d} value={String(d)}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">步长（秒）</label>
                <select
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                  value={String(enrollPeriod)}
                  onChange={(e) => setEnrollPeriod(Number(e.target.value))}
                  disabled={totpEnrollSaving || totpLoading}
                >
                  {(totpPolicy?.allowedPeriodSeconds?.length ? totpPolicy.allowedPeriodSeconds : [30, 60]).map(p => (
                    <option key={p} value={String(p)}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">skew（0..{maxSkew}）</label>
                <select
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                  value={String(enrollSkew)}
                  onChange={(e) => setEnrollSkew(Number(e.target.value))}
                  disabled={totpEnrollSaving || totpLoading}
                >
                  {Array.from({ length: maxSkew + 1 }).map((_, i) => (
                    <option key={i} value={String(i)}>
                      {i}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <button
              type="button"
              disabled={totpEnrollSaving || totpLoading || !masterKeyConfigured}
              onClick={async () => {
                try {
                  setTotpEnrollSaving(true);
                  setTotpErrorMsg(null);
                  setEnrollResult(null);
                  const res = await enrollTotp({
                    algorithm: enrollAlg,
                    digits: enrollDigits,
                    periodSeconds: enrollPeriod,
                    skew: enrollSkew,
                  });
                  setEnrollResult(res);
                  toast.success('密钥已生成，请用认证器应用绑定');
                } catch (e) {
                  const msg = e instanceof Error ? e.message : '生成失败';
                  setTotpErrorMsg(msg);
                  toast.error(msg);
                } finally {
                  setTotpEnrollSaving(false);
                }
              }}
              className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {totpEnrollSaving ? '生成密钥中...' : '启用TOTP'}
            </button>

            {enrollResult ? (
              <div className="rounded-md border bg-gray-50 p-3 space-y-2">
                <div className="text-sm font-medium">绑定信息</div>
                <div className="grid grid-cols-1 gap-3 md:grid-cols-[auto_1fr] md:items-start">
                  <div className="rounded-md border bg-white p-3 w-fit">
                    <div className="text-xs text-gray-500 mb-2">扫码绑定</div>
                    <div data-testid="totp-qr">
                      <QRCodeSVG value={enrollResult.otpauthUri} size={180} />
                    </div>
                    <div className="text-xs text-gray-500 mt-2">在认证器应用中选择“扫码添加”（推荐使用Google Authenticator）。</div>
                  </div>
                  <div className="space-y-2">
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  <div className="space-y-1">
                    <div className="text-xs text-gray-500">Secret (Base32)</div>
                    <div className="flex gap-2">
                      <input
                        value={enrollResult.secretBase32}
                        readOnly
                        className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm"
                      />
                      <button
                        type="button"
                        className="px-3 py-2 rounded-md border bg-white hover:bg-gray-50"
                        onClick={() => void copyText(enrollResult.secretBase32)}
                      >
                        复制
                      </button>
                    </div>
                  </div>
                  <div className="space-y-1">
                    <div className="text-xs text-gray-500">otpauth URI</div>
                    <div className="flex gap-2">
                      <input
                        value={enrollResult.otpauthUri}
                        readOnly
                        className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm"
                      />
                      <button
                        type="button"
                        className="px-3 py-2 rounded-md border bg-white hover:bg-gray-50"
                        onClick={() => void copyText(enrollResult.otpauthUri)}
                      >
                        复制
                      </button>
                    </div>
                  </div>
                </div>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_1fr_auto] md:items-end">
                  <div className="md:col-span-2 grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                      <input
                        value={totpEmailCode}
                        onChange={(e) => {
                          setTotpEmailCode(e.target.value);
                          if (totpErrorMsg) setTotpErrorMsg(null);
                        }}
                        className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                        placeholder="开启邮箱验证时必填"
                      />
                    </div>
                    <button
                      type="button"
                      disabled={sendingTotpEmailCode || totpVerifySaving || totpEmailCountdown > 0}
                      onClick={async () => {
                        try {
                          setSendingTotpEmailCode(true);
                          await sendAccountEmailVerificationCode('TOTP_ENABLE');
                          setTotpEmailCountdown(180);
                          toast.success('验证码已发送');
                        } catch (e) {
                          const msg = e instanceof Error ? e.message : '发送失败';
                          setTotpErrorMsg(msg);
                          toast.error(msg);
                        } finally {
                          setSendingTotpEmailCode(false);
                        }
                      }}
                      className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                    >
                      {sendingTotpEmailCode ? '发送中...' : totpEmailCountdown > 0 ? `${totpEmailCountdown}s` : '发送验证码'}
                    </button>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">输入验证码以启用</label>
                    <input
                      value={verifyCode}
                      onChange={(e) => {
                        setVerifyCode(e.target.value);
                        if (totpErrorMsg) setTotpErrorMsg(null);
                      }}
                      className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                      placeholder="6 或 8 位数字"
                      inputMode="numeric"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">当前密码</label>
                    <input
                      value={verifyPassword}
                      onChange={(e) => {
                        setVerifyPassword(e.target.value);
                        if (totpErrorMsg) setTotpErrorMsg(null);
                      }}
                      type="password"
                      autoComplete="current-password"
                      className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                      placeholder="用于启用前校验"
                    />
                  </div>
                  <button
                    type="button"
                    disabled={totpVerifySaving}
                    onClick={async () => {
                      try {
                        if (!verifyPassword.trim()) {
                          setTotpErrorMsg('请输入密码');
                          toast.error('请输入密码');
                          return;
                        }
                        setTotpVerifySaving(true);
                        setTotpErrorMsg(null);
                        const next = await verifyTotp(verifyCode, verifyPassword, totpEmailCode.trim() ? totpEmailCode.trim() : undefined);
                        setTotpStatus(next);
                        setEnrollResult(null);
                        setVerifyCode('');
                        setVerifyPassword('');
                        setTotpEmailCode('');
                        toast.success('TOTP 已启用');
                      } catch (e) {
                        const msg = e instanceof Error ? e.message : '启用失败';
                        setTotpErrorMsg(msg);
                        toast.error(msg);
                      } finally {
                        setTotpVerifySaving(false);
                      }
                    }}
                    className="px-4 py-2 rounded-md bg-green-600 text-white hover:bg-green-700 disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    {totpVerifySaving ? '启用中...' : '启用'}
                  </button>
                </div>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="space-y-2">
            <div className="text-sm text-gray-700">
              配置：{totpStatus?.algorithm ?? '-'} / {String(totpStatus?.digits ?? '-')} / {String(totpStatus?.periodSeconds ?? '-')} / skew={String(totpStatus?.skew ?? '-')}
            </div>
            {!showDisableInput ? (
              <div>
                <button
                  type="button"
                  onClick={() => setShowDisableInput(true)}
                  className="px-4 py-2 rounded-md bg-red-600 text-white hover:bg-red-700"
                >
                  停用
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto_auto] md:items-end">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">输入验证码以停用</label>
                  <input
                    value={disableCode}
                    onChange={(e) => {
                      setDisableCode(e.target.value);
                      if (totpErrorMsg) setTotpErrorMsg(null);
                    }}
                    className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                    placeholder="当前动态验证码"
                    inputMode="numeric"
                  />
                </div>
                <button
                  type="button"
                  disabled={totpDisableSaving}
                  onClick={async () => {
                    try {
                      setTotpDisableSaving(true);
                      setTotpErrorMsg(null);
                      const next = await disableTotp(disableCode);
                      setTotpStatus(next);
                      setDisableCode('');
                      setShowDisableInput(false);
                      toast.success('TOTP 已停用');
                    } catch (e) {
                      const msg = e instanceof Error ? e.message : '停用失败';
                      setTotpErrorMsg(msg);
                      toast.error(msg);
                    } finally {
                      setTotpDisableSaving(false);
                    }
                  }}
                  className="px-4 py-2 rounded-md bg-red-600 text-white hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {totpDisableSaving ? '停用中...' : '确认停用'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowDisableInput(false);
                    setDisableCode('');
                    setTotpErrorMsg(null);
                  }}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
                >
                  取消
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {!showPasswordForm ? (
        <div>
          <button
            type="button"
            onClick={() => setShowPasswordForm(true)}
            className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
          >
            修改密码
          </button>
        </div>
      ) : (
        <form className="space-y-3" onSubmit={handleSubmit}>
          {pwdStep === 0 && (
            <>
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
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleNext}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
                >
                  下一步
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowPasswordForm(false);
                    setOldPwd('');
                    setNewPwd('');
                    setConfirmNewPwd('');
                  }}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
                >
                  取消
                </button>
              </div>
            </>
          )}

          {pwdStep === 1 && (
            <>
              {enabled ? (
                <div className="space-y-3">
                  <div className="flex gap-6 mb-2">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="verifyMethod"
                        checked={verifyMethod === 'totp'}
                        onChange={() => setVerifyMethod('totp')}
                        className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">动态验证码</span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="verifyMethod"
                        checked={verifyMethod === 'email'}
                        onChange={() => setVerifyMethod('email')}
                        className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">邮箱验证码</span>
                    </label>
                  </div>

                  {verifyMethod === 'totp' && (
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                      <input
                        type="text"
                        value={changeTotpCode}
                        onChange={(e) => setChangeTotpCode(e.target.value)}
                        inputMode="numeric"
                        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="请输入 6 或 8 位数字"
                      />
                    </div>
                  )}

                  {verifyMethod === 'email' && (
                    <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                        <input
                          type="text"
                          value={changeEmailCode}
                          onChange={(e) => setChangeEmailCode(e.target.value)}
                          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                          placeholder="请输入验证码"
                        />
                      </div>
                      <button
                        type="button"
                        disabled={sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
                        onClick={async () => {
                          try {
                            setSendingChangeEmailCode(true);
                            await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                            setPwdEmailCountdown(180);
                            toast.success('验证码已发送');
                          } catch (e) {
                            const msg = e instanceof Error ? e.message : '发送失败';
                            setErrorMsg(msg);
                            toast.error(msg);
                          } finally {
                            setSendingChangeEmailCode(false);
                          }
                        }}
                        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                      >
                        {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送验证码'}
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                    <input
                      type="text"
                      value={changeEmailCode}
                      onChange={(e) => setChangeEmailCode(e.target.value)}
                      className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="开启邮箱验证时必填"
                    />
                  </div>
                  <button
                    type="button"
                    disabled={sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
                    onClick={async () => {
                      try {
                        setSendingChangeEmailCode(true);
                        await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                        setPwdEmailCountdown(180);
                        toast.success('验证码已发送');
                      } catch (e) {
                        const msg = e instanceof Error ? e.message : '发送失败';
                        setErrorMsg(msg);
                        toast.error(msg);
                      } finally {
                        setSendingChangeEmailCode(false);
                      }
                    }}
                    className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                  >
                    {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送验证码'}
                  </button>
                </div>
              )}

              <div className="flex gap-2 pt-2">
                <button
                  type="submit"
                  disabled={saving}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {saving ? '更新中...' : '更新密码'}
                </button>
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => setPwdStep(0)}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
                >
                  返回
                </button>
              </div>
            </>
          )}
        </form>
      )}
    </div>
  );
}
