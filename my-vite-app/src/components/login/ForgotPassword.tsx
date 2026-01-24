import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import backgroundImage1 from '../../assets/images/login_1.png';
import {
  getPasswordResetStatus,
  resetPasswordByEmailCode,
  resetPasswordByTotp,
  sendPasswordResetEmailCode,
  type PasswordResetStatusResponse,
} from '../../services/passwordResetService';

type Step = 'email' | 'reset' | 'done';

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [currentImage, setCurrentImage] = useState(backgroundImage1);
  const images = useMemo(() => [backgroundImage1], []);

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentImage((prev: string) => {
        const i = images.indexOf(prev);
        const next = (i + 1) % images.length;
        return images[next];
      });
    }, 2000);
    return () => clearInterval(interval);
  }, [images]);

  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<PasswordResetStatusResponse | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [newPwd, setNewPwd] = useState('');
  const [confirmNewPwd, setConfirmNewPwd] = useState('');
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [message, setMessage] = useState<{ type: 'error' | 'success' | ''; text: string }>({ type: '', text: '' });

  // 用户选择的验证方式：null 表示未选择，'totp' 或 'email'
  const [verifyMethod, setVerifyMethod] = useState<'totp' | 'email' | null>(null);

  const validateReset = () => {
    // 如果用户选择了 TOTP，则只校验 TOTP
    if (verifyMethod === 'totp') {
      const code = totpCode.trim();
      if (!code) return '请输入动态验证码';
      if (!(code.length === 6 || code.length === 8) || !/^\d+$/.test(code)) return '动态验证码格式不正确';
    }
    // 如果用户选择了邮箱，则只校验邮箱验证码
    else if (verifyMethod === 'email') {
      const code = emailCode.trim();
      if (!code) return '请输入邮箱验证码';
    }
    // 密码相关校验
    if (!newPwd.trim()) return '请输入新密码';
    if (newPwd.length < 6) return '新密码长度至少 6 位';
    if (newPwd !== confirmNewPwd) return '两次输入的新密码不一致';
    return null;
  };

  const submitEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    const em = email.trim();
    if (!em) return;
    setLoading(true);
    setMessage({ type: '', text: '' });
    try {
      const resp = await getPasswordResetStatus(em);
      setStatus(resp);
      if (!resp.allowed) {
        setStep('email');
        setMessage({ type: 'error', text: resp.message || '暂不支持找回密码' });
        return;
      }
      setStep('reset');
      // 如果同时支持 TOTP 和邮箱，默认不预选；否则按能力预选
      if (resp.totpEnabled && resp.emailEnabled) {
        setVerifyMethod(null);
      } else if (resp.totpEnabled) {
        setVerifyMethod('totp');
      } else if (resp.emailEnabled) {
        setVerifyMethod('email');
      }
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof Error ? err.message : '查询失败' });
    } finally {
      setLoading(false);
    }
  };

  const submitReset = async (e: React.FormEvent) => {
    e.preventDefault();
    const err = validateReset();
    if (err) {
      setMessage({ type: 'error', text: err });
      return;
    }
    setLoading(true);
    setMessage({ type: '', text: '' });
    try {
      if (verifyMethod === 'totp') {
        await resetPasswordByTotp(email.trim(), totpCode.trim(), newPwd);
      } else if (verifyMethod === 'email') {
        await resetPasswordByEmailCode(email.trim(), emailCode.trim(), newPwd);
      }
      setStep('done');
      setMessage({ type: 'success', text: '密码已重置，请使用新密码登录' });
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof Error ? err.message : '重置失败' });
    } finally {
      setLoading(false);
    }
  };

  const sendCode = async () => {
    const em = email.trim();
    if (!em) return;
    setSendingCode(true);
    setMessage({ type: '', text: '' });
    try {
      await sendPasswordResetEmailCode(em);
      setMessage({ type: 'success', text: '验证码已发送，请检查邮箱' });
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof Error ? err.message : '发送失败' });
    } finally {
      setSendingCode(false);
    }
  };

  return (
    <div className="bg-cover bg-center h-screen flex flex-col" style={{ backgroundImage: `url(${currentImage})` }}>
      <div className="flex items-center justify-center flex-grow">
        <div className="bg-white p-8 rounded-lg shadow-lg w-96">
          <div className="flex items-center mb-6">
            <i className="fas fa-key fa-2x mr-2"></i>
            <h1 className="text-2xl font-bold">找回密码</h1>
          </div>

          {message.text && (
            <div
              className={`p-4 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-700'}`}
            >
              {message.text}
            </div>
          )}

          {step === 'email' ? (
            <form className="space-y-4" onSubmit={submitEmail}>
              <div>
                <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="email">
                  邮箱
                </label>
                <input
                  className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  id="email"
                  name="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
              <button
                className={`w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                type="submit"
                disabled={loading}
              >
                {loading ? '处理中...' : '下一步'}
              </button>
              <div className="flex items-center justify-between text-sm">
                <Link to="/login" className="font-bold text-blue-500 hover:text-blue-800">
                  返回登录
                </Link>
                <Link to="/register" className="font-bold text-blue-500 hover:text-blue-800">
                  注册
                </Link>
              </div>
            </form>
          ) : null}

          {step === 'reset' ? (
            <form className="space-y-4" onSubmit={submitReset}>
              {/* 顶部提示：同时开启时引导用户二选一 */}
              {status?.totpEnabled && status?.emailEnabled && (
                <div className="text-sm text-gray-600">
                  该账号同时支持 TOTP 和邮箱验证码，请选择其中一种方式验证：
                </div>
              )}
              {/* 仅开启 TOTP 时的提示 */}
              {!status?.emailEnabled && status?.totpEnabled && (
                <div className="text-sm text-gray-600">该账号已启用 TOTP，请输入动态验证码并设置新密码。</div>
              )}
              {/* 仅开启邮箱时的提示 */}
              {status?.emailEnabled && !status?.totpEnabled && (
                <div className="text-sm text-gray-600">该账号可通过邮箱验证码找回密码。</div>
              )}

              {/* 新密码与确认密码始终显示 */}
              <div>
                <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="newPwd">
                  新密码
                </label>
                <input
                  className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  id="newPwd"
                  name="newPwd"
                  type="password"
                  value={newPwd}
                  onChange={(e) => setNewPwd(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="confirmNewPwd">
                  确认新密码
                </label>
                <input
                  className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                  id="confirmNewPwd"
                  name="confirmNewPwd"
                  type="password"
                  value={confirmNewPwd}
                  onChange={(e) => setConfirmNewPwd(e.target.value)}
                  required
                />
              </div>

              {/* 如果同时支持 TOTP 和邮箱，展示二选一按钮 */}
              {status?.totpEnabled && status?.emailEnabled && (
                <div className="flex items-center space-x-4 mb-2">
                  <button
                    type="button"
                    className={`flex-1 py-2 px-4 rounded border ${verifyMethod === 'totp' ? 'bg-blue-600 text-white' : 'bg-white text-blue-600 border-blue-600'}`}
                    onClick={() => setVerifyMethod('totp')}
                  >
                    使用 TOTP 验证
                  </button>
                  <button
                    type="button"
                    className={`flex-1 py-2 px-4 rounded border ${verifyMethod === 'email' ? 'bg-blue-600 text-white' : 'bg-white text-blue-600 border-blue-600'}`}
                    onClick={() => setVerifyMethod('email')}
                  >
                    使用邮箱验证码
                  </button>
                </div>
              )}

              {/* 根据用户选择或单一能力展示对应输入 */}
              {(verifyMethod === 'totp' || (status?.totpEnabled && !status?.emailEnabled)) && (
                <div>
                  <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="totpCode">
                    动态验证码
                  </label>
                  <input
                    className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                    id="totpCode"
                    name="totpCode"
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value)}
                    placeholder="6 或 8 位数字"
                    inputMode="numeric"
                    required={verifyMethod === 'totp'}
                  />
                </div>
              )}

              {(verifyMethod === 'email' || (status?.emailEnabled && !status?.totpEnabled)) && (
                <div className="space-y-2">
                  <button
                    type="button"
                    className={`w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${sendingCode ? 'opacity-50 cursor-not-allowed' : ''}`}
                    onClick={sendCode}
                    disabled={sendingCode || loading}
                  >
                    {sendingCode ? '发送中...' : '发送邮箱验证码'}
                  </button>
                  <div>
                    <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="emailCode">
                      邮箱验证码
                    </label>
                    <input
                      className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                      id="emailCode"
                      name="emailCode"
                      value={emailCode}
                      onChange={(e) => setEmailCode(e.target.value)}
                      required={verifyMethod === 'email'}
                    />
                  </div>
                </div>
              )}

              <button
                className={`w-full bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                type="submit"
                disabled={loading || (status?.totpEnabled && status?.emailEnabled && !verifyMethod)}
              >
                {loading ? '重置中...' : '重置密码'}
              </button>

              <div className="flex items-center justify-between text-sm">
                <button
                  type="button"
                  className="font-bold text-blue-500 hover:text-blue-800"
                  onClick={() => {
                    setStep('email');
                    setStatus(null);
                    setTotpCode('');
                    setEmailCode('');
                    setNewPwd('');
                    setConfirmNewPwd('');
                    setMessage({ type: '', text: '' });
                    setVerifyMethod(null);
                  }}
                >
                  上一步
                </button>
                <Link to="/login" className="font-bold text-blue-500 hover:text-blue-800">
                  返回登录
                </Link>
              </div>
            </form>
          ) : null}

          {step === 'done' ? (
            <div className="space-y-4">
              <button
                type="button"
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline"
                onClick={() => navigate('/login', { replace: true, state: { email: email.trim() } })}
              >
                去登录
              </button>
            </div>
          ) : null}

          {status && !status.allowed ? (
            <div className="mt-4 text-xs text-gray-500">
              {status.message || '该账号暂不支持找回密码（需启用 TOTP 或开启邮箱验证码服务）。'}
            </div>
          ) : null}
        </div>
      </div>
      <div className="text-center text-white p-4">
        <h2 className="text-3xl font-bold">解锁知识的世界</h2>
        <p className="text-lg">加入我们的社区，探索成千上万的新闻</p>
        <p className="text-sm mt-4">©2024. 版权所有。</p>
      </div>
    </div>
  );
}
