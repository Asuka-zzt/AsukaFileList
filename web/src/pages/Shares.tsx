import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { listShares, createShare, deleteShare, type ShareCreateParams } from '../api/share'
import { toast } from 'sonner'
import { RefreshCw, Trash2, Link2, Plus } from 'lucide-react'

// 构造分享公开页地址（基于当前站点）
function publicUrl(shareId: string): string {
  return `${window.location.origin}/s/${shareId}`
}

export default function Shares() {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<ShareCreateParams>({ rootPath: '' })

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['shares'],
    queryFn: () => listShares(1, 100),
  })

  const shares = data?.content || []

  const handleCreate = async () => {
    if (!form.rootPath.trim()) {
      toast.error('请填写要分享的路径')
      return
    }
    try {
      const payload: ShareCreateParams = {
        rootPath: form.rootPath.trim(),
        name: form.name?.trim() || undefined,
        password: form.password?.trim() || undefined,
        expiresAt: form.expiresAt || undefined,
        accessLimit: form.accessLimit ? Number(form.accessLimit) : undefined,
        burnAfterRead: form.burnAfterRead || undefined,
        allowDownload: form.allowDownload === false ? false : undefined,
      }
      const created = await createShare(payload)
      toast.success('分享已创建')
      await navigator.clipboard.writeText(publicUrl(created.shareId)).catch(() => {})
      setForm({ rootPath: '' })
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['shares'] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定删除这条分享？删除后链接立即失效。')) return
    try {
      await deleteShare(id)
      toast.success('已删除')
      queryClient.invalidateQueries({ queryKey: ['shares'] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const copyLink = async (shareId: string) => {
    await navigator.clipboard.writeText(publicUrl(shareId))
    toast.success('链接已复制')
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium text-gray-700">我的分享</h2>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowForm((v) => !v)} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
            <Plus className="w-4 h-4" /> 新建分享
          </button>
          <button onClick={() => refetch()} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </div>

      {showForm && (
        <div className="bg-white rounded-lg border p-4 mb-4 space-y-3 text-sm">
          <div>
            <label className="block text-gray-500 mb-1">分享路径（文件或目录的完整路径）</label>
            <input
              value={form.rootPath}
              onChange={(e) => setForm({ ...form, rootPath: e.target.value })}
              placeholder="/storage/docs/report.pdf"
              className="w-full border rounded px-2 py-1.5"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-gray-500 mb-1">名称（可选）</label>
              <input
                value={form.name || ''}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full border rounded px-2 py-1.5"
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1">访问密码（可选）</label>
              <input
                value={form.password || ''}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                className="w-full border rounded px-2 py-1.5"
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1">过期时间（可选）</label>
              <input
                type="datetime-local"
                value={form.expiresAt || ''}
                onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
                className="w-full border rounded px-2 py-1.5"
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1">访问次数上限（可选）</label>
              <input
                type="number"
                min={1}
                value={form.accessLimit ?? ''}
                onChange={(e) => setForm({ ...form, accessLimit: e.target.value ? Number(e.target.value) : undefined })}
                className="w-full border rounded px-2 py-1.5"
              />
            </div>
          </div>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-1.5">
              <input
                type="checkbox"
                checked={form.burnAfterRead || false}
                onChange={(e) => setForm({ ...form, burnAfterRead: e.target.checked })}
              />
              阅后即焚
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="checkbox"
                checked={form.allowDownload === false}
                onChange={(e) => setForm({ ...form, allowDownload: e.target.checked ? false : undefined })}
              />
              禁止下载（仅浏览）
            </label>
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowForm(false)} className="px-3 py-1.5 border rounded text-gray-600">取消</button>
            <button onClick={handleCreate} className="px-3 py-1.5 rounded bg-primary text-white">创建并复制链接</button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg border overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500 border-b">
              <th className="px-3 py-2">名称 / 路径</th>
              <th className="px-3 py-2 w-24">类型</th>
              <th className="px-3 py-2 w-40">限制</th>
              <th className="px-3 py-2 w-28">访问次数</th>
              <th className="px-3 py-2 w-44">过期时间</th>
              <th className="px-3 py-2 w-24">操作</th>
            </tr>
          </thead>
          <tbody>
            {shares.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-8 text-center text-gray-400">暂无分享</td></tr>
            )}
            {shares.map((s) => (
              <tr key={s.id} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2">
                  <div className="font-medium text-gray-700">{s.name || s.shareId}</div>
                  <div className="text-xs text-gray-400 font-mono truncate" title={s.rootPath}>{s.rootPath}</div>
                </td>
                <td className="px-3 py-2 text-gray-500">{s.isDir ? '目录' : '文件'}</td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    {s.hasPassword && <span className="px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 text-xs">密码</span>}
                    {s.burnAfterRead && <span className="px-1.5 py-0.5 rounded bg-red-50 text-red-700 text-xs">阅后即焚</span>}
                    {!s.allowDownload && <span className="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 text-xs">禁下载</span>}
                    {!s.enabled && <span className="px-1.5 py-0.5 rounded bg-gray-200 text-gray-500 text-xs">已失效</span>}
                  </div>
                </td>
                <td className="px-3 py-2 text-gray-500">
                  {s.accessCount}{s.accessLimit > 0 ? ` / ${s.accessLimit}` : ''}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500">
                  {s.expiresAt ? new Date(s.expiresAt).toLocaleString() : '永久'}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-2">
                    <button onClick={() => copyLink(s.shareId)} title="复制链接" className="text-gray-400 hover:text-primary">
                      <Link2 className="w-4 h-4" />
                    </button>
                    <button onClick={() => handleDelete(s.id)} title="删除" className="text-gray-400 hover:text-red-500">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
