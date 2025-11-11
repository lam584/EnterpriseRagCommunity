// src/services/boardService.ts

export interface BoardCreateDTO {
  tenantId?: number;
  parentId?: number | null;
  name: string;
  description?: string;
  visible?: boolean;
  sortOrder?: number;
}

export interface BoardDTO extends BoardCreateDTO {
  id: number;
  postCount?: number;
  tenantName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface FieldError {
  fieldErrors: Record<string, string>;
}

// In-memory mock storage so the UI can work before backend is ready
const boards: BoardDTO[] = [];
let seq = 1;

function validate(dto: BoardCreateDTO) {
  const errors: Record<string, string> = {};
  if (dto.tenantId != null && Number.isNaN(dto.tenantId)) {
    errors.tenantId = 'Invalid Tenant ID';
  }
  if (dto.parentId != null && Number.isNaN(dto.parentId)) {
    errors.parentId = 'Invalid Parent ID';
  }
  if (dto.name == null || dto.name.trim() === '') {
    errors.name = 'Name is required';
  } else if (dto.name.length > 64) {
    errors.name = 'Name must not exceed 64 characters';
  }
  if (dto.description && dto.description.length > 255) {
    errors.description = 'Description must not exceed 255 characters';
  }
  if (dto.sortOrder != null && !Number.isInteger(dto.sortOrder)) {
    errors.sortOrder = 'Sort order must be an integer';
  }
  return errors;
}

export async function createBoard(payload: BoardCreateDTO): Promise<BoardDTO> {
  const errors = validate(payload);
  if (Object.keys(errors).length > 0) {
    throw Object.assign(new Error('Validation failed'), { fieldErrors: errors } as FieldError);
  }

  const now = new Date().toISOString();
  const board: BoardDTO = {
    id: seq++,
    tenantId: payload.tenantId,
    parentId: payload.parentId ?? null,
    name: payload.name.trim(),
    description: payload.description?.trim(),
    visible: payload.visible ?? true,
    sortOrder: payload.sortOrder ?? 0,
    postCount: 0,
    tenantName: undefined,
    createdAt: now,
    updatedAt: now,
  };
  boards.push(board);
  await new Promise(r => setTimeout(r, 400));
  return { ...board };
}

export async function listBoards(): Promise<BoardDTO[]> {
  await new Promise(r => setTimeout(r, 200));
  return boards
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name))
    .map(b => ({ ...b }));
}
