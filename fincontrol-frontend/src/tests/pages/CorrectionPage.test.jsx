// @vitest-environment jsdom
/**
 * 2a 月度校正操作台（/correction）交互测试：
 * 默认值加载 → 计算 → 结果显示 → 历史记录（含联合校正回放入口）。
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
import CorrectionPage from '../../pages/CorrectionPage.jsx'

const DEFAULTS = {
  vTotalSixCategories: 9525.87,
  vMonetary: 988.27,
  vBond: 1486.37,
  vHighVol: 7051.23,
  uHigh: 618,
  uMonetaryDcaTheory: 82.4,
  uBondDcaTheory: 123.6,
  targetRatios: { 货币类: 10, 固收类: 15, 商品类: 25, A股权益类: 25, 海外权益类: 20, 港股大中华类: 5 },
  budgetLimit: 1000,
  purchaseThreshold: 100,
  snapshotDate: '2026-09-29',
  snapshotNote: '六大类合计，不含余额类',
  categories: {},
}

const OPERATIONS = {
  items: [
    {
      id: 10,
      operationDate: '2026-09-30 18:00:00',
      operationType: 'monthly_correction',
      correctionMode: 'lqr_zoh',
      snapshotDate: '2026-09-30',
      deltaMTheory: 85.22,
      deltaMActual: null,
      deltaBActual: null,
      totalInvestment: 1000,
      icDrrPct: 67.4,
      anchor: '海外权益类',
      hasDetail: true,
      notes: [],
    },
    {
      id: 8,
      operationDate: '2026-08-31 18:00:00',
      operationType: 'monthly_correction',
      correctionMode: 'zoh_only',
      snapshotDate: '2026-08-31',
      deltaMActual: 190,
      deltaBActual: 290,
      totalInvestment: 1143.76,
      icDrrPct: null,
      hasDetail: false,
      notes: [],
    },
  ],
}

const CALC = {
  deltaMTheory: 85.22,
  deltaBTheory: 123.88,
  uMonetaryDca: 0,
  uBondDca: 0,
  uMonetaryDcaTheory: 82.4,
  uBondDcaTheory: 123.6,
  totalInvestment: 1000,
  zohTriggered: false,
  eHigh: 382,
  roundingSuggestion: {
    deltaMSuggested: 0,
    deltaBSuggested: 0,
    roundingStrategy: 'round_up_10',
    deviationMSuggested: 0.0,
    deviationBSuggested: 0.0,
  },
  warnings: [{ type: 'zoh_not_triggered', message: 'ZOH 货币补仓 85.22 < 100 → 不触发' }],
}

function mockGets() {
  apiClient.get.mockImplementation((url) => {
    if (url === '/correction/defaults') return Promise.resolve(DEFAULTS)
    if (url === '/correction/operations') return Promise.resolve(OPERATIONS)
    return Promise.resolve(null)
  })
}

describe('CorrectionPage · 2a 月度校正操作台', () => {
  beforeEach(() => {
    useCorrectionStore.getState().reset()
    vi.clearAllMocks()
    mockGets()
  })

  it('加载默认值并展示当前状态与历史记录', async () => {
    render(
      <MemoryRouter>
        <CorrectionPage />
      </MemoryRouter>
    )
    expect(await screen.findByText('月度校正操作台')).toBeTruthy()
    await waitFor(() => {
      expect(screen.getAllByText(/9,525\.87/).length).toBeGreaterThan(0)
    })
    // 历史记录：两条（含联合校正标签）
    expect(await screen.findByText('LQR-ZOH 联合校正')).toBeTruthy()
    expect(screen.getByText('纯 ZOH 低波校正')).toBeTruthy()
    // IC-DRR 摘要
    expect(screen.getByText('67.4%')).toBeTruthy()
  })

  it('点击「计算校正方案」调用求解接口并展示结果', async () => {
    apiClient.post.mockResolvedValue(CALC)
    render(
      <MemoryRouter>
        <CorrectionPage />
      </MemoryRouter>
    )
    await screen.findByText('月度校正操作台')

    fireEvent.click(screen.getByText('计算校正方案'))

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/correction/monthly/calculate',
        expect.objectContaining({ vCurr: 9525.87, vMonetary: 988.27, uHigh: 618 })
      )
    })
    expect(await screen.findByText('求解结果')).toBeTruthy()
    expect(screen.getAllByText(/85\.22/).length).toBeGreaterThan(0)
    expect(screen.getByText('ZOH 不触发')).toBeTruthy()
  })

  it('打开取整弹窗并触发实时重算', async () => {
    apiClient.post.mockImplementation((url) => {
      if (url === '/correction/monthly/calculate') return Promise.resolve(CALC)
      if (url === '/correction/monthly/recalculate') {
        return Promise.resolve({
          newRatios: { 货币类: 10.12, 固收类: 15.07 },
          deviations: { 货币类: 0.12, 固收类: 0.07 },
          totalInvestmentActual: 1153.0,
          overBudgetLimit: false,
        })
      }
      return Promise.resolve(null)
    })
    render(
      <MemoryRouter>
        <CorrectionPage />
      </MemoryRouter>
    )
    await screen.findByText('月度校正操作台')
    fireEvent.click(screen.getByText('计算校正方案'))
    await screen.findByText('求解结果')

    fireEvent.click(screen.getByText('打开取整确认（CORR-004/005）'))
    expect(await screen.findByText('取整确认')).toBeTruthy()
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/correction/monthly/recalculate',
        expect.any(Object)
      )
    })
  })
})