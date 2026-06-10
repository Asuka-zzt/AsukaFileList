import api from './client';

// 设置当前用户的 WebDAV 专用密码（仅存 HA1，用于挂载）
export async function setWebdavPassword(password: string): Promise<void> {
  await api.post('/api/me/webdav-password', { password });
}

// 清除 WebDAV 密码（禁用 WebDAV 登录）
export async function clearWebdavPassword(): Promise<void> {
  await api.post('/api/me/webdav-password/clear');
}
