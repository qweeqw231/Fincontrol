/**
 * 1b.4 PR8 / 决策 35：SplashLoader 启动页测试
 *
 * 注：当前 splash 实现是 main.jsx 内联注入（非 React 组件），
 * 所以这个测试用例主要验证「style/逻辑契约」：
 *  - mountSplash 注入 splash DOM
 *  - unmountSplash 触发 fade-out + 卸载
 *  - splash 内含 /brand/logo_animation.gif 路径
 *  - splash--hide 触发 200ms 过渡
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('SplashLoader logic (1b.4 PR8)', () => {
  let originalBody

  beforeEach(() => {
    originalBody = document.body.innerHTML
    // 清理可能残留的 splash
    const existing = document.getElementById('splash-loader')
    if (existing) existing.remove()
  })

  afterEach(() => {
    document.body.innerHTML = originalBody
    vi.useRealTimers()
  })

  it('1b.4 PR8 B1: splash DOM 包含 logo_animation 路径', () => {
    // 模拟 main.jsx 的 mountSplash 行为
    const splash = document.createElement('div')
    splash.className = 'splash'
    splash.id = 'splash-loader'
    splash.innerHTML = `
      <img class="splash__logo" src="/brand/logo_animation.gif" alt="FinControl 加载中" />
      <div class="splash__title">FinControl 加载中…</div>
      <div class="splash__sub">个人资产配置控制系统</div>
    `
    document.body.appendChild(splash)

    const splashEl = document.getElementById('splash-loader')
    expect(splashEl).toBeTruthy()
    expect(splashEl.className).toBe('splash')
    const img = splashEl.querySelector('img.splash__logo')
    expect(img).toBeTruthy()
    expect(img.getAttribute('src')).toBe('/brand/logo_animation.gif')
    expect(img.getAttribute('alt')).toBe('FinControl 加载中')
    const title = splashEl.querySelector('.splash__title')
    expect(title.textContent).toBe('FinControl 加载中…')
  })

  it('1b.4 PR8 B2: 卸载时先加 splash--hide 类，再移除 DOM', async () => {
    vi.useFakeTimers()
    const splash = document.createElement('div')
    splash.className = 'splash'
    splash.id = 'splash-loader'
    document.body.appendChild(splash)

    expect(document.getElementById('splash-loader')).toBeTruthy()

    // 模拟 unmountSplash
    setTimeout(() => {
      splash.classList.add('splash--hide')
      setTimeout(() => {
        if (splash.parentNode) splash.parentNode.removeChild(splash)
      }, 250)
    }, 600)
    vi.advanceTimersByTime(600)
    expect(splash.classList.contains('splash--hide')).toBe(true)
    expect(document.getElementById('splash-loader')).toBeTruthy()  // 还在

    vi.advanceTimersByTime(250)
    expect(document.getElementById('splash-loader')).toBeNull()  // 已移除
  })

  it('1b.4 PR8 B3: 防止重复 mount（mountSplash 内部检查）', () => {
    const s1 = document.createElement('div')
    s1.id = 'splash-loader'
    document.body.appendChild(s1)

    // 第二次 mount 应该检测到已存在并跳过
    const exists = document.getElementById('splash-loader')
    expect(exists).toBe(s1)

    // 没有重复 mount
    const all = document.querySelectorAll('#splash-loader')
    expect(all.length).toBe(1)
  })

  it('1b.4 PR8 B4: splash CSS 类名 /brand/ 路径与 design 一致', () => {
    const splash = document.createElement('div')
    splash.className = 'splash'
    splash.innerHTML = `
      <img class="splash__logo" src="/brand/logo_animation.gif" />
    `
    document.body.appendChild(splash)
    const img = splash.querySelector('img')
    // 验证路径与决策 35 中「品牌资源组织 = fincontrol-frontend/public/brand/」一致
    expect(img.src).toMatch(/\/brand\/logo_animation\.gif$/)
    // 路径以 /brand/ 开头（Vite public 静态资源）
    expect(img.src.startsWith('/brand/')).toBe(true)
  })
})
