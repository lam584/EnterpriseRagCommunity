import React from 'react';

export type StatButtonProps = {
  icon?: React.ReactNode;
  label?: string;
  count?: number;
  active?: boolean;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  className?: string;
};

export const StatButton: React.FC<StatButtonProps> = ({
  icon,
  label,
  count,
  active,
  disabled,
  onClick,
  className = '',
}) => {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={
        `inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm transition-colors ` +
        `${disabled ? 'opacity-60 cursor-not-allowed' : 'hover:bg-gray-100'} ` +
        `${active ? 'text-blue-600' : 'text-gray-600'} ` +
        className
      }
    >
      {icon ? <span className="text-base">{icon}</span> : null}
      {label ? <span>{label}</span> : null}
      {typeof count === 'number' ? <span className="tabular-nums">{count}</span> : null}
    </button>
  );
};

export default StatButton;

