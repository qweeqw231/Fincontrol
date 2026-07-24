/**
 * 1b.4 PR4a · 修复 GLOBAL-010：路由切换同步 document.title
 *
 * 覆盖场景：
 * - `/` → 首页 · FinControl
 * - `/data` → 数据管理 · FinControl
 * - 已知路由（/config / correction / ai）→ 各自标题
 * - 未知路径 → 'FinControl' fallback（实际是 Navigate to '/'）
 *
 * 实现说明：导入导出的 RouterShell 而非 App，
 * 避免在测试中嵌套 BrowserRouter + MemoryRouter。
 * 每个 case 单独 render（rerender 同一 MemoryRouter 会复用 location）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RouterShell } from '../App.jsx'

describe('App · document.title (1b.4 PR4a · GLOBAL-010)', () => {
  beforeEach(() => {
    document.title = ''
    cleanup()
  })

  it('"/" → "首页 · FinControl"', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('首页 · FinControl')
  })

  it('"/data" → "数据管理 · FinControl"', () => {
    render(
      <MemoryRouter initialEntries={['/data']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('数据管理 · FinControl')
  })

  it('"/config" → "资产配置 · FinControl"', () => {
    render(
      <MemoryRouter initialEntries={['/config']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('资产配置 · FinControl')
  })

  it('"/correction" → "纠错页 · FinControl"', () => {
    render(
      <MemoryRouter initialEntries={['/correction']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('纠错页 · FinControl')
  })

  it('"/ai" → "AI 顾问 · FinControl"', () => {
    render(
      <MemoryRouter initialEntries={['/ai']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('AI 顾问 · FinControl')
  })

  it('未知路径 → "首页 · FinControl"（Navigate 到 / 后 useEffect 触发）', () => {
    render(
      <MemoryRouter initialEntries={['/unknown-path-xyz']}>
        <RouterShell />
      </MemoryRouter>
    )
    expect(document.title).toBe('首页 · FinControl')
  })
})