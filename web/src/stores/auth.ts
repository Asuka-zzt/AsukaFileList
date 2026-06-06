import { create } from 'zustand';
import type { CurrentUser } from '../api/auth';

interface AuthState {
  token: string | null;
  user: CurrentUser | null;
  isAdmin: boolean;
  setAuth: (token: string, user: CurrentUser) => void;
  clearAuth: () => void;
  initFromStorage: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  isAdmin: false,

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

  initFromStorage: () => {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('currentUser');
    if (token && userStr) {
      try {
        const user: CurrentUser = JSON.parse(userStr);
        set({ token, user, isAdmin: !!user.admin });
      } catch {
        // ignore corrupted
      }
    }
  },
}));
