-- H2 测试 schema（兼容 MySQL 8.0）
-- 仅 1a.3 confirm 涉及的三张表
-- 索引按 db-schema.sql 完整建；唯一键 H2 用 ALTER TABLE 单独添加
-- 1a.9：asset_raw + asset_snapshot 加 total_asset_source（H2 CHECK 模拟 MySQL ENUM）

CREATE TABLE IF NOT EXISTS asset_raw (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    fund_name VARCHAR(255) NOT NULL,
    fund_code VARCHAR(20),
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    -- 1a.10 P2：profit 兼容列与 holding_profit 同步语义。余额类 holding_profit 为 NULL 时，
    --           profit 也必须 NULL（保留余额类语义干净），H2 与 MySQL 一致允许 NULL。
    profit DECIMAL(12,2)                   NULL,
    -- 1a.8.8 v3.2：余额类（余额宝等）截图不显示 holding 列 → 允许 NULL；其他余额类累计也允许 NULL
    holding_profit DECIMAL(12,2)          NULL,
    cumulative_profit DECIMAL(12,2)         NULL,
    source VARCHAR(30) NOT NULL,
    -- 1a.9：该快照 total_asset 来源（denormalized，confirm 时 19 行同值）
    total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    confirmed_at TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS idx_raw_user_date_latest ON asset_raw(user_id, snapshot_date, is_latest);
CREATE INDEX IF NOT EXISTS idx_raw_user_fund ON asset_raw(user_id, fund_name);
-- 1a.9：H2 CHECK constraint 模拟 MySQL ENUM('top','visible_sum')
ALTER TABLE asset_raw ADD CONSTRAINT IF NOT EXISTS chk_asset_raw_total_asset_source
    CHECK (total_asset_source IN ('top', 'visible_sum'));

CREATE TABLE IF NOT EXISTS asset_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    target_ratio DECIMAL(5,2) NOT NULL DEFAULT 0,
    actual_ratio DECIMAL(5,2) NOT NULL DEFAULT 0,
    balance_fund DECIMAL(12,2) NOT NULL DEFAULT 0,
    sub_detail VARCHAR(2000),
    -- 1a.9：该快照总额来源（与 asset_raw.total_asset_source 语义一致）
    total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top',
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_snap_user_date ON asset_snapshot(user_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_snap_user_date_latest ON asset_snapshot(user_id, snapshot_date, is_latest);
-- 1a.9：H2 CHECK constraint 模拟 MySQL ENUM('top','visible_sum')
ALTER TABLE asset_snapshot ADD CONSTRAINT IF NOT EXISTS chk_asset_snapshot_total_asset_source
    CHECK (total_asset_source IN ('top', 'visible_sum'));

CREATE TABLE IF NOT EXISTS fund_category_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fund_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'user_manual',
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS idx_map_user_category ON fund_category_map(user_id, category);
CREATE INDEX IF NOT EXISTS idx_map_user_last_seen ON fund_category_map(user_id, last_seen_at);

-- 1a.10：category_master + 4 列/索引
CREATE TABLE IF NOT EXISTS category_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_canonical VARCHAR(50) NOT NULL,
    aliases VARCHAR(8000) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_master_canonical
    ON category_master(name_canonical);
CREATE INDEX IF NOT EXISTS idx_category_master_active ON category_master(is_active);

-- 1a.10：H2 中资产明细的 holding/cumulative 允许 NULL（1a.8.7）
ALTER TABLE asset_raw ALTER COLUMN holding_profit SET NULL;
ALTER TABLE asset_raw ALTER COLUMN cumulative_profit SET NULL;

-- 唯一键（CREATE UNIQUE INDEX IF NOT EXISTS 多次跑幂等；H2 1.4+ + MySQL 8 兼容）
CREATE UNIQUE INDEX IF NOT EXISTS uk_snap_user_date_cat_latest
    ON asset_snapshot(user_id, snapshot_date, category, is_latest);
CREATE UNIQUE INDEX IF NOT EXISTS uk_map_user_fund
    ON fund_category_map(user_id, fund_name);
-- 1b.3.1 决策 27：snapshot_meta 表（跨日期 is_current 元数据）
CREATE TABLE IF NOT EXISTS snapshot_meta (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  snapshot_date DATE NOT NULL,
  is_latest BOOLEAN NOT NULL DEFAULT FALSE,
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_snap_meta_user_date ON snapshot_meta(user_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_snap_meta_user_current ON snapshot_meta(user_id, is_current);
CREATE INDEX IF NOT EXISTS idx_snap_meta_user_latest ON snapshot_meta(user_id, is_latest);

-- 2026-10-05 修复：PR3plus 决策 30/31 引入 settings 表（SnapShotConfirmService 历史限制校验会读），
-- H2 schema 未同步导致 SnapShotConfirmRealFourPageH2Test 报 "Table settings not found"。
-- 与 db-schema.sql 同构（PK=user_id）；空表时 SettingsService 兜底默认 7 天。
CREATE TABLE IF NOT EXISTS settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    max_snapshot_age_days INT NOT NULL DEFAULT 7,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2026-10-05 修复（对齐生产库 SHOW CREATE TABLE）：fund_category_map 决策33 D7 新增两列，
-- H2 schema 未同步导致 upsertByFundName 报 "Column last_seen_snapshot_date not found"。
ALTER TABLE fund_category_map ADD COLUMN IF NOT EXISTS last_seen_snapshot_date DATE NULL;
ALTER TABLE fund_category_map ADD COLUMN IF NOT EXISTS first_missing_snapshot_date DATE NULL;
CREATE INDEX IF NOT EXISTS idx_map_user_last_seen_sd
    ON fund_category_map(user_id, last_seen_snapshot_date);
CREATE INDEX IF NOT EXISTS idx_map_user_first_missing
    ON fund_category_map(user_id, first_missing_snapshot_date);

-- 2026-10-06 新增（2a 校正页）：operation_log + 三张校正明细表。
-- 与 docs/phase-0/db-schema.sql 7/7.1/7.2/7.3 同构；供 CorrectionConfirm IT 与
-- 校正记录读写测试使用。
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    operation_date DATETIME NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    correction_mode VARCHAR(20) NULL,
    snapshot_date DATE NULL,
    v_curr DECIMAL(12,2) NULL,
    v_monetary DECIMAL(12,2) NULL,
    v_bond DECIMAL(12,2) NULL,
    v_high_vol DECIMAL(12,2) NULL,
    u_high DECIMAL(12,2) NULL,
    u_monetary_dca DECIMAL(12,2) NULL,
    u_bond_dca DECIMAL(12,2) NULL,
    delta_m_theory DECIMAL(12,2) NULL,
    delta_b_theory DECIMAL(12,2) NULL,
    delta_m_actual DECIMAL(12,2) NULL,
    delta_b_actual DECIMAL(12,2) NULL,
    rounding_strategy VARCHAR(30) NULL,
    deviation_m DECIMAL(5,2) NULL,
    deviation_b DECIMAL(5,2) NULL,
    target_ratios VARCHAR(200) NULL,
    budget_limit_used DECIMAL(12,2) NULL,
    total_investment DECIMAL(12,2) NULL,
    triggered_boundary VARCHAR(100) NULL,
    warnings TEXT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'monthly_correction',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS idx_op_user_date ON operation_log(user_id, operation_date);
CREATE INDEX IF NOT EXISTS idx_op_user_type_date ON operation_log(user_id, operation_type, operation_date);
CREATE INDEX IF NOT EXISTS idx_op_user_snapshot_date ON operation_log(user_id, snapshot_date);

CREATE TABLE IF NOT EXISTS correction_iteration (
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ci_op ON correction_iteration(operation_log_id);
CREATE INDEX IF NOT EXISTS idx_ci_user ON correction_iteration(user_id);

CREATE TABLE IF NOT EXISTS correction_asset_detail (
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cad_op ON correction_asset_detail(operation_log_id);
CREATE INDEX IF NOT EXISTS idx_cad_user ON correction_asset_detail(user_id);

CREATE TABLE IF NOT EXISTS correction_param (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    operation_log_id BIGINT NOT NULL,
    param_key VARCHAR(50) NOT NULL,
    num_value DECIMAL(18,4) NULL,
    text_value VARCHAR(300) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cp_op_key ON correction_param(operation_log_id, param_key);
CREATE INDEX IF NOT EXISTS idx_cp_user_key ON correction_param(user_id, param_key);

-- 2b 净值自动同步（NavSyncH2Test）：nav_history（与 db-schema/import-data 同构）
CREATE TABLE IF NOT EXISTS nav_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    nav_date DATE NOT NULL,
    weekday VARCHAR(10) NULL,
    daily_return_pct DECIMAL(12,8) NULL,
    actual_profit DECIMAL(12,2) NULL,
    cumulative_profit DECIMAL(12,2) NULL,
    nav DECIMAL(12,8) NULL,
    nav_pct DECIMAL(12,8) NULL,
    total_asset DECIMAL(14,2) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_nav_user_date ON nav_history(user_id, nav_date);
CREATE INDEX IF NOT EXISTS idx_nav_user_date ON nav_history(user_id, nav_date);
