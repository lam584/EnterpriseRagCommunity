// src/services/UserService_3.ts

import { ReaderPermissionDTO } from './UserRoleService_3';

/**
 * DTO：读者基本信息
 */
export interface ReaderDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    createdAt: string;              // ISO 日期字符串
    permission?: ReaderPermissionDTO;
    sex: '男' | '女' | '';           // 性别
    isActive: boolean;               // 是否激活
}

// mn的用户数据
const mockReaders: ReaderDTO[] = [
    {
        id: 1,
        account: 'zhangsan',
        phone: '13800138000',
        email: 'zhangsan@example.com',
        createdAt: '2023-01-15T08:00:00Z',
        permission: { id: 1, roles: '管理员' },
        sex: '男',
        isActive: true,
    },
    {
        id: 2,
        account: 'lisi',
        phone: '13900139000',
        email: 'lisi@example.com',
        createdAt: '2023-03-02T10:30:00Z',
        permission: { id: 2, roles: '普通用户' },
        sex: '女',
        isActive: false,
    },
    {
        id: 3,
        account: 'wangwu',
        phone: '13700137000',
        email: 'wangwu@example.com',
        createdAt: '2023-05-20T14:15:00Z',
        permission: { id: 3, roles: '访客' },
        sex: '男',
        isActive: true,
    },
    // …可自行添加更多mn数据
];

/**
 * mn查询用户列表，并根据参数做简单过滤
 */
export async function searchReaders(params: {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
    sex?: string;
    role?: string;
    startDate?: string;  // 格式：YYYY-MM-DD
    endDate?: string;    // 格式：YYYY-MM-DD
}): Promise<ReaderDTO[]> {
    return new Promise(resolve => {
        setTimeout(() => {
            let list = [...mockReaders];

            if (params.id != null) {
                list = list.filter(r => r.id === params.id);
            }
            if (params.account) {
                list = list.filter(r =>
                    r.account.includes(params.account!)
                );
            }
            if (params.phone) {
                list = list.filter(r =>
                    r.phone.includes(params.phone!)
                );
            }
            if (params.email) {
                list = list.filter(r =>
                    r.email.includes(params.email!)
                );
            }
            if (params.sex) {
                list = list.filter(r => r.sex === params.sex);
            }
            if (params.role) {
                list = list.filter(r =>
                    r.permission?.id.toString() === params.role
                );
            }
            if (params.startDate) {
                list = list.filter(r =>
                    r.createdAt.split('T')[0] >= params.startDate!
                );
            }
            if (params.endDate) {
                list = list.filter(r =>
                    r.createdAt.split('T')[0] <= params.endDate!
                );
            }

            resolve(list);
        }, 500);
    });
}