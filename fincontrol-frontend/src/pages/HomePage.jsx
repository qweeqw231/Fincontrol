import { useEffect } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { BalanceCard } from '../components/BalanceCard.jsx'
import { TotalAssetCard } from '../components/TotalAssetCard.jsx'
import { CumulativeReturnCard } from '../components/CumulativeReturnCard.jsx'
import { SixCategoriesPie } from '../components/SixCategoriesPie.jsx'
import { CategoryDetailTable } from '../components/CategoryDetailTable.jsx'
import { RecentOperationsTimeline } from '../components/RecentOperationsTimeline.jsx'

/**
 * HomePage — 首页（1b.2 实装）
 * <p>三卡片（余额 / 六大类总值 / 累计收益率）+ 环形图 + 明细表 + 时间线。
 * <p>挂载时调 useAssetSnapshotStore.fetchLatest() 触发 4 个 API 并行获取。
 * <p>全部数据来自决策 4 v2 累计收益率简化算法 + 现有 GET /asset/* 端点。
 */
export default function HomePage() {
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)

  useEffect(() => {
    fetchLatest(1)
  }, [fetchLatest])

  return (
    <div className="home-page">
      <h1 className="page-title">首页 · 资产总览</h1>
      <p className="page-sub">三卡片 + 六大类环形图 + 明细 + 最近操作（1b.2 里程碑）</p>

      <div className="cards-row">
        <BalanceCard />
        <TotalAssetCard />
        <CumulativeReturnCard />
      </div>

      <div className="chart-section">
        <SixCategoriesPie />
      </div>

      <div className="table-section">
        <CategoryDetailTable />
      </div>

      <div className="timeline-section">
        <RecentOperationsTimeline />
      </div>
    </div>
  )
}
