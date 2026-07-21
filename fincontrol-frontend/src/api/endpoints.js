/**
 * FinControl API endpoint 路径常量
 * 完整定义见 docs/phase-0/api-contract.md
 */

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
}

export default ENDPOINTS