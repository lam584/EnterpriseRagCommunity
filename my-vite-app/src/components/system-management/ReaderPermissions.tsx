// src/components/system-management/ReaderPermissions.tsx
import React, { useState, useEffect } from 'react';
import {
    fetchReaderPermissions,
    createReaderPermission,
    updateReaderPermission,
    deleteReaderPermission,
    ReaderPermissionDTO
} from '../../services/UserPermissionService.ts';

interface RolePermission {
    id: number;
    role: string;
    login: boolean;
    appointment: boolean;
    viewAnnouncements: boolean;
    viewHelpArticles: boolean;
    resetPassword: boolean;
    borrowReturn: boolean;
    editAccountInfo: boolean;
    notes: string;
}

const ReaderPermissions: React.FC = () => {
    const [permissions, setPermissions] = useState<RolePermission[]>([]);
    const [loading, setLoading] = useState(false);
    const [successMessage, setSuccessMessage] = useState('');
    const [showMessage, setShowMessage] = useState(false);
    const [error, setError] = useState('');

    // 获取权限数据
    useEffect(() => {
        const loadPermissions = async () => {
            try {
                setLoading(true);
                const data = await fetchReaderPermissions();

                // 转换API数据格式为组件内部使用的格式
                const mappedPermissions = data.map(item => ({
                    id: item.id,
                    role: item.roles ?? '未知角色', // 防止 undefined
                    login: item.canLogin || false,
                    appointment: item.canReserve || false,
                    viewAnnouncements: item.canViewAnnouncement || false,
                    viewHelpArticles: item.canViewHelpArticles || false,
                    resetPassword: item.canResetOwnPassword || false,
                    borrowReturn: item.canBorrowReturnBooks || false,
                    editAccountInfo: item.allowEditProfile || false,
                    notes: item.description || ''
                }));

                setPermissions(mappedPermissions);
                setSuccessMessage('获取权限列表成功!');
                setShowMessage(true);

                // 3秒后隐藏消息
                setTimeout(() => {
                    setShowMessage(false);
                }, 3000);
            } catch (err) {
                console.error('获取权限列表失败:', err);
                setError('获取权限列表失败，请刷新页面重试');
                setShowMessage(true);
            } finally {
                setLoading(false);
            }
        };

        loadPermissions();
    }, []);

    const handleTogglePermission = async (index: number, field: keyof RolePermission) => {
        if (typeof permissions[index][field] === 'boolean') {
            try {
                const newPermissions = [...permissions];
                // @ts-ignore - 我们已经确认这是布尔值类型
                newPermissions[index][field] = !newPermissions[index][field];
                setPermissions(newPermissions);

                // 后端API需要的数据结构
                const permissionToUpdate = mapToApiFormat(newPermissions[index]);

                await updateReaderPermission(newPermissions[index].id, permissionToUpdate);

                setSuccessMessage(`权限"${field}"更新成功!`);
                setShowMessage(true);
                setTimeout(() => setShowMessage(false), 3000);

            } catch (err) {
                console.error('更新权限失败:', err);
                setError('更新权限失败，请重试');
                setShowMessage(true);
            }
        }
    };

    const handleDelete = async (index: number) => {
        const confirmDelete = window.confirm('确定要删除该角色吗？');
        if (confirmDelete) {
            try {
                const permissionId = permissions[index].id;
                await deleteReaderPermission(permissionId);

                const newPermissions = permissions.filter((_, idx) => idx !== index);
                setPermissions(newPermissions);
                setSuccessMessage('角色删除成功!');
                setShowMessage(true);

                // 3秒后隐藏消息
                setTimeout(() => {
                    setShowMessage(false);
                }, 3000);
            } catch (err) {
                console.error('删除权限失败:', err);
                setError('删除权限失败，该权限可能正在被使用');
                setShowMessage(true);
            }
        }
    };

    const handleSave = async () => {
        try {
            setLoading(true);

            // 批量保存所有权限
            for (const permission of permissions) {
                const apiData = mapToApiFormat(permission);
                await updateReaderPermission(permission.id, apiData);
            }

            setSuccessMessage('权限设置保存成功!');
            setShowMessage(true);

            // 3秒后隐藏消息
            setTimeout(() => {
                setShowMessage(false);
            }, 3000);
        } catch (err) {
            console.error('保存权限失败:', err);
            setError('保存权限失败，请重试');
            setShowMessage(true);
        } finally {
            setLoading(false);
        }
    };

    const handleAddRole = async () => {
        try {
            const newRole: Omit<RolePermission, 'id'> = {
                role: '新角色',
                login: true,
                appointment: true,
                viewAnnouncements: true,
                viewHelpArticles: true,
                resetPassword: true,
                borrowReturn: true,
                editAccountInfo: true,
                notes: '新角色描述'
            };

            // 转换为API需要的格式
            const apiData = {
                name: newRole.role,
                description: newRole.notes,
                canLogin: newRole.login,
                canReserve: newRole.appointment,
                canViewAnnouncement: newRole.viewAnnouncements,
                canViewHelpArticles: newRole.viewHelpArticles,
                canResetOwnPassword: newRole.resetPassword,
                canBorrowReturnBooks: newRole.borrowReturn,
                allowEditProfile: newRole.editAccountInfo
            };

            const response = await createReaderPermission(apiData);

            const createdRole = {
                id: response.id,
                role: response.roles || '新角色',
                login: response.canLogin || true,
                appointment: response.canReserve || true,
                viewAnnouncements: response.canViewAnnouncement || true,
                viewHelpArticles: response.canViewHelpArticles || true,
                resetPassword: response.canResetOwnPassword || true,
                borrowReturn: response.canBorrowReturnBooks || true,
                editAccountInfo: response.allowEditProfile || true,
                notes: response.description || '新角色描述'
            };

            setPermissions([...permissions, createdRole]);
            setSuccessMessage('添加新角色成功!');
            setShowMessage(true);

            // 3秒后隐藏消息
            setTimeout(() => {
                setShowMessage(false);
            }, 3000);
        } catch (err) {
            console.error('添加角色失败:', err);
            setError('添加新角色失败，请重试');
            setShowMessage(true);
        }
    };

    // 将组件数据格式转换为API格式
    const mapToApiFormat = (permission: RolePermission): ReaderPermissionDTO => {
        return {
            id: permission.id,
            roles: permission.role,
            description: permission.notes,
            canLogin: permission.login,
            canReserve: permission.appointment,
            canViewAnnouncement: permission.viewAnnouncements,
            canViewHelpArticles: permission.viewHelpArticles,
            canResetOwnPassword: permission.resetPassword,
            canBorrowReturnBooks: permission.borrowReturn,
            allowEditProfile: permission.editAccountInfo
        };
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-lg p-6">
                <h2 className="text-2xl font-bold mb-4">读者权限管理</h2>

                {loading && (
                    <div className="bg-blue-100 text-blue-800 px-4 py-2 rounded mb-6">
                        加载中...请稍候
                    </div>
                )}

                {showMessage && (
                    <div className={`${error ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'} px-4 py-2 rounded mb-6`}>
                        {error || successMessage}
                    </div>
                )}

                <div className="overflow-x-auto">
                    <table className="w-full border-collapse border border-gray-300 text-center">
                        <thead>
                            <tr className="bg-gray-100">
                                <th className="border border-gray-300 px-4 py-2">角色</th>
                                <th className="border border-gray-300 px-4 py-2">登录</th>
                                <th className="border border-gray-300 px-4 py-2">预约</th>
                                <th className="border border-gray-300 px-4 py-2">查看公告</th>
                                <th className="border border-gray-300 px-4 py-2">查看帮助文章</th>
                                <th className="border border-gray-300 px-4 py-2">重置密码</th>
                                <th className="border border-gray-300 px-4 py-2">借阅、归还图书</th>
                                <th className="border border-gray-300 px-4 py-2">修改账号信息</th>
                                <th className="border border-gray-300 px-4 py-2">备注</th>
                                <th className="border border-gray-300 px-4 py-2">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            {permissions.map((permission, index) => (
                                <tr key={index}>
                                    <td className="border border-gray-300 px-4 py-2">{permission.role}</td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'login')}
                                    >
                                        {permission.login ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'appointment')}
                                    >
                                        {permission.appointment ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'viewAnnouncements')}
                                    >
                                        {permission.viewAnnouncements ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'viewHelpArticles')}
                                    >
                                        {permission.viewHelpArticles ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'resetPassword')}
                                    >
                                        {permission.resetPassword ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'borrowReturn')}
                                    >
                                        {permission.borrowReturn ? '启用' : '禁用'}
                                    </td>
                                    <td
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(index, 'editAccountInfo')}
                                    >
                                        {permission.editAccountInfo ? '启用' : '禁用'}
                                    </td>
                                    <td className="border border-gray-300 px-4 py-2">{permission.notes}</td>
                                    <td className="border border-gray-300 px-4 py-2">
                                        <button
                                            className="bg-red-500 text-white px-4 py-1 rounded hover:bg-red-600"
                                            onClick={() => handleDelete(index)}
                                        >
                                            删除
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div className="flex justify-end space-x-4 mt-6">
                    <button
                        className="bg-yellow-500 text-white px-6 py-2 rounded hover:bg-yellow-600"
                        onClick={() => window.location.reload()}
                    >
                        放弃修改
                    </button>
                    <button
                        className="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600"
                        onClick={handleSave}
                        disabled={loading}
                    >
                        {loading ? '保存中...' : '保存修改'}
                    </button>
                    <button
                        className="bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600"
                        onClick={handleAddRole}
                        disabled={loading}
                    >
                        {loading ? '添加中...' : '添加角色'}
                    </button>
                </div>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">
                        1. 点击表格中的"启用"/"禁用"可以直接切换权限状态<br />
                        2. 添加角色：点击"添加角色"按钮添加新的角色类型<br />
                        3. 删除角色：点击对应角色行的"删除"按钮删除角色<br />
                        4. 保存修改：所有修改完成后，点击"保存修改"按钮<br />
                        5. 放弃修改：取消所有未保存的修改并刷新页面
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ReaderPermissions;
