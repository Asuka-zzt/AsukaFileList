import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { Storage, StorageCreate } from '../../api/admin'
import { listStorages, listDrivers, createStorage, updateStorage, enableStorage, disableStorage, deleteStorage } from '../../api/admin'
import { toast } from 'sonner'
import { Plus, Edit2, Power, Trash2 } from 'lucide-react'

export default function AdminStorages() {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Storage | null>(null)
  const [selectedDriver, setSelectedDriver] = useState<string>('')
  const [form, setForm] = useState<Partial<StorageCreate>>({ mountPath: '', driver: '', addition: {} })

  const { data: storages = [], isLoading: loadingStorages } = useQuery({
    queryKey: ['storages'],
    queryFn: listStorages,
  })

  const { data: drivers = [] } = useQuery({
    queryKey: ['drivers'],
    queryFn: listDrivers,
  })

  const currentDriverInfo = drivers.find(d => d.name === (form.driver || selectedDriver))

  // Mutations
  const createMut = useMutation({
    mutationFn: createStorage,
    onSuccess: () => {
      toast.success('存储创建成功')
      queryClient.invalidateQueries({ queryKey: ['storages'] })
      closeForm()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMut = useMutation({
    mutationFn: updateStorage,
    onSuccess: () => {
      toast.success('更新成功')
      queryClient.invalidateQueries({ queryKey: ['storages'] })
      closeForm()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const toggleMut = useMutation({
    mutationFn: ({ id, enable }: { id: number; enable: boolean }) => enable ? enableStorage(id) : disableStorage(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['storages'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMut = useMutation({
    mutationFn: deleteStorage,
    onSuccess: () => {
      toast.success('删除成功')
      queryClient.invalidateQueries({ queryKey: ['storages'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  function openCreate() {
    setEditing(null)
    setForm({ mountPath: '', driver: '', addition: {}, remark: '' })
    setSelectedDriver('')
    setShowForm(true)
  }

  function openEdit(s: Storage) {
    setEditing(s)
    setForm({
      mountPath: s.mountPath,
      driver: s.driver,
      addition: { ...(s.addition || {}) },
      remark: s.remark,
      orderNo: s.orderNo,
      disabled: s.disabled,
    })
    setSelectedDriver(s.driver)
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditing(null)
    setSelectedDriver('')
  }

  function handleDriverChange(name: string) {
    setSelectedDriver(name)
    setForm(prev => ({ ...prev, driver: name, addition: {} }))
  }

  function handleAdditionChange(key: string, value: unknown) {
    setForm(prev => ({
      ...prev,
      addition: { ...(prev.addition || {}), [key]: value },
    }))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.mountPath || !form.driver) {
      toast.error('mountPath 和 driver 必填')
      return
    }

    const payload: StorageCreate = {
      mountPath: form.mountPath,
      driver: form.driver,
      addition: form.addition || {},
      orderNo: form.orderNo,
      remark: form.remark,
      disabled: form.disabled ?? false,
    }

    if (editing) {
      updateMut.mutate({ ...payload, id: editing.id })
    } else {
      createMut.mutate(payload)
    }
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-medium">存储挂载管理</h2>
        <button onClick={openCreate} className="flex items-center gap-1 bg-primary text-white px-3 py-1.5 rounded text-sm hover:bg-blue-600">
          <Plus className="w-4 h-4" /> 添加存储
        </button>
      </div>

      <div className="bg-white border rounded overflow-x-auto">
        <table className="admin-table w-full text-sm min-w-[800px]">
          <thead>
            <tr className="bg-gray-50">
              <th>ID</th>
              <th>挂载路径</th>
              <th>驱动</th>
              <th>状态</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loadingStorages && <tr><td colSpan={6} className="p-4 text-center">加载中...</td></tr>}
            {!loadingStorages && storages.length === 0 && <tr><td colSpan={6} className="p-6 text-center text-gray-400">暂无存储，请添加</td></tr>}
            {storages.map(s => (
              <tr key={s.id}>
                <td>{s.id}</td>
                <td className="font-mono">{s.mountPath}</td>
                <td>{s.driver}</td>
                <td>
                  <span className={`text-xs px-2 py-0.5 rounded ${s.status === 'work' ? 'bg-green-100 text-green-700' : s.status === 'disabled' ? 'bg-gray-200' : 'bg-red-100 text-red-600'}`}>
                    {s.status}
                  </span>
                </td>
                <td className="text-gray-500 max-w-[200px] truncate">{s.remark}</td>
                <td className="space-x-1">
                  <button onClick={() => openEdit(s)} className="p-1 hover:text-primary" title="编辑"><Edit2 className="w-4 h-4 inline" /></button>
                  <button
                    onClick={() => toggleMut.mutate({ id: s.id, enable: s.disabled })}
                    className="p-1 hover:text-primary"
                    title={s.disabled ? '启用' : '禁用'}
                  >
                    <Power className="w-4 h-4 inline" />
                  </button>
                  <button onClick={() => { if (confirm('确认删除？')) deleteMut.mutate(s.id) }} className="p-1 hover:text-red-600" title="删除">
                    <Trash2 className="w-4 h-4 inline" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Dynamic Form Drawer / Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={closeForm}>
          <div className="bg-white w-full max-w-lg rounded-xl p-6" onClick={e => e.stopPropagation()}>
            <h3 className="text-lg font-medium mb-4">{editing ? '编辑存储' : '添加存储'}</h3>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="text-sm block mb-1">挂载路径 (mountPath)</label>
                <input
                  className="w-full border px-3 py-2 rounded"
                  value={form.mountPath || ''}
                  onChange={e => setForm({ ...form, mountPath: e.target.value })}
                  placeholder="/local 或 /data/docs"
                  required
                />
              </div>

              <div>
                <label className="text-sm block mb-1">驱动类型</label>
                <select
                  className="w-full border px-3 py-2 rounded"
                  value={form.driver || selectedDriver}
                  onChange={e => handleDriverChange(e.target.value)}
                  required
                  disabled={!!editing}
                >
                  <option value="">-- 请选择驱动 --</option>
                  {drivers.map(d => (
                    <option key={d.name} value={d.name}>{d.name}</option>
                  ))}
                </select>
                {currentDriverInfo && (
                  <p className="text-xs text-gray-500 mt-1">{currentDriverInfo.defaultRoot || ''}</p>
                )}
              </div>

              {/* Dynamic addition fields */}
              {currentDriverInfo && currentDriverInfo.items.length > 0 && (
                <div className="border rounded p-3 bg-gray-50 space-y-3">
                  <div className="text-xs font-medium text-gray-600 mb-1">驱动配置 (addition)</div>
                  {currentDriverInfo.items.map(item => (
                    <div key={item.name}>
                      <label className="text-sm block mb-0.5">
                        {item.label} {item.required && <span className="text-red-500">*</span>}
                      </label>
                      {item.type === 'boolean' ? (
                        <input
                          type="checkbox"
                          checked={!!form.addition?.[item.name]}
                          onChange={e => handleAdditionChange(item.name, e.target.checked)}
                        />
                      ) : (
                        <input
                          className="w-full border px-3 py-1.5 rounded text-sm"
                          type={item.type === 'integer' ? 'number' : 'text'}
                          value={(form.addition?.[item.name] as string | number | undefined) ?? item.defaultValue ?? ''}
                          placeholder={item.defaultValue}
                          onChange={e => handleAdditionChange(item.name, item.type === 'integer' ? Number(e.target.value) : e.target.value)}
                          required={item.required}
                        />
                      )}
                      {item.description && <p className="text-[11px] text-gray-400 mt-0.5">{item.description}</p>}
                    </div>
                  ))}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-sm block mb-1">排序号</label>
                  <input type="number" className="w-full border px-3 py-2 rounded" value={form.orderNo ?? 0} onChange={e => setForm({ ...form, orderNo: Number(e.target.value) })} />
                </div>
                <div>
                  <label className="text-sm block mb-1">备注</label>
                  <input className="w-full border px-3 py-2 rounded" value={form.remark || ''} onChange={e => setForm({ ...form, remark: e.target.value })} />
                </div>
              </div>

              <div className="flex gap-2 pt-2">
                <button type="button" onClick={closeForm} className="flex-1 border py-2 rounded">取消</button>
                <button type="submit" className="flex-1 bg-primary text-white py-2 rounded" disabled={createMut.isPending || updateMut.isPending}>
                  {editing ? '保存修改' : '创建存储'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
