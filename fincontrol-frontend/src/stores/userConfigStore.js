import { create } from 'zustand'

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
 */
export const useUserConfigStore = create((set) => ({
  // state
  targetRatios: { ...DEFAULT_TARGET_RATIOS },
  budgetLimit: 1000,
  purchaseThreshold: 100,
  highVolDcaBudget: 560,
  loaded: false,
  loading: false,
  error: null,

  // actions
  setConfig: (data) => set({
    targetRatios: data.targetRatios || DEFAULT_TARGET_RATIOS,
    budgetLimit: data.budgetLimit ?? 1000,
    purchaseThreshold: data.purchaseThreshold ?? 100,
    highVolDcaBudget: data.highVolDcaBudget ?? 560,
    loaded: true,
  }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),
  reset: () => set({
    targetRatios: { ...DEFAULT_TARGET_RATIOS },
    budgetLimit: 1000,
    purchaseThreshold: 100,
    highVolDcaBudget: 560,
    loaded: false,
    loading: false,
    error: null,
  }),
}))