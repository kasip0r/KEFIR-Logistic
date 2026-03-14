// src/pages/HomePage.jsx
import React from 'react';
import { Link } from 'react-router-dom';

const HomePage = () => {
  return (
    <div className="min-h-screen flex items-center justify-center">
      {/* ← Убрали bg-gradient-to-br from-purple-600 to-blue-500 */}
      <div className="text-center p-8" style={{ color: '#333' }}>
        <h1 className="text-5xl font-bold mb-4">KEFIR Logistics</h1>
        <p className="text-xl mb-8">Система управления доставками</p>
        
        <div className="space-x-4">
          <Link 
            to="/login" 
            className="inline-block bg-purple-600 text-white px-8 py-3 rounded-lg font-bold hover:bg-purple-700 transition"
          >
            Войти
          </Link>
          <Link 
            to="/register" 
            className="inline-block bg-transparent border-2 border-purple-600 text-purple-600 px-8 py-3 rounded-lg font-bold hover:bg-purple-50 transition"
          >
            Регистрация
          </Link>
        </div>
        
        <div className="mt-12">
          <p className="text-lg mb-4 text-gray-600">Демо аккаунты:</p>
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4 max-w-4xl mx-auto">
            {[
              { role: '👑 Админ', login: 'admin / admin' },
              { role: '🛍️ Клиент', login: 'client / client' },
              { role: '🚴 Курьер', login: 'courier / courier' },
              { role: '📦 Сборщик', login: 'collector / collector' },
              { role: '🏢 Офис', login: 'office / office' },
            ].map((account, index) => (
              <div key={index} className="bg-white/90 backdrop-blur-sm p-4 rounded-lg shadow-lg">
                <div className="text-2xl mb-2">{account.role.split(' ')[0]}</div>
                <div className="text-sm text-gray-600">{account.login}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default HomePage;