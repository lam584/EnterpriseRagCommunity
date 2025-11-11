// my-vite-app/src/types/admin.ts

/**
 * 管理员 dto 定义
 */
export interface AdminDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: string;
    permissionsId: number;
    isActive: boolean;
    registeredAt: string; // ISO 字符串
    updatedAt: string;    // ISO 字符串
}

/**
 * 搜索条件
 */
export interface AdminSearchCriteria {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}