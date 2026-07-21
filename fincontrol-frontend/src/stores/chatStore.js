import { create } from 'zustand'

/**
 * chatStore — AI 顾问页（1b.4）专用
 * 字段定义参考 acceptance-criteria §1b.20-1b.22 和 api-contract §9
 */
export const useChatStore = create((set, get) => ({
  // state
  conversations: [],              // GET /api/conversations 响应 items
  currentConversationId: null,    // 当前激活的对话 ID
  messages: [],                   // 当前对话的 messages 列表
  loading: false,
  error: null,

  // actions
  setConversations: (items) => set({ conversations: items || [] }),
  setCurrentConversation: (id) => set({ currentConversationId: id }),
  setMessages: (messages) => set({ messages: messages || [] }),
  appendMessage: (msg) => set((state) => ({
    messages: [...state.messages, msg],
  })),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  // 切换当前对话（1b.21）
  selectConversation: (id) => {
    const conv = get().conversations.find((c) => c.conversationId === id)
    set({
      currentConversationId: id,
      messages: conv?.messages || [],
    })
  },

  reset: () => set({
    conversations: [],
    currentConversationId: null,
    messages: [],
    loading: false,
    error: null,
  }),
}))