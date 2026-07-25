/**
 * FinControl API endpoint 路径常量
 * 完整定义见 docs/phase-0/api-contract.md
 *
 * 同时支持两种 import 方式（兼容历史代码）：
 *   - named import: import { ASSET_BALANCE } from '../api/endpoints'
 *   - namespace:    import { ENDPOINTS } from '../api/endpoints'  或  endpoints.ASSET_BALANCE
 */

// === Namespace 风格（推荐，IDE 自动补全 + 集中管理）===
export const ENDPOINTS = {
  // 截图（2.x）
  SCREENSHOT_UPLOAD: '/screenshot/upload',
  SCREENSHOT_PARSE: '/screenshot/parse',
  SCREENSHOT_REPARSE: '/screenshot/reparse',
  SCREENSHOT_PARSE_BATCH: '/screenshot/parse-batch',

  // 快照（3.x / 4.x）
  SNAPSHOT_LATEST: '/snapshot/latest',
  SNAPSHOT_LATEST_DETAIL: '/snapshot/latest/detail',
  SNAPSHOT_BY_DATE: (date) => `/snapshot/${date}`,
  SNAPSHOT_HISTORY: '/snapshot/history',
  SNAPSHOT_CONFIRM: '/snapshot/confirm',
  SNAPSHOT_CONFIRM_DELETE: (id) => `/snapshot/confirm/${id}`,
  // 1b.3.3 决策 27：set-current + meta list
  SNAPSHOT_SET_CURRENT: '/snapshot/set-current',
  SNAPSHOT_META_LIST: '/snapshot/meta-list',

  // 月度校正（5.x，Phase 2）
  CORRECTION_DEFAULTS: '/correction/defaults',
  CORRECTION_MONTHLY_CALCULATE: '/correction/monthly/calculate',
  CORRECTION_MONTHLY_RECALCULATE: '/correction/monthly/recalculate',
  CORRECTION_MONTHLY_CONFIRM: '/correction/monthly/confirm',

  // 资产配置（6.x）
  CONFIG: '/config',

  // 基金大类映射（7.x）
  CATEGORY_MAP_MATCH: '/category-map/match',
  CATEGORY_MAP_UPDATE: '/category-map/update',
  CATEGORY_MAP_DELETE: (userId, fundName) =>
    `/category-map/${userId}/${encodeURIComponent(fundName)}`,
  CATEGORY_MAP_RESET: '/category-map/reset',
  CATEGORY_MAP_STALE: '/category-map/stale',

  // 大类主数据（8.x）
  CATEGORY_MASTER: '/category-master',
  CATEGORY_MASTER_BY_ID: (id) => `/category-master/${id}`,

  // 对话 / AI 顾问（9.x）
  CHAT_SEND: '/chat/send',
  CONVERSATIONS: '/conversations',
  CONVERSATION_BY_ID: (id) => `/conversations/${id}`,

  // 首页辅助（10.x）
  ASSET_BALANCE: '/asset/balance',
  ASSET_OPERATIONS_RECENT: '/asset/operations/recent',
  ASSET_CUMULATIVE_RETURN: '/asset/cumulative-return',

  // 解析日志（11.x）
  PARSE_LOGS: '/parse-logs',

  // PR3plus 决策 30/31：全局配置（settings 表）
  SETTINGS_MAX_AGE_GET: (userId) => `/settings/${userId}/max-snapshot-age-days`,
  SETTINGS_MAX_AGE_PUT: (userId) => `/settings/${userId}/max-snapshot-age-days`,

  // 1b.4 PR8：系统管理（关闭服务，决策 35）
  SYSTEM_SHUTDOWN: '/system/shutdown',
}

// === Named export（兼容 store 文件的 named import）===
// 函数型端点（如 SNAPSHOT_BY_DATE）也展开为 named 函数
export const SCREENSHOT_UPLOAD = ENDPOINTS.SCREENSHOT_UPLOAD
export const SCREENSHOT_PARSE = ENDPOINTS.SCREENSHOT_PARSE
export const SCREENSHOT_REPARSE = ENDPOINTS.SCREENSHOT_REPARSE
export const SCREENSHOT_PARSE_BATCH = ENDPOINTS.SCREENSHOT_PARSE_BATCH

export const SNAPSHOT_LATEST = ENDPOINTS.SNAPSHOT_LATEST
export const SNAPSHOT_LATEST_DETAIL = ENDPOINTS.SNAPSHOT_LATEST_DETAIL
export const SNAPSHOT_BY_DATE = ENDPOINTS.SNAPSHOT_BY_DATE
export const SNAPSHOT_HISTORY = ENDPOINTS.SNAPSHOT_HISTORY
export const SNAPSHOT_CONFIRM = ENDPOINTS.SNAPSHOT_CONFIRM
export const SNAPSHOT_CONFIRM_DELETE = ENDPOINTS.SNAPSHOT_CONFIRM_DELETE

export const CORRECTION_DEFAULTS = ENDPOINTS.CORRECTION_DEFAULTS
export const CORRECTION_MONTHLY_CALCULATE = ENDPOINTS.CORRECTION_MONTHLY_CALCULATE
export const CORRECTION_MONTHLY_RECALCULATE = ENDPOINTS.CORRECTION_MONTHLY_RECALCULATE
export const CORRECTION_MONTHLY_CONFIRM = ENDPOINTS.CORRECTION_MONTHLY_CONFIRM

export const CONFIG = ENDPOINTS.CONFIG

export const CATEGORY_MAP_MATCH = ENDPOINTS.CATEGORY_MAP_MATCH
export const CATEGORY_MAP_UPDATE = ENDPOINTS.CATEGORY_MAP_UPDATE
export const CATEGORY_MAP_DELETE = ENDPOINTS.CATEGORY_MAP_DELETE
export const CATEGORY_MAP_RESET = ENDPOINTS.CATEGORY_MAP_RESET
export const CATEGORY_MAP_STALE = ENDPOINTS.CATEGORY_MAP_STALE

export const CATEGORY_MASTER = ENDPOINTS.CATEGORY_MASTER
export const CATEGORY_MASTER_BY_ID = ENDPOINTS.CATEGORY_MASTER_BY_ID

export const CHAT_SEND = ENDPOINTS.CHAT_SEND
export const CONVERSATIONS = ENDPOINTS.CONVERSATIONS
export const CONVERSATION_BY_ID = ENDPOINTS.CONVERSATION_BY_ID

export const ASSET_BALANCE = ENDPOINTS.ASSET_BALANCE
export const ASSET_OPERATIONS_RECENT = ENDPOINTS.ASSET_OPERATIONS_RECENT
export const ASSET_CUMULATIVE_RETURN = ENDPOINTS.ASSET_CUMULATIVE_RETURN

export const PARSE_LOGS = ENDPOINTS.PARSE_LOGS

export const SETTINGS_MAX_AGE_GET = ENDPOINTS.SETTINGS_MAX_AGE_GET
export const SETTINGS_MAX_AGE_PUT = ENDPOINTS.SETTINGS_MAX_AGE_PUT

export const SYSTEM_SHUTDOWN = ENDPOINTS.SYSTEM_SHUTDOWN

export default ENDPOINTS
