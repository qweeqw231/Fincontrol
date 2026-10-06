import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('../../api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { apiClient } from '../../api/client.js'
import { useCorrectionStore } from '../../stores/correctionStore.js'

describe('correctionStore（2a 校正记录列表 + 详情回放）', () => {
  beforeEach(() => {
    useCorrectionStore.getState().reset()
    vi.clearAllMocks()
  })

  it('初始 state 正确', () => {
    const s = useCorrectionStore.getState()
    expect(s.operations).toEqual([])
    expect(s.detail).toBeNull()
    expect(s.operationsLoading).toBe(false)
    expect(s.detailLoading).toBe(false)
  })

  it('fetchOperations() 成功写入 items', async () => {
    apiClient.get.mockResolvedValue({ items: [{ id: 3, correctionMode: 'lqr_zoh' }] })
    const items = await useCorrectionStore.getState().fetchOperations()
    expect(items).toHaveLength(1)
    expect(useCorrectionStore.getState().operations[0].id).toBe(3)
    expect(apiClient.get).toHaveBeenCalledWith('/correction/operations')
  })

  it('fetchOperations() 失败时记录错误且返回空数组', async () => {
    apiClient.get.mockRejectedValue({ message: '网络错误' })
    const items = await useCorrectionStore.getState().fetchOperations()
    expect(items).toEqual([])
    expect(useCorrectionStore.getState().operationsError).toBe('网络错误')
  })

  it('openDetail()/closeDetail() 打开与关闭回放详情', async () => {
    apiClient.get.mockResolvedValue({
      operation: { id: 3 },
      iterations: [{ sortOrder: 1 }],
      assets: [],
      params: {},
    })
    await useCorrectionStore.getState().openDetail(3)
    const s = useCorrectionStore.getState()
    expect(s.detailId).toBe(3)
    expect(s.detail.operation.id).toBe(3)
    expect(apiClient.get).toHaveBeenCalledWith('/correction/operations/3')

    s.closeDetail()
    expect(useCorrectionStore.getState().detail).toBeNull()
  })

  it('openDetail() 失败时记录错误', async () => {
    apiClient.get.mockRejectedValue({ message: '不存在' })
    await useCorrectionStore.getState().openDetail(999)
    expect(useCorrectionStore.getState().detailError).toBe('不存在')
  })
})