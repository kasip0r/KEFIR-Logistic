// src/services/api.js
import axios from 'axios';

// Базовый URL (без /api в конце!)
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

console.log('API base URL:', API_BASE_URL);

const api = axios.create({
  baseURL: API_BASE_URL,  // Только хост, без /api
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor для логирования запросов
api.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    error => Promise.reject(error)
);

// Interceptor для ответов
api.interceptors.response.use(
  response => {
    console.log(`[API] Response ${response.status}:`, response.data);
    return response;
  },
  error => {
    console.error('[API] Response error:', {
      status: error.response?.status,
      data: error.response?.data,
      message: error.message
    });
    
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('authToken');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    
    return Promise.reject(error);
  }
);

// ==================== AUTH API ====================
export const authAPI = {
  login: async (credentials) => {
    try {
      const response = await api.post('api/auth/login', credentials);
      
      console.log('Login response:', response.data);
      
      // ✅ ПРОВЕРЬТЕ, ЧТО ЗДЕСЬ ВСЁ ПРАВИЛЬНО
      const responseData = response.data;
      const token = responseData.token;  // ДОЛЖЕН БЫТЬ ТУТ!
      const user = responseData.user;
      
      if (token) {
        localStorage.setItem('token', token);
        localStorage.setItem('authToken', token);  // ТОЖЕ СОХРАНЯЕМ
        console.log('✅ Token saved:', token.substring(0, 20) + '...');
      } else {
        console.error('❌ No token in response!', responseData);
      }
      
      if (user) {
        localStorage.setItem('user', JSON.stringify(user));
      }
      
      return responseData;
    } catch (error) {
      console.error('Login API error:', error);
      throw error;
    }
  },
  
  logout: async () => {
    try {
      const response = await api.post('/auth/logout');
      return response.data;
    } catch (error) {
      console.error('Logout error:', error);
      throw error;
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('authToken');
      localStorage.removeItem('user');
    }
  },
  
  register: async (userData) => {
    try {
      const payload = {
        username: userData.username,
        firstname: userData.name || userData.firstname,
        email: userData.email,
        password: userData.password
      };
      
      console.log('Sending registration data:', payload);
      const response = await api.post('/clients/register', payload);
      return response.data;
    } catch (error) {
      console.error('Registration API error:', error);
      throw error;
    }
  },
  
  validate: async (token) => {
    try {
      const response = await api.post('/auth/validate', { token });
      return response.data;
    } catch (error) {
      console.error('Validation error:', error);
      throw error;
    }
  },
  
  getCurrentUser: async () => {
    try {
      const response = await api.get('/auth/me');
      return response.data;
    } catch (error) {
      console.error('Get current user error:', error);
      throw error;
    }
  }
};

// ==================== HEALTH API ====================
export const healthAPI = {
  check: async () => {
    try {
      const response = await api.get('/health');
      return response.data;
    } catch (error) {
      console.error('Health check failed:', error);
      return { gateway: 'DOWN' };
    }
  },
  
  checkAuth: async () => {
    try {
      const response = await api.get('/auth/health');
      return response.data;
    } catch (error) {
      console.error('Auth health check failed:', error);
      return { status: 'DOWN' };
    }
  }
};

// ==================== CLIENTS API ====================
export const clientsAPI = {
  getAll: async () => {
    const response = await api.get('api/clients');
    return response.data;
  },
  
  getById: async (id) => {
    const response = await api.get(`api/clients/${id}`);
    return response.data;
  },
  
  getProfile: async (id) => {
    const response = await api.get(`api/clients/${id}/profile`);
    return response.data;
  },

  // ✅ ДОБАВИТЬ ЭТОТ МЕТОД
  create: async (data) => {
    const response = await api.post('api/admin/clients', data);
    return response.data;
  },

  delete: async (id) => {
    const response = await api.delete(`api/admin/clients/${id}`);
    return response.data;
  },

  update: async (id, data) => {
    const response = await api.put(`api/admin/clients/${id}`, data);
    return response.data;
  }
};

// ==================== PRODUCTS API ====================
export const productsAPI = {
  getAll: async () => {
    const response = await api.get('api/products');
    return response.data;  // ← это массив
  },
  
  getByWarehouse: async (warehouse) => {
    const response = await api.get(`api/warehouse/${warehouse}/products`);
    // ✅ Возвращаем ТОЛЬКО массив товаров
    return response.data.products || [];
  },
  
  // GET товар по ID
  getById: async (id) => {
    const response = await api.get(`api/products/${id}`);
    return response.data;
  },
  
  // POST создать новый товар
  create: async (productData) => {
  const token = localStorage.getItem('token');
  const response = await api.post('api/products', productData, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.data;
},
  
  // api.js - метод update
update: async (id, productData, warehouse) => {
  console.log('📤 Updating product ID:', id, 'with data:', productData, 'warehouse:', warehouse);
  
  // Добавляем warehouse как query-параметр
  const url = warehouse 
    ? `api/products/${id}?warehouse=${warehouse}`
    : `api/products/${id}`;
  
  const response = await api.put(url, productData);
  console.log('📥 Update response:', response.data);
  return response.data;
},
  
  delete: async (id, warehouse) => {
      console.log('📤 Deleting product ID:', id, 'warehouse:', warehouse);
  console.log('📤 URL:', `api/products/${id}?warehouse=${warehouse}`); 
   const url = warehouse 
    ? `api/products/${id}?warehouse=${warehouse}`
    : `api/products/${id}`;
  
  const response = await api.delete(url);
  console.log('📥 Delete response:', response.data);
  return response.data;
},
  
  // GET поиск товаров
  search: async (query) => {
    const response = await api.get(`api/products/search?query=${encodeURIComponent(query)}`);
    return response.data;
  },
  
  // GET товары по категории
  getByCategory: async (category) => {
    const response = await api.get(`api/products/category/${encodeURIComponent(category)}`);
    return response.data;
  },
  
  // GET статистика товаров
  getStats: async () => {
    const response = await api.get('api/products/stats');
    return response.data;
  },
  
  // GET товары с низким запасом
  getLowStock: async (threshold = 10) => {
    const response = await api.get(`api/products/low-stock?threshold=${threshold}`);
    return response.data;
  },
  
  // GET товары для клиента (с учетом города)
  getForClient: async () => {
    const response = await api.get('api/client/products');
    return response.data;
  },
  
  // GET конкретный товар для клиента
  getForClientById: async (id) => {
    const response = await api.get(`api/client/products/${id}`);
    return response.data;
  }
};

// ==================== CART API ====================
export const cartAPI = {
  getClientCarts: async (clientId) => {
    const response = await api.get(`/cart/client/${clientId}`);
    return response.data;
  },
  
  getClientCartsFull: async (clientId) => {
    const response = await api.get(`/cart/client/${clientId}/full`);
    return response.data;
  },
  
  checkout: async (cartId) => {
    const response = await api.post(`/cart/${cartId}/checkout`, {});
    return response.data;
  }
};

// ==================== ORDERS API ====================
export const ordersAPI = {
  create: async (orderData) => {
    const token = localStorage.getItem('token');
    const response = await api.post('/orders', orderData, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.data;
  },
  
  getMyOrders: async () => {
    const token = localStorage.getItem('token');
    const response = await api.get('/cart/my-orders', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.data;
  }
};

// ==================== OFFICE API ====================
export const officeAPI = {
  getActiveProblems: async () => {
    const response = await api.get('/office/problems/active');
    return response.data;
  },
  
  getOrderInfo: async (orderId) => {
    const response = await api.get(`/office/order/${orderId}/full-info`);
    return response.data;
  }
};

// ==================== DELIVERY API ====================
export const deliveryAPI = {
  getClientDeliveries: async (clientId) => {
    const response = await api.get(`/deliveries/client/${clientId}`);
    return response.data;
  },
  
  getActive: async () => {
    const response = await api.get('/deliveries/active');
    return response.data;
  }
};

// ==================== COLLECTOR API ====================
export const collectorAPI = {
  getAll: async () => {
    const response = await api.get('/collector/collectors');
    return response.data;
  },
  
  getTasks: async () => {
    const response = await api.get('/collector/tasks');
    return response.data;
  }
};

// ==================== UTILS ====================
export const utils = {
  saveToken: (token) => {
    if (token) {
      localStorage.setItem('token', token);
      localStorage.setItem('authToken', token);
    }
  },
  
  getToken: () => {
    return localStorage.getItem('token') || localStorage.getItem('authToken');
  },
  
  removeToken: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('authToken');
  },
  
  saveUser: (user) => {
    if (user) {
      localStorage.setItem('user', JSON.stringify(user));
    }
  },
  
  getUser: () => {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  },
  
  removeUser: () => {
    localStorage.removeItem('user');
  },
  
  logout: () => {
    utils.removeToken();
    utils.removeUser();
  },
  
  isAuthenticated: () => {
    return !!utils.getToken();
  }
};

// Единый объект для экспорта
const apiService = {
  authAPI,
  healthAPI,
  clientsAPI,
  productsAPI,
  cartAPI,
  ordersAPI,
  officeAPI,
  deliveryAPI,
  collectorAPI,
  utils
};

export default apiService;