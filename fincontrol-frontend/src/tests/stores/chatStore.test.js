import { describe, it, expect, beforeEach } from 'vitest'
import { useChatStore } from '../../stores/chatStore.js'

describe('chatStore', () => {
  beforeEach(() => {
    useChatStore.getState().reset()
  })

  it('初始 state 正确', () => {
    const s = useChatStore.getState()
    expect(s.conversations).toEqual([])
    expect(s.currentConversationId).toBeNull()
    expect(s.messages).toEqual([])
    expect(s.loading).toBe(false)
    expect(s.error).toBeNull()
  })

  it('setConversations + selectConversation 能切换当前对话', () => {
    const convs = [
      { conversationId: 'c1', type: 'ai_assistant', messages: [{ role: 'user', content: 'hi' }] },
      { conversationId: 'c2', type: 'ai_assistant', messages: [{ role: 'user', content: 'hello' }] },
    ]
    useChatStore.getState().setConversations(convs)
    useChatStore.getState().selectConversation('c2')

    const s = useChatStore.getState()
    expect(s.currentConversationId).toBe('c2')
    expect(s.messages).toEqual(convs[1].messages)
  })

  it('appendMessage() 把消息追加到当前对话', () => {
    useChatStore.getState().setMessages([{ role: 'user', content: 'A' }])
    useChatStore.getState().appendMessage({ role: 'assistant', content: 'B' })

    const s = useChatStore.getState()
    expect(s.messages.length).toBe(2)
    expect(s.messages[1]).toEqual({ role: 'assistant', content: 'B' })
  })
})