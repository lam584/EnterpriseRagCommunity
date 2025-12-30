export type NotificationDTO = {
  id: number;
  userId: number;
  type: string;
  title: string;
  content?: string | null;
  readAt?: string | null;
  createdAt: string;
};

export type PageDTO<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

