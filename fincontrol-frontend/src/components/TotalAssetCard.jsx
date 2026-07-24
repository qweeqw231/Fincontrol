import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * TotalAssetCard — 六大类总值卡片（1b.6）
 * <p>显示 六大类合计金额（不含余额类 / 来自 GET /api/snapshot/latest?includeDetail=true 的 sixCategoriesTotal）。
 * <p>2026-07-24 PR2：className="card"→"stat-card"、.card-title→.stat-card-title 等
 */
export const TotalAssetCard = () => {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  if (loading) {
    return (
      <div className="stat-card total-asset-card">
        <div className="stat-card-title">六大类总值</div>
        <div className="stat-card-value">加载中...</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="stat-card total-asset-card error">
        <div className="stat-card-title">六大类总值</div>
        <div className="stat-card-value">错误</div>
      </div>
    )
  }
  const total = snap?.sixCategoriesTotal ?? 0
  const snapshotDate = snap?.snapshotDate ?? '—'

  return (
    <div className="stat-card total-asset-card">
      <div className="stat-card-title">六大类总值</div>
      <div className="stat-card-value">¥{Number(total).toFixed(2)}</div>
      <div className="stat-card-sub">截至 {snapshotDate}</div>
    </div>
  )
}
