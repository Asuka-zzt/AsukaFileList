import api from './client';

export interface FileObject {
  id: string;
  path: string;
  name: string;
  size: number;
  isDir: boolean;
  modified: string;
  created: string;
  sign?: string;
  thumb?: string;
  type: number;
  hashInfo: Record<string, string>;
  storageClass: string; // "virtual" or driver name like "Local"
}

export interface FsListResponse {
  content: FileObject[];
  total: number;
  page: number;
  perPage: number;
  hasMore: boolean;
  readme?: string;
  header?: string;
  write: boolean;
  provider: string;
}

export interface FsListParams {
  path: string;
  password?: string;
  refresh?: boolean;
  page?: number;
  perPage?: number;
}

export async function listFiles(params: FsListParams): Promise<FsListResponse> {
  const { data } = await api.post('/api/fs/list', {
    path: params.path,
    password: params.password,
    refresh: params.refresh ?? false,
    page: params.page ?? 1,
    perPage: params.perPage ?? -1,
  });
  return data.data;
}

// 拼接目录路径与子项名
function joinPath(dir: string, name: string): string {
  if (!dir || dir === '/') return '/' + name;
  return dir.replace(/\/+$/, '') + '/' + name;
}

// 对路径逐段编码，保留分隔符
function encodePath(path: string): string {
  return path.split('/').map(encodeURIComponent).join('/');
}

export async function getFile(path: string): Promise<FileObject> {
  const { data } = await api.post('/api/fs/get', { path });
  return data.data;
}

export async function makeDir(path: string): Promise<FileObject> {
  const { data } = await api.post('/api/fs/mkdir', { path });
  return data.data;
}

export async function renameFile(path: string, name: string): Promise<void> {
  await api.post('/api/fs/rename', { path, name });
}

export async function moveFiles(srcDir: string, dstDir: string, names: string[]): Promise<void> {
  await api.post('/api/fs/move', { srcDir, dstDir, names });
}

export async function copyFiles(srcDir: string, dstDir: string, names: string[]): Promise<void> {
  await api.post('/api/fs/copy', { srcDir, dstDir, names });
}

export async function removeFiles(dir: string, names: string[]): Promise<void> {
  await api.post('/api/fs/remove', { dir, names });
}

// 流式上传：File-Path 头携带目标完整路径，请求体为文件内容
export async function uploadFile(dirPath: string, file: File): Promise<FileObject> {
  const filePath = joinPath(dirPath, file.name);
  const { data } = await api.put('/api/fs/put', file, {
    headers: {
      'File-Path': encodeURIComponent(filePath),
      'Content-Type': file.type || 'application/octet-stream',
    },
  });
  return data.data;
}

// 带鉴权下载并触发浏览器保存；M5 起携带下载签名（密码目录下文件必需）
export async function downloadFile(path: string, name: string, sign?: string): Promise<void> {
  const token = localStorage.getItem('accessToken');
  const query = sign ? '?sign=' + encodeURIComponent(sign) : '';
  const res = await fetch('/d' + encodePath(path) + query, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error('下载失败');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
