import { create } from 'zustand'
import { apiClient } from '../api/client.js'
import { ENDPOINTS } from '../api/endpoints.js'

const DEFAULT_TARGET_RATIOS = {
  货币类: 10,
  固收类: 15,
  商品类: 25,
  A股权益类: 25,
  海外权益类: 20,
  港股大中华类: 5,
}

/**
 * userConfigStore — 配置页 + 月度操作台共用
 * 字段定义参考 acceptance-criteria §1b.5 和 api-contract §6
 *
 * PR3plus 决策 30/31：新增 maxSnapshotAgeDays 字段（settings 表全局配置）
 *  - 默认 7（API fallback 行为）
 *  - 取值通过 fetchMaxSnapshotAgeDays(userId) 从后端读
 *  - 修改通过 setMaxSnapshotAgeDays(userId, days) 调 PUT 写
 */
export const useUserConfigStore = create((set, get) => ({
  // 1b.5 配置 state
  targetRatios: { ...DEFAULT_TARGET_RATIOS },
  budgetLimit: 1000,
  purchaseThreshold: 100,
  highVolDcaBudget: 560,
  loaded: false,
  loading: false,
  error: null,

  // PR3plus 决策 30/31：maxSnapshotAgeDays（-1 / 7 / 14 / 30 / 180）
  maxSnapshotAgeDays: 7,
  maxAgeLoaded: false,
  maxAgeLoading: false,
  maxAgeError: null,

  // 1b.5 actions
  setConfig: (data) => set({
    targetRatios: data.targetRatios || DEFAULT_TARGET_RATIOS,
    budgetLimit: data.budgetLimit ?? 1000,
    purchaseThreshold: data.purchaseThreshold ?? 100,
    highVolDcaBudget: data.highVolDcaBudget ?? 560,
    loaded: true,
  }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),

  // PR3plus 决策 30/31：actions
  /**
   * 调 GET /api/settings/{userId}/max-snapshot-age-days 拿当前限制
   */
  fetchMaxSnapshotAgeDays: async (userId) => {
    set({ maxAgeLoading: true, maxAgeError: null })
    try {
      const data = await apiClient.get(ENDPOINTS.SETTINGS_MAX_AGE_GET(userId))
      const days = data?.maxSnapshotAgeDays ?? 7
      set({ maxSnapshotAgeDays: days, maxAgeLoaded: true, maxAgeLoading: false })
      return days
    } catch (e) {
      set({ maxAgeError: e?.message || 'fetch settings failed', maxAgeLoading: false })
      throw e
    }
  },

  /**
   * 调 PUT /api/settings/{userId}/max-snapshot-age-days 写新限制
   * @param userId 默认 1
   * @param days -1 / 7 / 14 / 30 / 180
   */
  setMaxSnapshotAgeDays: async (userId, days) => {
    set({ maxAgeLoading: true, maxAgeError: null })
    try {
      const updated = await apiClient.put(
        ENDPOINTS.SETTINGS_MAX_AGE_PUT(userId),
        { days }
      )
      const newDays = updated?.maxSnapshotAgeDays ?? days
      set({ maxSnapshotAgeDays: newDays, maxAgeLoaded: true, maxAgeLoading: false })
      return newDays
    } catch (e) {
      set({ maxAgeError: e?.message || 'update settings failed', maxAgeLoading: false })
      throw e
    }
  },

  reset: () => set({
    targetRatios: { ...DEFAULT_TARGET_RATIOS },
    budgetLimit: 1000,
    purchaseThreshold: 100,
    highVolDcaBudget: 560,
    loaded: false,
    loading: false,
    error: null,
    maxSnapshotAgeDays: 7,
    maxAgeLoaded: false,
    maxAgeLoading: false,
    maxAgeError: null,
  }),
}))
