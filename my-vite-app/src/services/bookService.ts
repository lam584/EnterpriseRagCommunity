// src/services/bookService.ts
import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';

const API_BASE = '/api/books';

// 在 src/services/bookService.ts 中修改 BookDTO 接口

export interface BookDTO {
  id?: number;
  isbn: string;
  title: string;
  author: string;
  publisher: string;
  edition: string;
  price: string; // 使用字符串表示BigDecimal
  category: {id: number, name?: string};
  shelf: {id: number, shelfCode?: string};
  status: string;
  printTimes: string;
  createdAt?: string; // 添加创建时间
  updatedAt?: string; // 添加更新时间
}

// 新增高级搜索的 Criteria 接口
export interface AdvancedSearchCriteria {
  id?: string;
  idExact?: boolean;
  isbn?: string;
  isbnExact?: boolean;
  title?: string;
  titleExact?: boolean;
  author?: string;
  authorExact?: boolean;
  publisher?: string;
  publisherExact?: boolean;
  edition?: string;
  editionExact?: boolean;
  category?: string;
  categoryExact?: boolean;
  shelvesCode?: string;
  shelvesCodeExact?: boolean;
  priceMin?: number;
  priceMax?: number;
  printTimes?: string;
  printTimesExact?: boolean;
  status?: string;
}

// 普通搜索：不传参数时改为调用 /search
export async function fetchBooks(criteria?: Partial<BookDTO>): Promise<BookDTO[]> {
  let url = API_BASE;
  if (criteria && Object.keys(criteria).length > 0) {
    const params = new URLSearchParams();
    if (criteria.id)       params.append('id', String(criteria.id));
    if (criteria.isbn)     params.append('isbn', criteria.isbn);
    if (criteria.title)    params.append('title', criteria.title);
    if (criteria.author)   params.append('author', criteria.author);
    if (criteria.publisher)params.append('publisher', criteria.publisher);
    url = `${API_BASE}/search?${params.toString()}`;
  } else {
    // 无条件加载就走根接口
    url = `${API_BASE}`;
  }
  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) throw new Error('获取图书列表失败');
  return res.json();
}

// 高级搜索：请求地址保持与后端一致
export async function advancedSearch(criteria: AdvancedSearchCriteria): Promise<BookDTO[]> {
  const params = new URLSearchParams();
  Object.entries(criteria).forEach(([k,v])=>{
    if (v!==undefined && v!=='') params.append(k, String(v));
  });
  const url = `${API_BASE}/advanced-search?${params.toString()}`;
  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) throw new Error('高级搜索失败');
  return res.json();
}

export async function fetchBookById(id: number): Promise<BookDTO> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: 'include' });
  if (!res.ok) throw new Error('获取图书详情失败');
  return res.json();
}

export async function createBook(book: Partial<BookDTO>): Promise<BookDTO> {
  try {
    // 获取 CSRF 令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(API_BASE, {
      method: 'POST',
      credentials: 'include', // 确保包含凭证
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken
      },
      body: JSON.stringify(book)
    });

    // 根据不同响应状态处理
    if (res.status === 401) {
      // 如果是未授权错误，清除CSRF令牌并抛出特定错误
      clearCsrfToken();
      throw new Error('您的会话已过期或未登录，请重新登录后再试');
    } else if (res.status === 403) {
      // 如果是禁止访问错误，可能是CSRF令牌问题
      clearCsrfToken();
      throw new Error('安全验证失败，请刷新页面再试');
    } else if (!res.ok) {
      // 处理其他错误
      const errorData = await res.json().catch(() => ({ message: '创建图书失败' }));
      throw new Error(errorData.message || '创建图书失败');
    }

    return res.json();
  } catch (error) {
    console.error('创建图书时发生错误:', error);
    throw error;
  }
}

export async function updateBook(id: number, book: BookDTO): Promise<BookDTO> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken
    },
    body: JSON.stringify(book)
  });
  if (!res.ok) throw new Error('更新图书失败');
  return res.json();
}

export async function deleteBook(id: number): Promise<void> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      'X-CSRF-TOKEN': csrfToken
    }
  });
  if (!res.ok) throw new Error('删除图书失败');
}
