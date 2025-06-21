// src/components/reader-management/EditUser.tsx
import React, { useState, useEffect, useRef } from 'react'
import {
    fetchReaders,
    searchReaders,
    fetchReaderById,
    updateReader,
    ReaderDTO
} from '../../services/UserService.ts'
import {
    fetchReaderPermissions,
    ReaderPermissionDTO
} from '../../services/UserPermissionService.ts'

type SearchField = 'id' | 'account' | 'phone' | 'email'

interface ExtendedReaderDTO extends ReaderDTO {
    updatedAt?: string
}

const MAX_RECENT = 10;

const EditUser: React.FC = () => {
    // 完整用户列表，用于"最近更新"
    const [readers, setReaders] = useState<ExtendedReaderDTO[]>([])
    // 当前下拉列表内容
    const [filteredReaders, setFilteredReaders] = useState<ExtendedReaderDTO[]>([])
    // 是否展示下拉
    const [dropdownOpen, setDropdownOpen] = useState(false)
    // 搜索中 Loading
    const [searchLoading, setSearchLoading] = useState(false)

    // 搜索字段 & 关键字
    const [searchField, setSearchField] = useState<SearchField>('account')
    const [keyword, setKeyword] = useState('')
    // 上一次搜索的关键字，用于避免重复搜索
    const prevKeywordRef = useRef<string>('')

    // 选中用户 ID & 表单 DTO
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [form, setForm] = useState<ReaderDTO>({
        id: undefined,
        account: '',
        phone: '',
        email: '',
        sex: '男',
        permission: { id: 0 },
        isActive: true
    })

    // 权限列表 & 全局提示
    const [permissions, setPermissions] = useState<ReaderPermissionDTO[]>([])
    const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

    // 密码修改开关 & 密码校验
    const [changePassword, setChangePassword] = useState(false)
    const [newPassword, setNewPassword] = useState('')
    const [errors, setErrors] = useState<Record<string, string>>({})

    // 下拉框 ref + 失焦定时器
    const dropdownRef = useRef<HTMLDivElement>(null)
    const blurTimer = useRef<number | undefined>(undefined)
    const inputRef = useRef<HTMLInputElement>(null)

    // 搜索定时器ref
    const searchTimerRef = useRef<number | undefined>(undefined)

    // useEffect 拉取最近列表
    useEffect(() => {
        fetchReaderPermissions().then(setPermissions).catch(() => console.error('获取权限列表失败'));
        fetchReaders()
            .then(list => {
                // 排序取最新
                const recent = list
                    .sort((a, b) => {
                        const tA = new Date(a.updatedAt || a.createdAt || '').getTime();
                        const tB = new Date(b.updatedAt || b.createdAt || '').getTime();
                        return tB - tA;
                    })
                    .slice(0, MAX_RECENT);
                setReaders(list);
                setFilteredReaders(recent);
            })
            .catch(() => console.error('初始化用户列表失败'));
    }, []);

    /** 2. 点击外部区域关闭下拉 */
    useEffect(() => {
        const onClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setDropdownOpen(false)
            }
        }
        document.addEventListener('mousedown', onClickOutside)
        return () => document.removeEventListener('mousedown', onClickOutside)
    }, [])

    /** 3. 执行搜索的函数，抽离成独立函数以便重用 */
    const performSearch = async (searchKeyword: string) => {
        // 如果搜索关键字为空，显示最近更新的用户并返回
        if (!searchKeyword.trim()) {
            const recent = [...readers]
                .sort((a, b) => {
                    // 按更新时间和创建时间的组合排序
                    const updateTimeA = new Date(a.updatedAt || '').getTime()
                    const updateTimeB = new Date(b.updatedAt || '').getTime()

                    if (updateTimeA && updateTimeB) {
                        return updateTimeB - updateTimeA
                    }

                    const createTimeA = new Date(a.createdAt || '').getTime()
                    const createTimeB = new Date(b.createdAt || '').getTime()

                    return createTimeB - createTimeA
                })
                .slice(0, MAX_RECENT)
            setFilteredReaders(recent)

            // 仅在输入框聚焦时展开下拉
            if (document.activeElement === inputRef.current) {
                setDropdownOpen(true)
            }
            return
        }

        // 如果关键字与上次相同，不重复搜索
        if (searchKeyword.trim() === prevKeywordRef.current.trim()) {
            return
        }

        // 更新上次搜索的关键字
        prevKeywordRef.current = searchKeyword.trim()

        setSearchLoading(true)
        try {
            // 构造参数
            let idArg: number | undefined
            let accountArg: string | undefined
            let phoneArg: string | undefined
            let emailArg: string | undefined

            switch (searchField) {
                case 'id': {
                    const n = Number(searchKeyword)
                    if (!Number.isNaN(n)) idArg = n
                    break
                }
                case 'account':
                    accountArg = searchKeyword
                    break
                case 'phone':
                    phoneArg = searchKeyword
                    break
                case 'email':
                    emailArg = searchKeyword
                    break
            }

            // 发送搜索请求
            const res = await searchReaders({
                id: idArg,
                account: accountArg,
                phone: phoneArg,
                email: emailArg
            })

            setFilteredReaders(res)

            // 仅在输入框聚焦时展开下拉
            if (document.activeElement === inputRef.current) {
                setDropdownOpen(true)
            }
        } catch (err) {
            console.error('搜索用户失败', err)
        } finally {
            setSearchLoading(false)
        }
    }

    /** 4. 处理搜索字段和关键字变化 */
    const handleFieldChange = (e: React.ChangeEvent<HTMLSelectElement | HTMLInputElement>) => {
        if (e.target.name === 'searchField') {
            setSearchField(e.target.value as SearchField)

            // 搜索字段改变时，如果当前有关键字，立即执行一次搜索
            if (keyword.trim()) {
                // 清除之前的定时器
                if (searchTimerRef.current) {
                    window.clearTimeout(searchTimerRef.current)
                }

                // 设置新的定时器，延迟执行搜索
                searchTimerRef.current = window.setTimeout(() => {
                    performSearch(keyword)
                }, 300)
            }
        } else if (e.target.name === 'keyword') {
            const newKeyword = e.target.value
            setKeyword(newKeyword)

            // 清除之前的定时器
            if (searchTimerRef.current) {
                window.clearTimeout(searchTimerRef.current)
            }

            // 设置新的定时器，延迟执行搜索
            searchTimerRef.current = window.setTimeout(() => {
                performSearch(newKeyword)
            }, 300)
        }
    }

    /** 5. 输入框 & 下拉交互处理 */
    const handleInputBlur = () => {
        blurTimer.current = window.setTimeout(() => setDropdownOpen(false), 200)
    }

    const handleInputFocus = () => {
        // 输入框获取焦点时，根据当前keyword状态决定是否展示下拉框
        if (!keyword.trim()) {
            // 如果关键字为空，显示最近更新的用户
            const recent = [...readers]
                .sort((a, b) => {
                    // 按更新时间和创建时间的组合排序
                    const updateTimeA = new Date(a.updatedAt || '').getTime()
                    const updateTimeB = new Date(b.updatedAt || '').getTime()

                    if (updateTimeA && updateTimeB) {
                        return updateTimeB - updateTimeA
                    }

                    const createTimeA = new Date(a.createdAt || '').getTime()
                    const createTimeB = new Date(b.createdAt || '').getTime()

                    return createTimeB - createTimeA
                })
                .slice(0, MAX_RECENT)
            setFilteredReaders(recent)
        } else {
            // 如果有关键字且上次搜索结果与当前关键字不符，重新搜索
            if (keyword.trim() !== prevKeywordRef.current.trim()) {
                performSearch(keyword)
            }
        }

        // 显示下拉框
        setDropdownOpen(true)
    }



    /** 6. 选中某条用户，拉取详情填表 */
    const handleSelect = async (id: number) => {
        setSelectedId(id);
        setDropdownOpen(false);
        setMessage(null);
        setErrors({});
        setChangePassword(false);
        setNewPassword('');
        // **不要再清空 keyword**，保持原搜索内容
        try {
            setSearchLoading(true);
            const dto = await fetchReaderById(id);
            setForm(dto);
        } catch {
            setMessage({ type: 'error', text: '加载详情失败' });
            setForm(prev => ({ ...prev, id }));
        } finally {
            setSearchLoading(false);
        }
    };

    /** 7. 表单字段变动 */
    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target
        setForm(prev => {
            const c = { ...prev }
            if (name === 'permission') {
                c.permission = { id: Number(value) }
            } else if (name === 'isActive') {
                c.isActive = value === 'true'
            } else {
                // 指定可接受的键名类型
                const key = name as keyof Omit<ReaderDTO, 'id' | 'permission' | 'isActive'>
                c[key] = value
            }
            return c
        })
        if (errors[name]) {
            setErrors(prev => {
                const cp = { ...prev }
                delete cp[name]
                return cp
            })
        }
    }

    /** 8. 表单校验 */
    const validate = () => {
        const errs: Record<string, string> = {}
        if (!form.account) errs.account = '账号不能为空'
        if (!form.phone) errs.phone = '手机号不能为空'
        if (!form.email) errs.email = '邮箱不能为空'
        if (changePassword && !newPassword) errs.password = '新密码不能为空'
        setErrors(errs)
        return Object.keys(errs).length === 0
    }

    /** 9. 提交更新 */
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!validate() || !form.id) return

        setMessage(null)
        setSearchLoading(true)

        try {
            const payload: Partial<ReaderDTO> = { ...form }
            if (changePassword) payload.password = newPassword

            await updateReader(form.id, payload)
            setMessage({ type: 'success', text: '更新成功' })

            // 重新拉取详情 & 刷新列表缓存
            const updated = await fetchReaderById(form.id)
            setForm(updated)
            const all = await fetchReaders()
            setReaders(all)
        } catch (err) {
            console.error(err)
            setMessage({ type: 'error', text: '更新出错，请重试' })
        } finally {
            setSearchLoading(false)
        }
    }

    return (
        <div className="flex">
            <div className="flex-1 bg-white p-6 shadow rounded">
                <h2 className="text-xl font-bold mb-4">编辑用户信息</h2>
                {/* 搜索框容器加 relative */}
                <div className="mb-4 relative">
                    <div className="flex">
                        <select
                            name="searchField"
                            value={searchField}
                            onChange={handleFieldChange}
                            className="border p-2 rounded-l"
                        >
                            <option value="id">ID</option>
                            <option value="account">账号</option>
                            <option value="phone">手机号</option>
                            <option value="email">邮箱</option>
                        </select>
                        <input
                            ref={inputRef}
                            name="keyword"
                            value={keyword}
                            onChange={handleFieldChange}
                            onFocus={handleInputFocus}
                            onBlur={handleInputBlur}
                            disabled={searchLoading}
                            className="flex-1 border p-2 rounded-r"
                            placeholder={`请输入${searchField}`}
                        />
                    </div>
                    {/* 下拉改为绝对定位 */}
                    {dropdownOpen && (
                        <div
                            ref={dropdownRef}
                            className="absolute top-full left-0 right-0 bg-white border rounded shadow max-h-60 overflow-auto z-10 mt-1"

                        >
                            {searchLoading ? (
                                <div className="p-2 text-gray-500 text-center">搜索中...</div>
                            ) : filteredReaders.length > 0 ? (
                                filteredReaders.map(r => (
                                    <div
                                        key={r.id}
                                        className="p-2 hover:bg-gray-100 cursor-pointer border-b"
                                        onClick={() => r.id && handleSelect(r.id)}
                                    >
                                        <div className="font-medium">{r.account}</div>
                                        <div className="text-xs text-gray-600">
                                            ID:{r.id} | {r.phone} | {r.email}
                                            {r.updatedAt && <span> | 更新: {new Date(r.updatedAt).toLocaleString()}</span>}
                                        </div>
                                    </div>
                                ))
                            ) : (
                                <div className="p-2 text-gray-500">
                                    {keyword.trim() ? '暂无匹配结果' : '暂无最近更新的用户'}
                                </div>
                            )}
                        </div>
                    )}
                </div>


                {selectedId && (
                    <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
                        <span className="text-blue-700">已选择用户ID: {selectedId}</span>
                    </div>
                )}

                {/* 编辑表单 */}
                {selectedId && (
                    <form onSubmit={handleSubmit} className="space-y-4">
                        {message && (
                            <div
                                className={`p-2 rounded ${
                                    message.type === 'success'
                                        ? 'bg-green-100 text-green-700'
                                        : 'bg-red-100 text-red-700'
                                }`}
                            >
                                {message.text}
                            </div>
                        )}

                        <div className="grid grid-cols-2 gap-4">
                            {/* ... 表单内容保持不变 ... */}
                            {/* 账号 / 手机 / 邮箱 */}
                            {(['account', 'phone', 'email'] as const).map(field => (
                                <div key={field}>
                                    <label className="block mb-1">
                                        {field === 'account' ? '账号' : field === 'phone' ? '手机号' : '邮箱'}
                                    </label>
                                    <input
                                        name={field}
                                        value={form[field] || ''}
                                        onChange={handleChange}
                                        className={`w-full p-2 border rounded ${
                                            errors[field] ? 'border-red-500' : 'border-gray-300'
                                        }`}
                                    />
                                    {errors[field] && (
                                        <p className="text-red-500 text-sm">{errors[field]}</p>
                                    )}
                                </div>
                            ))}

                            {/* 性别 */}
                            <div>
                                <label className="block mb-1">性别</label>
                                <select
                                    name="sex"
                                    value={form.sex || '男'}
                                    onChange={handleChange}
                                    className="w-full p-2 border rounded"
                                >
                                    <option value="男">男</option>
                                    <option value="女">女</option>
                                    <option value="其他">其他</option>
                                </select>
                            </div>

                            {/* 权限 */}
                            <div>
                                <label className="block mb-1">权限角色</label>
                                <select
                                    name="permission"
                                    value={form.permission?.id || 0}
                                    onChange={handleChange}
                                    className="w-full p-2 border rounded"
                                >
                                    <option value={0}>请选择</option>
                                    {permissions.map(p => (
                                        <option key={p.id} value={p.id}>
                                            {p.roles}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* 状态 */}
                            <div>
                                <label className="block mb-1">状态</label>
                                <select
                                    name="isActive"
                                    value={(form.isActive || false).toString()}
                                    onChange={handleChange}
                                    className="w-full p-2 border rounded"
                                >
                                    <option value="true">激活</option>
                                    <option value="false">禁用</option>
                                </select>
                            </div>

                            {/* 修改密码 */}
                            <div className="col-span-2">
                                <label className="inline-flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={changePassword}
                                        onChange={e => setChangePassword(e.target.checked)}
                                        className="mr-2"
                                    />
                                    修改密码
                                </label>
                                {changePassword && (
                                    <div className="mt-2">
                                        <input
                                            type="password"
                                            placeholder="新密码"
                                            value={newPassword}
                                            onChange={e => setNewPassword(e.target.value)}
                                            className={`w-full p-2 border rounded ${
                                                errors.password ? 'border-red-500' : 'border-gray-300'
                                            }`}
                                        />
                                        {errors.password && (
                                            <p className="text-red-500 text-sm">{errors.password}</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>

                        <div className="text-right">
                            <button
                                type="submit"
                                disabled={searchLoading}
                                className={`px-4 py-2 rounded text-white ${
                                    searchLoading
                                        ? 'bg-gray-400 cursor-not-allowed'
                                        : 'bg-blue-500 hover:bg-blue-600'
                                }`}
                            >
                                {searchLoading ? '提交中...' : '保存修改'}
                            </button>
                        </div>
                    </form>
                )}
            </div>

            {/* 右侧帮助面板可以保持不变 */}
            <div className="w-1/4 ml-6">
                {/* ... */}
            </div>
        </div>
    )
}

export default EditUser