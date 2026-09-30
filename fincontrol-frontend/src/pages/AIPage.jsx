import { useEffect, useRef, useState } from 'react'
import { useChatStore } from '../stores/chatStore.js'
import '../styles/ai-page.css'

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return d.toTimeString().slice(0, 5)
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function getConvPreview(conv) {
  // 列表项预览：优先用最后消息时间，否则创建时间
  return conv.lastMessageAt ? formatTime(conv.lastMessageAt) : formatTime(conv.createdAt)
}

export default function AIPage() {
  const {
    conversations, currentConversationId, messages, loading, sending, error, pinnedIds,
    loadConversations, loadConversation, createConversation, sendMessage,
    deleteConversation, togglePin,
  } = useChatStore()

  const [input, setInput] = useState('')
  const messagesEndRef = useRef(null)
  const textareaRef = useRef(null)

  // 首次加载对话列表
  useEffect(() => {
    loadConversations()
  }, [])

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  const handleSend = () => {
    const msg = input.trim()
    if (!msg || sending) return
    // 如果没有选中对话，先创建一个
    if (!currentConversationId) {
      createConversation().then((id) => {
        if (id) sendMessage(msg)
      })
    } else {
      sendMessage(msg)
    }
    setInput('')
    if (textareaRef.current) textareaRef.current.style.height = 'auto'
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleTextareaInput = (e) => {
    setInput(e.target.value)
    // 自适应高度
    const ta = e.target
    ta.style.height = 'auto'
    ta.style.height = Math.min(ta.scrollHeight, 120) + 'px'
  }

  const handleSelectConv = (id) => {
    if (id === currentConversationId) return
    loadConversation(id)
  }

  const handleNewConv = () => {
    createConversation()
  }

  const currentConv = conversations.find((c) => c.conversationId === currentConversationId)

  return (
    <div className="ai-page">
      {/* 左侧对话栏 */}
      <aside className="ai-sidebar">
        <div className="ai-sidebar__header">
          <button className="ai-sidebar__new-btn" onClick={handleNewConv}>
            <span>＋</span> 新建对话
          </button>
        </div>
        <div className="ai-sidebar__list">
          {loading && conversations.length === 0 ? (
            <div className="ai-sidebar__empty">加载中…</div>
          ) : conversations.length === 0 ? (
            <div className="ai-sidebar__empty">暂无对话<br />点击上方新建</div>
          ) : (
            conversations.map((conv) => (
              <div
                key={conv.conversationId}
                className={`ai-conv-item ${conv.conversationId === currentConversationId ? 'is-active' : ''} ${pinnedIds.includes(conv.conversationId) ? 'is-pinned' : ''}`}
                onClick={() => handleSelectConv(conv.conversationId)}
              >
                <div className="ai-conv-item__title">
                  {conv.conversationId === currentConversationId ? '当前对话' : '新对话'}
                </div>
                <div className="ai-conv-item__time">{getConvPreview(conv)}</div>
                <div className="ai-conv-item__actions" onClick={(e) => e.stopPropagation()}>
                  <button
                    className="ai-conv-item__btn ai-conv-item__btn--pin"
                    title={pinnedIds.includes(conv.conversationId) ? '取消置顶' : '置顶'}
                    onClick={() => togglePin(conv.conversationId)}
                  >
                    {pinnedIds.includes(conv.conversationId) ? '📌' : '📍'}
                  </button>
                  <button
                    className="ai-conv-item__btn ai-conv-item__btn--del"
                    title="删除"
                    onClick={() => deleteConversation(conv.conversationId)}
                  >
                    ✕
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </aside>

      {/* 右侧聊天区 */}
      <section className="ai-chat">
        <div className="ai-chat__header">
          <div className="ai-chat__title">
            {currentConv ? 'AI 顾问' : 'AI 顾问'}
          </div>
        </div>

        <div className="ai-chat__messages">
          {messages.length === 0 && !sending ? (
            <div className="ai-empty">
              <div className="ai-empty__icon">🤖</div>
              <div className="ai-empty__title">你好，我是 AI 顾问</div>
              <div className="ai-empty__sub">
                我可以基于微观控制金融学理论回答你的投资决策问题。<br />
                试试问我"什么是 ZOH？"或"永久保留仓规则是什么？"
              </div>
            </div>
          ) : (
            messages.map((msg, i) => (
              <div key={i} className={`ai-msg ai-msg--${msg.role}`}>
                <div className="ai-msg__bubble">{msg.content}</div>
                {msg.role === 'assistant' && (
                  <div className="ai-msg__meta">
                    {msg.routedTo && (
                      <span className={`ai-badge ${msg.routedTo === 'main_loop' ? 'ai-badge--main' : 'ai-badge--garbage'}`}>
                        {msg.routedTo === 'main_loop' ? '投资决策' : '闲聊'}
                      </span>
                    )}
                    {msg.promptVersion && <span>{msg.promptVersion}</span>}
                    {msg.intentClassification && (
                      <span className="ai-msg__intent">
                        · 意图分类 {msg.intentClassification.result ? '✓' : '✗'}（{msg.intentClassification.latencyMs}ms）
                      </span>
                    )}
                  </div>
                )}
              </div>
            ))
          )}
          {sending && (
            <div className="ai-msg ai-msg--assistant">
              <div className="ai-typing">
                <span /><span /><span />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {error && <div style={{ padding: '0 20px', color: 'var(--color-error)', fontSize: 13 }}>{error}</div>}

        {/* 输入区 */}
        <div className="ai-input">
          <textarea
            ref={textareaRef}
            className="ai-input__textarea"
            placeholder="输入消息…（Enter 发送，Shift+Enter 换行）"
            value={input}
            onChange={handleTextareaInput}
            onKeyDown={handleKeyDown}
            rows={1}
          />
          <button
            className="ai-input__send"
            onClick={handleSend}
            disabled={!input.trim() || sending}
          >
            发送
          </button>
        </div>
      </section>
    </div>
  )
}
