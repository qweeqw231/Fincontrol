import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import Sidebar from './components/layout/Sidebar.jsx'
import HomePage from './pages/HomePage.jsx'
import DataPage from './pages/DataPage.jsx'
import AIPage from './pages/AIPage.jsx'
import ConfigPage from './pages/ConfigPage.jsx'
import CorrectionPage from './pages/CorrectionPage.jsx'
import NAVPage from './pages/NAVPage.jsx'
import RatioPage from './pages/RatioPage.jsx'
import QuarterlyPage from './pages/QuarterlyPage.jsx'

// 1b.4 PR4a · 修复 GLOBAL-010：路由切换同步 document.title
const TITLES = {
  '/':           '首页 · FinControl',
  '/data':       '数据管理 · FinControl',
  '/config':     '资产配置 · FinControl',
  '/correction': '纠错页 · FinControl',
  '/nav':        '净值 · FinControl',
  '/ratio':      '比例 · FinControl',
  '/quarterly':  '季度 · FinControl',
  '/ai':         'AI 顾问 · FinControl',
}

/**
 * 内层组件（必须在 Router 内才能用 useLocation）
 * 导出以便单元测试（避免 <Router> 嵌套）
 */
export function RouterShell() {
  const location = useLocation()
  useEffect(() => {
    document.title = TITLES[location.pathname] || 'FinControl'
  }, [location.pathname])

  return (
    <div className="app-layout">
      <Sidebar />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/data" element={<DataPage />} />
          <Route path="/ai" element={<AIPage />} />
          <Route path="/config" element={<ConfigPage />} />
          <Route path="/correction" element={<CorrectionPage />} />
          <Route path="/nav" element={<NAVPage />} />
          <Route path="/ratio" element={<RatioPage />} />
          <Route path="/quarterly" element={<QuarterlyPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <RouterShell />
    </BrowserRouter>
  )
}

export default App