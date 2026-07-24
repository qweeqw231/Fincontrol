/**
 * 1b.4 PR4a · 修复 HOME-009：formatYuan(n, { withSymbol: true }) thin space
 *
 * 覆盖场景：
 * - 默认行为（不变）
 * - withSymbol=true → ¥ + U+2009 thin space
 * - null / undefined / NaN 边界
 * - 0 / 负数 边界
 */
import { describe, it, expect } from 'vitest'
import { formatYuan } from '../../utils/formatters.js'

describe('formatYuan (1b.4 PR4a · HOME-009)', () => {
  it('默认行为：数字 → 千分位 + 2 位小数', () => {
    expect(formatYuan(1234.5)).toBe('1,234.50')
    expect(formatYuan(0)).toBe('0.00')
    expect(formatYuan(1234567.89)).toBe('1,234,567.89')
  })

  it('withSymbol=true：数字 → ¥ + U+2009 thin space + 千分位 + 2 位小数', () => {
    // U+2009 = Thin Space
    const thin = '\u2009'
    expect(formatYuan(1234.5, { withSymbol: true })).toBe(`¥${thin}1,234.50`)
    expect(formatYuan(0, { withSymbol: true })).toBe(`¥${thin}0.00`)
    expect(formatYuan(1234567.89, { withSymbol: true })).toBe(`¥${thin}1,234,567.89`)
  })

  it('withSymbol=true：负数 → ¥ + U+2009 + 千分位（符号内嵌在 Number.toLocaleString）', () => {
    const thin = '\u2009'
    // zh-CN 本地化对负数返回 "-1,234.50"
    expect(formatYuan(-1234.5, { withSymbol: true })).toBe(`¥${thin}-1,234.50`)
  })

  it('null / undefined → "—"（不论 withSymbol）', () => {
    expect(formatYuan(null)).toBe('—')
    expect(formatYuan(undefined)).toBe('—')
    expect(formatYuan(null, { withSymbol: true })).toBe('—')
    expect(formatYuan(undefined, { withSymbol: true })).toBe('—')
  })

  it('NaN → "—"（不论 withSymbol）', () => {
    expect(formatYuan(NaN)).toBe('—')
    expect(formatYuan(NaN, { withSymbol: true })).toBe('—')
  })

  it('字符串数字 → 自动转 Number', () => {
    expect(formatYuan('1234.5')).toBe('1,234.50')
    expect(formatYuan('1234.5', { withSymbol: true })).toBe(`¥\u20091,234.50`)
  })
})