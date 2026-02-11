import React, { useEffect } from 'react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  showFooterClose?: boolean;
}

const Modal: React.FC<ModalProps> = ({ isOpen, onClose, title, children, showFooterClose = true }) => {
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-x-hidden overflow-y-auto outline-none focus:outline-none">
      <div className="fixed inset-0 bg-black opacity-50" onClick={onClose}></div>
      <div className="relative w-full max-w-lg mx-auto my-6 bg-white rounded-lg shadow-lg outline-none focus:outline-none z-50">
        <div className="flex items-start justify-between p-5 border-b border-solid border-gray-300 rounded-t">
          <h3 className="text-xl font-semibold">{title}</h3>
          <button
            className="p-1 ml-auto bg-transparent border-0 text-black float-right text-3xl leading-none font-semibold outline-none focus:outline-none opacity-50 hover:opacity-100"
            onClick={onClose}
          >
            <span className="bg-transparent text-black h-6 w-6 text-2xl block outline-none focus:outline-none">
              ×
            </span>
          </button>
        </div>
        <div className="relative p-6 flex-auto max-h-[70vh] overflow-y-auto text-gray-700 leading-relaxed">
            {children}
        </div>
        {showFooterClose ? (
          <div className="flex items-center justify-end p-6 border-t border-solid border-gray-300 rounded-b">
            <button
              className="text-white bg-blue-500 hover:bg-blue-600 font-bold uppercase px-6 py-2 text-sm outline-none focus:outline-none mr-1 mb-1 ease-linear transition-all duration-150 rounded"
              type="button"
              onClick={onClose}
            >
              关闭
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default Modal;
