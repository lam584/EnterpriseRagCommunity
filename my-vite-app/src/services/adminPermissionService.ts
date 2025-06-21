// src/services/adminPermissionService.ts

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export interface AdminPermissionDTO {
    id: number;
    // 前端组件使用 .roles 显示，所以这里我们把后端的 name 映射过来
    roles: string;
    description: string;
    allowEditUserProfile: boolean;
    createdAt: string;
    updatedAt: string;
}

interface RawAdminPermission {
    id: number;
    name: string;
    description: string;
    allowEditUserProfile: boolean;
    createdAt: string;
    updatedAt: string;
}

export const fetchAdminPermissions = async (): Promise<AdminPermissionDTO[]> => {
    const res = await fetch(`${API_BASE}/admin-permissions`);
    if (!res.ok) {
        throw new Error('获取权限列表失败');
    }
    const data = (await res.json()) as RawAdminPermission[];
    return data.map(item => ({
        id: item.id,
        roles: item.name,
        description: item.description,
        allowEditUserProfile: item.allowEditUserProfile,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
    }));
};