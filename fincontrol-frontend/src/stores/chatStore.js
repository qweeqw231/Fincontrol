import { create } from 'zustand'
import apiClient from '../api/client.js'
import { ENDPOINTS } from '../api/endpoints.js'

const PIN_STORAGE_KEY = 'fincontrol.pinnedConversations'

function readPinnedIds() {
  try {
    const raw = localStorage.getItem(PIN_STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function writePinnedIds(ids) {
  try {
    localStorage.setItem(PIN_STORAGE_KEY, JSON.stringify(ids))
  } catch {
    /* ignore */
  }
}

/**
 * 对话列表按置顶状态 + 最后消息时间排序。
 * 置顶的排前面；同组内按 lastMessageAt 倒序。
 */
function sortConversations(items, pinnedIds) {
  const pinnedSet = new Set(pinnedIds)
  return [...items].sort((a, b) => {
    const aPinned = pinnedSet.has(a.conversationId) ? 1 : 0
    const bPinned = pinnedSet.has(b.conversationId) ? 1 : 0
    if (aPinned !== bPinned) return bPinned - aPinned
    const aTime = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0
    const bTime = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0
    return bTime - aTime
  })
}

/**
 * chatStore — AI 顾问页（1b.4）
 */
export const useChatStore = create((set, get) => ({
  // state
  conversations: [],              // 已按置顶+时间排序的对话列表
  currentConversationId: null,
  messages: [],
  loading: false,
  sending: false,
  error: null,
  pinnedIds: readPinnedIds(),

  // actions
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  /** 加载对话列表 */
  loadConversations: async () => {
    set({ loading: true, error: null })
    try {
      const data = await apiClient.get(ENDPOINTS.CONVERSATIONS, { params: { type: 'ai_assistant' } })
      const items = data?.items || []
      const sorted = sortConversations(items, get().pinnedIds)
      set({ conversations: sorted, loading: false })
    } catch (err) {
      set({ loading: false, error: err?.message || '加载对话列表失败' })
    }
  },

  /** 选中并加载某条对话的历史消息 */
  loadConversation: async (id) => {
    set({ currentConversationId: id, messages: [], error: null })
    try {
      const data = await apiClient.get(ENDPOINTS.CONVERSATION_BY_ID(id))
      set({ messages: data?.messages || [] })
    } catch (err) {
      set({ error: err?.message || '加载对话详情失败' })
    }
  },

  /** 新建空对话 */
  createConversation: async () => {
    try {
      const data = await apiClient.post(ENDPOINTS.CONVERSATIONS, { type: 'ai_assistant' })
      const newId = data?.conversationId
      if (newId) {
        const newItem = {
          conversationId: newId,
          type: 'ai_assistant',
          createdAt: new Date().toISOString(),
          lastMessageAt: null,
        }
        const items = [newItem, ...get().conversations]
        set({
          conversations: sortConversations(items, get().pinnedIds),
          currentConversationId: newId,
          messages: [],
        })
        return newId
      }
    } catch (err) {
      set({ error: err?.message || '新建对话失败' })
    }
    return null
  },

  /** 发送消息（多轮） */
  sendMessage: async (message) => {
    const { currentConversationId, messages } = get()
    if (!message?.trim()) return

    // 乐观追加 user 消息
    const userMsg = {
      role: 'user',
      content: message,
      createdAt: new Date().toISOString(),
    }
    set({ messages: [...messages, userMsg], sending: true, error: null })

    try {
      const data = await apiClient.post(ENDPOINTS.CHAT_SEND, {
        conversationId: currentConversationId,
        message,
      })
      // 后端返回 userMessage + assistantMessage + intentClassification
      const assistant = data?.assistantMessage
      const intent = data?.intentClassification
      const newMessages = [...messages, userMsg]
      if (assistant) {
        newMessages.push({
          ...assistant,
          intentClassification: intent || null,
        })
      }
      // 如果是新对话，更新 conversationId
      const convId = data?.conversationId || currentConversationId
      set({
        messages: newMessages,
        currentConversationId: convId,
        sending: false,
      })
      // 刷新列表（更新 lastMessageAt）
      get().loadConversations()
    } catch (err) {
      set({
        sending: false,
        error: err?.message || '发送失败',
      })
    }
  },

  /** 删除对话 */
  deleteConversation: async (id) => {
    try {
      await apiClient.delete(ENDPOINTS.CONVERSATION_BY_ID(id))
      const remaining = get().conversations.filter((c) => c.conversationId !== id)
      const pinned = get().pinnedIds.filter((pid) => pid !== id)
      writePinnedIds(pinned)
      set({
        conversations: remaining,
        pinnedIds: pinned,
        currentConversationId: get().currentConversationId === id ? null : get().currentConversationId,
        messages: get().currentConversationId === id ? [] : get().messages,
      })
    } catch (err) {
      set({ error: err?.message || '删除对话失败' })
    }
  },

  /** 切换置顶 */
  togglePin: (id) => {
    const pinned = get().pinnedIds
    let next
    if (pinned.includes(id)) {
      next = pinned.filter((pid) => pid !== id)
    } else {
      next = [...pinned, id]
    }
    writePinnedIds(next)
    set({
      pinnedIds: next,
      conversations: sortConversations(get().conversations, next),
    })
  },

  reset: () => set({
    conversations: [],
    currentConversationId: null,
    messages: [],
    loading: false,
    sending: false,
    error: null,
  }),
}))
