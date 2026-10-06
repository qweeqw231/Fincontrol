// @vitest-environment jsdom
/**
 * 2b 绩效统计页（/nav）测试：
 * 页头（改名 + 自然日文案 + 同步区）、统计四区块（核心绩效/风险调整/分布/累加区间）、手动同步。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../../api/client.js', () => {
  const client = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
  return { apiClient: client, default: client }
})

import { apiClient } from '../../api/client.js'
import NAVPage, { xTickFormatter } from '../../pages/NAVPage.jsx'

const HISTORY = {
  points: [
    { date: '2025-10-13', nav: 1.0001, navPct: 0.01, cumulativeProfit: 0.01, actualProfit: 0.01, weekday: '周一' },
    { date: '2026-10-05', nav: 1.0951, navPct: 9.51, cumulativeProfit: -18.01, actualProfit: 1.5, weekday: '周一' },
  ],
  milestones: [],
}

const STATS = {
  from: '2025-10-13', to: '2026-10-05', totalDays: 358, nonZeroDays: 246, zeroDays: 112, weekendDays: 98,
  metrics: {
    dailyReturnPct: { count: 246, mean: 0.000422, median: 0.0002, std: 0.008374, min: -0.0524, max: 0.028, p1: -0.026, p5: -0.011, p10: -0.008, p25: -0.003, p50: 0.0002, p75: 0.0046, p90: 0.0101, p95: 0.0135, p99: 0.0174, skewness: -1.23, kurtosis: 7.64 },
    nav: { count: 358, mean: 1.0883, median: 1.1017, std: 0.0392, min: 1.0001, max: 1.1694, p1: 1.0048, p5: 1.0097, p10: 1.0226, p25: 1.0664, p50: 1.1017, p75: 1.1178, p90: 1.1339, p95: 1.1408, p99: 1.1489, skewness: -0.66, kurtosis: -0.55 },
  },
  drawdown: {
    maxDrawdownPct: -10.32, peakDate: '2026-01-29', peakNav: 1.1694, troughDate: '2026-03-23', troughNav: 1.0488,
    durationDays: 53, daysNavAbove100: 358, daysNavAbove105: 271, daysNavAbove110: 176, daysNavAbove115: 3, daysNavBelow100: 0,
  },
  risk: {
    cagrPct: 10.04, annualVolPct: 13.29, riskFreePct: 1.16, sharpe: 0.67, calmar: 0.97, sortino: 0.91,
    annualDownsidePct: 9.8, winRatePct: 51.06, avgWinPct: 0.59, avgLossPct: -0.53, profitLossRatio: 1.11,
    note: '波动/胜率口径剔除 112 个 0% 收益日（周末+节假日）；CAGR 按自然日跨度 357 天计算',
  },
  returnHistogram: [
    { bin: '-0.1% ~ 0.0%', count: 53, pct: 14.8 },
    { bin: '0.0% ~ 0.1%', count: 60, pct: 16.76 },
  ],
  pnlHistogram: [
    { bin: '{0}', count: 49, pct: 13.69, total: 0, avg: 0 },
    { bin: '[0, 10)', count: 41, pct: 11.45, total: 184.59, avg: 4.5 },
  ],
  cumulative: {
    intervals: [
      { index: 1, start: '2025-10-22', end: '2025-11-09', days: 19, valley: -7.35, valleyDate: '2025-10-28', pctOfTotal: 5.31 },
    ],
    startValue: 0.01, currentValue: -18.01, peakValue: 330.15, peakDate: '2026-05-13',
    valleyValue: -231.58, valleyDate: '2026-07-30', peakToValleyDrop: 561.73, valleyToNowRebound: 213.57,
    belowZeroDays: 81, belowZeroPct: 22.63,
  },
}

const SYNC_STATUS = {
  enabled: true, sourceFile: 'portfolio_daily_complete_v3 (1).xlsx', fileExists: true,
  lastSyncAt: '2026-10-07 02:00:00', lastRows: 358, lastError: null,
}

function mockGets() {
  apiClient.get.mockImplementation((url) => {
    if (url === '/nav/history') return Promise.resolve(HISTORY)
    if (url === '/nav/operations') return Promise.resolve({ items: [] })
    if (url === '/nav/statistics') return Promise.resolve(STATS)
    if (url === '/nav/sync-status') return Promise.resolve(SYNC_STATUS)
    return Promise.resolve(null)
  })
}

describe('NAVPage · 2b 绩效统计', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGets()
  })

  it('X 轴刻度显示完整年份（2025-10 / 2026-01）', () => {
    expect(xTickFormatter('2025-10-13')).toBe('2025-10')
    expect(xTickFormatter('2026-01-05')).toBe('2026-01')
    expect(xTickFormatter(null)).toBe('')
  })

  it('页头：改名「绩效统计」+ 自然日文案 + 同步状态与按钮', async () => {
    render(
      <MemoryRouter>
        <NAVPage />
      </MemoryRouter>
    )
    expect(await screen.findByText('绩效统计')).toBeTruthy()
    expect(screen.getByText(/共 2 个自然日/)).toBeTruthy()
    expect(screen.getByText(/上次同步 2026-10-07 02:00:00/)).toBeTruthy()
    expect(screen.getByText('立即同步')).toBeTruthy()
  })

  it('统计四区块渲染：核心绩效 / 风险调整 / 收益分布 / 累加<0 区间', async () => {
    render(
      <MemoryRouter>
        <NAVPage />
      </MemoryRouter>
    )
    await screen.findByText('绩效统计')
    expect(await screen.findByText('核心绩效')).toBeTruthy()
    expect(screen.getByText('风险调整指标')).toBeTruthy()
    expect(screen.getByText('收益分布')).toBeTruthy()
    expect(screen.getByText(/累加<0 区间分析/)).toBeTruthy()
    // 关键指标值
    expect(screen.getByText('-10.32%')).toBeTruthy() // 最大回撤
    expect(screen.getByText('51.06%')).toBeTruthy()  // 胜率
    expect(screen.getByText('0.67')).toBeTruthy()    // 夏普
    expect(screen.getByText(/剔除 112 个 0% 收益日/)).toBeTruthy()
  })

  it('点击「立即同步」调用 POST /nav/sync 并刷新数据', async () => {
    apiClient.post.mockResolvedValue({ synced: true, rows: 358, message: '已同步 358 行（2025-10-13 ~ 2026-10-05）' })
    render(
      <MemoryRouter>
        <NAVPage />
      </MemoryRouter>
    )
    await screen.findByText('绩效统计')

    fireEvent.click(screen.getByText('立即同步'))

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/nav/sync')
    })
    expect(await screen.findByText(/已同步 358 行/)).toBeTruthy()
  })
})