# 决策 30：取消硬编码历史限制

**日期**：2026-07-24
**作者**：Cline（PR3plus 实施）
**关联**：决策 31（settings 全局配置表）

## 背景

`SnapShotConfirmService.validateRequest()` 中硬编码 `daysDiff > 7` 拒绝超过 7 天的截图。

**问题**：
- 用户在 PR3plus（1b.4-pr3）实测中上传 7/13 / 7/14 截图，距 7/24 = 10-11 天，被 400 拒绝
- 但这些截图是**真实历史数据**，用户有合理需求上传更早的截图
- 硬编码 7 天无法满足个性化需求（有人想 14 天，有人想 30 天）

## 决策

**取消硬编码历史限制**。改为从 `settings` 表读 `max_snapshot_age_days`。

具体：
- 7 天硬编码 → 删除
- 校验时 `int maxAge = settingsService.getMaxSnapshotAgeDays(req.getUserId());`
- `if (daysDiff > maxAge) throw ...`
- 当 `maxAge === -1` 时表示不限制，永远不超

## 影响

- `SnapShotConfirmService.java` 校验逻辑需要注入 `SettingsService`
- 前端 `DataPage` 加 "修改历史限制" 按钮，弹 4 步 modal 修改值
- 前端 `userConfigStore` 改从 API 读 `maxSnapshotAgeDays`，去掉 localStorage 方案（多用户预留）

## 替代方案考虑

- **A. 仅前端控制**：前端校验，后端不限制 → 多用户时前端绕不过去，不安全
- **B. 后端放宽 180 天**（之前尝试过）→ 用户拒绝，要求"不得简化实现"
- **C. ✅ 引入 settings 表**：全局配置可扩展，未来其他参数也存这里

## 决策 31 配套

见 [decision-31-settings-global-config-table.md](./decision-31-settings-global-config-table.md)
