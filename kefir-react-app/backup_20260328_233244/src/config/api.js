// src/config/api.js
const getApiBaseUrl = () => {
  // Для продакшена (через NGINX)
  if (process.env.NODE_ENV === 'production') {
    return process.env.REACT_APP_API_GATEWAY || '/api';
  }
  // Для разработки
  return process.env.REACT_APP_API_GATEWAY || 'http://localhost:8080/api';
};

export const API_BASE_URL = getApiBaseUrl();

export const API_ENDPOINTS = {
  // Auth
  LOGIN: `${API_BASE_URL}/auth/login`,
  LOGOUT: `${API_BASE_URL}/auth/logout`,
  VALIDATE: `${API_BASE_URL}/auth/validate`,
  ME: `${API_BASE_URL}/auth/me`,
  CHANGE_PASSWORD: `${API_BASE_URL}/auth/change-password`,
  
  // User/Client
  CLIENTS: `${API_BASE_URL}/clients`,
  USER_BY_USERNAME: (username) => `${API_BASE_URL}/clients/by-username/${username}`,
  USER_BY_ID: (id) => `${API_BASE_URL}/clients/${id}`,
  REGISTER: `${API_BASE_URL}/clients/register`,
  
  // Cart
  CART_CLIENT: (clientId) => `${API_BASE_URL}/cart/client/${clientId}`,
  CART_CLIENT_FULL: (clientId) => `${API_BASE_URL}/cart/client/${clientId}/full`,
  CART_ADD: (cartId) => `${API_BASE_URL}/cart/${cartId}/add`,
  CART_ITEMS: (cartId) => `${API_BASE_URL}/cart/${cartId}/items`,
  CART_REMOVE: (cartId, itemId) => `${API_BASE_URL}/cart/${cartId}/items/${itemId}`,
  CART_CLEAR: (cartId) => `${API_BASE_URL}/cart/${cartId}/clear`,
  CART_CHECKOUT: (cartId) => `${API_BASE_URL}/cart/${cartId}/checkout`,
  CART_DELETE: (cartId) => `${API_BASE_URL}/cart/${cartId}`,
  
  // Orders
  ORDERS: `${API_BASE_URL}/orders`,
  ORDER_BY_CART: (cartId) => `${API_BASE_URL}/orders/by-cart/${cartId}`,
  ORDER_STATUS: (orderId) => `${API_BASE_URL}/orders/${orderId}/status`,
  ORDER_CLIENT: (clientId) => `${API_BASE_URL}/orders/client/${clientId}`,
  
  // Products
  PRODUCTS: `${API_BASE_URL}/products`,
  PRODUCT_CLIENT: `${API_BASE_URL}/client/products`,
  PRODUCT_RELEASE: (productId) => `${API_BASE_URL}/products/${productId}/release`,
  
  // Deliveries
  DELIVERIES: `${API_BASE_URL}/deliveries`,
  COURIERS: `${API_BASE_URL}/couriers`,
  DELIVERY_ASSIGN: (deliveryId) => `${API_BASE_URL}/deliveries/${deliveryId}/assign`,
  DELIVERY_STATUS: (deliveryId) => `${API_BASE_URL}/deliveries/${deliveryId}/status`,
  
  // Collector
  COLLECTOR_PROCESSING_ORDERS: `${API_BASE_URL}/collector/processing-orders`,
  COLLECTOR_CHECK_ITEM: `${API_BASE_URL}/collector/check-item-in-warehouse`,
  COLLECTOR_REPORT_MISSING: `${API_BASE_URL}/collector/report-missing-items`,
  COLLECTOR_COMPLETE: `${API_BASE_URL}/collector/complete-with-selected-items`,
  COLLECTOR_NALICHIE_STATUS: (cartId) => `${API_BASE_URL}/collector/cart/${cartId}/nalichie-status`,
  
  // Office
  OFFICE_PROBLEMS_ACTIVE: `${API_BASE_URL}/office/problems/active`,
  OFFICE_TAOSHIBA_ORDERS: `${API_BASE_URL}/office/taoshibka-orders`,
  OFFICE_PROBLEM_FULL_INFO: (cartId) => `${API_BASE_URL}/office/problems/full-info/${cartId}`,
  OFFICE_NOTIFY_CLIENT: `${API_BASE_URL}/office/notify-client`,
  OFFICE_MAKE_DECISION: `${API_BASE_URL}/office/make-decision`,
  OFFICE_FIND_COLLECTORS: (orderId) => `${API_BASE_URL}/office/taoshibka-orders/${orderId}/find-collectors`,
  
  // Support
  SUPPORT_UNAVAILABLE_ITEMS: (clientId) => `${API_BASE_URL}/support/unavailable-items/${clientId}`,
  SUPPORT_REFUND_ITEMS: `${API_BASE_URL}/support/refund-items`,
  SUPPORT_UPDATE_ORDER_STATUS: `${API_BASE_URL}/support/update-order-status`,
  SUPPORT_RECOLLECT_ORDER: `${API_BASE_URL}/support/recollect-order`,
  
  // Health
  HEALTH: `${API_BASE_URL}/health`,
};

export default API_ENDPOINTS;