// 新闻相关类型定义
export interface NewsItem {
  id: number;
  title: string;
  content: string;
  summary: string;
  coverImage: string | null;
  authorId: number;
  authorName: string;
  topicId: number;
  topicName: string;
  status: string;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string;
}

export interface NewsSearchCriteria {
  id?: string;
  idExact?: boolean;
  title?: string;
  titleExact?: boolean;
  author?: string;
  authorExact?: boolean;
  categoryId?: string;
  categoryIdExact?: boolean;
  status?: string;
  createdStartDate?: string;
  createdEndDate?: string;
  updatedStartDate?: string;
  updatedEndDate?: string;
  viewsMin?: string;
  page?: number;
  size?: number;
}

export interface NewsResponse {
  content: NewsItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface Topic {
  id: number;
  name: string;
  description?: string;
}
