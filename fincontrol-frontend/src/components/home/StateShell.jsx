/**
 * StateShell（Phase 1b.4 PR1 · 修复 HOME-003）
 *
 * 统一首页三态视觉（loading / error / empty）：
 * - 不引入新依赖
 * - 沿用 global.css 中已有的 .app-header / .page-shell 等类
 * - 不与现有 .hero-card / .stat-card 等冲突（独立的 .state-shell 类）
 *
 * 用法：
 *   <StateShell icon="⏳" title="加载中..." sub="正在拉取最新快照" />
 *   <StateShell icon="⚠️" title="加载失败" sub={friendlyError(err)} action={<button onClick={retry}>重试</button>} />
 *   <StateShell icon="📊" title="暂无资产快照" sub="上传 4 张..." action={<button onClick={() => nav('/data')}>立即上传 →</button>} />
 */
export function StateShell({ icon, title, sub, action }) {
  return (
    <div className="page-shell">
      <header className="app-header">
        <div className="brand">
          <span className="logo">💰</span>
          <div className="brand-text">
            <h1>FinControl</h1>
            <div className="sub">个人资产配置全景 · 支付宝快照</div>
          </div>
        </div>
        <div className="meta">
          <div className="date">—</div>
          <div>来源：支付宝</div>
        </div>
      </header>

      <div className="state-shell" role="status">
        <div className="state-icon" aria-hidden="true">{icon}</div>
        <h2 className="state-title">{title}</h2>
        {sub ? <p className="state-sub">{sub}</p> : null}
        {action ? <div className="state-action">{action}</div> : null}
      </div>
    </div>
  )
}

export default StateShell