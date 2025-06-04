// src/components/account-management/EditProfile.tsx
import React, { useState } from 'react';

interface EditProfileProps {
  userData: {
    id: string;
    username: string;
    email: string;
    phone: string;
    gender: string;
  };
}

const EditProfile: React.FC<EditProfileProps> = ({ userData }) => {
  const [formData, setFormData] = useState({
    email: userData.email,
    phone: userData.phone,
    gender: userData.gender
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { id, value } = e.target;
    setFormData({
      ...formData,
      [id]: value
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // 这里可以添加表单提交逻辑，例如API调用
    alert('个人信息更新成功！');
  };

  return (
    <div className="bg-white shadow-md rounded-md p-6">
      <h2 className="text-xl font-bold mb-4">编辑个人信息</h2>
      <div className="bg-gray-50 p-6 rounded-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="mb-4">
            <label htmlFor="id" className="block text-gray-700 font-medium mb-1">id:</label>
            <input
              type="text"
              id="id"
              value={userData.id}
              className="w-full border border-gray-300 rounded-md p-2 bg-gray-100"
              readOnly
            />
          </div>
          <div className="mb-4">
            <label htmlFor="username" className="block text-gray-700 font-medium mb-1">用户名：</label>
            <input
              type="text"
              id="username"
              value={userData.username}
              className="w-full border border-gray-300 rounded-md p-2 bg-gray-100"
              readOnly
            />
          </div>
          <div className="mb-4">
            <label htmlFor="email" className="block text-gray-700 font-medium mb-1">电子邮件：</label>
            <input
              type="email"
              id="email"
              value={formData.email}
              onChange={handleChange}
              className="w-full border border-gray-300 rounded-md p-2"
            />
          </div>
          <div className="mb-4">
            <label htmlFor="phone" className="block text-gray-700 font-medium mb-1">手机号：</label>
            <input
              type="text"
              id="phone"
              value={formData.phone}
              onChange={handleChange}
              className="w-full border border-gray-300 rounded-md p-2"
            />
          </div>
          <div className="mb-4">
            <label htmlFor="gender" className="block text-gray-700 font-medium mb-1">性别：</label>
            <select
              id="gender"
              value={formData.gender}
              onChange={handleChange}
              className="w-full border border-gray-300 rounded-md p-2"
            >
              <option>男</option>
              <option>女</option>
            </select>
          </div>
          <div className="flex justify-end space-x-4">
            <button
              type="submit"
              className="bg-green-500 text-white px-4 py-2 rounded-md hover:bg-green-600"
            >
              保存更改
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditProfile;
