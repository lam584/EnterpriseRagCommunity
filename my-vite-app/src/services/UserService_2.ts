// src/services/UserService.ts

// DTO 定义，和后端接口保持一致
export interface ReaderDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: string;
    permission: { id: number };
    isActive: boolean;
    createdAt?: string;
    updatedAt?: string;
    // password 字段仅在 updateReader 时可传入，不会回传给前端列表
    password?: string;
}

// 内存中的用户列表（示例数据）
let mockReaders: ReaderDTO[] = [
    {
        id: 1,
        account: 'alice',
        phone: '13800138000',
        email: 'alice@example.com',
        sex: '女',
        permission: { id: 2 },
        isActive: true,
        createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(), // 昨天
        updatedAt: new Date(Date.now() - 1000 * 60 * 30).toISOString()     // 半小时前
    },
    {
        id: 2,
        account: 'bob',
        phone: '13900139000',
        email: 'bob@example.com',
        sex: '男',
        permission: { id: 1 },
        isActive: false,
        createdAt: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
        updatedAt: new Date(Date.now() - 1000 * 60 * 60).toISOString()
    },
    {
        id: 3,
        account: 'charlie',
        phone: '13700137000',
        email: 'charlie@example.com',
        sex: '其他',
        permission: { id: 3 },
        isActive: true,
        createdAt: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(),
        updatedAt: new Date(Date.now() - 1000 * 60 * 10).toISOString()
    }
]

// mn网络延迟
function delay<T>(data: T, ms = 300): Promise<T> {
    return new Promise(resolve => setTimeout(() => resolve(data), ms))
}

// 1. 获取全部用户
export async function fetchReaders(): Promise<ReaderDTO[]> {
    // 返回副本防止外部修改原数组
    return delay(mockReaders.map(r => ({ ...r })))
}

// 2. 根据参数搜索用户
export async function searchReaders(params: {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}): Promise<ReaderDTO[]> {
    let result = mockReaders
    if (params.id != null) {
        result = result.filter(r => r.id === params.id)
    }
    if (params.account) {
        result = result.filter(r => r.account.includes(params.account!))
    }
    if (params.phone) {
        result = result.filter(r => r.phone.includes(params.phone!))
    }
    if (params.email) {
        result = result.filter(r => r.email.includes(params.email!))
    }
    return delay(result.map(r => ({ ...r })))
}

// 3. 根据 ID 获取单个用户详情
export async function fetchReaderById(id: number): Promise<ReaderDTO> {
    const found = mockReaders.find(r => r.id === id)
    if (!found) {
        return Promise.reject(new Error('用户不存在'))
    }
    return delay({ ...found })
}

// 4. 更新用户（部分字段）
export async function updateReader(
    id: number,
    payload: Partial<ReaderDTO>
): Promise<void> {
    const idx = mockReaders.findIndex(r => r.id === id)
    if (idx === -1) {
        return Promise.reject(new Error('用户不存在'))
    }
    // 仅允许更新部分字段
    const old = mockReaders[idx]
    const updated: ReaderDTO = {
        ...old,
        ...payload,
        updatedAt: new Date().toISOString()
    }
    mockReaders[idx] = updated
    return delay(undefined, 200)
}