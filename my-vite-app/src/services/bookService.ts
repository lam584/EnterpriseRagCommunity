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

// 普通搜索接口（向后兼容）
export async function fetchBooks(criteria?: Partial<BookDTO>): Promise<BookDTO[]> {
  let url = API_BASE;

  if (criteria) {
    const params = new URLSearchParams();
    if (criteria.id) params.append('id', criteria.id.toString());
    if (criteria.isbn) params.append('isbn', criteria.isbn);
    if (criteria.title) params.append('title', criteria.title);
    if (criteria.author) params.append('author', criteria.author);
    if (criteria.publisher) params.append('publisher', criteria.publisher);

    if (params.toString()) {
      url += `/search?${params.toString()}`;
    }
  }

  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) throw new Error('获取图书列表失败');
  return res.json();
}

// 高级搜索接口
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

export async function advancedSearch(criteria: AdvancedSearchCriteria): Promise<BookDTO[]> {
  const params = new URLSearchParams();

  // 添加ID搜索条件
  if (criteria.id) {
    params.append('id', criteria.id);
    params.append('idExact', criteria.idExact ? 'true' : 'false');
  }

  // 添加所有可能的搜索条件
  if (criteria.isbn) {
    params.append('isbn', criteria.isbn);
    params.append('isbnExact', criteria.isbnExact ? 'true' : 'false');
  }

  if (criteria.title) {
    params.append('title', criteria.title);
    params.append('titleExact', criteria.titleExact ? 'true' : 'false');
  }

  if (criteria.author) {
    params.append('author', criteria.author);
    params.append('authorExact', criteria.authorExact ? 'true' : 'false');
  }

  if (criteria.publisher) {
    params.append('publisher', criteria.publisher);
    params.append('publisherExact', criteria.publisherExact ? 'true' : 'false');
  }

  if (criteria.edition) {
    params.append('edition', criteria.edition);
    params.append('editionExact', criteria.editionExact ? 'true' : 'false');
  }

  if (criteria.category) {
    params.append('category', criteria.category);
    params.append('categoryExact', criteria.categoryExact ? 'true' : 'false');
  }

  if (criteria.shelvesCode) {
    params.append('shelvesCode', criteria.shelvesCode);
    params.append('shelvesCodeExact', criteria.shelvesCodeExact ? 'true' : 'false');
  }

  if (criteria.priceMin !== undefined) {
    params.append('priceMin', criteria.priceMin.toString());
  }

  if (criteria.priceMax !== undefined) {
    params.append('priceMax', criteria.priceMax.toString());
  }

  if (criteria.printTimes) {
    params.append('printTimes', criteria.printTimes);
    params.append('printTimesExact', criteria.printTimesExact ? 'true' : 'false');
  }

  if (criteria.status) {
    params.append('status', criteria.status);
  }

  const url = `${API_BASE}/advanced-search?${params.toString()}`;

  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) throw new Error('获取图书列表失败');
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
