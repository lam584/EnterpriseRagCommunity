import {useEffect, useState, type RefObject} from 'react';

export function useEditorAutoHeight(wrapRef: RefObject<HTMLDivElement | null>) {
    const [heightPx, setHeightPx] = useState<number | null>(null);

    useEffect(() => {
        let raf = 0;

        const update = () => {
            cancelAnimationFrame(raf);
            raf = window.requestAnimationFrame(() => {
                const wrap = wrapRef.current;
                if (!wrap) return;

                const textarea = wrap.querySelector('textarea');
                const top = (textarea ?? wrap).getBoundingClientRect().top;
                const bottomReservePx = 220;
                const minPx = 240;
                const maxPx = 1200;
                const next = Math.floor(window.innerHeight - top - bottomReservePx);
                const clamped = Math.max(minPx, Math.min(maxPx, next));
                setHeightPx((prev) => (prev === clamped ? prev : clamped));
            });
        };

        update();
        window.addEventListener('resize', update);
        window.addEventListener('scroll', update, true);
        return () => {
            cancelAnimationFrame(raf);
            window.removeEventListener('resize', update);
            window.removeEventListener('scroll', update, true);
        };
    }, [wrapRef]);

    return heightPx;
}
