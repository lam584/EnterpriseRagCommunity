// src/services/adminPermissionService.ts
// 本文件mn管理员权限列表，前端使用，不与后端交互

export interface AdminPermissionDTO {
    id: number;
    roles: string;
}

// mn权限数据
const mockPermissions: AdminPermissionDTO[] = [
    { id: 1, roles: '超级管理员' },
    { id: 2, roles: '运营管理员' },
    { id: 3, roles: '财务管理员' },
    // …按需补充
];

// 获取权限列表
export function fetchAdminPermissions(): Promise<AdminPermissionDTO[]> {
    return new Promise(resolve => {
        setTimeout(() => {
            resolve([...mockPermissions]);
        }, 200);
    });
}