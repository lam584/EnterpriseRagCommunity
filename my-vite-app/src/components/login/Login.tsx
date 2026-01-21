import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import backgroundImage1 from '../../assets/images/login_1.png';
import backgroundImage2 from '../../assets/images/2.png';
import { login } from '../../services/authService';
import { useAuth } from '../../contexts/AuthContext';

interface LoginFormData {
    email: string; 
    password: string;
    rememberMe: boolean;
}

const Login: React.FC = () => {
    const navigate = useNavigate();
    const { setCurrentUser, setIsAuthenticated } = useAuth();
    const [formData, setFormData] = useState<LoginFormData>({
        email: '', 
        password: '',
        rememberMe: false
    });

    const [csrfToken, setCsrfToken] = useState<string>('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

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
        }, 2000); // 每5秒切换图片

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

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value, type, checked } = e.target;
        
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
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

            // 使用 authService 的 login 函数，传递 CSRF 令牌
            const userData = await login(formData.email, formData.password, csrfToken); 

            // 更新全局认证状态
            setCurrentUser(userData);
            setIsAuthenticated(true);

            // 保存到 localStorage 以实现记住我功能
            if (formData.rememberMe) {
                localStorage.setItem('userData', JSON.stringify(userData));
            }

            // 导航到主页面
            navigate('/helpCenter');
        } catch (err) {
            setError((err as Error).message || '登录失败');
            console.error('登录错误:', err);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-cover bg-center h-screen flex flex-col"
             style={{ backgroundImage: `url(${currentImage})` }}>
            <div className="flex items-center justify-center flex-grow">
                <div className="bg-white p-8 rounded-lg shadow-lg w-96">
                    <div className="flex items-center mb-6">
                        <i className="fas fa-book fa-2x mr-2"></i>
                        <h1 className="text-2xl font-bold">企业知识检索增强社区系统</h1>
                    </div>

                    {error && (
                        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
                            {error}
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
                        <div className="mb-4">
                            <input
                                type="checkbox"
                                id="rememberMe"
                                name="rememberMe"
                                className="mr-2 leading-tight"
                                checked={formData.rememberMe}
                                onChange={handleInputChange}
                            />
                            <label htmlFor="rememberMe" className="text-sm text-gray-700">
                                自动登录
                            </label>
                        </div>
                        <div className="flex items-center justify-between">
                            <button
                                className={`bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                                type="submit"
                                disabled={loading}>
                                {loading ? '登录中...' : '登录'}
                            </button>
                            <Link to="/register" className="inline-block align-baseline font-bold text-sm text-blue-500 hover:text-blue-800">
                                注册
                            </Link>
                        </div>
                    </form>
                </div>
            </div>
            <div className="text-center text-white p-4">
                <h2 className="text-3xl font-bold">解锁知识的世界</h2>
                <p className="text-lg">加入我们的社区，探索成千上万的新闻</p>
                <p className="text-sm mt-4">©2024. 版权所有。</p>
            </div>
        </div>
    );
};

export default Login;
