-- ============================================
-- Phase 1b.3 迁移 00: snapshot_meta 表
-- 决策 27: is_latest 双层语义 + 跨日期 is_current
-- 配合 1b.3.1 commit
-- 2026-07-22
-- ============================================
-- 设计：每 (user_id, snapshot_date) 唯一一行
-- - is_latest: per-date 标记（与 asset_raw.is_latest 语义一致）
-- - is_current: 跨日期当前快照（每 user 最多 1 行 true）
-- - confirmed_at: confirm 时戳
-- - created_at / updated_at: 系统维护
-- 初始化方式：手动 mysql 执行（按 1b.3 工作计划）
-- ============================================

-- 表不存在才创建
CREATE TABLE IF NOT EXISTS snapshot_meta (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  snapshot_date   DATE NOT NULL,
  is_latest       BOOLEAN NOT NULL DEFAULT FALSE,
  is_current      BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_at    DATETIME NOT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, snapshot_date),
  KEY idx_user_current (user_id, is_current),
  KEY idx_user_latest (user_id, is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='决策 27：跨日期 is_current 快照元数据（per-date is_latest + current 切换）';

-- 不插入任何历史数据（用户要求：必须通过截图解析 + confirm 流程自动写入）
-- 1b.3.1 完成后，用户跑 4 张图 → parseBatchSingle → confirm → 自动生成 1 行

-- 验证
SELECT COUNT(*) AS snapshot_meta_count FROM snapshot_meta;
