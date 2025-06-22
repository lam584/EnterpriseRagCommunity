//my-vite-app/src/components/reader-management/AddUser.tsx
import React, { useState, useEffect } from 'react';
import { createReader } from '../../services/UserService_1.ts';
import { fetchReaderPermissions, ReaderPermissionDTO } from '../../services/UserRoleService_1.ts';

const AddUser: React.FC = () => {
    // 表单状态
    const [formData, setFormData] = useState({
        account: '',
        password: '',
        confirmPassword: '',
        phone: '',
        email: '',
        sex: '男',
        permissionId: 0
    });

    // 权限列表
    const [permissions, setPermissions] = useState<ReaderPermissionDTO[]>([]);

    // UI状态
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState({ type: '', text: '' });
    const [errors, setErrors] = useState<Record<string, string>>({});

    // 加载权限数据
    useEffect(() => {
        const loadPermissions = async () => {
            try {
                const permissionsData = await fetchReaderPermissions();
                setPermissions(permissionsData);
                // 默认选中第一个权限
                if (permissionsData.length > 0) {
                    setFormData(prev => ({ ...prev, permissionId: permissionsData[0].id }));
                }
            } catch (err) {
                setMessage({ type: 'error', text: '加载权限数据失败，请刷新页面重试' });
            }
        };
        loadPermissions();
    }, []);

    // 表单验证
    const validateForm = () => {
        const newErrors: Record<string, string> = {};

        if (!formData.account.trim()) newErrors.account = '账号不能为空';
        if (!formData.password.trim()) newErrors.password = '密码不能为空';
        if (formData.password !== formData.confirmPassword)
            newErrors.confirmPassword = '两次输入的密码不一致';

        if (!formData.phone.trim()) newErrors.phone = '电话号码不能为空';
        else if (!/^\d{11}$/.test(formData.phone))
            newErrors.phone = '电话号码必须为11位数字';

        if (!formData.email.trim()) newErrors.email = '邮箱不能为空';
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email))
            newErrors.email = '邮箱格式不正确';

        if (formData.permissionId === 0) newErrors.permissionId = '请选择用户权限';

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // 处理输入变化
    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;

        setFormData(prev => ({ ...prev, [name]: value }));

        // 清除该字段的错误
        if (errors[name]) {
            setErrors(prev => {
                const newErrors = { ...prev };
                delete newErrors[name];
                return newErrors;
            });
        }
    };

    // 清空表单
    const clearForm = () => {
        setFormData({
            account: '',
            password: '',
            confirmPassword: '',
            phone: '',
            email: '',
            sex: '男',
            permissionId: permissions.length > 0 ? permissions[0].id : 0
        });
        setErrors({});
        // 不清除消息，保留消息的显示
    };

    // 表单提交
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateForm()) return;

        setLoading(true);
        setMessage({ type: '', text: '' });

        try {
            const readerData = {
                account: formData.account,
                password: formData.password,
                phone: formData.phone,
                email: formData.email,
                sex: formData.sex,
                permission: { id: formData.permissionId }
            };

            const newReader = await createReader(readerData);


            // 更详细的成功消息，包含用户信息但不包含密码
            setMessage({
                type: 'success',
                text: `用户 "${newReader.account}" 添加成功！权限组: ${newReader.permission?.roles || ''}`
            });

            clearForm();
        } catch (err: any) {
            console.error('添加用户时出错:', err);
            setMessage({
                type: 'error',
                text: err.message || '添加用户失败，请检查填写内容或稍后重试'
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex">
            {/* 左侧表单 */}
            <div className="w-3/4 bg-white shadow-md rounded-md p-6">
                <h2 className="text-xl font-bold mb-4">添加新用户</h2>

                {message.text && (
                    <div
                        className={`p-4 mb-4 rounded ${
                            message.type === 'success'
                                ? 'bg-green-100 text-green-800'
                                : 'bg-red-100 text-red-800'
                        }`}
                    >
                        {message.text}
                    </div>
                )}

                <form className="space-y-4" onSubmit={handleSubmit}>
                    <div className="grid grid-cols-2 gap-4">
                        {/* 账号 */}
                        <div>
                            <label className="block mb-1">
                                账号 <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                name="account"
                                value={formData.account}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.account ? 'border-red-500' : 'border-gray-300'
                                }`}
                                placeholder="请输入账号"
                            />
                            {errors.account && (
                                <p className="text-red-500 text-sm mt-1">{errors.account}</p>
                            )}
                        </div>

                        {/* 密码 */}
                        <div>
                            <label className="block mb-1">
                                密码 <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="password"
                                name="password"
                                value={formData.password}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.password ? 'border-red-500' : 'border-gray-300'
                                }`}
                                placeholder="请输入密码"
                            />
                            {errors.password && (
                                <p className="text-red-500 text-sm mt-1">{errors.password}</p>
                            )}
                        </div>

                        {/* 确认密码 */}
                        <div>
                            <label className="block mb-1">
                                确认密码 <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="password"
                                name="confirmPassword"
                                value={formData.confirmPassword}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.confirmPassword ? 'border-red-500' : 'border-gray-300'
                                }`}
                                placeholder="请再次输入密码"
                            />
                            {errors.confirmPassword && (
                                <p className="text-red-500 text-sm mt-1">{errors.confirmPassword}</p>
                            )}
                        </div>

                        {/* 性别 */}
                        <div>
                            <label className="block mb-1">性别</label>
                            <select
                                name="sex"
                                value={formData.sex}
                                onChange={handleChange}
                                className="w-full border border-gray-300 p-2 rounded"
                            >
                                <option value="男">男</option>
                                <option value="女">女</option>
                            </select>
                        </div>

                        {/* 电话号码 */}
                        <div>
                            <label className="block mb-1">
                                电话号码 <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                name="phone"
                                value={formData.phone}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.phone ? 'border-red-500' : 'border-gray-300'
                                }`}
                                placeholder="请输入电话号码"
                            />
                            {errors.phone && (
                                <p className="text-red-500 text-sm mt-1">{errors.phone}</p>
                            )}
                        </div>

                        {/* 邮箱 */}
                        <div>
                            <label className="block mb-1">
                                邮箱 <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="email"
                                name="email"
                                value={formData.email}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.email ? 'border-red-500' : 'border-gray-300'
                                }`}
                                placeholder="请输入邮箱"
                            />
                            {errors.email && (
                                <p className="text-red-500 text-sm mt-1">{errors.email}</p>
                            )}
                        </div>

                        {/* 用户权限 */}
                        <div>
                            <label className="block mb-1">
                                用户权限 <span className="text-red-500">*</span>
                            </label>
                            <select
                                name="permissionId"
                                value={formData.permissionId}
                                onChange={handleChange}
                                className={`w-full border p-2 rounded ${
                                    errors.permissionId ? 'border-red-500' : 'border-gray-300'
                                }`}
                            >
                                <option value={0}>请选择权限</option>
                                {permissions.map(perm => (
                                    <option key={perm.id} value={perm.id}>
                                        {perm.roles || `权限ID: ${perm.id}`}  {/* 使用 roles */}
                                    </option>
                                ))}
                            </select>
                            {errors.permissionId && (
                                <p className="text-red-500 text-sm mt-1">{errors.permissionId}</p>
                            )}
                        </div>
                    </div>

                    <div className="flex space-x-4">
                        <button
                            type="submit"
                            disabled={loading}
                            className={`bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600 ${
                                loading ? 'opacity-50 cursor-not-allowed' : ''
                            }`}
                        >
                            {loading ? '处理中...' : '添加用户'}
                        </button>
                        <button
                            type="button"
                            onClick={clearForm}
                            className="bg-gray-300 text-gray-800 px-6 py-2 rounded hover:bg-gray-400"
                        >
                            清空表单
                        </button>
                    </div>
                </form>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <ul className="list-disc pl-5 space-y-2 text-gray-700">
                        <li>
                            所有带<span className="text-red-500">*</span>的字段为必填项
                        </li>
                        <li>账号必须唯一，不能与已有用户重复</li>
                        <li>电话号码必须为11位数字</li>
                        <li>邮箱格式必须正确</li>
                        <li>添加成功后可在用户列表查看</li>
                    </ul>
                </div>
            </div>
        </div>
    );
};

export default AddUser;
