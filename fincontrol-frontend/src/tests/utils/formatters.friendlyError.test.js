/**
 * Phase 1b.4 PR1 测试：friendlyError 工具（修复 GLOBAL-007）
 */

import { describe, it, expect } from 'vitest'
import { friendlyError } from '../../utils/formatters.js'

describe('friendlyError', () => {
  it('PR1-101: null / undefined → "未知错误"', () => {
    expect(friendlyError(null)).toBe('未知错误')
    expect(friendlyError(undefined)).toBe('未知错误')
  })

  it('PR1-102: string → 直接返回', () => {
    expect(friendlyError('出错了')).toBe('出错了')
  })

  it('PR1-103: axios 网络异常（code 0）→ "网络异常，请检查后端服务"', () => {
    expect(friendlyError({ code: 0, message: 'Network Error' })).toBe('网络异常，请检查后端服务')
  })

  it('PR1-104: axios 业务码 4xx → "请求参数错误"', () => {
    expect(friendlyError({ code: 400, message: 'xxx' })).toBe('请求参数错误')
    expect(friendlyError({ code: 404, message: 'xxx' })).toBe('请求参数错误')
    expect(friendlyError({ code: 499, message: 'xxx' })).toBe('请求参数错误')
  })

  it('PR1-105: axios 业务码 5xx → "服务器错误，请稍后重试"', () => {
    expect(friendlyError({ code: 500, message: 'xxx' })).toBe('服务器错误，请稍后重试')
    expect(friendlyError({ code: 503, message: 'xxx' })).toBe('服务器错误，请稍后重试')
  })

  it('PR1-106: axios stack 长字符串 → 通用文案（不暴露 stack）', () => {
    const long = 'Error: at /very/long/path/file.js:123:45\n at /another/long/path/file.js:678:90\n' + 'x'.repeat(200)
    expect(friendlyError({ message: long })).toBe('加载失败，请稍后重试')
  })

  it('PR1-107: 短错误消息 → 透传（业务友好提示）', () => {
    expect(friendlyError({ message: '余额不足' })).toBe('余额不足')
    expect(friendlyError({ message: '快照日期不能为空' })).toBe('快照日期不能为空')
  })

  it('修复: 拦截器映射码 40000（HTTP 400）不再误报"服务器错误"', () => {
    // 无后端业务文案、带 axios 默认文案 → 分类兜底
    expect(friendlyError({
      code: 40000, status: 400, message: 'Request failed with status code 400',
    })).toBe('请求参数错误')
    expect(friendlyError({
      code: 40400, status: 404, message: 'Request failed with status code 404',
    })).toBe('请求参数错误')
  })

  it('修复: 拦截器映射码 50000（HTTP 500）→ 服务器错误', () => {
    expect(friendlyError({
      code: 50000, status: 500, message: 'Request failed with status code 500',
    })).toBe('服务器错误，请稍后重试')
  })

  it('修复: 带 status 的拦截器错误优先透传后端短文案', () => {
    expect(friendlyError({
      code: 40000, status: 400, message: '该日期快照已存在，请先删除',
    })).toBe('该日期快照已存在，请先删除')
    // Spring 默认英文 error 文案无信息量，不透传，走分类兜底
    expect(friendlyError({
      code: 50000, status: 500, message: 'Internal Server Error',
    })).toBe('服务器错误，请稍后重试')
  })
})