// my-vite-app/src/components/Admin-management/DeleteAdmin.tsx

import React, { useState, useEffect, useRef } from 'react'
import type { AdminDTO, AdminSearchCriteria } from '../../types/admin'
import {
    fetchAdmins,
    searchAdmins,
    fetchAdminById,
    deleteAdmin
} from '../../services/adminService_1'

type SearchField = 'id' | 'account' | 'phone' | 'email'
const MAX_RECENT = 10

const DeleteAdmin: React.FC = () => {
    // ========== 状态定义 ==========
    const [searchField, setSearchField] = useState<SearchField>('id')
    const [keyword, setKeyword] = useState<string>('')
    const [recentAdmins, setRecentAdmins] = useState<AdminDTO[]>([])
    const [filteredAdmins, setFilteredAdmins] = useState<AdminDTO[]>([])
    const [dropdownOpen, setDropdownOpen] = useState<boolean>(false)
    // @ts-ignore
    const searchTimer = useRef<number>()
    // @ts-ignore
    const blurTimer = useRef<number>()
    const dropdownRef = useRef<HTMLDivElement>(null)

    const [admin, setAdmin] = useState<AdminDTO | null>(null)
    const [showInfo, setShowInfo] = useState<boolean>(false)

    const [loading, setLoading] = useState<boolean>(false)
    const [error, setError] = useState<string | null>(null)
    const [success, setSuccess] = useState<string | null>(null)

    // ========== 初始化最近更新 ==========
    useEffect(() => {
        fetchAdmins()
            .then(list => {
                const recent = list
                    .sort((a, b) => {
                        const tA = new Date(a.updatedAt || a.registeredAt).getTime()
                        const tB = new Date(b.updatedAt || b.registeredAt).getTime()
                        return tB - tA
                    })
                    .slice(0, MAX_RECENT)
                setRecentAdmins(recent)
                setFilteredAdmins(recent)
            })
            .catch(() => console.error('初始化失败'))
    }, [])

    // ========== 搜索逻辑 ==========
    const performSearch = async (kw: string) => {
        if (!kw.trim()) {
            setFilteredAdmins(recentAdmins)
            return
        }
        const criteria: AdminSearchCriteria = {}
        if (searchField === 'id') {
            const idNum = Number(kw)
            if (!isNaN(idNum)) criteria.id = idNum
        } else {
            ;(criteria as any)[searchField] = kw
        }

        try {
            const res = await searchAdmins(criteria)
            setFilteredAdmins(res)
        } catch {
            setFilteredAdmins([])
        }
    }

    const handleKeywordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value
        setKeyword(val)
        setError(null)
        setSuccess(null)
        setShowInfo(false)
        setAdmin(null)
        window.clearTimeout(searchTimer.current)
        searchTimer.current = window.setTimeout(() => {
            performSearch(val)
            setDropdownOpen(true)
        }, 300)
    }

    const handleFieldChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const f = e.target.value as SearchField
        setSearchField(f)
        if (keyword.trim()) {
            window.clearTimeout(searchTimer.current)
            performSearch(keyword)
        }
    }

    // ========== 选择 & 删除 ==========
    const selectAdmin = async (id: number) => {
        setDropdownOpen(false)
        setError(null)
        setSuccess(null)
        setLoading(true)
        setShowInfo(true)
        try {
            const adminData = await fetchAdminById(id)
            setAdmin(adminData)
        } catch {
            setError('获取管理员信息失败')
            setAdmin(null)
        } finally {
            setLoading(false)
        }
    }

    const handleDelete = async () => {
        if (!admin) {
            setError('请先选择管理员')
            return
        }
        if (!window.confirm(`确定要删除管理员 ${admin.account}(ID:${admin.id})？`)) {
            return
        }
        setLoading(true)
        setError(null)
        setSuccess(null)
        try {
            await deleteAdmin(admin.id)
            setSuccess(`已删除 ${admin.account}`)
            setShowInfo(false)
            setAdmin(null)
            setKeyword('')
            // 刷新列表
            const list = await fetchAdmins()
            const recent = list
                .sort((a, b) => {
                    const tA = new Date(a.updatedAt || a.registeredAt).getTime()
                    const tB = new Date(b.updatedAt || b.registeredAt).getTime()
                    return tB - tA
                })
                .slice(0, MAX_RECENT)
            setRecentAdmins(recent)
            setFilteredAdmins(recent)
        } catch {
            setError('删除失败')
        } finally {
            setLoading(false)
        }
    }

    // ========== 点击外部收起下拉 ==========
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(e.target as Node)
            ) {
                window.clearTimeout(blurTimer.current)
                blurTimer.current = window.setTimeout(() => {
                    setDropdownOpen(false)
                }, 200)
            }
        }
        document.addEventListener('mousedown', handler)
        return () => {
            document.removeEventListener('mousedown', handler)
            window.clearTimeout(blurTimer.current)
            window.clearTimeout(searchTimer.current)
        }
    }, [])

    // ========== UI 渲染 ==========
    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-6">删除管理员</h2>
            {error && (
                <div className="mb-4 p-3 bg-red-100 text-red-700 rounded-md">
                    <p>{error}</p>
                </div>
            )}
            {success && (
                <div className="mb-4 p-3 bg-green-100 text-green-700 rounded-md">
                    <p>{success}</p>
                </div>
            )}

            {/* 搜索区 */}
            <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    搜索管理员
                </label>
                <div className="flex">
                    <select
                        value={searchField}
                        onChange={handleFieldChange}
                        className="mr-2 rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                    >
                        <option value="id">管理员ID</option>
                        <option value="account">用户名</option>
                        <option value="phone">手机号</option>
                        <option value="email">邮箱</option>
                    </select>
                    <div className="relative flex-1" ref={dropdownRef}>
                        <input
                            type="text"
                            value={keyword}
                            onChange={handleKeywordChange}
                            onFocus={() => setDropdownOpen(true)}
                            placeholder={`请输入${
                                searchField === 'id'
                                    ? 'ID'
                                    : searchField === 'account'
                                        ? '用户名'
                                        : searchField === 'phone'
                                            ? '手机号'
                                            : '邮箱'
                            }进行搜索`}
                            className="w-full px-3 py-2 rounded-md border border-gray-300 focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />
                        {dropdownOpen && (
                            <div className="absolute z-10 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg max-h-60 overflow-auto">
                                {filteredAdmins.length === 0 ? (
                                    <div className="p-3 text-gray-500">
                                        未找到符合条件的管理员
                                    </div>
                                ) : (
                                    filteredAdmins.map(item => (
                                        <div
                                            key={item.id}
                                            onClick={() => selectAdmin(item.id)}
                                            className="p-3 hover:bg-gray-100 cursor-pointer border-b border-gray-100 last:border-0"
                                        >
                                            <div className="flex justify-between items-center">
                                                <span className="font-medium">{item.account}</span>
                                                <span className="text-gray-500 text-sm">ID: {item.id}</span>
                                            </div>
                                            <div className="text-sm text-gray-600">{item.email}</div>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* 管理员详情 & 删除按钮 */}
            {showInfo && (
                <div className="mt-6">
                    {loading ? (
                        <div className="flex justify-center p-6">
                            <div className="animate-spin rounded-full h-8 w-8 border-4 border-purple-500 border-t-transparent"></div>
                        </div>
                    ) : admin ? (
                        <>
                            <h3 className="text-lg font-medium mb-4 pb-2 border-b border-gray-200">
                                管理员详情
                            </h3>
                            <div className="grid grid-cols-2 gap-4">
                                {/* ...（同之前字段展示） */}
                                <div>
                                    <p className="text-gray-500">管理员ID</p>
                                    <p>{admin.id}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">用户名</p>
                                    <p>{admin.account}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">邮箱</p>
                                    <p>{admin.email || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">手机号</p>
                                    <p>{admin.phone || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">性别</p>
                                    <p>{admin.sex || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">权限级别</p>
                                    <p>
                                        {admin.permissionsId === 1 && '普通管理员'}
                                        {admin.permissionsId === 2 && '高级管理员'}
                                        {admin.permissionsId === 3 && '超级管理员'}
                                        {(!admin.permissionsId || admin.permissionsId > 3) && '未知'}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">账号状态</p>
                                    <p>
                                        {admin.isActive ? (
                                            <span className="text-green-500">已激活</span>
                                        ) : (
                                            <span className="text-red-500">已禁用</span>
                                        )}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">注册时间</p>
                                    <p>
                                        {new Date(admin.registeredAt).toLocaleString()}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">最后更新</p>
                                    <p>
                                        {admin.updatedAt
                                            ? new Date(admin.updatedAt).toLocaleString()
                                            : '-'}
                                    </p>
                                </div>
                            </div>
                            <div className="mt-8 flex justify-center">
                                <button
                                    onClick={handleDelete}
                                    disabled={loading}
                                    className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 focus:outline-none disabled:bg-red-300"
                                >
                                    {loading ? '处理中...' : '删除此管理员'}
                                </button>
                            </div>
                        </>
                    ) : (
                        <div className="text-center py-4 text-gray-500">
                            未找到管理员信息
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}

export default DeleteAdmin