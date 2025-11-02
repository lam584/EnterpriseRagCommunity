// // src/components/Admin-management/EditAdmin.tsx
// import React, { useState, useEffect, useRef } from 'react';
// import {
//     fetchAdministrators,
//     searchAdministrators,
//     fetchAdministratorById,
//     updateAdministrator,
//     AdministratorDTO,
//     UpdateAdministratorDTO,
// } from '../../services/adminService_2';
// import {
//     fetchAdminPermissions,
//     AdminPermissionDTO,
// } from '../../services/adminPermissionService_1';
//
// type SearchField = 'id' | 'account' | 'phone' | 'email';
// const MAX_RECENT = 10;
//
// const EditAdmin: React.FC = () => {
//     // 全量 & 下拉
//     const [admins, setAdmins] = useState<AdministratorDTO[]>([]);
//     const [filteredAdmins, setFilteredAdmins] = useState<AdministratorDTO[]>([]);
//     const [dropdownOpen, setDropdownOpen] = useState(false);
//     const [searchLoading, setSearchLoading] = useState(false);
//
//     // 搜索
//     const [searchField, setSearchField] = useState<SearchField>('account');
//     const [keyword, setKeyword] = useState('');
//     const prevKeywordRef = useRef<string>('');
//
//     // 选中 & 表单
//     const [selectedId, setSelectedId] = useState<number | null>(null);
//     const [form, setForm] = useState<AdministratorDTO>({
//         id: 0,
//         account: '',
//         phone: '',
//         email: '',
//         sex: '男',
//         permissionsId: 0,
//         isActive: true,
//         registeredAt: '',
//         updatedAt: '',
//     });
//
//     // 权限 & 全局提示
//     const [permissions, setPermissions] = useState<AdminPermissionDTO[]>([]);
//     const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
//
//     // 改密 & 新密 & 字段错误
//     const [changePassword, setChangePassword] = useState(false);
//     const [newPassword, setNewPassword] = useState('');
//     const [errors, setErrors] = useState<Record<string, string>>({});
//
// // 下拉 & 定时器
//     const dropdownRef = useRef<HTMLDivElement>(null);
//     const inputRef = useRef<HTMLInputElement>(null);
// // 把 null 换成 undefined
//     const blurTimer = useRef<number | undefined>(undefined);
//     const searchTimerRef = useRef<number | undefined>(undefined);
//     // 初始加载
//     useEffect(() => {
//         fetchAdminPermissions()
//             .then(list => setPermissions(list))
//             .catch(() => console.error('获取管理员权限列表失败'));
//
//         fetchAdministrators()
//             .then(list => {
//                 const recent = [...list]
//                     .sort((a, b) => {
//                         const ta = new Date(a.updatedAt).getTime() || new Date(a.registeredAt).getTime();
//                         const tb = new Date(b.updatedAt).getTime() || new Date(b.registeredAt).getTime();
//                         return tb - ta;
//                     })
//                     .slice(0, MAX_RECENT);
//                 setAdmins(list);
//                 setFilteredAdmins(recent);
//             })
//             .catch(() => console.error('初始化管理员列表失败'));
//     }, []);
//
//     // 点击外部关闭下拉
//     useEffect(() => {
//         const onClickOutside = (e: MouseEvent) => {
//             if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
//                 setDropdownOpen(false);
//             }
//         };
//         document.addEventListener('mousedown', onClickOutside);
//         return () => document.removeEventListener('mousedown', onClickOutside);
//     }, []);
//
//     // 执行搜索
//     const performSearch = async (kw: string) => {
//         if (!kw.trim()) {
//             const recent = [...admins]
//                 .sort((a, b) => {
//                     const ua = new Date(a.updatedAt).getTime();
//                     const ub = new Date(b.updatedAt).getTime();
//                     if (ua && ub) return ub - ua;
//                     return new Date(b.registeredAt).getTime() - new Date(a.registeredAt).getTime();
//                 })
//                 .slice(0, MAX_RECENT);
//             setFilteredAdmins(recent);
//             if (document.activeElement === inputRef.current) {
//                 setDropdownOpen(true);
//             }
//             return;
//         }
//
//         if (kw.trim() === prevKeywordRef.current.trim()) return;
//         prevKeywordRef.current = kw.trim();
//
//         setSearchLoading(true);
//         try {
//             let idArg: number | undefined;
//             let accountArg: string | undefined;
//             let phoneArg: string | undefined;
//             let emailArg: string | undefined;
//
//             switch (searchField) {
//                 case 'id': {
//                     const n = Number(kw);
//                     if (!Number.isNaN(n)) idArg = n;
//                     break;
//                 }
//                 case 'account': {
//                     accountArg = kw;
//                     break;
//                 }
//                 case 'phone': {
//                     phoneArg = kw;
//                     break;
//                 }
//                 case 'email': {
//                     emailArg = kw;
//                     break;
//                 }
//             }
//
//             const res = await searchAdministrators({ id: idArg, account: accountArg, phone: phoneArg, email: emailArg });
//             setFilteredAdmins(res);
//             if (document.activeElement === inputRef.current) {
//                 setDropdownOpen(true);
//             }
//         } catch (err) {
//             console.error('搜索管理员失败', err);
//         } finally {
//             setSearchLoading(false);
//         }
//     };
//
//     // 字段 & 关键字变化
//     const handleFieldChange = (e: React.ChangeEvent<HTMLSelectElement | HTMLInputElement>) => {
//         const { name, value } = e.target;
//         if (name === 'searchField') {
//             setSearchField(value as SearchField);
//             if (keyword.trim()) {
//                 if (searchTimerRef.current !== undefined) {
//                     window.clearTimeout(searchTimerRef.current);
//                 }
//                 searchTimerRef.current = window.setTimeout(() => performSearch(keyword), 300);
//             }
//         } else {
//             setKeyword(value);
//             if (searchTimerRef.current !== undefined) {
//                 window.clearTimeout(searchTimerRef.current);
//             }
//             searchTimerRef.current = window.setTimeout(() => performSearch(value), 300);
//         }
//     };
//
//     const handleInputBlur = () => {
//         blurTimer.current = window.setTimeout(() => setDropdownOpen(false), 200);
//     };
//     const handleInputFocus = () => {
//         if (!keyword.trim()) {
//             const recent = [...admins]
//                 .sort((a, b) => {
//                     const ua = new Date(a.updatedAt).getTime();
//                     const ub = new Date(b.updatedAt).getTime();
//                     if (ua && ub) return ub - ua;
//                     return new Date(b.registeredAt).getTime() - new Date(a.registeredAt).getTime();
//                 })
//                 .slice(0, MAX_RECENT);
//             setFilteredAdmins(recent);
//         } else if (keyword.trim() !== prevKeywordRef.current.trim()) {
//             performSearch(keyword);
//         }
//         setDropdownOpen(true);
//     };
//
//     // 选中某个管理员
//     const handleSelect = async (id: number) => {
//         setSelectedId(id);
//         setDropdownOpen(false);
//         setMessage(null);
//         setErrors({});
//         setChangePassword(false);
//         setNewPassword('');
//         try {
//             setSearchLoading(true);
//             const dto = await fetchAdministratorById(id);
//             setForm(dto);
//         } catch {
//             setMessage({ type: 'error', text: '加载详情失败' });
//             setForm(prev => ({ ...prev, id }));
//         } finally {
//             setSearchLoading(false);
//         }
//     };
//
//     // 表单字段变动
//     const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
//         const { name, value } = e.target;
//         setForm(prev => ({
//             ...prev,
//             [name]:
//                 name === 'permissionsId'
//                     ? Number(value)
//                     : name === 'isActive'
//                         ? value === 'true'
//                         : value,
//         } as AdministratorDTO));
//
//         if (errors[name]) {
//             setErrors(prev => {
//                 const cp = { ...prev };
//                 delete cp[name];
//                 return cp;
//             });
//         }
//     };
//
//     // 校验
//     const validate = () => {
//         const errs: Record<string, string> = {};
//         if (!form.account) errs.account = '账号不能为空';
//         if (!form.phone) errs.phone = '手机号不能为空';
//         if (!form.email) errs.email = '邮箱不能为空';
//         if (changePassword && !newPassword) errs.password = '新密码不能为空';
//         setErrors(errs);
//         return Object.keys(errs).length === 0;
//     };
//
//     // 提交
//     const handleSubmit = async (e: React.FormEvent) => {
//         e.preventDefault();
//         if (!validate() || !form.id) return;
//         setMessage(null);
//         setSearchLoading(true);
//         try {
//             const payload: UpdateAdministratorDTO = {
//                 phone: form.phone,
//                 email: form.email,
//                 sex: form.sex,
//                 permissionsId: form.permissionsId,
//                 isActive: form.isActive,
//             };
//             if (changePassword) {
//                 payload.password = newPassword;
//             }
//             await updateAdministrator(form.id, payload);
//             setMessage({ type: 'success', text: '更新成功' });
//             // 刷新
//             const updated = await fetchAdministratorById(form.id);
//             setForm(updated);
//             const all = await fetchAdministrators();
//             setAdmins(all);
//         } catch (err) {
//             console.error(err);
//             setMessage({ type: 'error', text: '更新出错，请重试' });
//         } finally {
//             setSearchLoading(false);
//         }
//     };
//
//     return (
//         <div className="flex">
//             <div className="flex-1 bg-white p-6 shadow rounded">
//                 <h2 className="text-xl font-bold mb-4">编辑管理员信息</h2>
//                 {/* 搜索框 */}
//                 <div className="mb-4 relative">
//                     <div className="flex">
//                         <select
//                             name="searchField"
//                             value={searchField}
//                             onChange={handleFieldChange}
//                             className="border p-2 rounded-l"
//                         >
//                             <option value="id">ID</option>
//                             <option value="account">账号</option>
//                             <option value="phone">手机号</option>
//                             <option value="email">邮箱</option>
//                         </select>
//                         <input
//                             ref={inputRef}
//                             name="keyword"
//                             value={keyword}
//                             onChange={handleFieldChange}
//                             onFocus={handleInputFocus}
//                             onBlur={handleInputBlur}
//                             disabled={searchLoading}
//                             className="flex-1 border p-2 rounded-r"
//                             placeholder={`请输入${searchField}`}
//                         />
//                     </div>
//                     {dropdownOpen && (
//                         <div
//                             ref={dropdownRef}
//                             className="absolute top-full left-0 right-0 bg-white border rounded shadow max-h-60 overflow-auto z-10 mt-1"
//                         >
//                             {searchLoading ? (
//                                 <div className="p-2 text-gray-500 text-center">搜索中...</div>
//                             ) : filteredAdmins.length > 0 ? (
//                                 filteredAdmins.map(a => (
//                                     <div
//                                         key={a.id}
//                                         className="p-2 hover:bg-gray-100 cursor-pointer border-b"
//                                         onClick={() => handleSelect(a.id)}
//                                     >
//                                         <div className="font-medium">{a.account}</div>
//                                         <div className="text-xs text-gray-600">
//                                             ID:{a.id} | {a.phone} | {a.email}
//                                             {a.updatedAt && (
//                                                 <span> | 更新: {new Date(a.updatedAt).toLocaleString()}</span>
//                                             )}
//                                         </div>
//                                     </div>
//                                 ))
//                             ) : (
//                                 <div className="p-2 text-gray-500">
//                                     {keyword.trim() ? '暂无匹配结果' : '暂无最近更新的管理员'}
//                                 </div>
//                             )}
//                         </div>
//                     )}
//                 </div>
//
//                 {selectedId && (
//                     <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
//                         <span className="text-blue-700">已选择管理员ID: {selectedId}</span>
//                     </div>
//                 )}
//
//                 {/* 编辑表单 */}
//                 {selectedId && (
//                     <form onSubmit={handleSubmit} className="space-y-4">
//                         {message && (
//                             <div
//                                 className={`p-2 rounded ${
//                                     message.type === 'success'
//                                         ? 'bg-green-100 text-green-700'
//                                         : 'bg-red-100 text-red-700'
//                                 }`}
//                             >
//                                 {message.text}
//                             </div>
//                         )}
//
//                         <div className="grid grid-cols-2 gap-4">
//                             {(['account', 'phone', 'email'] as const).map(field => (
//                                 <div key={field}>
//                                     <label className="block mb-1">
//                                         {field === 'account'
//                                             ? '账号'
//                                             : field === 'phone'
//                                                 ? '手机号'
//                                                 : '邮箱'}
//                                     </label>
//                                     <input
//                                         name={field}
//                                         value={form[field]}
//                                         onChange={handleChange}
//                                         className={`w-full p-2 border rounded ${
//                                             errors[field] ? 'border-red-500' : 'border-gray-300'
//                                         }`}
//                                         disabled={field === 'account'}
//                                     />
//                                     {errors[field] && (
//                                         <p className="text-red-500 text-sm">{errors[field]}</p>
//                                     )}
//                                 </div>
//                             ))}
//
//                             <div>
//                                 <label className="block mb-1">性别</label>
//                                 <select
//                                     name="sex"
//                                     value={form.sex}
//                                     onChange={handleChange}
//                                     className="w-full p-2 border rounded"
//                                 >
//                                     <option value="男">男</option>
//                                     <option value="女">女</option>
//                                     <option value="其他">其他</option>
//                                 </select>
//                             </div>
//
//                             <div>
//                                 <label className="block mb-1">权限角色</label>
//                                 <select
//                                     name="permissionsId"
//                                     value={form.permissionsId}
//                                     onChange={handleChange}
//                                     className="w-full p-2 border rounded"
//                                 >
//                                     <option value={0}>请选择</option>
//                                     {permissions.map(p => (
//                                         <option key={p.id} value={p.id}>
//                                             {p.roles}
//                                         </option>
//                                     ))}
//                                 </select>
//                             </div>
//
//                             <div>
//                                 <label className="block mb-1">状态</label>
//                                 <select
//                                     name="isActive"
//                                     value={form.isActive.toString()}
//                                     onChange={handleChange}
//                                     className="w-full p-2 border rounded"
//                                 >
//                                     <option value="true">激活</option>
//                                     <option value="false">禁用</option>
//                                 </select>
//                             </div>
//
//                             <div className="col-span-2">
//                                 <label className="inline-flex items-center">
//                                     <input
//                                         type="checkbox"
//                                         checked={changePassword}
//                                         onChange={e => setChangePassword(e.target.checked)}
//                                         className="mr-2"
//                                     />
//                                     修改密码
//                                 </label>
//                                 {changePassword && (
//                                     <div className="mt-2">
//                                         <input
//                                             type="password"
//                                             placeholder="新密码"
//                                             value={newPassword}
//                                             onChange={e => setNewPassword(e.target.value)}
//                                             className={`w-full p-2 border rounded ${
//                                                 errors.password ? 'border-red-500' : 'border-gray-300'
//                                             }`}
//                                         />
//                                         {errors.password && (
//                                             <p className="text-red-500 text-sm">{errors.password}</p>
//                                         )}
//                                     </div>
//                                 )}
//                             </div>
//                         </div>
//
//                         <div className="text-right">
//                             <button
//                                 type="submit"
//                                 disabled={searchLoading}
//                                 className={`px-4 py-2 rounded text-white ${
//                                     searchLoading ? 'bg-gray-400 cursor-not-allowed' : 'bg-blue-500 hover:bg-blue-600'
//                                 }`}
//                             >
//                                 {searchLoading ? '提交中...' : '保存修改'}
//                             </button>
//                         </div>
//                     </form>
//                 )}
//             </div>
//             <div className="w-1/4 ml-6">{/* 帮助面板 */}</div>
//         </div>
//     );
// };
//
// export default EditAdmin;
