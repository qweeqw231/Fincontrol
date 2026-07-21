import { create } from 'zustand'

/**
 * assetSnapshotStore — 首页 + 数据管理页共用
 * 字段定义参考 acceptance-criteria §1b.6-1b.9 和 api-contract §3.1 / §10.1 / §10.2
 */
export const useAssetSnapshotStore = create((set) => ({
  // state
  latestSnapshot: null,    // GET /api/snapshot/latest 响应
  balance: null,           // GET /api/asset/balance 响应
  operationsRecent: [],    // GET /api/asset/operations/recent 响应
  loading: false,
  error: null,

  // actions
  setLatest: (data) => set({ latestSnapshot: data }),
  setBalance: (data) => set({ balance: data }),
  setOperations: (data) => set({ operationsRecent: data }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  // 批量刷新（1b.2 调用）
  reset: () => set({
    latestSnapshot: null,
    balance: null,
    operationsRecent: [],
    loading: false,
    error: null,
  }),
}))