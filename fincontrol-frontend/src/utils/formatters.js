/**
 * 1b.3 补救：统一数据口径/格式化工具。
 * <p>注意：必须使用原始未取整金额做比例计算，前端展示才四舍五入。
 */
import { CATEGORY_COLORS } from './categoryColors.js';

const SCALE = 100;
const HUNDRED = 100;

export function getCategoryColor(name) {
  return CATEGORY_COLORS[name] || '#94a3b8';
}

/**
 * 1b.4 PR4a · 修复 HOME-009：¥ 符号 + thin space（U+2009）
 * - opts.withSymbol = true → 返回 "¥\u2009{body}"
 * - 默认行为不变（透传数字）
 */
export function formatYuan(n, opts = {}) {
  if (n == null || Number.isNaN(Number(n))) return '—';
  const body = Number(n).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  if (opts && opts.withSymbol) return `¥\u2009${body}`;
  return body;
}

export function formatSignedAmount(n) {
  if (n == null || Number.isNaN(Number(n))) return '—';
  const v = Number(n);
  const sign = v >= 0 ? '+' : '−';
  return sign + Math.abs(v).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function formatPercent(p) {
  if (p == null || Number.isNaN(Number(p))) return '—';
  const v = Number(p);
  const sign = v >= 0 ? '+' : '';
  return sign + v.toFixed(2) + '%';
}

export function formatSignedPercent(p) {
  if (p == null || Number.isNaN(Number(p))) return '—';
  const v = Number(p);
  return (v >= 0 ? '+' : '') + v.toFixed(2) + '%';
}

export function safeNumber(x, d = 0) {
  if (x == null) return d;
  const n = Number(x);
  return Number.isFinite(n) ? n : d;
}

/**
 * 1b.3 补救 R1：取大类中所有基金（funds.length），并按当前快照权威金额排序。
 */
export function getCategoryFundCount(category) {
  if (!category) return 0;
  return Array.isArray(category.funds) ? category.funds.length : 0;
}

export function getTotalFundCount(categories = []) {
  return categories
    .filter((c) => c.categoryName !== '余额类')
    .reduce((s, c) => s + getCategoryFundCount(c), 0);
}

export function getBalanceFundCount(categories = []) {
  const bal = categories.find((c) => c.categoryName === '余额类');
  return bal ? getCategoryFundCount(bal) : 0;
}

export function isBalanceCategory(name) {
  return name === '余额类';
}

/**
 * 1b.3 补救 R4：按未取整金额重算占比；返回 (actualRatio, targetRatio, deviation)，
 * 余额类全部返回 null。v6 基础 / 6 总额 = 0 时返回 (0, target, 0)。
 */
export function calcRatios(sixTotal, categoryTotal, targetRatio) {
  if (sixTotal == null || sixTotal <= 0) {
    return { actual: 0, target: safeNumber(targetRatio, 0), deviation: -safeNumber(targetRatio, 0) };
  }
  const actual = (Number(categoryTotal) / Number(sixTotal)) * HUNDRED;
  const t = safeNumber(targetRatio, 0);
  return { actual, target: t, deviation: actual - t };
}

export function sumSixTotal(categories = []) {
  return categories
    .filter((c) => !isBalanceCategory(c.categoryName))
    .reduce((s, c) => s + safeNumber(c.categoryTotal, 0), 0);
}

export function sumBalanceTotal(categories = []) {
  const bal = categories.find((c) => isBalanceCategory(c.categoryName));
  return bal ? safeNumber(bal.categoryTotal, 0) : 0;
}

/**
 * 1b.3 补救 R1：基金类内占比 + 小计 = 100%。
 * <p>显式 0 也返回 0.00；未知(null/undefined)返回 null。
 */
export function classInCategoryRatio(amount, categoryTotal) {
  if (amount == null || categoryTotal == null || categoryTotal === 0) return null;
  if (amount === 0) return 0;
  return (Number(amount) / Number(categoryTotal)) * HUNDRED;
}

export function formatRatioSafe(p) {
  if (p == null) return '—';
  return Number(p).toFixed(2) + '%';
}

/**
 * 把 axios error 转换为面向用户的友好文案（Phase 1b.4 PR1 · 修复 GLOBAL-007）
 *
 * - 避免暴露 axios stack / 业务码给最终用户
 * - 未知错误返回通用文案
 *
 * 兼容：
 *   - null / undefined         → "未知错误"
 *   - string                   → 直接返回
 *   - { code, message }        → 业务码映射（0 / 4xx / 5xx）
 *   - { message: long string } → 通用文案（避免暴露 stack）
 *   - { message: short string }→ 透传（业务友好提示）
 */
export function friendlyError(err) {
  if (err == null) return '未知错误'
  if (typeof err === 'string') return err

  // 业务错误：{ code, message }
  if (typeof err.code === 'number') {
    if (err.code === 0) return '网络异常，请检查后端服务'

    // 拦截器对 HTTP 错误使用 status * 100 的映射码（40000 / 50000…），
    // 旧实现直接拿映射码与裸 HTTP 码比较，导致 4xx 全部落进 >=500 分支误报"服务器错误"。
    // 同时兼容历史裸码（400/500）与业务码（1001 等）。
    const httpStatus = err.code >= 1000 ? Math.floor(err.code / 100) : err.code

    // 新拦截器 reject 的对象带 status：此时 err.message 已优先取后端 body 的业务文案，
    // 安全的短文案直接透传（如"该日期快照已存在"）；axios 默认文案 / Spring 默认英文
    // error 文案无信息量，继续走下面的分类兜底。
    const msg = err.message
    const axiosDefault = typeof msg === 'string' && /^Request failed with status code/.test(msg)
    const springDefault = typeof msg === 'string' && /^(Bad Request|Unauthorized|Forbidden|Not Found|Internal Server Error|Service Unavailable|Gateway Timeout)$/.test(msg)
    const safeShort = typeof msg === 'string' && msg.length < 80 && !msg.includes('\n')
    if (typeof err.status === 'number' && err.status > 0 && safeShort && !axiosDefault && !springDefault) {
      return msg
    }

    if (httpStatus >= 400 && httpStatus < 500) return '请求参数错误'
    if (httpStatus >= 500) return '服务器错误，请稍后重试'
    if (err.message) return err.message
    return '请求失败'
  }

  // axios 错误：err.message 可能含 stack
  if (
    err.message &&
    err.message.length < 80 &&
    !err.message.includes('\n')
  ) {
    return err.message
  }

  return '加载失败，请稍后重试'
}