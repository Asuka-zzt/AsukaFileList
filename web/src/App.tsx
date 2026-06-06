import { useEffect } from 'react'
import { Routes, Route, Navigate, useLocation, Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import { getCurrentUser, logout } from './api/auth'
import { toast } from 'sonner'

// Pages
import Login from './pages/Login'
import FileBrowser from './pages/FileBrowser'
import AdminStorages from './pages/admin/Storages'

// Simple protected route wrapper
function ProtectedRoute({ children, requireAdmin = false }: { children: React.ReactNode; requireAdmin?: boolean }) {
  // store 已在初始化时同步从 localStorage 恢复，user 在首屏即可用，无需再异步 hydrate
  const { user } = useAuthStore()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (requireAdmin && !user.admin) {
    toast.error('需要管理员权限')
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}

// Main layout with header + sidebar (AList-like)
function MainLayout({ children }: { children: React.ReactNode }) {
  const { user, clearAuth } = useAuthStore()
  const location = useLocation()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
      // 退出接口失败也继续清本地态
    }
    clearAuth()
    // SPA 内跳转，避免整页刷新
    navigate('/login', { replace: true })
  }

  const isAdminPath = location.pathname.startsWith('/admin')

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <div className="w-60 bg-white border-r flex flex-col">
        <div className="h-14 flex items-center px-4 border-b font-semibold text-lg text-primary">
          AsukaFileList
        </div>
        <div className="p-2 text-sm">
          <Link to="/" className={`block px-3 py-2 rounded hover:bg-gray-100 ${!isAdminPath ? 'bg-blue-50 text-primary font-medium' : ''}`}>
            📁 文件浏览
          </Link>
          {user?.admin && (
            <Link to="/admin/storages" className={`block px-3 py-2 rounded hover:bg-gray-100 mt-1 ${isAdminPath ? 'bg-blue-50 text-primary font-medium' : ''}`}>
              ⚙️ 存储管理
            </Link>
          )}
        </div>
        <div className="mt-auto p-3 text-xs text-gray-500 border-t">
          {user ? `已登录：${user.username}` : ''}
          <br />
          basePath: {user?.basePath || '/'}
        </div>
      </div>

      {/* Main */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="h-14 border-b bg-white flex items-center px-4 justify-between">
          <div className="font-medium text-gray-700">
            {isAdminPath ? '管理后台' : '文件列表'}
          </div>
          <div className="flex items-center gap-3 text-sm">
            {user?.admin && !isAdminPath && (
              <Link to="/admin/storages" className="text-primary hover:underline">进入管理</Link>
            )}
            <button onClick={handleLogout} className="text-gray-500 hover:text-red-600">
              退出登录
            </button>
            <span className="text-gray-400">|</span>
            <span>{user?.username}</span>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-auto p-4">
          {children}
        </main>
      </div>
    </div>
  )
}

function App() {
  const { token, setAuth, clearAuth } = useAuthStore()

  // 启动时若已有 token，向 /api/me 校验并刷新用户信息；token 失效则清除登录态。
  // 仅在挂载时执行一次。
  useEffect(() => {
    if (!token) return
    getCurrentUser()
      .then((u) => setAuth(token, u))
      .catch(() => clearAuth())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout>
              <FileBrowser />
            </MainLayout>
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/storages"
        element={
          <ProtectedRoute requireAdmin>
            <MainLayout>
              <AdminStorages />
            </MainLayout>
          </ProtectedRoute>
        }
      />

      {/* Fallback */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
