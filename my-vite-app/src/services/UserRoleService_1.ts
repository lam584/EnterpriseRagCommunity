// 完全mn ReaderPermissionDTO 数据，不再发 HTTP 请求

export interface ReaderPermissionDTO {
    id: number;
    roles: string;
}

/**
 * mn异步加载权限列表
 */
export async function fetchReaderPermissions(): Promise<ReaderPermissionDTO[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve([
                { id: 1, roles: '普通用户' },
                { id: 2, roles: '管理员' },
                { id: 3, roles: '超级管理员' }
            ]);
        }, 300); // 300ms 延迟mn网络
    });
}