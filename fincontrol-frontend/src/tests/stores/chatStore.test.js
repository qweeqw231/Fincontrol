import { describe, it, expect, beforeEach, vi } from 'vitest'

// 2026-10-06 修复：store API 已从 setConversations/selectConversation/appendMessage
// 演进为 API 驱动的 loadConversations/loadConversation/sendMessage —— 测试同步对齐实现，
// mock apiClient 后验证真实行为（原用例断言的方法已不存在）。
vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

import apiClient from '../../api/client.js'
import { useChatStore } from '../../stores/chatStore.js'

describe('chatStore', () => {
  beforeEach(() => {
    useChatStore.getState().reset()
    vi.clearAllMocks()
  })

  it('初始 state 正确', () => {
    const s = useChatStore.getState()
    expect(s.conversations).toEqual([])
    expect(s.currentConversationId).toBeNull()
    expect(s.messages).toEqual([])
    expect(s.loading).toBe(false)
    expect(s.error).toBeNull()
  })

  it('loadConversation(id)：设置当前对话并加载历史消息', async () => {
    apiClient.get.mockResolvedValueOnce({
      messages: [{ role: 'user', content: 'hello' }],
    })

    await useChatStore.getState().loadConversation('c2')

    const s = useChatStore.getState()
    expect(s.currentConversationId).toBe('c2')
    expect(s.messages).toEqual([{ role: 'user', content: 'hello' }])
    expect(s.error).toBeNull()
  })

  it('sendMessage()：乐观追加 user 消息并写入 assistant 回复', async () => {
    apiClient.post.mockResolvedValueOnce({
      conversationId: 'c1',
      assistantMessage: { role: 'assistant', content: 'B' },
      intentClassification: { result: true, latencyMs: 1 },
    })
    // sendMessage 成功后内部会刷新对话列表（loadConversations → GET）
    apiClient.get.mockResolvedValue({ items: [] })

    await useChatStore.getState().sendMessage('A')

    const s = useChatStore.getState()
    expect(s.messages.length).toBe(2)
    expect(s.messages[0]).toMatchObject({ role: 'user', content: 'A' })
    expect(s.messages[1]).toMatchObject({ role: 'assistant', content: 'B' })
    expect(s.sending).toBe(false)
  })
})