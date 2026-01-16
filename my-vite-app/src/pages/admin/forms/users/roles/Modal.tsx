import React from 'react';

type ModalProps = {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
};

const Modal: React.FC<ModalProps> = ({isOpen, onClose, title, children}) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
            {/*
        Bigger "configure permissions" area:
        - near full-screen width/height on large screens
        - keep safe padding on small screens
        - larger base font for readability
      */}
            <div className="bg-white rounded-lg shadow-lg w-full max-w-[60vw] max-h-[98vh] overflow-hidden p-6 text-base">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-xl font-semibold">{title}</h3>
                    <button onClick={onClose} className="text-gray-500 hover:text-gray-700 text-2xl leading-none">
                        &times;
                    </button>
                </div>
                <div className="max-h-[calc(98vh)] overflow-auto pr-2">{children}</div>
            </div>
        </div>
    );
};

export default Modal;
export type {ModalProps};

