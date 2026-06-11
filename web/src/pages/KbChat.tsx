import { useRef, useState } from 'react'
import { useParams, useSearchParams, Link } from 'react-router-dom'
import { streamChat, type ChatEvent, type ChatMessage } from '../api/kb'
import { toast } from 'sonner'
import { Send, ArrowLeft, Loader2 } from 'lucide-react'

interface Citation {
  index: number
  file_path: string
}

interface Msg {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  stage?: string // 进行中的阶段提示
}

const STAGE_LABEL: Record<string, string> = {
  route: '路由',
  decompose: '分解问题',
  decomposed: '已分解',
  retrieve: '检索中',
  grade: '评估证据',
  generate: '生成回答',
}

export default function KbChat() {
  const { kbId } = useParams<{ kbId: string }>()
  const id = Number(kbId)
  const [searchParams] = useSearchParams()
  const docIdParam = searchParams.get('docId')
  const docId = docIdParam ? Number(docIdParam) : undefined

  const [messages, setMessages] = useState<Msg[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const abortRef = useRef<AbortController | null>(null)

  const send = async () => {
    const question = input.trim()
    if (!question || busy) return
    setInput('')
    // 历史 = 已完成的对话
    const history: ChatMessage[] = messages.map((m) => ({ role: m.role, content: m.content }))
    setMessages((prev) => [...prev, { role: 'user', content: question }, { role: 'assistant', content: '', stage: 'decompose' }])
    setBusy(true)
    const controller = new AbortController()
    abortRef.current = controller

    const patchLast = (patch: Partial<Msg>) =>
      setMessages((prev) => {
        const next = [...prev]
        next[next.length - 1] = { ...next[next.length - 1], ...patch }
        return next
      })

    try {
      await streamChat(
        id,
        { question, history },
        {
          docId,
          signal: controller.signal,
          onEvent: (e: ChatEvent) => {
            if (e.type === 'token' && e.text) {
              setMessages((prev) => {
                const next = [...prev]
                const last = next[next.length - 1]
                next[next.length - 1] = { ...last, content: last.content + e.text, stage: undefined }
                return next
              })
            } else if (e.type === 'status') {
              patchLast({ stage: e.stage })
            } else if (e.type === 'citations') {
              patchLast({ citations: (e.items || []).map((i) => ({ index: i.index, file_path: i.file_path })) })
            } else if (e.type === 'error') {
              patchLast({ stage: undefined, content: (e.message || '出错了') })
              toast.error(e.message || '问答出错')
            }
          },
        },
      )
    } catch (err) {
      patchLast({ stage: undefined })
      toast.error(err instanceof Error ? err.message : '问答失败')
    } finally {
      setBusy(false)
      abortRef.current = null
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-lg font-medium text-gray-700 flex items-center gap-2">
          <Link to={`/kb/${id}/documents`} className="text-gray-400 hover:text-primary">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          {docId != null ? `单文档问答 · 文档 #${docId}` : '整库问答'}
        </h2>
      </div>

      {/* 消息列表 */}
      <div className="flex-1 overflow-auto space-y-3 pb-3">
        {messages.length === 0 && (
          <div className="text-center text-sm text-gray-400 mt-10">
            向{docId != null ? '该文档' : '该知识库'}提问，回答将带来源引用。
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[80%] rounded-lg px-4 py-2 text-sm whitespace-pre-wrap ${
                m.role === 'user' ? 'bg-primary text-white' : 'bg-white border text-gray-800'
              }`}
            >
              {m.role === 'assistant' && m.stage && !m.content && (
                <div className="flex items-center gap-2 text-gray-400">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  {STAGE_LABEL[m.stage] || m.stage}…
                </div>
              )}
              {m.content}
              {m.citations && m.citations.length > 0 && (
                <div className="mt-2 pt-2 border-t text-xs text-gray-500">
                  来源：
                  {m.citations.map((c) => (
                    <span key={c.index} className="mr-2">
                      [{c.index}] {c.file_path}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* 输入框 */}
      <div className="border-t pt-3 flex items-end gap-2">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            }
          }}
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          rows={2}
          className="flex-1 border rounded px-3 py-2 text-sm resize-none"
        />
        <button
          onClick={send}
          disabled={busy}
          className="flex items-center gap-1 text-sm px-4 py-2 rounded bg-primary text-white disabled:opacity-50"
        >
          {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          发送
        </button>
      </div>
    </div>
  )
}
