import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * RecentOperationsTimeline — 最近操作时间线（1b.9）
 * <p>从 GET /api/asset/operations/recent 取最近 5 条操作。
 * <p>每条：HH:MM:SS（operationDate 时间部分） + 简介（summary）。
 */
function fmtTime(iso) {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    return d.toTimeString().slice(0, 8) // HH:MM:SS
  } catch {
    return '—'
  }
}

export const RecentOperationsTimeline = () => {
  const ops = useAssetSnapshotStore((s) => s.operationsRecent)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)

  if (loading) return <div className="timeline loading">加载中...</div>
  if (error) return <div className="timeline error">错误</div>

  const items = (ops || []).slice(0, 5)

  return (
    <div className="timeline">
      <h3 className="section-title">最近操作</h3>
      {items.length === 0 ? (
        <div className="empty">暂无操作记录（请先上传截图）</div>
      ) : (
        <ul className="timeline-list">
          {items.map((op, idx) => (
            <li key={idx} className={`timeline-item ${op.operationType || ''}`}>
              <span className="time">{fmtTime(op.operationDate)}</span>
              <span className="summary">{op.summary || op.operationType || '—'}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
