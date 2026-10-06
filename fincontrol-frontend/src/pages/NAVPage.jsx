import { useState, useEffect, useMemo } from 'react'
import {
  ResponsiveContainer,
  ComposedChart,
  BarChart,
  Bar,
  Area,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceDot,
} from 'recharts'
import apiClient from '../api/client.js'
import {
  NAV_HISTORY,
  NAV_OPERATIONS,
  NAV_STATISTICS,
  NAV_SYNC,
  NAV_SYNC_STATUS,
} from '../api/endpoints.js'
import './nav-page.css'

/**
 * 2b：绩效统计页（原「净值曲线」扩容改名）
 *
 * 数据源：nav_history（Excel 自动同步，自然日连续记录）
 *        + nav_milestone（关键里程碑）+ operation_log（校正与操作）
 *        + nav_statistics（后端实时计算：核心绩效/风险调整/分布与区间）
 *
 * 设计要点（修复 fundgraph 的标注/图线重叠问题）：
 *  1. 图内零文字标注 —— 仅用编号圆点锚定里程碑，全部说明文字移到图下方卡片，
 *     从根本上消除文字与图线/坐标轴的视觉重叠。
 *  2. 图表边距收紧但留足刻度空间（left 56 / right 32），Y 轴宽度显式限定。
 *  3. 双图分离：净值走势（上）+ 累加盈亏（下），避免双轴刻度互相干扰。
 *  4. 里程碑竖向参考线为极浅虚线，hover 由 tooltip 承担信息密度。
 *  5. 横坐标显示完整年份（2025-10），区分跨年的两年数据。
 */

const CATEGORY_BADGE = {
  起点: 'start',
  净值峰值: 'peak',
  最深回撤日: 'drawdown',
  累加峰值: 'peak',
  '7/23 谷底': 'bottom',
}

const OP_TYPE_LABEL = {
  monthly_correction: 'ZOH 校正',
  quarterly_correction: '季度校正',
  manual_adjustment: '战术调仓',
}

/** 2b 指标总览行（scale=100 的序列在展示时换算为百分比） */
const METRIC_ROWS = [
  { key: 'dailyReturnPct', label: '日收益率 %（剔除 0）', scale: 100, dp: 4 },
  { key: 'actualProfit', label: '当日盈亏 元', scale: 1, dp: 2 },
  { key: 'cumulativeProfit', label: '累加 元', scale: 1, dp: 2 },
  { key: 'nav', label: '净值', scale: 1, dp: 4 },
  { key: 'totalAsset', label: '总资产 元', scale: 1, dp: 2 },
]

function fmtNav(v) {
  return v == null ? '—' : Number(v).toFixed(4)
}
function fmtPct(v) {
  return v == null ? '—' : `${Number(v) >= 0 ? '+' : ''}${Number(v).toFixed(2)}%`
}
function fmtAmount(v) {
  return v == null ? '—' : `${Number(v) >= 0 ? '+' : ''}${Number(v).toFixed(2)}`
}

/** 自定义 tooltip：一行一只指标，避免文字堆叠 */
function NavTooltip({ active, payload }) {
  if (!active || !payload || payload.length === 0) return null
  const p = payload[0].payload
  if (!p) return null
  return (
    <div className="nav-tooltip">
      <div className="nav-tooltip-date">
        {p.date}
        {p.weekday ? ` · ${p.weekday}` : ''}
      </div>
      <div className="nav-tooltip-grid">
        <span>净值</span>
        <strong>{fmtNav(p.nav)}</strong>
        <span>净值涨幅</span>
        <strong className={p.navPct >= 0 ? 'pos' : 'neg'}>{fmtPct(p.navPct)}</strong>
        <span>累加盈亏</span>
        <strong className={p.cumulativeProfit >= 0 ? 'pos' : 'neg'}>
          ¥{fmtAmount(p.cumulativeProfit)}
        </strong>
        <span>当日盈亏</span>
        <strong className={p.actualProfit >= 0 ? 'pos' : 'neg'}>
          ¥{fmtAmount(p.actualProfit)}
        </strong>
      </div>
      {p.milestoneLabel && (
        <div className="nav-tooltip-flag">
          {p.milestoneIndex} · {p.milestoneLabel}
        </div>
      )}
    </div>
  )
}

function CumTooltip({ active, payload }) {
  if (!active || !payload || payload.length === 0) return null
  const p = payload[0].payload
  if (!p) return null
  return (
    <div className="nav-tooltip">
      <div className="nav-tooltip-date">{p.date}</div>
      <div className="nav-tooltip-grid">
        <span>累加盈亏</span>
        <strong className={p.cumulativeProfit >= 0 ? 'pos' : 'neg'}>
          ¥{fmtAmount(p.cumulativeProfit)}
        </strong>
        <span>当日盈亏</span>
        <strong className={p.actualProfit >= 0 ? 'pos' : 'neg'}>
          ¥{fmtAmount(p.actualProfit)}
        </strong>
      </div>
    </div>
  )
}

/**
 * 里程碑圆点：编号 + 白描边，无文字，避免与图线重叠。
 *
 * 内置碰撞检测：Recharts 按数据顺序同步调用本渲染器，
 * 与已放置圆点横向距离不足一个直径（19px）时自动省略该圆点，
 * 保证任意视口宽度下圆点永不重叠（对应事件仍在下方时间线卡片中完整列出）。
 */
function createMilestoneDot() {
  const placedX = []
  const MIN_GAP = 19
  return function MilestoneDot({ cx, cy, payload }) {
    if (cx == null || cy == null || !payload?.milestoneIndex) return null
    for (const px of placedX) {
      if (Math.abs(px - cx) < MIN_GAP) return null
    }
    placedX.push(cx)
    const idx = payload.milestoneIndex
    return (
      <g>
        <circle cx={cx} cy={cy} r={9} fill="#1e40af" stroke="#fff" strokeWidth={2} />
        <text
          x={cx}
          y={cy + 3.5}
          textAnchor="middle"
          fontSize={10}
          fontWeight={700}
          fill="#fff"
        >
          {idx}
        </text>
      </g>
    )
  }
}

/** X 轴刻度：完整年份标签（2025-10 / 2026-01），用于区分跨年的两年数据 */
export function xTickFormatter(v) {
  if (!v) return ''
  return v.slice(0, 7)
}

/** 2b 统计数值格式化 */
function fmtNum(v, dp = 2) {
  return v == null ? '—' : Number(v).toFixed(dp)
}
function fmtPctPoint(v, dp = 2, signed = true) {
  if (v == null) return '—'
  const n = Number(v)
  return `${signed && n >= 0 ? '+' : ''}${n.toFixed(dp)}%`
}

export default function NAVPage() {
  const [data, setData] = useState(null)
  const [operations, setOperations] = useState([])
  const [statistics, setStatistics] = useState(null)
  const [syncStatus, setSyncStatus] = useState(null)
  const [syncing, setSyncing] = useState(false)
  const [syncMessage, setSyncMessage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [logScale, setLogScale] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    Promise.all([
      apiClient.get(NAV_HISTORY),
      // 操作记录 / 统计 / 同步状态为增强信息，失败不阻断主视图
      apiClient.get(NAV_OPERATIONS).catch(() => ({ items: [] })),
      apiClient.get(NAV_STATISTICS).catch(() => null),
      apiClient.get(NAV_SYNC_STATUS).catch(() => null),
    ])
      .then(([navRes, opRes, statRes, syncRes]) => {
        if (!alive) return
        setData(navRes)
        setOperations(Array.isArray(opRes?.items) ? opRes.items : [])
        setStatistics(statRes)
        setSyncStatus(syncRes)
        setError(null)
      })
      .catch((err) => {
        if (!alive) return
        setError(err?.message || '加载失败')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [])

  /** 2b：手动「立即同步」——触发后端从 Excel 导入后刷新三份数据 */
  const handleSync = async () => {
    setSyncing(true)
    setSyncMessage(null)
    try {
      const result = await apiClient.post(NAV_SYNC)
      const [navRes, statRes, syncRes] = await Promise.all([
        apiClient.get(NAV_HISTORY),
        apiClient.get(NAV_STATISTICS).catch(() => null),
        apiClient.get(NAV_SYNC_STATUS).catch(() => null),
      ])
      setData(navRes)
      setStatistics(statRes)
      setSyncStatus(syncRes)
      setSyncMessage(result?.message || '同步完成')
    } catch (err) {
      setSyncMessage(err?.message || '同步失败')
    } finally {
      setSyncing(false)
    }
  }

  // 里程碑按日期索引，注入到每日数据点
  const { chartData, milestones } = useMemo(() => {
    const points = Array.isArray(data?.points) ? data.points : []
    const ms = Array.isArray(data?.milestones) ? data.milestones : []
    const msByDate = new Map()
    ms.forEach((m, i) => msByDate.set(m.date, { ...m, index: i + 1 }))
    const merged = points.map((p) => {
      const hit = msByDate.get(p.date)
      return {
        ...p,
        milestoneIndex: hit ? hit.index : null,
        milestoneLabel: hit ? hit.category : null,
      }
    })
    return {
      chartData: merged,
      milestones: ms.map((m, i) => ({ ...m, index: i + 1 })),
    }
  }, [data])

  // 概览指标
  const stats = useMemo(() => {
    if (chartData.length === 0) return null
    const first = chartData[0]
    const last = chartData[chartData.length - 1]
    let peak = first
    // 最大回撤：追踪回撤前的高点与回撤中的低点（而非全期最低点）
    let peakSoFar = first
    let maxDrawdown = 0
    let ddPeak = first
    let ddTrough = first
    for (const p of chartData) {
      if (p.nav == null) continue
      if (p.nav > peak.nav) peak = p
      if (p.nav > peakSoFar.nav) peakSoFar = p
      const dd = (p.nav - peakSoFar.nav) / peakSoFar.nav
      if (dd < maxDrawdown) {
        maxDrawdown = dd
        ddPeak = peakSoFar
        ddTrough = p
      }
    }
    let cumPeak = first
    for (const p of chartData) {
      if (p.cumulativeProfit != null && p.cumulativeProfit > (cumPeak.cumulativeProfit ?? -Infinity)) {
        cumPeak = p
      }
    }
    return { first, last, peak, maxDrawdown, ddPeak, ddTrough, cumPeak }
  }, [chartData])

  if (loading) {
    return <div className="nav-state">⏳ 加载净值数据...</div>
  }
  if (error) {
    return <div className="nav-state nav-state--error">⚠️ {error}</div>
  }
  if (!stats) {
    return (
      <div className="nav-state">
        暂无净值数据。请运行 <code>scripts/import-data/import-data.cjs</code> 导入外部数据源。
      </div>
    )
  }

  const { first, last, peak, maxDrawdown, ddPeak, ddTrough, cumPeak } = stats
  const cumUp = (last.cumulativeProfit ?? 0) >= 0
  // 两张图各自独立的圆点渲染器（共享实例会因 x 坐标相同而互相抑制）
  const navDot = createMilestoneDot()
  const cumDot = createMilestoneDot()

  return (
    <div className="page-shell nav-page">
      <header className="nav-header">
        <div>
          <h1>绩效统计</h1>
          <p className="nav-sub">
            北极星策略实盘净值走势 · {first.date} ~ {last.date} · 共 {chartData.length} 个自然日（含周末，按日连续记录）
          </p>
        </div>
        <div className="nav-header-side">
          <div className="nav-source-tag">数据源：portfolio_daily_complete_v3（自动同步）</div>
          <div className="nav-sync-row">
            <span className="nav-sync-chip">
              {syncStatus?.lastSyncAt
                ? `上次同步 ${syncStatus.lastSyncAt}${syncStatus.lastRows ? ` · ${syncStatus.lastRows} 行` : ''}`
                : syncStatus?.enabled
                  ? '自动同步已启用（等待首次同步）'
                  : '自动同步未配置'}
            </span>
            <button
              type="button"
              className="nav-sync-btn"
              onClick={handleSync}
              disabled={syncing}
            >
              {syncing ? '同步中...' : '立即同步'}
            </button>
          </div>
          {syncMessage && <div className="nav-sync-msg">{syncMessage}</div>}
        </div>
      </header>

      {/* 概览卡片 */}
      <div className="nav-stat-row">
        <div className="nav-stat">
          <div className="nav-stat-label">最新净值</div>
          <div className="nav-stat-value">{fmtNav(last.nav)}</div>
          <div className={`nav-stat-sub ${last.navPct >= 0 ? 'pos' : 'neg'}`}>
            {fmtPct(last.navPct)} · {last.date}
          </div>
        </div>
        <div className="nav-stat">
          <div className="nav-stat-label">历史峰值净值</div>
          <div className="nav-stat-value">{fmtNav(peak.nav)}</div>
          <div className="nav-stat-sub">{peak.date}</div>
        </div>
        <div className="nav-stat">
          <div className="nav-stat-label">最大回撤</div>
          <div className="nav-stat-value neg">{(maxDrawdown * 100).toFixed(2)}%</div>
          <div className="nav-stat-sub">
            {ddPeak.date} {fmtNav(ddPeak.nav)} → {ddTrough.date} {fmtNav(ddTrough.nav)}
          </div>
        </div>
        <div className="nav-stat">
          <div className="nav-stat-label">当前累加盈亏</div>
          <div className={`nav-stat-value ${cumUp ? 'pos' : 'neg'}`}>
            ¥{fmtAmount(last.cumulativeProfit)}
          </div>
          <div className="nav-stat-sub">峰值 ¥{fmtAmount(cumPeak.cumulativeProfit)}（{cumPeak.date}）</div>
        </div>
      </div>

      {/* 图 1：净值走势 */}
      <div className="nav-panel">
        <div className="nav-panel-head">
          <div className="nav-panel-title">
            净值走势
            <span className="nav-panel-tag">编号 ①-⑨ 对应下方关键事件</span>
          </div>
          <div className="nav-toggle-group">
            <button
              type="button"
              className={!logScale ? 'active' : ''}
              onClick={() => setLogScale(false)}
            >
              线性
            </button>
            <button
              type="button"
              className={logScale ? 'active' : ''}
              onClick={() => setLogScale(true)}
            >
              对数
            </button>
          </div>
        </div>
        <ResponsiveContainer width="100%" height={320}>
          <ComposedChart data={chartData} margin={{ top: 24, right: 32, bottom: 8, left: 8 }}>
            <defs>
              <linearGradient id="navFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#1e40af" stopOpacity={0.22} />
                <stop offset="100%" stopColor="#1e40af" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" vertical={false} />
            <XAxis
              dataKey="date"
              tickFormatter={xTickFormatter}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              minTickGap={48}
              tickMargin={8}
              axisLine={{ stroke: '#e5e7eb' }}
              tickLine={false}
            />
            <YAxis
              scale={logScale ? 'log' : 'linear'}
              domain={logScale ? ['auto', 'auto'] : ['dataMin - 0.01', 'dataMax + 0.01']}
              tickFormatter={(v) => Number(v).toFixed(2)}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              width={52}
              axisLine={false}
              tickLine={false}
              allowDataOverflow={false}
            />
            <Tooltip content={<NavTooltip />} />
            {/* 基准线：起点净值 1.0 */}
            <ReferenceLine y={1} stroke="#9ca3af" strokeDasharray="4 4" />
            {/* 里程碑竖向参考线：极浅虚线，无文字标注（防重叠） */}
            {milestones.map((m) => (
              <ReferenceLine
                key={m.date}
                x={m.date}
                stroke="#cbd5e1"
                strokeDasharray="3 3"
                strokeWidth={1}
              />
            ))}
            <Area
              type="monotone"
              dataKey="nav"
              stroke="#1e40af"
              strokeWidth={1.8}
              fill="url(#navFill)"
              dot={navDot}
              activeDot={{ r: 4, fill: '#1e40af', stroke: '#fff', strokeWidth: 2 }}
              isAnimationActive={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 图 2：累加盈亏 */}
      <div className="nav-panel">
        <div className="nav-panel-head">
          <div className="nav-panel-title">
            累加盈亏（实际收益）
            <span className="nav-panel-tag">零轴以上为累计盈利</span>
          </div>
        </div>
        <ResponsiveContainer width="100%" height={200}>
          <ComposedChart data={chartData} margin={{ top: 16, right: 32, bottom: 8, left: 8 }}>
            <defs>
              <linearGradient id="cumFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#10b981" stopOpacity={0.24} />
                <stop offset="100%" stopColor="#10b981" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" vertical={false} />
            <XAxis
              dataKey="date"
              tickFormatter={xTickFormatter}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              minTickGap={48}
              tickMargin={8}
              axisLine={{ stroke: '#e5e7eb' }}
              tickLine={false}
            />
            <YAxis
              tickFormatter={(v) => Number(v).toFixed(0)}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              width={52}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip content={<CumTooltip />} />
            <ReferenceLine y={0} stroke="#9ca3af" strokeDasharray="4 4" />
            <Area
              type="monotone"
              dataKey="cumulativeProfit"
              stroke="#10b981"
              strokeWidth={1.8}
              fill="url(#cumFill)"
              activeDot={{ r: 4, fill: '#10b981', stroke: '#fff', strokeWidth: 2 }}
              isAnimationActive={false}
            />
            {/* 里程碑编号点同步标注 */}
            <Line
              type="monotone"
              dataKey="cumulativeProfit"
              stroke="none"
              dot={cumDot}
              activeDot={false}
              isAnimationActive={false}
              legendType="none"
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 关键事件卡片：承载全部文字说明，与图完全分离 */}
      <div className="nav-panel">
        <div className="nav-panel-head">
          <div className="nav-panel-title">
            关键事件时间线
            <span className="nav-panel-tag">{milestones.length} 个里程碑</span>
          </div>
        </div>
        <ol className="milestone-list">
          {milestones.map((m) => (
            <li key={m.date} className={`milestone-item ${CATEGORY_BADGE[m.category] || ''}`}>
              <span className="milestone-badge">{m.index}</span>
              <div className="milestone-body">
                <div className="milestone-row1">
                  <span className="milestone-date">{m.date}</span>
                  <span className="milestone-cat">{m.category}</span>
                  <span className="milestone-metrics">
                    净值 <strong>{fmtNav(m.nav)}</strong>
                    <em className={m.navPct >= 0 ? 'pos' : 'neg'}>{fmtPct(m.navPct)}</em>
                  </span>
                </div>
                <div className="milestone-desc">{m.description}</div>
              </div>
            </li>
          ))}
        </ol>
        <div className="nav-note">
          ⓘ 净值（策略表现）与累加（真实现金盈亏）口径不同：定投入金会推高累加分母，
          导致「累加为负但净值大于 1」的反直觉现象 —— 这正是 2026-07-23 谷底的成因。
        </div>
      </div>

      {/* 2b 绩效统计：核心绩效 / 风险调整 / 分布与区间（与个人统计报告 Excel 维度对齐） */}
      {statistics && (
        <>
          <div className="nav-panel">
            <div className="nav-panel-head">
              <div className="nav-panel-title">
                核心绩效
                <span className="nav-panel-tag">
                  {statistics.from} ~ {statistics.to} · {statistics.totalDays} 自然日（有波动 {statistics.nonZeroDays} 天）
                </span>
              </div>
            </div>
            <div className="nav-stat-row">
              <div className="nav-stat">
                <div className="nav-stat-label">最大回撤</div>
                <div className="nav-stat-value neg">
                  {fmtPctPoint(statistics.drawdown?.maxDrawdownPct, 2, false)}
                </div>
                <div className="nav-stat-sub">
                  {statistics.drawdown?.peakDate} → {statistics.drawdown?.troughDate}
                  （{statistics.drawdown?.durationDays} 天）
                </div>
              </div>
              <div className="nav-stat">
                <div className="nav-stat-label">净值分档天数</div>
                <div className="nav-stat-value">≥1.15 · {statistics.drawdown?.daysNavAbove115} 天</div>
                <div className="nav-stat-sub">
                  ≥1.10 {statistics.drawdown?.daysNavAbove110} 天 · ≥1.05 {statistics.drawdown?.daysNavAbove105} 天
                  · ≥1.00 {statistics.drawdown?.daysNavAbove100} 天
                </div>
              </div>
              <div className="nav-stat">
                <div className="nav-stat-label">累加盈亏（最新）</div>
                <div className={`nav-stat-value ${(statistics.cumulative?.currentValue ?? 0) >= 0 ? 'pos' : 'neg'}`}>
                  ¥{fmtNum(statistics.cumulative?.currentValue)}
                </div>
                <div className="nav-stat-sub">
                  峰值 ¥{fmtNum(statistics.cumulative?.peakValue)}（{statistics.cumulative?.peakDate}）
                  · 谷值 ¥{fmtNum(statistics.cumulative?.valleyValue)}（{statistics.cumulative?.valleyDate}）
                </div>
              </div>
              <div className="nav-stat">
                <div className="nav-stat-label">零收益天数</div>
                <div className="nav-stat-value">{statistics.zeroDays} 天</div>
                <div className="nav-stat-sub">
                  其中周末 {statistics.weekendDays} 天（不计入波动率/胜率）
                </div>
              </div>
            </div>

            <div className="nav-table-wrap">
              <table className="nav-stat-table">
                <thead>
                  <tr>
                    <th>序列</th><th>样本</th><th>均值</th><th>中位</th><th>标准差</th>
                    <th>最小</th><th>最大</th><th>P5</th><th>P25</th><th>P75</th><th>P95</th>
                    <th>偏度</th><th>峰度*</th>
                  </tr>
                </thead>
                <tbody>
                  {METRIC_ROWS.map((row) => {
                    const m = statistics.metrics?.[row.key]
                    if (!m) return null
                    const s = row.scale || 1
                    return (
                      <tr key={row.key}>
                        <td className="cat-cell">{row.label}</td>
                        <td>{m.count}</td>
                        <td>{fmtNum(m.mean * s, row.dp)}</td>
                        <td>{fmtNum(m.median * s, row.dp)}</td>
                        <td>{fmtNum(m.std * s, row.dp)}</td>
                        <td>{fmtNum(m.min * s, row.dp)}</td>
                        <td>{fmtNum(m.max * s, row.dp)}</td>
                        <td>{fmtNum(m.p5 * s, row.dp)}</td>
                        <td>{fmtNum(m.p25 * s, row.dp)}</td>
                        <td>{fmtNum(m.p75 * s, row.dp)}</td>
                        <td>{fmtNum(m.p95 * s, row.dp)}</td>
                        <td>{fmtNum(m.skewness, 3)}</td>
                        <td>{fmtNum(m.kurtosis, 3)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <div className="nav-note">
              ⓘ *「峰度」为超额峰度（Excel KURT 口径，正态 = 0）；日收益率序列剔除 0% 收益日（周末 + 节假日）。
            </div>
          </div>

          <div className="nav-panel">
            <div className="nav-panel-head">
              <div className="nav-panel-title">风险调整指标</div>
            </div>
            <div className="nav-kpi-grid">
              <div className="nav-kpi">
                <span>年化收益 CAGR</span>
                <strong>{fmtPctPoint(statistics.risk?.cagrPct)}</strong>
              </div>
              <div className="nav-kpi">
                <span>年化波动</span>
                <strong>{fmtNum(statistics.risk?.annualVolPct)}%</strong>
              </div>
              <div className="nav-kpi">
                <span>夏普（rf {fmtNum(statistics.risk?.riskFreePct)}%）</span>
                <strong>{fmtNum(statistics.risk?.sharpe, 2)}</strong>
              </div>
              <div className="nav-kpi">
                <span>卡玛</span>
                <strong>{fmtNum(statistics.risk?.calmar, 2)}</strong>
              </div>
              <div className="nav-kpi">
                <span>索提诺</span>
                <strong>{fmtNum(statistics.risk?.sortino, 2)}</strong>
              </div>
              <div className="nav-kpi">
                <span>胜率</span>
                <strong>{fmtNum(statistics.risk?.winRatePct)}%</strong>
              </div>
              <div className="nav-kpi">
                <span>平均盈利 / 平均亏损</span>
                <strong>
                  <em className="pos">{fmtPctPoint(statistics.risk?.avgWinPct)}</em> / <em className="neg">{fmtPctPoint(statistics.risk?.avgLossPct)}</em>
                </strong>
              </div>
              <div className="nav-kpi">
                <span>盈亏比</span>
                <strong>{fmtNum(statistics.risk?.profitLossRatio, 2)}</strong>
              </div>
            </div>
            {statistics.risk?.note && <div className="nav-note">ⓘ {statistics.risk.note}</div>}
          </div>

          <div className="nav-panel">
            <div className="nav-panel-head">
              <div className="nav-panel-title">
                收益分布
                <span className="nav-panel-tag">仅显示非空区间</span>
              </div>
            </div>
            <div className="nav-hist-grid">
              <div>
                <div className="nav-hist-title">日收益率区间分布（0.1% 步长）</div>
                <ResponsiveContainer width="100%" height={230}>
                  <BarChart
                    data={(statistics.returnHistogram || []).filter((b) => b.count > 0)}
                    margin={{ top: 8, right: 12, bottom: 4, left: 4 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" vertical={false} />
                    <XAxis
                      dataKey="bin"
                      tick={{ fontSize: 10, fill: '#6b7280' }}
                      minTickGap={36}
                      tickFormatter={(v) => String(v).split(' ~ ')[0]}
                      axisLine={{ stroke: '#e5e7eb' }}
                      tickLine={false}
                    />
                    <YAxis tick={{ fontSize: 10, fill: '#6b7280' }} width={36} allowDecimals={false} />
                    <Tooltip
                      formatter={(value, name, item) => [`${value} 天（${item?.payload?.pct}%）`, '天数']}
                      labelFormatter={(label) => `区间 ${label}`}
                    />
                    <Bar dataKey="count" fill="#3b82f6" isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div>
                <div className="nav-hist-title">日盈亏金额区间分布（10 元步长）</div>
                <ResponsiveContainer width="100%" height={230}>
                  <BarChart
                    data={(statistics.pnlHistogram || []).filter((b) => b.count > 0)}
                    margin={{ top: 8, right: 12, bottom: 4, left: 4 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" vertical={false} />
                    <XAxis
                      dataKey="bin"
                      tick={{ fontSize: 10, fill: '#6b7280' }}
                      minTickGap={36}
                      axisLine={{ stroke: '#e5e7eb' }}
                      tickLine={false}
                    />
                    <YAxis tick={{ fontSize: 10, fill: '#6b7280' }} width={36} allowDecimals={false} />
                    <Tooltip
                      formatter={(value, name, item) => [
                        `${value} 天（${item?.payload?.pct}%，合计 ¥${item?.payload?.total}）`,
                        '天数',
                      ]}
                      labelFormatter={(label) => `区间 ${label}`}
                    />
                    <Bar dataKey="count" fill="#10b981" isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="nav-panel">
            <div className="nav-panel-head">
              <div className="nav-panel-title">
                累加&lt;0 区间分析
                <span className="nav-panel-tag">
                  {statistics.cumulative?.intervals?.length || 0} 个区间 · 共 {statistics.cumulative?.belowZeroDays} 天
                  （{fmtNum(statistics.cumulative?.belowZeroPct, 1)}%）
                </span>
              </div>
            </div>
            <div className="nav-table-wrap">
              <table className="nav-stat-table">
                <thead>
                  <tr><th>#</th><th>起始</th><th>结束</th><th>天数</th><th>谷值(元)</th><th>谷值日期</th><th>占比</th></tr>
                </thead>
                <tbody>
                  {(statistics.cumulative?.intervals || []).map((it) => (
                    <tr key={it.index}>
                      <td>{it.index}</td>
                      <td>{it.start}</td>
                      <td>{it.end}</td>
                      <td>{it.days}</td>
                      <td className="neg">{fmtNum(it.valley)}</td>
                      <td>{it.valleyDate}</td>
                      <td>{fmtNum(it.pctOfTotal, 2)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="nav-note">
              ⓘ 峰→谷回撤 ¥{fmtNum(statistics.cumulative?.peakToValleyDrop)} ·
              谷→最新反弹 +¥{fmtNum(statistics.cumulative?.valleyToNowRebound)}。
              「累加&lt;0 但净值&gt;1」是入金节奏的负效应，不是策略亏损。
            </div>
          </div>
        </>
      )}

      {/* 校正与操作记录：来自 operation_log（mcf 实证数据） */}
      {operations.length > 0 && (
        <div className="nav-panel">
          <div className="nav-panel-head">
            <div className="nav-panel-title">
              校正与操作记录
              <span className="nav-panel-tag">
                来源 operation_log · {operations.length} 条（含 8/12 之后的 mcf 实证数据）
              </span>
            </div>
          </div>
          <div className="op-list">
            {operations.map((op) => (
              <div
                key={`${op.date}-${op.operationType}-${op.snapshotDate}`}
                className={`op-item ${op.operationType === 'monthly_correction' ? 'op-item--correction' : 'op-item--manual'}`}
              >
                <div className="op-head">
                  <span className="op-date">{(op.date || '').slice(0, 10)}</span>
                  <span className="op-type">
                    {OP_TYPE_LABEL[op.operationType] || op.operationType}
                  </span>
                  {op.triggered ? (
                    <span className="op-flag op-flag--on">已触发</span>
                  ) : op.totalInvestment > 0 ? (
                    <span className="op-flag op-flag--manual">主动操作</span>
                  ) : (
                    <span className="op-flag">未触发</span>
                  )}
                  {op.totalInvestment > 0 && (
                    <span className="op-invest">
                      投入 ¥{Number(op.totalInvestment).toLocaleString('zh-CN')}
                    </span>
                  )}
                </div>
                <div className="op-metrics">
                  {op.vCurr != null && (
                    <span className="op-metric">
                      <em>校正前六大类</em>
                      <strong>¥{Number(op.vCurr).toLocaleString('zh-CN')}</strong>
                    </span>
                  )}
                  {op.deltaMTheory != null && (
                    <span className="op-metric">
                      <em>理论 Δm</em>
                      <strong>{Number(op.deltaMTheory).toFixed(2)}</strong>
                    </span>
                  )}
                  {op.deltaBTheory != null && (
                    <span className="op-metric">
                      <em>理论 Δb</em>
                      <strong>{Number(op.deltaBTheory).toFixed(2)}</strong>
                    </span>
                  )}
                  {op.deltaMActual != null && op.deltaBActual != null && (
                    <span className="op-metric">
                      <em>实际补仓</em>
                      <strong>
                        {Number(op.deltaMActual).toFixed(0)} / {Number(op.deltaBActual).toFixed(0)}
                      </strong>
                    </span>
                  )}
                  {op.budgetLimitUsed != null && (
                    <span className="op-metric">
                      <em>上限 M_max</em>
                      <strong>¥{Number(op.budgetLimitUsed).toLocaleString('zh-CN')}</strong>
                    </span>
                  )}
                </div>
                {op.notes?.length > 0 && (
                  <ul className="op-notes">
                    {op.notes.map((n, i) => (
                      <li key={i}>{n}</li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}