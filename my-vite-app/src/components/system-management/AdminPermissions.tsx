// src/components/system-management/AdminPermissions.tsx
import React, { useEffect, useState } from 'react';
import {
    AdminPermissionDTO,
    createAdminPermission,
    CreateAdminPermissionData,
    deleteAdminPermission,
    fetchAdminPermissions,
    updateAdminPermission
} from '../../services/adminPermissionService';

/** 前端组件使用的权限对象 */
interface AdminPermission {
    role: string;
    id: number;
    description: string;
    allowEditUserProfile: boolean;

    // 以下都是布尔字段
    login: boolean;
    announcement: boolean;
    helpArticles: boolean;
    createSuperAdmin: boolean;
    createAdmin: boolean;
    createReader: boolean;
    managePermissions: boolean;
    manageUserPermissions: boolean;
    resetAdminPassword: boolean;
    resetReaderPassword: boolean;
    payFines: boolean;
    borrowBook: boolean;
    returnBook: boolean;
    editReader: boolean;
    editPersonalInfo: boolean;
    editAdminInfo: boolean;
}

/** 提取出所有值类型为 boolean 的属性名 */
type BooleanKeys = {
    [K in keyof AdminPermission]: AdminPermission[K] extends boolean ? K : never
}[keyof AdminPermission];

/** 明确列出需要渲染和切换的布尔字段顺序 */
const booleanFields: BooleanKeys[] = [
    'login',
    'announcement',
    'helpArticles',
    'createSuperAdmin',
    'createAdmin',
    'createReader',
    'managePermissions',
    'manageUserPermissions',
    'resetAdminPassword',
    'resetReaderPassword',
    'payFines',
    'borrowBook',
    'returnBook',
    'editReader',
    'editPersonalInfo',
    'editAdminInfo'
];

const AdminPermissions: React.FC = () => {
    const [permissions, setPermissions] = useState<AdminPermission[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const [showMessage, setShowMessage] = useState<boolean>(false);

    useEffect(() => {
        const loadPermissions = async () => {
            try {
                setLoading(true);
                const data = await fetchAdminPermissions();
                const mapped = data.map(item => mapToAdminPermission(item));
                setPermissions(mapped);

                setSuccessMessage('获取权限列表成功!');
                setShowMessage(true);
                setTimeout(() => setShowMessage(false), 3000);
            } catch (err) {
                console.error(err);
                setError('获取权限列表失败，请稍后再试');
                setShowMessage(true);
            } finally {
                setLoading(false);
            }
        };

        loadPermissions();
    }, []);

    /** 后端 DTO -> 前端 AdminPermission */
    const mapToAdminPermission = (dto: AdminPermissionDTO): AdminPermission => {
        const isAdmin = dto.roles.toLowerCase().includes('admin');
        const isSuper = dto.roles.toLowerCase().includes('super');
        return {
            id: dto.id,
            role: dto.roles,
            description: dto.description,
            allowEditUserProfile: dto.allowEditUserProfile,
            login: true,
            announcement: isAdmin || isSuper,
            helpArticles: isAdmin || isSuper,
            createSuperAdmin: isSuper,
            createAdmin: isSuper,
            createReader: isAdmin || isSuper,
            managePermissions: isSuper,
            manageUserPermissions: isSuper,
            resetAdminPassword: isSuper,
            resetReaderPassword: isAdmin || isSuper,
            payFines: isAdmin || isSuper,
            borrowBook: true,
            returnBook: true,
            editReader: isAdmin || isSuper,
            editPersonalInfo: true,
            editAdminInfo: isSuper || dto.allowEditUserProfile
        };
    };

    /** 前端 AdminPermission -> 后端 CreateAdminPermissionData */
    const mapToAdminPermissionDTO = (
        permission: AdminPermission
    ): CreateAdminPermissionData => ({
        name: permission.role,
        description: permission.description,
        allowEditUserProfile: permission.allowEditUserProfile
    });

    /** 切换布尔字段的值 */
    const handleTogglePermission = (index: number, field: BooleanKeys) => {
        const newPermissions = [...permissions];
        newPermissions[index][field] = !newPermissions[index][field];
        setPermissions(newPermissions);
    };

    const handleDelete = async (index: number) => {
        if (!window.confirm('确定要删除该角色吗？')) return;
        try {
            const toDelete = permissions[index];
            await deleteAdminPermission(toDelete.id);
            const filtered = permissions.filter((_, i) => i !== index);
            setPermissions(filtered);

            setSuccessMessage('角色删除成功!');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        } catch (err) {
            console.error(err);
            setError('删除角色失败，请稍后再试');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        }
    };

    const handleSave = async () => {
        try {
            for (const perm of permissions) {
                await updateAdminPermission(perm.id, mapToAdminPermissionDTO(perm));
            }
            setSuccessMessage('权限设置保存成功!');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        } catch (err) {
            console.error(err);
            setError('保存权限设置失败，请稍后再试');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        }
    };

    const handleAddRole = async () => {
        try {
            const newData: CreateAdminPermissionData = {
                name: '新角色',
                description: '新添加的角色',
                allowEditUserProfile: true
            };
            const created = await createAdminPermission(newData);
            setPermissions([...permissions, mapToAdminPermission(created)]);
            setSuccessMessage('添加新角色成功!');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        } catch (err) {
            console.error(err);
            setError('添加新角色失败，请稍后再试');
            setShowMessage(true);
            setTimeout(() => setShowMessage(false), 3000);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center items-center h-64">
                <p className="text-lg">加载中...</p>
            </div>
        );
    }

    if (error && permissions.length === 0) {
        return (
            <div className="flex justify-center items-center h-64">
                <p className="text-red-500">{error}</p>
            </div>
        );
    }

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-lg p-6">
                <h2 className="text-2xl font-bold mb-4">管理员权限管理</h2>

                {showMessage && (
                    <div
                        className={`px-4 py-2 rounded mb-6 ${
                            error ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'
                        }`}
                    >
                        {error || successMessage}
                    </div>
                )}

                <div className="overflow-x-auto">
                    <table className="table-auto w-full border-collapse border border-gray-300 text-sm text-center">
                        <thead className="bg-gray-100">
                        <tr>
                            <th className="border border-gray-300 px-4 py-2">角色</th>
                            {booleanFields.map((field) => (
                                <th key={field} className="border border-gray-300 px-4 py-2">
                                    {field}
                                </th>
                            ))}
                            <th className="border border-gray-300 px-4 py-2">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        {permissions.map((perm, idx) => (
                            <tr key={perm.id}>
                                <td className="border border-gray-300 px-4 py-2">{perm.role}</td>
                                {booleanFields.map((field) => (
                                    <td
                                        key={field}
                                        className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                        onClick={() => handleTogglePermission(idx, field)}
                                    >
                                        {perm[field] ? '启用' : '禁用'}
                                    </td>
                                ))}
                                <td className="border border-gray-300 px-4 py-2">
                                    <button
                                        className="bg-red-500 text-white px-4 py-1 rounded hover:bg-red-600"
                                        onClick={() => handleDelete(idx)}
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
                        className="bg-gray-500 text-white px-6 py-2 rounded hover:bg-gray-600"
                        onClick={() => console.log('临时保存:', permissions)}
                    >
                        临时保存
                    </button>
                    <button
                        className="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600"
                        onClick={handleSave}
                    >
                        保存修改
                    </button>
                    <button
                        className="bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600"
                        onClick={handleAddRole}
                    >
                        添加角色
                    </button>
                </div>
            </div>

            {/* 右侧说明 */}
            <div className="w-1/4 ml-6">
                <div className="bg-white shadow-md rounded-md p-6">
                    <h2 className="text-xl font-bold mb-4">操作指南</h2>
                    <p className="text-gray-700">
                        1. 点击表格中的"启用"/"禁用"可以直接切换权限状态
                        <br />
                        2. 添加角色：点击"添加角色"按钮添加新的角色类型
                        <br />
                        3. 删除角色：点击对应角色行的"删除"按钮删除角色
                        <br />
                        4. 保存修改：所有修改完成后，点击"保存修改"按钮
                        <br />
                        5. 临时保存：可以暂时保存当前修改
                        <br />
                        6. 放弃修改：取消所有未保存的修改并刷新页面
                        <br />
                        7. 超级管理员具有所有权限，请谨慎修改
                    </p>
                </div>
            </div>
        </div>
    );
};

export default AdminPermissions;