-- 1b4pr6b 决策 33 D7 · 消失-重现机制 (R5)
-- 给 fund_category_map 表加 2 个字段：last_seen_snapshot_date + first_missing_snapshot_date
-- 用途：追踪基金的"消失-重现"状态，让用户在 preview modal 看到「您可能于 t1 之前清仓」的特别提示
--
-- 执行：
--   mysql -h127.0.0.1 -P3306 -uroot -proot fincontrol < scripts/1b/03-fund-category-map-add-fields.sql
-- 或通过 docker：
--   docker exec -i fincontrol-mysql mysql -uroot -proot fincontrol < scripts/1b/03-fund-category-map-add-fields.sql

USE fincontrol;

-- 加字段（如果不存在）
-- MySQL 8.0 不支持 IF NOT EXISTS on ADD COLUMN，但可用 INFORMATION_SCHEMA 检查
-- 这里用 try-catch 友好的方式：直接尝试加，重复执行报错可忽略

ALTER TABLE fund_category_map
    ADD COLUMN last_seen_snapshot_date DATE NULL COMMENT '上次有该基金的 confirm snapshot_date（决策 33 D7 · 锚点计算基于此字段 MAX）',
    ADD COLUMN first_missing_snapshot_date DATE NULL COMMENT '第一次发现该基金缺失的 confirm snapshot_date（消失-重现标记；reappearing 时被清空）';

-- 加索引（加速锚点计算）
CREATE INDEX idx_user_last_seen ON fund_category_map (user_id, last_seen_snapshot_date);
CREATE INDEX idx_user_first_missing ON fund_category_map (user_id, first_missing_snapshot_date);

-- 数据回填（如果表里有 user_correct 记录但 last_seen_snapshot_date 为 NULL）：
-- 保守策略：从 asset_raw 的最新 is_latest=true 记录中找 snapshot_date
-- 一次性 SQL（仅用于历史数据迁移，新 confirm 流程会自动写）：
UPDATE fund_category_map fcm
JOIN (
    SELECT fund_name, MAX(snapshot_date) AS last_seen
    FROM asset_raw
    WHERE user_id = 1 AND is_latest = true
    GROUP BY fund_name
) ar ON fcm.fund_name = ar.fund_name
SET fcm.last_seen_snapshot_date = ar.last_seen
WHERE fcm.user_id = 1 AND fcm.last_seen_snapshot_date IS NULL;

-- 验证迁移结果
SELECT 'fund_category_map 加字段后结构' AS step;
DESCRIBE fund_category_map;

SELECT '锚点计算测试' AS step;
SELECT user_id, COUNT(*) AS total,
       SUM(CASE WHEN last_seen_snapshot_date IS NOT NULL THEN 1 ELSE 0 END) AS has_last_seen,
       SUM(CASE WHEN first_missing_snapshot_date IS NOT NULL THEN 1 ELSE 0 END) AS has_first_missing,
       MAX(last_seen_snapshot_date) AS anchor_date
FROM fund_category_map
WHERE user_id = 1
GROUP BY user_id;

SELECT '✅ R5 schema 迁移完成' AS done;