// @vitest-environment jsdom
/**
 * 1b4pr6b-recovery (2026-07-25)：HomePage 订阅 refreshCounter 的回归测试
 *
 * 场景：
 *   1. User 进入首页（HomePage mount）→ 触发 fetchLatest(1)
 *   2. 切到 DataPage → 上传 + confirm → DataPage 调 bumpRefresh()
 *   3. 回到 HomePage（仍挂载） → 因 [refreshCounter] 依赖触发 useEffect → 再次 fetchLatest(1)
 *
 * 修复前：Bump 后用户回首页仍看到旧数据（!snap 守卫永不触发再拉）。
 * 修复后：每次 refreshCounter 变化都会强制重新拉取。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, act, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useAssetSnapshotStore } from '../../stores/assetSnapshotStore.js'

// Mock apiClient 让 fetchLatest 走 mock 不打真实后端
vi.mock('../../api/client.js', () => {
  return {
    apiClient: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  }
})

// 用一个动态 import 拿到 mock 实例（必须在 vi.mock 之后）
import { apiClient } from '../../api/client.js'

// 在 import 之后再覆盖 default
import apiClientDefault from '../../api/client.js'

import HomePage from '../../pages/HomePage.jsx'

describe('HomePage - 1b4pr6b-recovery refreshCounter 订阅', () => {
  beforeEach(() => {
    useAssetSnapshotStore.getState().reset()
    vi.clearAllMocks()
  })

  it('mount 时调一次 fetchLatest（fetchLatest→apiClient.get count >= 4）', async () => {
    // apiClient.get 链式返回：balance / snapshot / operations / cumulative
    apiClient.get.mockResolvedValue({ data: null })

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    // 等异步 fetchLatest 完成
    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalled()
    })
    expect(apiClient.get.mock.calls.length).toBeGreaterThanOrEqual(4)
  })

  it('已有数据时，再调 bumpRefresh() 仍触发再拉（关键回归用例）', async () => {
    // 第一轮：返回空
    apiClient.get.mockResolvedValueOnce({ data: null })

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    // 等首次 mount fetchLatest 落定
    await waitFor(() => {
      expect(apiClient.get.mock.calls.length).toBeGreaterThanOrEqual(4)
    })

    // 模拟 store 里已有数据（DataPage confirm 后状态）
    act(() => {
      useAssetSnapshotStore.setState({
        latestSnapshot: { snapshotDate: '2026-07-24', sixCategoriesTotal: 7623.14 },
        refreshCounter: 0,
      })
    })
    const callsAfterMount = apiClient.get.mock.calls.length

    // 第二轮：bumpRefresh → refreshCounter 0→1
    await act(async () => {
      useAssetSnapshotStore.getState().bumpRefresh()
      await new Promise((r) => setTimeout(r, 50))
    })

    // 关键断言：bump 后即便 snap 已有值，也必须再触发 fetchLatest
    expect(useAssetSnapshotStore.getState().refreshCounter).toBe(1)
    expect(apiClient.get.mock.calls.length).toBeGreaterThan(callsAfterMount)
  })

  it('连续多次 bumpRefresh，每次都触发再拉（保持订阅链路活的）', async () => {
    apiClient.get.mockResolvedValue({ data: null })

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalled()
    })
    const initialCalls = apiClient.get.mock.calls.length

    await act(async () => {
      useAssetSnapshotStore.getState().bumpRefresh()
      await new Promise((r) => setTimeout(r, 30))
    })
    await act(async () => {
      useAssetSnapshotStore.getState().bumpRefresh()
      await new Promise((r) => setTimeout(r, 30))
    })
    await act(async () => {
      useAssetSnapshotStore.getState().bumpRefresh()
      await new Promise((r) => setTimeout(r, 30))
    })

    // 3 次 bump 后应至少有 3 次额外 fetch（每 refreshCounter 变化触发 1 次）
    expect(apiClient.get.mock.calls.length).toBeGreaterThanOrEqual(initialCalls + 4 * 3)
    expect(useAssetSnapshotStore.getState().refreshCounter).toBe(3)
  })
})
