export interface UserDTO {
    id: number;
    tenantId?: number;
    email: string;
    username: string;
    status: 'ACTIVE' | 'DISABLED' | 'EMAIL_UNVERIFIED' | 'DELETED';
    /**
     * 兼容：后端历史数据/接口可能返回 null 或缺失。
     * - true: 已软删除
     * - false/null/undefined: 未标记软删除
     */
    isDeleted?: boolean | null;
    metadata?: string;
    createdAt?: string;
    updatedAt?: string;
    lastLoginAt?: string;
}

export interface UserCreateDTO {
    email: string;
    username: string;
    passwordHash: string;
    status: 'ACTIVE' | 'DISABLED' | 'EMAIL_UNVERIFIED' | 'DELETED';
    isDeleted?: boolean;
    metadata?: string;
    roleIds?: number[];
}

export interface UserUpdateDTO {
    id: number;
    email?: string;
    username?: string;
    passwordHash?: string;
    status?: 'ACTIVE' | 'DISABLED' | 'EMAIL_UNVERIFIED' | 'DELETED';
    isDeleted?: boolean;
    metadata?: string;
}

export interface UserQueryDTO {
    pageNum: number;
    pageSize: number;
    tenantId?: number;
    email?: string;
    username?: string;
    status?: string[];
    lastLoginFrom?: string;
    lastLoginTo?: string;
    createdAfter?: string;
    createdBefore?: string;
    includeDeleted?: boolean;
}

export interface UserRoleDTO {
    userId: number;
    roleId: number;
}
