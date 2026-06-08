import { useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { FileObject } from '../api/fs'
import {
  listFiles,
  makeDir,
  removeFiles,
  uploadFile,
  downloadFile,
  renameFile,
} from '../api/fs'
import { Folder, File, RefreshCw, Upload, FolderPlus, Download, Trash2, Pencil } from 'lucide-react'

export default function FileBrowser() {
  const [currentPath, setCurrentPath] = useState('/')
  const [refreshKey, setRefreshKey] = useState(0)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['fs-list', currentPath, refreshKey],
    queryFn: () => listFiles({ path: currentPath, perPage: -1 }),
  })

  const reload = () => {
    setRefreshKey((k) => k + 1)
    refetch()
  }

  // 统一包装写操作：处理 busy / 错误提示 / 刷新
  const run = async (label: string, fn: () => Promise<unknown>) => {
    setBusy(true)
    setMessage(null)
    try {
      await fn()
      reload()
    } catch (e) {
      setMessage(`${label}失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleEnter = (item: FileObject) => {
    if (item.isDir || item.storageClass === 'virtual') {
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

  const handleNewFolder = () => {
    const name = window.prompt('新建文件夹名称')
    if (!name) return
    const path = currentPath === '/' ? '/' + name : currentPath + '/' + name
    run('新建文件夹', () => makeDir(path))
  }

  const handleUploadChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0) return
    const list = Array.from(files)
    run('上传', async () => {
      for (const f of list) await uploadFile(currentPath, f)
    })
    e.target.value = ''
  }

  const handleDownload = (item: FileObject) => run('下载', () => downloadFile(item.path, item.name))

  const handleRename = (item: FileObject) => {
    const name = window.prompt('重命名为', item.name)
    if (!name || name === item.name) return
    run('重命名', () => renameFile(item.path, name))
  }

  const handleDelete = (item: FileObject) => {
    if (!window.confirm(`确认删除 ${item.name}？`)) return
    run('删除', () => removeFiles(currentPath, [item.name]))
  }

  const items = data?.content || []
  const atVirtual = currentPath === '/'

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

        <div className="flex items-center gap-2">
          <button
            onClick={handleNewFolder}
            disabled={busy || atVirtual}
            className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white disabled:opacity-40"
          >
            <FolderPlus className="w-4 h-4" /> 新建文件夹
          </button>
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={busy || atVirtual}
            className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white disabled:opacity-40"
          >
            <Upload className="w-4 h-4" /> 上传
          </button>
          <input ref={fileInputRef} type="file" multiple className="hidden" onChange={handleUploadChange} />
          <button
            onClick={reload}
            className="flex items-center gap-1 text-sm px-3 py-1.5 border rounded hover:bg-white"
            disabled={isLoading}
          >
            <RefreshCw className={`w-4 h-4 ${isLoading || busy ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </div>

      {message && <div className="mb-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{message}</div>}

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
                <th className="w-32">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-gray-400">此目录为空</td>
                </tr>
              )}
              {items.map((item, idx) => {
                const isVirtual = item.storageClass === 'virtual'
                return (
                  <tr key={idx} className="hover:bg-gray-50">
                    <td className="pl-3 cursor-pointer" onClick={() => handleEnter(item)}>
                      {item.isDir || isVirtual ? (
                        <Folder className="w-4 h-4 text-amber-500" />
                      ) : (
                        <File className="w-4 h-4 text-gray-400" />
                      )}
                    </td>
                    <td className="font-medium cursor-pointer" onClick={() => handleEnter(item)}>{item.name}</td>
                    <td className="text-gray-500">{item.isDir ? '—' : formatSize(item.size)}</td>
                    <td className="text-gray-500 text-xs">{item.modified ? new Date(item.modified).toLocaleString() : '—'}</td>
                    <td>
                      {!isVirtual && (
                        <div className="flex items-center gap-2 text-gray-400">
                          {!item.isDir && (
                            <button title="下载" disabled={busy} onClick={() => handleDownload(item)} className="hover:text-primary disabled:opacity-40">
                              <Download className="w-4 h-4" />
                            </button>
                          )}
                          <button title="重命名" disabled={busy} onClick={() => handleRename(item)} className="hover:text-primary disabled:opacity-40">
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button title="删除" disabled={busy} onClick={() => handleDelete(item)} className="hover:text-red-500 disabled:opacity-40">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      <div className="mt-3 text-xs text-gray-400">
        提示：点击文件夹或虚拟挂载点进入。进入具体存储后可上传、下载、新建文件夹、重命名与删除（M4）。
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
