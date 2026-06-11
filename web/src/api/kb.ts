import api from './client';

// ─── 类型 ───────────────────────────────────────────────────────

export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  workspace: string;
  status: string;
  createdAt: string;
}

export type KbDocStatus = 'pending' | 'parsing' | 'indexing' | 'indexed' | 'failed';

export interface KbDocument {
  id: number;
  kbId: number;
  fileName: string;
  sourcePath: string;
  docType: string;
  status: KbDocStatus;
  errorMsg?: string;
  taskId?: string;
  createdAt: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

// SSE 事件（与 AI 服务 agent loop 对齐）
export interface ChatEvent {
  type: 'status' | 'token' | 'citations' | 'done' | 'error';
  stage?: string;
  mode?: string;
  subqueries?: string[];
  iter?: number;
  sufficient?: boolean;
  text?: string;
  items?: { index: number; reference_id: string; file_path: string }[];
  message?: string;
}

// ─── 知识库 CRUD ─────────────────────────────────────────────────

export async function listKbs(): Promise<KnowledgeBase[]> {
  const { data } = await api.get('/api/kb');
  return data.data;
}

export async function createKb(name: string, description?: string): Promise<KnowledgeBase> {
  const { data } = await api.post('/api/kb', { name, description });
  return data.data;
}

export async function deleteKb(kbId: number): Promise<void> {
  await api.delete(`/api/kb/${kbId}`);
}

// ─── 文档 ───────────────────────────────────────────────────────

export async function listDocuments(kbId: number): Promise<KbDocument[]> {
  const { data } = await api.get(`/api/kb/${kbId}/documents`);
  return data.data;
}

export async function addDocument(
  kbId: number,
  path: string,
  docType = 'paper',
): Promise<KbDocument> {
  const { data } = await api.post(`/api/kb/${kbId}/documents`, { path, docType });
  return data.data;
}

export async function deleteDocument(kbId: number, docId: number): Promise<void> {
  await api.delete(`/api/kb/${kbId}/documents/${docId}`);
}

// ─── 问答（SSE 流式）────────────────────────────────────────────

/**
 * 发起知识库问答并消费 SSE 流。docId 非空走单文档问答。
 * 用 fetch 而非 axios/EventSource：需 POST + Authorization 头 + 流式读取。
 */
export async function streamChat(
  kbId: number,
  body: { question: string; history?: ChatMessage[] },
  opts: { docId?: number; signal?: AbortSignal; onEvent: (e: ChatEvent) => void },
): Promise<void> {
  const token = localStorage.getItem('accessToken');
  const url =
    opts.docId != null
      ? `/api/kb/${kbId}/documents/${opts.docId}/chat`
      : `/api/kb/${kbId}/chat`;
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal: opts.signal,
  });
  if (!resp.ok || !resp.body) {
    throw new Error(`问答请求失败：HTTP ${resp.status}`);
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let idx: number;
    // SSE 事件以空行分隔
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const raw = buf.slice(0, idx).trim();
      buf = buf.slice(idx + 2);
      if (raw.startsWith('data:')) {
        try {
          opts.onEvent(JSON.parse(raw.slice(5).trim()) as ChatEvent);
        } catch {
          // 忽略无法解析的片段
        }
      }
    }
  }
}
