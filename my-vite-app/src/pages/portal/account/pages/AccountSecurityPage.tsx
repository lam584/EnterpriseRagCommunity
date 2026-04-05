import {useEffect, useMemo, useState} from "react";
import { toast } from 'react-hot-toast';
import { QRCodeSVG } from 'qrcode.react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../../../contexts/AuthContext';
import OtpCodeInput from '../../../../components/common/OtpCodeInput';
import { sendAccountEmailVerificationCode } from '../../../../services/emailVerificationService';
import {
  getMySecurity2faPolicy,
  type Security2faPolicyStatusDTO,
  updateMyLogin2faPreference,
  verifyMyLogin2faPreferencePassword,
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
import Modal from '../../../../components/common/Modal';
import ChangePasswordCard from './ChangePasswordCard';


export default function AccountSecurityPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { refreshSecurityGate } = useAuth();

  const forceTotp = useMemo(() => new URLSearchParams(location.search).get('force') === 'totp', [location.search]);

  useEffect(() => {
    if (location.hash !== '#email') return;
    const el = document.getElementById('email');
    el?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }, [location.hash]);

  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [totpPolicy, setTotpPolicy] = useState<TotpAdminSettingsDTO | null>(null);
  const [totpStatus, setTotpStatus] = useState<TotpStatusResponse | null>(null);
  const [securityPolicy, setSecurityPolicy] = useState<Security2faPolicyStatusDTO | null>(null);
  const [login2faPrefSaving, setLogin2faPrefSaving] = useState(false);
  const [login2faPrefDialogOpen, setLogin2faPrefDialogOpen] = useState(false);
  const [login2faPrefTargetEnabled, setLogin2faPrefTargetEnabled] = useState(false);
  const [login2faPrefStep, setLogin2faPrefStep] = useState<1 | 2>(1);
  const [login2faPrefPassword, setLogin2faPrefPassword] = useState('');
  const [login2faPrefPasswordVerifying, setLogin2faPrefPasswordVerifying] = useState(false);
  const [login2faPrefPasswordVerified, setLogin2faPrefPasswordVerified] = useState(false);
  const [login2faPrefMethod, setLogin2faPrefMethod] = useState<'totp' | 'email'>('totp');
  const [login2faPrefTotpCode, setLogin2faPrefTotpCode] = useState('');
  const [login2faPrefEmailCode, setLogin2faPrefEmailCode] = useState('');
  const [sendingLogin2faPrefEmailCode, setSendingLogin2faPrefEmailCode] = useState(false);
  const [login2faPrefEmailCountdown, setLogin2faPrefEmailCountdown] = useState(0);
  const [login2faPrefErrorMsg, setLogin2faPrefErrorMsg] = useState<string | null>(null);
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

  const resetTotpEnableForm = () => {
    setEnablePassword('');
    setEnablePasswordVerified(false);
    setEnablePasswordVerifying(false);
    setTotpEmailCode('');
    setTotpEmailCountdown(0);
    setSendingTotpEmailCode(false);
    setEnrollResult(null);
    setVerifyCode('');
    setTotpErrorMsg(null);
  };

  // Countdown state for email verification codes
  const [totpEmailCountdown, setTotpEmailCountdown] = useState(0);
  const [disableEmailCountdown, setDisableEmailCountdown] = useState(0);

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
    if (disableEmailCountdown > 0) {
      timer = window.setInterval(() => {
        setDisableEmailCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [disableEmailCountdown]);

  useEffect(() => {
    let timer: number | undefined;
    if (login2faPrefEmailCountdown > 0) {
      timer = window.setInterval(() => {
        setLogin2faPrefEmailCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [login2faPrefEmailCountdown]);

  const enabled = Boolean(totpStatus?.enabled);
  const masterKeyConfigured = totpStatus?.masterKeyConfigured !== false;
  const policyLoaded = securityPolicy != null;
  const totpAllowedByPolicy = securityPolicy?.totpAllowed ?? true;
  const totpRequiredByPolicy = securityPolicy?.totpRequired ?? false;
  const totpCanDisableByPolicy = securityPolicy?.totpCanDisable ?? true;
  const emailOtpAllowedByPolicy = securityPolicy?.emailOtpAllowed ?? true;
  const emailOtpRequiredByPolicy = securityPolicy?.emailOtpRequired ?? false;
  const login2faCanEnableByPolicy = securityPolicy?.login2faCanEnable ?? false;
  const login2faEnabledByPolicy = securityPolicy?.login2faEnabled ?? false;

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

  const closeLogin2faPrefDialog = () => {
    setLogin2faPrefDialogOpen(false);
    setLogin2faPrefStep(1);
    setLogin2faPrefPassword('');
    setLogin2faPrefPasswordVerified(false);
    setLogin2faPrefPasswordVerifying(false);
    setLogin2faPrefTotpCode('');
    setLogin2faPrefEmailCode('');
    setSendingLogin2faPrefEmailCode(false);
    setLogin2faPrefEmailCountdown(0);
    setLogin2faPrefErrorMsg(null);
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
                  onClick={() => {
                    if (login2faPrefSaving) return;
                    const nextEnabled = !login2faEnabledByPolicy;
                    const totpOk = enabled && totpAllowedByPolicy;
                    const emailOk = emailOtpAllowedByPolicy;
                    setLogin2faPrefTargetEnabled(nextEnabled);
                    setLogin2faPrefStep(1);
                    setLogin2faPrefPassword('');
                    setLogin2faPrefPasswordVerified(false);
                    setLogin2faPrefPasswordVerifying(false);
                    setLogin2faPrefMethod(totpOk ? 'totp' : (emailOk ? 'email' : 'totp'));
                    setLogin2faPrefTotpCode('');
                    setLogin2faPrefEmailCode('');
                    setSendingLogin2faPrefEmailCode(false);
                    setLogin2faPrefEmailCountdown(0);
                    setLogin2faPrefErrorMsg(null);
                    setLogin2faPrefDialogOpen(true);
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
            <div className="text-lg font-semibold">TOTP（基于时间的一次性口令）</div>
            <span className="sr-only">二次验证（TOTP）</span>
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
            {enrollResult && (String(enrollResult.algorithm).toUpperCase() !== 'SHA1' || Number(enrollResult.digits) !== 6 || Number(enrollResult.periodSeconds) !== 30) ? (
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
                    resetTotpEnableForm();
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
                      resetTotpEnableForm();
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
                    {totpPolicy ? (
                      <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                        <div className="rounded-md border bg-gray-50 px-3 py-2">
                          <div className="text-xs text-gray-500">算法</div>
                          <div className="text-sm font-medium text-gray-900">{enrollResult.algorithm}</div>
                        </div>
                        <div className="rounded-md border bg-gray-50 px-3 py-2">
                          <div className="text-xs text-gray-500">位数</div>
                          <div className="text-sm font-medium text-gray-900">{String(enrollResult.digits)}</div>
                        </div>
                        <div className="rounded-md border bg-gray-50 px-3 py-2">
                          <div className="text-xs text-gray-500">步长（秒）</div>
                          <div className="text-sm font-medium text-gray-900">{String(enrollResult.periodSeconds)}</div>
                        </div>
                        <div className="rounded-md border bg-gray-50 px-3 py-2">
                          <div className="text-xs text-gray-500">Skew</div>
                          <div className="text-sm font-medium text-gray-900">{String(enrollResult.skew)}</div>
                        </div>
                      </div>
                    ) : null}
                    <div className="rounded-md border bg-gray-50 p-3 space-y-2">
                      <div className="text-sm font-medium">绑定信息</div>
                      <div className="text-sm text-gray-700">
                        本次配置：{enrollResult.algorithm} / {String(enrollResult.digits)} / {String(enrollResult.periodSeconds)} / skew={String(enrollResult.skew)}
                      </div>
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

          <ChangePasswordCard
            enabled={enabled}
            activeTotpDigits={activeTotpDigits}
            policyLoaded={policyLoaded}
            totpAllowedByPolicy={totpAllowedByPolicy}
            totpRequiredByPolicy={totpRequiredByPolicy}
            emailOtpAllowedByPolicy={emailOtpAllowedByPolicy}
            emailOtpRequiredByPolicy={emailOtpRequiredByPolicy}
            errorMsg={errorMsg}
            setErrorMsg={setErrorMsg}
          />
        </div>

        <div className="space-y-4">
          <div id="email" className="scroll-mt-24">
            <ChangeEmailSection mode="embedded" />
          </div>
        </div>
      </div>

      <Modal
        isOpen={login2faPrefDialogOpen}
        onClose={closeLogin2faPrefDialog}
        title={login2faPrefTargetEnabled ? '开启登录二次验证' : '关闭登录二次验证'}
        showFooterClose={false}
      >
        <div className="space-y-3">
          {login2faPrefErrorMsg ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
              {login2faPrefErrorMsg}
            </div>
          ) : null}

          {login2faPrefStep === 1 ? (
            <div className="space-y-3">
              <div className="text-sm text-gray-700">为保护账号安全，请先验证当前密码。</div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">当前密码</label>
                <input
                  value={login2faPrefPassword}
                  onChange={(e) => {
                    setLogin2faPrefPassword(e.target.value);
                    if (login2faPrefErrorMsg) setLogin2faPrefErrorMsg(null);
                  }}
                  disabled={login2faPrefPasswordVerified || login2faPrefPasswordVerifying || login2faPrefSaving}
                  type="password"
                  autoComplete="current-password"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white disabled:opacity-60 disabled:cursor-not-allowed"
                  placeholder={login2faPrefPasswordVerified ? '已验证' : '请输入当前密码'}
                />
              </div>
              <div className="flex justify-end">
                <button
                  type="button"
                  disabled={login2faPrefPasswordVerified || login2faPrefPasswordVerifying || login2faPrefSaving}
                  onClick={async () => {
                    if (login2faPrefPasswordVerified || login2faPrefPasswordVerifying) return;
                    const pwd = login2faPrefPassword.trim();
                    if (!pwd) {
                      setLogin2faPrefErrorMsg('请输入当前密码');
                      toast.error('请输入当前密码');
                      return;
                    }
                    try {
                      setLogin2faPrefPasswordVerifying(true);
                      setLogin2faPrefErrorMsg(null);
                      await verifyMyLogin2faPreferencePassword(pwd);
                      setLogin2faPrefPasswordVerified(true);
                      setLogin2faPrefStep(2);
                      toast.success('密码验证通过');
                    } catch (e) {
                      const msg = e instanceof Error ? e.message : '验证失败';
                      setLogin2faPrefErrorMsg(msg);
                      toast.error(msg);
                    } finally {
                      setLogin2faPrefPasswordVerifying(false);
                    }
                  }}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {login2faPrefPasswordVerifying ? '验证中...' : '验证密码'}
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="text-sm text-gray-700">请选择一种方式完成二次验证。</div>

              {!(emailOtpAllowedByPolicy || (enabled && totpAllowedByPolicy)) ? (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  当前账号无法使用 TOTP 或邮箱验证码完成验证，请联系管理员。
                </div>
              ) : null}

              <div className="flex flex-wrap gap-4">
                <label className={`flex items-center gap-2 cursor-pointer ${enabled && totpAllowedByPolicy ? '' : 'opacity-60'}`}>
                  <input
                    type="radio"
                    name="login2faPrefMethod"
                    checked={login2faPrefMethod === 'totp'}
                    onChange={() => {
                      setLogin2faPrefMethod('totp');
                      setLogin2faPrefTotpCode('');
                      setLogin2faPrefEmailCode('');
                      setLogin2faPrefErrorMsg(null);
                    }}
                    disabled={!(enabled && totpAllowedByPolicy) || login2faPrefSaving}
                    className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                  />
                  <span className="text-sm font-medium text-gray-700">动态验证码（TOTP）</span>
                </label>
                <label className={`flex items-center gap-2 cursor-pointer ${emailOtpAllowedByPolicy ? '' : 'opacity-60'}`}>
                  <input
                    type="radio"
                    name="login2faPrefMethod"
                    checked={login2faPrefMethod === 'email'}
                    onChange={() => {
                      setLogin2faPrefMethod('email');
                      setLogin2faPrefTotpCode('');
                      setLogin2faPrefEmailCode('');
                      setLogin2faPrefErrorMsg(null);
                    }}
                    disabled={!emailOtpAllowedByPolicy || login2faPrefSaving}
                    className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                  />
                  <span className="text-sm font-medium text-gray-700">邮箱验证码</span>
                </label>
              </div>

              {login2faPrefMethod === 'totp' ? (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                  <OtpCodeInput
                    digits={activeTotpDigits}
                    value={login2faPrefTotpCode}
                    onChange={setLogin2faPrefTotpCode}
                    disabled={login2faPrefSaving}
                  />
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
                      <OtpCodeInput
                        digits={6}
                        value={login2faPrefEmailCode}
                        onChange={setLogin2faPrefEmailCode}
                        disabled={login2faPrefSaving}
                      />
                    </div>
                    <button
                      type="button"
                      disabled={sendingLogin2faPrefEmailCode || login2faPrefSaving || login2faPrefEmailCountdown > 0 || !emailOtpAllowedByPolicy}
                      onClick={async () => {
                        try {
                          setSendingLogin2faPrefEmailCode(true);
                          const resp = await sendAccountEmailVerificationCode('LOGIN_2FA_PREFERENCE');
                          const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
                          setLogin2faPrefEmailCountdown(wait);
                          toast.success('验证码已发送');
                        } catch (e) {
                          const msg = e instanceof Error ? e.message : '发送失败';
                          setLogin2faPrefErrorMsg(msg);
                          toast.error(msg);
                        } finally {
                          setSendingLogin2faPrefEmailCode(false);
                        }
                      }}
                      className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
                    >
                      {sendingLogin2faPrefEmailCode ? '发送中...' : login2faPrefEmailCountdown > 0 ? `${login2faPrefEmailCountdown}s` : '发送'}
                    </button>
                  </div>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  disabled={login2faPrefSaving}
                  onClick={closeLogin2faPrefDialog}
                  className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  取消
                </button>
                <button
                  type="button"
                  disabled={login2faPrefSaving}
                  onClick={async () => {
                    if (login2faPrefSaving) return;
                    try {
                      setLogin2faPrefSaving(true);
                      setLogin2faPrefErrorMsg(null);
                      if (login2faPrefMethod === 'totp') {
                        const code = login2faPrefTotpCode.trim();
                        if (!code) {
                          setLogin2faPrefErrorMsg('请输入动态验证码');
                          toast.error('请输入动态验证码');
                          return;
                        }
                        const updated = await updateMyLogin2faPreference({
                          enabled: login2faPrefTargetEnabled,
                          method: 'totp',
                          totpCode: code,
                        });
                        setSecurityPolicy(updated);
                      } else {
                        const code = login2faPrefEmailCode.trim();
                        if (!code) {
                          setLogin2faPrefErrorMsg('请输入邮箱验证码');
                          toast.error('请输入邮箱验证码');
                          return;
                        }
                        const updated = await updateMyLogin2faPreference({
                          enabled: login2faPrefTargetEnabled,
                          method: 'email',
                          emailCode: code,
                        });
                        setSecurityPolicy(updated);
                      }
                      toast.success(login2faPrefTargetEnabled ? '已开启登录二次验证' : '已关闭登录二次验证');
                      closeLogin2faPrefDialog();
                    } catch (e) {
                      const msg = e instanceof Error ? e.message : '保存失败';
                      setLogin2faPrefErrorMsg(msg);
                      toast.error(msg);
                    } finally {
                      setLogin2faPrefSaving(false);
                    }
                  }}
                  className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {login2faPrefSaving ? '保存中...' : (login2faPrefTargetEnabled ? '确认开启' : '确认关闭')}
                </button>
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
