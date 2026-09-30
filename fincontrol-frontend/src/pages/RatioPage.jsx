import { useState, useEffect, useMemo } from 'react'
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts'
import apiClient from '../api/client.js'
import { RATIO_HISTORY } from '../api/endpoints.js'
import './ratio-page.css'

/**
 * Phase 3：比例演化看板（RatioPage）
 *
 * 数据源：asset_snapshot（131 个交易日 2026-03-02 ~ 2026-09-24）
 *        六大类 actual_ratio 已按「六大类合计 = 100%」归一化；
 *        余额类 ratio 口径为「余额宝 / 总资产」，故在「含余额类」视图下前端重算为 7 项合计 100%。
 *
 * 设计要点（对齐 fundgraph ①-A 并修复其重叠问题）：
 *  1. 堆叠面积图 + 底部图例，图内不写数值标签，数值由 tooltip 承载。
 *  2. 四种视图组合（占比/金额 × 六大类/含余额类），避免多图堆叠造成阅读负担。
 *  3. 右侧当前快照对比表与图表并排，信息密度高但不重叠。
 */

const SIX_CATEGORIES = [
  { key: '货币类', color: '#10b981' },
  { key: '固收类', color: '#3b82f6' },
  { key: '商品类', color: '#f59e0b' },
  { key: 'A股权益类', color: '#ef4444' },
  { key: '海外权益类', color: '#8b5cf6' },
  { key: '港股大中华类', color: '#ec4899' },
]
const BALANCE = { key: '余额类', color: '#6b7280' }

function fmtPct(v) {
  return v == null ? '—' : `${Number(v).toFixed(2)}%`
}
function fmtAmount(v) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function xTickFormatter(v) {
  return v ? v.slice(2, 7) : ''
}

/** 堆叠图 tooltip：按当日占比降序，金额与占比并列 */
function RatioTooltip({ active, payload, mode, cats }) {
  if (!active || !payload || payload.length === 0) return null
  const p = payload[0].payload
  const rows = cats
    .map((c) => ({
      name: c.key,
      color: c.color,
      value: mode === 'ratio' ? p[c.key] : p[`${c.key}_amount`],
    }))
    .filter((r) => r.value != null)
    .sort((a, b) => b.value - a.value)
  const total = rows.reduce((s, r) => s + r.value, 0)
  return (
    <div className="ratio-tooltip">
      <div className="ratio-tooltip-date">{p.date}</div>
      <div className="ratio-tooltip-rows">
        {rows.map((r) => (
          <div key={r.name} className="ratio-tooltip-row">
            <span className="dot" style={{ background: r.color }} />
            <span className="name">{r.name}</span>
            <span className="val">
              {mode === 'ratio' ? fmtPct(r.value) : `¥${fmtAmount(r.value)}`}
            </span>
          </div>
        ))}
      </div>
      <div className="ratio-tooltip-total">
        <span>合计</span>
        <span>{mode === 'ratio' ? fmtPct(total) : `¥${fmtAmount(total)}`}</span>
      </div>
    </div>
  )
}

export default function RatioPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [mode, setMode] = useState('ratio') // 'ratio' | 'amount'
  const [includeBalance, setIncludeBalance] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    apiClient
      .get(RATIO_HISTORY)
      .then((res) => {
        if (!alive) return
        setData(res)
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

  const cats = useMemo(
    () => (includeBalance ? [...SIX_CATEGORIES, BALANCE] : SIX_CATEGORIES),
    [includeBalance]
  )

  // 数据点：含余额类视图下重算占比（7 项合计 100%）
  const chartData = useMemo(() => {
    const points = Array.isArray(data?.points) ? data.points : []
    return points.map((p) => {
      if (!includeBalance) return p
      const keys = [...SIX_CATEGORIES, BALANCE]
      const total = keys.reduce((s, c) => s + (p[`${c.key}_amount`] || 0), 0)
      if (total <= 0) return p
      const out = { ...p }
      for (const c of keys) {
        out[c.key] = ((p[`${c.key}_amount`] || 0) / total) * 100
      }
      return out
    })
  }, [data, includeBalance])

  // 最新快照 + 首日快照对比（用于右侧表格）
  const comparison = useMemo(() => {
    if (chartData.length === 0) return null
    const first = chartData[0]
    const last = chartData[chartData.length - 1]
    const rows = cats.map((c) => {
      const firstV = first[c.key]
      const lastV = last[c.key]
      return {
        ...c,
        first: firstV,
        last: lastV,
        delta: firstV != null && lastV != null ? lastV - firstV : null,
        lastAmount: last[`${c.key}_amount`],
      }
    })
    return { first, last, rows }
  }, [chartData, cats])

  if (loading) return <div className="ratio-state">⏳ 加载比例数据...</div>
  if (error) return <div className="ratio-state ratio-state--error">⚠️ {error}</div>
  if (!comparison) {
    return (
      <div className="ratio-state">
        暂无占比数据。请运行 <code>scripts/import-data/import-data.cjs</code> 导入外部数据源。
      </div>
    )
  }

  const { first, last, rows } = comparison

  return (
    <div className="page-shell ratio-page">
      <header className="ratio-header">
        <div>
          <h1>比例演化看板</h1>
          <p className="ratio-sub">
            六大类目标配置演化 · {first.date} ~ {last.date} · 共 {chartData.length} 个交易日
          </p>
        </div>
        <div className="ratio-source-tag">数据源：asset_table_total</div>
      </header>

      {/* 控制条：两种视角 × 两种口径 */}
      <div className="ratio-controls">
        <div className="ratio-toggle-group">
          <button
            type="button"
            className={mode === 'ratio' ? 'active' : ''}
            onClick={() => setMode('ratio')}
          >
            占比 %
          </button>
          <button
            type="button"
            className={mode === 'amount' ? 'active' : ''}
            onClick={() => setMode('amount')}
          >
            金额 ¥
          </button>
        </div>
        <div className="ratio-toggle-group">
          <button
            type="button"
            className={!includeBalance ? 'active' : ''}
            onClick={() => setIncludeBalance(false)}
          >
            六大类
          </button>
          <button
            type="button"
            className={includeBalance ? 'active' : ''}
            onClick={() => setIncludeBalance(true)}
          >
            含余额类
          </button>
        </div>
        <span className="ratio-hint">
          {mode === 'ratio'
            ? includeBalance
              ? '口径：7 项合计 100%（余额类含余额宝 + 余额）'
              : '口径：六大类合计 100%（不含余额类）'
            : '口径：各标的绝对市值（元）'}
        </span>
      </div>

      <div className="ratio-layout">
        {/* 堆叠面积图 */}
        <div className="ratio-panel">
          <div className="ratio-panel-head">
            <div className="ratio-panel-title">
              {mode === 'ratio' ? '占比时间线' : '市值时间线'}
              <span className="ratio-panel-tag">堆叠视图</span>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={360}>
            <AreaChart data={chartData} margin={{ top: 16, right: 24, bottom: 8, left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" vertical={false} />
              <XAxis
                dataKey="date"
                tickFormatter={xTickFormatter}
                tick={{ fontSize: 11, fill: '#6b7280' }}
                minTickGap={44}
                tickMargin={8}
                axisLine={{ stroke: '#e5e7eb' }}
                tickLine={false}
              />
              <YAxis
                tickFormatter={(v) =>
                  mode === 'ratio' ? `${Number(v).toFixed(0)}%` : `${Math.round(v / 1000)}k`
                }
                tick={{ fontSize: 11, fill: '#6b7280' }}
                width={48}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip content={<RatioTooltip mode={mode} cats={cats} />} />
              {cats.map((c) => {
                const dataKey = mode === 'ratio' ? c.key : `${c.key}_amount`
                return (
                  <Area
                    key={dataKey}
                    type="monotone"
                    dataKey={dataKey}
                    stackId="1"
                    stroke={c.color}
                    strokeWidth={1.2}
                    fill={c.color}
                    fillOpacity={0.72}
                    isAnimationActive={false}
                  />
                )
              })}
            </AreaChart>
          </ResponsiveContainer>

          {/* 底部图例：名称 + 最新占比（与图分离，避免压线） */}
          <div className="ratio-legend">
            {rows.map((r) => (
              <div key={r.key} className="ratio-legend-item">
                <span className="dot" style={{ background: r.color }} />
                <span className="name">{r.key}</span>
                <span className="val">
                  {mode === 'ratio' ? fmtPct(r.last) : `¥${fmtAmount(r.lastAmount)}`}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* 首末对比表 */}
        <div className="ratio-panel ratio-panel--table">
          <div className="ratio-panel-head">
            <div className="ratio-panel-title">
              首末对比
              <span className="ratio-panel-tag">
                {first.date} → {last.date}
              </span>
            </div>
          </div>
          <table className="ratio-compare-table">
            <thead>
              <tr>
                <th>大类</th>
                <th>首日</th>
                <th>最新</th>
                <th>变化</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key}>
                  <td className="cat-cell">
                    <span className="dot" style={{ background: r.color }} />
                    {r.key}
                  </td>
                  <td>{mode === 'ratio' ? fmtPct(r.first) : `¥${fmtAmount(first[`${r.key}_amount`])}`}</td>
                  <td className="strong">
                    {mode === 'ratio' ? fmtPct(r.last) : `¥${fmtAmount(r.lastAmount)}`}
                  </td>
                  <td className={r.delta == null ? '' : r.delta >= 0 ? 'pos' : 'neg'}>
                    {r.delta == null
                      ? '—'
                      : `${r.delta >= 0 ? '+' : ''}${r.delta.toFixed(2)}pp`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="ratio-note">
            ⓘ 「pp」= 百分点（percentage point），表示占比差值的绝对变化。
            条形堆叠顺序按目标配置由低波到高波排列，便于观察风险敞口的迁移。
          </div>
        </div>
      </div>
    </div>
  )
}