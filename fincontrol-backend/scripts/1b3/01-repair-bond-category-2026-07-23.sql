-- ============================================================
-- 1b.3 P7 一次性数据修复脚本（2026-07-23）
-- ============================================================
-- 背景：
--   AI 视觉模型在 2026-07-21 / 2026-07-23 的解析中，把
--   长城短债债券A / 鹏华纯债债券D 误归到 A股权益类。
--   confirm 流程又把 ai_guess 错误地升级为 user_correct，
--   导致后续 confirm 一直被错误映射覆盖，固收类消失。
--
-- 修复（使用 CONVERT(... USING utf8mb4) COLLATE utf8mb4_unicode_ci 绕过 cmd/GBK 输入歧义）：
--   1) fund_category_map：source 改回 ai_guess、category 改回 固收类
--   2) asset_raw：4 个日期的 category 改回 固收类
--   3) asset_snapshot：4 个日期的 A股/固收 总额重算
--   4) 缺失固收类汇总行：通过 INSERT ... SELECT 插入
--   5) 不动其他基金、其他日期、其他类别
--
-- 前置备份：.tmp\repair-2026-07-23\before_*.txt
-- 使用：
--   mysql -h localhost -P 3306 -u root -proot --default-character-set=utf8mb4 fincontrol < 01-repair-bond-category-2026-07-23.sql
-- ============================================================

-- Step 1：fund_category_map 把 source 恢复为 ai_guess，category 写回固收类
UPDATE fund_category_map
   SET category     = CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
       source       = CONVERT('ai_guess' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
       last_seen_at = CURRENT_TIMESTAMP
 WHERE user_id  = 1
   AND fund_name IN (CONVERT('长城短债债券A' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
                    CONVERT('鹏华纯债债券D' USING utf8mb4) COLLATE utf8mb4_unicode_ci)
   AND source     = CONVERT('user_correct' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

-- Step 2：asset_raw 把 4 个日期的 category 改回固收类
UPDATE asset_raw
   SET category = CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci
 WHERE user_id     = 1
   AND fund_name   IN (CONVERT('长城短债债券A' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
                      CONVERT('鹏华纯债债券D' USING utf8mb4) COLLATE utf8mb4_unicode_ci)
   AND is_latest   = true
   AND snapshot_date IN ('2026-07-16', '2026-07-21', '2026-07-22', '2026-07-23');

-- Step 3：asset_snapshot 4 个日期的 A股 / 固收 总额重算
UPDATE asset_snapshot s
   SET s.total_amount = (
       SELECT COALESCE(SUM(ar.amount), 0)
         FROM asset_raw ar
        WHERE ar.user_id      = s.user_id
          AND ar.snapshot_date = s.snapshot_date
          AND ar.category     = s.category
          AND ar.is_latest    = true
   ),
   s.updated_at = CURRENT_TIMESTAMP
 WHERE s.user_id      = 1
   AND s.is_latest    = true
   AND s.snapshot_date IN ('2026-07-16', '2026-07-21', '2026-07-22', '2026-07-23')
   AND s.category     IN (CONVERT('A股权益类' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
                         CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci);

-- Step 4：缺失固收类汇总行（2026-07-21 / 2026-07-23）—— 通过 INSERT ... SELECT WHERE NOT EXISTS 插入
INSERT INTO asset_snapshot
    (user_id, snapshot_date, category, total_amount, target_ratio, actual_ratio,
     balance_fund, sub_detail, total_asset_source, is_latest, created_at, updated_at)
SELECT
    1, s.snapshot_date, CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci,
    COALESCE((SELECT SUM(ar.amount) FROM asset_raw ar
              WHERE ar.user_id = 1
                AND ar.snapshot_date = s.snapshot_date
                AND ar.category = CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci
                AND ar.is_latest = true), 0) AS total_amount,
    0 AS target_ratio,
    0 AS actual_ratio,
    0 AS balance_fund,
    NULL AS sub_detail,
    CONVERT('top' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS total_asset_source,
    true AS is_latest,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM asset_snapshot s
WHERE s.user_id = 1
  AND s.is_latest = true
  AND s.snapshot_date IN ('2026-07-21', '2026-07-23')
  AND NOT EXISTS (
      SELECT 1 FROM asset_snapshot t
      WHERE t.user_id      = s.user_id
        AND t.snapshot_date = s.snapshot_date
        AND t.category     = CONVERT('固收类' USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND t.is_latest    = true
  )
GROUP BY s.snapshot_date;

-- Step 5：自检
SELECT snapshot_date, category, total_amount, is_latest
  FROM asset_snapshot
 WHERE user_id = 1
   AND is_latest = true
   AND snapshot_date IN ('2026-07-16', '2026-07-21', '2026-07-22', '2026-07-23')
 ORDER BY snapshot_date, category;
