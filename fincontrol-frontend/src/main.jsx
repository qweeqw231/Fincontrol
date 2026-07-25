import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import { ErrorBoundary } from './components/common/ErrorBoundary.jsx'
import './styles/variables.css'
import './styles/global.css'
import './styles/cumulative-return.css'
import './styles/data-page.css'  // 1b.3.11: DataPage 页面级样式（修复 4 张图占全屏、按钮消失、21 世纪初风格）
import './styles/splash.css'        // 1b.4 PR8: 启动页（白底）样式
import './styles/home-page.css'     // 1b.4 PR8: HomePage 关闭服务按钮 + toast 样式

// 1b.4 PR8: 启动页（白底） — 纯 HTML 注入，不依赖 React mount 时间，确保 dev server 启动后第一时间显示
function mountSplash() {
  if (document.getElementById('splash-loader')) return
  const splash = document.createElement('div')
  splash.className = 'splash'
  splash.id = 'splash-loader'
  splash.setAttribute('data-testid', 'splash-loader')
  splash.innerHTML = `
    <img class="splash__logo" src="/brand/logo_animation.gif" alt="FinControl 加载中" />
    <div class="splash__title">FinControl 加载中…</div>
    <div class="splash__sub">个人资产配置控制系统</div>
  `
  document.body.appendChild(splash)
  return splash
}

function unmountSplash(splash, afterMs = 600) {
  if (!splash) return
  // React 接管后等一会儿再 fade-out（避免主页数据未加载就卸载）
  setTimeout(() => {
    splash.classList.add('splash--hide')
    setTimeout(() => {
      if (splash.parentNode) splash.parentNode.removeChild(splash)
    }, 250)
  }, afterMs)
}

const splash = mountSplash()

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>,
)

// 渲染后卸载 splash（即使 React 渲染完成才执行也不会卡住）
unmountSplash(splash)
