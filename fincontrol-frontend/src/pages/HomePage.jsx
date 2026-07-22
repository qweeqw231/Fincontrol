import { useEffect } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { TotalAssetCard } from '../components/TotalAssetCard.jsx'
import { CumulativeReturnCard } from '../components/CumulativeReturnCard.jsx'
import { SixCategoriesPie } from '../components/SixCategoriesPie.jsx'
import { CategoryDetailTable } from '../components/CategoryDetailTable.jsx'
import { RecentOperationsTimeline } from '../components/RecentOperationsTimeline.jsx'

/**
 * HomePage — 资产总览（1b.2 Step 9 重写，参考 alipay_snapshot.html 风格）
 * <p>布局：
 *   1. AppHeader（深蓝渐变 + Logo + 当日快照信息）
 *   2. Hero（总资产 + 余额类 + 累计/持有 3 卡片）
 *   3. two-col（六大类占比饼图 + 实际/目标/偏差表）
 *   4. fund-detail（19 只基金明细，按大类分组，含 cat 小计）
 *   5. note-box（数据口径说明）
 *   6. summary-list（执行总结 6 条 pill 标签）
 *   7. recent-timeline（最近操作时间线）
 */
export default function HomePage() {
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)
  const refreshCounter = useAssetSnapshotStore((s) => s.refreshCounter)

  useEffect(() => {
    fetchLatest(1)
  }, [fetchLatest, refreshCounter])

  return (
    <div className="home-page">
      <header className="app-header">
        <div className="brand">
          <div className="logo">💰</div>
          <div className="brand-text">
            <h1>FinControl</h1>
            <div className="sub">个人资产配置全景 · 支付宝快照</div>
          </div>
        </div>
        <div className="header-meta">
          <SnapshotDate />
          <div>来源：支付宝</div>
        </div>
      </header>

      <main className="container">
        {/* stat-row：六大类总值 / 余额类 / 累计收益率 */}
        <div className="stat-row">
          <TotalAssetWithBalanceCard />
          <SixCategoriesTotalCard />
          <BalanceCardStat />
          <CumulativeReturnCard />
        </div>

        {/* two-col：饼图 + 实际/目标/偏差表 */}
        <div className="two-col">
          <SixCategoriesPie />
          <CategoryDetailTable />
        </div>

        {/* fund-detail：19 只基金明细 */}
        <FundDetailTable />

        {/* note-box */}
        <NoteBox />

        {/* summary-list */}
        <SummaryList />

        {/* recent-timeline */}
        <RecentOperationsTimeline />

        <div className="footer">FinControl · 仅供个人记录</div>
      </main>
    </div>
  )
}

/** 内部小组件：仅取 snapshotDate 渲染头部日期 */
function SnapshotDate() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const date = snap?.snapshotDate ?? '—'
  return <div className="date">{date}</div>
}

/** 1b.3.6 总资产卡（含余额类） */
function TotalAssetWithBalanceCard() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const total = snap?.totalAssetWithBalance ?? snap?.sixCategoriesTotal ?? 0
  const date = snap?.snapshotDate ?? '—'
  if (loading) return <div className="stat-card">加载中...</div>
  return (
    <div className="stat-card highlight">
      <div className="label">总资产（含余额类）· 截至 {date}</div>
      <div className="value">¥{Number(total).toFixed(2)}</div>
    </div>
  )
}

/** 1b.3.6 六大类总值卡（不含余额类） */
function SixCategoriesTotalCard() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  if (loading) return <div className="stat-card">加载中...</div>
  const total = snap?.sixCategoriesTotal ?? 0
  return (
    <div className="stat-card">
      <div className="label">六大类总值</div>
      <div className="value">¥{Number(total).toFixed(2)}</div>
    </div>
  )
}

/** 余额类 1 张卡（hero 已经放过，这里再放 stat-row 保持视觉布局） */
function BalanceCardStat() {
  const balance = useAssetSnapshotStore((s) => s.balance)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const total = balance?.balanceFundTotal ?? 0
  if (loading) return <div className="stat-card">加载中...</div>
  return (
    <div className="stat-card">
      <div className="label">
        <span className="dot" style={{ background: '#722ED1' }} />
        余额类（定投水源）
      </div>
      <div className="value">
        <span className="currency">¥</span>
        {Number(total).toFixed(2)}
      </div>
      <div className="sub">余额宝 · 7月定投资金池</div>
    </div>
  )
}

/** 内部：19 只基金明细（按大类分组，含 cat 小计） */
function FundDetailTable() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  if (loading) return <div className="section">加载中...</div>
  if (!snap?.categories) return <div className="section">暂无数据</div>

  // 过滤掉余额类（不计入六大类）+ 0 基金的大类
  const realCats = (snap.categories || []).filter(
    (c) => c.categoryName !== '余额类' && c.fundCount > 0
  )

  return (
    <div className="section">
      <div className="section-title">
        基金持仓明细
        <span className="tag">{realCats.reduce((s, c) => s + (c.fundCount || 0), 0)} 只基金 · 持有收益 vs 累计收益</span>
      </div>
      <table className="data-table">
        <thead>
          <tr>
            <th>基金名称</th>
            <th>持仓金额（元）</th>
            <th>持有收益（元）</th>
            <th>累计收益（元）</th>
            <th>占比</th>
          </tr>
        </thead>
        <tbody>
          {realCats.map((cat) => (
            <CategoryGroup key={cat.categoryName} cat={cat} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function CategoryGroup({ cat }) {
  const colorMap = {
    '货币类': '#5B8FF9', '固收类': '#5AD8A6', '商品类': '#5D7092',
    'A股权益类': '#F6BD16', '海外权益类': '#E8684A', '港股大中华类': '#6DC8EC',
  }
  const color = colorMap[cat.categoryName] || '#8c8c8c'
  const total = cat.categoryTotal ?? 0
  const pct = cat.actualRatio ?? cat.categoryPercentage ?? 0
  const funds = cat.funds || []

  return (
    <>
      <tr className="cat-header">
        <td colSpan={5}>
          <span className="dot" style={{ background: color }} />
          {cat.categoryName}
        </td>
      </tr>
      {funds.map((f, idx) => (
        <tr key={idx} className="fund-row">
          <td>{f.fundName}</td>
          <td>{f.amount != null ? Number(f.amount).toFixed(2) : '—'}</td>
          <td className={f.holdingProfit == null ? 'neutral' : Number(f.holdingProfit) >= 0 ? 'pos' : 'neg'}>
            {f.holdingProfit == null ? '—' : Number(f.holdingProfit).toFixed(2)}
          </td>
          <td className={f.cumulativeProfit == null ? 'neutral' : Number(f.cumulativeProfit) >= 0 ? 'pos' : 'neg'}>
            {f.cumulativeProfit == null ? '—' : Number(f.cumulativeProfit).toFixed(2)}
          </td>
          <td>{cat.fundCount > 0 && f.amount != null && total > 0
            ? (Number(f.amount) / total * 100).toFixed(1) + '%'
            : '—'}</td>
        </tr>
      ))}
      <tr className="total-row">
        <td>{cat.categoryName}小计</td>
        <td>{Number(total).toFixed(2)}</td>
        <td className={posNeg(cat, 'holding')}>{posNeg(cat, 'holding') || '—'}</td>
        <td className={posNeg(cat, 'cumulative')}>{posNeg(cat, 'cumulative') || '—'}</td>
        <td>{Number(pct).toFixed(2)}%</td>
      </tr>
    </>
  )
}

function snapTotal(snap) {
  return snap?.totalAssetWithBalance ?? snap?.sixCategoriesTotal ?? 0
}

function posNeg(cat, key) {
  // API 不返回 holding_total/cumulative_total（每只基金有），这里累加
  const sum = (cat.funds || []).reduce(
    (s, f) => s + Number(f[key === 'holding' ? 'holdingProfit' : 'cumulativeProfit'] || 0),
    0
  )
  if (sum === 0) return ''
  return (sum >= 0 ? '+' : '') + sum.toFixed(2)
}

/** note-box：数据口径说明 */
function NoteBox() {
  return (
    <div className="note-box">
      <div className="note-title">📌 数据口径说明</div>
      余额类（余额宝）不计入六大类配置；中加货币E为货币基金，仅展示累计收益，持有收益因本金变动无关以"—"表示。
    </div>
  )
}

/** summary-list：执行总结（参考 alipay_snapshot.html） */
function SummaryList() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  if (!snap) return null
  const items = []
  for (const cat of snap.categories || []) {
    const dev = Number(cat.deviation ?? 0)
    if (Math.abs(dev) < 0.5) {
      items.push({ pill: `${cat.categoryName}达标`, cls: 'pill-on', text: `占比 ${cat.actualRatio?.toFixed(2) ?? 0}%，接近目标 ${cat.targetRatio ?? 0}%` })
    } else if (dev > 0) {
      items.push({ pill: `${cat.categoryName} +${dev.toFixed(2)}%`, cls: 'pill-over', text: `占比 ${cat.actualRatio?.toFixed(2) ?? 0}%，超 ${dev.toFixed(2)}pp` })
    } else {
      items.push({ pill: `${cat.categoryName} ${dev.toFixed(2)}%`, cls: 'pill-under', text: `占比 ${cat.actualRatio?.toFixed(2) ?? 0}%，缺口 ${(-dev).toFixed(2)}pp` })
    }
  }
  if (items.length === 0) return null
  return (
    <div className="section">
      <div className="section-title">执行总结 <span className="tag">{snap.snapshotDate}</span></div>
      <ul className="summary-list">
        {items.slice(0, 6).map((it, i) => (
          <li key={i}>
            <span className={`pill ${it.cls}`}>{it.pill}</span>
            <span>{it.text}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
