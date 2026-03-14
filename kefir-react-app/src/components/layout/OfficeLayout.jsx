// src/components/layout/OfficeLayout.jsx
import React from 'react';

<<<<<<< HEAD
const OfficeLayout = ({ children, onLogout }) => {
  return (
    <div className="min-h-screen"> {/* ← Убрали bg-white */}
=======
const OfficeLayout = ({ children }) => {
  return (
    <div className="min-h-screen bg-white">
>>>>>>> 7a3aa214dca64c69999070de7cdb7b131cb5bada
      {/* Тонкая черная полоска вместо фиолетовой шапки */}
      <div className="h-1 bg-black"></div>
      <div className="h-[calc(100vh-4px)]">
        {children}
      </div>
    </div>
  );
};

export default OfficeLayout;