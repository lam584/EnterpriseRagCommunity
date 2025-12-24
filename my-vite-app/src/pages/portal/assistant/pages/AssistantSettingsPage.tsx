import {useState} from "react";


export default function AssistantSettingsPage() {
  const [topK, setTopK] = useState(5);
  const [stream, setStream] = useState(true);

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">设置</h3>
        <p className="text-gray-600">这里放检索参数、模型偏好等设置表单占位。</p>
      </div>

      <form className="space-y-3" onSubmit={(e) => e.preventDefault()}>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">TopK（检索条数）</label>
          <input
            type="number"
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
            min={1}
            max={50}
            className="w-40 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={stream} onChange={(e) => setStream(e.target.checked)} />
          流式输出
        </label>

        <button type="submit" className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700">
          保存
        </button>
      </form>
    </div>
  );
}

