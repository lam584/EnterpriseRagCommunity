import { useEffect } from 'react';

export type ImageLightboxProps = {
  open: boolean;
  src: string | null;
  alt?: string;
  onClose: () => void;
};

export default function ImageLightbox({ open, src, alt, onClose }: ImageLightboxProps) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open || !src) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      onMouseDown={(e) => {
        // click backdrop to close
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="max-h-[90vh] max-w-[95vw]">
        <img
          src={src}
          alt={alt || 'image'}
          className="max-h-[90vh] max-w-[95vw] object-contain rounded-lg shadow-2xl"
          draggable={false}
        />
      </div>
    </div>
  );
}

