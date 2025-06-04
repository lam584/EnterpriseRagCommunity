// src/components/account-management/ChangePassword.tsx
import React, { useState } from 'react';

const ChangePassword: React.FC = () => {
  const [formData, setFormData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  });
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<boolean>(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, value } = e.target;
    setFormData({
      ...formData,
      [id]: value
    });
    setError(null);
  };

  const validatePassword = () => {
    // 检查新密码是否满足复杂性要求
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!passwordRegex.test(formData.newPassword)) {
      setError('新密码必须至少包含8个字符，包括大写字母、小写字母、数字和特殊字符');
      return false;
    }

    // 检查两次输入的新密码是否一致
    if (formData.newPassword !== formData.confirmNewPassword) {
      setError('两次输入的新密码不一致');
      return false;
    }

    return true;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (validatePassword()) {
      // 这里可以添加密码更改的API调用
      setSuccess(true);
      setFormData({
        currentPassword: '',
        newPassword: '',
        confirmNewPassword: ''
      });
    }
  };

  return (
    <div className="bg-white shadow-md rounded-md p-6">
      <h2 className="text-xl font-bold mb-4">更改密码</h2>
      <div className="bg-gray-50 p-6 rounded-lg">
        {success ? (
          <div className="text-center">
            <div className="text-green-500 text-xl mb-4">✓</div>
            <p className="text-gray-700 mb-4">密码已成功更改！</p>
            <button
              className="bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600"
              onClick={() => setSuccess(false)}
            >
              继续
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative mb-4">
                <span>{error}</span>
              </div>
            )}
            <div className="mb-4">
              <label htmlFor="currentPassword" className="block text-gray-700 font-medium mb-1">当前密码：</label>
              <input
                type="password"
                id="currentPassword"
                value={formData.currentPassword}
                onChange={handleChange}
                className="w-full border border-gray-300 rounded-md p-2"
                required
              />
            </div>
            <div className="mb-4">
              <label htmlFor="newPassword" className="block text-gray-700 font-medium mb-1">新密码：</label>
              <input
                type="password"
                id="newPassword"
                value={formData.newPassword}
                onChange={handleChange}
                className="w-full border border-gray-300 rounded-md p-2"
                required
              />
            </div>
            <div className="mb-4">
              <label htmlFor="confirmNewPassword" className="block text-gray-700 font-medium mb-1">确认新密码：</label>
              <input
                type="password"
                id="confirmNewPassword"
                value={formData.confirmNewPassword}
                onChange={handleChange}
                className="w-full border border-gray-300 rounded-md p-2"
                required
              />
            </div>
            <div className="flex justify-end space-x-4">
              <button
                type="submit"
                className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
              >
                更改密码
              </button>
            </div>
          </form>
        )}
        <p className="text-gray-500 text-sm mt-6">
          密码应至少包括大写字母、小写字母、数字、符号，以提高您对您的账户安全性的控制。
        </p>
      </div>
    </div>
  );
};

export default ChangePassword;
