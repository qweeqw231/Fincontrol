import { useLocation } from 'react-router-dom'

/**
 * 通用占位页组件
 * @param {Object} props
 * @param {string} props.title - 页面标题
 * @param {string} props.description - 页面描述
 * @param {string} props.phase - 启用阶段（如 'Phase 1b'）
 * @param {boolean} props.disabled - 是否灰显（未启用）
 */
export function PlaceholderPage({ title, description, phase, disabled = false }) {
  const location = useLocation()
  return (
    <div className={`page-placeholder ${disabled ? 'is-disabled' : ''}`}>
      <header className="page-placeholder__header">
        <h1>{title}</h1>
        {disabled && <span className="page-placeholder__badge">{phase || '即将上线'}</span>}
      </header>
      <p className="page-placeholder__desc">{description}</p>
      <div className="page-placeholder__path">
        路径：<code>{location.pathname}</code>
      </div>
      {disabled && (
        <div className="page-placeholder__notice">
          此页面在 {phase || '后续阶段'} 启用，当前展示为占位。
        </div>
      )}
    </div>
  )
}

// 同时支持 default export（兼容 7 个 page 文件用 default import 的写法）
export default PlaceholderPage
