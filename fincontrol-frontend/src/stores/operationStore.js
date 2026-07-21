import { create } from 'zustand'

/**
 * operationStore — 月度操作台 + 首页操作时间线共用
 * 字段定义参考 acceptance-criteria §1b.9 和 api-contract §10.2
 */
export const useOperationStore = create((set) => ({
  // state
  recentOperations: [],   // GET /api/asset/operations/recent 响应 items
  loading: false,
  error: null,

  // actions
  setRecent: (items) => set({ recentOperations: items || [] }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  // 单条追加（Phase 2 月度校正写入后调用）
  appendOperation: (op) => set((state) => ({
    recentOperations: [op, ...state.recentOperations].slice(0, 50),
  })),

  reset: () => set({
    recentOperations: [],
    loading: false,
    error: null,
  }),
}))