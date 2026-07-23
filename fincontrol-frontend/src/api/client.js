import axios from 'axios'

/**
 * Axios 实例 + 拦截器
 *
 * - baseURL = '/api'（相对路径，通过 Vite 代理转发到 :8080，避免 dev 期跨域）
 * - 请求拦截器：自动注入 X-User-Id: 1（MVP 单用户场景，acceptance-criteria §1b.4）
 * - 响应拦截器：统一处理 {code, data, message} 业务响应结构（api-contract §1.3）
 */

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 120000,  // 1b.3 P6 决策 28：临时从 60s 延长到 120s（AI vision 单图/批处理易超时）
  headers: { 'Content-Type': 'application/json; charset=UTF-8' },
})

// 请求拦截器：自动注入 X-User-Id
apiClient.interceptors.request.use(
  (config) => {
    if (!config.headers['X-User-Id']) {
      config.headers['X-User-Id'] = '1'
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 {code, data, message} 结构
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      // 业务错误：抛出统一错误对象
      return Promise.reject({
        code: body.code,
        message: body.message,
        raw: body,
      })
    }
    return body
  },
  (error) => {
    // 网络错误 / HTTP 错误
    const status = error.response?.status
    return Promise.reject({
      code: status ? status * 100 : 0,
      message: error.message || '网络错误',
      raw: error,
    })
  },
)

export default apiClient
