import type {MouseEvent, RefObject} from 'react';
import {Send, Square} from 'lucide-react';

import type {UploadResult} from '../../../../services/uploadService';
import { extractImageFilesFromClipboardData } from '../../../../utils/clipboardImageFiles';

type AssistantChatComposerProps = {
    fileInputRef: RefObject<HTMLInputElement | null>;
    isStreaming: boolean;
    onFilesSelected: (files: File[]) => void;
    onResizeMouseDown: (e: MouseEvent<HTMLDivElement>) => void;
    question: string;
    onQuestionChange: (value: string) => void;
    onPasteImages: (files: File[]) => void;
    onSend: () => void;
    inputHeight: number;
    pendingImages: UploadResult[];
    pendingFiles: UploadResult[];
    maxVisionImages: number;
    maxChatFiles: number;
    onRemovePendingImage: (fileUrl: string) => void;
    onRemovePendingFile: (fileUrl: string) => void;
    effectiveDeepThink: boolean;
    thinkingOnly: boolean;
    onDeepThinkChange: (next: boolean) => void;
    useRag: boolean;
    onUseRagChange: (next: boolean) => void;
    tokensTotals: { inStr: string; outStr: string; totalStr: string };
    onPickImages: () => void;
    imageUploading: boolean;
    canSend: boolean;
    onStop: () => void;
};

export function AssistantChatComposer(props: AssistantChatComposerProps) {
    const {
        fileInputRef,
        isStreaming,
        onFilesSelected,
        onResizeMouseDown,
        question,
        onQuestionChange,
        onPasteImages,
        onSend,
        inputHeight,
        pendingImages,
        pendingFiles,
        maxVisionImages,
        maxChatFiles,
        onRemovePendingImage,
        onRemovePendingFile,
        effectiveDeepThink,
        thinkingOnly,
        onDeepThinkChange,
        useRag,
        onUseRagChange,
        tokensTotals,
        onPickImages,
        imageUploading,
        canSend,
        onStop,
    } = props;

    return (
        <form
            className="space-y-2"
            onSubmit={(e) => {
                e.preventDefault();
                onSend();
            }}
        >
            <input
                ref={fileInputRef}
                type="file"
                accept=".png,.jpg,.jpeg,.gif,.webp,.bmp,.svg,.pdf,.txt,.md,.html,.htm,.epub,.doc,.docx,.ppt,.pptx,.xls,.xlsx"
                multiple
                className="hidden"
                onChange={(e) => {
                    const files = Array.from(e.target.files ?? []);
                    e.target.value = '';
                    onFilesSelected(files);
                }}
                disabled={isStreaming}
            />
            <div
                onMouseDown={onResizeMouseDown}
                className="h-1.5 w-full cursor-row-resize hover:bg-blue-400/30 transition-colors flex items-center justify-center group"
                title="拖动调整高度"
            >
                <div className="w-12 h-0.5 rounded-full bg-gray-300 group-hover:bg-blue-400 transition-colors"/>
            </div>
            <textarea
                value={question}
                onChange={(e) => {
                    onQuestionChange(e.target.value);
                }}
                onPaste={(e) => {
                    const files = extractImageFilesFromClipboardData(e.clipboardData);
                    if (files.length === 0) return;
                    onPasteImages(files);
                }}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        onSend();
                    }
                }}
                style={{height: inputHeight}}
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                placeholder="输入你的问题..."
                disabled={isStreaming}
            />
            {pendingImages.length ? (
                <div className="flex gap-2 overflow-x-auto">
                    {pendingImages.slice(0, maxVisionImages).map((it) => (
                        <div key={`${it.id}-${it.fileUrl}`}
                             className="shrink-0 relative w-16 h-16 rounded border overflow-hidden bg-gray-50">
                            <img src={it.fileUrl} alt={it.fileName} className="w-full h-full object-cover"
                                 loading="lazy"/>
                            <button
                                type="button"
                                className="absolute top-0 right-0 px-1 py-0.5 text-[10px] bg-white/90 border border-gray-200 rounded-bl"
                                onClick={() => onRemovePendingImage(it.fileUrl)}
                                disabled={isStreaming}
                            >
                                删除
                            </button>
                        </div>
                    ))}
                </div>
            ) : null}

            {pendingFiles.length ? (
                <div className="flex flex-wrap gap-2">
                    {pendingFiles.slice(0, maxChatFiles).map((it) => (
                        <div
                            key={`${it.id}-${it.fileUrl}`}
                            className="inline-flex items-center gap-2 rounded border border-gray-200 bg-gray-50 px-2 py-1 text-xs text-gray-700"
                        >
                            <span className="max-w-[260px] truncate">{it.fileName}</span>
                            <button
                                type="button"
                                className="text-gray-600 hover:text-gray-900"
                                onClick={() => onRemovePendingFile(it.fileUrl)}
                                disabled={isStreaming}
                            >
                                ×
                            </button>
                        </div>
                    ))}
                </div>
            ) : null}

            <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-4">
                    <label className="inline-flex items-center gap-2 text-sm text-gray-600">
                        <input
                            type="checkbox"
                            checked={effectiveDeepThink}
                            disabled={thinkingOnly}
                            onChange={(e) => {
                                onDeepThinkChange(e.target.checked);
                            }}
                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        />
                        深度思考{thinkingOnly ? <span className="text-xs text-gray-500"></span> : null}
                    </label>
                    <label className="inline-flex items-center gap-2 text-sm text-gray-600">
                        <input
                            type="checkbox"
                            checked={useRag}
                            onChange={(e) => {
                                onUseRagChange(e.target.checked);
                            }}
                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        />
                        使用RAG功能
                    </label>
                </div>
                <div className="flex items-center gap-2">
                    <div className="text-xs text-gray-600">
                        Token：in {tokensTotals.inStr} / out {tokensTotals.outStr} / total {tokensTotals.totalStr}
                    </div>
                    <button
                        type="button"
                        className="inline-flex items-center gap-2 px-3 py-2 rounded-md border border-gray-300 bg-white text-sm hover:bg-gray-50 disabled:opacity-60"
                        onClick={onPickImages}
                        disabled={isStreaming || imageUploading}
                    >
                        {imageUploading ? '上传中…' : '添加文件/图片'}
                    </button>
                    {isStreaming ? (
                        <button
                            type="button"
                            onClick={onStop}
                            className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-gray-200 hover:bg-gray-300 transition-colors"
                        >
                            <Square size={16} fill="currentColor"/>
                            停止输出
                        </button>
                    ) : (
                        <button
                            type="submit"
                            disabled={!canSend}
                            className={
                                'inline-flex items-center gap-2 px-4 py-2 rounded-md text-white transition-colors ' +
                                (canSend ? 'bg-blue-600 hover:bg-blue-700' : 'bg-blue-300 cursor-not-allowed')
                            }
                        >
                            <Send size={16}/>
                            发送
                        </button>
                    )}
                </div>
            </div>
        </form>
    );
}
