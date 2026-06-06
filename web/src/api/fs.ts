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
