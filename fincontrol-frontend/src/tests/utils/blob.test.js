/**
 * Phase 1b.4 PR1 测试：blob URL 释放工具（修复 GLOBAL-015）
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { revokeAll, revokeOne } from '../../utils/blob.js'

describe('revokeAll', () => {
  beforeEach(() => {
    // 每个测试前 mock URL.revokeObjectURL
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  afterEach(() => {
    // 每个测试后清理 mock（关键：vi.restoreAllMocks 比 mockClear 更彻底）
    vi.restoreAllMocks()
  })

  it('PR1-201: 释放所有有 url 的项', () => {
    revokeAll([
      { url: 'blob:1' },
      { url: 'blob:2' },
      { name: 'no-url' },
      null,
      { url: null },
    ])
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:2')
  })

  it('PR1-202: 空数组 → 不调用 revokeObjectURL', () => {
    revokeAll([])
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  })

  it('PR1-203: URL.revokeObjectURL 抛错时不影响其他项', () => {
    URL.revokeObjectURL.mockImplementationOnce(() => { throw new Error('boom') })
    expect(() => revokeAll([{ url: 'blob:bad' }, { url: 'blob:good' }])).not.toThrow()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
  })

  it('PR1-203a: 非数组输入 → 不抛错', () => {
    expect(() => revokeAll(null)).not.toThrow()
    expect(() => revokeAll(undefined)).not.toThrow()
    expect(() => revokeAll('not array')).not.toThrow()
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  })
})

describe('revokeOne', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('PR1-204: 释放单个项', () => {
    revokeOne({ url: 'blob:1' })
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1')
  })

  it('PR1-205: 没有 url → 不调用', () => {
    revokeOne({ name: 'no-url' })
    revokeOne(null)
    revokeOne(undefined)
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  })

  it('PR1-206: URL.revokeObjectURL 抛错时不抛出', () => {
    URL.revokeObjectURL.mockImplementationOnce(() => { throw new Error('boom') })
    expect(() => revokeOne({ url: 'blob:bad' })).not.toThrow()
  })
})