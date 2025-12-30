import React, {useEffect, useMemo, useState} from 'react';
import { useNavigate } from 'react-router-dom';
import backgroundImage1 from '../../assets/images/login_1.png';
import { register } from '../../services/authService';
interface RegisterFormData {
    username: string;
    password: string;
    confirmPassword: string;
    email: string;
}



const Register: React.FC = () => {
    const [currentImage, setCurrentImage] = useState(backgroundImage1);
    const images = useMemo(() => [backgroundImage1], []);

    useEffect(() => {
        const interval = setInterval(() => {
            setCurrentImage((prevImage: string) => {
                const currentIndex = images.indexOf(prevImage);
                const nextIndex = (currentIndex + 1) % images.length;
                return images[nextIndex];
            });
        }, 2000); // 每5秒切换图片

        return () => clearInterval(interval);
    }, [images]);

    const navigate = useNavigate();
    const [formData, setFormData] = useState<RegisterFormData>({
        username: '',
        password: '',
        confirmPassword: '',
        email: ''
    });

    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [message, setMessage] = useState({ type: '', text: '' });

    // 表单验证
    const validateForm = () => {
        const newErrors: Record<string, string> = {};

        if (!formData.username.trim()) newErrors.username = '用户名不能为空';
        else if (formData.username.trim().length < 2 || formData.username.trim().length > 20)
            newErrors.username = '用户名长度必须在2-20位之间';

        if (!formData.password.trim()) newErrors.password = '密码不能为空';
        else if (formData.password.length < 6 || formData.password.length > 20)
            newErrors.password = '密码长度必须在6-20位之间';

        if (formData.password !== formData.confirmPassword)
            newErrors.confirmPassword = '两次输入的密码不一致';

        if (!formData.email.trim()) newErrors.email = '邮箱不能为空';
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email))
            newErrors.email = '邮箱格式不正确';

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));

        // 清除该字段的错误
        if (errors[name]) {
            setErrors(prev => {
                const newErrors = { ...prev };
                delete newErrors[name];
                return newErrors;
            });
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateForm()) return;

        setLoading(true);
        setMessage({ type: '', text: '' });

        try {
            await register({
                email: formData.email.trim(),
                password: formData.password,
                username: formData.username.trim()
            });

            setMessage({
                type: 'success',
                text: '注册成功！请使用您的邮箱和密码登录'
            });

            setTimeout(() => {
                navigate('/login', { state: { email: formData.email.trim() } });
            }, 1500);
        } catch (error) {
            setMessage({
                type: 'error',
                text: error instanceof Error ? error.message : '注册失败，请稍后重试'
            });
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
                        <h1 className="text-2xl font-bold">新用户注册</h1>
                    </div>

                    {message.text && (
                        <div
                            className={`p-4 mb-4 rounded ${
                                message.type === 'success'
                                    ? 'bg-green-100 text-green-800'
                                    : 'bg-red-100 text-red-700'
                            }`}
                        >
                            {message.text}
                        </div>
                    )}

                    <form className="space-y-6" onSubmit={handleSubmit}>
                        {/* 用户名 */}
                        <div>
                            <label htmlFor="username" className="block text-sm font-medium text-gray-700">
                                用户名 <span className="text-red-500">*</span>
                            </label>
                            <div className="mt-1">
                                <input
                                    id="username"
                                    name="username"
                                    type="text"
                                    autoComplete="username"
                                    required
                                    value={formData.username}
                                    onChange={handleInputChange}
                                    className={`appearance-none block w-full px-3 py-2 border ${
                                        errors.username ? 'border-red-500' : 'border-gray-300'
                                    } rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-green-500 focus:border-green-500 sm:text-sm`}
                                />
                                {errors.username && (
                                    <p className="mt-2 text-sm text-red-600">{errors.username}</p>
                                )}
                            </div>
                        </div>

                        {/* 邮箱 */}
                        <div>
                            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                                邮箱 <span className="text-red-500">*</span>
                            </label>
                            <div className="mt-1">
                                <input
                                    id="email"
                                    name="email"
                                    type="email"
                                    autoComplete="email"
                                    required
                                    value={formData.email}
                                    onChange={handleInputChange}
                                    className={`appearance-none block w-full px-3 py-2 border ${
                                        errors.email ? 'border-red-500' : 'border-gray-300'
                                    } rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-green-500 focus:border-green-500 sm:text-sm`}
                                />
                                {errors.email && (
                                    <p className="mt-2 text-sm text-red-600">{errors.email}</p>
                                )}
                            </div>
                        </div>

                        {/* 密码 */}
                        <div>
                            <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                                密码 <span className="text-red-500">*</span>
                            </label>
                            <div className="mt-1">
                                <input
                                    id="password"
                                    name="password"
                                    type="password"
                                    autoComplete="new-password"
                                    required
                                    value={formData.password}
                                    onChange={handleInputChange}
                                    className={`appearance-none block w-full px-3 py-2 border ${
                                        errors.password ? 'border-red-500' : 'border-gray-300'
                                    } rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-green-500 focus:border-green-500 sm:text-sm`}
                                />
                                {errors.password && (
                                    <p className="mt-2 text-sm text-red-600">{errors.password}</p>
                                )}
                            </div>
                        </div>

                        {/* 确认密码 */}
                        <div>
                            <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700">
                                确认密码 <span className="text-red-500">*</span>
                            </label>
                            <div className="mt-1">
                                <input
                                    id="confirmPassword"
                                    name="confirmPassword"
                                    type="password"
                                    autoComplete="new-password"
                                    required
                                    value={formData.confirmPassword}
                                    onChange={handleInputChange}
                                    className={`appearance-none block w-full px-3 py-2 border ${
                                        errors.confirmPassword ? 'border-red-500' : 'border-gray-300'
                                    } rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-green-500 focus:border-green-500 sm:text-sm`}
                                />
                                {errors.confirmPassword && (
                                    <p className="mt-2 text-sm text-red-600">{errors.confirmPassword}</p>
                                )}
                            </div>
                        </div>

                        <div>
                            <button
                                type="submit"
                                disabled={loading}
                                className={`w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-green-600 hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 ${
                                    loading ? 'opacity-50 cursor-not-allowed' : ''
                                }`}
                            >
                                {loading ? '注册中...' : '注册'}
                            </button>
                        </div>
                    </form>

                    <div className="mt-6">
                        <div className="relative">
                            <div className="absolute inset-0 flex items-center">
                                <div className="w-full border-t border-gray-300"></div>
                            </div>
                            <div className="relative flex justify-center text-sm">
                                <span className="px-2 bg-white text-gray-500">已有账号?</span>
                            </div>
                        </div>

                        <div className="mt-6">
                            <button
                                onClick={() => navigate('/login')}
                                className="w-full flex justify-center py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500"
                            >
                                返回登录
                            </button>
                        </div>
                    </div>
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

export default Register;
