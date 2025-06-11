import React, { useState } from 'react';
import { AdminAccountInfo, updateAccountInfo } from '../../services/accountService';
import { toast } from 'react-hot-toast'; // 假设使用react-hot-toast
interface EditProfileProps {
  userData: AdminAccountInfo;
  onBack: () => void;
}

const EditProfile: React.FC<EditProfileProps> = ({ userData, onBack }) => {
  const [formData, setFormData] = useState({
    phone: userData.phone || '',
    email: userData.email || '',
    sex: userData.sex || '男',
  });
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      // 只发送已修改的字段
      const updatedFields: Record<string, string> = {};
      if (formData.phone !== userData.phone) updatedFields.phone = formData.phone;
      if (formData.email !== userData.email) updatedFields.email = formData.email;
      if (formData.sex !== userData.sex) updatedFields.sex = formData.sex;

      if (Object.keys(updatedFields).length === 0) {
        toast('没有内容被更改', { icon: 'ℹ️' });
        return;
      }

      await updateAccountInfo(updatedFields);
      toast.success('个人信息更新成功');

      // 可以添加刷新页面或更新父组件数据的逻辑
      // 例如通过props传递回调函数或使用context
      setTimeout(() => {
        window.location.reload(); // 简单起见，这里直接刷新页面
      }, 1500);

    } catch (err) {
      console.error('Failed to update profile:', err);
      toast.error(err instanceof Error ? err.message : '更新个人信息失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-lg mx-auto bg-white p-6 rounded-lg shadow-md">
      <h2 className="text-xl font-bold mb-4">编辑个人信息</h2>

      <form onSubmit={handleSubmit}>
        <div className="mb-4">
          <label htmlFor="account" className="block text-gray-700 font-medium mb-1">用户名：</label>
          <input
            type="text"
            id="account"
            value={userData.account}
            className="w-full border border-gray-300 rounded-md p-2 bg-gray-100"
            readOnly
          />
          <p className="text-xs text-gray-500 mt-1">用户名不可更改</p>
        </div>

        <div className="mb-4">
          <label htmlFor="email" className="block text-gray-700 font-medium mb-1">电子邮件：</label>
          <input
            type="email"
            id="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            className="w-full border border-gray-300 rounded-md p-2"
            required
          />
        </div>

        <div className="mb-4">
          <label htmlFor="phone" className="block text-gray-700 font-medium mb-1">手机号：</label>
          <input
            type="text"
            id="phone"
            name="phone"
            value={formData.phone}
            onChange={handleChange}
            className="w-full border border-gray-300 rounded-md p-2"
            pattern="[0-9]{11}"
            title="请输入11位有效手机号码"
            required
          />
        </div>

        <div className="mb-4">
          <label htmlFor="sex" className="block text-gray-700 font-medium mb-1">性别：</label>
          <select
            id="sex"
            name="sex"
            value={formData.sex}
            onChange={handleChange}
            className="w-full border border-gray-300 rounded-md p-2"
          >
            <option value="男">男</option>
            <option value="女">女</option>
          </select>
        </div>

        <div className="flex justify-end space-x-4 mt-6">
          <button onClick={onBack} className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600 disabled:bg-gray-400">返回</button>
          <button
            type="submit"
            className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
            disabled={loading}
          >
            {loading ? '保存中...' : '保存更改'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default EditProfile;