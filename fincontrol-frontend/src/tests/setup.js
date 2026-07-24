// Vitest 全局 setup
// 用于 jsdom 环境 + cleanup
// 2026-07-24 PR2.envfix：注册 jest-dom matchers（修 StateShell.test.jsx toBeInTheDocument 缺失）
//                   + URL.createObjectURL / revokeObjectURL polyfill（修 blob.test.js jsdom 缺失）
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom 不实现 URL.createObjectURL / URL.revokeObjectURL（PR1 GLOBAL-015 测试需要）
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mock-' + Math.random().toString(36).slice(2)
}
if (typeof URL.revokeObjectURL !== 'function') {
  URL.revokeObjectURL = () => {}
}

afterEach(() => {
  cleanup()
})
