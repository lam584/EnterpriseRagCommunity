import { getCsrfToken } from '../utils/csrfUtils';
import { UserDTO, UserCreateDTO, UserUpdateDTO, UserQueryDTO, UserRoleDTO } from '../types/userAccess';
import { toApiError } from './apiError';

const API_BASE_URL = '/api/users';

interface PageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
}

export const userAccessService = {
    async queryUsers(query: UserQueryDTO): Promise<PageResponse<UserDTO>> {
        const csrfToken = await getCsrfToken();
        const res = await fetch(`${API_BASE_URL}/query`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include',
            body: JSON.stringify(query)
        });
        if (!res.ok) throw new Error('Failed to query users');
        return res.json();
    },

    async createUser(data: UserCreateDTO): Promise<UserDTO> {
        const csrfToken = await getCsrfToken();
        const res = await fetch(API_BASE_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include',
            body: JSON.stringify(data)
        });
        if (!res.ok) throw new Error('Failed to create user');
        return res.json();
    },

    async updateUser(data: UserUpdateDTO): Promise<UserDTO> {
        const csrfToken = await getCsrfToken();
        const res = await fetch(API_BASE_URL, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include',
            body: JSON.stringify(data)
        });
        if (!res.ok) throw new Error('Failed to update user');
        return res.json();
    },

    async deleteUser(id: number): Promise<void> {
        const csrfToken = await getCsrfToken();
        const res = await fetch(`${API_BASE_URL}/${id}`, {
            method: 'DELETE',
            headers: {
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include'
        });
        if (!res.ok) {
            const msg = await safeReadErrorMessage(res);
            throw new Error(msg || 'Failed to delete user');
        }
    },

    async hardDeleteUser(id: number): Promise<void> {
        const csrfToken = await getCsrfToken();
        const res = await fetch(`${API_BASE_URL}/${id}/hard`, {
            method: 'DELETE',
            headers: {
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include'
        });
        if (!res.ok) {
            const msg = await safeReadErrorMessage(res);
            throw new Error(msg || 'Failed to hard delete user');
        }
    },

    async getUserRoles(userId: number): Promise<UserRoleDTO[]> {
        const res = await fetch(`${API_BASE_URL}/${userId}/roles`, {
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Failed to get user roles');
        return res.json();
    },

    async getUserById(userId: number): Promise<UserDTO> {
        const res = await fetch(`${API_BASE_URL}/${userId}`, {
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Failed to get user');
        return res.json();
    },

    async assignRoles(userId: number, roleIds: number[], opts?: { adminReason?: string }): Promise<void> {
        // Normalize/validate payload to avoid sending nested arrays or nulls.
        const normalizedRoleIds = Array.from(
            new Set(
                (Array.isArray(roleIds) ? (roleIds as unknown[]) : [])
                    .flatMap((v: unknown) => (Array.isArray(v) ? v : [v]))
                    .filter((v: unknown): v is number => typeof v === 'number' && Number.isFinite(v))
            )
        );

        if (!Array.isArray(roleIds)) {
            throw new Error('roleIds must be an array');
        }
        // If we had to drop values, it means the caller passed an unexpected structure.
        if (normalizedRoleIds.length !== roleIds.length) {
            throw new Error('Invalid roleIds payload (contains non-number values)');
        }

        const csrfToken = await getCsrfToken();
        const res = await fetch(`${API_BASE_URL}/${userId}/roles`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken,
                ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
            },
            credentials: 'include',
            body: JSON.stringify(normalizedRoleIds)
        });
        if (!res.ok) throw await toApiError(res, '分配角色失败');
    }
};

async function safeReadErrorMessage(res: Response): Promise<string> {
    try {
        const ct = res.headers.get('content-type') || '';
        if (ct.includes('application/json')) {
            const data = await res.json();
            // Common patterns
            if (typeof data === 'string') return data;
            if (data && typeof data.message === 'string') return data.message;
            if (data && typeof data.error === 'string') return data.error;
            if (data && typeof data.detail === 'string') return data.detail;
            return JSON.stringify(data);
        }
        const text = await res.text();
        return text;
    } catch {
        return '';
    }
}
