import {useEffect, useMemo, useState} from "react";
import { toast } from 'react-hot-toast';
import { QRCodeSVG } from 'qrcode.react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../../../contexts/AuthContext';
import OtpCodeInput from '../../../../components/common/OtpCodeInput';
import { logout } from '../../../../services/authService';
import { changePassword } from '../../../../services/accountService';
import { sendAccountEmailVerificationCode } from '../../../../services/emailVerificationService';
import { validateChangePasswordForm } from './accountSecurity.validation';
import {
  getMySecurity2faPolicy,
  type Security2faPolicyStatusDTO,
  updateMyLogin2faPreference,
} from '../../../../services/security2faPolicyAccountService';
import {
  disableTotp,
  enrollTotp,
  getTotpPolicy,
  getTotpStatus,
  type TotpEnrollResponse,
  type TotpStatusResponse,
  verifyTotp,
  verifyTotpPassword,
} from '../../../../services/totpAccountService';
import type { TotpAdminSettingsDTO } from '../../../../services/totpAdminService';
import { ChangeEmailSection } from './AccountConnectionsPage';


export default function AccountSecurityPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { setCurrentUser, setIsAuthenticated, refreshSecurityGate } = useAuth();

  const forceTotp = useMemo(() => new URLSearchParams(location.search).get('force') === 'totp', [location.search]);

  useEffect(() => {
    if (location.hash !== '#email') return;
    const el = document.getElementById('email');
    el?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }, [location.hash]);

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
  const [securityPolicy, setSecurityPolicy] = useState<Security2faPolicyStatusDTO | null>(null);
  const [login2faPrefSaving, setLogin2faPrefSaving] = useState(false);
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
  const [enablePassword, setEnablePassword] = useState('');
  const [enablePasswordVerified, setEnablePasswordVerified] = useState(false);
  const [enablePasswordVerifying, setEnablePasswordVerifying] = useState(false);
  const [totpEmailCode, setTotpEmailCode] = useState('');
  const [sendingTotpEmailCode, setSendingTotpEmailCode] = useState(false);
  const [disableCode, setDisableCode] = useState('');
  const [disableEmailCode, setDisableEmailCode] = useState('');
  const [sendingDisableEmailCode, setSendingDisableEmailCode] = useState(false);
  const [disableMethod, setDisableMethod] = useState<'totp' | 'email'>('totp');
  const [disablePassword, setDisablePassword] = useState('');
  const [disablePasswordVerified, setDisablePasswordVerified] = useState(false);
  const [disablePasswordVerifying, setDisablePasswordVerifying] = useState(false);

  const [showEnableInput, setShowEnableInput] = useState(false);
  const [showDisableInput, setShowDisableInput] = useState(false);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [pwdStep, setPwdStep] = useState(0);
  const [verifyMethod, setVerifyMethod] = useState<'totp' | 'email'>('totp');

  // Countdown state for email verification codes
  const [totpEmailCountdown, setTotpEmailCountdown] = useState(0);
  const [disableEmailCountdown, setDisableEmailCountdown] = useState(0);
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
    let timer: number | undefined;
    if (disableEmailCountdown > 0) {
      timer = window.setInterval(() => {
        setDisableEmailCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [disableEmailCountdown]);

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
  const policyLoaded = securityPolicy != null;
  const totpAllowedByPolicy = securityPolicy?.totpAllowed ?? true;
  const totpRequiredByPolicy = securityPolicy?.totpRequired ?? false;
  const totpCanDisableByPolicy = securityPolicy?.totpCanDisable ?? true;
  const emailOtpAllowedByPolicy = securityPolicy?.emailOtpAllowed ?? true;
  const emailOtpRequiredByPolicy = securityPolicy?.emailOtpRequired ?? false;
  const canUseTotpForPwd = enabled && totpAllowedByPolicy;
  const canUseEmailForPwd = emailOtpAllowedByPolicy;
  const login2faCanEnableByPolicy = securityPolicy?.login2faCanEnable ?? false;
  const login2faEnabledByPolicy = securityPolicy?.login2faEnabled ?? false;

  const maxSkew = useMemo(() => {
    const v = typeof totpPolicy?.maxSkew === 'number' ? totpPolicy.maxSkew : 2;
    if (!Number.isFinite(v)) return 2;
    return Math.max(0, Math.min(10, Math.trunc(v)));
  }, [totpPolicy?.maxSkew]);

  useEffect(() => {
    void (async () => {
      setTotpLoading(true);
      try {
        const [policy, status, secPolicy] = await Promise.all([
          getTotpPolicy(),
          getTotpStatus(),
          getMySecurity2faPolicy().catch(() => null),
        ]);
        setTotpPolicy(policy);
        setTotpStatus(status);
        setSecurityPolicy(secPolicy);
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

    const totpCodeTrimmed = changeTotpCode.trim();
    const emailCodeTrimmed = changeEmailCode.trim();
    const canUseTotpForPwd = enabled && totpAllowedByPolicy;
    const canUseEmailForPwd = emailOtpAllowedByPolicy;

    if (policyLoaded) {
      if (totpRequiredByPolicy && !enabled) {
        setErrorMsg('管理员已强制启用 TOTP，请先启用后再修改密码');
        toast.error('管理员已强制启用 TOTP，请先启用后再修改密码');
        return;
      }
      if (totpRequiredByPolicy && !totpCodeTrimmed) {
        setErrorMsg('请输入动态验证码');
        toast.error('请输入动态验证码');
        return;
      }
      if (emailOtpRequiredByPolicy && !emailCodeTrimmed) {
        setErrorMsg('请输入邮箱验证码');
        toast.error('请输入邮箱验证码');
        return;
      }

      if (!totpRequiredByPolicy && !emailOtpRequiredByPolicy) {
        if (canUseTotpForPwd && canUseEmailForPwd) {
          if (verifyMethod === 'totp') {
            if (!totpCodeTrimmed) {
              setErrorMsg('请输入动态验证码');
              toast.error('请输入动态验证码');
              return;
            }
          } else {
            if (!emailCodeTrimmed) {
              setErrorMsg('请输入邮箱验证码');
              toast.error('请输入邮箱验证码');
              return;
            }
          }
        } else if (canUseTotpForPwd) {
          if (!totpCodeTrimmed) {
            setErrorMsg('请输入动态验证码');
            toast.error('请输入动态验证码');
            return;
          }
        } else if (canUseEmailForPwd) {
          if (!emailCodeTrimmed) {
            setErrorMsg('请输入邮箱验证码');
            toast.error('请输入邮箱验证码');
            return;
          }
        }
      }
    } else {
      if (enabled) {
        if (verifyMethod === 'totp') {
          if (!totpCodeTrimmed) {
            setErrorMsg('请输入动态验证码');
            toast.error('请输入动态验证码');
            return;
          }
        } else {
          if (!emailCodeTrimmed) {
            setErrorMsg('请输入邮箱验证码');
            toast.error('请输入邮箱验证码');
            return;
          }
        }
      } else {
        if (!emailCodeTrimmed) {
          setErrorMsg('请输入邮箱验证码');
          toast.error('请输入邮箱验证码');
          return;
        }
      }
    }

    try {
      setErrorMsg(null);
      setSaving(true);
      await changePassword({
        currentPassword: oldPwd,
        newPassword: newPwd,
        totpCode: policyLoaded
          ? (totpRequiredByPolicy ? totpCodeTrimmed : (enabled && totpAllowedByPolicy && (!emailOtpAllowedByPolicy || verifyMethod === 'totp') ? totpCodeTrimmed : undefined))
          : ((enabled && verifyMethod === 'totp') ? totpCodeTrimmed : undefined),
        emailCode: policyLoaded
          ? (emailOtpRequiredByPolicy ? emailCodeTrimmed : (emailOtpAllowedByPolicy && (!enabled || !totpAllowedByPolicy || verifyMethod === 'email') ? emailCodeTrimmed : undefined))
          : ((!enabled || verifyMethod === 'email') ? emailCodeTrimmed : undefined),
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

  const activeTotpDigits = totpStatus?.digits === 8 ? 8 : 6;
  const enrollVerifyDigits = enrollResult?.digits === 8 ? 8 : (enrollDigits === 8 ? 8 : 6);

  const handleEnableTotp = async (codeOverride?: string) => {
    if (totpVerifySaving) return;
    try {
      const codeTrimmed = (codeOverride ?? verifyCode).trim();
      if (!codeTrimmed) {
        setTotpErrorMsg('请输入验证码');
        toast.error('请输入验证码');
        return;
      }
      setTotpVerifySaving(true);
      setTotpErrorMsg(null);
      const next = await verifyTotp({ code: codeTrimmed });
      setTotpStatus(next);
      setEnrollResult(null);
      setVerifyCode('');
      setEnablePasswordVerified(false);
      toast.success('TOTP 已启用');
      await refreshSecurityGate();
      if (forceTotp) {
        const from = (location.state as { from?: { pathname?: string; search?: string } } | null)?.from;
        const target = from?.pathname ? `${from.pathname}${from.search ?? ''}` : '/portal/discover/home';
        navigate(target, { replace: true });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : '启用失败';
      setTotpErrorMsg(msg);
      toast.error(msg);
    } finally {
      setTotpVerifySaving(false);
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

      {forceTotp && !enabled ? (
        <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          管理员要求你启用 TOTP 二次验证。完成启用前，系统将限制访问其他功能。
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="space-y-4">
          {policyLoaded && login2faCanEnableByPolicy ? (
            <div className="rounded-lg border bg-white p-4 space-y-2">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-lg font-semibold">登录二次验证</div>
                  <div className="text-sm text-gray-600">启用后，登录时需要完成二次验证（TOTP / 邮箱验证码二选一）。</div>
                </div>
                <button
                  type="button"
                  onClick={async () => {
                    if (login2faPrefSaving) return;
                    try {
                      setLogin2faPrefSaving(true);
                      const nextEnabled = !login2faEnabledByPolicy;
                      const updated = await updateMyLogin2faPreference(nextEnabled);
                      setSecurityPolicy(updated);
                      toast.success(nextEnabled ? '已开启登录二次验证' : '已关闭登录二次验证');
                    } catch (e) {
                      const msg = e instanceof Error ? e.message : '保存失败';
                      toast.error(msg);
                    } finally {
                      setLogin2faPrefSaving(false);
                    }
                  }}
                  className="px-3 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
                  disabled={login2faPrefSaving || totpLoading}
                >
                  {login2faPrefSaving ? '保存中...' : (login2faEnabledByPolicy ? '关闭' : '开启')}
                </button>
              </div>

              <div className="text-sm">当前状态：{login2faEnabledByPolicy ? '已开启' : '未开启'}</div>
            </div>
          ) : null}

          <div className="rounded-lg border bg-white p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-lg font-semibold">二次验证（TOTP）</div>
            <div className="text-sm text-gray-600">
              使用认证器应用（Google Authenticator / Microsoft Authenticator 等）生成动态验证码
            </div>
          </div>
        </div>

        <div className="text-sm">
          当前状态：{enabled ? '已启用' : '未启用'}
          {enabled && totpStatus?.verifiedAt ? <span className="text-gray-500">（verifiedAt={totpStatus.verifiedAt}）</span> : null}
        </div>
        {policyLoaded && enabled && !totpCanDisableByPolicy ? (
          <div className="text-xs text-amber-700">管理员已强制启用 TOTP，无法自行停用。</div>
        ) : null}

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
            {policyLoaded && totpRequiredByPolicy ? (
              <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                管理员已强制启用 TOTP：请完成绑定与验证后再进行敏感操作。
              </div>
            ) : null}
            {policyLoaded && !totpAllowedByPolicy ? (
              <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                管理员已禁止启用 TOTP。
              </div>
            ) : null}
            {(String(enrollAlg).toUpperCase() !== 'SHA1' || Number(enrollDigits) !== 6 || Number(enrollPeriod) !== 30) ? (
              <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                提示：当使用 SHA256/SHA512/8 位时，请优先用“扫码”绑定。注：Microsoft Authenticator只支持SHA1；如需60秒步长请使用支持自定义算法的认证器（例如Aegis / 2FAS）。
              </div>
            ) : null}

            {!showEnableInput ? (
              <div>
                <button
                  type="button"
                  disabled={totpLoading || totpEnrollSaving || !masterKeyConfigured || (policyLoaded && (!totpAllowedByPolicy || !emailOtpAllowedByPolicy))}
                  onClick={() => {
                    setShowEnableInput(true);
                    setEnablePassword('');
                    setEnablePasswordVerified(false);
                    setEnablePasswordVerifying(false);
                    setTotpEmailCode('');
                    setTotpEmailCountdown(0);
                    setSendingTotpEmailCode(false);
                    setEnrollResult(null);
                    setVerifyCode('');
                    setTotpErrorMsg(null);
                  }}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  开始启用
                </button>
              </div>
            ) : (
              <>
                <div className="flex justify-end">
                  <button
                    type="button"
                    disabled={totpEnrollSaving || totpVerifySaving || totpLoading}
                    onClick={() => {
                      setShowEnableInput(false);
                      setEnablePassword('');
                      setEnablePasswordVerified(false);
                      setEnablePasswordVerifying(false);
                      setTotpEmailCode('');
                      setTotpEmailCountdown(0);
                      setSendingTotpEmailCode(false);
                      setEnrollResult(null);
                      setVerifyCode('');
                      setTotpErrorMsg(null);
                    }}
                    className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    取消
                  </button>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">1</div>
                    <div className="text-sm font-medium text-gray-900">验证密码</div>
                  </div>
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">当前密码</label>
                      <input
                        value={enablePassword}
                        onChange={(e) => {
                          setEnablePassword(e.target.value);
                          if (totpErrorMsg) setTotpErrorMsg(null);
                        }}
                        disabled={enablePasswordVerified || enablePasswordVerifying || totpEnrollSaving || totpLoading}
                        type="password"
                        autoComplete="current-password"
                        className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white disabled:opacity-60 disabled:cursor-not-allowed"
                        placeholder={enablePasswordVerified ? '已验证' : '先验证密码才能继续'}
                      />
                    </div>
                    <button
                      type="button"
                      disabled={enablePasswordVerified || enablePasswordVerifying || totpEnrollSaving || totpLoading || (policyLoaded && !totpAllowedByPolicy)}
                      onClick={async () => {
                        if (enablePasswordVerified) return;
                        try {
                          const pwd = enablePassword.trim();
                          if (!pwd) {
                            setTotpErrorMsg('请输入密码');
                            toast.error('请输入密码');
                            return;
                          }
                          setEnablePasswordVerifying(true);
                          setTotpErrorMsg(null);
                          await verifyTotpPassword('ENABLE', pwd);
                          setEnablePasswordVerified(true);
                          setEnablePassword('');
                          toast.success('密码验证通过');
                        } catch (e) {
                          const msg = e instanceof Error ? e.message : '密码验证失败';
                          setTotpErrorMsg(msg);
                          toast.error(msg);
                        } finally {
                          setEnablePasswordVerifying(false);
                        }
                      }}
                      className="px-4 py-2 rounded-md bg-gray-900 text-white hover:bg-black disabled:opacity-60 disabled:cursor-not-allowed w-28"
                    >
                      {enablePasswordVerifying ? '验证中...' : enablePasswordVerified ? '已验证' : '验证密码'}
                    </button>
                  </div>
                </div>

                {enablePasswordVerified ? (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">2</div>
                      <div className="text-sm font-medium text-gray-900">验证邮箱</div>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-4 md:items-end">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">算法</label>
                        <select
                          className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                          value={enrollAlg}
                          onChange={(e) => setEnrollAlg(e.target.value)}
                          disabled={totpEnrollSaving || totpLoading || Boolean(enrollResult)}
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
                          disabled={totpEnrollSaving || totpLoading || Boolean(enrollResult)}
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
                          disabled={totpEnrollSaving || totpLoading || Boolean(enrollResult)}
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
                          disabled={totpEnrollSaving || totpLoading || Boolean(enrollResult)}
                        >
                          {Array.from({ length: maxSkew + 1 }).map((_, i) => (
                            <option key={i} value={String(i)}>
                              {i}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    {policyLoaded && !emailOtpAllowedByPolicy ? (
                      <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                        管理员已禁止使用邮箱验证码，无法启用 TOTP。
                      </div>
                    ) : null}

                    <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                        <input
                          value={totpEmailCode}
                          onChange={(e) => {
                            setTotpEmailCode(e.target.value);
                            if (totpErrorMsg) setTotpErrorMsg(null);
                          }}
                          disabled={totpEnrollSaving || totpLoading || Boolean(enrollResult)}
                          className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                          placeholder="启用前必填"
                        />
                      </div>
                      <button
                        type="button"
                        disabled={!emailOtpAllowedByPolicy || sendingTotpEmailCode || totpEnrollSaving || totpLoading || totpEmailCountdown > 0 || Boolean(enrollResult)}
                        onClick={async () => {
                          try {
                            setSendingTotpEmailCode(true);
                            const resp = await sendAccountEmailVerificationCode('TOTP_ENABLE');
                            const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                            setTotpEmailCountdown(wait);
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
                        {sendingTotpEmailCode ? '发送中...' : totpEmailCountdown > 0 ? `${totpEmailCountdown}s` : '发送'}
                      </button>
                    </div>

                    <button
                      type="button"
                      disabled={totpEnrollSaving || totpLoading || !masterKeyConfigured || (policyLoaded && (!totpAllowedByPolicy || !emailOtpAllowedByPolicy)) || Boolean(enrollResult)}
                      onClick={async () => {
                        try {
                          const emailCodeTrimmed = totpEmailCode.trim();
                          if (!emailCodeTrimmed) {
                            setTotpErrorMsg('请输入邮箱验证码');
                            toast.error('请输入邮箱验证码');
                            return;
                          }
                          setTotpEnrollSaving(true);
                          setTotpErrorMsg(null);
                          setEnrollResult(null);
                          const res = await enrollTotp({
                            emailCode: emailCodeTrimmed,
                            algorithm: enrollAlg,
                            digits: enrollDigits,
                            periodSeconds: enrollPeriod,
                            skew: enrollSkew,
                          });
                          setEnrollResult(res);
                          setTotpEmailCode('');
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
                      {enrollResult ? '已验证邮箱' : (totpEnrollSaving ? '验证中...' : '验证验证码')}
                    </button>
                  </div>
                ) : null}

                {enrollResult ? (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">3</div>
                      <div className="text-sm font-medium text-gray-900">输入并验证动态验证码</div>
                    </div>
                    <div className="rounded-md border bg-gray-50 p-3 space-y-2">
                      <div className="text-sm font-medium">绑定信息</div>
                      <div className="grid grid-cols-1 gap-3 md:grid-cols-[220px_1fr] md:items-start">
                        <div className="rounded-md border bg-white p-3">
                          <div className="text-xs text-gray-500 mb-2">扫码绑定</div>
                          <div data-testid="totp-qr" className="flex justify-center">
                            <QRCodeSVG value={enrollResult.otpauthUri} size={180} />
                          </div>
                          <div className="text-xs text-gray-500 mt-2">在认证器应用中选择“扫码添加”（推荐使用Google Authenticator）。</div>
                        </div>
                        <div className="space-y-3">
                          <div className="space-y-1">
                            <div className="flex items-center justify-between gap-2">
                              <div className="text-xs text-gray-500">Secret (Base32)</div>
                              <button
                                type="button"
                                className="px-2 py-1 rounded-md border bg-white hover:bg-gray-50 text-xs whitespace-nowrap shrink-0"
                                onClick={() => void copyText(enrollResult.secretBase32)}
                              >
                                复制
                              </button>
                            </div>
                            <input
                              value={enrollResult.secretBase32}
                              readOnly
                              className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm font-mono"
                            />
                          </div>
                          <div className="space-y-1">
                            <div className="flex items-center justify-between gap-2">
                              <div className="text-xs text-gray-500">otpauth URI</div>
                              <button
                                type="button"
                                className="px-2 py-1 rounded-md border bg-white hover:bg-gray-50 text-xs whitespace-nowrap shrink-0"
                                onClick={() => void copyText(enrollResult.otpauthUri)}
                              >
                                复制
                              </button>
                            </div>
                            <input
                              value={enrollResult.otpauthUri}
                              readOnly
                              className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-xs font-mono"
                            />
                          </div>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                        <div data-testid="totp-enable-code">
                          <label className="block text-sm font-medium text-gray-700 mb-1">输入验证码以启用</label>
                          <OtpCodeInput
                            digits={enrollVerifyDigits}
                            value={verifyCode}
                            onChange={(v) => {
                              setVerifyCode(v);
                              if (totpErrorMsg) setTotpErrorMsg(null);
                            }}
                            disabled={totpVerifySaving}
                            autoFocus
                          />
                        </div>
                        <button
                          type="button"
                          disabled={totpVerifySaving}
                          onClick={() => void handleEnableTotp()}
                          className="px-4 py-2 rounded-md bg-green-600 text-white hover:bg-green-700 disabled:opacity-60 disabled:cursor-not-allowed"
                        >
                          {totpVerifySaving ? '启用中...' : '启用'}
                        </button>
                      </div>
                    </div>
                  </div>
                ) : null}
              </>
            )}
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
                  disabled={!totpCanDisableByPolicy}
                  onClick={() => {
                    if (!totpCanDisableByPolicy) return;
                    setDisableMethod('totp');
                    setDisableCode('');
                    setDisableEmailCode('');
                    setDisablePassword('');
                    setDisablePasswordVerified(false);
                    setTotpErrorMsg(null);
                    setShowDisableInput(true);
                  }}
                  className="px-4 py-2 rounded-md bg-red-600 text-white hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  停用
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                {!disablePasswordVerified ? (
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto_auto] md:items-end">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">当前密码</label>
                      <input
                        value={disablePassword}
                        onChange={(e) => {
                          setDisablePassword(e.target.value);
                          if (totpErrorMsg) setTotpErrorMsg(null);
                        }}
                        type="password"
                        autoComplete="current-password"
                        className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                        placeholder="先验证密码才能继续"
                      />
                    </div>
                    <button
                      type="button"
                      disabled={disablePasswordVerifying || totpDisableSaving}
                      onClick={async () => {
                        try {
                          const pwd = disablePassword.trim();
                          if (!pwd) {
                            setTotpErrorMsg('请输入密码');
                            toast.error('请输入密码');
                            return;
                          }
                          setDisablePasswordVerifying(true);
                          setTotpErrorMsg(null);
                          await verifyTotpPassword('DISABLE', pwd);
                          setDisablePasswordVerified(true);
                          setDisablePassword('');
                          toast.success('密码验证通过');
                        } catch (e) {
                          const msg = e instanceof Error ? e.message : '密码验证失败';
                          setTotpErrorMsg(msg);
                          toast.error(msg);
                        } finally {
                          setDisablePasswordVerifying(false);
                        }
                      }}
                      className="px-4 py-2 rounded-md bg-gray-900 text-white hover:bg-black disabled:opacity-60 disabled:cursor-not-allowed w-28"
                    >
                      {disablePasswordVerifying ? '验证中...' : '验证密码'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setShowDisableInput(false);
                        setDisableCode('');
                        setDisableEmailCode('');
                        setDisablePassword('');
                        setDisablePasswordVerified(false);
                        setTotpErrorMsg(null);
                      }}
                      className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
                    >
                      取消
                    </button>
                  </div>
                ) : (
                  <>
                    {emailOtpAllowedByPolicy ? (
                      <div className="flex gap-6 mb-1">
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="totpDisableMethod"
                            checked={disableMethod === 'totp'}
                            onChange={() => setDisableMethod('totp')}
                            className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                          />
                          <span className="text-sm font-medium text-gray-700">动态验证码</span>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="totpDisableMethod"
                            checked={disableMethod === 'email'}
                            onChange={() => setDisableMethod('email')}
                            className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                          />
                          <span className="text-sm font-medium text-gray-700">邮箱验证码</span>
                        </label>
                      </div>
                    ) : null}

                {disableMethod === 'totp' || !emailOtpAllowedByPolicy ? (
                  <div className="space-y-2">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">输入验证码以停用</label>
                      <OtpCodeInput
                        digits={activeTotpDigits}
                        value={disableCode}
                        onChange={(v) => {
                          setDisableCode(v);
                          if (totpErrorMsg) setTotpErrorMsg(null);
                        }}
                        disabled={!totpCanDisableByPolicy || totpDisableSaving}
                      />
                    </div>
                    <div className="flex gap-2 pt-1">
                      <button
                        type="button"
                        disabled={!totpCanDisableByPolicy || totpDisableSaving}
                        onClick={async () => {
                          try {
                            const codeTrimmed = disableCode.trim();
                            if (!codeTrimmed) {
                              setTotpErrorMsg('请输入动态验证码');
                              toast.error('请输入动态验证码');
                              return;
                            }
                            setTotpDisableSaving(true);
                            setTotpErrorMsg(null);
                            const next = await disableTotp({ method: 'totp', code: codeTrimmed });
                            setTotpStatus(next);
                            setDisableCode('');
                            setShowDisableInput(false);
                          setShowEnableInput(false);
                            setDisablePasswordVerified(false);
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
                          setDisableEmailCode('');
                          setDisablePasswordVerified(false);
                          setTotpErrorMsg(null);
                        }}
                        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                        <input
                          value={disableEmailCode}
                          onChange={(e) => {
                            setDisableEmailCode(e.target.value);
                            if (totpErrorMsg) setTotpErrorMsg(null);
                          }}
                          className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white"
                          placeholder="请输入验证码"
                        />
                      </div>
                      <button
                        type="button"
                        disabled={!emailOtpAllowedByPolicy || sendingDisableEmailCode || totpDisableSaving || disableEmailCountdown > 0}
                        onClick={async () => {
                          try {
                            setSendingDisableEmailCode(true);
                            const resp = await sendAccountEmailVerificationCode('TOTP_DISABLE');
                            const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                            setDisableEmailCountdown(wait);
                            toast.success('验证码已发送');
                          } catch (e) {
                            const msg = e instanceof Error ? e.message : '发送失败';
                            setTotpErrorMsg(msg);
                            toast.error(msg);
                          } finally {
                            setSendingDisableEmailCode(false);
                          }
                        }}
                        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28 whitespace-nowrap"
                      >
                        {sendingDisableEmailCode ? '发送中...' : disableEmailCountdown > 0 ? `${disableEmailCountdown}s` : '发送'}
                      </button>
                    </div>

                    <div className="flex gap-2 pt-1">
                      <button
                        type="button"
                        disabled={!totpCanDisableByPolicy || totpDisableSaving}
                        onClick={async () => {
                          try {
                            const emailCodeTrimmed = disableEmailCode.trim();
                            if (!emailCodeTrimmed) {
                              setTotpErrorMsg('请输入邮箱验证码');
                              toast.error('请输入邮箱验证码');
                              return;
                            }
                            setTotpDisableSaving(true);
                            setTotpErrorMsg(null);
                            const next = await disableTotp({ method: 'email', emailCode: emailCodeTrimmed });
                            setTotpStatus(next);
                            setDisableEmailCode('');
                            setShowDisableInput(false);
                            setShowEnableInput(false);
                            setDisablePasswordVerified(false);
                            toast.success('TOTP 已停用');
                          } catch (e) {
                            const msg = e instanceof Error ? e.message : '停用失败';
                            setTotpErrorMsg(msg);
                            toast.error(msg);
                          } finally {
                            setTotpDisableSaving(false);
                          }
                        }}
                        className="px-4 py-2 rounded-md bg-red-600 text-white hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed w-28 whitespace-nowrap"
                      >
                        {totpDisableSaving ? '停用中...' : '确认停用'}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setShowDisableInput(false);
                          setDisableCode('');
                          setDisableEmailCode('');
                          setDisablePasswordVerified(false);
                          setTotpErrorMsg(null);
                        }}
                        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 w-28"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                )}
                  </>
                )}
              </div>
            )}
          </div>
        )}
      </div>

          <div className="rounded-lg border border-gray-200 bg-white p-4 space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-lg font-semibold">修改密码</div>
                <div className="text-sm text-gray-600">修改后会退出当前登录，需要重新登录</div>
              </div>
              {!showPasswordForm ? (
                <button
                  type="button"
                  onClick={() => setShowPasswordForm(true)}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
                >
                  修改密码
                </button>
              ) : (
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => {
                    setShowPasswordForm(false);
                    setOldPwd('');
                    setNewPwd('');
                    setConfirmNewPwd('');
                    setChangeTotpCode('');
                    setChangeEmailCode('');
                    setPwdStep(0);
                    setErrorMsg(null);
                  }}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  收起
                </button>
              )}
            </div>

            {!showPasswordForm ? (
              <div className="text-sm text-gray-500">建议使用不少于 12 位的高强度密码，并避免与其他站点重复。</div>
            ) : (
              <form className="space-y-3" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">1</div>
                    <div className="text-sm font-medium text-gray-900">设置新密码</div>
                  </div>
                  <div className="space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">旧密码</label>
                      <input
                        type="password"
                        value={oldPwd}
                        onChange={(e) => setOldPwd(e.target.value)}
                        autoComplete="current-password"
                        disabled={pwdStep !== 0 || saving}
                        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">新密码</label>
                      <input
                        type="password"
                        value={newPwd}
                        onChange={(e) => setNewPwd(e.target.value)}
                        autoComplete="new-password"
                        disabled={pwdStep !== 0 || saving}
                        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">确认新密码</label>
                      <input
                        type="password"
                        value={confirmNewPwd}
                        onChange={(e) => setConfirmNewPwd(e.target.value)}
                        autoComplete="new-password"
                        disabled={pwdStep !== 0 || saving}
                        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                      />
                    </div>
                  </div>

                  {pwdStep === 0 ? (
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
                  ) : (
                    <div className="flex justify-end">
                      <button
                        type="button"
                        disabled
                        className="px-4 py-2 rounded-md border bg-white opacity-60 cursor-not-allowed w-28"
                      >
                        已填写
                      </button>
                    </div>
                  )}
                </div>

                {pwdStep === 1 && (
                  <>
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">2</div>
                        <div className="text-sm font-medium text-gray-900">二次验证</div>
                      </div>

                      {policyLoaded ? (
                        <div className="space-y-3">
                          {totpRequiredByPolicy && !enabled ? (
                            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                              管理员已强制启用 TOTP；当前账号尚未启用，请先完成 TOTP 绑定后再修改密码。
                            </div>
                          ) : null}

                          {totpRequiredByPolicy ? (
                            <div>
                              <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                              <OtpCodeInput
                                digits={activeTotpDigits}
                                value={changeTotpCode}
                                onChange={setChangeTotpCode}
                                disabled={saving}
                              />
                            </div>
                          ) : null}

                          {emailOtpRequiredByPolicy ? (
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
                                disabled={!canUseEmailForPwd || sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
                                onClick={async () => {
                                  try {
                                    setSendingChangeEmailCode(true);
                                    const resp = await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                                    const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                                    setPwdEmailCountdown(wait);
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
                                {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送'}
                              </button>
                            </div>
                          ) : null}

                          {!totpRequiredByPolicy && !emailOtpRequiredByPolicy ? (
                            canUseTotpForPwd && canUseEmailForPwd ? (
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

                                {verifyMethod === 'totp' ? (
                                  <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                                    <OtpCodeInput
                                      digits={activeTotpDigits}
                                      value={changeTotpCode}
                                      onChange={setChangeTotpCode}
                                      disabled={saving}
                                    />
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
                                        placeholder="请输入验证码"
                                      />
                                    </div>
                                    <button
                                      type="button"
                                      disabled={!canUseEmailForPwd || sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
                                      onClick={async () => {
                                        try {
                                          setSendingChangeEmailCode(true);
                                          const resp = await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                                          const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                                          setPwdEmailCountdown(wait);
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
                                      {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送'}
                                    </button>
                                  </div>
                                )}
                              </div>
                            ) : canUseTotpForPwd ? (
                              <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                                <OtpCodeInput
                                  digits={activeTotpDigits}
                                  value={changeTotpCode}
                                  onChange={setChangeTotpCode}
                                  disabled={saving}
                                />
                              </div>
                            ) : canUseEmailForPwd ? (
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
                                  disabled={!canUseEmailForPwd || sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
                                  onClick={async () => {
                                    try {
                                      setSendingChangeEmailCode(true);
                                      const resp = await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                                      const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                                      setPwdEmailCountdown(wait);
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
                                  {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送'}
                                </button>
                              </div>
                            ) : (
                              <div className="text-sm text-gray-500">当前无需验证码。</div>
                            )
                          ) : null}
                        </div>
                      ) : (
                        enabled ? (
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
                                <OtpCodeInput
                                  digits={activeTotpDigits}
                                  value={changeTotpCode}
                                  onChange={setChangeTotpCode}
                                  disabled={saving}
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
                                      const resp = await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                                      const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                                      setPwdEmailCountdown(wait);
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
                                  {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送'}
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
                                  const resp = await sendAccountEmailVerificationCode('CHANGE_PASSWORD');
                                  const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                                  setPwdEmailCountdown(wait);
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
                              {sendingChangeEmailCode ? '发送中...' : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送'}
                            </button>
                          </div>
                        )
                      )}
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <div className="h-6 w-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center">3</div>
                        <div className="text-sm font-medium text-gray-900">确认更新</div>
                      </div>
                      <div className="flex gap-2 pt-1">
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
                    </div>
                  </>
                )}
              </form>
            )}
          </div>
        </div>

        <div className="space-y-4">
          <div id="email" className="scroll-mt-24">
            <ChangeEmailSection mode="embedded" />
          </div>
        </div>
      </div>
    </div>
  );
}
