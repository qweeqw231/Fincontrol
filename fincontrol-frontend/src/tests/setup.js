// Vitest 全局 setup
// 用于 jsdom 环境 + cleanup
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})