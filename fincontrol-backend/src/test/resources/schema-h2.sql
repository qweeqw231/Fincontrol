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