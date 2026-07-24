# PR3plus 验收计划

**日期**：2026-07-24
**关联 PR**：1b.4-pr3plus（pr3plus）
**关联决策**：[decision-30](../../../phase-1/decisions/decision-30-no-hardcode-history-limit.md) + [decision-31](../../../phase-1/decisions/decision-31-settings-global-config-table.md)
**关联工作计划**：[work-plan](../../../phase-1/work-plans/1b/2026-07-24_1b4-pr3plus-work-plan.md)

## 验收范围

### 后端验收

#### A. settings 表创建
- [ ] SQL 脚本 `fincontrol-backend/scripts/1b3/01-settings.sql` 存在
- [ ] 脚本可执行：登录 MySQL `mysql -u root -p fincontrol < 01-settings.sql`
- [ ] 验证表存在：`SHOW TABLES LIKE 'settings';`
- [ ] 验证字段：`DESC settings;` 看到 user_id / max_snapshot_age_days / created_at / updated_at
- [ ] 验证 user_id=1 已初始化：`SELECT * FROM settings WHERE user_id=1;` 返回 max_snapshot_age_days=7

#### B. GET /api/settings/{userId}/max-snapshot-age-days
- [ ] 初始 GET `http://localhost:8080/api/settings/1/max-snapshot-age-days` → 200 + `{maxSnapshotAgeDays: 7}`
- [ ] 未知 userId GET `.../2/...` → 200 + `{maxSnapshotAgeDays: 7}`（默认）

#### C. PUT /api/settings/{userId}/max-snapshot-age-days
- [ ] PUT 30 天 → 200 + DB 写入 30
- [ ] 紧接着 GET → 200 + 30
- [ ] PUT 7 天 → 200 + DB 改回 7
- [ ] PUT -1 (不限制) → 200 + DB 写入 -1
- [ ] PUT 非法值 999 → 400 + 业务 message
- [ ] PUT 0 → 400 + 业务 message

#### D. SnapShotConfirmService 校验
- [ ] 初始 (7 天限制) confirm 7/13 截图 → 400 + message "相差 11 天（>7 天拒绝）"
- [ ] 改为 30 天后 confirm 7/13 截图 → 200 ✓
- [ ] 改为 -1 (不限制) 后 confirm 7/13 → 200 ✓
- [ ] confirm 1 年前的截图（>180 天）→ 400（兜底上限仍生效）

### 前端验收

#### E. userConfigStore
- [ ] 启动时调 GET 拿 maxSnapshotAgeDays
- [ ] 初始 state = 7
- [ ] setMaxSnapshotAgeDays(1, 30) 调 PUT 成功 → state 更新为 30

#### F. DataPage 按钮
- [ ] 上传 section 显示 `当前历史截图限制：最近 7 天 [修改]`
- [ ] 数字 7 来自 store（不是硬编码字符串）— 改 store 后按钮文字同步更新

#### G. HistoryLimitDialog 4 步流程
- [ ] 点 [修改] 按钮 → 弹 prompt
- [ ] prompt 步骤：选择"取消" → 弹"OK，您可继续上传 7 天内" → 确认 → 关弹窗
- [ ] prompt 步骤：选择"是" → 弹选择框（5 选项 + 当前标注）
- [ ] 选择框：选 7 天（当前） → 弹"当前限制仍为 7 天" → 确认 → 回选择框
- [ ] 选择框：选 30 天 → 弹"确认将限制从 7 改为 30" → 确认 → 关弹窗 + store 更新 + 按钮文字变为"30 天"
- [ ] 选择框：选 30 天 → 弹"确认从 7 改为 30" → 取消 → 回选择框（值不变）
- [ ] 选择框：选 -1（不限制）→ 弹"确认从 7 改为 不限制" → 确认 → 关弹窗 + 按钮变为"不限制"

#### H. 预校验
- [ ] 默认 7 天：uploadAndParse 选择 7/13 截图 → 立即 error 提示（不发请求）
- [ ] 改 30 天：uploadAndParse 7/13 截图 → 正常上传 + 解析
- [ ] 改 -1：uploadAndParse 7/13 → 正常

### 端到端验收

#### I. 完整流程
1. [ ] 上传 7/13 截图，默认 7 天 → 报错
2. [ ] 改 30 天
3. [ ] 上传 7/13 截图 → 解析成功 → confirm 成功 → 列表显示 7/13
4. [ ] 后端重启 → GET 仍是 30 天
5. [ ] 改回 7 天 → 上传 7/13 截图 → 报错

#### J. 回归（PR3+ 已修复的功能不能坏）
- [ ] 解析 7/23 截图正常
- [ ] 解析后不报 null.fundCount
- [ ] confirm 成功 banner 显示
- [ ] 自动 setCurrent
- [ ] 预览弹窗日期可点击编辑

## 验收负责人

- 后端验收：开发者 + 用户复测
- 前端验收：开发者 + 用户复测
- 端到端：用户最终实测

## 通过标准

所有 A-I 勾选 ✓，J 回归无破坏 → PR3plus 通过
