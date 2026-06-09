import api from './client';

export interface Task {
  id: number;
  type: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED';
  progress: number;
  creatorId: number;
  result?: string;
  error?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskPage {
  content: Task[];
  total: number;
  page: number;
  perPage: number;
}

export async function listTasks(page = 1, perPage = 50): Promise<TaskPage> {
  const { data } = await api.get('/api/task/list', { params: { page, perPage } });
  return data.data;
}

export async function cancelTask(id: number): Promise<void> {
  await api.post(`/api/task/${id}/cancel`);
}

// 管理员触发重建文件名索引，返回任务 id
export async function buildIndex(storageId?: number): Promise<number> {
  const { data } = await api.post('/api/admin/index/build', storageId ? { storageId } : {});
  return data.data;
}
