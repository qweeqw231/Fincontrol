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
  profit           DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '持有收益（元），保留兼容；新代码优先读 holding_profit',
  holding_profit   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '持有收益（元），严格=截图「持有收益」列（不含当日浮盈）',
  cumulative_profit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '累计收益（元），含已实现盈亏（卖出后分母更新）',
  source        VARCHAR(30)  NOT NULL COMMENT '数据来源：screenshot_manual/screenshot_folder/manual_input/re_parse/manual_edit/import_csv/system_seed/data_correction',
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
  UNIQUE KEY uk_user_fund     (user_id, fund_name),
  INDEX idx_user_category     (user_id, category)
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

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 1a.8 增量脚本：已建库兼容（chat_history 加 used_provider + fallback_triggered）
-- ============================================
-- 上面 CREATE TABLE 已含新字段；下面这段用于已存在 chat_history 表的库补字段
-- MySQL 8.0+ 支持 IF NOT EXISTS（ADD COLUMN IF NOT EXISTS）
ALTER TABLE chat_history
  ADD COLUMN IF NOT EXISTS used_provider VARCHAR(20) NULL COMMENT '1a.8：本响应实际使用的 provider',
  ADD COLUMN IF NOT EXISTS fallback_triggered TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1a.8：本响应是否走了 fallback';

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
-- Schema 初始化完成
-- ============================================
-- 执行验证：
-- SELECT table_name, table_comment FROM information_schema.tables
--   WHERE table_schema = DATABASE() ORDER BY table_name;
-- 应返回 7 张表。
-- ============================================
