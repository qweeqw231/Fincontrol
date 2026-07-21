import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Sidebar from './components/layout/Sidebar.jsx'
import HomePage from './pages/HomePage.jsx'
import DataPage from './pages/DataPage.jsx'
import AIPage from './pages/AIPage.jsx'
import ConfigPage from './pages/ConfigPage.jsx'
import CorrectionPage from './pages/CorrectionPage.jsx'
import NAVPage from './pages/NAVPage.jsx'
import RatioPage from './pages/RatioPage.jsx'
import QuarterlyPage from './pages/QuarterlyPage.jsx'

function App() {
  return (
    <BrowserRouter>
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
    </BrowserRouter>
  )
}

export default App