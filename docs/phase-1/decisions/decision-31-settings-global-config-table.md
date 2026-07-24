# 决策 31：引入 settings 全局配置表

**日期**：2026-07-24
**作者**：Cline（PR3plus 实施）
**关联**：决策 30（取消硬编码历史限制）

## 背景

项目目前**没有"全局配置"概念**。所有可调参数都散落在代码硬编码里（例：`SnapShotConfirmService` 中 `daysDiff > 7`）。

第一个需要配置的参数：`max_snapshot_age_days`（决策 30 配套）。

## 决策

**引入 `settings` 表**作为全局配置存储。

## 表结构

```sql
CREATE TABLE settings (
  user_id BIGINT PRIMARY KEY COMMENT '用户 ID（单用户 MVP 默认 1，多用户预留）',
  max_snapshot_age_days INT NOT NULL DEFAULT 7 COMMENT '历史限制天数；-1 = 不限制；其他有效值：7/14/30/180',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '全局配置表（PR3plus 决策 31）';
```

## 字段约束

- `user_id` 主键
- `max_snapshot_age_days` 枚举值：`{-1, 7, 14, 30, 180}`
  - `-1` = 不限制
  - `7/14/30/180` = 限定天数（对应前端可选项）

## API 设计

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/settings/{userId}/max-snapshot-age-days` | 读取某用户的 max_snapshot_age_days（找不到返 7） |
| PUT | `/api/settings/{userId}/max-snapshot-age-days` | 更新（upsert）某用户的 max_snapshot_age_days |

PUT 请求体：
```json
{ "days": 7 }
```

PUT 响应体：
```json
{ "userId": 1, "maxSnapshotAgeDays": 7, "updatedAt": "2026-07-24T18:30:00" }
```

## 实施细节

### 后端
- `Settings.java` (Entity, Lombok @Data)
- `SettingsMapper.java` + `SettingsMapper.xml`
- `SettingsService.java` (`getMaxSnapshotAgeDays(userId)`, `setMaxSnapshotAgeDays(userId, days)`)
- `SettingsController.java` (GET/PUT)
- `SnapShotConfirmService` 注入 `SettingsService`，validateRequest 改读 settings

### 前端
- `userConfigStore.js` 加 `maxSnapshotAgeDays` state + `setMaxSnapshotAgeDays(days)` action（**调 API**，不用 localStorage）
- `api/endpoints.js` 加 `SETTINGS_MAX_AGE(userId)` + `SETTINGS_MAX_AGE_UPDATE(userId)`
- `HistoryLimitDialog.jsx` (新建) — 4 步 modal
- `DataPage.jsx` 集成按钮 + 预校验

## 扩展性

未来可能加的配置项（举例）：
- `auto_set_current_after_confirm` (BOOLEAN, default true) — confirm 后自动设 is_current
- `monthly_dca_reminder_day` (INT, default 25) — 每月 DCA 提醒日
- `enable_ai_vision` (BOOLEAN, default true) — 是否启用 AI vision

`settings` 表预留 `key-value` 形式扩展（后续 PR 可加 `setting_key` + `setting_value` JSON 列），MVP 阶段先存单行结构化字段。

## 决策 30 配套

见 [decision-30-no-hardcode-history-limit.md](./decision-30-no-hardcode-history-limit.md)
