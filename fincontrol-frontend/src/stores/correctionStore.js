import { create } from 'zustand'
import { apiClient } from '../api/client.js'
import {
  CORRECTION_OPERATIONS,
  CORRECTION_OPERATION_DETAIL,
} from '../api/endpoints.js'

/**
 * correctionStore — 2a 校正页共用：校正与操作记录列表 + 单条详情回放。
 *
 * <p>月度台（/correction）与季度台（/quarterly）共用同一数据源
 * `GET /api/correction/operations`；详情抽屉通过 `openDetail(id)` 拉取：
 * 主表字段 + 迭代轮次 + 逐资产明细 + 参数指标。
 */
export const useCorrectionStore = create((set, get) => ({
  // 列表
  operations: [],
  operationsLoading: false,
  operationsError: null,

  // 详情（回放）
  detail: null,
  detailId: null,
  detailLoading: false,
  detailError: null,

  setOperations: (items) => set({ operations: items || [] }),

  async fetchOperations() {
    set({ operationsLoading: true, operationsError: null })
    try {
      const res = await apiClient.get(CORRECTION_OPERATIONS)
      set({ operations: res?.items || [], operationsLoading: false })
      return res?.items || []
    } catch (err) {
      set({
        operationsLoading: false,
        operationsError: err?.message || '加载校正记录失败',
      })
      return []
    }
  },

  async openDetail(id) {
    set({ detailId: id, detailLoading: true, detailError: null })
    try {
      const res = await apiClient.get(CORRECTION_OPERATION_DETAIL(id))
      set({ detail: res, detailLoading: false })
      return res
    } catch (err) {
      set({
        detailLoading: false,
        detailError: err?.message || '加载记录详情失败',
      })
      return null
    }
  },

  closeDetail: () => set({ detail: null, detailId: null, detailError: null }),

  /** 校正确认入库后：刷新列表并关闭详情 */
  async refreshAfterConfirm() {
    await get().fetchOperations()
  },

  reset: () =>
    set({
      operations: [],
      operationsLoading: false,
      operationsError: null,
      detail: null,
      detailId: null,
      detailLoading: false,
      detailError: null,
    }),
}))

export default useCorrectionStore