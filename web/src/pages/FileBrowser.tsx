import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { FileObject } from '../api/fs'
import { listFiles } from '../api/fs'
import { Folder, File, RefreshCw } from 'lucide-react'

export default function FileBrowser() {
  const [currentPath, setCurrentPath] = useState('/')
  const [refreshKey, setRefreshKey] = useState(0)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['fs-list', currentPath, refreshKey],
    queryFn: () => listFiles({ path: currentPath, perPage: -1 }),
  })

  const handleRefresh = () => {
    setRefreshKey((k) => k + 1)
    refetch()
  }

  const handleEnter = (item: FileObject) => {
    if (item.isDir || item.storageClass === 'virtual') {
      // virtual entries have path like /local
      const target = item.path.startsWith('/') ? item.path : '/' + item.path
      setCurrentPath(target)
    }
  }

  const goUp = () => {
    if (currentPath === '/' || currentPath === '') return
    const parts = currentPath.split('/').filter(Boolean)
    parts.pop()
    setCurrentPath(parts.length ? '/' + parts.join('/') : '/')
  }

  const items = data?.content || []

  return (
    <div>
      {/* Breadcrumb + Toolbar */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2 text-sm">
          <button onClick={() => setCurrentPath('/')} className="text-primary hover:underline">根目录</button>
          {currentPath !== '/' && (
            <>
              <span className="text-gray-400">/</span>
              <span className="font-mono text-gray-600">{currentPath}</span>
              <button onClick={goUp} className="ml-2 text-xs px-2 py-0.5 border rounded hover:bg-gray-100">返回上级</button>
            </>
          )}
        </div>

        <button
          onClick={handleRefresh}
          className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white"
          disabled={isLoading}
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 刷新
        </button>
      </div>

      {/* File list */}
      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading && <div className="p-8 text-center text-gray-500">加载中...</div>}
        {isError && <div className="p-8 text-center text-red-500">加载失败，请检查后端服务或权限</div>}

        {!isLoading && !isError && (
          <table className="file-table w-full text-sm">
            <thead>
              <tr>
                <th className="w-8"></th>
                <th>名称</th>
                <th className="w-28">大小</th>
                <th className="w-44">修改时间</th>
                <th className="w-20">类型</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-gray-400">此目录为空</td>
                </tr>
              )}
              {items.map((item, idx) => (
                <tr
                  key={idx}
                  className="hover:bg-gray-50 cursor-pointer"
                  onClick={() => handleEnter(item)}
                >
                  <td className="pl-3">
                    {item.isDir || item.storageClass === 'virtual' ? (
                      <Folder className="w-4 h-4 text-amber-500" />
                    ) : (
                      <File className="w-4 h-4 text-gray-400" />
                    )}
                  </td>
                  <td className="font-medium">{item.name}</td>
                  <td className="text-gray-500">{item.isDir ? '—' : formatSize(item.size)}</td>
                  <td className="text-gray-500 text-xs">{item.modified ? new Date(item.modified).toLocaleString() : '—'}</td>
                  <td>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-600">
                      {item.storageClass || (item.isDir ? 'dir' : 'file')}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="mt-3 text-xs text-gray-400">
        提示：点击文件夹或虚拟挂载点进入。当前仅支持只读列表（M3 阶段）。上传等功能敬请期待。
      </div>
    </div>
  )
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}
