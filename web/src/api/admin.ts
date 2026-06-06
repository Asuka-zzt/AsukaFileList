import api from './client';

// Driver
export interface DriverItem {
  name: string;
  label: string;
  type: string; // string | boolean | integer ...
  required: boolean;
  defaultValue?: string;
  description?: string;
}

export interface DriverInfo {
  name: string;
  localSort: boolean;
  onlyLocal: boolean;
  onlyProxy: boolean;
  noCache: boolean;
  noUpload: boolean;
  defaultRoot: string;
  checkStatus: boolean;
  items: DriverItem[];
}

export async function listDrivers(): Promise<DriverInfo[]> {
  const { data } = await api.get('/api/admin/driver/list');
  return data.data;
}

// Storage
export interface Storage {
  id: number;
  mountPath: string;
  orderNo?: number;
  driver: string;
  cacheExpiration?: number;
  status: string; // work | disabled | init_error
  addition: Record<string, unknown>;
  remark?: string;
  disabled: boolean;
  // other fields omitted for brevity
}

export interface StorageCreate {
  mountPath: string;
  driver: string;
  addition: Record<string, unknown>;
  orderNo?: number;
  remark?: string;
  disabled?: boolean;
}

export interface StorageUpdate extends StorageCreate {
  id: number;
}

export async function listStorages(): Promise<Storage[]> {
  const { data } = await api.get('/api/admin/storage/list');
  return data.data;
}

export async function createStorage(req: StorageCreate): Promise<Storage> {
  const { data } = await api.post('/api/admin/storage/create', req);
  return data.data;
}

export async function updateStorage(req: StorageUpdate): Promise<Storage> {
  const { data } = await api.post('/api/admin/storage/update', req);
  return data.data;
}

export async function enableStorage(id: number): Promise<Storage> {
  const { data } = await api.post('/api/admin/storage/enable', { id });
  return data.data;
}

export async function disableStorage(id: number): Promise<Storage> {
  const { data } = await api.post('/api/admin/storage/disable', { id });
  return data.data;
}

export async function deleteStorage(id: number): Promise<void> {
  await api.post('/api/admin/storage/delete', { id });
}
