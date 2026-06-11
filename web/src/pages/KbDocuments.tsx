import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import {
  listDocuments,
  addDocument,
  deleteDocument,
  type KbDocStatus,
} from '../api/kb'
import { toast } from 'sonner'
import { RefreshCw, Trash2, Plus, MessageSquare, ArrowLeft } from 'lucide-react'

const STATUS_STYLE: Record<KbDocStatus, string> = {
  pending: 'bg-gray-100 text-gray-600',
  parsing: 'bg-blue-100 text-blue-700',
  indexing: 'bg-blue-100 text-blue-700',
  indexed: 'bg-green-100 text-green-700',
  failed: 'bg-red-100 text-red-700',
}

const DOC_TYPES = ['paper', 'book', 'note']

export default function KbDocuments() {
  const { kbId } = useParams<{ kbId: string }>()
  const id = Number(kbId)
  const queryClient = useQueryClient()
  const [path, setPath] = useState('')
  const [docType, setDocType] = useState('paper')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['kb-docs', id],
    queryFn: () => listDocuments(id),
    refetchInterval: 3000, // 轮询索引状态
  })

  const docs = data || []

  const handleAdd = async () => {
    if (!path.trim()) {
      toast.error('请填写网盘文件路径，如 /papers/foo.pdf')
      return
    }
    try {
      await addDocument(id, path.trim(), docType)
      toast.success('已加入，开始解析索引')
      setPath('')
      queryClient.invalidateQueries({ queryKey: ['kb-docs', id] })
    } catch (e) {
      const err = e as Error & { code?: string }
      toast.error(err.code === 'KB_DOCUMENT_DUPLICATE' ? '该文件已在知识库中' : err.message || '加入失败')
    }
  }

  const handleDelete = async (docId: number) => {
    if (!confirm('从知识库移除该文档？其索引证据将被删除。')) return
    try {
      await deleteDocument(id, docId)
      toast.success('已移除')
      queryClient.invalidateQueries({ queryKey: ['kb-docs', id] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '移除失败')
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium text-gray-700 flex items-center gap-2">
          <Link to="/kb" className="text-gray-400 hover:text-primary">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          知识库文档
        </h2>
        <div className="flex items-center gap-2">
          <Link to={`/kb/${id}/chat`} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
            <MessageSquare className="w-4 h-4" /> 整库问答
          </Link>
          <button onClick={() => refetch()} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </div>

      {/* 加文档：填写网盘文件路径 */}
      <div className="bg-white border rounded p-3 mb-4 flex flex-wrap items-center gap-2">
        <input
          value={path}
          onChange={(e) => setPath(e.target.value)}
          placeholder="网盘文件路径，如 /papers/insec.pdf 或 /notes/foo.md"
          className="flex-1 min-w-[260px] border rounded px-3 py-2 text-sm"
        />
        <select value={docType} onChange={(e) => setDocType(e.target.value)} className="border rounded px-2 py-2 text-sm">
          {DOC_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <button onClick={handleAdd} className="flex items-center gap-1 text-sm px-3 py-2 rounded bg-primary text-white">
          <Plus className="w-4 h-4" /> 加入知识库
        </button>
      </div>

      <div className="bg-white border rounded divide-y">
        {docs.length === 0 && (
          <div className="p-6 text-center text-sm text-gray-400">还没有文档，填写路径加入。</div>
        )}
        {docs.map((doc) => (
          <div key={doc.id} className="flex items-center justify-between px-4 py-3">
            <div className="min-w-0">
              <div className="font-medium text-gray-800 truncate">{doc.fileName}</div>
              <div className="text-xs text-gray-400 truncate">
                {doc.sourcePath} · {doc.docType}
                {doc.status === 'failed' && doc.errorMsg ? ` · ${doc.errorMsg}` : ''}
              </div>
            </div>
            <div className="flex items-center gap-3 text-sm shrink-0">
              <span className={`px-2 py-0.5 rounded text-xs ${STATUS_STYLE[doc.status]}`}>{doc.status}</span>
              <Link
                to={`/kb/${id}/chat?docId=${doc.id}`}
                className="flex items-center gap-1 text-primary hover:underline"
              >
                <MessageSquare className="w-4 h-4" /> 单文档问答
              </Link>
              <button onClick={() => handleDelete(doc.id)} className="text-gray-400 hover:text-red-600">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
