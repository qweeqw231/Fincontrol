import { create } from 'zustand'
import { apiClient } from '../api/client'
import {
  ASSET_BALANCE,
  ASSET_OPERATIONS_RECENT,
  ASSET_CUMULATIVE_RETURN,
  SNAPSHOT_LATEST,
} from '../api/endpoints'

/**
 * assetSnapshotStore — 首页 + 数据管理页共用
 * <p>字段定义参考 acceptance-criteria §1b.6-1b.9 和 api-contract §3.1 / §10.1 / §10.2 / §9.3
 * <p>1b.2 新增 {@code cumulativeReturn}（决策 4 v2 累计收益率 / 口径 A）
 * <p>1b.2 新增 {@code refreshCounter}（/data confirm 后 bump，HomePage 监听重拉）
 */
export const useAssetSnapshotStore = create((set, get) => ({
  // state
  latestSnapshot: null,       // GET /api/snapshot/latest 响应
  balance: null,              // GET /api/asset/balance 响应
  operationsRecent: [],       // GET /api/asset/operations/recent 响应
  cumulativeReturn: null,     // GET /api/asset/cumulative-return 响应（1b.2 决策 4 v2 / 口径 A）
  refreshCounter: 0,          // 1b.2 全局联动：DataPage confirm 后 bump，HomePage 监听重拉
  loading: false,
  error: null,

  // actions
  setLatest: (data) => set({ latestSnapshot: data }),
  setBalance: (data) => set({ balance: data }),
  setOperations: (data) => set({ operationsRecent: data }),
  setCumulativeReturn: (data) => set({ cumulativeReturn: data }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  // 1b.2 全局联动（/data confirm 后调）：递增 counter，触发 HomePage 监听重拉
  bumpRefresh: () => set((s) => ({ refreshCounter: (s.refreshCounter || 0) + 1 })),

  // 批量刷新首页 4 个数据源（1b.2 调用 / 1b.3 /data confirm 后调用）
  fetchLatest: async (userId = 1) => {
    set({ loading: true, error: null })
    try {
      const [bal, snap, ops, cum] = await Promise.all([
        apiClient.get(ASSET_BALANCE, { headers: { 'X-User-Id': String(userId) } }),
        apiClient.get(SNAPSHOT_LATEST, {
          params: { includeDetail: true },
          headers: { 'X-User-Id': String(userId) },
        }),
        apiClient.get(ASSET_OPERATIONS_RECENT, {
          params: { limit: 5 },
          headers: { 'X-User-Id': String(userId) },
        }),
        apiClient.get(ASSET_CUMULATIVE_RETURN, {
          headers: { 'X-User-Id': String(userId) },
        }),
      ])
      set({
        balance: bal,
        latestSnapshot: snap,
        operationsRecent: ops?.items || [],
        cumulativeReturn: cum,
        loading: false,
      })
    } catch (err) {
      set({ error: err?.message || 'fetchLatest failed', loading: false })
    }
  },

  // 批量刷新（1b.2 调用）
  reset: () => set({
    latestSnapshot: null,
    balance: null,
    operationsRecent: [],
    cumulativeReturn: null,
    refreshCounter: 0,
    loading: false,
    error: null,
  }),
}))
