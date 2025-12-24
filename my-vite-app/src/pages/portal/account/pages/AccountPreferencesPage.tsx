import { useState } from 'react';

export default function AccountPreferencesPage() {
  const [compact, setCompact] = useState(true);
  const [emailNoti, setEmailNoti] = useState(false);

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">偏好</h3>
        <p className="text-gray-600">这里放展示、通知等偏好设置表单。</p>
      </div>

      <form className="space-y-3" onSubmit={(e) => e.preventDefault()}>
        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={compact} onChange={(e) => setCompact(e.target.checked)} />
          紧凑模式
        </label>
        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={emailNoti} onChange={(e) => setEmailNoti(e.target.checked)} />
          邮件通知
        </label>

        <button type="submit" className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700">
          保存
        </button>
      </form>
    </div>
  );
}

