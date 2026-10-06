// @vitest-environment jsdom
/**
 * Sidebar · 2a 校正台入口启用回归测试（决策 37）。
 *
 * 背景：2a 交付校正台时曾漏改本配置，导致页面已实现但侧边栏灰显点不进去。
 * 本测试守护：/correction 与 /quarterly 必须是可点击 NavLink；
 * /config（资产配置，Phase 2 未启动）保持灰显占位。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Sidebar from '../../../components/layout/Sidebar.jsx'

describe('Sidebar · 校正台入口（2a）', () => {
  beforeEach(() => {
    cleanup()
  })

  it('月度校正 / 季度操作为可点击链接（href 正确）', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    )
    const correction = screen.getByText('月度校正').closest('a')
    expect(correction).toBeTruthy()
    expect(correction.getAttribute('href')).toBe('/correction')

    const quarterly = screen.getByText('季度操作').closest('a')
    expect(quarterly).toBeTruthy()
    expect(quarterly.getAttribute('href')).toBe('/quarterly')
  })

  it('资产配置仍为灰显占位（无链接 + is-disabled）', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    )
    const configLabel = screen.getByText('资产配置')
    expect(configLabel.closest('a')).toBeNull()
    expect(configLabel.closest('.is-disabled')).toBeTruthy()
  })

  it('其余已启用页面保持可点击', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    )
    for (const [label, href] of [
      ['首页', '/'],
      ['数据管理', '/data'],
      ['净值曲线', '/nav'],
      ['比例演化', '/ratio'],
      ['AI 顾问', '/ai'],
    ]) {
      const link = screen.getByText(label).closest('a')
      expect(link, `${label} 应为可点击链接`).toBeTruthy()
      expect(link.getAttribute('href')).toBe(href)
    }
  })
})