// my-vite-app/src/services/adminService.ts
import type { AdminDTO, AdminSearchCriteria } from '../types/admin';

// 这里预先准备一组假数据，可按需扩充
let mockAdmins: AdminDTO[] = [
    {
        id: 1,
        account: 'admin1',
        phone: '13800000001',
        email: 'admin1@example.com',
        sex: '男',
        permissionsId: 1,
        isActive: true,
        registeredAt: '2023-01-01T08:00:00Z',
        updatedAt: '2023-01-10T10:30:00Z'
    },
    {
        id: 2,
        account: 'admin2',
        phone: '13800000002',
        email: 'admin2@example.com',
        sex: '女',
        permissionsId: 2,
        isActive: true,
        registeredAt: '2023-02-01T09:00:00Z',
        updatedAt: '2023-02-15T11:20:00Z'
    },
    {
        id: 3,
        account: 'admin3',
        phone: '13800000003',
        email: 'admin3@example.com',
        sex: '男',
        permissionsId: 3,
        isActive: false,
        registeredAt: '2023-03-01T10:00:00Z',
        updatedAt: '2023-03-05T12:00:00Z'
    },
    {
        id: 4,
        account: 'guest',
        phone: '13800000004',
        email: 'guest@example.com',
        sex: '女',
        permissionsId: 1,
        isActive: true,
        registeredAt: '2023-04-01T11:00:00Z',
        updatedAt: '2023-04-05T13:00:00Z'
    }
];

/**
 * 获取所有管理员（用于最近更新）
 */
export const fetchAdmins = async (): Promise<AdminDTO[]> => {
    return new Promise(resolve => {
        setTimeout(() => {
            // 返回深拷贝，防止组件里误修改原数组
            resolve(JSON.parse(JSON.stringify(mockAdmins)));
        }, 300);
    });
};

/**
 * 根据条件搜索管理员
 */
export const searchAdmins = async (
    criteria: AdminSearchCriteria
): Promise<AdminDTO[]> => {
    return new Promise(resolve => {
        setTimeout(() => {
            let results = mockAdmins.slice();

            if (criteria.id !== undefined) {
                results = results.filter(a => a.id === criteria.id);
            }
            if (criteria.account) {
                results = results.filter(a =>
                    a.account.includes(criteria.account!)
                );
            }
            if (criteria.phone) {
                results = results.filter(a =>
                    a.phone.includes(criteria.phone!)
                );
            }
            if (criteria.email) {
                results = results.filter(a =>
                    a.email.includes(criteria.email!)
                );
            }

            resolve(JSON.parse(JSON.stringify(results)));
        }, 200);
    });
};

/**
 * 获取某个管理员详情
 */
export const fetchAdminById = async (id: number): Promise<AdminDTO> => {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            const found = mockAdmins.find(a => a.id === id);
            if (found) {
                resolve(JSON.parse(JSON.stringify(found)));
            } else {
                reject(new Error('管理员不存在'));
            }
        }, 200);
    });
};

/**
 * 删除某个管理员
 */
export const deleteAdmin = async (id: number): Promise<boolean> => {
    return new Promise(resolve => {
        setTimeout(() => {
            mockAdmins = mockAdmins.filter(a => a.id !== id);
            resolve(true);
        }, 300);
    });
};