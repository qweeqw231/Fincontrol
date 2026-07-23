import { useState } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { useUserConfigStore } from '../stores/userConfigStore.js'
import { useNavigate } from 'react-router-dom'
import { CumulativeReturnCard } from '../components/CumulativeReturnCard.jsx'
import {
  formatYuan,
  formatSignedAmount,
  formatSignedPercent,
  formatRatioSafe,
  safeNumber,
  getCategoryColor,
  getCategoryFundCount,
  getTotalFundCount,
  getBalanceFundCount,
  isBalanceCategory,
  calcRatios,
  sumSixTotal,
  sumBalanceTotal,
  classInCategoryRatio,
} from '../utils/formatters.js'

const BALANCE_NAME = '余额类'

// 1b.3 补救：默认目标比例（userConfigStore 尚未初始化时使用）
const DEFAULT_TARGET_RATIOS = {
  货币类: 10,
  固收类: 15,
  商品类: 25,
  A股权益类: 25,
  海外权益类: 20,
  港股大中华类: 5,
}

function sumFunds(funds) {
  return (Array.isArray(funds) ? funds : []).reduce(
    (s, f) => s + safeNumber(f.amount, 0),
    0
  )
}

/**
 * 1b.3.14 修复（DATA-G-013）：
 * 收益字段全部为 null 时返回 null（显示 —）；
 * 否则正常求和（正负相抵得 0 显示 +0.00，不能显示 —）。
 */
function sumIfAllDefined(getter, funds) {
  const arr = Array.isArray(funds) ? funds : []
  if (arr.length === 0) return null
  const vals = arr.map(getter)
  if (vals.every((v) => v == null)) return null
  return vals.reduce((s, v) => s + safeNumber(v, 0), 0)
}

function FundDetailTable({ categories, sixTotal, targetRatios, open, onToggle }) {
  const sixCats = (categories || []).filter((c) => !isBalanceCategory(c.categoryName))
  if (!open) {
    return (
      <button className="cat-detail-toggle" onClick={onToggle} type="button">
        ▶ 展开明细（{getTotalFundCount(categories) + getBalanceFundCount(categories)} 只基金 / 余额 {getBalanceFundCount(categories)} 只）
      </button>
    )
  }
  return (
    <div className="cat-detail">
      <div className="section-title">
        基金持仓明细
        <span className="tag">
          {getTotalFundCount(categories)} 只基金 · 持有收益 vs 累计收益
        </span>
        <button
          onClick={onToggle}
          type="button"
          style={{ marginLeft: 8, fontSize: 12, color: '#0A59F7' }}
        >
          收起
        </button>
      </div>
      <table className="cat-detail-table">
        <thead>
          <tr>
            <th>基金名称</th>
            <th>持仓金额（元）</th>
            <th>持有收益（元）</th>
            <th>累计收益（元）</th>
            <th>类内占比</th>
          </tr>
        </thead>
        <tbody>
          {sixCats.map((cat) => {
            const cTotal = safeNumber(cat.categoryTotal, 0)
            const funds = Array.isArray(cat.funds) ? cat.funds : []
            return (
              <SixCategoryGroup
                key={cat.categoryName}
                cat={cat}
                cTotal={cTotal}
                funds={funds}
              />
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function SixCategoryGroup({ cat, cTotal, funds }) {
  // 1b.3.14：用 sumIfAllDefined 替换旧的 buggy 逻辑
  const subtotalHolding = sumIfAllDefined((f) => f.holdingProfit, funds)
  const subtotalCumulative = sumIfAllDefined((f) => f.cumulativeProfit, funds)
  return (
    <>
      <tr className="cat-header">
        <td colSpan={5}>
          <span
            className="dot"
            style={{ background: getCategoryColor(cat.categoryName) }}
          />
          {cat.categoryName}
        </td>
      </tr>
      {funds.length === 0 ? (
        <tr className="fund-row">
          <td colSpan={5} style={{ color: '#8C8C8C', textAlign: 'center' }}>
            （暂无基金行）
          </td>
        </tr>
      ) : (
        funds.map((f, idx) => {
          const r = classInCategoryRatio(f.amount, cTotal)
          return (
            <tr key={`${cat.categoryName}-${idx}`} className="fund-row">
              <td>{f.fundName || '—'}</td>
              <td>{formatYuan(f.amount)}</td>
              <td className={f.holdingProfit == null ? 'neutral' : safeNumber(f.holdingProfit, 0) >= 0 ? 'pos' : 'neg'}>
                {f.holdingProfit == null ? '—' : formatSignedAmount(f.holdingProfit)}
              </td>
              <td className={f.cumulativeProfit == null ? 'neutral' : safeNumber(f.cumulativeProfit, 0) >= 0 ? 'pos' : 'neg'}>
                {f.cumulativeProfit == null ? '—' : formatSignedAmount(f.cumulativeProfit)}
              </td>
              <td>{formatRatioSafe(r)}</td>
            </tr>
          )
        })
      )}
      <tr className="subtotal-row">
        <td>{cat.categoryName}小计</td>
        <td>{formatYuan(cTotal)}</td>
        <td>{subtotalHolding == null ? '—' : formatSignedAmount(subtotalHolding)}</td>
        <td>{subtotalCumulative == null ? '—' : formatSignedAmount(subtotalCumulative)}</td>
        <td>100.00%</td>
      </tr>
    </>
  )
}

function SixPiePanel({ sixCats, sixTotal, categories, targetRatios, open, onToggle }) {
  return (
    <div className="panel">
      <div className="panel-title">
        六大类分布
        <span className="tag">基于 {formatYuan(sixTotal)} 元（不含余额类）</span>
      </div>
      <div className="six-pie-row">
        <div className="pie-wrap" style={{ minHeight: 280 }}>
          {open ? (
            <PieChart data={sixCats} sixTotal={sixTotal} />
          ) : (
            <div className="empty" style={{ minHeight: 280, lineHeight: '280px' }}>
              点击下方展开按钮查看环形图
            </div>
          )}
        </div>
        <ConfigDeviationTable
          sixCats={sixCats}
          sixTotal={sixTotal}
          targetRatios={targetRatios}
        />
      </div>
      <div style={{ marginTop: 12, textAlign: 'right' }}>
        <button
          type="button"
          className="cat-detail-toggle"
          onClick={onToggle}
        >
          {open ? '收起' : '展开'} 分布图
        </button>
      </div>
    </div>
  )
}

function PieChart({ data, sixTotal }) {
  if (!data || data.length === 0 || sixTotal <= 0) {
    return <div className="empty">暂无数据</div>
  }
  const total = sixTotal
  const segments = []
  let angle = -Math.PI / 2
  const cx = 110, cy = 110, rOuter = 100, rInner = 60
  data.forEach((c) => {
    const v = safeNumber(c.categoryTotal, 0)
    const pct = v / total
    const nextAngle = angle + pct * 2 * Math.PI
    const x1 = cx + rOuter * Math.cos(angle)
    const y1 = cy + rOuter * Math.sin(angle)
    const x2 = cx + rOuter * Math.cos(nextAngle)
    const y2 = cy + rOuter * Math.sin(nextAngle)
    const x3 = cx + rInner * Math.cos(nextAngle)
    const y3 = cy + rInner * Math.sin(nextAngle)
    const x4 = cx + rInner * Math.cos(angle)
    const y4 = cy + rInner * Math.sin(angle)
    const large = pct > 0.5 ? 1 : 0
    const path = `M${cx},${cy} L${x1},${y1} A${rOuter},${rOuter} 0 ${large} 1 ${x2},${y2} L${x3},${y3} A${rInner},${rInner} 0 ${large} 0 ${x4},${y4} Z`
    segments.push({
      path,
      color: getCategoryColor(c.categoryName),
      name: c.categoryName,
      pct: pct * 100,
      value: v,
    })
    angle = nextAngle
  })
  return (
    <svg width={220} height={220} viewBox="0 0 220 220" role="img" aria-label="六大类环形图">
      {segments.map((s, i) => (
        <path key={i} d={s.path} fill={s.color} stroke="#fff" strokeWidth={1.5}>
          <title>{`${s.name} ${formatYuan(s.value)} (${s.pct.toFixed(2)}%)`}</title>
        </path>
      ))}
    </svg>
  )
}

function ConfigDeviationTable({ sixCats, sixTotal, targetRatios }) {
  return (
    <table className="ratio-table">
      <thead>
        <tr>
          <th>大类</th>
          <th>金额（元）</th>
          <th>占六大类</th>
          <th>目标</th>
          <th>偏差</th>
        </tr>
      </thead>
      <tbody>
        {sixCats.map((c) => {
          const r = calcRatios(sixTotal, c.categoryTotal, (targetRatios || {})[c.categoryName])
          return (
            <tr key={c.categoryName}>
              <td>
                <span className="pname">
                  <span
                    className="dot"
                    style={{ background: getCategoryColor(c.categoryName) }}
                  />
                  {c.categoryName}
                </span>
              </td>
              <td>{formatYuan(c.categoryTotal)}</td>
              <td>{formatRatioSafe(r.actual)}</td>
              <td>{Number.isFinite(r.target) ? r.target.toFixed(0) + '%' : '—'}</td>
              <td
                className={
                  safeNumber(r.deviation, 0) >= 0 ? 'pos' : 'neg'
                }
              >
                {formatSignedPercent(r.deviation)}
              </td>
            </tr>
          )
        })}
        <tr className="total-row">
          <td>六大类合计</td>
          <td>{formatYuan(sixTotal)}</td>
          <td>100.00%</td>
          <td>100%</td>
          <td>—</td>
        </tr>
      </tbody>
    </table>
  )
}

function SummaryList({ categories, sixTotal, targetRatios }) {
  const sixCats = (categories || []).filter((c) => !isBalanceCategory(c.categoryName))
  const items = sixCats
    .map((c) => {
      const r = calcRatios(sixTotal, c.categoryTotal, (targetRatios || {})[c.categoryName])
      return { c, r }
    })
    .filter(({ r }) => Number.isFinite(r.actual) && Number.isFinite(r.target))
  if (items.length === 0) return null
  return (
    <div className="section-card">
      <div className="section-title">
        执行总结
        <span className="tag">{categories?.[0]?.snapshotDate || '—'}</span>
      </div>
      <ul className="summary-list">
        {items.map(({ c, r }) => {
          const dev = safeNumber(r.deviation, 0)
          let pillCls = 'pill-on'
          let pillText = `${c.categoryName}达标`
          if (Math.abs(dev) < 0.5) {
            pillCls = 'pill-on'
            pillText = `${c.categoryName}达标`
          } else if (dev > 0) {
            pillCls = 'pill-over'
            pillText = `${c.categoryName} +${dev.toFixed(2)}%`
          } else {
            pillCls = 'pill-under'
            pillText = `${c.categoryName} ${dev.toFixed(2)}%`
          }
          return (
            <li key={c.categoryName}>
              <span className={`pill ${pillCls}`}>{pillText}</span>
              <span>
                {c.categoryName} 占比 {r.actual.toFixed(2)}%
                {Math.abs(dev) >= 0.5
                  ? dev > 0
                    ? `，超 ${dev.toFixed(2)}pp`
                    : `，缺口 ${(-dev).toFixed(2)}pp`
                  : '，接近目标'}
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function QuickActions({ navigate }) {
  return (
    <div className="quick-actions">
      <div
        className="action"
        onClick={() => navigate('/data')}
        role="button"
        tabIndex={0}
      >
        <span className="icon">📊</span>
        <div className="body">
          <div className="title">上传新快照</div>
          <div className="sub">解析 + 入库</div>
        </div>
      </div>
      <div
        className="action"
        onClick={() => navigate('/correction')}
        role="button"
        tabIndex={0}
      >
        <span className="icon">📅</span>
        <div className="body">
          <div className="title">月度校正</div>
          <div className="sub">配置 + 求解</div>
        </div>
      </div>
      <div
        className="action"
        onClick={() => navigate('/ai')}
        role="button"
        tabIndex={0}
      >
        <span className="icon">🤖</span>
        <div className="body">
          <div className="title">AI 顾问</div>
          <div className="sub">多轮对话</div>
        </div>
      </div>
    </div>
  )
}

/**
 * 1b.3.12 修复（BUG-P4-004 / DATA-G-012 / HOME-008）：
 * 当后端 summary 文本为"解析 0 只基金"时，前端追加橙色 ⚠"基金数未知"标注，
 * 避免把 AI 解析失败/模型未返回的记录静默显示为"0"。
 */
function isZeroFundAnomaly(summary) {
  if (!summary || typeof summary !== 'string') return false
  return /解析\s*0\s*只基金/.test(summary)
}

function RecentOps({ ops }) {
  const items = (ops || []).slice(0, 5)
  function fmtTime(iso) {
    if (!iso) return '—'
    try {
      const d = new Date(iso)
      return d.toTimeString().slice(0, 8)
    } catch {
      return '—'
    }
  }
  function renderSummary(op) {
    const text = op.summary || op.operationType || '—'
    if (isZeroFundAnomaly(text)) {
      return (
        <span>
          {text}
          <span
            style={{
              color: '#fa8c16',
              fontSize: 12,
              marginLeft: 6,
              fontWeight: 500,
            }}
            title="后端 AI 解析未返回完整基金数据，请检查截图或重新解析"
          >
            ⚠ 基金数未知
          </span>
        </span>
      )
    }
    return text
  }
  return (
    <div className="timeline">
      <div className="section-title">
        最近操作
        <span className="tag" style={{ marginLeft: 'auto' }}>
          最近 {items.length} 条
        </span>
      </div>
      {items.length === 0 ? (
        <div className="empty">暂无操作记录（请先上传截图）</div>
      ) : (
        <ul className="timeline-list">
          {items.map((op, idx) => (
            <li
              key={idx}
              className={`timeline-item ${op.operationType || ''}`}
            >
              <span className="time">{fmtTime(op.operationDate)}</span>
              <span className="summary">{renderSummary(op)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function HomePage() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const cum = useAssetSnapshotStore((s) => s.cumulativeReturn)
  const ops = useAssetSnapshotStore((s) => s.operationsRecent)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)
  // 1b.3.15：目标比例从 userConfigStore 订阅（DATA-G-004：user_config 为权威源）
  const storedTargetRatios = useUserConfigStore((s) => s.targetRatios)
  const navigate = useNavigate()
  const [pieOpen, setPieOpen] = useState(false)
  const [fundOpen, setFundOpen] = useState(false)

  if (loading) {
    return <div className="page-shell"><div className="empty">加载中...</div></div>
  }
  if (error) {
    return (
      <div className="page-shell">
        <div className="error">加载失败：{String(error)}</div>
      </div>
    )
  }
  if (!snap) {
    return (
      <div className="page-shell">
        <header className="app-header">
          <div className="brand">
            <span className="logo">💰</span>
            <div className="brand-text">
              <h1>FinControl</h1>
              <div className="sub">个人资产配置全景 · 支付宝快照</div>
            </div>
          </div>
          <div className="meta">
            <div className="date">—</div>
            <div>来源：支付宝</div>
          </div>
        </header>
        <div className="empty">暂无快照数据，请先到 /data 上传资产截图。</div>
      </div>
    )
  }

  const categories = Array.isArray(snap.categories) ? snap.categories : []
  const sixCats = categories.filter((c) => !isBalanceCategory(c.categoryName))
  const sixTotal = sumSixTotal(categories) || safeNumber(snap.sixCategoriesTotal, 0)
  const balanceTotal =
    sumBalanceTotal(categories) || safeNumber(snap.balanceFund, 0)
  const totalAll = sixTotal + balanceTotal
  const totalFundCount = getTotalFundCount(categories)
  const balFundCount = getBalanceFundCount(categories)
  // cumDate 仍用于 hero-card 底部（"截至 xxx · ..."）
  const cumDate = cum?.snapshotDate || snap.snapshotDate || '—'
  // 1b.3.15：userConfigStore 为空时回退到内置默认值
  const targetRatios = (storedTargetRatios && Object.keys(storedTargetRatios).length > 0)
    ? storedTargetRatios
    : DEFAULT_TARGET_RATIOS

  return (
    <div className="page-shell">
      <header className="app-header">
        <div className="brand">
          <span className="logo">💰</span>
          <div className="brand-text">
            <h1>FinControl</h1>
            <div className="sub">个人资产配置全景 · 支付宝快照</div>
          </div>
        </div>
        <div className="meta">
          <div className="date">{snap.snapshotDate || '—'}</div>
          <div>来源：支付宝</div>
        </div>
      </header>

      <div className="hero-card">
        <div className="hero-label">总资产（含余额类）</div>
        <div className="hero-value">¥ {formatYuan(totalAll)}</div>
        <div className="hero-foot">
          截至 {cumDate} · 六大类 {formatYuan(sixTotal)} 元 · 余额{' '}
          {formatYuan(balanceTotal)} 元
        </div>
      </div>

      <div className="stat-row">
        <div className="card">
          <div className="label">
            <span
              className="dot"
              style={{ background: '#0A59F7' }}
            />
            六大类总值
          </div>
          <div className="value">¥ {formatYuan(sixTotal)}</div>
          <div className="sub">{totalFundCount} 只基金 · 配置基准</div>
        </div>
        <div className="card">
          <div className="label">
            <span
              className="dot"
              style={{ background: '#722ED1' }}
            />
            余额类
          </div>
          <div className="value">¥ {formatYuan(balanceTotal)}</div>
          <div className="sub">{balFundCount} 只基金 · 定投水源</div>
        </div>
        {/* P5-1: 1b.2 累计/持有收益 ℹ️ 提示恢复 —— 用 CumulativeReturnCard 组件自带 4 个 ℹ️ + InfoModal */}
        <CumulativeReturnCard />
      </div>

      <div className="two-col">
        <SixPiePanel
          sixCats={sixCats}
          sixTotal={sixTotal}
          categories={categories}
          targetRatios={targetRatios}
          open={pieOpen}
          onToggle={() => setPieOpen((v) => !v)}
        />
        <div className="panel">
          <div className="panel-title">
            数据口径说明
            <span className="tag">v1.0-DRAFT</span>
          </div>
          <div className="note-box" style={{ marginBottom: 0 }}>
            <div className="note-title">📌 数据口径</div>
            “六大类”仅含货币类、固收类、商品类、A股权益类、海外权益类、港股大中华类。
            余额类不参与占六大类比例与目标偏差；首页所有金额、收益、基金数均绑定
            <code style={{ background: '#fff', padding: '0 4px' }}>
              snapshot_meta.is_current
            </code>
            对应日期，避免跨日累加。基金明细“类内占比”分母为大类金额；配置表
            “占六大类”分母为六大类总值；零值（如 +3.84 + -3.84）须显示
            <code style={{ background: '#fff', padding: '0 4px' }}>0.00</code>
            ，未知的 null 才显示
            <code style={{ background: '#fff', padding: '0 4px' }}>—</code>。
          </div>
        </div>
      </div>

      <FundDetailTable
        categories={categories}
        sixTotal={sixTotal}
        targetRatios={targetRatios}
        open={fundOpen}
        onToggle={() => setFundOpen((v) => !v)}
      />

      <SummaryList
        categories={categories}
        sixTotal={sixTotal}
        targetRatios={targetRatios}
      />

      <QuickActions navigate={navigate} />

      <RecentOps ops={ops} />

      <div style={{ textAlign: 'center', color: '#8C8C8C', fontSize: 12, padding: 16 }}>
        FinControl · 仅供个人记录
      </div>
    </div>
  )
}
