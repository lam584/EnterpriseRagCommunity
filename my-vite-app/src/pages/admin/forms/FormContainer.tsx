import React from 'react';

// A reusable container for admin forms area
const FormContainer: React.FC<React.PropsWithChildren> = ({ children }) => (
  <div className="bg-[rgb(221,221,221)] rounded-lg w-full flex-1 min-h-0 overflow-auto px-8 pb-8 pt-0 first:pt-8">
    {children}
  </div>
);

export default FormContainer;
