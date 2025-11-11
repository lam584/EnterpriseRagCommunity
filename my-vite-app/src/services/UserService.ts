// src/services/UserService.ts
// import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';
import { getCsrfToken } from '../utils/csrfUtils';
const API_BASE = '/api/readers';

export interface ReaderDTO {
  id?: number;
  account: string; // 对齐：SQL users.account → DTO.account
  password?: string;
  phone: string; // 对齐：SQL users.phone → DTO.phone
  email: string; // 对齐：SQL users.email → DTO.email
  sex?: string; // 对齐：SQL users.sex → DTO.sex
  permission?: {
    id: number;
    roles?: string;
  };
  isActive?: boolean; // 对齐：SQL users.is_active → DTO.isActive
  createdAt?: string; // 对齐：SQL users.created_at → DTO.createdAt
  updatedAt?: string; // 对齐：SQL users.updated_at → DTO.updatedAt
}

// 获取所有 ReaderDTO 列表（避免实体懒加载代理序列化问题）
    export async function fetchReaders(
        account?: string,
        phone?: string,
        email?: string
            ): Promise<ReaderDTO[]> {
    let url = `${API_BASE}/dto`;

  // 添加可选的查询参数
  const params = new URLSearchParams();
  if (account) params.append('account', account);
  if (phone) params.append('phone', phone);
  if (email) params.append('email', email);

  const queryString = params.toString();
  if (queryString) {
      url += `?${ queryString }`;
  }

  const res = await fetch(url, { credentials: 'include' });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '获取读者列表失败' }));
    throw new Error(errorData.message || '获取读者列表失败');
  }

  return res.json();
}

// 获取读者详情
export async function fetchReaderById(id: number): Promise<ReaderDTO> {
    const res = await fetch(`${API_BASE}/${id}/dto`, { credentials: 'include' });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '获取读者详情失败' }));
    throw new Error(errorData.message || '获取读者详情失败');
  }

  return res.json();
}

// 创建读者
export async function createReader(reader: Partial<ReaderDTO>): Promise<ReaderDTO> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(reader)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '创建读者失败' }));
    throw new Error(errorData.message || '创建读者失败');
  }

  return res.json();
}

// 更新读者
export async function updateReader(id: number, reader: Partial<ReaderDTO>): Promise<ReaderDTO> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(reader)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '更新读者失败' }));
    throw new Error(errorData.message || '更新读者失败');
  }

  return res.json();
}

// 删除读者
export async function deleteReader(id: number): Promise<void> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE',
    headers: {
      'X-CSRF-TOKEN': csrfToken
    },
    credentials: 'include'
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '删除读者失败' }));
    throw new Error(errorData.message || '删除读者失败');
  }
}

// 搜索读者（使用 dto 接口）
export interface ReaderSearchCriteria {
  id?: number;
  account?: string;
  phone?: string;
  email?: string;
  sex?: string;
  role?: string;
  startDate?: string;
  endDate?: string;
}

export async function searchReaders(
    criteria: ReaderSearchCriteria
): Promise<ReaderDTO[]> {
  let url = `${API_BASE}/search/dto`;

  const params = new URLSearchParams();
  if (criteria.id !== undefined) params.append('id', criteria.id.toString());
  if (criteria.account) params.append('account', criteria.account);
  if (criteria.phone) params.append('phone', criteria.phone);
  if (criteria.email) params.append('email', criteria.email);
  if (criteria.sex) params.append('sex', criteria.sex);
  if (criteria.role) params.append('role', criteria.role);
  if (criteria.startDate) params.append('startDate', criteria.startDate);
  if (criteria.endDate) params.append('endDate', criteria.endDate);

  const queryString = params.toString();
  if (queryString) {
    url += `?${queryString}`;
  }

  const res = await fetch(url, { credentials: 'include' });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: '搜索读者失败' }));
    throw new Error(errorData.message || '搜索读者失败');
  }

  return res.json();
}