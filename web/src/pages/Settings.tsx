import { useState } from 'react'
import { useAuthStore } from '../stores/auth'
import { setWebdavPassword, clearWebdavPassword } from '../api/me'
import { toast } from 'sonner'
import { HardDrive, Copy } from 'lucide-react'

// WebDAV 挂载地址（生产环境前端由后端同源伺服，故用当前 origin）
function mountUrl(): string {
  return `${window.location.origin}/dav/`
}

export default function Settings() {
  const { user } = useAuthStore()
  const [password, setPassword] = useState('')
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    if (password.trim().length < 6) {
      toast.error('WebDAV 密码至少 6 位')
      return
    }
    setSaving(true)
    try {
      await setWebdavPassword(password.trim())
      toast.success('WebDAV 密码已设置，可用于挂载')
      setPassword('')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '设置失败')
    } finally {
      setSaving(false)
    }
  }

  const handleClear = async () => {
    if (!confirm('确定清除 WebDAV 密码？清除后将无法通过 WebDAV 登录。')) return
    try {
      await clearWebdavPassword()
      toast.success('已清除 WebDAV 密码')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '清除失败')
    }
  }

  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text)
    toast.success('已复制')
  }

  return (
    <div className="max-w-2xl">
      <h2 className="text-lg font-medium text-gray-700 mb-4">个人设置</h2>

      <div className="bg-white rounded-lg border p-5 space-y-4">
        <div className="flex items-center gap-2 text-gray-700 font-medium">
          <HardDrive className="w-5 h-5 text-primary" /> WebDAV 挂载
        </div>
        <p className="text-sm text-gray-500 leading-relaxed">
          设置一个<strong>专用 WebDAV 密码</strong>（与登录密码独立），即可在 Windows「映射网络驱动器」、
          macOS Finder 或 rclone 中挂载你的全部存储。本地 / 局域网即可，无需公网。
        </p>

        <div className="text-sm space-y-2">
          <div className="flex items-center justify-between bg-gray-50 rounded px-3 py-2">
            <span className="text-gray-500">挂载地址</span>
            <span className="flex items-center gap-2 font-mono text-gray-700">
              {mountUrl()}
              <button onClick={() => copy(mountUrl())} title="复制" className="text-gray-400 hover:text-primary">
                <Copy className="w-4 h-4" />
              </button>
            </span>
          </div>
          <div className="flex items-center justify-between bg-gray-50 rounded px-3 py-2">
            <span className="text-gray-500">用户名</span>
            <span className="font-mono text-gray-700">{user?.username}</span>
          </div>
        </div>

        <div>
          <label className="block text-sm text-gray-500 mb-1">设置 / 更新 WebDAV 密码</label>
          <div className="flex gap-2">
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少 6 位，用于挂载登录"
              className="flex-1 border rounded px-2 py-1.5"
            />
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-3 py-1.5 rounded bg-primary text-white disabled:opacity-50"
            >
              保存
            </button>
            <button onClick={handleClear} className="px-3 py-1.5 border rounded text-gray-600">
              清除
            </button>
          </div>
        </div>

        <p className="text-xs text-gray-400 leading-relaxed">
          提示：Windows 在明文 HTTP 上挂载需确保「WebClient」服务已启动；上传大文件可能需调高
          <code className="mx-1">FileSizeLimitInBytes</code>。公网暴露请务必启用 HTTPS。
        </p>
      </div>
    </div>
  )
}
