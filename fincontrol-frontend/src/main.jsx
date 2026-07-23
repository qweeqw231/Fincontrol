import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import { ErrorBoundary } from './components/common/ErrorBoundary.jsx'
import './styles/variables.css'
import './styles/global.css'
import './styles/cumulative-return.css'
import './styles/data-page.css'  // 1b.3.11: DataPage 页面级样式（修复 4 张图占全屏、按钮消失、21 世纪初风格）

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>,
)
