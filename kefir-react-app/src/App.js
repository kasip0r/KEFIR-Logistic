// src/App.js
import React, { useState, useEffect, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import './styles/global.css';

// Import API service
import apiService from './services/api';

// Layout Components
import MainLayout from './components/layout/MainLayout';
import OfficeLayout from './components/layout/OfficeLayout';
import CollectorLayout from './components/layout/CollectorLayout';

// Auth Pages
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';

// Client Pages
import ClientPortal from './pages/client/ClientPortal';
import ClientCart from './pages/client/ClientCart';
import ClientProfile from './pages/client/ClientProfile';
import SupportPage from './pages/client/SupportPage';

// Admin Pages
import AdminDashboard from './pages/admin/Dashboard';
import AdminClients from './pages/admin/Clients';
import AdminProducts from './pages/admin/Products';
import AdminCarts from './pages/admin/Carts';
import AdminWarehouse from './pages/admin/Warehouse';
import AdminCouriers from './pages/admin/Couriers';
import AdminDeliveries from './pages/admin/Deliveries';

// Office Pages
import OfficePage from './pages/office/OfficePage';
import OfficeDeliveries from './pages/office/OfficeDeliveries';
import OfficeOrders from './pages/office/OfficeOrders';
import OfficeReports from './pages/office/OfficeReports';

// Worker Pages
import CourierApp from './pages/courier/CourierApp';
import CollectorApp from './pages/collector/CollectorApp';

// Константы ролей
const ROLES = {
  ADMIN: 'admin',
  CLIENT: 'client',
  COURIER: 'courier',
  COLLECTOR: 'collector',
  OFFICE: 'office',
};

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(null);
  const [userRole, setUserRole] = useState('');
  const [userData, setUserData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const [initialized, setInitialized] = useState(false);

  // Функция для нормализации роли
  const normalizeRole = useCallback((role) => {
    if (!role) return '';
    return String(role).toLowerCase().trim();
  }, []);

  // Очистка данных авторизации
  const clearAuthData = useCallback(() => {
    setIsAuthenticated(false);
    setUserRole('');
    setUserData(null);
    setError(null);
    apiService.utils.logout();
  }, []);

  // Проверка авторизации по localStorage
  const checkAuthFromStorage = useCallback(() => {
    const user = apiService.utils.getUser();
    
    if (user) {
      const normalizedRole = normalizeRole(user.role);
      
      setIsAuthenticated(true);
      setUserData(user);
      setUserRole(normalizedRole);
      setError(null);
      
      return true;
    } else {
      setIsAuthenticated(false);
      return false;
    }
  }, [normalizeRole]);

  // Эффект для инициализации
  useEffect(() => {
    const token = apiService.utils.getToken();
    if (token) {
      checkAuthFromStorage();
    } else {
      setIsAuthenticated(false);
    }
    
    // Имитация загрузки
    setTimeout(() => {
      setLoading(false);
      setInitialized(true);
    }, 500);
  }, [checkAuthFromStorage]);

  // Обработчик входа
  const handleLogin = async (credentials) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await apiService.authAPI.login(credentials);
      
      const user = response.user;
      const normalizedRole = normalizeRole(user.role);
      
      setIsAuthenticated(true);
      setUserData(user);
      setUserRole(normalizedRole);
      
      return { success: true, user, role: normalizedRole };
      
    } catch (error) {
      console.error('Login error:', error);
      
      let errorMessage = 'Ошибка входа';
      
      if (error.response) {
        const data = error.response.data;
        if (data.error === 'Неверный логин или пароль') {
          errorMessage = 'Неверное имя пользователя или пароль';
        } else if (data.status === 'banned') {
          const bannedError = new Error('Ваш аккаунт заблокирован');
          bannedError.isBanned = true;
          throw bannedError;
        } else {
          errorMessage = data.error || data.message || errorMessage;
        }
      } else if (error.request) {
        errorMessage = 'Сервер недоступен. Проверьте подключение.';
      } else {
        errorMessage = error.message || errorMessage;
      }
      
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Обработчик выхода
  const handleLogout = useCallback(async () => {
    try {
      await apiService.authAPI.logout();
    } catch (logoutError) {
      console.log('Logout error:', logoutError);
    } finally {
      clearAuthData();
      window.location.href = '/login';
    }
  }, [clearAuthData]);

  // Функция для получения редиректа по роли
  const getRoleRedirect = useCallback((role) => {
    if (!role) return '/login';
    
    const normalizedRole = normalizeRole(role);
    
    switch(normalizedRole) {
      case ROLES.ADMIN: return '/admin';
      case ROLES.COURIER: return '/courier';
      case ROLES.COLLECTOR: return '/collector';
      case ROLES.OFFICE: return '/office';
      case ROLES.CLIENT: return '/client';
      default: return '/login';
    }
  }, [normalizeRole]);

  // Защищенный роут компонент
  const ProtectedRoute = ({ children, allowedRoles = [] }) => {
    if (!initialized || isAuthenticated === null || loading) {
      return (
        <div className="flex-center" style={{ 
          height: '100vh',
          background: 'transparent'  // ← ИЗМЕНЕНО: прозрачный фон
        }}>
          <div className="text-center" style={{ color: '#333' }}>
            <div style={{
              width: '50px',
              height: '50px',
              margin: '0 auto 20px',
              border: '3px solid rgba(51, 51, 51, 0.3)',
              borderTop: '3px solid #333',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite'
            }} />
            <div>Загрузка приложения...</div>
          </div>
        </div>
      );
    }

    if (!isAuthenticated) {
      return <Navigate to="/login" replace />;
    }

    const normalizedUserRole = normalizeRole(userRole);
    const normalizedAllowedRoles = allowedRoles.map(role => normalizeRole(role));

    if (normalizedAllowedRoles.length > 0 && !normalizedAllowedRoles.includes(normalizedUserRole)) {
      const redirectPath = getRoleRedirect(normalizedUserRole);
      return <Navigate to={redirectPath} replace />;
    }

    return (
      <MainLayout 
        userRole={normalizedUserRole}
        userData={userData}
        onLogout={handleLogout}
      >
        {children}
      </MainLayout>
    );
  };

  // Защищенный роут для Office
  const ProtectedOfficeRoute = ({ children, allowedRoles = [ROLES.OFFICE] }) => {
    if (!initialized || isAuthenticated === null || loading) {
      return (
        <div className="flex-center" style={{ 
          height: '100vh',
          background: 'transparent'  // ← ИЗМЕНЕНО: прозрачный фон
        }}>
          <div className="text-center" style={{ color: '#333' }}>
            <div style={{
              width: '50px',
              height: '50px',
              margin: '0 auto 20px',
              border: '3px solid rgba(51, 51, 51, 0.3)',
              borderTop: '3px solid #333',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite'
            }} />
            <div>Загрузка приложения...</div>
          </div>
        </div>
      );
    }

    if (!isAuthenticated) {
      return <Navigate to="/login" replace />;
    }

    const normalizedUserRole = normalizeRole(userRole);
    const normalizedAllowedRoles = allowedRoles.map(role => normalizeRole(role));

    if (normalizedAllowedRoles.length > 0 && !normalizedAllowedRoles.includes(normalizedUserRole)) {
      const redirectPath = getRoleRedirect(normalizedUserRole);
      return <Navigate to={redirectPath} replace />;
    }

    return (
      <OfficeLayout onLogout={handleLogout}>
        {children}
      </OfficeLayout>
    );
  };

  // Защищенный роут для Collector
  const ProtectedCollectorRoute = ({ children, allowedRoles = [ROLES.COLLECTOR] }) => {
    if (!initialized || isAuthenticated === null || loading) {
      return (
        <div className="flex-center" style={{ 
          height: '100vh',
          background: 'transparent'  // ← ИЗМЕНЕНО: прозрачный фон
        }}>
          <div className="text-center" style={{ color: '#333' }}>
            <div style={{
              width: '50px',
              height: '50px',
              margin: '0 auto 20px',
              border: '3px solid rgba(51, 51, 51, 0.3)',
              borderTop: '3px solid #333',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite'
            }} />
            <div>Загрузка приложения...</div>
          </div>
        </div>
      );
    }

    if (!isAuthenticated) {
      return <Navigate to="/login" replace />;
    }

    const normalizedUserRole = normalizeRole(userRole);
    const normalizedAllowedRoles = allowedRoles.map(role => normalizeRole(role));

    if (normalizedAllowedRoles.length > 0 && !normalizedAllowedRoles.includes(normalizedUserRole)) {
      const redirectPath = getRoleRedirect(normalizedUserRole);
      return <Navigate to={redirectPath} replace />;
    }

    return (
      <CollectorLayout onLogout={handleLogout}>
        {children}
      </CollectorLayout>
    );
  };

  // Лоадер при начальной загрузке
  if (loading && isAuthenticated === null) {
    return (
      <div className="flex-center" style={{ 
        height: '100vh',
        background: 'transparent'  // ← ИЗМЕНЕНО: прозрачный фон
      }}>
        <div className="text-center" style={{ color: '#333' }}>
          <div style={{
            width: '50px',
            height: '50px',
            margin: '0 auto 20px',
            border: '3px solid rgba(51, 51, 51, 0.3)',
            borderTop: '3px solid #333',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite'
          }} />
          <div>Загрузка приложения...</div>
        </div>
        <style>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  return (
    <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
       <div className="App"> 
        {error && (
          <div className="error-banner">
            <span>❌ {error}</span>
            <button onClick={() => setError(null)}>×</button>
          </div>
        )}
        
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={
            isAuthenticated && initialized ? (
              <Navigate to={getRoleRedirect(userRole)} replace />
            ) : (
              <Login onLogin={handleLogin} loading={loading} />
            )
          } />
          
          <Route path="/register" element={
            isAuthenticated && initialized ? (
              <Navigate to={getRoleRedirect(userRole)} replace />
            ) : (
              <Register onRegister={apiService.authAPI.register} loading={loading} />
            )
          } />
          
          {/* Client Routes */}
          <Route path="/client" element={
            <ProtectedRoute allowedRoles={[ROLES.CLIENT]}>
              <ClientPortal />
            </ProtectedRoute>
          } />
          <Route path="/client/cart" element={
            <ProtectedRoute allowedRoles={[ROLES.CLIENT]}>
              <ClientCart />
            </ProtectedRoute>
          } />
          <Route path="/client/profile" element={
            <ProtectedRoute allowedRoles={[ROLES.CLIENT]}>
              <ClientProfile />
            </ProtectedRoute>
          } />
          <Route path="/client/support" element={
            <ProtectedRoute allowedRoles={[ROLES.CLIENT]}>
              <SupportPage />
            </ProtectedRoute>
          } />
          
          {/* Admin Routes */}
          <Route path="/admin" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="/admin/clients" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminClients />
            </ProtectedRoute>
          } />
          <Route path="/admin/products" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminProducts />
            </ProtectedRoute>
          } />
          <Route path="/admin/carts" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminCarts />
            </ProtectedRoute>
          } />
          <Route path="/admin/warehouse" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminWarehouse />
            </ProtectedRoute>
          } />
          <Route path="/admin/couriers" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminCouriers />
            </ProtectedRoute>
          } />
          <Route path="/admin/deliveries" element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <AdminDeliveries />
            </ProtectedRoute>
          } />
          
          {/* Office Routes */}
          <Route path="/office" element={
            <ProtectedOfficeRoute allowedRoles={[ROLES.OFFICE]}>
              <OfficePage onLogout={handleLogout} />
            </ProtectedOfficeRoute>
          } />
          <Route path="/office/deliveries" element={
            <ProtectedOfficeRoute allowedRoles={[ROLES.OFFICE]}>
              <OfficeDeliveries />
            </ProtectedOfficeRoute>
          } />
          <Route path="/office/orders" element={
            <ProtectedOfficeRoute allowedRoles={[ROLES.OFFICE]}>
              <OfficeOrders />
            </ProtectedOfficeRoute>
          } />
          <Route path="/office/reports" element={
            <ProtectedOfficeRoute allowedRoles={[ROLES.OFFICE]}>
              <OfficeReports />
            </ProtectedOfficeRoute>
          } />
          
          {/* Worker Routes */}
          <Route path="/courier" element={
            <ProtectedRoute allowedRoles={[ROLES.COURIER]}>
              <CourierApp />
            </ProtectedRoute>
          } />
          <Route path="/collector" element={
            <ProtectedCollectorRoute allowedRoles={[ROLES.COLLECTOR]}>
              <CollectorApp />
            </ProtectedCollectorRoute>
          } />
          
          {/* Default Routes */}
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;