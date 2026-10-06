-- ============================================
-- FinControl 数据库 Schema v1.0（1a.8 增 used_provider + fallback_triggered）
-- ============================================
-- 配套文档：docs/api-contract.md, docs/phase-0-decisions.md
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 时区：UTC+8（Asia/Shanghai）
-- 引擎：InnoDB
-- 包含：四轮评审 + Phase 0 决策的所有 P0 修复 + 1a.8 AI 韧性增强
-- ============================================

-- 字符集与时区设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 删除已有表（按外键反序）
DROP TABLE IF EXISTS correction_param;
DROP TABLE IF EXISTS correction_asset_detail;
DROP TABLE IF EXISTS correction_iteration;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS chat_history;
DROP TABLE IF EXISTS prompt_versions;
DROP TABLE IF EXISTS user_config;
DROP TABLE IF EXISTS fund_category_map;
DROP TABLE IF EXISTS asset_snapshot;
DROP TABLE IF EXISTS asset_raw;

-- ============================================
-- 1. asset_raw（原始资产明细表）
-- ============================================
CREATE TABLE asset_raw (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID（Phase 5b 多用户预留）',
  snapshot_date DATE         NOT NULL COMMENT '快照日期',
  fund_name     VARCHAR(255) NOT NULL COMMENT '基金完整名称',
  fund_code     VARCHAR(20)  NULL COMMENT '基金代码（远期预留，Phase 5）',
  category      VARCHAR(50)  NOT NULL COMMENT '七大类：货币类/固收类/商品类/A股权益类/海外权益类/港股/大中华类/余额类',
  amount        DECIMAL(12,2) NOT NULL COMMENT '持仓金额（元）',
  -- 1a.8.7 拆分：profit 与 holding_profit 同步写（兼容期），cumulative_profit 单独存。
  -- 1a.8.8 v3.2 修订：余额类（余额宝等）截图不显示 holding 列 → holding_profit 允许 NULL；
  --                 余额类（活期存款等）截图也不显示 cumulative → cumulative_profit 允许 NULL
  profit           DECIMAL(12,2)          NULL COMMENT '持有收益（元），保留兼容；与 holding_profit 同步：余额类允许 NULL（1a.10 P2）',
  holding_profit   DECIMAL(12,2)          NULL COMMENT '持有收益（元），严格=截图「持有收益」列（不含当日浮盈）；余额类允许 NULL',
  cumulative_profit DECIMAL(12,2)         NULL COMMENT '累计收益（元），含已实现盈亏（卖出后分母更新）；其他余额类允许 NULL',
  source        VARCHAR(30)  NOT NULL COMMENT '数据来源：screenshot_manual/screenshot_folder/manual_input/re_parse/manual_edit/import_csv/system_seed/data_correction',
  -- 1a.9：该快照 total_asset 来源（denormalized，confirm 时 19 行同值）
  total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top' COMMENT '1a.9：该快照总额来源（top=顶部总资产 / visible_sum=deduped fund 加总）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录首次写入时间',
  is_latest     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否为该快照日期的最新记录',
  confirmed_at  DATETIME     NULL COMMENT '用户确认入库时间（第一轮评审P0 1.3新增）',
  INDEX idx_user_date_latest (user_id, snapshot_date, is_latest),
  INDEX idx_user_fund        (user_id, fund_name),
  INDEX idx_user_created     (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原始资产明细表（不可变）';

-- ============================================
-- 2. asset_snapshot（资产快照汇总表）
-- ============================================
CREATE TABLE asset_snapshot (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id        BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  snapshot_date  DATE         NOT NULL COMMENT '快照日期',
  category       VARCHAR(50)  NOT NULL COMMENT '七大类（含余额类）',
  total_amount   DECIMAL(12,2) NOT NULL COMMENT '该大类总金额',
  target_ratio   DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '目标比例（%），从user_config同步（第四轮P0 2.2.1 解读4）',
  actual_ratio   DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '实际占比（%）',
  balance_fund   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '余额类金额（冗余存储，便于查询；亦可通过SUM动态计算）',
  sub_detail     JSON         NULL COMMENT '大类内部子类金额明细（Phase 5 LQR预留）',
  -- 1a.9：该快照总额来源（与 asset_raw.total_asset_source 语义一致）
  total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top' COMMENT '1a.9：top=顶部总资产 / visible_sum=deduped fund 加总',
  is_latest      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '同日多次入库时唯一true',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '汇总写入时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总更新时间（第四轮P0新增）',
  UNIQUE KEY uk_user_date_category (user_id, snapshot_date, category, is_latest),
  INDEX idx_user_date        (user_id, snapshot_date),
  INDEX idx_user_date_latest (user_id, snapshot_date, is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产快照汇总表';

-- ============================================
-- 3. fund_category_map（基金-大类映射表）
-- ============================================
CREATE TABLE fund_category_map (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  fund_name    VARCHAR(255) NOT NULL COMMENT '基金完整名称',
  category     VARCHAR(50)  NOT NULL COMMENT '七大类',
  source       VARCHAR(30)  NOT NULL DEFAULT 'user_manual' COMMENT '确认方式：ai_guess/ai_guess_confirmed/user_correct/user_manual/user_voided',
  confirmed_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认时间',
  last_seen_at DATETIME     NULL COMMENT '最近一次出现在截图中的时间（1a.8.8：stale 判定 + re-confirm 去弹窗）',
  last_seen_snapshot_date DATE NULL COMMENT '决策33 D7(R5)：上次有该基金的 confirm snapshot_date；锚点=MAX(此字段)',
  first_missing_snapshot_date DATE NULL COMMENT '决策33 D7(R5)：首次发现该基金缺失的 confirm snapshot_date；重现时清空',
  UNIQUE KEY uk_user_fund     (user_id, fund_name),
  INDEX idx_user_category     (user_id, category),
  INDEX idx_user_last_seen    (user_id, last_seen_at),
  INDEX idx_user_last_seen_sd (user_id, last_seen_snapshot_date),
  INDEX idx_user_first_missing (user_id, first_missing_snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金-大类映射表';

-- ============================================
-- 4. chat_history（对话历史表，1a.8 增监控字段）
-- ============================================
CREATE TABLE chat_history (
  id                  BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id             BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  conversation_id     VARCHAR(50)  NOT NULL COMMENT '对话ID（UUID）',
  role                VARCHAR(20)  NOT NULL COMMENT 'user/assistant',
  content             TEXT         NOT NULL COMMENT '消息内容',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  conversation_type   VARCHAR(30)  NOT NULL DEFAULT 'ai_assistant' COMMENT '对话类型：ai_assistant/screenshot_parse',
  used_provider       VARCHAR(20)  NULL COMMENT '1a.8：本响应实际使用的 provider：minimax/doubao/deepseek（user 行 / 失败行为空）',
  fallback_triggered  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1a.8：本响应是否走了 fallback（0=主路径 / 1=已 fallback）',
  INDEX idx_user_conv_created (user_id, conversation_id, created_at),
  INDEX idx_user_type_created (user_id, conversation_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话历史表';

-- ============================================
-- 5. user_config（用户配置表）
-- ============================================
CREATE TABLE user_config (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  config_key  VARCHAR(100) NOT NULL COMMENT '配置键',
  config_value VARCHAR(500) NOT NULL COMMENT '配置值',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_key (user_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户配置表';

-- ============================================
-- 6. prompt_versions（Prompt版本管理表）
-- ============================================
CREATE TABLE prompt_versions (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  prompt_name   VARCHAR(100) NOT NULL COMMENT 'Prompt名称：screenshot_parser/ai_assistant/intent_classifier',
  prompt_content TEXT        NOT NULL COMMENT 'Prompt完整内容',
  version       VARCHAR(20)  NOT NULL COMMENT '版本号',
  change_reason TEXT         NULL COMMENT '修改原因',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_name_version (prompt_name, version),
  INDEX idx_name (prompt_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt版本管理表';

-- ============================================
-- 7. operation_log（操作日志表 - 第二轮评审P0 2.1.10新增）
-- ============================================
CREATE TABLE operation_log (
  id                 BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id            BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_date     DATETIME     NOT NULL COMMENT '用户操作当天时间',
  operation_type     VARCHAR(30)  NOT NULL COMMENT '操作类型：monthly_correction/quarterly_correction/manual_adjustment',
  correction_mode    VARCHAR(20)  NULL COMMENT '2a：校正模式（zoh_only=纯低波ZOH / lqr_zoh=LQR-ZOH联合 / manual=手工战术；NULL=未分类历史行）',
  snapshot_date      DATE         NULL COMMENT '关联快照日期',
  v_curr             DECIMAL(12,2) NULL COMMENT '操作时六大类合计（不含余额类，第二轮P0已澄清）',
  v_monetary         DECIMAL(12,2) NULL COMMENT '货币类市值',
  v_bond             DECIMAL(12,2) NULL COMMENT '固收类市值',
  v_high_vol         DECIMAL(12,2) NULL COMMENT '高波合计市值',
  u_high             DECIMAL(12,2) NULL COMMENT '高波定投总额',
  u_monetary_dca     DECIMAL(12,2) NULL COMMENT '低波定投-货币类反推份额',
  u_bond_dca         DECIMAL(12,2) NULL COMMENT '低波定投-固收类反推份额',
  delta_m_theory     DECIMAL(12,2) NULL COMMENT '货币类理论补仓（方程组精确解）',
  delta_b_theory     DECIMAL(12,2) NULL COMMENT '固收类理论补仓（方程组精确解）',
  delta_m_actual     DECIMAL(12,2) NULL COMMENT '货币类取整后实际值',
  delta_b_actual     DECIMAL(12,2) NULL COMMENT '固收类取整后实际值',
  rounding_strategy  VARCHAR(30)  NULL COMMENT '取整策略：round_up_10/round_up_100/manual',
  deviation_m        DECIMAL(5,2) NULL COMMENT '货币类取整后偏差(%)',
  deviation_b        DECIMAL(5,2) NULL COMMENT '固收类取整后偏差(%)',
  target_ratios      VARCHAR(200) NULL COMMENT '当时user_config.target_ratios（JSON字符串，决策2）',
  budget_limit_used  DECIMAL(12,2) NULL COMMENT '生效的budgetLimit',
  total_investment   DECIMAL(12,2) NULL COMMENT '总投入',
  triggered_boundary VARCHAR(100) NULL COMMENT '触发的边界编号（如1,2,3,4,5）',
  warnings           TEXT         NULL COMMENT '约束警告信息（JSON数组字符串）',
  source             VARCHAR(30)  NOT NULL DEFAULT 'monthly_correction' COMMENT '数据来源',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  confirmed_at       DATETIME     NULL COMMENT '用户确认时间',
  INDEX idx_user_op_date      (user_id, operation_date),
  INDEX idx_user_op_type_date (user_id, operation_type, operation_date),
  INDEX idx_user_snapshot_date (user_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表（月度/季度校正流水）';

-- ============================================
-- 7.1 correction_iteration（校正迭代轮次明细表 · 2a 校正页提前交付）
-- ============================================
-- 一次校正的求解过程拆分为多轮（如 6/30 联合校正 α 七轮压缩），每轮一行。
CREATE TABLE correction_iteration (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  sort_order       INT          NOT NULL COMMENT '轮次顺序（0=初始，1..n=第 n 轮）',
  alpha            DECIMAL(6,4) NULL COMMENT '本轮高波校正预算占比 α（小数，如 0.2000/0.0419）',
  e_high           DECIMAL(12,2) NULL COMMENT '本轮高波校正预算 E_high（元）',
  delta_m          DECIMAL(12,2) NULL COMMENT '本轮低波货币补仓 Δm（元）',
  delta_b          DECIMAL(12,2) NULL COMMENT '本轮低波固收补仓 Δb（元）',
  zoh_triggered    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '本轮 Δm 是否触发 ZOH 补仓（≥ purchaseThreshold）',
  total_investment DECIMAL(12,2) NULL COMMENT '本轮总投入（元）',
  over_limit       DECIMAL(12,2) NULL COMMENT '超出 M_max 金额（元）；NULL=未超限',
  note             VARCHAR(200) NULL COMMENT '备注（如“第 7 次迭代，满足约束”）',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_ci_op   (operation_log_id),
  INDEX idx_ci_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正迭代/调节轮次明细（α 压缩与分布式调节）';

-- ============================================
-- 7.2 correction_asset_detail（校正逐资产明细表 · 2a 校正页提前交付）
-- ============================================
-- 一行 = 一个阶段 × 一个类别；phase 枚举：pre_six/post_six/pre_high/post_high。
CREATE TABLE correction_asset_detail (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  sort_order       INT          NOT NULL COMMENT '展示顺序',
  phase            VARCHAR(20)  NOT NULL COMMENT '阶段：pre_six=校正前六大类/post_six=校正后六大类/pre_high=高波内部校正前/post_high=高波内部校正后',
  category         VARCHAR(50)  NOT NULL COMMENT 'canonical 类别名',
  amount           DECIMAL(12,2) NULL COMMENT '该阶段市值（元）',
  ratio_actual     DECIMAL(6,2) NULL COMMENT '实际占比（%）：six 阶段=占六大类；high 阶段=高波内部占比',
  ratio_target     DECIMAL(6,2) NULL COMMENT '目标占比（%）',
  deviation        DECIMAL(6,2) NULL COMMENT '偏差（百分点，actual - target）',
  delta_raw        DECIMAL(12,2) NULL COMMENT '本类补仓求解原值（元，未取整）',
  delta_amount     DECIMAL(12,2) NULL COMMENT '本类补仓执行值（元，取整后）',
  note             VARCHAR(200) NULL COMMENT '备注（如“锚定资产 Δ=0”）',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_cad_op   (operation_log_id),
  INDEX idx_cad_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正逐资产明细（校正前/后 × 六大类与高波内部）';

-- ============================================
-- 7.3 correction_param（校正参数与指标表 · 2a 校正页提前交付）
-- ============================================
-- key-value 存放不适合建列的参数与指标，便于新增指标不改表。
-- 常用 param_key 枚举：surplus/mMax/mMaxSource/alphaInit/alphaDecay/alphaFinal/
-- zohThreshold/zohStepPointEHigh/zohStepTotal/icDrrBefore/icDrrAfter/icDrrPct/
-- anchor/zohTriggered/actualTransfer/kktNote。
CREATE TABLE correction_param (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  param_key        VARCHAR(50)  NOT NULL COMMENT '参数键（见上方枚举）',
  num_value        DECIMAL(18,4) NULL COMMENT '数值型值',
  text_value       VARCHAR(300) NULL COMMENT '文本型值',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_cp_op_key (operation_log_id, param_key),
  INDEX idx_cp_user_key (user_id, param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正参数与指标（key-value）';

-- 1a.10：七大类主数据（DB-driven 类别 + 别名）
CREATE TABLE category_master (
  id              BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  name_canonical  VARCHAR(50) NOT NULL COMMENT '标准类别名',
  aliases         JSON        NOT NULL COMMENT '别名数组（JSON）',
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '是否启用',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_category_master_canonical (name_canonical),
  KEY idx_category_master_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='1a.10 七大类主数据';

-- 决策 27：跨日期 is_current 快照元数据（per-date is_latest + current 切换）
CREATE TABLE snapshot_meta (
  id              BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id         BIGINT      NOT NULL COMMENT '用户ID',
  snapshot_date   DATE        NOT NULL COMMENT '快照日期',
  is_latest       BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'per-date 标记（与 asset_raw.is_latest 语义一致）',
  is_current      BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '跨日期当前快照（每 user 最多 1 行 true）',
  confirmed_at    DATETIME    NOT NULL COMMENT 'confirm 时戳',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_date (user_id, snapshot_date),
  KEY idx_user_current (user_id, is_current),
  KEY idx_user_latest (user_id, is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='决策27：跨日期 is_current 快照元数据';

-- 决策 31：全局配置表（单用户 MVP）
CREATE TABLE settings (
  user_id               BIGINT  NOT NULL COMMENT '用户ID（单用户默认1）',
  max_snapshot_age_days INT     NOT NULL DEFAULT 7 COMMENT '历史限制天数；-1=不限制；7/14/30/180=限定天数',
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='决策31：全局配置表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 1a.8+ 增量脚本：已建库兼容（补字段/补索引，幂等）
-- ============================================
-- 上面 CREATE TABLE 已含所有新字段；下面这段用于已存在旧表的库补字段。
-- 注意：MySQL 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（那是 MariaDB 语法），
-- 统一用存储过程查 information_schema 实现"存在则跳过"。
DELIMITER //
DROP PROCEDURE IF EXISTS add_col_if_not_exists//
CREATE PROCEDURE add_col_if_not_exists(
  IN p_table      VARCHAR(64),
  IN p_column     VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = p_table
      AND COLUMN_NAME  = p_column
  ) THEN
    SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//

DROP PROCEDURE IF EXISTS add_idx_if_not_exists//
CREATE PROCEDURE add_idx_if_not_exists(
  IN p_table      VARCHAR(64),
  IN p_index      VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = p_table
      AND INDEX_NAME   = p_index
  ) THEN
    SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD INDEX ', p_index, ' ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

-- ============================================
-- 2026-10-05：asset_raw.profit 允许 NULL（1a.10 P2）
-- ============================================
-- 事故：仅执行本文件的重装库缺少 00-mysql-migration.sql 中同一 MODIFY →
-- 余额类（holding=NULL 时 profit 同步 NULL）写入撞 "Column 'profit' cannot be null"，
-- confirm 整事务回滚（前端报 DataIntegrityViolationException）。
-- MODIFY 幂等：列已是 NULL 时重复执行结果不变。
ALTER TABLE asset_raw MODIFY COLUMN profit DECIMAL(12,2) NULL COMMENT '1a.8.7 兼容列，与 holding_profit 同步；余额类允许 NULL';

-- chat_history：1a.8 加 used_provider + fallback_triggered
CALL add_col_if_not_exists('chat_history', 'used_provider',
  'VARCHAR(20) NULL COMMENT ''1a.8：本响应实际使用的 provider''');
CALL add_col_if_not_exists('chat_history', 'fallback_triggered',
  'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''1a.8：本响应是否走了 fallback''');

-- fund_category_map：1a.8.8 加 last_seen_at + 索引
CALL add_col_if_not_exists('fund_category_map', 'last_seen_at',
  'DATETIME NULL COMMENT ''1a.8.8：最近一次出现在截图中的时间''');
CALL add_idx_if_not_exists('fund_category_map', 'idx_user_last_seen',
  '(user_id, last_seen_at)');

-- fund_category_map：决策33 D7(R5) 加 last_seen_snapshot_date + first_missing_snapshot_date
CALL add_col_if_not_exists('fund_category_map', 'last_seen_snapshot_date',
  'DATE NULL COMMENT ''决策33 D7(R5)：上次有该基金的 confirm snapshot_date''');
CALL add_col_if_not_exists('fund_category_map', 'first_missing_snapshot_date',
  'DATE NULL COMMENT ''决策33 D7(R5)：首次发现该基金缺失的 confirm snapshot_date''');
CALL add_idx_if_not_exists('fund_category_map', 'idx_user_last_seen_sd',
  '(user_id, last_seen_snapshot_date)');
CALL add_idx_if_not_exists('fund_category_map', 'idx_user_first_missing',
  '(user_id, first_missing_snapshot_date)');

-- asset_raw / asset_snapshot：1a.9 加 total_asset_source
CALL add_col_if_not_exists('asset_raw', 'total_asset_source',
  'VARCHAR(20) NOT NULL DEFAULT ''top'' COMMENT ''1a.9：该快照总额来源（top/visible_sum）''');
CALL add_col_if_not_exists('asset_snapshot', 'total_asset_source',
  'VARCHAR(20) NOT NULL DEFAULT ''top'' COMMENT ''1a.9：top=顶部总资产 / visible_sum=deduped fund 加总''');

-- operation_log：2a 加 correction_mode（校正模式：zoh_only/lqr_zoh/manual）
CALL add_col_if_not_exists('operation_log', 'correction_mode',
  'VARCHAR(20) NULL COMMENT ''2a：校正模式（zoh_only=纯低波ZOH / lqr_zoh=LQR-ZOH联合 / manual=手工战术；NULL=未分类历史行）''');

DROP PROCEDURE IF EXISTS add_col_if_not_exists;
DROP PROCEDURE IF EXISTS add_idx_if_not_exists;

-- ============================================
-- 2a 校正明细表（已建库兼容：CREATE TABLE IF NOT EXISTS 幂等）
-- ============================================
-- 与上方 7.1/7.2/7.3 同构；仅用于已存在旧库补齐新表。
-- 注意：整份文件从头重跑会 DROP 全部表，已建库请勿整体重跑；可执行
-- fincontrol-backend/scripts/2a-correction/00-correction-tables.sql（等效且带校验）或仅本段。
CREATE TABLE IF NOT EXISTS correction_iteration (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  sort_order       INT          NOT NULL,
  alpha            DECIMAL(6,4) NULL,
  e_high           DECIMAL(12,2) NULL,
  delta_m          DECIMAL(12,2) NULL,
  delta_b          DECIMAL(12,2) NULL,
  zoh_triggered    BOOLEAN      NOT NULL DEFAULT FALSE,
  total_investment DECIMAL(12,2) NULL,
  over_limit       DECIMAL(12,2) NULL,
  note             VARCHAR(200) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ci_op   (operation_log_id),
  INDEX idx_ci_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正迭代/调节轮次明细（α 压缩与分布式调节）';

CREATE TABLE IF NOT EXISTS correction_asset_detail (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  sort_order       INT          NOT NULL,
  phase            VARCHAR(20)  NOT NULL,
  category         VARCHAR(50)  NOT NULL,
  amount           DECIMAL(12,2) NULL,
  ratio_actual     DECIMAL(6,2) NULL,
  ratio_target     DECIMAL(6,2) NULL,
  deviation        DECIMAL(6,2) NULL,
  delta_raw        DECIMAL(12,2) NULL,
  delta_amount     DECIMAL(12,2) NULL,
  note             VARCHAR(200) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cad_op   (operation_log_id),
  INDEX idx_cad_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正逐资产明细（校正前/后 × 六大类与高波内部）';

CREATE TABLE IF NOT EXISTS correction_param (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  operation_log_id BIGINT       NOT NULL COMMENT '关联 operation_log.id',
  param_key        VARCHAR(50)  NOT NULL,
  num_value        DECIMAL(18,4) NULL,
  text_value       VARCHAR(300) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cp_op_key (operation_log_id, param_key),
  INDEX idx_cp_user_key (user_id, param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='2a：校正参数与指标（key-value）';

-- ============================================
-- 初始化数据：用户默认配置
-- ============================================
INSERT INTO user_config (user_id, config_key, config_value) VALUES
  (1, 'purchase_threshold', '100'),
  (1, 'monthly_budget_limit', '1000'),
  (1, 'high_vol_dca_budget', '560'),
  (1, 'target_ratios', '{"货币类":10,"固收类":15,"商品类":25,"A股权益类":25,"海外权益类":20,"港股/大中华类":5}'),
  (1, 'carry_over_amount', '0')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- ============================================
-- 初始化数据：Prompt版本（screenshot_parser v1.0）
-- ============================================
INSERT INTO prompt_versions (prompt_name, prompt_content, version, change_reason) VALUES
  ('screenshot_parser',
   '识别[日期]（支付宝）资产明细。默认数据源为支付宝，SDK阶段再考虑多个基金软件的适配。请返回完整的六大类资产明细表格和六大类汇总表格，以及一段总结。表格使用Markdown格式。每只基金需包含基金名称、持仓金额（元）、持有收益（元）、六大类归属（含"余额类"，第一轮P0 1.1新增）。同时返回结构化JSON供后端解析。日期格式必须为 YYYY-MM-DD（第三轮P0 4.2.1新增）。\n\n【重要】结构化JSON必须严格使用以下嵌套结构（后端只解析此结构）：\n{\n  "snapshot_date": "YYYY-MM-DD",\n  "total_asset": 浮点,\n  "categories": [\n    {\n      "category_name": "余额类|固收类|商品类|权益类|另类资产|保障类",\n      "category_total": 浮点,\n      "category_percentage": 浮点,\n      "funds": [\n        {"fund_name": "...", "amount": 浮点, "profit": 浮点}\n      ]\n    }\n  ],\n  "matchedFunds": ["基金1", "基金2", ...]\n}\n不要使用顶层 holdings + category_summary 结构。',
   'v1.0',
   '初始版本，已验证可用。支持六大类+余额类识别，返回Markdown表格+JSON。'),
  ('ai_assistant',
   '你是"FinControl AI顾问"，严格基于用户预设的资产配置规则系统给出建议。\n\n核心规则：\n资产配置：六大类（货币10%/固收15%/商品25%/A股25%/海外20%/港股5%）\n执行法则：月初归集资金、一次性校准低风险资产、剩余资金按日自动定投\n核心原则：不预测市场、零摩擦（不鼓励卖出）、用规则应对波动\n\n对话原则：\n- 去术语化：不使用"零阶保持器""前馈补偿"等词汇，用"自动校准""月初一键修复"替代\n- 共情优先：当用户表达焦虑时，先安抚情绪，再给出规则内的操作建议\n- 结论先行：直接告诉用户该做什么，解释不超过20字\n\n安全边界：\n当被问及"该不该卖"或预测涨跌时，重申"系统只按比例调配，不预测市场"\n所有建议最后必须加上："以上分析基于你的规则系统，仅供参考，不构成投资决策。"',
   'v1.0',
   '初始版本，继承微控金融鸿蒙版AI顾问的System Prompt，去术语化处理。'),
  ('intent_classifier',
   '判断用户输入是否属于"投资决策咨询"。\n\n正例（投资决策咨询）：\n- "本月应该补仓多少"\n- "海外权益类占比偏高怎么办"\n- "比例偏离分析"\n- "市场波动对持仓的影响"\n- "如何再平衡"\n- "我应该买什么基金"\n- "定投金额怎么调整"\n\n反例（非投资决策咨询）：\n- "今天天气怎么样"\n- "你是什么模型"\n- "帮我写一首诗"\n- "Python怎么读取MySQL"\n- "介绍你的功能"\n- "早上好"\n- "你今年多大"\n\n用户输入：[用户输入文本]\n请只返回 true 或 false（小写，无标点）。true 表示属于投资决策咨询，false 表示不属于。',
   'v1.0',
   '第三轮评审P0 3.1.1补充few-shot示例（7对正例 + 7对反例）')
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content), change_reason = VALUES(change_reason);

-- ============================================
-- 2026-10-05：screenshot_parser v2.7.2（防幻觉 + 截断行防 0 占位）
-- 事故背景：新设备重装库后仅执行本文件（种子为 v1.0 旧"权益类"口径），
-- scripts/1a10 迁移未重跑 → AI 按旧口径幻觉基金、截断行用 0.00 占位。
-- uk(prompt_name, version) 保证幂等；PromptLoaderService 按 id DESC 取最新版本。
-- ============================================
INSERT INTO prompt_versions (prompt_name, version, prompt_content, change_reason) VALUES
  ('screenshot_parser',
   '识别[日期]（支付宝）资产明细。默认数据源为支付宝，SDK 阶段再考虑多个基金软件的适配。请返回完整的 6 大配置类 + 余额类 的资产明细表格和汇总表格，以及一段总结。表格使用 Markdown 格式。每只基金需包含基金名称、持仓金额（元）、持有收益（元）、累计收益（元），以及类别归属。同时返回结构化 JSON 供后端解析。日期格式必须为 YYYY-MM-DD。\n\n【最高优先级 - 防幻觉约束】\n- 基金名称必须逐字转录当前截图中可见的文字，禁止编造、补全、推测，禁止输出训练记忆中的基金。\n- 当前截图中不存在的基金绝对不能出现在输出里；看不清或不确定的行宁可不输出，也不要猜。\n- amount 必须来自截图数字，禁止估算，禁止把多页数字拼凑相加。\n- 一次请求只输出一份 JSON，不要为每页分别输出 JSON。\n\n【截断行处理 - 禁止用 0.00 占位】\n- 若某基金名称可见、但金额/收益在截图中被截断不可见：amount 输出 null（禁止输出 0 或 0.00），holding_profit / cumulative_profit 同样输出 null。\n- 禁止把 0.00 当作"未知金额"的占位值；0.00 只用于截图明确显示为 0 的情况。\n- 若连基金名称都无法辨认，直接忽略该行。\n- 余额类（余额宝等）的 holding_profit / cumulative_profit 允许输出 null（支付宝不显示此列）。\n\n【重要 - 类别口径】\n本系统采用「6 大配置类 + 余额类」结构。\n  - 6 大配置类（参与资产比例计算）：\n    货币类（aliases: 货币、货基、货币基金）\n    固收类（aliases: 固收、债券、纯债、短债、固收+、中短债）\n    商品类（aliases: 商品、黄金、大宗商品）\n    A股权益类（aliases: A股、股票、中证、沪深300、中证500）\n    海外权益类（aliases: QDII、纳斯达克、标普、海外股票）\n    港股大中华类（aliases: 港股、恒生、大中华）\n  - 余额类（不参与配比，仅作为货币等价物）：余额宝、活期存款等\n  重要：只输出上述 7 个 canonical 名之一，禁止用「其他」「权益类」「另类资产」「保障类」等旧名。\n\n【重要 - 结构化 JSON 严格使用】\n{\n  "snapshot_date": "YYYY-MM-DD",\n  "total_asset": 浮点或null,\n  "categories": [\n    {\n      "category_name": "货币类|固收类|商品类|A股权益类|海外权益类|港股大中华类|余额类",\n      "category_total": 浮点,\n      "funds": [\n        {"fund_name": "...", "amount": 浮点或null, "holding_profit": 浮点或null, "cumulative_profit": 浮点或null}\n      ]\n    }\n  ],\n  "matchedFunds": ["基金1", "基金2", ...]\n}\n不要使用顶层 holdings + category_summary 结构。\n\n【端到端闭环】\n- total_asset 字段 = 截图顶部「总资产」数字，不可见时输出 null，禁止把当前页基金加总作为 total_asset。\n- 即使顶部不可见也必须输出完整 categories[].funds[]；截断行按上方规则输出 null，不要编造数字。\n- category_total 必须 = 该类 funds[].amount 之和；截图小计与此不一致时以逐只金额之和为准。\n- 只输出一份完整资产 JSON（放在 fenced code block ```json``` 内），不要附带解释性文字或思考过程。\n- 任何局部示例 / schema 文档 JSON 视为参考，模型最终输出必须基于图片内容，并按 fund_name 去重。',
   'v2.7.2',
   '2026-10-05 v2.7.2：防幻觉约束（逐字转录、禁止编造）+ 截断行禁止用 0.00 占位（金额不可见时输出 null）+ 继承七大类口径')
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content), change_reason = VALUES(change_reason);

-- 1a.10：category_master 七大类 canonical 种子（幂等）
INSERT INTO category_master (name_canonical, aliases) VALUES
  ('货币类',       JSON_ARRAY('货币', '货基', '货币基金')),
  ('固收类',       JSON_ARRAY('固收', '债券', '债券类', '纯债', '短债', '固收+', '中短债')),
  ('商品类',       JSON_ARRAY('商品', '黄金', '大宗商品', '黄金ETF')),
  ('A股权益类',    JSON_ARRAY('A股', '股票', '股票类', 'A股权益', '股票指数', '中证', '宽基', '沪深300', '中证500', '中证1000')),
  ('海外权益类',    JSON_ARRAY('海外权益', 'QDII', '海外股票', '海外QDII', '纳斯达克', '标普', '海外')),
  ('港股大中华类',  JSON_ARRAY('港股', '大中华', '港股QDII', '恒生', '港股/大中华类', '港股/大中华')),
  ('余额类',       JSON_ARRAY('余额', '余额宝'))
ON DUPLICATE KEY UPDATE
  aliases = VALUES(aliases),
  is_active = TRUE,
  updated_at = CURRENT_TIMESTAMP;

-- 决策 31：settings 全局配置种子（单用户 MVP，默认 7 天历史限制）
INSERT INTO settings (user_id, max_snapshot_age_days)
VALUES (1, 7)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- ============================================
-- Schema 初始化完成
-- ============================================
-- 执行验证：
-- SELECT table_name, table_comment FROM information_schema.tables
--   WHERE table_schema = DATABASE() ORDER BY table_name;
-- 应返回 13 张表（asset_raw / asset_snapshot / category_master / chat_history /
--   fund_category_map / operation_log / prompt_versions / settings / snapshot_meta / user_config /
--   correction_iteration / correction_asset_detail / correction_param）。
-- ============================================
