export interface SpringPage<_T> {
  content: _T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
  // 允许后端追加字段（如 pageable/sort 等），避免因字段差异导致解析失败
  [key: string]: unknown;
}
