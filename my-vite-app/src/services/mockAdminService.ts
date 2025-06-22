// src/services/mockAdminService.ts

export interface AdminFormData {
    username: string;
    password: string;
    confirmPassword: string;
    email: string;
    name: string;
    phone: string;
    department: string;
    role: string;
}

// 最终用于展示的管理员类型，不包含 password/confirmPassword
export interface Admin {
    id: string;
    username: string;
    email: string;
    name: string;
    phone: string;
    department: string;
    role: string;
    createdAt: string; // ISO 字符串
}

// mn的“数据库”
const admins: Admin[] = [];

/**
 * 获取所有管理员
 */
export const getAdmins = async (): Promise<Admin[]> => {
    // 直接返回当前数组的浅拷贝
    return Promise.resolve([...admins]);
};

/**
 * 添加一个管理员
 */
export const addAdmin = async (data: AdminFormData): Promise<Admin> => {
    return new Promise<Admin>(resolve => {
        setTimeout(() => {
            const newAdmin: Admin = {
                id: Date.now().toString(),
                username: data.username,
                email: data.email,
                name: data.name,
                phone: data.phone,
                department: data.department,
                role: data.role,
                createdAt: new Date().toISOString()
            };
            admins.push(newAdmin);
            resolve(newAdmin);
        }, 1000); // mn网络延迟 1s
    });
};