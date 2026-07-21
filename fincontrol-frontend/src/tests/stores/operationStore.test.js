import { describe, it, expect, beforeEach } from 'vitest'
import { useOperationStore } from '../../stores/operationStore.js'

describe('operationStore', () => {
  beforeEach(() => {
    useOperationStore.getState().reset()
  })

  it('初始 state 正确', () => {
    const s = useOperationStore.getState()
    expect(s.recentOperations).toEqual([])
    expect(s.loading).toBe(false)
    expect(s.error).toBeNull()
  })

  it('setRecent() 能设置操作列表', () => {
    const items = [
      { operationType: 'screenshot_parse', operationDate: '2026-07-21' },
      { operationType: 'monthly_correction', operationDate: '2026-07-01' },
    ]
    useOperationStore.getState().setRecent(items)
    const s = useOperationStore.getState()
    expect(s.recentOperations).toEqual(items)
  })

  it('appendOperation() 把新项放到最前', () => {
    const old = { operationType: 'screenshot_parse', operationDate: '2026-07-20' }
    useOperationStore.getState().setRecent([old])

    const fresh = { operationType: 'monthly_correction', operationDate: '2026-07-21' }
    useOperationStore.getState().appendOperation(fresh)

    const s = useOperationStore.getState()
    expect(s.recentOperations[0]).toEqual(fresh)
    expect(s.recentOperations[1]).toEqual(old)
    expect(s.recentOperations.length).toBe(2)
  })
})