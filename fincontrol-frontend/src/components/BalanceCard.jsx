import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * BalanceCard — 余额类卡片（1b.6）
 * <p>显示 余额类总额（来自 GET /api/asset/balance）。
 * <p>纯 CSS（决策 17），不用 UI 库。
 * <p>2026-07-24 PR2：className="card"→"stat-card"、.card-title→.stat-card-title 等
 */
export const BalanceCard = () => {
  const balance = useAssetSnapshotStore((s) => s.balance)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  if (loading) {
    return (
      <div className="stat-card balance-card">
        <div className="stat-card-title">余额类</div>
        <div className="stat-card-value">加载中...</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="stat-card balance-card error">
        <div className="stat-card-title">余额类</div>
        <div className="stat-card-value">错误</div>
      </div>
    )
  }
  const total = balance?.balanceFundTotal ?? 0
  const snapshotDate = balance?.snapshotDate ?? '—'

  return (
    <div className="stat-card balance-card">
      <div className="stat-card-title">余额类</div>
      <div className="stat-card-value">¥{Number(total).toFixed(2)}</div>
      <div className="stat-card-sub">截至 {snapshotDate}</div>
    </div>
  )
}
