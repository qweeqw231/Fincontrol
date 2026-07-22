import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * CumulativeReturnCard — 累计收益率卡片（1b.6 / 决策 4 v2）
 * <p>显示 累计收益率 = Σcumulative_profit / Σamount（口径 A / 全口径含余额类）。
 * <p>取自 GET /api/asset/cumulative-return（决策 4 v2 phase1_simple 算法）。
 * <p>底部带 algorithm 标识（"phase1_simple"）+ tooltip。
 */
export const CumulativeReturnCard = () => {
  const cum = useAssetSnapshotStore((s) => s.cumulativeReturn)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  if (loading) {
    return (
      <div className="card cumulative-return-card">
        <div className="card-title">累计收益率</div>
        <div className="card-value">加载中...</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="card cumulative-return-card error">
        <div className="card-title">累计收益率</div>
        <div className="card-value">错误</div>
      </div>
    )
  }
  if (!cum || !cum.available) {
    return (
      <div className="card cumulative-return-card disabled">
        <div className="card-title">累计收益率</div>
        <div className="card-value">—</div>
        <div className="card-sub">{cum?.message || 'Phase 3 上线'}</div>
      </div>
    )
  }
  const rate = Number(cum.returnRate ?? 0)
  const isPos = rate >= 0
  const algo = cum.algorithm || 'phase1_simple'

  return (
    <div className="card cumulative-return-card">
      <div className="card-title">累计收益率</div>
      <div
        className="card-value"
        style={{ color: isPos ? 'var(--color-success)' : 'var(--color-error)' }}
        title={`算法: ${algo}\n公式: Σcumulative_profit / Σamount (口径 A 含余额类)`}
      >
        {isPos ? '+' : ''}{(rate * 100).toFixed(2)}%
      </div>
      <div className="card-sub">算法：{algo}</div>
    </div>
  )
}
