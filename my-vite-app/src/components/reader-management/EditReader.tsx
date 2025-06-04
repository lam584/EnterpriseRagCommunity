// src/components/reader-management/EditReader.tsx
import React, { useState, useEffect } from 'react';
import { fetchReaderById, updateReader, ReaderDTO } from '../../services/readerService';
import { fetchReaderPermissions, ReaderPermissionDTO } from '../../services/readerPermissionService';

const EditReader: React.FC = () => {
    const [readerId, setReaderId] = useState<string>('');
    const [permissions, setPermissions] = useState<ReaderPermissionDTO[]>([]);
    const [readerData, setReaderData] = useState<ReaderDTO | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);
    const [changePassword, setChangePassword] = useState<boolean>(false);
    const [newPassword, setNewPassword] = useState<string>('');
    const [errors, setErrors] = useState<Record<string, string>>({});

    // 获取所有权限列表
    useEffect(() => {
        fetchReaderPermissions()
            .then(data => setPermissions(data))
            .catch(error => {
                console.error("获取权限列表失败:", error);
                setMessage({ type: 'error', text: '无法获取权限列表，请刷新页面重试' });
            });
    }, []);

    // 搜索读者信息
    const handleSearch = async () => {
        if (!readerId || isNaN(Number(readerId))) {
            setErrors({ readerId: '请输入有效的读者ID' });
            return;
        }

        setLoading(true);
        setMessage(null);
        setErrors({});
        setChangePassword(false);
        setNewPassword('');

        try {
            const id = Number(readerId);
            const reader = await fetchReaderById(id);
            setReaderData(reader);
        } catch (error) {
            console.error("获取读者信息失败:", error);
            setMessage({ type: 'error', text: '获取读者信息失败，请确认ID是否正确' });
            setReaderData(null);
        } finally {
            setLoading(false);
        }
    };

    // 处理表单字段变化
    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;

        if (!readerData) return;

        if (name === 'permission') {
            setReaderData({
                ...readerData,
                permission: { id: Number(value) }
            });
        } else if (name === 'isActive') {
            setReaderData({
                ...readerData,
                isActive: value === 'true'
            });
        } else {
            setReaderData({
                ...readerData,
                [name]: value
            });
        }

        // 清除相关字段的错误
        if (errors[name]) {
            setErrors(prev => {
                const newErrors = { ...prev };
                delete newErrors[name];
                return newErrors;
            });
        }
    };

    // 表单验证
    const validate = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!readerData) {
            newErrors.general = '没有读者数据可更新';
            setErrors(newErrors);
            return false;
        }

        if (!readerData.account) newErrors.account = '账号不能为空';
        if (!readerData.phone) newErrors.phone = '手机号不能为空';
        if (!readerData.email) newErrors.email = '邮箱不能为空';
        if (changePassword && !newPassword) newErrors.password = '新密码不能为空';

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // 提交表单更新读者信息
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validate() || !readerData || !readerData.id) return;

        setLoading(true);
        setMessage(null);

        try {
            const updateData: ReaderDTO = { ...readerData };

            // 如果修改密码，添加密码字段
            if (changePassword && newPassword) {
                updateData.password = newPassword;
            }

            // 调用更新接口
            await updateReader(readerData.id, updateData);

            // 刷新读者数据
            const refreshedData = await fetchReaderById(readerData.id);
            setReaderData(refreshedData);

            setMessage({ type: 'success', text: '读者信息更新成功！' });
            setChangePassword(false);
            setNewPassword('');
        } catch (error) {
            console.error("更新读者信息失败:", error);
            setMessage({ type: 'error', text: '更新失败，请检查数据格式或网络连接' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex">
            {/* 左侧表单 */}
            <div className="flex-1 bg-white shadow-md rounded-md p-6">
                <h2 className="text-xl font-bold mb-4">编辑读者信息</h2>

                {message && (
                    <div className={`p-4 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                        {message.text}
                    </div>
                )}

                <div className="mb-4">
                    <label htmlFor="searchReaderId" className="block text-gray-700 font-medium">输入读者ID</label>
                    <div className="flex mt-2">
                        <input
                            type="text"
                            id="searchReaderId"
                            className={`flex-1 p-2 border ${errors.readerId ? 'border-red-500' : 'border-gray-300'} rounded-l-md`}
                            value={readerId}
                            onChange={(e) => setReaderId(e.target.value)}
                        />
                        <button
                            onClick={handleSearch}
                            disabled={loading}
                            className={`bg-blue-500 text-white px-4 py-2 rounded-r-md hover:bg-blue-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                        >
                            {loading ? '搜索中...' : '搜索读者信息'}
                        </button>
                    </div>
                    {errors.readerId && <p className="text-red-500 text-sm mt-1">{errors.readerId}</p>}
                </div>

                {readerData && (
                    <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label htmlFor="account" className="block text-gray-700 font-medium">账号</label>
                                <input
                                    type="text"
                                    id="account"
                                    name="account"
                                    value={readerData.account || ''}
                                    onChange={handleChange}
                                    className={`w-full mt-2 p-2 border ${errors.account ? 'border-red-500' : 'border-gray-300'} rounded-md`}
                                />
                                {errors.account && <p className="text-red-500 text-sm mt-1">{errors.account}</p>}
                            </div>
                            <div>
                                <label htmlFor="phone" className="block text-gray-700 font-medium">电话号码</label>
                                <input
                                    type="text"
                                    id="phone"
                                    name="phone"
                                    value={readerData.phone || ''}
                                    onChange={handleChange}
                                    className={`w-full mt-2 p-2 border ${errors.phone ? 'border-red-500' : 'border-gray-300'} rounded-md`}
                                />
                                {errors.phone && <p className="text-red-500 text-sm mt-1">{errors.phone}</p>}
                            </div>
                            <div>
                                <label htmlFor="email" className="block text-gray-700 font-medium">邮箱地址</label>
                                <input
                                    type="email"
                                    id="email"
                                    name="email"
                                    value={readerData.email || ''}
                                    onChange={handleChange}
                                    className={`w-full mt-2 p-2 border ${errors.email ? 'border-red-500' : 'border-gray-300'} rounded-md`}
                                />
                                {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email}</p>}
                            </div>

                            {/* 性别选择 */}
                            <div>
                                <label htmlFor="sex" className="block text-gray-700 font-medium">性别</label>
                                <select
                                    id="sex"
                                    name="sex"
                                    value={readerData.sex || '男'}
                                    onChange={handleChange}
                                    className="w-full mt-2 p-2 border border-gray-300 rounded-md"
                                >
                                    <option value="男">男</option>
                                    <option value="女">女</option>
                                    <option value="其他">其他</option>
                                </select>
                            </div>

                            {/* 权限选择器 */}
                            <div>
                                <label htmlFor="permission" className="block text-gray-700 font-medium">权限角色</label>
                                <select
                                    id="permission"
                                    name="permission"
                                    value={readerData.permission?.id || ''}
                                    onChange={handleChange}
                                    className="w-full mt-2 p-2 border border-gray-300 rounded-md"
                                >
                                    <option value="">选择权限角色...</option>
                                    {permissions.map(perm => (
                                        <option key={perm.id} value={perm.id}>
                                            {perm.roles || perm.description || `权限ID: ${perm.id}`}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* 状态选择 */}
                            <div>
                                <label htmlFor="isActive" className="block text-gray-700 font-medium">状态</label>
                                <select
                                    id="isActive"
                                    name="isActive"
                                    value={readerData.isActive?.toString() || 'true'}
                                    onChange={handleChange}
                                    className="w-full mt-2 p-2 border border-gray-300 rounded-md"
                                >
                                    <option value="true">激活</option>
                                    <option value="false">禁用</option>
                                </select>
                            </div>

                            {/* 修改密码区域 */}
                            <div className="col-span-2">
                                <div className="flex items-center mb-2">
                                    <input
                                        type="checkbox"
                                        id="changePassword"
                                        checked={changePassword}
                                        onChange={(e) => setChangePassword(e.target.checked)}
                                        className="mr-2"
                                    />
                                    <label htmlFor="changePassword" className="text-gray-700">修改密码</label>
                                </div>

                                {changePassword && (
                                    <div>
                                        <input
                                            type="password"
                                            id="newPassword"
                                            placeholder="输入新密码"
                                            value={newPassword}
                                            onChange={(e) => setNewPassword(e.target.value)}
                                            className={`w-full p-2 border ${errors.password ? 'border-red-500' : 'border-gray-300'} rounded-md`}
                                        />
                                        {errors.password && <p className="text-red-500 text-sm mt-1">{errors.password}</p>}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 错误提示（如果有） */}
                        {errors.general && (
                            <div className="bg-red-100 text-red-800 p-3 rounded">
                                {errors.general}
                            </div>
                        )}

                        {/* 提交按钮 */}
                        <div className="flex justify-end mt-6">
                            <button
                                type="submit"
                                disabled={loading}
                                className={`bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                            >
                                {loading ? '更新中...' : '保存修改'}
                            </button>
                        </div>
                    </form>
                )}
            </div>

            {/* 右侧指导面板 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h3 className="text-lg font-semibold mb-3">使用指南</h3>
                    <ul className="list-disc pl-5 space-y-2 text-gray-700">
                        <li>输入读者ID并点击"搜索读者信息"按钮。</li>
                        <li>修改需要更新的字段。</li>
                        <li>账号、手机号和邮箱为必填字段。</li>
                        <li>如需修改密码，请勾选"修改密码"并输入新密码。</li>
                        <li>点击"保存修改"按钮提交更改。</li>
                    </ul>

                    <div className="mt-4 p-3 bg-blue-50 border border-blue-100 rounded-md text-blue-800">
                        <p className="text-sm"><strong>提示：</strong>修改权限时，请确保选择合适的读者权限角色。</p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default EditReader;
