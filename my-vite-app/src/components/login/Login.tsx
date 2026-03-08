import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import backgroundImage1 from '../../assets/images/1.png';
import backgroundImage2 from '../../assets/images/2.png';
import { getRegistrationStatus, login, resendLogin2faEmail, resendRegisterCode, verifyLogin2fa, verifyRegister } from '../../services/authService';
import { useAuth } from '../../contexts/AuthContext';
import OtpCodeInput from '../common/OtpCodeInput';
import AuthFooter from './AuthFooter';

interface LoginFormData {
    email: string; 
    password: string;
    rememberMe: boolean;
}

const Login: React.FC = () => {
    const navigate = useNavigate();
    const { setCurrentUser, setIsAuthenticated } = useAuth();
    const [formData, setFormData] = useState<LoginFormData>(() => {
        const rememberedEmail = localStorage.getItem('rememberedEmail');
        const rememberedPassword = localStorage.getItem('rememberedPassword');
        if (rememberedEmail) {
            return {
                email: rememberedEmail,
                password: rememberedPassword || '',
                rememberMe: true
            };
        }
        return { email: '', password: '', rememberMe: false };
    });

    const [csrfToken, setCsrfToken] = useState<string>('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [info, setInfo] = useState<string | null>(null);
    const [verifyMode, setVerifyMode] = useState(false);
    const [verifyEmail, setVerifyEmail] = useState('');
    const [verifyCode, setVerifyCode] = useState('');
    const [verifyLoading, setVerifyLoading] = useState(false);
    const [resendCooldownSeconds, setResendCooldownSeconds] = useState(0);

    const [login2faRequired, setLogin2faRequired] = useState(false);
    const [login2faMethods, setLogin2faMethods] = useState<Array<'email' | 'totp'>>([]);
    const [login2faMethod, setLogin2faMethod] = useState<'email' | 'totp' | null>(null);
    const [login2faEmailCode, setLogin2faEmailCode] = useState('');
    const [login2faTotpCode, setLogin2faTotpCode] = useState('');
    const [login2faTotpDigits, setLogin2faTotpDigits] = useState(6);
    const [registrationEnabled, setRegistrationEnabled] = useState(true);

    useEffect(() => {
        (async () => {
            try {
                const s = await getRegistrationStatus();
                setRegistrationEnabled(s.registrationEnabled);
            } catch {
                setRegistrationEnabled(true);
            }
        })();
    }, []);
    const [login2faSubmitting, setLogin2faSubmitting] = useState(false);
    const [login2faResendLoading, setLogin2faResendLoading] = useState(false);
    const [login2faResendCountdown, setLogin2faResendCountdown] = useState(0);
    const [login2faEmailHasSent, setLogin2faEmailHasSent] = useState(false);

    // 轮播图相关状态
    const [currentImage, setCurrentImage] = useState(backgroundImage1);
    const images = [backgroundImage1, backgroundImage2];

    useEffect(() => {
        const interval = setInterval(() => {
            setCurrentImage((prevImage: string) => {
                const currentIndex = images.indexOf(prevImage);
                const nextIndex = (currentIndex + 1) % images.length;
                return images[nextIndex];
            });
        }, 10000); // 每10秒切换图片

        return () => clearInterval(interval);
    }, []);

    // 在组件加载时获取CSRF令牌 - 安全保障措施，防止CSRF攻击
    useEffect(() => {
        fetch('/api/auth/csrf-token', {
            credentials: 'include' // 确保包含cookies
        })
            .then(response => response.json())
            .then(data => {
                if (data.token) {
                    setCsrfToken(data.token);
                }
            })
            .catch(err => {
                console.error('获取CSRF令牌失败:', err);
                setError('无法获取安全令牌，请刷新页面重试');
            });
    }, []);

    useEffect(() => {
        if (!verifyMode) {
            setResendCooldownSeconds(0);
            return;
        }

        const email = (verifyEmail || formData.email || '').trim();
        if (!email) {
            setResendCooldownSeconds(0);
            return;
        }

        const key = `login:resend-register-code:availableAt:${email}`;
        const availableAtMs = Number(localStorage.getItem(key) || 0);
        if (!Number.isFinite(availableAtMs) || availableAtMs <= 0) {
            setResendCooldownSeconds(0);
            return;
        }

        const tick = () => {
            const remaining = Math.max(0, Math.ceil((availableAtMs - Date.now()) / 1000));
            setResendCooldownSeconds(remaining);
            if (remaining <= 0) {
                localStorage.removeItem(key);
            }
        };

        tick();
        const intervalId = window.setInterval(tick, 1000);
        return () => window.clearInterval(intervalId);
    }, [verifyMode, verifyEmail, formData.email]);

    useEffect(() => {
        if (!login2faRequired) {
            setLogin2faResendCountdown(0);
            return;
        }
        if (login2faResendCountdown <= 0) return;
        const t = window.setInterval(() => {
            setLogin2faResendCountdown((s) => Math.max(0, s - 1));
        }, 1000);
        return () => window.clearInterval(t);
    }, [login2faRequired, login2faResendCountdown]);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value, type, checked } = e.target;
        
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' && name === 'rememberMe' ? !prev.rememberMe : type === 'checkbox' ? checked : value
        }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!csrfToken) {
            setError('安全令牌缺失，请刷新页面重试');
            return;
        }

        try {
            setLoading(true);
            setError(null);
            setInfo(null);

            // 使用 authService 的 login 函数，传递 CSRF 令牌
            const userData = await login(formData.email, formData.password, csrfToken); 

            // 更新全局认证状态
            setCurrentUser(userData);
            setIsAuthenticated(true);
            setVerifyMode(false);
            setLogin2faRequired(false);
            setLogin2faMethods([]);
            setLogin2faMethod(null);
            setLogin2faEmailCode('');
            setLogin2faTotpCode('');
            setLogin2faResendCountdown(0);
            setLogin2faEmailHasSent(false);

            // 保存到 localStorage 以实现记住我功能
            if (formData.rememberMe) {
                localStorage.setItem('userData', JSON.stringify(userData));
                localStorage.setItem('rememberedEmail', formData.email);
                localStorage.setItem('rememberedPassword', formData.password);
            } else {
                localStorage.removeItem('rememberedEmail');
                localStorage.removeItem('rememberedPassword');
            }

            // 导航到主页面
            navigate('/portal/discover/home');
        } catch (err) {
            const anyErr = err as Error & { code?: string; email?: string; methods?: string[]; resendWaitSeconds?: number; totpDigits?: number };
            if (anyErr.code === 'EMAIL_NOT_VERIFIED') {
                setVerifyMode(true);
                setLogin2faRequired(false);
                const email = (anyErr.email || formData.email || '').trim();
                setVerifyEmail(email);
                setError(anyErr.message || '账号未完成邮箱验证');
                return;
            }
            if (anyErr.code === 'LOGIN_2FA_REQUIRED') {
                const methods = (anyErr.methods || []).filter((m) => m === 'email' || m === 'totp') as Array<'email' | 'totp'>;
                setLogin2faRequired(true);
                setLogin2faMethods(methods);
                setLogin2faMethod(methods.length === 1 ? methods[0] : null);
                setLogin2faEmailCode('');
                setLogin2faTotpCode('');
                const digits = Number.isFinite(Number(anyErr.totpDigits)) ? Number(anyErr.totpDigits) : 6;
                setLogin2faTotpDigits(digits === 8 ? 8 : 6);
                setLogin2faResendCountdown(0);
                setLogin2faEmailHasSent(false);
                setVerifyMode(false);
                setError(null);
                setInfo(anyErr.message || '登录需要二次验证');
                return;
            }
            setError(anyErr.message || '登录失败');
            console.error('登录错误:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleVerify = async () => {
        const email = (verifyEmail || formData.email || '').trim();
        const code = verifyCode.trim();
        if (!email) {
            setError('缺少邮箱，请先输入邮箱');
            return;
        }
        if (!code) {
            setError('请输入邮箱验证码');
            return;
        }
        try {
            setVerifyLoading(true);
            setError(null);
            setInfo(null);
            await verifyRegister(email, code);
            setVerifyMode(false);
            setVerifyCode('');
            setInfo('激活成功，请使用邮箱和密码登录');
        } catch (e) {
            setError((e as Error).message || '激活失败');
        } finally {
            setVerifyLoading(false);
        }
    };

    const handleResend = async () => {
        const email = (verifyEmail || formData.email || '').trim();
        if (!email) {
            setError('缺少邮箱，请先输入邮箱');
            return;
        }
        if (resendCooldownSeconds > 0) {
            setError(`请稍后再试（${resendCooldownSeconds}秒后可重新发送）`);
            return;
        }
        try {
            setVerifyLoading(true);
            setError(null);
            setInfo(null);
            const resp = await resendRegisterCode(email);
            const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 120;
            const availableAtMs = Date.now() + wait * 1000;
            localStorage.setItem(`login:resend-register-code:availableAt:${email}`, String(availableAtMs));
            setResendCooldownSeconds(wait);
            setInfo('验证码已发送，请查收邮箱');
        } catch (e) {
            setError((e as Error).message || '发送失败');
        } finally {
            setVerifyLoading(false);
        }
    };

    const handleLogin2faResend = async () => {
        if (login2faResendCountdown > 0) {
            setError(`请稍后再试（${login2faResendCountdown}秒后可重新发送）`);
            return;
        }
        try {
            setLogin2faResendLoading(true);
            setError(null);
            setInfo(null);
            const resp = await resendLogin2faEmail();
            const wait = Number.isFinite(Number(resp?.resendWaitSeconds)) ? Number(resp.resendWaitSeconds) : 60;
            setLogin2faResendCountdown(wait);
            setLogin2faEmailHasSent(true);
            setInfo(resp.message || '验证码已发送，请查收邮箱');
        } catch (e) {
            setError((e as Error).message || '发送失败');
        } finally {
            setLogin2faResendLoading(false);
        }
    };

    const verifyLogin2faCode = async (method: 'email' | 'totp', code: string) => {
        if (!login2faRequired) return;
        const trimmed = (code || '').trim();
        if (!trimmed) {
            setError('请输入验证码');
            return;
        }
        if (login2faSubmitting) return;
        try {
            setLogin2faSubmitting(true);
            setError(null);
            setInfo(null);
            const userData = await verifyLogin2fa(method, trimmed);
            setCurrentUser(userData);
            setIsAuthenticated(true);
            setLogin2faRequired(false);
            setLogin2faMethods([]);
            setLogin2faMethod(null);
            setLogin2faEmailCode('');
            setLogin2faTotpCode('');
            setLogin2faResendCountdown(0);
            navigate('/portal/discover/home');
        } catch (e) {
            setError((e as Error).message || '验证失败');
        } finally {
            setLogin2faSubmitting(false);
        }
    };

    const handleLogin2faVerify = async () => {
        if (!login2faRequired) return;
        if (!login2faMethod) {
            setError('请选择验证方式');
            return;
        }
        const code = (login2faMethod === 'email' ? login2faEmailCode : login2faTotpCode).trim();
        await verifyLogin2faCode(login2faMethod, code);
    };

    return (
        <div className="bg-cover bg-center h-screen flex flex-col"
             style={{ backgroundImage: `url(${currentImage})` }}>
            <div className="flex items-center justify-center flex-grow">
                <div className="bg-white p-8 rounded-lg shadow-lg w-96">
                    <div className="flex items-center mb-6">
                        <i className="fas fa-book fa-2x mr-2"></i>
                        <h1 className="text-2xl font-bold">RAG技术学习探索笔记</h1>
                    </div>

                    {error && (
                        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
                            {error}
                        </div>
                    )}
                    {info && (
                        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
                            {info}
                        </div>
                    )}

                    <form onSubmit={handleSubmit}>
                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2"
                                   htmlFor="email">邮箱</label> 
                            <input
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                                id="email"
                                name="email"
                                type="email"
                                value={formData.email}
                                onChange={handleInputChange}
                                required
                            />
                        </div>
                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2"
                                   htmlFor="password">密码</label>
                            <input
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                                id="password"
                                name="password"
                                type="password"
                                value={formData.password}
                                onChange={handleInputChange}
                                required
                            />
                        </div>
                        {login2faRequired && (
                            <div className="mb-4">
                                <div className="text-sm text-gray-600 mb-2">该账号登录需要二次验证</div>

                                {login2faMethods.length > 1 && (
                                    <div className="flex gap-2 mb-3">
                                        <button
                                            type="button"
                                            className={`flex-1 font-bold py-2 px-3 rounded focus:outline-none focus:shadow-outline ${
                                                login2faMethod === 'totp' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-800 hover:bg-gray-300'
                                            }`}
                                            onClick={() => setLogin2faMethod('totp')}
                                        >
                                            使用 TOTP
                                        </button>
                                        <button
                                            type="button"
                                            className={`flex-1 font-bold py-2 px-3 rounded focus:outline-none focus:shadow-outline ${
                                                login2faMethod === 'email' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-800 hover:bg-gray-300'
                                            }`}
                                            onClick={() => setLogin2faMethod('email')}
                                        >
                                            使用邮箱验证码
                                        </button>
                                    </div>
                                )}

                                {login2faMethod === 'totp' && (
                                    <div className="mb-3">
                                        <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="login2faTotpCode">动态验证码</label>
                                        <div id="login2faTotpCode">
                                            <OtpCodeInput
                                                digits={login2faTotpDigits}
                                                value={login2faTotpCode}
                                                onChange={setLogin2faTotpCode}
                                                onComplete={(code) => void verifyLogin2faCode('totp', code)}
                                                disabled={login2faSubmitting}
                                                autoFocus
                                            />
                                        </div>
                                    </div>
                                )}

                                {login2faMethod === 'email' && (
                                    <div className="mb-3">
                                        <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="login2faEmailCode">邮箱验证码</label>
                                        <input
                                            className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                                            id="login2faEmailCode"
                                            name="login2faEmailCode"
                                            type="text"
                                            value={login2faEmailCode}
                                            onChange={e => setLogin2faEmailCode(e.target.value)}
                                        />
                                        <div className="mt-3 flex justify-end">
                                            <button
                                                type="button"
                                                className={`bg-gray-200 hover:bg-gray-300 text-gray-800 font-bold py-2 px-3 rounded focus:outline-none focus:shadow-outline ${
                                                    (login2faResendLoading || login2faResendCountdown > 0) ? 'opacity-50 cursor-not-allowed' : ''
                                                }`}
                                                onClick={handleLogin2faResend}
                                                disabled={login2faResendLoading || login2faResendCountdown > 0}
                                            >
                                                {login2faResendLoading ? '发送中...' : login2faResendCountdown > 0 ? `${login2faResendCountdown}s` : (login2faEmailHasSent ? '重发验证码' : '发送验证码')}
                                            </button>
                                        </div>
                                    </div>
                                )}

                                <button
                                    type="button"
                                    className={`w-full bg-green-600 hover:bg-green-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${
                                        login2faSubmitting ? 'opacity-50 cursor-not-allowed' : ''
                                    }`}
                                    onClick={handleLogin2faVerify}
                                    disabled={login2faSubmitting}
                                >
                                    {login2faSubmitting ? '验证中...' : '完成验证'}
                                </button>
                            </div>
                        )}
                        {verifyMode && (
                            <div className="mb-4">
                                <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="verifyCode">邮箱验证码</label>
                                <input
                                    className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                                    id="verifyCode"
                                    name="verifyCode"
                                    type="text"
                                    value={verifyCode}
                                    onChange={e => setVerifyCode(e.target.value)}
                                />
                                <div className="flex gap-2 mt-3">
                                    <button
                                        type="button"
                                        className={`flex-1 bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-3 rounded focus:outline-none focus:shadow-outline ${verifyLoading ? 'opacity-50 cursor-not-allowed' : ''}`}
                                        onClick={handleVerify}
                                        disabled={verifyLoading}
                                    >
                                        {verifyLoading ? '处理中...' : '验证激活'}
                                    </button>
                                    <button
                                        type="button"
                                        className={`flex-1 bg-gray-200 hover:bg-gray-300 text-gray-800 font-bold py-2 px-3 rounded focus:outline-none focus:shadow-outline ${(verifyLoading || resendCooldownSeconds > 0) ? 'opacity-50 cursor-not-allowed' : ''}`}
                                        onClick={handleResend}
                                        disabled={verifyLoading || resendCooldownSeconds > 0}
                                    >
                                        {resendCooldownSeconds > 0
                                            ? `重发验证码（${Math.floor(resendCooldownSeconds / 60)}:${String(resendCooldownSeconds % 60).padStart(2, '0')}）`
                                            : '重发验证码'}
                                    </button>
                                </div>
                            </div>
                        )}
                        <div className="mb-4 flex items-center">
                            <input
                                type="checkbox"
                                id="rememberMe"
                                name="rememberMe"
                                className="mr-2 leading-tight cursor-pointer"
                                checked={formData.rememberMe}
                                onChange={handleInputChange}
                            />
                            <label htmlFor="rememberMe" className="text-sm text-gray-700 cursor-pointer select-none">
                                记住密码
                            </label>
                        </div>
                        <div className="flex items-center justify-between">
                            <button
                                className={`bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                                type="submit"
                                disabled={loading || verifyMode || login2faRequired}>
                                {loading ? '登录中...' : '登录'}
                            </button>
                            <div className="flex gap-4">
                                <Link to="/forgot-password" title="忘记密码" className="inline-block align-baseline font-bold text-sm text-blue-500 hover:text-blue-800">
                                    忘记密码？
                                </Link>
                                {registrationEnabled ? (
                                    <Link to="/register" className="inline-block align-baseline font-bold text-sm text-blue-500 hover:text-blue-800">
                                        注册
                                    </Link>
                                ) : null}
                            </div>
                        </div>
                    </form>
                </div>
            </div>
            <AuthFooter />
        </div>
    );
};

export default Login;
