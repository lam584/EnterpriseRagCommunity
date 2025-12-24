import {useState} from "react";

export default function AccountProfilePage() {
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">个人资料</h3>
        <p className="text-gray-600">这里放个人资料编辑表单（昵称/头像/简介等）。</p>
      </div>

      <form className="space-y-3" onSubmit={(e) => e.preventDefault()}>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">昵称</label>
          <input
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="输入昵称..."
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">简介</label>
          <textarea
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            rows={4}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="一句话介绍自己..."
          />
        </div>

        <button type="submit" className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700">
          保存
        </button>
      </form>
    </div>
  );
}
