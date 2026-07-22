import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * SixCategoriesPie — 六大类环形图（1b.7 / 决策 16）
 * <p>Recharts PieChart 全宽（来自 latestSnapshot.categories[]）。
 * <p>6 大类配色：货币类/固收类/商品类/A股权益类/海外权益类/港股大中华类。
 */
const COLORS = {
  '货币类':     'var(--color-cat-monetary)',
  '固收类':     'var(--color-cat-bond)',
  '商品类':     'var(--color-cat-commodity)',
  'A股权益类':  'var(--color-cat-a-share)',
  '海外权益类': 'var(--color-cat-overseas)',
  '港股大中华类':'var(--color-cat-hk)',
}

export const SixCategoriesPie = () => {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  if (loading) return <div className="six-cats-pie loading">加载中...</div>
  if (error) return <div className="six-cats-pie error">错误</div>

  const data = (snap?.categories || []).map((c) => ({
    name: c.categoryName,
    value: Number(c.categoryTotal ?? 0),
  }))

  if (data.length === 0) {
    return <div className="six-cats-pie empty">暂无数据</div>
  }

  return (
    <div className="six-cats-pie">
      <h3 className="section-title">六大类分布</h3>
      <div className="pie-row">
        <div className="pie-chart-wrap">
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={data}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                outerRadius={100}
                label={(d) => `${d.name} ${((d.percent || 0) * 100).toFixed(1)}%`}
              >
                {data.map((d) => (
                  <Cell key={d.name} fill={COLORS[d.name] || '#94a3b8'} />
                ))}
              </Pie>
              <Tooltip formatter={(v) => `¥${Number(v).toFixed(2)}`} />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <table className="ratio-table">
          <thead>
            <tr><th>类别</th><th>实际%</th><th>目标%</th><th>偏差</th></tr>
          </thead>
          <tbody>
            {(snap.categories || []).map((c) => (
              <tr key={c.categoryName}>
                <td>{c.categoryName}</td>
                <td>{Number(c.actualRatio ?? c.categoryPercentage ?? 0).toFixed(2)}%</td>
                <td>{Number(c.targetRatio ?? 0).toFixed(0)}%</td>
                <td style={{ color: Number(c.deviation ?? 0) >= 0 ? 'var(--color-success)' : 'var(--color-error)' }}>
                  {Number(c.deviation ?? 0).toFixed(2)}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
