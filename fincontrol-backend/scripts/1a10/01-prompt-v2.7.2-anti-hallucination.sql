-- ============================================
-- screenshot_parser prompt v2.7.2（防幻觉 + 截断行防 0 占位）
-- ============================================
-- 背景：新设备重装数据库后只执行了 docs/phase-0/db-schema.sql（种子为 v1.0 旧"权益类"
--       口径），scripts/1a10/00-mysql-migration.sql（v2.7.1）未重跑。PromptLoaderService
--       启动预热缓存 v1.0 → AI 按旧口径幻觉基金（权益类）、截断行用 0.00 占位
--       （安信新价值 390.98 被 0.00 覆盖事故）。
-- 本脚本幂等，可重复执行。执行后必须重启后端（prompt 在启动时预热进内存缓存）。
-- 验证（应返回 v2.7.2 在第一行）：
--   SELECT id, version, created_at FROM prompt_versions
--   WHERE prompt_name='screenshot_parser' ORDER BY id DESC;
-- ============================================

INSERT INTO prompt_versions (prompt_name, version, prompt_content, change_reason)
SELECT 'screenshot_parser', 'v2.7.2',
'识别[日期]（支付宝）资产明细。默认数据源为支付宝，SDK 阶段再考虑多个基金软件的适配。请返回完整的 6 大配置类 + 余额类 的资产明细表格和汇总表格，以及一段总结。表格使用 Markdown 格式。每只基金需包含基金名称、持仓金额（元）、持有收益（元）、累计收益（元），以及类别归属。同时返回结构化 JSON 供后端解析。日期格式必须为 YYYY-MM-DD。\n\n【最高优先级 - 防幻觉约束】\n- 基金名称必须逐字转录当前截图中可见的文字，禁止编造、补全、推测，禁止输出训练记忆中的基金。\n- 当前截图中不存在的基金绝对不能出现在输出里；看不清或不确定的行宁可不输出，也不要猜。\n- amount 必须来自截图数字，禁止估算，禁止把多页数字拼凑相加。\n- 一次请求只输出一份 JSON，不要为每页分别输出 JSON。\n\n【截断行处理 - 禁止用 0.00 占位】\n- 若某基金名称可见、但金额/收益在截图中被截断不可见：amount 输出 null（禁止输出 0 或 0.00），holding_profit / cumulative_profit 同样输出 null。\n- 禁止把 0.00 当作"未知金额"的占位值；0.00 只用于截图明确显示为 0 的情况。\n- 若连基金名称都无法辨认，直接忽略该行。\n- 余额类（余额宝等）的 holding_profit / cumulative_profit 允许输出 null（支付宝不显示此列）。\n\n【重要 - 类别口径】\n本系统采用「6 大配置类 + 余额类」结构。\n  - 6 大配置类（参与资产比例计算）：\n    货币类（aliases: 货币、货基、货币基金）\n    固收类（aliases: 固收、债券、纯债、短债、固收+、中短债）\n    商品类（aliases: 商品、黄金、大宗商品）\n    A股权益类（aliases: A股、股票、中证、沪深300、中证500）\n    海外权益类（aliases: QDII、纳斯达克、标普、海外股票）\n    港股大中华类（aliases: 港股、恒生、大中华）\n  - 余额类（不参与配比，仅作为货币等价物）：余额宝、活期存款等\n  重要：只输出上述 7 个 canonical 名之一，禁止用「其他」「权益类」「另类资产」「保障类」等旧名。\n\n【重要 - 结构化 JSON 严格使用】\n{\n  "snapshot_date": "YYYY-MM-DD",\n  "total_asset": 浮点或null,\n  "categories": [\n    {\n      "category_name": "货币类|固收类|商品类|A股权益类|海外权益类|港股大中华类|余额类",\n      "category_total": 浮点,\n      "funds": [\n        {"fund_name": "...", "amount": 浮点或null, "holding_profit": 浮点或null, "cumulative_profit": 浮点或null}\n      ]\n    }\n  ],\n  "matchedFunds": ["基金1", "基金2", ...]\n}\n不要使用顶层 holdings + category_summary 结构。\n\n【端到端闭环】\n- total_asset 字段 = 截图顶部「总资产」数字，不可见时输出 null，禁止把当前页基金加总作为 total_asset。\n- 即使顶部不可见也必须输出完整 categories[].funds[]；截断行按上方规则输出 null，不要编造数字。\n- category_total 必须 = 该类 funds[].amount 之和；截图小计与此不一致时以逐只金额之和为准。\n- 只输出一份完整资产 JSON（放在 fenced code block ```json``` 内），不要附带解释性文字或思考过程。\n- 任何局部示例 / schema 文档 JSON 视为参考，模型最终输出必须基于图片内容，并按 fund_name 去重。',
'2026-10-05 v2.7.2：防幻觉约束（逐字转录、禁止编造）+ 截断行禁止用 0.00 占位（金额不可见时输出 null）+ 继承七大类口径'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_versions
    WHERE prompt_name = 'screenshot_parser' AND version = 'v2.7.2'
);