import { create } from 'zustand';
import type { CurrentUser } from '../api/auth';

interface AuthState {
  token: string | null;
  user: CurrentUser | null;
  isAdmin: boolean;
  setAuth: (token: string, user: CurrentUser) => void;
  clearAuth: () => void;
}

/**
 * 同步从 localStorage 恢复登录态，作为 store 的初始值。
 * 放在 create 之外、首次渲染前执行，避免 ProtectedRoute 首屏因 user 为 null 而误跳登录。
 */
function loadInitialState(): Pick<AuthState, 'token' | 'user' | 'isAdmin'> {
  const token = localStorage.getItem('accessToken');
  const userStr = localStorage.getItem('currentUser');
  if (token && userStr) {
    try {
      const user: CurrentUser = JSON.parse(userStr);
      return { token, user, isAdmin: !!user.admin };
    } catch {
      // 数据损坏则清理，回退到未登录
      localStorage.removeItem('accessToken');
      localStorage.removeItem('currentUser');
    }
  }
  return { token: null, user: null, isAdmin: false };
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadInitialState(),

  setAuth: (token, user) => {
    localStorage.setItem('accessToken', token);
    localStorage.setItem('currentUser', JSON.stringify(user));
    set({ token, user, isAdmin: !!user?.admin });
  },

  clearAuth: () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('currentUser');
    set({ token: null, user: null, isAdmin: false });
  },
}));
