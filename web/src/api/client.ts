import axios from 'axios';

// Central axios instance with base config
const api = axios.create({
  baseURL: '', // use relative so Vite proxy works in dev; in prod can set VITE_API_BASE
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: attach JWT if present
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: basic error + 401 handling
api.interceptors.response.use(
  (res) => {
    // Our backend wraps in ApiResponse; many callers just want .data.data
    return res;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token invalid/expired: clear and let UI redirect
      localStorage.removeItem('accessToken');
      localStorage.removeItem('currentUser');
      // Force a soft reload to login in most cases
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    const msg = error.response?.data?.message || error.message || 'Request failed';
    // 透传后端错误码与 HTTP 状态，便于上层区分（如目录密码 PASSWORD_REQUIRED/INCORRECT）
    const apiError = new Error(msg) as Error & { code?: string; status?: number };
    apiError.code = error.response?.data?.code;
    apiError.status = error.response?.status;
    return Promise.reject(apiError);
  }
);

// 带后端错误码的错误类型
export interface ApiError extends Error {
  code?: string;
  status?: number;
}

export default api;
