// src/services/readerService.ts
// import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';
import { getCsrfToken } from '../utils/csrfUtils';
const API_BASE = '/api/readers';

export interface ReaderDTO {
  id?: number;
  account: string;
  password?: string;
  phone: string;
  email: string;
  sex?: string;
  permission?: {
    id: number;
    roles?: string;
  };
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// 获取所有读者
export async function fetchReaders(
  account?: string,
  phone?: string,
  email?: string
): Promise<ReaderDTO[]> {
  let url = API_BASE;

  // 添加可选的查询参数
  const params = new URLSearchParams();
  if (account) params.append('account', account);
  if (phone) params.append('phone', phone);
  if (email) params.append('email', email);

  const queryString = params.toString();
  if (queryString) {
    url += `?${queryString}`;
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
  const res = await fetch(`${API_BASE}/${id}`, { credentials: 'include' });

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

// 搜索读者（使用 DTO 接口）
export async function searchReaders(
    id?: number,
    account?: string,
    phone?: string,
    email?: string,
    gender?: string,
    role?: string,
    startDate?: string,
    endDate?: string
): Promise<ReaderDTO[]> {
  let url = `${API_BASE}/search/dto`;

  const params = new URLSearchParams();
  if (id !== undefined) params.append('id', id.toString());
  if (account) params.append('account', account);
  if (phone) params.append('phone', phone);
  if (email) params.append('email', email);
  if (gender) params.append('sex', gender); // 使用 sex 字段名对应后端
  if (role) params.append('role', role);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);

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