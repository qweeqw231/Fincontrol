import { PlaceholderPage } from '../components/common/PlaceholderPage.jsx'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * DataPage — 数据管理页（1b.2 占位 + Step 6 占位 confirm 按钮）
 * <p>1b.3 完整实装：上传截图 → AI 解析 → 大类确认面板 → 10 秒撤销。
 * <p>1b.2 Step 6：下方"模拟 confirm"按钮调 {@link useAssetSnapshotStore#bumpRefresh}
 *     + {@link useAssetSnapshotStore#fetchLatest}，验证 HomePage 全局联动有效。
 *     1b.3 替换为真实 confirm API 调用。
 */
export default function DataPage() {
  const bumpRefresh = useAssetSnapshotStore((s) => s.bumpRefresh)
  const fetchLatest  = useAssetSnapshotStore((s) => s.fetchLatest)

  const handleSimulateConfirm = async () => {
    // 1b.2 占位：真实 confirm API 还没接入
    // 仅触发全局联动（bumpRefresh + fetchLatest），证明 HomePage 能响应
    bumpRefresh()
    await fetchLatest(1)
  }

  return (
    <div className="data-page">
      <PlaceholderPage
        title="数据管理"
        description="上传截图 → AI 解析 → 大类确认面板 → 10 秒撤销（1b.3 里程碑实现）"
        phase="Phase 1b"
      />
      <div className="simulate-confirm">
        <h3>1b.2 Step 6 占位按钮（仅用于验证全局联动）</h3>
        <p>点击模拟 confirm，应触发首页三卡片 + 环形图 + 明细 + 时间线 自动重拉。</p>
        <button className="primary-btn" onClick={handleSimulateConfirm}>
          模拟 confirm 触发首页刷新
        </button>
        <p className="simulate-hint">
          1b.3 替换为真实 POST /api/snapshot/confirm（multipart upload + multipart confirm body）。
        </p>
      </div>
    </div>
  )
}
