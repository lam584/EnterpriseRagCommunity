// src/services/UserRoleService_3.ts

/**
 * DTO：读者角色权限
 */
export interface ReaderPermissionDTO {
    id: number;
    roles: string;
}

// mn的角色列表
const mockPermissions: ReaderPermissionDTO[] = [
    { id: 1, roles: '管理员' },
    { id: 2, roles: '普通用户' },
    { id: 3, roles: '访客' },
];

/**
 * mn获取角色列表
 */
export async function fetchReaderPermissions(): Promise<ReaderPermissionDTO[]> {
    return new Promise(resolve => {
        setTimeout(() => {
            resolve(mockPermissions);
        }, 300);
    });
}