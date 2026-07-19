-- ============================================
-- 1a.10 MySQL 8.0.46 整合迁移脚本（可重复执行）
-- ============================================
-- 1. 合并 `1a10-migration-mysql.sql` + `1a10-real-mysql-migration.sql`
-- 2. 加 `last_seen_at` 兼容 + 港股 alias 归一化 + 余额宝 holding=null
-- 3. 加 v2.7.1 prompt 入库（2601 字节，id 自动 >= 11）
-- 4. 所有 DDL 用 information_schema + PREPARE/EXECUTE 守卫，可重复
-- ============================================

USE fincontrol;
SET NAMES utf8mb4;
SET @schema := DATABASE();

-- ============================================
-- 1) fund_category_map.last_seen_at（1a.8.8）
-- ============================================
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

-- ============================================
-- 2) idx_user_last_seen
-- ============================================
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

-- ============================================
-- 3) asset_raw.holding_profit / cumulative_profit 改为可空
-- ============================================
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

-- 1a.10 P2：profit 兼容列与 holding_profit 同步语义（余额类 holding=NULL 时 profit 也 NULL）
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'asset_raw'
                 AND COLUMN_NAME = 'profit'
                 AND IS_NULLABLE = 'YES'),
        'SELECT 1',
        'ALTER TABLE asset_raw MODIFY COLUMN profit DECIMAL(12,2) NULL COMMENT "1a.8.7 兼容列，与 holding_profit 同步；余额类允许 NULL"'
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

-- ============================================
-- 4) asset_raw + asset_snapshot.total_asset_source（1a.9）
-- ============================================
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'asset_raw'
                 AND COLUMN_NAME = 'total_asset_source'),
        'SELECT 1',
        'ALTER TABLE asset_raw ADD COLUMN total_asset_source VARCHAR(20) NOT NULL DEFAULT "top" COMMENT "1a.9：该行 totalAsset 来源（top/visible_sum）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'asset_snapshot'
                 AND COLUMN_NAME = 'total_asset_source'),
        'SELECT 1',
        'ALTER TABLE asset_snapshot ADD COLUMN total_asset_source VARCHAR(20) NOT NULL DEFAULT "top" COMMENT "1a.9：该快照 totalAsset 来源"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 5) category_master（1a.10 P3）
-- ============================================
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

-- ============================================
-- 6) 7 canonical seed（idempotent）
-- ============================================
INSERT INTO category_master (name_canonical, aliases) VALUES
    ('货币类', JSON_ARRAY('货币', '货基', '货币基金')),
    ('固收类', JSON_ARRAY('固收', '债券', '债券类', '纯债', '短债', '固收+', '中短债')),
    ('商品类', JSON_ARRAY('商品', '黄金', '大宗商品', '黄金ETF')),
    ('A股权益类', JSON_ARRAY('A股', '股票', '股票类', 'A股权益', '股票指数', '中证', '宽基', '沪深300', '中证500', '中证1000')),
    ('海外权益类', JSON_ARRAY('海外权益', 'QDII', '海外股票', '海外QDII', '纳斯达克', '标普', '海外')),
    ('港股大中华类', JSON_ARRAY('港股', '大中华', '港股QDII', '恒生', '港股/大中华类', '港股/大中华')),
    ('余额类', JSON_ARRAY('余额', '余额宝'))
ON DUPLICATE KEY UPDATE
    aliases = VALUES(aliases),
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================
-- 7) 港股 alias 归一化：把旧「港股/大中华类」→ canonical「港股大中华类」
-- ============================================
UPDATE fund_category_map
SET category = '港股大中华类'
WHERE category = '港股/大中华类';

-- ============================================
-- 8) v2.7.1 prompt 入库（idempotent）
-- ============================================
INSERT INTO prompt_versions (prompt_name, version, prompt_content, change_reason)
SELECT 'screenshot_parser', 'v2.7.1',
'识别[日期]（支付宝）资产明细。默认数据源为支付宝，SDK 阶段再考虑多个基金软件的适配。请返回完整的 6 大配置类 + 余额类 的资产明细表格和汇总表格，以及一段总结。表格使用 Markdown 格式。每只基金需包含基金名称、持仓金额（元）、持有收益（元）、累计收益（元）（1a.8.7 双字段），以及类别归属。同时返回结构化 JSON 供后端解析。日期格式必须为 YYYY-MM-DD。

【重要 - 1a.10 类别口径修正】
本系统采用「6 大配置类 + 余额类」结构。
  - 6 大配置类（参与 user_config.target_ratios 配置和资产比例计算）：
    货币类（aliases: 货币、货基、货币基金）
    固收类（aliases: 固收、债券、纯债、短债、固收+、中短债）
    商品类（aliases: 商品、黄金、大宗商品）
    A股权益类（aliases: A股、股票、中证、沪深300、中证500）
    海外权益类（aliases: QDII、纳斯达克、标普、海外股票）
    港股大中华类（aliases: 港股、恒生、大中华）
  - 余额类（**不参与**配比，仅作为货币等价物）：余额宝、活期存款等
  重要：只输出上述 7 个 canonical 名之一，禁止用「其他」「权益类」「保障类」等旧名。

【重要 - 结构化 JSON 严格使用】
{
  "snapshot_date": "YYYY-MM-DD",
  "total_asset": 浮点,
  "categories": [
    {
      "category_name": "货币类|固收类|商品类|A股权益类|海外权益类|港股大中华类|余额类",
      "category_total": 浮点,
      "funds": [
        {"fund_name": "...", "amount": 浮点, "holding_profit": 浮点或null, "cumulative_profit": 浮点或null}
      ]
    }
  ],
  "matchedFunds": ["基金1", "基金2", ...]
}

【1a.10 P0 - 端到端闭环】
- total_asset 字段 = 截图顶部"总资产"数字（无论第几页都应读到），不可见时输出 null，**禁止**把当前页基金加总作为 total_asset。
- 即使顶部不可见也必须输出完整 categories[].funds[]；缺失或截断的基金仍写入 categories[].funds，name + amount 必须存在，holding_profit / cumulative_profit 允许为 null（特别是余额类）。
- 只输出一份完整资产 JSON（放在 fenced code block ```json``` 内），不要附带解释性文字或思考过程。
- 任何局部示例 / schema 文档 JSON 视为参考，模型最终输出必须基于图片内容，并按 fund_name 去重。
- 标题行（仅显示名称但 amount 截断或缺失）**不算完整基金**，应忽略，不计入 total_asset。',
'1a.10 v2.7.1：术语修正为「6 大配置类 + 余额类」，余额类不参与配比；修复 P4 minimax 自相矛盾问题（六大类 vs 七大类）'
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_versions
    WHERE prompt_name = 'screenshot_parser' AND version = 'v2.7.1'
);

-- ============================================
-- 9) 验证
-- ============================================
SELECT 'last_seen_at' AS check_name,
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'fund_category_map'
                AND COLUMN_NAME = 'last_seen_at') AS ok
UNION ALL
SELECT 'idx_user_last_seen',
       EXISTS(SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'fund_category_map'
                AND INDEX_NAME = 'idx_user_last_seen')
UNION ALL
SELECT 'asset_raw.holding_profit nullable',
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'asset_raw'
                AND COLUMN_NAME = 'holding_profit'
                AND IS_NULLABLE = 'YES')
UNION ALL
SELECT 'asset_raw.cumulative_profit nullable',
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'asset_raw'
                AND COLUMN_NAME = 'cumulative_profit'
                AND IS_NULLABLE = 'YES')
UNION ALL
SELECT 'total_asset_source on asset_raw',
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'asset_raw'
                AND COLUMN_NAME = 'total_asset_source')
UNION ALL
SELECT 'total_asset_source on asset_snapshot',
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'asset_snapshot'
                AND COLUMN_NAME = 'total_asset_source')
UNION ALL
SELECT 'category_master table',
       EXISTS(SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'category_master')
UNION ALL
SELECT 'category_master 7 rows',
       (SELECT COUNT(*) FROM category_master WHERE is_active = TRUE)
UNION ALL
SELECT 'v2.7.1 prompt installed',
       EXISTS(SELECT 1 FROM prompt_versions
              WHERE prompt_name = 'screenshot_parser' AND version = 'v2.7.1')
UNION ALL
SELECT '港股 alias 归一化剩余 (应为 0)',
       (SELECT COUNT(*) FROM fund_category_map WHERE category = '港股/大中华类');
