// src/components/system-management/AdminPermissions.tsx
import React, { useState } from 'react';

interface AdminPermission {
    role: string;
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

const AdminPermissions: React.FC = () => {
    const [permissions, setPermissions] = useState<AdminPermission[]>([
        {
            role: 'SuperAdministrator',
            login: true,
            announcement: true,
            helpArticles: true,
            createSuperAdmin: true,
            createAdmin: true,
            createReader: true,
            managePermissions: true,
            manageUserPermissions: true,
            resetAdminPassword: true,
            resetReaderPassword: true,
            payFines: true,
            borrowBook: true,
            returnBook: true,
            editReader: true,
            editPersonalInfo: true,
            editAdminInfo: true
        },
        {
            role: '超级管理员',
            login: true,
            announcement: true,
            helpArticles: true,
            createSuperAdmin: true,
            createAdmin: true,
            createReader: true,
            managePermissions: true,
            manageUserPermissions: true,
            resetAdminPassword: true,
            resetReaderPassword: true,
            payFines: true,
            borrowBook: true,
            returnBook: true,
            editReader: true,
            editPersonalInfo: true,
            editAdminInfo: true
        }
    ]);

    const [successMessage, setSuccessMessage] = useState('获取权限列表成功!');
    const [showMessage, setShowMessage] = useState(true);

    const handleTogglePermission = (index: number, field: keyof AdminPermission) => {
        if (typeof permissions[index][field] === 'boolean') {
            const newPermissions = [...permissions];
            // @ts-ignore - 我们已经确认这是布尔值类型
            newPermissions[index][field] = !newPermissions[index][field];
            setPermissions(newPermissions);
        }
    };

    const handleDelete = (index: number) => {
        const confirmDelete = window.confirm('确定要删除该角色吗？');
        if (confirmDelete) {
            const newPermissions = permissions.filter((_, idx) => idx !== index);
            setPermissions(newPermissions);
            setSuccessMessage('角色删除成功!');
            setShowMessage(true);

            // 3秒后隐藏消息
            setTimeout(() => {
                setShowMessage(false);
            }, 3000);
        }
    };

    const handleSave = () => {
        console.log('保存修改:', permissions);
        setSuccessMessage('权限设置保存成功!');
        setShowMessage(true);

        // 3秒后隐藏消息
        setTimeout(() => {
            setShowMessage(false);
        }, 3000);
    };

    const handleAddRole = () => {
        const newRole: AdminPermission = {
            role: '新角色',
            login: true,
            announcement: true,
            helpArticles: true,
            createSuperAdmin: false,
            createAdmin: false,
            createReader: true,
            managePermissions: false,
            manageUserPermissions: false,
            resetAdminPassword: false,
            resetReaderPassword: true,
            payFines: true,
            borrowBook: true,
            returnBook: true,
            editReader: true,
            editPersonalInfo: true,
            editAdminInfo: false
        };

        setPermissions([...permissions, newRole]);
        setSuccessMessage('添加新角色成功!');
        setShowMessage(true);

        // 3秒后隐藏消息
        setTimeout(() => {
            setShowMessage(false);
        }, 3000);
    };

    return (
        <div className="flex">
            {/* 左侧内容 */}
            <div className="flex-1 bg-white shadow-md rounded-lg p-6">
                <h2 className="text-2xl font-bold mb-4">管理员权限管理</h2>

                {showMessage && (
                    <div className="bg-green-100 text-green-800 px-4 py-2 rounded mb-6">
                        {successMessage}
                    </div>
                )}

                <div className="overflow-x-auto">
                    <table className="table-auto w-full border-collapse border border-gray-300 text-sm text-center">
                        <thead className="bg-gray-100">
                            <tr>
                                <th className="border border-gray-300 px-4 py-2">角色</th>
                                <th className="border border-gray-300 px-4 py-2">登录</th>
                                <th className="border border-gray-300 px-4 py-2">公告</th>
                                <th className="border border-gray-300 px-4 py-2">帮助文章</th>
                                <th className="border border-gray-300 px-4 py-2">创建超级管理员</th>
                                <th className="border border-gray-300 px-4 py-2">创建普通管理员</th>
                                <th className="border border-gray-300 px-4 py-2">创建读者</th>
                                <th className="border border-gray-300 px-4 py-2">管理权限</th>
                                <th className="border border-gray-300 px-4 py-2">管理用户权限</th>
                                <th className="border border-gray-300 px-4 py-2">重置管理员密码</th>
                                <th className="border border-gray-300 px-4 py-2">重置读者密码</th>
                                <th className="border border-gray-300 px-4 py-2">缴纳欠款</th>
                                <th className="border border-gray-300 px-4 py-2">借阅图书</th>
                                <th className="border border-gray-300 px-4 py-2">归还图书</th>
                                <th className="border border-gray-300 px-4 py-2">编辑读者</th>
                                <th className="border border-gray-300 px-4 py-2">编辑个人信息</th>
                                <th className="border border-gray-300 px-4 py-2">编辑管理员信息</th>
                                <th className="border border-gray-300 px-4 py-2">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            {permissions.map((permission, index) => (
                                <tr key={index}>
                                    <td className="border border-gray-300 px-4 py-2">{permission.role}</td>
                                    {Object.keys(permission).map((key) => {
                                        // 跳过角色名称
                                        if (key === 'role') return null;

                                        const permissionKey = key as keyof AdminPermission;
                                        return (
                                            <td
                                                key={key}
                                                className="border border-gray-300 px-4 py-2 cursor-pointer hover:bg-gray-100"
                                                onClick={() => handleTogglePermission(index, permissionKey)}
                                            >
                                                {permission[permissionKey] ? '启用' : '禁用'}
                                            </td>
                                        );
                                    })}
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
                        1. 点击表格中的"启用"/"禁用"可以直接切换权限状态<br />
                        2. 添加角色：点击"添加角色"按钮添加新的角色类型<br />
                        3. 删除角色：点击对应角色行的"删除"按钮删除角色<br />
                        4. 保存修改：所有修改完成后，点击"保存修改"按钮<br />
                        5. 临时保存：可以暂时保存当前修改<br />
                        6. 放弃修改：取消所有未保存的修改并刷新页面<br />
                        7. 超级管理员具有所有权限，请谨慎修改
                    </p>
                </div>
            </div>
        </div>
    );
};

export default AdminPermissions;
