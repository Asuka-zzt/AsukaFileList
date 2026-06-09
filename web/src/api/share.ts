import api from './client';
import type { FileObject, FsListResponse } from './fs';

// ─── 管理端（登录态）类型 ───────────────────────────────────────

export interface Share {
  id: number;
  shareId: string;
  name: string;
  rootPath: string;
  isDir: boolean;
  hasPassword: boolean;
  burnAfterRead: boolean;
  accessLimit: number;
  accessCount: number;
  allowPreview: boolean;
  allowDownload: boolean;
  enabled: boolean;
  expiresAt?: string;
  createdAt: string;
}

export interface SharePage {
  content: Share[];
  total: number;
  page: number;
  perPage: number;
}

export interface ShareCreateParams {
  rootPath: string;
  name?: string;
  password?: string;
  expiresAt?: string;
  accessLimit?: number;
  burnAfterRead?: boolean;
  allowPreview?: boolean;
  allowDownload?: boolean;
}

export async function listShares(page = 1, perPage = 50): Promise<SharePage> {
  const { data } = await api.get('/api/share/list', { params: { page, perPage } });
  return data.data;
}

export async function createShare(params: ShareCreateParams): Promise<Share> {
  const { data } = await api.post('/api/share/create', params);
  return data.data;
}

export async function deleteShare(id: number): Promise<void> {
  await api.post('/api/share/delete', { id });
}

// ─── 公开端（匿名）类型 ─────────────────────────────────────────

export interface PublicShareInfo {
  shareId: string;
  name: string;
  isDir: boolean;
  needPassword: boolean;
  allowPreview: boolean;
  allowDownload: boolean;
}

export interface ShareAuth {
  token: string;
  name: string;
  isDir: boolean;
  allowPreview: boolean;
  allowDownload: boolean;
}

export async function getShareInfo(shareId: string): Promise<PublicShareInfo> {
  const { data } = await api.get('/api/public/share/info', { params: { shareId } });
  return data.data;
}

export async function authShare(shareId: string, password?: string): Promise<ShareAuth> {
  const { data } = await api.post('/api/public/share/auth', { shareId, password });
  return data.data;
}

export async function listShareDir(
  shareId: string,
  subPath: string,
  token: string,
): Promise<FsListResponse> {
  const { data } = await api.post(
    '/api/public/share/list',
    { shareId, subPath },
    { headers: { 'X-Share-Token': token } },
  );
  return data.data;
}

export async function getShareFile(
  shareId: string,
  subPath: string,
  token: string,
): Promise<FileObject> {
  const { data } = await api.post(
    '/api/public/share/get',
    { shareId, subPath },
    { headers: { 'X-Share-Token': token } },
  );
  return data.data;
}

// 对路径逐段编码，保留分隔符
function encodePath(path: string): string {
  return path.split('/').map(encodeURIComponent).join('/');
}

// 构造公开下载直链：/sd/{shareId}{子路径}?token=
export function shareDownloadUrl(shareId: string, subPath: string, token: string): string {
  const tail = subPath && subPath !== '/' ? encodePath(subPath) : '';
  return `/sd/${encodeURIComponent(shareId)}${tail}?token=${encodeURIComponent(token)}`;
}
