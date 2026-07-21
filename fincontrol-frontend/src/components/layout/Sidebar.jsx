import { useState, useEffect } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import '../../styles/sidebar.css'

const STORAGE_KEY = 'fincontrol.sidebar.collapsed'

// 菜单配置（acceptance-criteria §1b.5）
const MENU_ITEMS = [
  { path: '/',            label: '首页',     icon: '🏠', enabled: true,  phase: '1b.2' },
  { path: '/data',        label: '数据管理', icon: '📊', enabled: true,  phase: '1b.3' },
  { path: '/correction',  label: '月度校正', icon: '📅', enabled: false, phase: 'Phase 2' },
  { path: '/config',      label: '资产配置', icon: '⚙️', enabled: false, phase: 'Phase 2' },
  { path: '/nav',         label: '净值曲线', icon: '📈', enabled: false, phase: 'Phase 3' },
  { path: '/ratio',       label: '比例演化', icon: '🥧', enabled: false, phase: 'Phase 3' },
  { path: '/ai',          label: 'AI 顾问',  icon: '🤖', enabled: true,  phase: '1b.4' },
  { path: '/quarterly',   label: '季度操作', icon: '🎯', enabled: false, phase: 'Phase 5a' },
]

export default function Sidebar() {
  const location = useLocation()
  // 折叠状态：优先读 localStorage，初次访问默认展开
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'true'
    } catch {
      return false
    }
  })

  // 同步到 localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(collapsed))
    } catch {
      /* localStorage 不可用时静默 */
    }
  }, [collapsed])

  const toggle = () => setCollapsed((c) => !c)

  return (
    <aside className={`sidebar ${collapsed ? 'is-collapsed' : ''}`}>
      <div className="sidebar__brand">
        <span className="sidebar__brand-icon">💰</span>
        {!collapsed && <span className="sidebar__brand-text">FinControl</span>}
      </div>

      <nav className="sidebar__nav">
        {MENU_ITEMS.map((item) => {
          const isActive = location.pathname === item.path
          if (!item.enabled) {
            return (
              <div
                key={item.path}
                className={`sidebar__item is-disabled ${isActive ? 'is-active' : ''}`}
                title={`${item.label} · ${item.phase} 启用`}
              >
                <span className="sidebar__item-icon">{item.icon}</span>
                {!collapsed && (
                  <span className="sidebar__item-label">{item.label}</span>
                )}
                {!collapsed && (
                  <span className="sidebar__item-badge">{item.phase}</span>
                )}
              </div>
            )
          }
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `sidebar__item ${isActive ? 'is-active' : ''}`
              }
              title={item.label}
            >
              <span className="sidebar__item-icon">{item.icon}</span>
              {!collapsed && (
                <span className="sidebar__item-label">{item.label}</span>
              )}
            </NavLink>
          )
        })}
      </nav>

      <button
        type="button"
        className="sidebar__toggle"
        onClick={toggle}
        title={collapsed ? '展开侧边栏' : '折叠侧边栏'}
      >
        {collapsed ? '»' : '«'}
      </button>
    </aside>
  )
}