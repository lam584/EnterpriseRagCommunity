import * as React from 'react';

const VectorIndexForm: React.FC = () => (
  <div className="bg-white rounded-lg shadow p-4 space-y-4">
    <h3 className="text-lg font-semibold">向量索引构建</h3>
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
      <input className="rounded border px-3 py-2" placeholder="数据集路径或名称" />
      <button className="rounded bg-blue-600 text-white px-4 py-2">构建</button>
    </div>
  </div>
);

export default VectorIndexForm;
