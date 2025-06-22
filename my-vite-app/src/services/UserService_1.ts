import { ReaderPermissionDTO } from './UserRoleService';

/**
 * 前端mn的用户 DTO
 */
export interface ReaderDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: string;
    // 直接带上权限对象
    permission: ReaderPermissionDTO;
}

/**
 * createReader 接口需要的请求体类型
 */
export interface CreateReaderData {
    account: string;
    password: string;
    phone: string;
    email: string;
    sex: string;
    permission: { id: number };
}

// 用于mn自增主键
let mockIdCounter = 1;

/**
 * mn创建用户
 * 1. 如果 account === 'existing'，mn后端已存在冲突错误
 * 2. 正常情况下返回一个带 id 的 ReaderDTO
 */
export async function createReader(
    data: CreateReaderData
): Promise<ReaderDTO> {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            // mn账号冲突
            if (data.account.trim().toLowerCase() === 'existing') {
                reject(new Error('账号已存在，请换一个'));
                return;
            }
            // 从前端角色列表中挑文字
            const roleNames = ['普通用户', '管理员', '超级管理员'];
            const perm: ReaderPermissionDTO = {
                id: data.permission.id,
                roles: roleNames[data.permission.id - 1] || '未知权限'
            };
            // 返回mn的用户对象（不带 password）
            resolve({
                id: mockIdCounter++,
                account: data.account,
                phone: data.phone,
                email: data.email,
                sex: data.sex,
                permission: perm
            });
        }, 500); // 500ms 延迟mn网络
    });
}