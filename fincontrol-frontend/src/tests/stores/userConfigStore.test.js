import { describe, it, expect, beforeEach } from 'vitest'
import { useUserConfigStore } from '../../stores/userConfigStore.js'

describe('userConfigStore', () => {
  beforeEach(() => {
    useUserConfigStore.getState().reset()
  })

  it('初始 state 正确（默认 6 大类比例 + 预算）', () => {
    const s = useUserConfigStore.getState()
    expect(s.targetRatios).toEqual({
      货币类: 10, 固收类: 15, 商品类: 25, A股权益类: 25, 海外权益类: 20, 港股大中华类: 5,
    })
    expect(s.budgetLimit).toBe(1000)
    expect(s.purchaseThreshold).toBe(100)
    expect(s.highVolDcaBudget).toBe(560)
    expect(s.loaded).toBe(false)
  })

  it('setConfig() 能更新配置', () => {
    useUserConfigStore.getState().setConfig({
      targetRatios: { 货币类: 15, 固收类: 20, 商品类: 20, A股权益类: 20, 海外权益类: 20, 港股大中华类: 5 },
      budgetLimit: 1500,
      purchaseThreshold: 200,
      highVolDcaBudget: 700,
    })
    const s = useUserConfigStore.getState()
    expect(s.targetRatios.货币类).toBe(15)
    expect(s.budgetLimit).toBe(1500)
    expect(s.loaded).toBe(true)
  })

  it('reset() 能恢复默认 state', () => {
    const store = useUserConfigStore.getState()
    store.setConfig({
      targetRatios: { 货币类: 50, 固收类: 10, 商品类: 10, A股权益类: 10, 海外权益类: 10, 港股大中华类: 10 },
      budgetLimit: 9999,
    })
    store.reset()
    const s = useUserConfigStore.getState()
    expect(s.budgetLimit).toBe(1000)
    expect(s.loaded).toBe(false)
  })
})