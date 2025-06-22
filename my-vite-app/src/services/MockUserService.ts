// src/services/MockUserService.ts

// DTO 定义（和后端约定的字段保持一致）
export interface ReaderDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex?: string;
    permission?: { roles: string[] };
    isActive: boolean;
    createdAt?: string;
    updatedAt?: string;
}

export interface ReaderSearchCriteria {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}

// 用一个数组来mn「数据库」
let readers: ReaderDTO[] = [
    {
        id: 1,
        account: 'alice',
        phone: '13800000001',
        email: 'alice@example.com',
        sex: '女',
        permission: { roles: ['READER'] },
        isActive: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 2,
        account: 'bob',
        phone: '13800000002',
        email: 'bob@example.com',
        sex: '男',
        permission: { roles: ['READER', 'ADMIN'] },
        isActive: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 3,
        account: 'charlie',
        phone: '13800000003',
        email: 'charlie@example.com',
        sex: '男',
        permission: { roles: ['READER'] },
        isActive: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    // …你可以随意添加更多条目
];

// 一个简单的延迟mn
const delay = (ms = 300) => new Promise<void>(resolve => setTimeout(resolve, ms));

// 1. 拉取所有 reader （取副本，防止组件侧误改）
export const fetchReaders = async (): Promise<ReaderDTO[]> => {
    await delay();
    return readers.map(r => ({ ...r }));
};

// 2. 搜索
export const searchReaders = async (
    criteria: ReaderSearchCriteria
): Promise<ReaderDTO[]> => {
    await delay();
    return readers.filter(r => {
        if (criteria.id != null) {
            return r.id === criteria.id;
        }
        if (criteria.account) {
            return r.account.includes(criteria.account);
        }
        if (criteria.phone) {
            return r.phone.includes(criteria.phone);
        }
        if (criteria.email) {
            return r.email.includes(criteria.email);
        }
        return true;
    }).map(r => ({ ...r }));
};

// 3. 根据 ID 取详情
export const fetchReaderById = async (id: number): Promise<ReaderDTO> => {
    await delay();
    const r = readers.find(r => r.id === id);
    if (!r) {
        throw new Error('找不到该用户');
    }
    return { ...r };
};

// 4. 删除
export const deleteReader = async (id: number): Promise<void> => {
    await delay();
    const idx = readers.findIndex(r => r.id === id);
    if (idx === -1) {
        throw new Error('删除失败：用户不存在');
    }
    readers.splice(idx, 1);
};