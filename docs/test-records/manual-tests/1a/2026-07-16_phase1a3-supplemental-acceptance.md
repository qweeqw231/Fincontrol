# Phase 1a.3 快照确认与事务补充性过程验收报告

**日期**：2026-07-16  
**测试者**：刘博丞  
**Phase**：1a.3（快照确认、三表写入、Dedup 集成、Controller、1a.8 撤销）  
**关联历史报告**：[2026-07-16_phase1a3-dedup-and-snapconfirm-acceptance.md](./2026-07-16_phase1a3-dedup-and-snapconfirm-acceptance.md)  
**当前结论**：🟡 **业务逻辑闭环通过；真实 MySQL 持久化与前后端端到端验收待统一测试**

> 本报告是前一份 1a.3 过程性报告的补充，不覆盖历史记录。当前 1a.3 已经可以进入后续 1a.4 开发，但不能把本轮 mock Mapper 测试等同于生产数据库验收。

---

## 1. 本轮验收目标

本轮补充验证前一份报告尚未覆盖的部分：

1. `1a.3.3` 快照确认 Controller 与 `1a.8` 10 秒撤销服务已接入；
2. `1a.3.4` confirm + rollback 业务测试是否稳定通过；
3. Mockito 测试替身是否正确覆盖 Dedup、三表写入编排、镜像校验和回滚时间窗口；
4. MyBatis Mapper 重复加载告警是否已消除；
5. 诚实区分当前已经通过的业务层 DoD 与需要留到前后端完成后的真实库验收项。

---

## 2. 验收环境与证据

| 项目 | 内容 |
|---|---|
| JDK | Java 17.0.12 |
| Maven | 3.9.x |
| Spring Boot | 3.3.5 |
| MyBatis-Plus | 3.5.9 |
| 测试数据库 | H2 内存库，Spring `test` profile |
| 测试类 | `com.fincontrol.it.SnapShotConfirmServiceIT` |
| 最新本地 commit | `0fb676c fix(1a.3.4): stabilize snapshot confirm tests` |
| 远端状态 | 本地 commit 已创建；GitHub push 因 `github.com:443` 连接重置未成功 |

### 2.1 执行命令

```cmd
mvn -f C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\pom.xml -B clean test -Dtest=SnapShotConfirmServiceIT
```

随后又执行了不带 `clean` 的复验：

```cmd
mvn -f C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\pom.xml -B test -Dtest=SnapShotConfirmServiceIT
```

### 2.2 实际结果

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

两次执行均为 **6/6 PASS**。

---

## 3. 通过的 6 个测试场景

| # | 测试方法 | 验证内容 | 结果 |
|---|---|---|---|
| 1 | `confirm_normal2Images_dedup3Tables_mirrorCheckPass` | 两张图 dedup 后写入 3 条 raw、2 条 snapshot、3 条 category map，并通过镜像校验 | ✅ PASS |
| 2 | `confirm_singleImage_dedupWrites3Tables` | 单图确认写入与镜像校验 | ✅ PASS |
| 3 | `confirm_existingSameDayWithoutOverwrite_returnsWarnings` | 同日已有数据且未确认覆盖时返回 warning，不写三表 | ✅ PASS |
| 4 | `confirm_existingSameDayWithOverwrite_writesAndReplaces` | confirmedOverwrite=true 后继续写库编排 | ✅ PASS |
| 5 | `confirm_sameFundAcrossCategories_throws` | 同 fund_name 跨大类冲突抛 `INTERNAL_ERROR`，不发生写入 | ✅ PASS |
| 6 | `rollback_within10s_flipsIsLatest` | 10 秒窗口内调用 raw/snapshot 的 is_latest 翻转 | ✅ PASS |

启动期间原先出现的以下 Mapper 重复注册日志已不再出现：

```text
mapper[...] is ignored, because it exists, maybe from xml file
```

原因是删除了 `mybatis-config.xml` 中与 `mybatis-plus.mapper-locations` 重复的 `<mappers>` 声明，保留单一 XML 加载入口。

---

## 4. 本轮修复内容

### 4.1 Mockito 替身配置

- 原测试把 `DedupEngine` 注入为 `@Autowired` 实例，却使用 `doReturn(...).when(dedupEngine)`，导致 `NotAMockException`。
- 改为 `@SpyBean`：正常场景继续使用真实 DedupEngine，warning/冲突场景只做局部 stub。
- 删除了对真实 `DedupResult` record 的链式 `when(dedupResult.report().warnings())`；warning 直接放入 fixture 的 `DedupReport`。

### 4.2 镜像校验 fixture

为 Mapper mock 配置了与 dedup 结果一致的：

- raw fund_name 集合；
- fund-category map fund_name 集合；
- 每个 category 的 raw amount 汇总；
- `is_latest=true` 行数；
- insert/upsert 的影响行数。

同时修正了正常两图场景的实际统计：输入是 1 只基金 + 2 只基金，因此 raw/map 是 3 条、snapshot 是 2 条，不是旧断言中的 5 条和 3 条。

### 4.3 rollback fixture

给回滚用的 `AssetSnapshot` 设置了 `createdAt=LocalDateTime.now()`，避免服务计算 10 秒 deadline 时因 null 产生 NPE。

### 4.4 MyBatis 配置

`mybatis-plus.mapper-locations` 已作为唯一 Mapper XML 加载入口；`mybatis-config.xml` 只保留全局 settings，避免同一 statement 被 XML 重复注册。

---

## 5. 1a.3 当前 DoD 状态

| 验收项 | 当前状态 | 说明 |
|---|---|---|
| DedupEngine 5 维与 confirm 集成 | ✅ 业务通过 | A/C/D/E 已验证；B SHA256 仍是 1a.4 预留项 |
| `@Transactional` 三表写入编排 | ✅ 业务层通过 | Service 已使用事务入口，6 个测试验证写入顺序/阻断逻辑 |
| 同日覆盖 warning | ✅ 通过 | 未确认时不写库，确认后继续写入编排 |
| fund 跨 category 冲突 | ✅ 通过 | 返回 `INTERNAL_ERROR`，写入未发生 |
| 镜像校验 | ✅ 业务层通过 | fund 集合、category sum、latest count 均已验证 |
| 日期相差超过 7 天拒绝 | 🟡 已实现，专项验收待补 | Service 有日期校验，但本轮 6 个测试未覆盖该错误路径 |
| 10 秒内撤销 | ✅ 业务层通过 | rollback 测试通过；真实表状态待数据库验收 |
| 超时撤销返回 410 | 🟡 代码路径存在，专项验收待补 | 需要补充过期和找不到 snapshot 的 HTTP/API 测试 |
| ignored=true 过滤 | ⏳ 待确认 | 应在前后端确认面板联调时验证，不能因当前 6/6 就提前标记完成 |
| 真实 MySQL 三表写入与回滚 | ⏳ PENDING | 本轮 Mapper 全部 mock，未执行 XML upsert |
| 前端上传→解析→确认→首页查询 | ⏳ PENDING | 等 1a.4 查询 API 与前端完成后统一验收 |

---

## 6. 当前已知遗留问题与风险

### 6.1 真实 MySQL upsert 尚未验证（重要）

当前 Mapper XML 使用了 PostgreSQL 风格的：

```sql
ON CONFLICT (...) DO UPDATE
```

本轮测试没有执行该 SQL，因为 Mapper 被 `@MockBean` 替换。MySQL 8 常用的是 `ON DUPLICATE KEY UPDATE`，因此在真实 MySQL 8 验收前必须确认并修正 MySQL/H2 双方言方案。该问题不阻塞本轮业务逻辑测试，但会阻塞最终生产持久化验收。

### 6.2 测试启动仍有 PromptLoader warning

H2 测试 schema 当前只覆盖 1a.3 的三张业务表，没有 `prompt_versions` 表，因此启动时会出现预热查询 warning。`PromptLoaderService` 已捕获异常并使用运行时 DB 查询兜底，不影响 6/6 测试；后续可补充测试表或在 test profile 关闭预热。

### 6.3 测试 fixture 日期写死

当前测试使用 `2026-07-16`。服务有“日期距离当前不得超过 7 天”的业务校验，因此未来长期复跑时应改为可控 Clock 或动态测试日期。

### 6.4 真实事务边界仍需最终验证

本轮验证的是 Service 业务编排和 Mockito 调用，不是数据库事务提交/回滚后的实际行状态。真实 MySQL 验收时应故意让中间步骤失败，确认三表不会出现部分提交。

---

## 7. 阶段结论

### 7.1 可确认的结论

> **1a.3 business acceptance：PASS。**
>
> 快照确认的 dedup、覆盖提示、冲突阻断、三表写入编排、镜像校验和 10 秒撤销业务路径已形成闭环，`SnapShotConfirmServiceIT` 6/6 PASS。

### 7.2 暂不宣称的结论

> **1a.3 production persistence acceptance：PENDING。**
>
> 真实 MySQL upsert 方言、事务回滚后的实际数据状态、HTTP 端到端和前端首页查询尚未统一验证。按照当前计划，这些内容留到前端完成后做统一冒烟测试。

因此，1a.3 可以作为**业务层已通过**进入整体子阶段 1a.4；Phase 1a 总体仍未完成。

---

## 8. 下一步

按当前项目计划，下一步进入整体子阶段 **1a.4 快照查询 + 首页辅助 API**：

- `GET /api/snapshot/latest`
- `GET /api/snapshot/latest/detail`
- `GET /api/snapshot/{date}`
- `GET /api/snapshot/history`
- `GET /api/asset/balance`
- `GET /api/asset/operations/recent`
- `GET /api/asset/cumulative-return`

生产数据库验证暂不阻塞 1a.4；但在“前端上传图片 → 解析 → 确认入库 → 首页查询”统一冒烟前，必须完成 MySQL 方言与真实三表状态验证。
