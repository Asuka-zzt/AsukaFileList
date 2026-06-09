import { useQuery, useQueryClient } from '@tanstack/react-query'
import { listTasks, cancelTask, buildIndex, type Task } from '../api/task'
import { useAuthStore } from '../stores/auth'
import { toast } from 'sonner'
import { RefreshCw, XCircle, DatabaseZap } from 'lucide-react'

const STATUS_STYLE: Record<Task['status'], string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  RUNNING: 'bg-blue-100 text-blue-700',
  SUCCESS: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  CANCELED: 'bg-amber-100 text-amber-700',
}

export default function Tasks() {
  const { user } = useAuthStore()
  const queryClient = useQueryClient()

  // 轮询任务列表，运行中任务进度实时刷新
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['tasks'],
    queryFn: () => listTasks(1, 50),
    refetchInterval: 2000,
  })

  const handleCancel = async (id: number) => {
    try {
      await cancelTask(id)
      toast.success('已请求取消')
      refetch()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '取消失败')
    }
  }

  const handleBuildIndex = async () => {
    try {
      const id = await buildIndex()
      toast.success(`已提交索引重建任务 #${id}`)
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '提交失败')
    }
  }

  const tasks = data?.content || []

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium text-gray-700">任务中心</h2>
        <div className="flex items-center gap-2">
          {user?.admin && (
            <button onClick={handleBuildIndex} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
              <DatabaseZap className="w-4 h-4" /> 重建文件名索引
            </button>
          )}
          <button onClick={() => refetch()} className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white">
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg border overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500 border-b">
              <th className="px-3 py-2 w-16">#</th>
              <th className="px-3 py-2 w-32">类型</th>
              <th className="px-3 py-2 w-28">状态</th>
              <th className="px-3 py-2">进度</th>
              <th className="px-3 py-2 w-44">更新时间</th>
              <th className="px-3 py-2 w-20">操作</th>
            </tr>
          </thead>
          <tbody>
            {tasks.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-8 text-center text-gray-400">暂无任务</td></tr>
            )}
            {tasks.map((t) => {
              const active = t.status === 'PENDING' || t.status === 'RUNNING'
              return (
                <tr key={t.id} className="border-b last:border-0 hover:bg-gray-50">
                  <td className="px-3 py-2 text-gray-500">{t.id}</td>
                  <td className="px-3 py-2 font-mono text-xs">{t.type}</td>
                  <td className="px-3 py-2">
                    <span className={`px-2 py-0.5 rounded text-xs ${STATUS_STYLE[t.status]}`}>{t.status}</span>
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-2 bg-gray-100 rounded overflow-hidden">
                        <div className="h-full bg-primary transition-all" style={{ width: `${t.progress}%` }} />
                      </div>
                      <span className="text-xs text-gray-400 w-9 text-right">{t.progress}%</span>
                    </div>
                    {t.error && <div className="text-xs text-red-500 mt-1 truncate" title={t.error}>{t.error}</div>}
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-500">{t.updatedAt ? new Date(t.updatedAt).toLocaleString() : '—'}</td>
                  <td className="px-3 py-2">
                    {active && (
                      <button onClick={() => handleCancel(t.id)} title="取消" className="text-gray-400 hover:text-red-500">
                        <XCircle className="w-4 h-4" />
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
