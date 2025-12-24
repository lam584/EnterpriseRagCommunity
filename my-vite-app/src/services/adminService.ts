// src/services/adminService.ts

import { getCsrfToken } from '../utils/csrfUtils'; // 获取 CSRF 令牌的工具

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export interface AdministratorDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: string;
    permissionsId: number;
    isActive: boolean;
    registeredAt: string;
    updatedAt: string;
}

export interface SearchParams {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}

/**
 * 获取所有管理员
 */
export const fetchAdministrators = async (): Promise<AdministratorDTO[]> => {
    const res = await fetch(`${API_BASE}/administrators`, {
        credentials: 'include' // 确保携带会话 Cookie
    });
    if (!res.ok) {
        throw new Error('获取管理员列表失败');
    }
    return res.json();
};

/**
 * 按条件搜索管理员
 */
export const searchAdministrators = async (
    params: SearchParams
): Promise<AdministratorDTO[]> => {
    const query = new URLSearchParams();
    if (params.id !== undefined)   query.append('id',   params.id.toString());
    if (params.account)            query.append('account', params.account);
    if (params.phone)              query.append('phone',   params.phone);
    if (params.email)              query.append('email',   params.email);

    const url = `${API_BASE}/administrators/search?${query.toString()}`;
    const res = await fetch(url, {
        credentials: 'include'
    });
    if (!res.ok) {
        throw new Error('搜索管理员失败');
    }
    return res.json();
};

/**
 * 根据 ID 获取单个管理员详情
 */
export const fetchAdministratorById = async (
    id: number
): Promise<AdministratorDTO> => {
    const res = await fetch(`${API_BASE}/administrators/${id}`, {
        credentials: 'include'
    });
    if (!res.ok) {
        throw new Error(`获取管理员 ${id} 详情失败`);
    }
    return res.json();
};

export interface UpdateAdministratorDTO {
    phone: string;
    email: string;
    sex: string;
    permissionsId: number;
    isActive: boolean;
    password?: string;
}

/**
 * 更新管理员信息（需要 CSRF 保护）
 */
export const updateAdministrator = async (
    id: number,
    payload: UpdateAdministratorDTO
): Promise<void> => {
    // 获取 CSRF 令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(`${API_BASE}/administrators/${id}`, {
        method: 'PUT',
        credentials: 'include',  // 携带 Cookie
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken  // CSRF 保护
        },
        body: JSON.stringify(payload)
    });

    if (!res.ok) {
        throw new Error(`更新管理员 ${id} 失败`);
    }
};