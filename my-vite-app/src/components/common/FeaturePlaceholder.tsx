// filepath: e:\EnterpriseRagCommunity-main\my-vite-app\src\components\common\FeaturePlaceholder.tsx
import React from 'react';

interface FeaturePlaceholderProps {
  title: string;
  description?: string;
  tips?: string;
}

const FeaturePlaceholder: React.FC<FeaturePlaceholderProps> = ({ title, description, tips }) => {
  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-semibold mb-2">{title}</h1>
      {description && <p className="text-gray-600 mb-4">{description}</p>}
      <div className="rounded-md border border-dashed p-6 bg-white">
        <p className="text-gray-700">该页面为功能占位，按“功能清单（精简终版）”重构导航与信息架构。</p>
        {tips && <p className="text-gray-500 mt-2 text-sm">{tips}</p>}
      </div>
    </div>
  );
};

export default FeaturePlaceholder;

