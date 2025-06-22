import axios from 'axios';
import { NewsItem, NewsResponse, NewsSearchCriteria } from '../types/news';

// 添加 NewsDTO 类型定义
export interface NewsDTO {
  id: number;
  title: string;
  content: string;
  summary: string;
  coverImage?: string;
  topicId: number;
  topicName?: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  publishedAt?: string;
  viewCount?: number;
  commentCount?: number;
  likeCount?: number;
  authorId?: number;
  authorName?: string;
}

const BASE_URL = '/api';

/**
 * 创建新闻
 * @param newsData 新闻数据
 */
export const createNews = async (newsData: Omit<NewsDTO, 'id' | 'createdAt' | 'updatedAt'>): Promise<NewsDTO> => {
  const response = await axios.post(`${BASE_URL}/news`, newsData);
  return response.data;
};

/**
 * 搜索新闻
 * @param criteria 搜索条件
 */
export const searchNews = async (criteria: NewsSearchCriteria): Promise<NewsDTO[]> => {
  const response = await axios.get(`${BASE_URL}/news/search`, { params: criteria });
  return response.data.content;
};

/**
 * 删除新闻
 * @param newsId 新闻ID
 */
export const deleteNews = async (newsId: number): Promise<boolean> => {
  const response = await axios.delete(`${BASE_URL}/news/${newsId}`);
  return response.status === 200;
};

/**
 * 获取新闻列表
 */
export const fetchNewsList = async (params: NewsSearchCriteria = { page: 0, size: 10 }): Promise<NewsResponse> => {
  const response = await axios.get(`${BASE_URL}/news`, { params });
  return response.data;
};

/**
 * 获取热门新闻列表
 */
export const fetchHotNewsList = async (limit: number = 5): Promise<NewsItem[]> => {
  const response = await axios.get(`${BASE_URL}/news/hot`, { params: { limit } });
  return response.data;
};

/**
 * 获取最新新闻列表
 */
export const fetchLatestNewsList = async (limit: number = 10): Promise<NewsItem[]> => {
  const response = await axios.get(`${BASE_URL}/news/latest`, { params: { limit } });
  return response.data;
};

/**
 * 根据ID获取新闻详情
 */
export const fetchNewsById = async (newsId: number): Promise<NewsItem> => {
  const response = await axios.get(`${BASE_URL}/news/${newsId}`);
  return response.data;
};

/**
 * 根据主题ID获取新闻列表
 */
export const fetchNewsByTopic = async (topicId: number, params: NewsSearchCriteria = { page: 0, size: 10 }): Promise<NewsResponse> => {
  const response = await axios.get(`${BASE_URL}/news/topic/${topicId}`, { params });
  return response.data;
};

/**
 * 获取所有可用主题列表
 */
export const fetchAllTopics = async () => {
  const response = await axios.get(`${BASE_URL}/topics`);
  return response.data;
};

/**
 * 增加新闻浏览量
 */
export const incrementNewsView = async (newsId: number) => {
  await axios.post(`${BASE_URL}/news/${newsId}/view`);
};

/**
 * 点赞新闻
 */
export const likeNews = async (newsId: number) => {
  await axios.post(`${BASE_URL}/news/${newsId}/like`);
};

/**
 * 获取相关推荐新闻
 */
export const fetchRelatedNews = async (newsId: number, limit: number = 5): Promise<NewsItem[]> => {
  const response = await axios.get(`${BASE_URL}/news/${newsId}/related`, { params: { limit } });
  return response.data;
};

/**
 * 更新新闻
 * @param newsId 新闻ID
 * @param newsData 新闻数据
 */
export const updateNews = async (newsId: number, newsData: Partial<NewsDTO>): Promise<NewsDTO> => {
  const response = await axios.put(`${BASE_URL}/news/${newsId}`, newsData);
  return response.data;
};

/**
 * 基础搜索功能
 * @param criteria 基本搜索条件
 * @param page 页码
 * @param size 每页条数
 */
export const searchNewsBasic = async (criteria: {
  title?: string;
  author?: string;
  categoryId?: string;
  status?: string;
}, page: number = 0, size: number = 10): Promise<NewsResponse> => {
  const params = {
    ...criteria,
    page,
    size
  };
  const response = await axios.get(`${BASE_URL}/news/search/basic`, { params });
  return response.data;
};

/**
 * 高级搜索功能
 * @param criteria 高级搜索条件
 * @param page 页码
 * @param size 每页条数
 */
export const searchNewsAdvanced = async (criteria: {
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
  viewsMax?: string;
  likesMin?: string;
  likesMax?: string;
  commentCountMin?: string;
  commentCountMax?: string;
  isTop?: boolean;
}, page: number = 0, size: number = 10): Promise<NewsResponse> => {
  const params = {
    ...criteria,
    page,
    size
  };
  const response = await axios.get(`${BASE_URL}/news/search/advanced`, { params });
  return response.data;
};
