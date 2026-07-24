// @vitest-environment jsdom
/**
 * Phase 1b.4 PR1 测试：StateShell 组件（修复 HOME-003）
 */

import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { StateShell } from '../../../components/home/StateShell.jsx'

describe('StateShell', () => {
  it('PR1-301: 渲染 icon / title / sub / action', () => {
    render(
      <MemoryRouter>
        <StateShell
          icon="📊"
          title="暂无资产快照"
          sub="上传 4 张支付宝基金截图，自动解析你的六大类配置"
          action={<button>立即上传</button>}
        />
      </MemoryRouter>
    )
    expect(screen.getByText('📊')).toBeInTheDocument()
    expect(screen.getByText('暂无资产快照')).toBeInTheDocument()
    expect(screen.getByText(/上传 4 张支付宝基金截图/)).toBeInTheDocument()
    expect(screen.getByText('立即上传')).toBeInTheDocument()
  })

  it('PR1-302: 不传 sub / action 也能正常渲染', () => {
    render(
      <MemoryRouter>
        <StateShell icon="⏳" title="加载中..." />
      </MemoryRouter>
    )
    expect(screen.getByText('⏳')).toBeInTheDocument()
    expect(screen.getByText('加载中...')).toBeInTheDocument()
  })

  it('PR1-303 (附加): 错误态 + 重试按钮', () => {
    const onRetry = () => {}
    render(
      <MemoryRouter>
        <StateShell
          icon="⚠️"
          title="加载失败"
          sub="网络异常，请检查后端服务"
          action={<button onClick={onRetry}>重试</button>}
        />
      </MemoryRouter>
    )
    expect(screen.getByText('⚠️')).toBeInTheDocument()
    expect(screen.getByText('加载失败')).toBeInTheDocument()
    expect(screen.getByText('网络异常，请检查后端服务')).toBeInTheDocument()
    expect(screen.getByText('重试')).toBeInTheDocument()
  })
})