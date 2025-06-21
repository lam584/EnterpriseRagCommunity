// src/services/adminService.ts

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

export const fetchAdministrators = async (): Promise<AdministratorDTO[]> => {
    const res = await fetch(`${API_BASE}/administrators`);
    if (!res.ok) {
        throw new Error('获取管理员列表失败');
    }
    return res.json();
};

export const searchAdministrators = async (
    params: SearchParams
): Promise<AdministratorDTO[]> => {
    const query = new URLSearchParams();
    if (params.id !== undefined) query.append('id', params.id.toString());
    if (params.account) query.append('account', params.account);
    if (params.phone) query.append('phone', params.phone);
    if (params.email) query.append('email', params.email);

    const url = `${API_BASE}/administrators/search?${query.toString()}`;
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error('搜索管理员失败');
    }
    return res.json();
};

export const fetchAdministratorById = async (
    id: number
): Promise<AdministratorDTO> => {
    const res = await fetch(`${API_BASE}/administrators/${id}`);
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

export const updateAdministrator = async (
    id: number,
    payload: UpdateAdministratorDTO
): Promise<void> => {
    const res = await fetch(`${API_BASE}/administrators/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });
    if (!res.ok) {
        throw new Error(`更新管理员 ${id} 失败`);
    }
};