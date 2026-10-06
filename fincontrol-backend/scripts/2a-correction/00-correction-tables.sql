-- ============================================
-- 2a 校正页数据模型迁移（MySQL 8.0，可重复执行）
-- ============================================
-- 1. operation_log 加 correction_mode（zoh_only/lqr_zoh/manual）
-- 2. 新建 correction_iteration / correction_asset_detail / correction_param 三张明细表
-- 3. 所有 DDL 用 information_schema + PREPARE/EXECUTE 守卫，可重复执行
-- 4. 末尾附验证查询（全部 ok=1 视为成功）
--
-- 用法：mysql -uroot -p fincontrol < 00-correction-tables.sql
-- 说明：与 docs/phase-0/db-schema.sql 的 7.1/7.2/7.3 及增量段同构。
-- ============================================

USE fincontrol;
SET NAMES utf8mb4;
SET @schema := DATABASE();

-- ============================================
-- 1) operation_log.correction_mode
-- ============================================
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'operation_log'
                 AND COLUMN_NAME = 'correction_mode'),
        'SELECT 1',
        'ALTER TABLE operation_log ADD COLUMN correction_mode VARCHAR(20) NULL COMMENT "2a：校正模式（zoh_only=纯低波ZOH / lqr_zoh=LQR-ZOH联合 / manual=手工战术；NULL=未分类历史行）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 2) correction_iteration（校正迭代轮次明细）
-- ============================================
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'correction_iteration'),
        'SELECT 1',
        'CREATE TABLE correction_iteration (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL DEFAULT 1,
            operation_log_id BIGINT NOT NULL,
            sort_order INT NOT NULL,
            alpha DECIMAL(6,4) NULL,
            e_high DECIMAL(12,2) NULL,
            delta_m DECIMAL(12,2) NULL,
            delta_b DECIMAL(12,2) NULL,
            zoh_triggered BOOLEAN NOT NULL DEFAULT FALSE,
            total_investment DECIMAL(12,2) NULL,
            over_limit DECIMAL(12,2) NULL,
            note VARCHAR(200) NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_ci_op (operation_log_id),
            INDEX idx_ci_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="2a：校正迭代/调节轮次明细（α 压缩与分布式调节）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 3) correction_asset_detail（校正逐资产明细）
-- ============================================
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'correction_asset_detail'),
        'SELECT 1',
        'CREATE TABLE correction_asset_detail (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL DEFAULT 1,
            operation_log_id BIGINT NOT NULL,
            sort_order INT NOT NULL,
            phase VARCHAR(20) NOT NULL,
            category VARCHAR(50) NOT NULL,
            amount DECIMAL(12,2) NULL,
            ratio_actual DECIMAL(6,2) NULL,
            ratio_target DECIMAL(6,2) NULL,
            deviation DECIMAL(6,2) NULL,
            delta_raw DECIMAL(12,2) NULL,
            delta_amount DECIMAL(12,2) NULL,
            note VARCHAR(200) NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_cad_op (operation_log_id),
            INDEX idx_cad_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="2a：校正逐资产明细（校正前/后 × 六大类与高波内部）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 4) correction_param（校正参数与指标 key-value）
-- ============================================
SET @ddl := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = @schema
                 AND TABLE_NAME = 'correction_param'),
        'SELECT 1',
        'CREATE TABLE correction_param (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL DEFAULT 1,
            operation_log_id BIGINT NOT NULL,
            param_key VARCHAR(50) NOT NULL,
            num_value DECIMAL(18,4) NULL,
            text_value VARCHAR(300) NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_cp_op_key (operation_log_id, param_key),
            INDEX idx_cp_user_key (user_id, param_key)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="2a：校正参数与指标（key-value）"'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 5) 验证（全部 ok=1）
-- ============================================
SELECT 'operation_log.correction_mode' AS check_name,
       EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'operation_log'
                AND COLUMN_NAME = 'correction_mode') AS ok
UNION ALL
SELECT 'correction_iteration table',
       EXISTS(SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'correction_iteration')
UNION ALL
SELECT 'correction_asset_detail table',
       EXISTS(SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'correction_asset_detail')
UNION ALL
SELECT 'correction_param table',
       EXISTS(SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = @schema
                AND TABLE_NAME = 'correction_param');