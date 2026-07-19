-- ============================================
-- 1a.10 MySQL 8.0.46 幂等增量迁移
-- ============================================
-- 与 docs/phase-0/db-schema.sql 顶层 CREATE 严格解耦。
-- 任何带 IF NOT EXISTS 的 MySQL 8.0.46 不支持语法都已替换为
-- information_schema 守卫 + PREPARE/EXECUTE，可反复执行。
-- ============================================

SET NAMES utf8mb4;
SET @schema := DATABASE();

-- 1) fund_category_map.last_seen_at（1a.8.8）
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'fund_category_map'
                 AND COLUMN_NAME = 'last_seen_at'),
        'SELECT 1',
        'ALTER TABLE fund_category_map ADD COLUMN last_seen_at DATETIME NULL COMMENT "1a.8.8：最近一次出现在截图中的时间"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) idx_user_last_seen
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'fund_category_map'
                 AND INDEX_NAME = 'idx_user_last_seen'),
        'SELECT 1',
        'ALTER TABLE fund_category_map ADD INDEX idx_user_last_seen (user_id, last_seen_at)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) asset_raw.holding_profit / cumulative_profit 改为可空
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'asset_raw'
                 AND COLUMN_NAME = 'holding_profit'
                 AND IS_NULLABLE = 'YES'),
        'SELECT 1',
        'ALTER TABLE asset_raw MODIFY COLUMN holding_profit DECIMAL(12,2) NULL COMMENT "1a.8.7 持有收益（余额类允许 NULL）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'asset_raw'
                 AND COLUMN_NAME = 'cumulative_profit'
                 AND IS_NULLABLE = 'YES'),
        'SELECT 1',
        'ALTER TABLE asset_raw MODIFY COLUMN cumulative_profit DECIMAL(12,2) NULL COMMENT "1a.8.7 累计收益（允许 NULL）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) category_master（1a.10 P3）
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'category_master'),
        'SELECT 1',
        'CREATE TABLE category_master (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            name_canonical VARCHAR(50) NOT NULL,
            aliases JSON NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_category_master_canonical (name_canonical)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="1a.10 七大类主数据"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) v2.7 prompt（追加 insert 走 v2.6 之后更高 id，不覆盖 v2.6）
INSERT INTO prompt_versions (prompt_name, version, prompt_content, change_reason)
SELECT 'screenshot_parser', 'v2.7', '识别[日期]（支付宝）资产明细...', '1a.10 v2.7 占位'
WHERE NOT EXISTS (SELECT 1 FROM prompt_versions
                  WHERE prompt_name='screenshot_parser' AND version='v2.7');
