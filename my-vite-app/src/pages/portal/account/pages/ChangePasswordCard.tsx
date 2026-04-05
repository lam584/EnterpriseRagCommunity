import { useEffect, useState } from "react";
import { toast } from "react-hot-toast";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../../../contexts/AuthContext";
import OtpCodeInput from "../../../../components/common/OtpCodeInput";
import { logout } from "../../../../services/authService";
import { changePassword } from "../../../../services/accountService";
import { sendAccountEmailVerificationCode } from "../../../../services/emailVerificationService";
import { validateChangePasswordForm } from "./accountSecurity.validation";

type VerifyMethod = "totp" | "email";

export default function ChangePasswordCard(props: {
  enabled: boolean;
  activeTotpDigits: 6 | 8;
  policyLoaded: boolean;
  totpAllowedByPolicy: boolean;
  totpRequiredByPolicy: boolean;
  emailOtpAllowedByPolicy: boolean;
  emailOtpRequiredByPolicy: boolean;
  errorMsg: string | null;
  setErrorMsg: (v: string | null) => void;
}) {
  const navigate = useNavigate();
  const { setCurrentUser, setIsAuthenticated } = useAuth();

  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [oldPwd, setOldPwd] = useState("");
  const [newPwd, setNewPwd] = useState("");
  const [confirmNewPwd, setConfirmNewPwd] = useState("");
  const [changeTotpCode, setChangeTotpCode] = useState("");
  const [changeEmailCode, setChangeEmailCode] = useState("");
  const [sendingChangeEmailCode, setSendingChangeEmailCode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [pwdStep, setPwdStep] = useState(0);
  const [verifyMethod, setVerifyMethod] = useState<VerifyMethod>(props.enabled ? "totp" : "email");
  const [pwdEmailCountdown, setPwdEmailCountdown] = useState(0);

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
    setVerifyMethod(props.enabled ? "totp" : "email");
  }, [props.enabled]);

  const canUseTotpForPwd = props.enabled && props.totpAllowedByPolicy;
  const canUseEmailForPwd = props.emailOtpAllowedByPolicy;
  const emailCodeButtonLabel = sendingChangeEmailCode ? "发送中..." : pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : "发送";

  const handleSendChangeEmailCode = async () => {
    try {
      setSendingChangeEmailCode(true);
      const resp = await sendAccountEmailVerificationCode("CHANGE_PASSWORD");
      const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 180;
      setPwdEmailCountdown(wait);
      toast.success("验证码已发送");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "发送失败";
      props.setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSendingChangeEmailCode(false);
    }
  };

  const renderEmailCodeInput = (placeholder: string, disabled: boolean) => (
    <div className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_auto] md:items-end">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">邮箱验证码</label>
        <input
          type="text"
          value={changeEmailCode}
          onChange={(e) => setChangeEmailCode(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder={placeholder}
        />
      </div>
      <button
        type="button"
        disabled={disabled || sendingChangeEmailCode || saving || pwdEmailCountdown > 0}
        onClick={handleSendChangeEmailCode}
        className="px-4 py-2 rounded-md border bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed w-28"
      >
        {emailCodeButtonLabel}
      </button>
    </div>
  );

  const handleNext = () => {
    const err = validateChangePasswordForm({ oldPwd, newPwd, confirmNewPwd });
    if (err) {
      props.setErrorMsg(err);
      toast.error(err);
      return;
    }
    props.setErrorMsg(null);
    setPwdStep(1);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const err = validateChangePasswordForm({ oldPwd, newPwd, confirmNewPwd });
    if (err) {
      props.setErrorMsg(err);
      toast.error(err);
      return;
    }

    const totpCodeTrimmed = changeTotpCode.trim();
    const emailCodeTrimmed = changeEmailCode.trim();

    if (props.policyLoaded) {
      if (props.totpRequiredByPolicy && !props.enabled) {
        props.setErrorMsg("管理员已强制启用 TOTP，请先启用后再修改密码");
        toast.error("管理员已强制启用 TOTP，请先启用后再修改密码");
        return;
      }
      if (props.totpRequiredByPolicy && !totpCodeTrimmed) {
        props.setErrorMsg("请输入动态验证码");
        toast.error("请输入动态验证码");
        return;
      }
      if (props.emailOtpRequiredByPolicy && !emailCodeTrimmed) {
        props.setErrorMsg("请输入邮箱验证码");
        toast.error("请输入邮箱验证码");
        return;
      }

      if (!props.totpRequiredByPolicy && !props.emailOtpRequiredByPolicy) {
        if (canUseTotpForPwd && canUseEmailForPwd) {
          if (verifyMethod === "totp") {
            if (!totpCodeTrimmed) {
              props.setErrorMsg("请输入动态验证码");
              toast.error("请输入动态验证码");
              return;
            }
          } else {
            if (!emailCodeTrimmed) {
              props.setErrorMsg("请输入邮箱验证码");
              toast.error("请输入邮箱验证码");
              return;
            }
          }
        } else if (canUseTotpForPwd) {
          if (!totpCodeTrimmed) {
            props.setErrorMsg("请输入动态验证码");
            toast.error("请输入动态验证码");
            return;
          }
        } else if (canUseEmailForPwd) {
          if (!emailCodeTrimmed) {
            props.setErrorMsg("请输入邮箱验证码");
            toast.error("请输入邮箱验证码");
            return;
          }
        }
      }
    } else {
      if (props.enabled) {
        if (verifyMethod === "totp") {
          if (!totpCodeTrimmed) {
            props.setErrorMsg("请输入动态验证码");
            toast.error("请输入动态验证码");
            return;
          }
        } else {
          if (!emailCodeTrimmed) {
            props.setErrorMsg("请输入邮箱验证码");
            toast.error("请输入邮箱验证码");
            return;
          }
        }
      } else {
        if (!emailCodeTrimmed) {
          props.setErrorMsg("请输入邮箱验证码");
          toast.error("请输入邮箱验证码");
          return;
        }
      }
    }

    try {
      props.setErrorMsg(null);
      setSaving(true);
      await changePassword({
        currentPassword: oldPwd,
        newPassword: newPwd,
        totpCode: props.policyLoaded
          ? (props.totpRequiredByPolicy
            ? totpCodeTrimmed
            : (props.enabled && props.totpAllowedByPolicy && (!props.emailOtpAllowedByPolicy || verifyMethod === "totp") ? totpCodeTrimmed : undefined))
          : ((props.enabled && verifyMethod === "totp") ? totpCodeTrimmed : undefined),
        emailCode: props.policyLoaded
          ? (props.emailOtpRequiredByPolicy
            ? emailCodeTrimmed
            : (props.emailOtpAllowedByPolicy && (!props.enabled || !props.totpAllowedByPolicy || verifyMethod === "email") ? emailCodeTrimmed : undefined))
          : ((!props.enabled || verifyMethod === "email") ? emailCodeTrimmed : undefined),
      });
      toast.success("密码修改成功，请重新登录");
      setOldPwd("");
      setNewPwd("");
      setConfirmNewPwd("");
      setChangeTotpCode("");
      setChangeEmailCode("");
      setShowPasswordForm(false);
      setPwdStep(0);

      await new Promise((resolve) => window.setTimeout(resolve, 800));
      try {
        await logout();
      } catch {
      } finally {
        try {
          localStorage.removeItem("userData");
        } catch {
        }
        setCurrentUser(null);
        setIsAuthenticated(false);
        navigate("/login", { replace: true });
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "密码更新失败";
      props.setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
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
              setOldPwd("");
              setNewPwd("");
              setConfirmNewPwd("");
              setChangeTotpCode("");
              setChangeEmailCode("");
              setPwdStep(0);
              props.setErrorMsg(null);
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
                    setOldPwd("");
                    setNewPwd("");
                    setConfirmNewPwd("");
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

                {props.policyLoaded ? (
                  <div className="space-y-3">
                    {props.totpRequiredByPolicy && !props.enabled ? (
                      <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                        管理员已强制启用 TOTP；当前账号尚未启用，请先完成 TOTP 绑定后再修改密码。
                      </div>
                    ) : null}

                    {props.totpRequiredByPolicy ? (
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                        <OtpCodeInput
                          digits={props.activeTotpDigits}
                          value={changeTotpCode}
                          onChange={setChangeTotpCode}
                          disabled={saving}
                        />
                      </div>
                    ) : null}

                    {props.emailOtpRequiredByPolicy ? renderEmailCodeInput("请输入验证码", !canUseEmailForPwd) : null}

                    {!props.totpRequiredByPolicy && !props.emailOtpRequiredByPolicy ? (
                      canUseTotpForPwd && canUseEmailForPwd ? (
                        <div className="space-y-3">
                          <div className="flex gap-6 mb-2">
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="radio"
                                name="verifyMethod"
                                checked={verifyMethod === "totp"}
                                onChange={() => setVerifyMethod("totp")}
                                className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                              />
                              <span className="text-sm font-medium text-gray-700">动态验证码</span>
                            </label>
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="radio"
                                name="verifyMethod"
                                checked={verifyMethod === "email"}
                                onChange={() => setVerifyMethod("email")}
                                className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                              />
                              <span className="text-sm font-medium text-gray-700">邮箱验证码</span>
                            </label>
                          </div>

                          {verifyMethod === "totp" ? (
                            <div>
                              <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                              <OtpCodeInput
                                digits={props.activeTotpDigits}
                                value={changeTotpCode}
                                onChange={setChangeTotpCode}
                                disabled={saving}
                              />
                            </div>
                          ) : (
                            renderEmailCodeInput("请输入验证码", !canUseEmailForPwd)
                          )}
                        </div>
                      ) : canUseTotpForPwd ? (
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                          <OtpCodeInput
                            digits={props.activeTotpDigits}
                            value={changeTotpCode}
                            onChange={setChangeTotpCode}
                            disabled={saving}
                          />
                        </div>
                      ) : canUseEmailForPwd ? (
                        renderEmailCodeInput("请输入验证码", !canUseEmailForPwd)
                      ) : (
                        <div className="text-sm text-gray-500">当前无需验证码。</div>
                      )
                    ) : null}
                  </div>
                ) : (
                  props.enabled ? (
                    <div className="space-y-3">
                      <div className="flex gap-6 mb-2">
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="verifyMethod"
                            checked={verifyMethod === "totp"}
                            onChange={() => setVerifyMethod("totp")}
                            className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                          />
                          <span className="text-sm font-medium text-gray-700">动态验证码</span>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="verifyMethod"
                            checked={verifyMethod === "email"}
                            onChange={() => setVerifyMethod("email")}
                            className="w-4 h-4 text-blue-600 border-gray-300 focus:ring-blue-500"
                          />
                          <span className="text-sm font-medium text-gray-700">邮箱验证码</span>
                        </label>
                      </div>

                      {verifyMethod === "totp" && (
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">动态验证码</label>
                          <OtpCodeInput
                            digits={props.activeTotpDigits}
                            value={changeTotpCode}
                            onChange={setChangeTotpCode}
                            disabled={saving}
                          />
                        </div>
                      )}

                      {verifyMethod === "email" && renderEmailCodeInput("请输入验证码", false)}
                    </div>
                  ) : (
                    renderEmailCodeInput("开启邮箱验证时必填", false)
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
                    {saving ? "更新中..." : "更新密码"}
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
  );
}
