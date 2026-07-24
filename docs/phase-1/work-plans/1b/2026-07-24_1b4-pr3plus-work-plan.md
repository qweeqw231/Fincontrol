l# PR3plus 实施计划

**日期**：2026-07-24
**PR 名**：1b.4-pr3plus（pr3plus）
**目标**：把"历史截图限制"从硬编码 7 天改为前端可配置，配套引入 `settings` 全局配置表
**决策**：[decision-30](../decisions/decision-30-no-hardcode-history-limit.md) + [decision-31](../decisions/decision-31-settings-global-config-table.md)

## 背景

PR3+（1b.4-pr3）修复后实测发现：
- 7/13 / 7/14 截图距 7/24 = 10-11 天，confirm 报 400（硬编码 7 天限制）
- 7/23 截图距 7/24 = 1 天，正常
- 用户合理需求：能上传真实历史数据

## 实施范围

### 后端 (5 个新文件 + 2 个改动)

1. **SQL 脚本** `fincontrol-backend/scripts/1b3/01-settings.sql` (新建)
   - `CREATE TABLE settings (...)`
   - `INSERT INTO settings (user_id=1, max_snapshot_age_days=7)` 初始化

2. **Entity** `fincontrol-backend/src/main/java/com/fincontrol/entity/Settings.java` (新建)
   - Lombok @Data + @NoArgsConstructor + @AllArgsConstructor
   - 字段：userId, maxSnapshotAgeDays, createdAt, updatedAt

3. **Mapper** `fincontrol-backend/src/main/java/com/fincontrol/mapper/SettingsMapper.java` (新建)
   - `Settings selectByUserId(Long userId)`
   - `int upsert(Settings settings)` — on duplicate key update

4. **Mapper XML** `fincontrol-backend/src/main/resources/mapper/SettingsMapper.xml` (新建)
   - 2 个 SQL：selectByUserId + upsert

5. **Service** `fincontrol-backend/src/main/java/com/fincontrol/service/SettingsService.java` (新建)
   - `int getMaxSnapshotAgeDays(Long userId)` — 查表，找不到返 7
   - `Settings setMaxSnapshotAgeDays(Long userId, int days)` — upsert + 校验 days ∈ {-1,7,14,30,180}

6. **Controller** `fincontrol-backend/src/main/java/com/fincontrol/controller/SettingsController.java` (新建)
   - `GET /api/settings/{userId}/max-snapshot-age-days` → `{maxSnapshotAgeDays: 7}`
   - `PUT /api/settings/{userId}/max-snapshot-age-days` body `{days: 7}` → `{userId, maxSnapshotAgeDays, updatedAt}`

7. **改 SnapShotConfirmService.java**
   - 注入 SettingsService
   - validateRequest 改：删硬编码 7 天，调 `settingsService.getMaxSnapshotAgeDays(req.getUserId())` 拿 maxAge
   - `if (daysDiff > maxAge && maxAge !== -1) throw ...`

### 前端 (1 个新 store 字段 + 1 个新组件 + 2 个改动)

1. **改 userConfigStore.js**
   - 加 `maxSnapshotAgeDays: 7` state（init from API）
   - 加 `loadingMaxAge: false` + `error: null`
   - 加 action `fetchMaxSnapshotAgeDays(userId)` 调 GET
   - 加 action `setMaxSnapshotAgeDays(userId, days)` 调 PUT
   - **不**用 localStorage（多用户预留）

2. **改 api/endpoints.js**
   - `SETTINGS_MAX_AGE_GET: (userId) => '/settings/${userId}/max-snapshot-age-days'`
   - `SETTINGS_MAX_AGE_PUT: (userId) => '/settings/${userId}/max-snapshot-age-days'`

3. **新建 HistoryLimitDialog.jsx** `fincontrol-frontend/src/components/data/HistoryLimitDialog.jsx`
   - 4 步状态机 (useState step)
   - step 'prompt' → 询问是否修改
   - step 'confirm-no-change' → "OK，您可继续上传 X 天内"
   - step 'select' → 5 选项 (7/14/30/180/-1)
   - step 'confirm-change' → "确认将限制从 OLD 改为 NEW 吗"
   - 复用 data-page.css 的 modal 样式

4. **改 DataPage.jsx**
   - 上传 section 加按钮：`当前历史截图限制：最近 {days} 天 [修改]`
   - days 读自 store，**绝不硬编码**
   - 打开 HistoryLimitDialog
   - uploadAndParse 前用 store.maxSnapshotAgeDays 预校验

### 文档 (4 个)

1. ✅ decision-30-no-hardcode-history-limit.md（已落盘）
2. ✅ decision-31-settings-global-config-table.md（已落盘）
3. 本文档 (work-plan)
4. acceptance-plan（落盘在 docs/test-records/manual-tests/1b/）

## 实施顺序（自下而上）

1. ✅ decision-30 + decision-31（已落盘）
2. **回滚** SnapShotConfirmService 7→180 改动 → 改读 settings
3. **新建** settings SQL + entity + mapper + service + controller
4. **改** SnapShotConfirmService
5. **改** userConfigStore + endpoints
6. **新建** HistoryLimitDialog
7. **改** DataPage
8. **写** acceptance-plan
9. **重启后端 + 浏览器验证**（用户测）
10. **commit pr3plus**

## 验收标准（详见 acceptance-plan）

- [ ] settings 表创建成功 + 用户 1 初始化为 7 天
- [ ] GET /api/settings/1/max-snapshot-age-days 返回 7
- [ ] PUT /api/settings/1/max-snapshot-age-days {days:30} 成功
- [ ] 上传 7/13 截图：默认 7 天限制 → 400 + 前端 message
- [ ] 改为 30 天 → 上传 7/13 → 200 ✓
- [ ] 4 步 modal 完整流程
- [ ] 按钮显示当前 days（从 store 读，不硬编码）
- [ ] 后端关闭再启动，配置仍然保留
