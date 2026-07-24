/**
 * Blob URL 释放工具（Phase 1b.4 PR1 · 修复 GLOBAL-015）
 *
 * DataPage 通过 URL.createObjectURL 为上传图片生成预览 URL，
 * 但 JS 没有自动回收机制，每次上传/删除/离开页面时必须手动 revoke，
 * 否则 blob 会一直占用浏览器内存。
 */

/**
 * 释放单个预览项的 blob URL（PR1 修复 GLOBAL-015）
 * @param {{url?: string} | null | undefined} preview
 */
export function revokeOne(preview) {
  if (!preview || !preview.url) return
  try {
    URL.revokeObjectURL(preview.url)
  } catch (_) {
    // revokeObjectURL 在某些边界场景会抛错（如 URL 已释放或非法），
    // 这里静默吞掉，避免影响主流程。
  }
}

/**
 * 批量释放预览列表的 blob URL（PR1 修复 GLOBAL-015）
 * @param {Array<{url?: string}> | null | undefined} previews
 */
export function revokeAll(previews) {
  if (!Array.isArray(previews)) return
  previews.forEach(revokeOne)
}