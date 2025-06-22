// src/services/adminService.ts
// 本文件mn管理员数据，前端使用，不与后端交互

// 性别类型
type Sex = '男' | '女' | '其他';

// DTO 定义
export interface AdministratorDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: Sex;
    permissionsId: number;
    isActive: boolean;
    registeredAt: string; // ISO 字符串
    updatedAt: string;    // ISO 字符串
}

export interface UpdateAdministratorDTO {
    phone: string;
    email: string;
    sex: Sex;
    permissionsId: number;
    isActive: boolean;
    password?: string;
}

// ———— mn数据源（内存） ————
const mockAdmins: AdministratorDTO[] = [
    {
        id: 1,
        account: 'admin1',
        phone: '13800000001',
        email: 'admin1@example.com',
        sex: '男',
        permissionsId: 1,
        isActive: true,
        registeredAt: '2024-01-01T08:00:00Z',
        updatedAt: '2024-02-01T10:00:00Z',
    },
    {
        id: 2,
        account: 'user2',
        phone: '13800000002',
        email: 'user2@example.com',
        sex: '女',
        permissionsId: 2,
        isActive: false,
        registeredAt: '2024-01-15T12:30:00Z',
        updatedAt: '2024-02-10T14:20:00Z',
    },
    // …可继续补充更多数据
];

// 获取所有管理员（mn延迟）
export function fetchAdministrators(): Promise<AdministratorDTO[]> {
    return new Promise(resolve => {
        setTimeout(() => {
            // 返回一个副本，避免外部篡改原数组
            resolve([...mockAdmins]);
        }, 200);
    });
}

// 根据条件搜索管理员
export function searchAdministrators(params: {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}): Promise<AdministratorDTO[]> {
    return new Promise(resolve => {
        setTimeout(() => {
            const filtered = mockAdmins.filter(item => {
                if (params.id != null && item.id !== params.id) return false;
                if (params.account && !item.account.includes(params.account)) return false;
                if (params.phone && !item.phone.includes(params.phone)) return false;
                if (params.email && !item.email.includes(params.email)) return false;
                return true;
            });
            resolve([...filtered]);
        }, 300);
    });
}

// 根据 ID 获取单个管理员
export function fetchAdministratorById(id: number): Promise<AdministratorDTO> {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            const admin = mockAdmins.find(a => a.id === id);
            if (admin) {
                resolve({ ...admin });
            } else {
                reject(new Error('管理员未找到'));
            }
        }, 200);
    });
}

// 更新管理员信息（内存中更新）
export function updateAdministrator(
    id: number,
    payload: UpdateAdministratorDTO
): Promise<void> {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            const idx = mockAdmins.findIndex(a => a.id === id);
            if (idx === -1) {
                return reject(new Error('管理员不存在'));
            }
            const old = mockAdmins[idx];
            const updated: AdministratorDTO = {
                ...old,
                phone: payload.phone,
                email: payload.email,
                sex: payload.sex,
                permissionsId: payload.permissionsId,
                isActive: payload.isActive,
                // 更新 updatedAt
                updatedAt: new Date().toISOString(),
            };
            // 注意：密码在这里不存储
            mockAdmins[idx] = updated;
            resolve();
        }, 400);
    });
}