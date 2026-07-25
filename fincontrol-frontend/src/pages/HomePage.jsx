import { useState, useEffect, useRef } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { useUserConfigStore } from '../stores/userConfigStore.js'
import { shutdownServer } from '../api/client.js'
import { useNavigate } from 'react-router-dom'
import { CumulativeReturnCard } from '../components/CumulativeReturnCard.jsx'
import { StateShell } from '../components/home/StateShell.jsx'
import {
  formatYuan,
  formatSignedAmount,
  formatSignedPercent,
  formatRatioSafe,
  safeNumber,
  friendlyError,
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
        {/* 1b.4 PR4a · HOME-014 ⚪：100% 是设计意图，防误删加 tooltip */}
        <td title="大类内部各基金持仓占比之和（恒为 100%）">100.00%</td>
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
      {open ? (
        <div className="six-pie-row">
          <div className="pie-wrap">
            <PieChart data={sixCats} sixTotal={sixTotal} />
          </div>
          <ConfigDeviationTable
            sixCats={sixCats}
            sixTotal={sixTotal}
            targetRatios={targetRatios}
          />
        </div>
      ) : (
        <ConfigDeviationTable
          sixCats={sixCats}
          sixTotal={sixTotal}
          targetRatios={targetRatios}
        />
      )}
      <div style={{ marginTop: 12, textAlign: 'right' }}>
        <button
          type="button"
          className="cat-detail-toggle"
          onClick={onToggle}
        >
          {open ? '切换为表格' : '切换为环形图'}
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
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)
  // 1b4pr6b-recovery (2026-07-25)：订阅 refreshCounter，让 DataPage confirm 后 bumpRefresh()
  // 能驱动 HomePage 主动重拉（原先「[]」+「!snap」守卫会导致 Homepage 吃到 stale 数据）。
  const refreshCounter = useAssetSnapshotStore((s) => s.refreshCounter)
  // 1b.3.15：目标比例从 userConfigStore 订阅（DATA-G-004：user_config 为权威源）
  const storedTargetRatios = useUserConfigStore((s) => s.targetRatios)
  const navigate = useNavigate()
  const [pieOpen, setPieOpen] = useState(false)
  const [fundOpen, setFundOpen] = useState(false)

  // 1b.4 PR8 / 决策 35：HomePage 关闭服务按钮状态机
  // idle → 点 1 下后 confirming（5 秒倒计时 + 按钮变红）
  // confirming → 5 秒内点 2 下触发 shutdown → 按钮 disabled + 'shutting-down' toast
  // shutting-down → 后端 800ms 后退出；前端持续显示该状态直到刷新/重建
  const [shutdownState, setShutdownState] = useState('idle')  // 'idle' | 'confirming' | 'shutting-down'
  const shutdownTimerRef = useRef(null)

  // 5 秒内不点 2 下 → 自动回到 idle
  function handleShutdownClick() {
    if (shutdownState === 'shutting-down') return
    if (shutdownState === 'idle') {
      setShutdownState('confirming')
      // 5 秒倒计时
      if (shutdownTimerRef.current) clearTimeout(shutdownTimerRef.current)
      shutdownTimerRef.current = setTimeout(() => {
        setShutdownState('idle')
        shutdownTimerRef.current = null
      }, 5000)
      return
    }
    if (shutdownState === 'confirming') {
      // 二次点击 → 调后端
      if (shutdownTimerRef.current) clearTimeout(shutdownTimerRef.current)
      shutdownTimerRef.current = null
      setShutdownState('shutting-down')
      shutdownServer().catch((err) => {
        // 同步响应成功但后续网络断开是预期的（SpringApplication.exit 正在生效）
        if (err && err.code !== 0 && !String(err.message || '').includes('Network')) {
          console.warn('[shutdown] 后端异常：', err)
          setShutdownState('idle')  // 失败时回退到 idle 允许重试
        }
      })
    }
  }

  // 卸载时清理 timer
  useEffect(() => {
    return () => {
      if (shutdownTimerRef.current) clearTimeout(shutdownTimerRef.current)
    }
  }, [])

  // 1b4pr6b-recovery (2026-07-25)：同时触发于 ①首页 mount ②DataPage bumpRefresh()。
  // 依赖列表改为 [refreshCounter]：store 默认 0 触发起始拉取，
  // DataPage confirm 成功调 bumpRefresh → refreshCounter 递增 → 本 effect 重跑。
  useEffect(() => {
    if (error) return
    if (loading) return
    fetchLatest(1)
    // fetchLatest 本身未含在 deps 不影响本 effect 语义
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshCounter])

  // PR1 修复 HOME-003：加载中 → StateShell
  if (loading) {
    return <StateShell icon="⏳" title="加载中..." sub="正在拉取最新快照" />
  }

  // PR1 修复 HOME-003 + GLOBAL-007：错误 → StateShell + friendlyError + 重试按钮
  if (error) {
    return (
      <StateShell
        icon="⚠️"
        title="加载失败"
        sub={friendlyError(error)}
        action={
          <button
            className="primary-btn"
            onClick={() => fetchLatest(1)}
            type="button"
          >
            重试
          </button>
        }
      />
    )
  }

  // PR1 修复 HOME-002 + HOME-003：空 → StateShell + 立即上传按钮
  if (!snap) {
    return (
      <StateShell
        icon="📊"
        title="暂无资产快照"
        sub="上传 4 张支付宝基金截图，自动解析你的六大类配置"
        action={
          <button
            className="primary-btn"
            onClick={() => navigate('/data')}
            type="button"
          >
            立即上传 →
          </button>
        }
      />
    )
  }

  const categories = Array.isArray(snap.categories) ? snap.categories : []
  const sixCats = categories.filter((c) => !isBalanceCategory(c.categoryName))
  // 1b4pr6b-recovery (2026-07-25 03:30+)：优先信后端权威总额，仅在后端未传时才退到 sumSixTotal
  // 防止 categories 数组漏类时，sumSixTotal 误把后端已算对的总额覆盖掉（原 bug）。
  const sixTotal =
    safeNumber(snap.sixCategoriesTotal, 0) || sumSixTotal(categories)
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
          {/* 1b.4 PR9：emoji 💰 换成真正的 logo */}
          <img className="logo" src="/brand/logo.png" alt="FinControl" />
          <div className="brand-text">
            <h1>FinControl</h1>
            <div className="sub">个人资产配置全景 · 支付宝快照</div>
          </div>
        </div>
        {/* 1b.4 PR9：meta + 关闭按钮 作为 group 推到右边 */}
        <div className="header-actions">
          <div className="meta">
            <div className="date">{snap.snapshotDate || '—'}</div>
            <div>来源：支付宝</div>
          </div>
          {/* 1b.4 PR8 / 决策 35：关闭服务按钮（右上角） */}
          <button
            type="button"
            className={`shutdown-btn shutdown-btn--${shutdownState}`}
            data-testid="shutdown-btn"
            data-state={shutdownState}
            onClick={handleShutdownClick}
            disabled={shutdownState === 'shutting-down'}
            title="点击关闭服务，可以停止本系统的运行以节约资源"
            aria-label="关闭服务"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true">
              <path d="M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.16 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z" />
            </svg>
          </button>
        </div>
      </header>

      {/* 1b.4 PR9：confirming 状态仅显示顶部 toast（5 秒短时反馈） */}
      {shutdownState === 'confirming' && (
        <div
          className="shutdown-toast shutdown-toast--confirming"
          data-testid="shutdown-toast"
          role="status"
        >
          再点一次确认关闭（5 秒倒计时）
        </div>
      )}

      {/* 1b.4 PR9：shutting-down 状态升级为全屏 modal（明确告知系统已不可用） */}
      {shutdownState === 'shutting-down' && (
        <div
          className="shutdown-modal"
          data-testid="shutdown-modal"
          role="status"
          aria-live="polite"
        >
          <div className="shutdown-modal-content">
            <img className="logo" src="/brand/logo.png" alt="" />
            <h2>服务已停止</h2>
            <p className="lead">您可以关闭本页面</p>
            <p className="hint">下次启动请双击桌面 FinControl 图标</p>
          </div>
        </div>
      )}

      <div className="hero-card">
        <div className="hero-label">总资产（含余额类）</div>
        {/* 1b.4 PR4a · HOME-009：¥ 用 thin space + withSymbol */}
        <div className="hero-value">{formatYuan(totalAll, { withSymbol: true })}</div>
        <div className="hero-foot">
          截至 {cumDate} · 六大类 {formatYuan(sixTotal)} 元 · 余额{' '}
          {formatYuan(balanceTotal)} 元
        </div>
      </div>

      <div className="stat-row">
        <div className="stat-card">
          <div className="label">
            <span
              className="dot"
              style={{ background: '#0A59F7' }}
            />
            六大类总值
          </div>
          <div className="value">{formatYuan(sixTotal, { withSymbol: true })}</div>
          <div className="sub">{totalFundCount} 只基金 · 配置基准</div>
        </div>
        <div className="stat-card">
          <div className="label">
            <span
              className="dot"
              style={{ background: '#722ED1' }}
            />
            余额类
          </div>
          <div className="value">{formatYuan(balanceTotal, { withSymbol: true })}</div>
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
          </div>
          <div className="note-box" style={{ marginBottom: 0 }}>
            <div className="note-title">📌 数据口径</div>
            "六大类"仅含货币类、固收类、商品类、A股权益类、海外权益类、港股大中华类。
            余额类不参与占六大类比例与目标偏差；首页所有金额、收益、基金数均绑定当前快照日期，避免跨日累加。
            基金明细"类内占比"分母为大类金额；配置表"占六大类"分母为六大类总值；零值（如 +3.84 + -3.84）须显示 <strong>0.00</strong>，未知的 null 才显示 <strong>—</strong>。
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