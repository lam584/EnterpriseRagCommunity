import {useCallback, useEffect, useState, type MouseEvent as ReactMouseEvent} from 'react';

export function useResizableInputHeight(initial = 120, min = 80, max = 600) {
    const [inputHeight, setInputHeight] = useState<number>(initial);
    const [isResizing, setIsResizing] = useState(false);

    const handleResizeMouseDown = useCallback((e: ReactMouseEvent) => {
        e.preventDefault();
        setIsResizing(true);
    }, []);

    useEffect(() => {
        const handleMouseMove = (e: MouseEvent) => {
            if (!isResizing) return;
            // 向上拖动（movementY 为负）增加高度
            setInputHeight((prev) => {
                const next = prev - e.movementY;
                return Math.max(min, Math.min(max, next));
            });
        };

        const handleMouseUp = () => {
            setIsResizing(false);
        };

        if (isResizing) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);
            document.body.style.cursor = 'row-resize';
            document.body.style.userSelect = 'none';
        } else {
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }

        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        };
    }, [isResizing, min, max]);

    return {inputHeight, handleResizeMouseDown};
}
