import api from './client';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface CurrentUser {
  id: number;
  username: string;
  basePath: string;
  permission: number;
  admin: boolean;
  roles: Array<{ id: number; name: string }>;
}

export interface LoginResponse {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: CurrentUser;
}

export async function login(req: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post('/api/auth/login', req);
  return data.data; // unwrap ApiResponse
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const { data } = await api.get('/api/me');
  return data.data;
}

export async function logout(): Promise<void> {
  await api.get('/api/auth/logout');
  localStorage.removeItem('accessToken');
  localStorage.removeItem('currentUser');
}
