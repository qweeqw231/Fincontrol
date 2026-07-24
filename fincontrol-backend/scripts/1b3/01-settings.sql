-- PR3plus 决策 31：引入 settings 全局配置表
-- 创建时间：2026-07-24
-- 关联决策：docs/phase-1/decisions/decision-31-settings-global-config-table.md

CREATE TABLE IF NOT EXISTS settings (
  user_id              BIGINT       NOT NULL                COMMENT '用户 ID（单用户 MVP 默认 1）',
  max_snapshot_age_days INT         NOT NULL DEFAULT 7      COMMENT '历史限制天数；-1=不限制；7/14/30/180=限定天数',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局配置表（PR3plus 决策 31）';

-- 初始化用户 1 的默认 7 天限制
INSERT INTO settings (user_id, max_snapshot_age_days)
VALUES (1, 7)
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 验证
SELECT * FROM settings WHERE user_id = 1;
