# 1b4pr6b 模块 B · 验收计划

> **会话时点**：2026-07-25 00:57
> **范围**：C 选项 · P0 + P1 + P2 + R5 全做
> **决策依据**：`docs/phase-1/decisions/decision-33-pr6b-category-ux.md`（v2）
> **执行后输出**：`docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`

---

## 一、验收总览（V1-V18）

| 类别 | 编号范围 | 用例数 | 状态 |
|---|---|---|---|
| 前端 DataPage.test.jsx | V1 | 22 | ⏳ |
| 后端 SnapShotConfirmServiceP7Test | V2 | 8 | ⏳ |
| 联调端到端（前端 + 后端） | V3 | 7 | ⏳ |
| 双重计入修复 | V4 | 4 | ⏳ |
| 消失-重现机制（R5 端到端） | V5 | 4 | ⏳ |
| **主页 固收类 缺失 bug 修复（1b4pr6b-recovery）** | **V6** | **8** | ⏳ |
| **合计** | V1-V6 | **53** | ⏳ |

---

## 二、V1 · 前端 DataPage.test.jsx 验收（22 用例 · T1-T22）

### V1.1 决策 33 v1 基础（19 用例 · T1-T19）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| T1 | 渲染基金明细表，dropdown 默认 = AI 原猜 | `category-select-鹏华纯债债券D`.value === 'A股权益类' | ☐ |
| T2 | dropdown 选项 = 7 canonical + 余额类 | `queryAllByRole('option').length === 8` | ☐ |
| T3 | 未表态 badge = 🤖 ai_guess + 黄背景 | `getByTestId('status-guess')` 存在 + 行 className 含 `fund-row-pending` | ☐ |
| T4 | 改 dropdown 不点 ✓ 不调 API | `spy(apiClient.post).calls.length === 0` | ☐ |
| T5 | 改 dropdown → 「确认入库」仍禁用（D1） | 按钮 disabled，title 含「还有 N 条 AI 猜测未确认」 | ☐ |
| T6 | 「确认入库」+ pendingCount=0 → 启用 | 按钮 disabled === false | ☐ |
| T7 | 点 ✓ 调 update 1 次 + 正确 body | spy 计数 === 1，url 含 `/category-map/update`，body === {fundName, category} | ☐ |
| T8 | ✓ 后 badge ✅ + 行绿 + 按钮变 ↺ | `getByTestId('status-verified')` + `reset-btn-${fund}` | ☐ |
| T9 | ↺ 调 reset 1 次 | spy 计数 === 1，url 含 `/category-map/reset` | ☐ |
| T10 | ↺ 后 badge 🤖 + dropdown 归位 | `getByTestId('status-guess')` 重新出现 | ☐ |
| T11 | 改 dropdown 后各类小计实时联动（D3） | 鹏华 a股→固收：固收 +435.86，a股 -435.86 | ☐ |
| T12 | ✓ 期间按钮 loading + 禁用 | `confirm-btn` disabled + text === '提交中…' | ☐ |
| T13 | update 失败 → setError + badge 保持原状 | mock reject → setError 被调 | ☐ |
| T14 | modal 关闭重开 overrides 重新加载（D6） | unmount → 重 mount → state 清空 → 重新调 match | ☐ |
| T15 | D2 二次确认：dirtyCount=0 不弹 | `showSubmitDirtyModal === false` | ☐ |
| T16 | D2 二次确认：dirtyCount>0 弹 modal | `getByText('还有 N 行 dropdown 已改动但未 ✓')` | ☐ |
| T17 | D2 [全部提交] → 调 update N 次 + confirm | spy 计数 === dirtyCount + 1 | ☐ |
| T18 | D2 [仅提交已 ✓ 的] → 清 dirty + confirm | dirtyCount 变 0 | ☐ |
| T19 | D2 [返回修改] → 关闭二次确认 modal | `showSubmitDirtyModal === false` | ☐ |

### V1.2 决策 33 v2 新增（3 用例 · T20-T22）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| **T20** | **D5：mock match API 返回 user_correct → dropdown 默认值 = user_correct.category，badge = ✅，不计入 pendingCount** | match 返回 `{fundName: '鹏华纯债债券D', category: '固收类', source: 'user_correct'}` → dropdown.value === '固收类'，badge === ✅，pendingCount 不变 | ☐ |
| **T21** | **R1 安信新价值用例：match 返回 user_correct = A 股权益类 → dropdown 默认 A 股 → 用户改 dropdown = 固收 → 点 ✓ → update 调用 → 再次 confirm → DedupEngine 采纳 user_correct = 固收** | match 返回安信 user_correct=A 股权益类 → dropdown 默认 A 股 → 改固收 → ✓ → update body === {fundName: '安信...', category: '固收类'} → 再次 confirm → asset_raw 安信 category === '固收类' | ☐ |
| **T22** | **D7：match 返回 first_missing_snapshot_date 非 NULL → 黄色 banner 渲染** | match 返回 `{fundName: '天弘纳指100', firstMissingSnapshotDate: '2026-07-20', lastSeenSnapshotDate: '2026-07-18'}` → 行内有 `.modal-warning-banner.re-confirm` 且包含「您可能于 2026-07-20 及之前清仓」 | ☐ |

---

## 三、V2 · 后端单测验收（8 用例 · B1-B8）

### V2.1 决策 33 v1 基础（3 用例 · B1-B3）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| B1 | D4-R1：writeAssetSnapshot 前调 updateIsLatestBySnapshotDate | mock `assetSnapshotMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)` 被调 1 次 | ✅（v1 已绿） |
| B2 | D4-R2：两表对称 | verify(assetRawMapper) + verify(assetSnapshotMapper) 各 1 次 | ✅（v1 已绿） |
| B3 | 决策 32 R6：user_correct 采纳（已有） | 鹏华 user_correct=固收 + AI a股 → dedup 后 categoryName=固收 | ✅（已有） |

### V2.2 决策 33 v2 新增（5 用例 · B4-B8）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| **B4** | **R5-1：confirm snapshotDate=锚点 → 标记 first_missing_snapshot_date** | fund_category_map 已存 G@last_seen=D1 → confirm snapshotDate=D2 > D1，G 不在 parsedAssets → G.first_missing_snapshot_date === D2 | ☐ |
| **B5** | **R5-2：confirm snapshotDate=锚点 + G 重现 → 清 first_missing_snapshot_date + pendingReConfirms** | G@first_missing=D2 → confirm snapshotDate=D3 > D2，G 在 parsedAssets → G.first_missing_snapshot_date === null + pendingReConfirms 含 G | ☐ |
| **B6** | **R5-3：回填 snapshotDate < 锚点 → 仅入库，不修改 last_seen / first_missing** | 锚点 D4，回填 D2 < D4 → G.last_seen_snapshot_date 保持 D4 不变，G.first_missing_snapshot_date 保持 NULL 不变 | ☐ |
| **B7** | **R5-4：锚点计算（anchorDate = MAX(last_seen_snapshot_date)）** | fund_category_map 有 3 条 last_seen ∈ {D1, D2, D3} → anchorDate === D3；空表 → anchorDate === null | ☐ |
| **B8** | **R5-5：writeAssetSnapshot + R5 顺序正确** | confirm 流程：先 writeAssetRaw（D4 清理）→ 再 writeAssetSnapshot（D4 清理 v1）→ 再 writeFundCategoryMap → 最后 R5 锚点更新 | ☐ |

---

## 四、V3 · 联调端到端验收（7 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| V3.1 | 上传 4 张截图 → preview modal 出现 19 行 | DataPage 解析 → preview modal 显示 19 只基金 + dropdown + 状态徽章 + 操作 | ☐ |
| V3.2 | **R1 端到端**：安信 match 返回 user_correct=A 股 → dropdown 默认 A 股 → 改 dropdown=固收 → ✓ → 再 confirm → asset_raw 安信 category=固收 | curl `/api/snapshot/latest/detail` → 安信在固收类 categoryName 下 + amount === 299.88 | ☐ |
| V3.3 | **D5 端到端**：match 返回 user_correct → dropdown 自动套用 + badge ✅ + 不阻塞 | preview modal 打开后，已有 user_correct 的基金 badge=✅ + 「确认入库」按钮未 disabled | ☐ |
| V3.4 | **D7 端到端**：清仓 → 重现 → 黄色 banner | 模拟 G@last_seen=D1, first_missing=D2 → 上传 D3（含 G）→ preview modal 显示黄色 banner + ✓ 按钮亮起 | ☐ |
| V3.5 | **D6 端到端**：退出 modal → 重新进入 → 重新调 match → 重置 | DataPage 卸载 → 重 mount → categoryOverrides 全空 → 重新调 match API → 重新填充 | ☐ |
| V3.6 | **D1+D2 端到端**：严格阻塞 + 二次确认 modal | 改 dropdown 不点 ✓ → 「确认入库」禁用 + tooltip 提示 + dirtyCount>0 触发二次确认 modal | ☐ |
| V3.7 | **D3 端到端**：实时联动各类小计 | 改 dropdown → 各类小计立即刷新（不需点 ✓） | ☐ |

---

## 五、V4 · 双重计入修复验收（4 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| V4.1 | 重跑 confirm → 旧 asset_snapshot[固收类, 890.58] 被清掉 | `SELECT * FROM asset_snapshot WHERE user_id=1 AND snapshot_date='2026-07-23' AND category='固收类' AND is_latest=true` 返 0 行（或 total=0） | ☐ |
| V4.2 | A 股权益类 total = 2202.27（不含鹏华/长城） | curl latest/detail → A股 total === 2202.27，fundCount === 6 | ☐ |
| V4.3 | sixCategoriesTotal = 6793.97（-890.58） | curl latest/detail → sixCategoriesTotal === 6793.97 | ☐ |
| V4.4 | totalAssetWithBalance = 6962.91 | curl latest/detail → totalAssetWithBalance === 6962.91 | ☐ |

---

## 六、V5 · 消失-重现机制（R5 端到端 · 4 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| V5.1 | 上传 D1（含 G）→ G last_seen=D1 | `SELECT last_seen_snapshot_date FROM fund_category_map WHERE fund_name='G' AND user_id=1` === D1 | ☐ |
| V5.2 | 上传 D2 > D1（无 G）→ G first_missing=D2 + banner 预告（preview 之前 curl 查 DB） | `SELECT first_missing_snapshot_date FROM fund_category_map WHERE fund_name='G' AND user_id=1` === D2 | ☐ |
| V5.3 | 上传 D3 > D2（含 G）→ G first_missing 清空 + pendingReConfirm + 黄色 banner 提示 + 用户 ✓ → first_missing 保持清空 | `SELECT first_missing_snapshot_date FROM fund_category_map WHERE fund_name='G' AND user_id=1` === NULL | ☐ |
| V5.4 | 回填 D_old < D1 → 不修改任何字段，仅入库 | 上传 D_old → fund_category_map 中所有字段不变（last_seen / first_missing 保持） | ☐ |

---

## 七、验收执行流程

### 7.1 准备阶段
1. 重启 backend（`scripts/1b/restart-backend.ps1`）
2. 重启 frontend（如已停）
3. 验证后端健康：`curl http://localhost:8080/actuator/health` → HTTP 200

### 7.2 执行阶段
1. 跑后端测试：`mvn -f fincontrol-backend/pom.xml test` → B1-B8 全绿
2. 跑前端测试：`cd fincontrol-frontend && npm test` → T1-T22 全绿
3. 联调（V3-V5）：实际操作 + curl 验证

### 7.3 报告阶段
1. 填写本验收计划中所有 ☐ → ✅ 或 ❌
2. 输出 `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`
3. 过程报告：每个 Step 完成后写 `test/1b/2026-07-25-r5-消失重现测试报告.md` 和 `test/1b/2026-07-25-前端测试报告.md`

---

## 八、验收签字

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 开发 | （自动） | 2026-07-25 | ☐ |
| 测试 | （手动） | 2026-07-25 | ☐ |
| 用户 | 用户 | 2026-07-25 | ☐ |

---

## 九、与决策 33 v2 的一致性确认

- ✅ D1 严格阻塞 → T5 / T6 / V3.6
- ✅ D2 警告 + 让用户选 → T15-T19 / V3.6
- ✅ D3 实时联动 → T11 / V3.7
- ✅ D4 双重计入修复 → B1-B3 / V4.1-V4.4
- ✅ D5 user_correct 自动套用 → T20 / V3.3
- ✅ D6 退出 reload → T14 / V3.5
- ✅ D7 消失-重现特别提示 → T22 / V3.4 / B4-B8 / V5.1-V5.4
- ✅ R1 安信新价值用例 → T21 / V3.2

---

## 十、待用户最终验收

- [ ] 全部 V1-V5 验证通过后，由用户在 final_report 或 GitHub issue 中确认
- [ ] 决策 33 v2 标记为「✅ 已实施」（v1 标记为「v1 · 已实施」）
- [ ] 工作计划 `2026-07-25_1b4-pr6b-work-plan.md` 标记为「✅ 已完成」
- [ ] 联调记录 `2026-07-25-1b4-pr6b-联调记录.md` 最终版落盘

---

## 十一、V6 · 主页 固收类 缺失 bug 修复验收（8 用例 · 1b4pr6b-recovery）

> **背景**：本章为 pr6b 验收阶段收尾。主页固收类金额从余额汇总中「消失」导致总额错位 1,189.92。本章验收标准是用户场景验证：「重跑后端 + 重启前端 + curl + 浏览器」四项同步验证。这与六件折中类错列（V4）是同源的，皆属于 **Fix D 不完整** 的违綤。

### V6.1 后端 SnapshotQueryService 修改验收（2 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| V6.1.1 | **buildLatestResponse()不再只迭代 original snapshots** | curl `/api/snapshot/latest?includeDetail=true` 返回 categories 数组包含「固收类」entry 且 categoryTotal == 1189.92 | ☐ |
| V6.1.2 | **buildByDateResponse() 同上** | curl `/api/snapshot/by-date/2026-07-24?includeBalance=true` 返回 categories 含「固收类」entry | ☐ |

### V6.2 后端连续性验收（2 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| V6.2.1 | **sixCategoriesTotal 包含固收类后总额与 effective 集合一致** | curl latest → sixCategoriesTotal == 7623.14且 totalAssetWithBalance == 7764.08、balanceFund == 140.94 | ☐ |
| V6.2.2 | **buildHistoryItem 的 categoryCount 能反咉 effective 集合** | curl `/api/snapshot/history?includeBalance=true` → 当前快照的 categoryCount == 7（含余额类） | ☐ |

### V6.3 前端 HomePage 优先信后端总额验收（2 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| V6.3.1 | **HomePage 的 `sixTotal` 优先使用 snap.sixCategoriesTotal** | `sixTotal = safeNumber(snap.sixCategoriesTotal, 0) || sumSixTotal(categories)`（防后端漏类时总额错位） | ☐ |
| V6.3.2 | **前端其它 sums仍然准确** | `formatYuan(totalAll)` === 7764.08、`formatYuan(sixTotal)` === 7623.14 | ☐ |

### V6.4 后端单测验收（1 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| **V6.4.1** | **SnapshotQueryServiceTest.latest_userCorrectAddsNewCategory_固收类** | mock AssetSnapshot 仅 5 大类 + 余额类；mock userCorrectMap 添加 3 只【A 股 → 固收】，mock asset_raw 对应行；断言 `resp.categories` 有「固收类」且 total == 1189.92、sixCategoriesTotal == 7623.14、totalAssetWithBalance == 7764.08 | ☐ |

### V6.5 端到端浏览器验收（1 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| **V6.5.1** | **主页正确显示固收类（其他 5 大类 + 余额类 + 总额）** | http://localhost:5174/ 截图：固收类行出现且金额 1,189.92；A股权益类从 1,882.50 隆至 692.58；六大类合计 7,623.14；总资产 7,764.08；基金数 18 + 1 余额。 | ☐ |

### V6.6 重启验证（1 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| V6.6.1 | **mvn package + java -jar 后端重启及 curl 响应包含固收类** | `scripts/1b/restart-backend.ps1` 完成后，curl latest → 响应包含「固收类」entry且上述连续性验收三个值都对了 | ☐ |

### V6.7 错误用例验收（1 用例）

| ID | 测试 | 验收标准 | 通过 |
|---|---|---|---|
| V6.7.1 | **仅有 5 类原始快照的 user 不变** | 备份 DB 表后删除一个快照，确认响应还正常工作（不出现遗留 entry） | ☐ |

### V6.8 验收依赖（V6 须走在其他验证之前）

- 写在 V1-V5 之后验证，避免引入额外变动。
- mvn test 全绿、DataPage 22 用例全绿后取 V6。

### V6.9 文件依赖

- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapshotQueryService.java` — 修改
- `fincontrol-frontend/src/pages/HomePage.jsx` — 修改
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapshotQueryServiceTest.java` — 加 1 用例

