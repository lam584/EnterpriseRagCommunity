import  { useState } from 'react';

export default function AssistantChatPage() {
  const [question, setQuestion] = useState('');

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">对话</h3>
        <p className="text-gray-600">这里是 RAG 问答入口表单壳（后续接入对话 API）。</p>
      </div>

      <form className="space-y-2" onSubmit={(e) => e.preventDefault()}>
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={4}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="输入你的问题..."
        />
        <button type="submit" className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700">
          发送
        </button>
      </form>

      <div className="text-sm text-gray-500">当前输入：{question ? `${question.slice(0, 50)}${question.length > 50 ? '…' : ''}` : '（空）'}</div>
    </div>
  );
}

