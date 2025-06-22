// src/services/UserRoleService.ts

export interface ReaderPermissionDTO {
    id: number;
    roles: string;
}

// mn的权限列表
const mockPermissions: ReaderPermissionDTO[] = [
    { id: 1, roles: '访客' },
    { id: 2, roles: '普通用户' },
    { id: 3, roles: '管理员' }
]

// mn网络延迟
function delay<T>(data: T, ms = 200): Promise<T> {
    return new Promise(resolve => setTimeout(() => resolve(data), ms))
}

// 获取权限列表
export async function fetchReaderPermissions(): Promise<ReaderPermissionDTO[]> {
    return delay(mockPermissions.map(p => ({ ...p })))
}