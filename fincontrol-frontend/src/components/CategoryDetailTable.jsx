import { useState } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * CategoryDetailTable — 六大类明细表格（1b.8 / 决策 3 含 profit 列）
 * <p>默认折叠（<details>），展开后展示每只基金的 amount / 占比 / 目标比例 / 偏差 / 持有收益。
 * <p>数据来自 latestSnapshot.categories[].funds[]（即 includeDetail=true 的 GET /snapshot/latest）。
 */
export const CategoryDetailTable = () => {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  const [isOpen, setIsOpen] = useState(false)

  if (loading) return <div className="cat-detail loading">加载中...</div>
  if (error) return <div className="cat-detail error">错误</div>

  // 扁平化所有 funds
  const allFunds = []
  for (const cat of (snap?.categories || [])) {
    for (const f of (cat.funds || [])) {
      allFunds.push({
        ...f,
        categoryName: cat.categoryName,
        targetRatio: cat.targetRatio,
      })
    }
  }

  return (
    <div className="cat-detail">
      <button className="toggle-btn" onClick={() => setIsOpen((v) => !v)}>
        {isOpen ? '▼' : '▶'} 展开明细（{allFunds.length} 条基金）
      </button>
      {isOpen && (
        allFunds.length === 0 ? (
          <div className="empty">暂无基金数据（请先上传 4 张截图）</div>
        ) : (
          <table className="cat-detail-table">
            <thead>
              <tr>
                <th>基金名称</th>
                <th>类别</th>
                <th>金额</th>
                <th>占比</th>
                <th>目标%</th>
                <th>偏差</th>
                <th>持有收益</th>
              </tr>
            </thead>
            <tbody>
              {allFunds.map((f) => (
                <tr key={`${f.categoryName}-${f.fundName}`}>
                  <td>{f.fundName}</td>
                  <td>{f.categoryName}</td>
                  <td>¥{Number(f.amount ?? 0).toFixed(2)}</td>
                  <td>{Number(f.proportion ?? 0).toFixed(2)}%</td>
                  <td>{Number(f.targetRatio ?? 0).toFixed(0)}%</td>
                  <td style={{ color: Number(f.deviation ?? 0) >= 0 ? 'var(--color-success)' : 'var(--color-error)' }}>
                    {Number(f.deviation ?? 0).toFixed(2)}%
                  </td>
                  <td style={{ color: Number(f.holdingProfit ?? f.profit ?? 0) >= 0 ? 'var(--color-success)' : 'var(--color-error)' }}>
                    ¥{Number(f.holdingProfit ?? f.profit ?? 0).toFixed(2)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </div>
  )
}
