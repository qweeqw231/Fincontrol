import { describe, it, expect } from 'vitest'
import { buildQuarterlyConfirmPayload } from '../../utils/correctionPayload.js'

/**
 * 2a 季度确认入库载荷装配（9/30 场景 fixture）。
 */
const calcFixture = {
  vCurr: 9525.87,
  vHighVol: 7051.23,
  anchor: '海外权益类',
  preSix: [
    { category: '货币类', amount: 988.27, ratioActual: 10.38, ratioTarget: 10, deviation: 0.38 },
    { category: '固收类', amount: 1486.37, ratioActual: 15.60, ratioTarget: 15, deviation: 0.6 },
  ],
  highVolPre: [
    { category: '商品类', amount: 2207.71, ratioActual: 31.31, ratioTarget: 33.33, deviation: -2.02 },
    { category: '海外权益类', amount: 2152.13, ratioActual: 30.52, ratioTarget: 26.67, deviation: 3.85 },
  ],
  highVolPost: [
    { category: '商品类', amount: 2414.71, ratioActual: 32.49, ratioTarget: 33.33, deviation: -0.84 },
  ],
  lqr: {
    kktNote: '内点解不成立（港股大中华类 补仓为负）→ 边界解：置 0 后重解；',
    deltas: [
      { category: '商品类', deltaRaw: 207.12, deltaAmount: 207 },
      { category: '海外权益类', deltaRaw: 0, deltaAmount: 0, note: '锚定资产 Δ=0' },
    ],
  },
  icDrr: { fBefore: 21.41, fAfter: 6.97, ratioPct: 67.4 },
  zohStep: { eHighAtStep: 492.8, totalAfterStep: 1357.08 },
  params: {
    surplus: 700,
    mMax: 1000,
    mMaxSource: 'manual',
    uHigh: 618,
    uMonetaryDca: 0,
    uBondDca: 0,
    alphaInit: 0.2,
    alphaDecay: 0.8,
    purchaseThreshold: 100,
    mode: 'mMaxCapped',
  },
  chosen: {
    alphaFinal: 0.3032,
    eHigh: 382,
    deltaMRaw: 85.22,
    deltaBRaw: 123.88,
    deltaMActual: 0,
    deltaBActual: 0,
    zohTriggered: false,
    totalInvestment: 1000,
    roundingStrategy: 'round_up_1',
  },
  alphaTable: [
    { alpha: 0.2, eHigh: 252, deltaM: 67.89, deltaB: 97.84, zohTriggered: false, totalInvestment: 870, overLimit: null, note: null },
    { alpha: 0.39, eHigh: 491.4, deltaM: 99.75, deltaB: 145.68, zohTriggered: false, totalInvestment: 1109.4, overLimit: 109.4, note: 'E_high 超出 M_max 可用空间' },
  ],
  warnings: [
    { type: 'zoh_not_triggered', message: 'ZOH 货币补仓 85.22 < 100 → 不触发，低波不补仓' },
  ],
}

describe('buildQuarterlyConfirmPayload', () => {
  const payload = buildQuarterlyConfirmPayload(calcFixture, { snapshotDate: '2026-09-30' })

  it('主表字段：vCurr/vMonetary/vBond/模式与取整', () => {
    expect(payload.vCurr).toBe(9525.87)
    expect(payload.vMonetary).toBe(988.27)
    expect(payload.vBond).toBe(1486.37)
    expect(payload.vHighVol).toBe(7051.23)
    expect(payload.deltaMTheory).toBe(85.22)
    expect(payload.deltaMActual).toBe(0)
    expect(payload.roundingStrategy).toBe('round_up_1')
    expect(payload.budgetLimitUsed).toBe(1000)
    expect(payload.totalInvestment).toBe(1000)
    expect(payload.correctionMode).toBe('lqr_zoh')
    expect(payload.triggeredBoundary).toBeNull()
    expect(payload.notes).toHaveLength(1)
    expect(payload.snapshotDate).toBe('2026-09-30')
  })

  it('迭代行：按 alphaTable 逐行映射', () => {
    expect(payload.iterations).toHaveLength(2)
    expect(payload.iterations[0].sortOrder).toBe(0)
    expect(payload.iterations[0].alpha).toBe(0.2)
    expect(payload.iterations[1].overLimit).toBe(109.4)
    expect(payload.iterations[1].zohTriggered).toBe(false)
  })

  it('资产行：pre_six + pre_high（合入 LQR Δ）+ post_high', () => {
    const phases = payload.assets.map((a) => a.phase)
    expect(phases).toEqual(['pre_six', 'pre_six', 'pre_high', 'pre_high', 'post_high'])
    const goods = payload.assets.find((a) => a.phase === 'pre_high' && a.category === '商品类')
    expect(goods.deltaRaw).toBe(207.12)
    expect(goods.deltaAmount).toBe(207)
    const overseas = payload.assets.find((a) => a.phase === 'pre_high' && a.category === '海外权益类')
    expect(overseas.deltaAmount).toBe(0)
    expect(overseas.note).toBe('锚定资产 Δ=0')
  })

  it('参数指标：IC-DRR / 阶跃点 / 锚定 / KKT 说明齐全', () => {
    const keys = payload.params.map((p) => p.key)
    expect(keys).toContain('icDrrPct')
    expect(keys).toContain('icDrrBefore')
    expect(keys).toContain('icDrrAfter')
    expect(keys).toContain('zohStepPointEHigh')
    expect(keys).toContain('anchor')
    expect(keys).toContain('kktNote')
    const icDrr = payload.params.find((p) => p.key === 'icDrrPct')
    expect(icDrr.numValue).toBe(67.4)
    const anchor = payload.params.find((p) => p.key === 'anchor')
    expect(anchor.textValue).toBe('海外权益类')
  })

  it('空入参返回 null', () => {
    expect(buildQuarterlyConfirmPayload(null, {})).toBeNull()
  })
})