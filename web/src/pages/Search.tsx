import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { searchFiles } from '../api/fs'
import { Folder, File, Search as SearchIcon } from 'lucide-react'

const PER_PAGE = 50

export default function Search() {
  const [input, setInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  // nonce 保证即使关键字不变，每次点击搜索也强制重新拉取（索引可能已变化）
  const [nonce, setNonce] = useState(0)

  const { data, isLoading, isFetching, isError, error } = useQuery({
    queryKey: ['fs-search', keyword, page, nonce],
    queryFn: () => searchFiles(keyword, page, PER_PAGE),
    enabled: keyword.trim().length > 0,
    staleTime: 0,
  })

  const submit = () => {
    setPage(1)
    setNonce((n) => n + 1)
    setKeyword(input.trim())
  }

  const results = data?.content || []
  const total = data?.total || 0
  const hasMore = page * PER_PAGE < total

  return (
    <div>
      <h2 className="text-lg font-medium text-gray-700 mb-4">文件名搜索</h2>

      <div className="flex items-center gap-2 mb-4">
        <input
          value={input}
          autoFocus
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
          placeholder="输入文件名关键字"
          className="flex-1 max-w-md text-sm px-3 py-2 border rounded"
        />
        <button onClick={submit} disabled={isFetching} className="flex items-center gap-1 text-sm px-3 py-2 border rounded bg-primary text-white hover:opacity-90 disabled:opacity-60">
          <SearchIcon className="w-4 h-4" /> 搜索
        </button>
      </div>

      {keyword && (
        <div className="bg-white rounded-lg border overflow-hidden">
          {isLoading && <div className="p-8 text-center text-gray-500">搜索中...</div>}
          {isError && <div className="p-8 text-center text-red-500">{error instanceof Error ? error.message : '搜索失败'}</div>}
          {!isLoading && !isError && (
            <>
              <div className="px-3 py-2 text-xs text-gray-400 border-b">命中索引 {total} 项（按权限过滤后展示本页）</div>
              <table className="w-full text-sm">
                <tbody>
                  {results.length === 0 && (
                    <tr><td className="px-3 py-8 text-center text-gray-400">无匹配结果</td></tr>
                  )}
                  {results.map((r, idx) => (
                    <tr key={idx} className="border-b last:border-0 hover:bg-gray-50">
                      <td className="pl-3 w-8">
                        {r.isDir ? <Folder className="w-4 h-4 text-amber-500" /> : <File className="w-4 h-4 text-gray-400" />}
                      </td>
                      <td className="py-2 font-medium">{r.name}</td>
                      <td className="py-2 text-gray-400 font-mono text-xs">{r.path}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {(page > 1 || hasMore) && (
                <div className="flex items-center justify-between px-3 py-2 border-t text-sm">
                  <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)} className="px-2 py-1 border rounded disabled:opacity-40">上一页</button>
                  <span className="text-gray-400 text-xs">第 {page} 页</span>
                  <button disabled={!hasMore} onClick={() => setPage((p) => p + 1)} className="px-2 py-1 border rounded disabled:opacity-40">下一页</button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
