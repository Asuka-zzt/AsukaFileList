import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import type { LoginRequest } from '../api/auth'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { toast } from 'sonner'

export default function Login() {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { setAuth } = useAuthStore()

  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || '/'

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password) {
      toast.error('请输入用户名和密码')
      return
    }
    setLoading(true)
    try {
      const res = await login({ username, password } as LoginRequest)
      setAuth(res.accessToken, res.user)
      toast.success(`欢迎回来，${res.user.username}`)
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="w-full max-w-sm bg-white rounded-xl shadow p-8">
        <div className="text-center mb-6">
          <h1 className="text-2xl font-semibold text-primary">AsukaFileList</h1>
          <p className="text-gray-500 text-sm mt-1">网盘挂载 + 语义知识库</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm mb-1 text-gray-600">用户名</label>
            <input
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-primary"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="admin"
              autoComplete="username"
            />
          </div>
          <div>
            <label className="block text-sm mb-1 text-gray-600">密码</label>
            <input
              type="password"
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-primary"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-2 bg-primary text-white py-2 rounded font-medium disabled:opacity-60 hover:bg-blue-600 transition"
          >
            {loading ? '登录中...' : '登录'}
          </button>
        </form>

        <p className="text-center text-xs text-gray-400 mt-6">
          默认管理员账号通常为 admin（密码见环境变量）
        </p>
      </div>
    </div>
  )
}
