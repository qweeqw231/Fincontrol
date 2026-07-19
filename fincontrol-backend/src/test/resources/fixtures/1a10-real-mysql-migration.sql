-- ============================================
-- 1a.10 MySQL 8.0.46 应用迁移
-- ============================================
-- 1. 仅供本机测试；应用前先 cat 核对 schema 信息
-- 2. 与 docs/phase-0/db-schema.sql 顶层 CREATE 解耦
-- 3. 通过 information_schema + PREPARE/EXECUTE 守卫，确保多次跑幂等
-- ============================================

USE fincontrol;
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

-- 5) 7 canonical 类别 seed（idempotent）
INSERT INTO category_master (name_canonical, aliases) VALUES
    ('货币类', JSON_ARRAY('货币','货基','货币基金')),
    ('固收类', JSON_ARRAY('固收','债券','债券类','纯债','短债','固收+','中短债')),
    ('商品类', JSON_ARRAY('商品','黄金','大宗商品','黄金ETF')),
    ('A股权益类', JSON_ARRAY('A股','股票','股票类','A股权益','股票指数','中证','宽基','沪深300','中证500','中证1000')),
    ('海外权益类', JSON_ARRAY('海外权益','QDII','海外股票','海外QDII','纳斯达克','标普','海外')),
    ('港股大中华类', JSON_ARRAY('港股','大中华','港股QDII','恒生','港股/大中华类','港股/大中华')),
    ('余额类', JSON_ARRAY('余额','余额宝'))
ON DUPLICATE KEY UPDATE
    aliases = VALUES(aliases),
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- 6) v2.7 prompt 入库（仅插入，保留 v2.6 以便回退）
INSERT INTO prompt_versions (prompt_name, version, prompt_content, change_reason)
SELECT 'screenshot_parser', 'v2.7',
'识别[日期]（支付宝）资产明细。默认数据源为支付宝，SDK 阶段再考虑多个基金软件的适配。请返回完整的六大类资产明细表格和六大类汇总表格，以及一段总结。表格使用 Markdown 格式。每只基金需包含基金名称、持仓金额（元）、持有收益（元）、累计收益（元）（1a.8.7 双字段），以及七大类归属（含"余额类"）。同时返回结构化 JSON 供后端解析。日期格式必须为 YYYY-MM-DD。

【1a.10 P0 - 端到端闭环】
- total_asset 字段 = 截图顶部"总资产"数字（无论第几页都应读到），不可见时输出 null，**禁止**把当前页基金加总作为 total_asset。
- 即使顶部不可见也必须输出完整 categories[].funds[]；缺失或截断的基金仍写入 categories[].funds，name + amount 必须存在，holding_profit / cumulative_profit 允许为 null（特别是余额类）。
- 只输出一份完整资产 JSON（放在 fenced code block ```json``` 内），不要附带解释性文字或思考过程。
- 任何局部示例 / schema 文档 JSON 视为参考，模型最终输出必须基于图片内容，并按 fund_name 去重。

【七大类 canonical 名】货币类 / 固收类 / 商品类 / A股权益类 / 海外权益类 / 港股大中华类 / 余额类。',
'1a.10 v2.7：单次多图合并 + 顶部不可见仍输出完整 fund + 严格 7 canonical'
WHERE NOT EXISTS (SELECT 1 FROM prompt_versions
                  WHERE prompt_name = 'screenshot_parser' AND version = 'v2.7');

-- 7) 验证结果（人工对照）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema
  AND ((TABLE_NAME = 'fund_category_map' AND COLUMN_NAME = 'last_seen_at')
       OR (TABLE_NAME = 'asset_raw' AND COLUMN_NAME IN ('holding_profit','cumulative_profit')));

SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = @schema
  AND TABLE_NAME = 'category_master';

SELECT id, name_canonical, JSON_LENGTH(aliases) AS alias_count, is_active
FROM category_master
ORDER BY id;

SELECT id, prompt_name, version, LENGTH(prompt_content) AS bytes
FROM prompt_versions
WHERE prompt_name = 'screenshot_parser' AND version IN ('v2.6','v2.7')
ORDER BY id;
