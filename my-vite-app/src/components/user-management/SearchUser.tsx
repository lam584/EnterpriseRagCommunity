// src/components/user-management/SearchUser.tsx

// import React, {useEffect, useState} from 'react';
// import DatePicker from 'react-datepicker';
// import 'react-datepicker/dist/react-datepicker.css';

// import {ReaderDTO, searchReaders} from '../../services/UserService'; // 对齐：UserService.ts
// import {fetchReaderPermissions, ReaderPermissionDTO} from '../../services/UserRoleService';
//
// const SearchUser: React.FC = () => {
//     // 表单字段状态
//     const [formData, setFormData] = useState({
//         id: '',
//         account: '', // 对齐：SQL users.account → DTO.account
//         phone: '', // 对齐：SQL users.phone → DTO.phone
//         email: '', 
//         role: '',        // 空表示“全部”
//         sex: '请选择', // 对齐：SQL users.sex → DTO.sex
//         startDate: null as Date | null,
//         endDate: null as Date | null,
//     });
//
//     // 精确搜索开关
//     const [exactSearch, setExactSearch] = useState({
//         id: false,
//         account: false, // 对齐：SQL users.account → DTO.account
//         phone: false, // 对齐：SQL users.phone → DTO.phone
//         email: false, 
//     });
//
//     // 角色列表
//     const [roles, setRoles] = useState<ReaderPermissionDTO[]>([]);
//
//     // 搜索结果
//     const [showResults, setShowResults] = useState(false);
//     const [showSuccess, setShowSuccess] = useState(false);
//     const [results, setResults] = useState<ReaderDTO[]>([]);
//
//     // 挂载时加载角色
//     useEffect(() => {
//         (async () => {
//             try {
//                 const perms = await fetchReaderPermissions();
//                 setRoles(perms);
//             } catch (err) {
//                 console.error('获取角色失败', err);
//             }
//         })();
//     }, []);
//
//     // 输入变化
//     const handleInputChange = (
//         e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
//     ) => {
//         const { id, value } = e.target;
//         // 对齐：SQL users.account/phone/email/sex → DTO.account/phone/email/sex
//         setFormData(prev => ({...prev, [id]: value}));
//     };
//
//     // 日期变化
//     const handleDateChange = (
//         date: Date | null,
//         field: 'startDate' | 'endDate'
//     ) => {
//         setFormData(prev => ({...prev, [field]: date}));
//     };
//
//     // 精确搜索开关
//     const handleExactSearchChange = (field: keyof typeof exactSearch) => (
//         e: React.ChangeEvent<HTMLInputElement>
//     ) => {
//         // 对齐：SQL users.account/phone/email → DTO.account/phone/email
//         setExactSearch(prev => ({...prev, [field]: e.target.checked}));
//     };
//
//     // 提交搜索
//     const handleSearch = async (e: React.FormEvent) => {
//         e.preventDefault();
//
//         const idParam = formData.id ? parseInt(formData.id, 10) : undefined;
//         const accountParam = formData.account || undefined; // 对齐：SQL users.account → DTO.account
//         const phoneParam = formData.phone || undefined; // 对齐：SQL users.phone → DTO.phone
//         const emailParam = formData.email || undefined; 
//         const sexParam =
//             formData.sex !== '请选择' ? formData.sex : undefined; // 对齐：SQL users.sex → DTO.sex
//         const roleParam = formData.role || undefined;
//         const startDateParam = formData.startDate
//             ? formData.startDate.toISOString().split('T')[0]
//             : undefined;
//         const endDateParam = formData.endDate
//             ? formData.endDate.toISOString().split('T')[0]
//             : undefined;
//
//         try {
//             const data = await searchReaders({
//                 id: idParam,
//                 account: accountParam,
//                 phone: phoneParam,
//                 email: emailParam,
//                 sex: sexParam, // 对齐：SQL users.sex → DTO.sex
//                 role: roleParam,
//                 startDate: startDateParam,
//                 endDate: endDateParam,
//             });
//             setResults(data);
//             setShowResults(true);
//             setShowSuccess(true);
//         } catch (err) {
//             console.error('搜索失败', err);
//             setShowResults(false);
//             setShowSuccess(false);
//         }
//     };
//
//     return (
//         <div className="max-w-7xl mx-auto p-6 bg-white shadow-md rounded-md mt-10">
//             <h1 className="text-xl font-bold mb-6">用户查询</h1>
//
//             <form className="grid grid-cols-2 gap-6" onSubmit={handleSearch}>
//                 {/* ID */}
//                 <div>
//                     <label htmlFor="id" className="block text-sm font-medium mb-1">
//                         ID
//                     </label>
//                     <input
//                         type="text"
//                         id="id"
//                         value={formData.id}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                     <div className="mt-2">
//                         <input
//                             type="checkbox"
//                             id="exactSearchId"
//                             checked={exactSearch.id}
//                             onChange={handleExactSearchChange('id')}
//                             className="mr-2"
//                         />
//                         <label htmlFor="exactSearchId" className="text-sm">
//                             精确搜索
//                         </label>
//                     </div>
//                 </div>
//
//                 {/* 账号 */} // 对齐：SQL users.account → DTO.account
//                 <div>
//                     <label htmlFor="account" className="block text-sm font-medium mb-1">
//                         账号
//                     </label>
//                     <input
//                         type="text"
//                         id="account"
//                         value={formData.account}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                     <div className="mt-2">
//                         <input
//                             type="checkbox"
//                             id="exactSearchAccount"
//                             checked={exactSearch.account}
//                             onChange={handleExactSearchChange('account')}
//                             className="mr-2"
//                         />
//                         <label htmlFor="exactSearchAccount" className="text-sm">
//                             精确搜索
//                         </label>
//                     </div>
//                 </div>
//
//                 {/* 手机号 */}
//                 <div>
//                     <label htmlFor="phone" className="block text-sm font-medium mb-1">
//                         手机号
//                     </label>
//                     <input
//                         type="text"
//                         id="phone"
//                         value={formData.phone}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                     <div className="mt-2">
//                         <input
//                             type="checkbox"
//                             id="exactSearchPhone"
//                             checked={exactSearch.phone}
//                             onChange={handleExactSearchChange('phone')}
//                             className="mr-2"
//                         />
//                         <label htmlFor="exactSearchPhone" className="text-sm">
//                             精确搜索
//                         </label>
//                     </div>
//                 </div>
//
//                 {/* 邮箱 */}
//                 <div>
//                     <label htmlFor="email" className="block text-sm font-medium mb-1">
//                         邮箱
//                     </label>
//                     <input
//                         type="text"
//                         id="email"
//                         value={formData.email}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                     <div className="mt-2">
//                         <input
//                             type="checkbox"
//                             id="exactSearchEmail"
//                             checked={exactSearch.email}
//                             onChange={handleExactSearchChange('email')}
//                             className="mr-2"
//                         />
//                         <label htmlFor="exactSearchEmail" className="text-sm">
//                             精确搜索
//                         </label>
//                     </div>
//                 </div>
//
//                 {/* 角色 */}
//                 <div>
//                     <label htmlFor="role" className="block text-sm font-medium mb-1">
//                         角色
//                     </label>
//                     <select
//                         id="role"
//                         value={formData.role}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     >
//                         <option value="">全部</option>
//                         {roles.map(r => (
//                             <option key={r.id} value={r.id.toString()}>
//                                 {r.roles}
//                             </option>
//                         ))}
//                     </select>
//                 </div>
//
//                 {/* 性别 */} // 对齐：SQL users.sex → DTO.sex
//                 <div>
//                     <label htmlFor="sex" className="block text-sm font-medium mb-1">
//                         性别
//                     </label>
//                     <select
//                         id="sex"
//                         value={formData.sex}
//                         onChange={handleInputChange}
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     >
//                         <option value="请选择">请选择</option>
//                         <option value="男">男</option>
//                         <option value="女">女</option>
//                     </select>
//                 </div>
//
//                 {/* 注册日期 起 */}
//                 <div>
//                     <label htmlFor="startDate" className="block text-sm font-medium mb-1">
//                         注册日期 (起始)
//                     </label>
//                     <DatePicker
//                         selected={formData.startDate}
//                         onChange={d => handleDateChange(d, 'startDate')}
//                         placeholderText="选择开始日期"
//                         dateFormat="yyyy/MM/dd"
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                 </div>
//
//                 {/* 注册日期 止 */}
//                 <div>
//                     <label htmlFor="endDate" className="block text-sm font-medium mb-1">
//                         注册日期 (结束)
//                     </label>
//                     <DatePicker
//                         selected={formData.endDate}
//                         onChange={d => handleDateChange(d, 'endDate')}
//                         placeholderText="选择结束日期"
//                         dateFormat="yyyy/MM/dd"
//                         className="w-full border border-gray-300 rounded-md p-2"
//                     />
//                 </div>
//
//                 {/* 按钮 */}
//                 <div className="col-span-2">
//                     <button
//                         type="submit"
//                         className="mt-6 bg-green-500 text-white px-6 py-2 rounded-md hover:bg-green-600"
//                     >
//                         高级搜索
//                     </button>
//                 </div>
//             </form>
//
//             {showSuccess && (
//                 <div className="mt-4 bg-green-100 text-green-700 p-4 rounded-md">
//                     用户搜索成功！
//                 </div>
//             )}
//
//             {showResults && (
//                 <div className="mt-6">
//                     <h2 className="text-lg font-bold mb-4">搜索结果</h2>
//                     <table className="w-full border border-gray-300 text-sm">
//                         <thead>
//                         <tr className="bg-gray-100">
//                             <th className="border p-2">ID</th>
//                             <th className="border p-2">账号</th> // 对齐：SQL users.account → DTO.account
//                             <th className="border p-2">手机号</th>
//                             <th className="border p-2">邮箱</th>
//                             <th className="border p-2">注册日期</th>
//                             <th className="border p-2">角色</th>
//                             <th className="border p-2">性别</th>
//                             <th className="border p-2">激活状态</th>
//                         </tr>
//                         </thead>
//                         <tbody>
//                         {results.map((r, i) => (
//                             <tr key={i}>
//                                 <td className="border p-2 text-center">{r.id}</td>
//                                 <td className="border p-2 text-center">{r.account}</td>
//                                 <td className="border p-2 text-center">{r.phone}</td>
//                                 <td className="border p-2 text-center">{r.email}</td>
//                                 <td className="border p-2 text-center">
//                                     {new Date(r.createdAt).toLocaleDateString()}
//                                 </td>
//                                 <td className="border p-2 text-center">
//                                     {r.permission?.roles || '无权限'}
//                                 </td>
//                                 <td className="border p-2 text-center">{r.sex}</td>
//                                 <td className="border p-2 text-center">
//                                     {r.isActive ? (
//                                         <span className="text-green-600">已激活</span>
//                                     ) : (
//                                         <span className="text-red-600">未激活</span>
//                                     )}
//                                 </td>
//                             </tr>
//                         ))}
//                         </tbody>
//                     </table>
//                 </div>
//             )}
//         </div>
//     );
// };
//
// export default SearchUser;