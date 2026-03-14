// src/components/layout/OfficeLayout.jsx
import React from 'react';

const OfficeLayout = ({ children, onLogout }) => {
  return (
    <div className="min-h-screen"> {/* ← Убрали bg-white */}
      {/* Тонкая черная полоска вместо фиолетовой шапки */}
      <div className="h-1 bg-black"></div>
      <div className="h-[calc(100vh-4px)]">
        {children}
      </div>
    </div>
  );
};

export default OfficeLayout;