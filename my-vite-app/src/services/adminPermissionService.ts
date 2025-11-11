// src/services/adminPermissionService.ts

import { getCsrfToken } from '../utils/csrfUtils';

// 修改 API 基础路径，确保与获取 CSRF 令牌时使用的域一致
const API_BASE = '/api';

// Spring Security默认的CSRF头名称
const CSRF_HEADER = 'X-CSRF-TOKEN';

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

/**
 * 获取管理员权限列表
 * 已添加 CSRF 令牌和携带 Cookie（credentials: 'include'），
 * 确保所有非登录类接口都有 CSRF 保护，禁止匿名访问。
 *
 * @returns {Promise<AdminPermissionDTO[]>} 权限列表
 * @throws 当网络请求失败或响应状态不是 2xx 时抛出错误
 */
export const fetchAdminPermissions = async (): Promise<AdminPermissionDTO[]> => {
    // 1. 获取 CSRF 令牌（如果尚未存储，会自动从后端 /api/auth/csrf-token 接口拉取并缓存）
    const csrfToken = await getCsrfToken();

    // 2. 发起带有 CSRF 令牌的 GET 请求，并携带 Cookie
    const res = await fetch(`${API_BASE}/admin-permissions`, {
        method: 'GET',
        headers: {
            // 使用常量确保一致性
            [CSRF_HEADER]: csrfToken,
        },
        // ensure cookies (session id etc.) are sent
        credentials: 'include',
    });

    if (!res.ok) {
        // 如果不是 2xx，抛出错误，交由调用方处理
        throw new Error(`获取权限列表失败，HTTP 状态码：${res.status}`);
    }

    // 3. 解析原始数据并映射到前端 dto
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

/**
 * 创建新的管理员权限
 * @param permissionData 权限数据
 * @returns {Promise<AdminPermissionDTO>} 创建成功的权限对象
 */
export interface CreateAdminPermissionData {
    name: string;
    description?: string;
    allowEditUserProfile?: boolean;
    // 可以根据需要添加更多字段
}

export const createAdminPermission = async (permissionData: CreateAdminPermissionData): Promise<AdminPermissionDTO> => {
    try {
        // 1. 获取最新的 CSRF 令牌，确保有效
        console.log('为创建权限操作获取新的 CSRF 令牌...');
        const csrfToken = await getCsrfToken(true); // 强制刷新令牌
        console.log('获取到的 CSRF 令牌:', csrfToken);

        // 2. 发起带有 CSRF 令牌的 POST 请求
        console.log('发送创建权限请求，数据:', permissionData);
        const res = await fetch(`${API_BASE}/admin-permissions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // 使用常量确保一致性
                [CSRF_HEADER]: csrfToken,
            },
            credentials: 'include', // 确保包含 cookie
            body: JSON.stringify(permissionData), // 添加请求体，将权限数据序列化为JSON
        });

        // 3. 检查响应状态
        if (!res.ok) {
            const errorText = await res.text();
            console.error('创建权限失败:', errorText);
            throw new Error(`创建权限失败，HTTP 状态码：${res.status}, 错误信息：${errorText}`);
        }

        // 4. 解析返回的权限数据
        const data = await res.json() as RawAdminPermission;
        console.log('创建权限成功，返回数据:', data);

        return {
            id: data.id,
            roles: data.name,
            description: data.description,
            allowEditUserProfile: data.allowEditUserProfile,
            createdAt: data.createdAt,
            updatedAt: data.updatedAt,
        };
    } catch (error) {
        console.error('创建权限过程中发生错误:', error);
        throw error; // 重新抛出错误，让调用者处理
    }
};

export const updateAdminPermission = async (id: number, permissionData: Partial<CreateAdminPermissionData>): Promise<AdminPermissionDTO> => {
    // 强制获取最新的 CSRF 令牌，确保有效
    const csrfToken = await getCsrfToken(true);

    const res = await fetch(`${API_BASE}/admin-permissions/${id}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            [CSRF_HEADER]: csrfToken,
        },
        credentials: 'include',
        body: JSON.stringify(permissionData),
    });

    if (!res.ok) {
        const errorText = await res.text();
        console.error('更新权限失败:', errorText);
        throw new Error(`更新管理员权限失败，HTTP 状态码：${res.status}`);
    }

    const data = await res.json() as RawAdminPermission;
    return {
        id: data.id,
        roles: data.name,
        description: data.description,
        allowEditUserProfile: data.allowEditUserProfile,
        createdAt: data.createdAt,
        updatedAt: data.updatedAt,
    };
};

export const deleteAdminPermission = async (id: number): Promise<void> => {
    // 强制获取最新的 CSRF 令牌，确保有效
    const csrfToken = await getCsrfToken(true);

    const res = await fetch(`${API_BASE}/admin-permissions/${id}`, {
        method: 'DELETE',
        headers: {
            [CSRF_HEADER]: csrfToken,
        },
        credentials: 'include',
    });

    if (!res.ok) {
        const errorText = await res.text();
        console.error('删除权限失败:', errorText);
        throw new Error(`删除管理员权限失败，HTTP 状态码：${res.status}`);
    }
};
