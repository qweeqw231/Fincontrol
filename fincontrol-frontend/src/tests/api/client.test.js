import { describe, it, expect, vi } from 'vitest'
import apiClient from '../../api/client.js'

// 用 axios 拦截请求的方式，验证请求头 / 响应解包
describe('apiClient 拦截器', () => {
  it('请求拦截器自动注入 X-User-Id: 1', () => {
    // 直接调用 request 拦截器，模拟 axios 行为
    const interceptor = apiClient.interceptors.request.handlers[0]
    expect(interceptor).toBeDefined()

    const config = { headers: {} }
    const result = interceptor.fulfilled(config)
    expect(result.headers['X-User-Id']).toBe('1')
  })

  it('已有 X-User-Id 不覆盖', () => {
    const interceptor = apiClient.interceptors.request.handlers[0]
    const config = { headers: { 'X-User-Id': '99' } }
    const result = interceptor.fulfilled(config)
    expect(result.headers['X-User-Id']).toBe('99')
  })

  it('响应拦截器：code=0 时返回 data 字段', () => {
    const interceptor = apiClient.interceptors.response.handlers[0]
    const response = { data: { code: 0, data: { foo: 'bar' }, message: 'ok' } }
    const result = interceptor.fulfilled(response)
    expect(result).toEqual({ foo: 'bar' })
  })

  it('响应拦截器：code != 0 时 reject 业务错误', async () => {
    const interceptor = apiClient.interceptors.response.handlers[0]
    const response = { data: { code: 1001, data: null, message: '参数错误' } }
    await expect(
      Promise.resolve(interceptor.fulfilled(response))
    ).rejects.toEqual({
      code: 1001,
      message: '参数错误',
      raw: response.data,
    })
  })
})