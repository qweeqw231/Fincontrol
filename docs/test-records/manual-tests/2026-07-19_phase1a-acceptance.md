# Phase 1a 整体验收报告（2026-07-19）

> **状态**：✅ **整体验收通过**（mvn test 238/238 + 路径 A 真实 E2E 通过 + 路径 B 部分完成）
> **报告类型**：1a.1–1a.10 全切片整体验收
> **配套文档**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)（实时清单）+ [`decisions.md`](../../phase-0/decisions.md)（决策 1–12）
> **真实 E2E 报告**：[`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 报告版本 | v1.0（Phase 1a 收尾终版） |
| 编写日期 | 2026-07-19 22:55（Asia/Shanghai） |
| 编写者 | Cline（按用户要求 Phase 1a 收尾确认） |
| 文档类型 | 整体验收（5 段式 + 路径 A/B 独立判定） |
| 配套工作 | [`2026-07-19_phase1a10-work-plan.md`](../../phase-1/work-plans/2026-07-19_phase1a10-work-plan.md) |

---

## 1. Phase 1a 范围（10 个子切片 + GATE 0）

| 切片 | 内容 | 状态 |
|------|------|------|
| 1a.1 | Spring Boot 项目可启动 + HikariCP pool | ✅ |
| 1a.2 | 截图上传 / 解析 / ReParse API 链 | ✅ |
| 1a.3 | 快照 confirm + rollback 三表事务 | ✅ |
| 1a.4 | 快照查询 + 首页辅助 + 大类映射 + AI 顾问 + 解析日志（19 API） | ✅ |
| 1a.5 | 视觉模型切到 minimax 多模态 | ✅ |
| 1a.6 | 文本模型主切到 minimax | ✅ |
| 1a.7 | 真实 MySQL 持久化 + dialect 修复 | ✅ |
| 1a.8 | 双 provider 路由 + Caffeine cache + resilience4j（架构就绪） | ✅ |
| 1a.9 | 总资产双轨 + DISCREPANCY 1% 报警 | ✅ |
| 1a.10 | 双路径并存（4×单图 + 1×confirm / 1×parse-batch） | ✅ |
| 1a.24 | JaCoCo 覆盖率 ≥ 60%（实际 ~77%） | ✅ |

---

## 2. 真实 ground truth（用户 14:42 确认）

- 支付宝总资产：**7,884.68 元**
- 六大类合计：7,563.83 元；余额类 320.85 元
- 唯一基金 **19 只**（6+3+5+6 = 20 完整行 → 1 个重复 → 19 unique）
- 余额宝 holding=NULL、cumulative=1.89
- 国泰黄金 ETF 联接 C 保留差异：holding=-45.25、cumulative=-40.24
- 机器 canonical「港股大中华类」；展示名「港股/大中华类」
- P1/P3/P4 top=null；P2 top=7884.68（唯一非空）

---

## 3. 顶部总资产三级判定（路径 A 与路径 B 共用）

1. 任意页面读到「总金额」或「总资产」字样 + 数字 → 记为该页 `top`
2. 4 页 `top` 一致 → `totalAsset=top`、`totalAssetSource="top"`
3. 4 页 `top` 不一致 → 报警 `TOP_INCONSISTENT`、`totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`
4. 4 页 `top` 全 null → `totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`、**无报警**

---

## 4. 5 段式验收（路径 A / 路径 B 独立判定）

### 4.1 路径 A（4×单图 + 1×confirm）

| 段 | 结果 | 证据 |
|---|------|------|
| **BUSINESS** | ✅ 6/6 + 多 1a.8.7/1a.8.8/1a.9/1a.10 增量 | `SnapShotConfirmServiceIT` + `Phase1a8RealFourPageFixtureTest` |
| **CONTRACT** | ✅ 现有 API 行为不变 | 1a.7 契约沿用 |
| **READ_SQL** | ✅ user 19999：19 raw + 7 snapshot + 19 map | 路径 A 真实 confirm 后 SELECT 镜像一致 |
| **PRODUCTION** | ✅ 真实 4/4 confirm 跑通 | rawInserted=19、snapshotUpserted=7、dedupInput=20、dedupMerged=19、dedupDropped=1 |
| **COVERAGE** | ✅ JaCoCo ≥ 60% | 实际 76.5%（1a.7 验收时） / ~77%（1a.10 回归） |

**本轮重测结果（2026-07-19 22:37）**：

| 页面 | code | fundCount | fundSum | top | topSource |
|------|------|-----------|---------|-----|-----------|
| P1 | 0 | 6 | 2954.91 | (未读) | — |
| P2 | 0 | 3 | 2987.32 | 7884.68 | — |
| P3 | 0 | 5 | 605.20 | (未读) | — |
| P4 | 0 | 6 | 1493.73 | (未读) | — |
| **总和** | 0/0/0/0 | **20** | **8041.16** | 7884.68 (P2) | — |

**confirm 真实结果**：
```
user 19999, assetRawInserted=19, assetSnapshotUpserted=7,
dedupInput=20, dedupMerged=19, dedupDropped=1
```

→ 5 段式 **全部 PASS**，与清理死代码前的真实数据完全一致。

### 4.2 路径 B（1×parse-batch 4 图）

| 段 | 结果 | 证据 |
|---|------|------|
| **BUSINESS** | ✅ 多 1a.10 单元 + 集成用例 PASS | `AiRouterTest` + `VisionModelClientTest` 4 图 + `ScreenshotServiceTest` batch |
| **CONTRACT** | ✅ `parse-batch` Request/Response DTO 已落定 | `ScreenshotBatchParseRequest/Response` |
| **READ_SQL** | ✅ 与路径 A 共享（user 29999 + 上传 fileId） | 上传 4 个 fileId 成功 |
| **PRODUCTION** | ⚠️ **minimax 4 图 batch 偶发 3002 timeout** | 重试 2 次均 timeout（300s 限制），豆包 fallback 不可用（决策 12） |
| **COVERAGE** | ✅ JaCoCo ≥ 60% | 同路径 A |

**本轮重测结果（2026-07-19 22:47 + 23:03 两次）**：

| 尝试 | code | message | 是否 fallback |
|------|------|---------|------|
| 第一次 | 3002 | minimax OpenAI chat completions 调用超时: timeout | 否（豆包账户不可用） |
| 第二次 | 3002 | minimax OpenAI chat completions 调用超时: timeout | 否（豆包账户不可用） |

→ 4 段 PASS，**PRODUCTION 段诚实标 `PRODUCTION_BLOCKED`**：与 1a.10 验收时一致（已记录于 [`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md) §9.5b/§9.6），与本次清理死代码无关。

**根因**：minimax 对 4 张大图单次请求响应时间超过 300s（5 min）限制；豆包 4 个 model 全部失败（决策 12 暂废）。建议 Phase 2+ 优化：流式上传 / 并发 4 次单图 / 切到其他 vision provider。

---

## 5. 关键决策摘要（涉及 Phase 1a 验收）

| # | 决策 | 影响 |
|---|------|------|
| 1 | REST API 契约 | 30+ 端点锁定 |
| 2 | target_ratio 同步 = 解读 4 | 1a.4 写入路径 |
| 3 | profit 字段读取 = 首页明细表 | 1a.4 detail API |
| 4 | 累计收益率卡片 Phase 1 隐藏 | 1a.4 cumulative-return 返回 `available:false` |
| 5 | Phase 1 验收标准追加 P0 | 1a.7 / 1a.8 / 1a.9 覆盖 |
| 6 | Phase 计划修订 | 1a 拆分 a/b/c 切片 |
| 7 | profit 拆分 holding + cumulative（1a.8.7） | 1a.8 schema +2 列 |
| 8 | 基金类别归一化 + 双向 cache + 清仓可恢复（1a.8.8） | 7 canonical + CategoryEnum + FundCategoryResolver |
| 9 | 总资产双轨 + DISCREPANCY 1% 报警（1a.9） | 顶部 vs visible sum 决策 + 1% 报警 |
| 10 | 双路径并存（1a.10） | 路径 A（4×单图+confirm）/ 路径 B（1×parse-batch） |
| 11 | 4 页真实数据 mirror（1a.10 验证） | 19 raw / 7 snapshot / 19 map / top=7884.68 / 偏差 0 |
| 12 | 豆包 vision 路径暂时废弃（1a.10 收尾） | Phase 1a 按 minimax-only 验收通过；代码完整保留 |

---

## 6. 1a.11+ 遗留债（按优先级）

1. **豆包 vision 重新评估**（决策 12）：联系 ARK 客服开通 vision 权限 / 评估其他 vision provider（Azure Computer Vision / 阿里云视觉智能 / 腾讯云图像识别）
2. **parse-batch 4 图优化**（路径 B PRODUCTION_BLOCKED）：流式上传 / 并发 4 次单图 / 切到更快的 vision provider
3. **category_master CRUD**（1a.10 新增 API，但 master table 增删改 UI 1b.x 才有）
4. **multi-user RBAC 完整版**（1a.5+ 多次暴露，1b.x / Phase 5b 范畴）
5. **prompt_versions v2.7+** 升级（1a.9 增量）：单图 vs 多图输出 schema 统一

---

## 7. Phase 1b 启动条件检查

| 条件 | 状态 |
|------|------|
| 后端 24 个 API 全部实现 | ✅ 1a.1–1a.23 + 1a.24 |
| 8 项 P0 全部达成 | ✅ |
| 2 条冒烟测试通过 | ✅ |
| 单元测试覆盖率 ≥ 60% | ✅ 实际 ~77% |
| Swagger UI 全部 API 可访问 | ✅ |
| 真实 MySQL 持久化闭环 | ✅ 路径 A 19/7/19 镜像 |
| 真实 confirm 跑通 | ✅ top=7884.68、偏差 0 |
| **路径 A 真实 E2E** | ✅ 本轮重测 4/4 + confirm 全对 |
| **路径 B 真实 E2E** | ⚠️ minimax 4 图 batch 3002 timeout（PRODUCTION_BLOCKED 已知） |

→ **Phase 1a 验收通过，可启动 Phase 1b 前端对接**。路径 B 的 4 图 batch 失败属 minimax 上游问题，不阻塞 Phase 1a 收尾，但需在 Phase 2 重新评估。

---

## 8. 本次清理（1a.10 follow-up）

### 8.1 动机

`fincontrol.vision.*` 段在 application.yml + application-local.yml + FincontrolApplicationTests.java 三处存在，1a.8 重构后已无代码引用（grep 0 处）。作为 1a 收尾清理项，删除这三处死代码以避免误导后续开发者。

### 8.2 修改

| 文件 | 改动 | 入仓 commit |
|------|------|-------------|
| `application.yml` | 删除 lines 123-134（vision 段 + 1a.8 兼容注释） | `af81f1f` |
| `application-local.yml` | 删除 lines 29-31（"1a.7 兼容段：VisionModelClient @Value 注入使用"） | (gitignored) |
| `FincontrolApplicationTests.java` | 删除 `@TestPropertySource` 中 `"fincontrol.vision.api-key=placeholder-for-test-only"` | `af81f1f` |

### 8.3 验证

- `mvn clean test`：**238/238 PASS**（含 H2 集成 + 单测）
- 路径 A 真实 E2E：4/4 upload+parse + confirm → 19 raw / 7 snapshot / 19 map / top=7884.68 / 偏差 0
- 路径 B 真实 E2E：1×parse-batch 4 图 → 3002 timeout（与清理无关，minimax 4 图 batch 已知问题）

---

## 9. 关联文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| Phase 1a 实时清单 | [`phase-1a.md`](../../phase-1/checklists/phase-1a.md) | 24+ API / 8 P0 / 2 冒烟 + 1a.8/1a.9/1a.10 增量 |
| Phase 0 决策 | [`decisions.md`](../../phase-0/decisions.md) | 决策 1–12 全集 |
| API 契约 | [`api-contract.md`](../../phase-0/api-contract.md) | 30+ 端点 |
| 1a.8 验收 | [`2026-07-18_phase1a8-acceptance-report.md`](2026-07-18_phase1a8-acceptance-report.md) | 方案 C + 双 provider 路由 |
| 1a.8 v3.2 真实数据 | [`2026-07-18_phase1a8-v3_2-real-data-check.md`](2026-07-18_phase1a8-v3_2-real-data-check.md) | 220/220 PASS + 类别归一化 |
| 1a.9 真实数据 | [`2026-07-18_phase1a8-v3_3-real-data-check.md`](2026-07-18_phase1a8-v3_3-real-data-check.md) | 225/225 PASS + DISCREPANCY 1% |
| 1a.10 真实 E2E | [`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md) | §9.5b/§9.6 minimax 4 图 batch 失败明细 |
| 1a.10 验收计划 | [`2026-07-19_phase1a10-acceptance-plan.md`](2026-07-19_phase1a10-acceptance-plan.md) | 5 段式验收设计 |
| 1a.10 工作计划 | [`2026-07-19_phase1a10-work-plan.md`](../../phase-1/work-plans/2026-07-19_phase1a10-work-plan.md) | 双路径切片 |

---

**Phase 1a 状态**：✅ **整体收尾完成，可进入 Phase 1b 前端对接**

*完成日期：2026-07-19 23:00（Asia/Shanghai）*
