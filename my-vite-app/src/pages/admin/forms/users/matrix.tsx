import React, {useCallback, useEffect, useState} from 'react';
import {
    createPermission,
    deletePermission,
    PermissionsCreateDTO,
    PermissionsQueryDTO,
    PermissionsUpdateDTO,
    queryPermissions,
    updatePermission
} from '../../../../services/permissionsService';
import {Button} from '../../../../components/ui/button';
import {Input} from '../../../../components/ui/input';
import {FaSearch} from "react-icons/fa";

// Simple Modal Component
const Modal = ({isOpen, onClose, title, children}: {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode
}) => {
    if (!isOpen) return null;
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
            <div className="bg-white rounded-lg shadow-lg w-full max-w-md p-6">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-semibold">{title}</h3>
                    <button onClick={onClose} className="text-gray-500 hover:text-gray-700">&times;</button>
                </div>
                {children}
            </div>
        </div>
    );
};

const PermissionsManagement: React.FC = () => {
    const [permissions, setPermissions] = useState<PermissionsUpdateDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [query, setQuery] = useState<PermissionsQueryDTO>({pageNum: 1, pageSize: 20});

    // Modal State
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [currentPermission, setCurrentPermission] = useState<PermissionsUpdateDTO | null>(null);
    const [formData, setFormData] = useState<PermissionsCreateDTO>({resource: '', action: '', description: ''});

    const fetchPermissions = useCallback(async () => {
        setLoading(true);
        try {
            const res = await queryPermissions(query);
            setPermissions(res.content);
            setTotal(res.totalElements);
        } catch (error) {
            console.error(error);
            // alert('加载权限失败');
        } finally {
            setLoading(false);
        }
    }, [query]);

    useEffect(() => {
        fetchPermissions();
    }, [fetchPermissions]);

    const handleSearch = () => {
        setQuery(prev => ({...prev, pageNum: 1}));
        // fetchPermissions(); // 交给 useEffect，避免重复请求
    };

    const handleDelete = async (id: number) => {
        if (!window.confirm('确定要删除该权限吗？')) return;
        try {
            await deletePermission(id);
            fetchPermissions();
        } catch (error) {
            console.error(error);
            alert('删除失败');
        }
    };

    const openCreateModal = () => {
        setCurrentPermission(null);
        setFormData({resource: '', action: '', description: ''});
        setIsModalOpen(true);
    };

    const openEditModal = (perm: PermissionsUpdateDTO) => {
        setCurrentPermission(perm);
        setFormData({
            resource: perm.resource || '',
            action: perm.action || '',
            description: perm.description || ''
        });
        setIsModalOpen(true);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            if (currentPermission) {
                await updatePermission({...formData, id: currentPermission.id});
            } else {
                await createPermission(formData);
            }
            setIsModalOpen(false);
            fetchPermissions();
        } catch (error) {
            console.error(error);
            alert('保存失败');
        }
    };

    return (
        <div className="space-y-4 p-4 bg-white rounded-lg shadow">
            <div className="flex justify-between items-center">
                <h2 className="text-xl font-bold">权限管理</h2>
                <div className="flex items-center gap-2">
                    <Button onClick={openCreateModal}>新增权限</Button>
                </div>
            </div>

            <div className="space-y-4 bg-white rounded-lg">
                {/* Search Bar */}
                <div className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_1fr_1fr_auto] md:items-center">
                    <Input
                        placeholder="资源标识"
                        value={query.resource || ''}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery({
                            ...query,
                            resource: e.target.value
                        })}
                    />
                    <Input
                        placeholder="操作标识"
                        value={query.action || ''}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery({
                            ...query,
                            action: e.target.value
                        })}
                    />
                    <Input
                        placeholder="描述"
                        value={query.description || ''}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery({
                            ...query,
                            description: e.target.value
                        })}
                    />
                    <Button onClick={handleSearch} variant="secondary" className="whitespace-nowrap"> <FaSearch className="mr-2"/> 搜索</Button>
                </div>

                {/* Table */}
                <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                        <thead className="bg-gray-50">
                        <tr>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">资源</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">描述</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
                        </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                        {loading ? (
                            <tr>
                                <td colSpan={5} className="text-center py-4">加载中...</td>
                            </tr>
                        ) : permissions.length === 0 ? (
                            <tr>
                                <td colSpan={5} className="text-center py-4">暂无数据</td>
                            </tr>
                        ) : (
                            permissions.map(perm => (
                                <tr key={perm.id}>
                                    <td className="px-4 py-2 whitespace-nowrap text-sm text-gray-500">{perm.id}</td>
                                    <td className="px-4 py-2 whitespace-nowrap text-sm font-medium text-gray-900">{perm.resource}</td>
                                    <td className="px-4 py-2 whitespace-nowrap text-sm text-gray-500">{perm.action}</td>
                                    <td className="px-4 py-2 whitespace-nowrap text-sm text-gray-500">{perm.description}</td>
                                    <td className="px-4 py-2 whitespace-nowrap text-sm font-medium space-x-2">
                                        <Button size="sm" variant="outline"
                                                onClick={() => openEditModal(perm)}>编辑</Button>
                                        <Button size="sm" variant="secondary"
                                                className="bg-red-100 text-red-600 hover:bg-red-200"
                                                onClick={() => handleDelete(perm.id)}>删除</Button>
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination (Simple) */}
                <div className="flex flex-col gap-2 sm:flex-row sm:justify-between sm:items-center mt-4">
                    <div className="text-sm text-gray-500">
                        共 {total} 条记录
                    </div>
                    <div className="flex items-center justify-between sm:justify-end gap-3">
                        <div className="flex items-center gap-2">
                            <span className="text-sm text-gray-600">每页</span>
                            <select
                                className="h-9 rounded-md border border-gray-300 bg-white px-2 text-sm"
                                value={query.pageSize || 20}
                                onChange={(e) => {
                                    const nextPageSize = Number(e.target.value) || 20;
                                    setQuery(prev => ({...prev, pageSize: nextPageSize, pageNum: 1}));
                                }}
                            >
                                {[20, 50, 100, 200].map(size => (
                                    <option key={size} value={size}>{size}</option>
                                ))}
                            </select>
                            <span className="text-sm text-gray-600">条</span>
                        </div>

                        <div className="space-x-2">
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={query.pageNum === 1}
                                onClick={() => setQuery(prev => ({...prev, pageNum: (prev.pageNum || 1) - 1}))}
                            >
                                上一页
                            </Button>
                            <span className="text-sm text-gray-600">第 {query.pageNum} 页</span>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={permissions.length < (query.pageSize || 20)}
                                onClick={() => setQuery(prev => ({...prev, pageNum: (prev.pageNum || 1) + 1}))}
                            >
                                下一页
                            </Button>
                        </div>
                    </div>
                </div>

                {/* Modal */}
                <Modal
                    isOpen={isModalOpen}
                    onClose={() => setIsModalOpen(false)}
                    title={currentPermission ? '编辑权限' : '新增权限'}
                >
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700">资源标识</label>
                            <Input
                                required
                                value={formData.resource}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({
                                    ...formData,
                                    resource: e.target.value
                                })}
                                placeholder="例如: user"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700">操作标识</label>
                            <Input
                                required
                                value={formData.action}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({
                                    ...formData,
                                    action: e.target.value
                                })}
                                placeholder="例如: create"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700">描述</label>
                            <Input
                                value={formData.description || ''}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({
                                    ...formData,
                                    description: e.target.value
                                })}
                                placeholder="权限描述"
                            />
                        </div>
                        <div className="flex justify-end space-x-2 pt-4">
                            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>取消</Button>
                            <Button type="submit">保存</Button>
                        </div>
                    </form>
                </Modal>
            </div>
        </div>
    );
};

export default PermissionsManagement;
