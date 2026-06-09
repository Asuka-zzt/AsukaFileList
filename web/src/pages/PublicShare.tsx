import { useEffect, useState, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import {
  getShareInfo,
  authShare,
  listShareDir,
  shareDownloadUrl,
  type PublicShareInfo,
  type ShareAuth,
} from '../api/share'
import type { FileObject } from '../api/fs'
import { Folder, FileText, Download, Lock, ArrowLeft } from 'lucide-react'
import { toast } from 'sonner'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = bytes / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(1)} ${units[i]}`
}

// 父级子路径：/a/b/c -> /a/b
function parentPath(p: string): string {
  const trimmed = p.replace(/\/+$/, '')
  const idx = trimmed.lastIndexOf('/')
  return idx <= 0 ? '/' : trimmed.slice(0, idx)
}

function joinSub(dir: string, name: string): string {
  if (!dir || dir === '/') return '/' + name
  return dir.replace(/\/+$/, '') + '/' + name
}

export default function PublicShare() {
  const { shareId = '' } = useParams()
  const [info, setInfo] = useState<PublicShareInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [password, setPassword] = useState('')
  const [auth, setAuth] = useState<ShareAuth | null>(null)
  const [subPath, setSubPath] = useState('/')
  const [entries, setEntries] = useState<FileObject[]>([])
  const [loading, setLoading] = useState(false)

  const loadDir = useCallback(
    async (path: string, token: string) => {
      setLoading(true)
      try {
        const res = await listShareDir(shareId, path, token)
        setEntries(res.content)
        setSubPath(path)
      } catch (e) {
        toast.error(e instanceof Error ? e.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [shareId],
  )

  // 取得令牌后保存认证态；目录分享顺带列出根目录
  const applyAuth = useCallback(
    (a: ShareAuth) => {
      setAuth(a)
      if (a.isDir) {
        loadDir('/', a.token)
      }
    },
    [loadDir],
  )

  // 加载元信息；无密码分享直接尝试取令牌
  useEffect(() => {
    let alive = true
    getShareInfo(shareId)
      .then((i) => {
        if (!alive) return
        setInfo(i)
        if (!i.needPassword) {
          authShare(shareId).then((a) => alive && applyAuth(a)).catch((e) => alive && setError(e.message))
        }
      })
      .catch((e) => alive && setError(e.message))
    return () => {
      alive = false
    }
  }, [shareId, applyAuth])

  const submitPassword = async () => {
    try {
      applyAuth(await authShare(shareId, password))
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '密码错误')
    }
  }

  const download = (path: string, name: string) => {
    if (!auth) return
    if (!auth.allowDownload) {
      toast.error('该分享禁止下载')
      return
    }
    const a = document.createElement('a')
    a.href = shareDownloadUrl(shareId, path, auth.token)
    a.download = name
    a.click()
  }

  // ─── 渲染分支 ───────────────────────────────────────────────

  if (error) {
    return (
      <Centered>
        <div className="text-center">
          <Lock className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <div className="text-gray-600">{error}</div>
        </div>
      </Centered>
    )
  }

  if (!info) {
    return <Centered><div className="text-gray-400">加载中…</div></Centered>
  }

  // 需要密码且尚未认证
  if (info.needPassword && !auth) {
    return (
      <Centered>
        <div className="bg-white rounded-lg border p-6 w-80">
          <div className="font-medium text-gray-700 mb-1">{info.name}</div>
          <div className="text-sm text-gray-400 mb-4">该分享需要访问密码</div>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submitPassword()}
            placeholder="请输入密码"
            className="w-full border rounded px-2 py-1.5 mb-3"
            autoFocus
          />
          <button onClick={submitPassword} className="w-full py-1.5 rounded bg-primary text-white">访问</button>
        </div>
      </Centered>
    )
  }

  if (!auth) {
    return <Centered><div className="text-gray-400">加载中…</div></Centered>
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="h-14 border-b bg-white flex items-center px-6 font-semibold text-primary">
        AsukaFileList · 分享
      </header>
      <main className="max-w-3xl mx-auto p-6">
        <h2 className="text-lg font-medium text-gray-700 mb-4">{auth.name}</h2>

        {!auth.isDir ? (
          <div className="bg-white rounded-lg border p-6 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileText className="w-6 h-6 text-gray-400" />
              <span className="text-gray-700">{auth.name}</span>
            </div>
            {auth.allowDownload ? (
              <button onClick={() => download('/', auth.name)} className="flex items-center gap-1 px-3 py-1.5 rounded bg-primary text-white text-sm">
                <Download className="w-4 h-4" /> 下载
              </button>
            ) : (
              <span className="text-sm text-gray-400">禁止下载</span>
            )}
          </div>
        ) : (
          <div className="bg-white rounded-lg border overflow-hidden">
            <div className="flex items-center gap-2 px-3 py-2 border-b text-sm text-gray-500">
              {subPath !== '/' && (
                <button onClick={() => loadDir(parentPath(subPath), auth.token)} className="flex items-center gap-1 hover:text-primary">
                  <ArrowLeft className="w-4 h-4" /> 返回上级
                </button>
              )}
              <span className="font-mono">{subPath}</span>
            </div>
            <table className="w-full text-sm">
              <tbody>
                {loading && (
                  <tr><td className="px-3 py-8 text-center text-gray-400">加载中…</td></tr>
                )}
                {!loading && entries.length === 0 && (
                  <tr><td className="px-3 py-8 text-center text-gray-400">空目录</td></tr>
                )}
                {!loading && entries.map((f) => (
                  <tr key={f.path} className="border-b last:border-0 hover:bg-gray-50">
                    <td className="px-3 py-2">
                      {f.isDir ? (
                        <button onClick={() => loadDir(joinSub(subPath, f.name), auth.token)} className="flex items-center gap-2 text-gray-700 hover:text-primary">
                          <Folder className="w-4 h-4 text-amber-500" /> {f.name}
                        </button>
                      ) : (
                        <span className="flex items-center gap-2 text-gray-700">
                          <FileText className="w-4 h-4 text-gray-400" /> {f.name}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2 w-28 text-right text-gray-400">{f.isDir ? '' : formatSize(f.size)}</td>
                    <td className="px-3 py-2 w-16 text-right">
                      {!f.isDir && auth.allowDownload && (
                        <button onClick={() => download(joinSub(subPath, f.name), f.name)} title="下载" className="text-gray-400 hover:text-primary">
                          <Download className="w-4 h-4" />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen bg-gray-50 flex items-center justify-center">{children}</div>
}
