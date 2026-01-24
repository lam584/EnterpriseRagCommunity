// summary.tsx
import React from 'react';

const SummaryForm: React.FC = () => (
  <div className="bg-white rounded-lg shadow p-4 space-y-4">
    <h3 className="text-lg font-semibold">帖子摘要</h3>
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
      <input className="rounded border px-3 py-2" placeholder="输入正文" />
      <button className="rounded bg-blue-600 text-white px-4 py-2">生成摘要</button>
    </div>
  </div>
);

export default SummaryForm;
