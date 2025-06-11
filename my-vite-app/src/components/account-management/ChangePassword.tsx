import React, { useState } from 'react';
import { changePassword } from '../../services/accountService';
import { toast } from 'react-hot-toast';

interface ChangePasswordProps {
  onBack: () => void;
}

const ChangePassword: React.FC<ChangePasswordProps> = ({ onBack }) => {
  const [formData, setFormData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    // 清除对应字段的错误
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    // 验证当前密码
    if (!formData.currentPassword) {
      newErrors.currentPassword = '请输入当前密码';
    }

    // 验证新密码
    if (!formData.newPassword) {
      newErrors.newPassword = '请输入新密码';
    } else if (formData.newPassword.length < 8) {
      newErrors.newPassword = '密码长度至少为8个字符';
    } else if (!/[A-Z]/.test(formData.newPassword)) {
      newErrors.newPassword = '密码应至少包含一个大写字母';
    } else if (!/[a-z]/.test(formData.newPassword)) {
      newErrors.newPassword = '密码应至少包含一个小写字母';
    } else if (!/[0-9]/.test(formData.newPassword)) {
      newErrors.newPassword = '密码应至少包含一个数字';
    } else if (!/[^A-Za-z0-9]/.test(formData.newPassword)) {
      newErrors.newPassword = '密码应至少包含一个特殊字符';
    }

    // 验证确认密码
    if (!formData.confirmPassword) {
      newErrors.confirmPassword = '请确认新密码';
    } else if (formData.confirmPassword !== formData.newPassword) {
      newErrors.confirmPassword = '两次输入的密码不一致';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    try {
      setLoading(true);
      await changePassword({
        currentPassword: formData.currentPassword,
        newPassword: formData.newPassword
      });

      toast.success('密码修改成功');

      // 清空表单
      setFormData({
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      });

    } catch (err) {
      console.error('Failed to change password:', err);
      toast.error(err instanceof Error ? err.message : '密码修改失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-lg mx-auto bg-white p-6 rounded-lg shadow-md">
      <h2 className="text-xl font-bold mb-4">更改密码</h2>

      <form onSubmit={handleSubmit}>
        <div className="mb-4">
          <label htmlFor="currentPassword" className="block text-gray-700 font-medium mb-1">当前密码：</label>
          <input
            type="password"
            id="currentPassword"
            name="currentPassword"
            value={formData.currentPassword}
            onChange={handleChange}
            className={`w-full border ${errors.currentPassword ? 'border-red-500' : 'border-gray-300'} rounded-md p-2`}
            placeholder="请输入当前密码"
          />
          {errors.currentPassword && (
            <p className="text-red-500 text-xs mt-1">{errors.currentPassword}</p>
          )}
        </div>

        <div className="mb-4">
          <label htmlFor="newPassword" className="block text-gray-700 font-medium mb-1">新密码：</label>
          <input
            type="password"
            id="newPassword"
            name="newPassword"
            value={formData.newPassword}
            onChange={handleChange}
            className={`w-full border ${errors.newPassword ? 'border-red-500' : 'border-gray-300'} rounded-md p-2`}
            placeholder="请输入新密码"
          />
          {errors.newPassword ? (
            <p className="text-red-500 text-xs mt-1">{errors.newPassword}</p>
          ) : (
            <p className="text-gray-500 text-xs mt-1">
              密码应至少包含8个字符，包括大写字母、小写字母、数字和特殊字符
            </p>
          )}
        </div>

        <div className="mb-4">
          <label htmlFor="confirmPassword" className="block text-gray-700 font-medium mb-1">确认新密码：</label>
          <input
            type="password"
            id="confirmPassword"
            name="confirmPassword"
            value={formData.confirmPassword}
            onChange={handleChange}
            className={`w-full border ${errors.confirmPassword ? 'border-red-500' : 'border-gray-300'} rounded-md p-2`}
            placeholder="请再次输入新密码"
          />
          {errors.confirmPassword && (
            <p className="text-red-500 text-xs mt-1">{errors.confirmPassword}</p>
          )}
        </div>

        <div className="flex justify-end space-x-4 mt-6">
          <button
            onClick={onBack}
            className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600 disabled:bg-gray-400"
          >
            返回
          </button>
          <button
            type="submit"
            className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
            disabled={loading}
          >
            {loading ? '提交中...' : '更改密码'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ChangePassword;