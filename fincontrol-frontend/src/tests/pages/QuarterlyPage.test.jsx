// @vitest-environment jsdom
/**
 * 2a 季度操作台（/quarterly）交互测试：
 * 默认值 → 求解（α 表 / 阶跃点 / KKT / IC-DRR）→ 历史联合校正筛选。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../../api/client.js', () => {
  const client = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
  return { apiClient: client, default: client }
})

import { apiClient } from '../../api/client.js'
import { useCorrectionStore } from '../../stores/correctionStore.js'
import QuarterlyPage from '../../pages/QuarterlyPage.jsx'

const DEFAULTS = {
  snapshotDate: '2026-09-29',
  vCurr: 9525.87,
  vHighVol: 7051.23,
  categories: {},
  targetRatios: { 货币类: 10, 固收类: 15, 商品类: 25, A股权益类: 25, 海外权益类: 20, 港股大中华类: 5 },
  uHighDefault: 618,
  purchaseThreshold: 100,
  alphaInitDefault: 0.2,
  alphaDecayDefault: 0.8,
  uMonetaryDcaTheory: 82.4,
  uBondDcaTheory: 123.6,
  snapshotNote: '六大类合计，不含余额类',
}

const OPERATIONS = {
  items: [
    { id: 10, operationDate: '2026-09-30 18:00:00', operationType: 'monthly_correction', correctionMode: 'lqr_zoh', snapshotDate: '2026-09-30', deltaMTheory: 85.22, totalInvestment: 1000, icDrrPct: 67.4, anchor: '海外权益类', hasDetail: true },
    { id: 3, operationDate: '2026-06-30 18:00:00', operationType: 'monthly_correction', correctionMode: 'lqr_zoh', snapshotDate: '2026-06-30', deltaMTheory: 68.3, totalInvestment: 1069.81, icDrrPct: 23.4, anchor: '海外权益类', hasDetail: true },
    { id: 8, operationDate: '2026-08-31 18:00:00', operationType: 'monthly_correction', correctionMode: 'zoh_only', snapshotDate: '2026-08-31', deltaMActual: 190, totalInvestment: 1143.76, hasDetail: false },
  ],
}

const CALC = {
  vCurr: 9525.87,
  vHighVol: 7051.23,
  anchor: '海外权益类',
  targetRatios: {},
  preSix: [
    { category: '货币类', amount: 988.27, ratioActual: 10.38, ratioTarget: 10, deviation: 0.38 },
  ],
  highVolPre: [
    { category: '商品类', amount: 2207.71, ratioActual: 31.31, ratioTarget: 33.33, deviation: -2.02 },
  ],
  params: { surplus: 700, mMax: 1000, mMaxSource: 'manual', uHigh: 618, uMonetaryDca: 0, uBondDca: 0, alphaInit: 0.2, alphaDecay: 0.8, purchaseThreshold: 100, mode: 'mMaxCapped' },
  alphaTable: [
    { alpha: 0.2, eHigh: 252, deltaM: 67.89, deltaB: 97.84, zohTriggered: false, totalInvestment: 870, overLimit: null, note: null },
    { alpha: 0.45, eHigh: 567, deltaM: 109.89, deltaB: 160.88, zohTriggered: true, totalInvestment: 1454.77, overLimit: 454.77, note: 'ZOH 触发' },
  ],
  zohStep: { eHighAtStep: 492.8, deltaMAtStep: 100, totalBeforeStep: 1110.8, totalAfterStep: 1357.08, jumpAmount: 246.28, note: '' },
  chosen: { alphaFinal: 0.3032, eHigh: 382, deltaMRaw: 85.22, deltaBRaw: 123.88, deltaMActual: 0, deltaBActual: 0, zohTriggered: false, totalInvestment: 1000, roundingStrategy: 'round_up_1' },
  lqr: {
    kktNote: '内点解不成立（港股大中华类 补仓为负）→ 边界解：置 0 后重解；',
    deltas: [
      { category: '商品类', amount: 2207.71, deltaRaw: 207.12, deltaAmount: 207, note: null },
      { category: '海外权益类', amount: 2152.13, deltaRaw: 0, deltaAmount: 0, note: '锚定资产 Δ=0' },
    ],
    betaTriggered: [],
  },
  icDrr: { fBefore: 21.41, fAfter: 6.97, ratioPct: 67.4 },
  highVolPost: [
    { category: '商品类', amount: 2414.71, ratioActual: 32.49, ratioTarget: 33.33, deviation: -0.84 },
  ],
  plan: [
    { item: '当月高波定投', amount: 618, note: '转入余额宝，月内自动扣款' },
    { item: 'LQR 高波校正', amount: 382, note: '商品类 207 / A股权益类 175' },
    { item: '总投入', amount: 1000, note: 'M_max = 1000（在范围内）' },
  ],
  warnings: [{ type: 'zoh_not_triggered', message: 'ZOH 货币补仓 85.22 < 100 → 不触发，低波不补仓' }],
}

describe('QuarterlyPage · 2a 季度 LQR-ZOH 操作台', () => {
  beforeEach(() => {
    useCorrectionStore.getState().reset()
    vi.clearAllMocks()
    apiClient.get.mockImplementation((url) => {
      if (url === '/correction/quarterly/defaults') return Promise.resolve(DEFAULTS)
      if (url === '/correction/operations') return Promise.resolve(OPERATIONS)
      return Promise.resolve(null)
    })
  })

  it('只显示联合校正历史（lqr_zoh），不含纯 ZOH 记录', async () => {
    render(
      <MemoryRouter>
        <QuarterlyPage />
      </MemoryRouter>
    )
    expect(await screen.findByText('季度操作台 · LQR-ZOH 联合校正')).toBeTruthy()
    await waitFor(() => {
      expect(screen.getAllByText('回放')).toHaveLength(2)
    })
    // 6/30 与 9/30 两次 IC-DRR
    expect(screen.getByText('23.4%')).toBeTruthy()
    expect(screen.getByText('67.4%')).toBeTruthy()
  })

  it('求解联合校正方案并展示 α 表 / 阶跃点 / KKT / IC-DRR', async () => {
    apiClient.post.mockResolvedValue(CALC)
    render(
      <MemoryRouter>
        <QuarterlyPage />
      </MemoryRouter>
    )
    await screen.findByText('季度操作台 · LQR-ZOH 联合校正')

    fireEvent.click(screen.getByText('求解联合校正方案'))

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/correction/quarterly/calculate',
        expect.objectContaining({ mode: 'auto', uHigh: 618 })
      )
    })
    expect(await screen.findByText('α 迭代 / 探针过程')).toBeTruthy()
    expect(screen.getAllByText(/ZOH 阶跃点/).length).toBeGreaterThan(0)
    expect(screen.getByText('LQR 高波内部求解（KKT）')).toBeTruthy()
    expect(screen.getAllByText(/67\.4/).length).toBeGreaterThan(0)
    expect(screen.getByText('最终执行方案')).toBeTruthy()
  })

  it('切换人为上限模式后求解参数随之改变', async () => {
    apiClient.post.mockResolvedValue(CALC)
    render(
      <MemoryRouter>
        <QuarterlyPage />
      </MemoryRouter>
    )
    await screen.findByText('季度操作台 · LQR-ZOH 联合校正')

    fireEvent.click(screen.getByText(/人为上限倒推/))
    fireEvent.change(screen.getByPlaceholderText('用于默认上限 1.8 × S'), {
      target: { value: '700' },
    })
    fireEvent.click(screen.getByText('求解联合校正方案'))

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/correction/quarterly/calculate',
        expect.objectContaining({ mode: 'mMaxCapped', surplus: 700, mMax: 1260 })
      )
    })
  })
})