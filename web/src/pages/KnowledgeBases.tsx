import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { listKbs, createKb, deleteKb } from '../api/kb'
import { toast } from 'sonner'
import { RefreshCw, Trash2, Plus, FileText, MessageSquare, Database } from 'lucide-react'

export default function KnowledgeBases() {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['kbs'],
    queryFn: listKbs,
  })

  const kbs = data || []

  const handleCreate = async () => {
    if (!name.trim()) {
      toast.error('请填写知识库名称')
      return
    }
    try {
      await createKb(name.trim(), description.trim() || undefined)
      toast.success('知识库已创建')
      setName('')
      setDescription('')
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['kbs'] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定删除该知识库？其文档与索引将一并清除，不可恢复。')) return
    try {
      await deleteKb(id)
      toast.success('已删除')
      queryClient.invalidateQueries({ queryKey: ['kbs'] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium text-gray-700 flex items-center gap-2">
          <Database className="w-5 h-5" /> 知识库
        </h2>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowForm((v) => !v)}
            className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white"
          >
            <Plus className="w-4 h-4" /> 新建知识库
          </button>
          <button
            onClick={() => refetch()}
            className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </div>

      {showForm && (
        <div className="bg-white border rounded p-4 mb-4 space-y-3">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="知识库名称"
            className="w-full border rounded px-3 py-2 text-sm"
          />
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="描述（可选）"
            rows={2}
            className="w-full border rounded px-3 py-2 text-sm"
          />
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowForm(false)} className="text-sm px-3 py-1.5 border rounded">
              取消
            </button>
            <button onClick={handleCreate} className="text-sm px-3 py-1.5 rounded bg-primary text-white">
              创建
            </button>
          </div>
        </div>
      )}

      <div className="bg-white border rounded divide-y">
        {kbs.length === 0 && (
          <div className="p-6 text-center text-sm text-gray-400">
            还没有知识库，点击「新建知识库」开始。
          </div>
        )}
        {kbs.map((kb) => (
          <div key={kb.id} className="flex items-center justify-between px-4 py-3">
            <div className="min-w-0">
              <div className="font-medium text-gray-800 truncate">{kb.name}</div>
              <div className="text-xs text-gray-400 truncate">
                {kb.description || '无描述'} · {kb.workspace}
              </div>
            </div>
            <div className="flex items-center gap-3 text-sm shrink-0">
              <Link to={`/kb/${kb.id}/documents`} className="flex items-center gap-1 text-primary hover:underline">
                <FileText className="w-4 h-4" /> 文档
              </Link>
              <Link to={`/kb/${kb.id}/chat`} className="flex items-center gap-1 text-primary hover:underline">
                <MessageSquare className="w-4 h-4" /> 问答
              </Link>
              <button onClick={() => handleDelete(kb.id)} className="text-gray-400 hover:text-red-600">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
