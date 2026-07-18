# Phase 1a.8 v3 真实四图闭环验收计划

**状态**：`IN_PROGRESS` 〔复核 Gate 0：`PASS`；A8V3-S01~S13 待执行〕  
**日期**：2026-07-18  
**配套工作计划**：[`2026-07-18_phase1a8-v2-work-plan.md`](../../phase-1/work-plans/2026-07-18_phase1a8-v2-work-plan.md)  
**历史 v2 对照报告**：[`2026-07-18_phase1a8-v2-real-data-check.md`](./2026-07-18_phase1a8-v2-real-data-check.md)  
**总进度清单**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)  
**API 契约**：[`api-contract.md §2 / §3`](../../phase-0/api-contract.md)

> 本文件保留原文件名以维持既有链接，但已按 2026-07-18 17:41 的复核结论升级为 v3。测试 ID、fixture、real/mock/H2/MySQL 边界与停止条件在实现前冻结。不得通过删除失败记录、改名测试、把 real 换成 mock 或只检查 `code=0` 来改变验收结论。

---

## 0. Gate 0 冻结

### 0.1 进度纠正

本轮执行前确认：

- 正确四图已经存在于 `fincontrol-backend/uploads/samples/`。
- 最近四次 upload 有成功产物，但后续 request 中 `fileId` 为空。
- 四次 parse 均返回 `code=1001 / fileId 必填`，未进入 `ScreenshotService.parse()`。
- MySQL `chat_history` 没有本轮记录，OCR 目录没有本轮文件。
- 因而真实四图 production 验收状态是 **NOT RUN/FAILED BEFORE BUSINESS**，不是“等待响应”。
- `ScreenshotService` OCR 初版只通过 compile/test-compile，不能据此判定行为 PASS。
- `.gitignore` 存在误删规则，必须作为 A8V3-S01 修复并留证。

### 0.2 唯一当前验收口径

- 真实唯一标的：**19**（18 只基金 + 1 余额宝）。
- 四页完整持仓行：**20**（6 + 3 + 5 + 6）。
- 完整跨页重复：`华安黄金ETF联接C` 1 条。
- Dedup 结果：**20 → 19，dropped=1**。
- 总金额：**7884.68 元，误差不超过 0.01**。
- 仅标题边界：第一页末尾 `天弘纳指C`、第四页末尾 `华安香港`；不得计作完整持仓或覆盖完整数据。
- v2 prompt 不固定六大类标签文字；本轮 PRODUCTION 硬门槛是名称、金额、收益、唯一数与总额。分类语义差异单独记录，留 v3 分类阶段处理。

---

## 1. Ground-truth fixture

### 1.1 四页预期

| Page ID | 文件 | 完整记录数 | 完整记录 | 仅标题 |
|---|---|---:|---|---|
| P1 | `phase1a2-alipay-fund-list-20260715-2355-1.jpg` | 6 | 天弘纳指A、国泰黄金C、摩根纳指A、长城短债A、鹏华纯债D、余额宝 | 天弘纳指C |
| P2 | `phase1a2-alipay-fund-list-20260715-2355-2.jpg` | 3 | 诺安A100A、国泰黄金A、中加货币E；页头总资产 7884.68 | 无 |
| P3 | `phase1a2-alipay-fund-list-20260715-2355-3.jpg` | 5 | 华安黄金C、华安香港、广发价值C、易方达机器人C、诺安A100C | 无 |
| P4 | `phase1a2-alipay-fund-list-20260715-2356-1.jpg` | 6 | 天弘纳指C、安信新价值、国泰海通500、易方达恒生、招商纳指C、华安黄金C | 华安香港 |

### 1.2 19 个唯一标的逐项预期

| # | 类别基准 | 基金/资产完整名称 | amount | profit |
|---:|---|---|---:|---:|
| 1 | 货币类 | 中加货币E | 796.32 | 2.32 |
| 2 | 固收类 | 长城短债债券A | 454.58 | 4.58 |
| 3 | 固收类 | 鹏华纯债债券D | 435.82 | 1.62 |
| 4 | 固收类 | 安信新价值灵活配置混合A | 298.45 | -1.55 |
| 5 | 商品类 | 国泰黄金ETF联接A | 923.16 | -129.84 |
| 6 | 商品类 | 国泰黄金ETF联接C | 564.86 | -45.25 |
| 7 | 商品类 | 华安黄金ETF联接C | 156.48 | -26.27 |
| 8 | A 股权益类 | 诺安中证A100指数A | 1267.84 | 46.84 |
| 9 | A 股权益类 | 国泰海通中证500指数增强C | 289.87 | -0.13 |
| 10 | A 股权益类 | 广发价值回报混合C | 113.84 | -6.16 |
| 11 | A 股权益类 | 易方达机器人ETF联接C | 108.20 | 8.20 |
| 12 | A 股权益类 | 诺安中证A100指数C | 105.57 | 5.57 |
| 13 | 海外权益类 | 天弘纳斯达克100指数(QDII)A | 633.32 | 51.32 |
| 14 | 海外权益类 | 天弘纳斯达克100指数(QDII)C | 308.82 | 33.82 |
| 15 | 海外权益类 | 摩根纳斯达克100指数(QDII)A | 545.48 | 35.48 |
| 16 | 海外权益类 | 招商纳斯达克100ETF联接(QDII)C | 184.69 | 24.69 |
| 17 | 港股/大中华类 | 易方达恒生科技ETF联接(QDII)C | 255.42 | -8.58 |
| 18 | 港股/大中华类 | 华安香港精选股票(QDII) | 121.11 | 1.11 |
| 19 | 余额类 | 余额宝 | 320.85 | 1.89 |
| **合计** | | **19 个唯一标的** | **7884.68** | 不作为总收益门槛 |

机器可读 fixture 必须与此表同源，测试不得另建一份不一致的硬编码答案。

---

## 2. 测试边界

| 证据层 | 使用对象 | 禁止替换为 | 结论范围 |
|---|---|---|---|
| Unit | Mockito、真实 ObjectMapper、`@TempDir` | 只验证方法被调用但不检查文件 | OCR 写盘行为 |
| Algorithm | 机器 fixture + real DedupEngine | mock DedupEngine | 20→19 与完整性规则 |
| Integration | H2/测试事务 + real SnapShotConfirmService + real DedupEngine | 直接手工调用私有辅助方法 | confirm 编排、三表镜像、事务 |
| Production parse | 真实 JPG + 真实 MySQL + 当前真实 vision provider | mock provider、历史 screenshots | 模型真实识别、OCR、SQL 审计 |
| Production confirm | 隔离 MySQL 数据 | 用户既有快照且不清理 | endpoint 补充证据；执行前需批准 |

### 2.1 判定边界

- Unit/Algorithm/Integration 全通过，只能得到 BUSINESS PASS，不能替代 PRODUCTION。
- 四张图全部 `code=0`，但任一项数据错误，PRODUCTION 仍为 FAIL。
- 分类标签文本因 v2 prompt 自由命名可不完全一致；同名完整记录跨类别冲突仍必须显式失败，不能静默吞掉。
- prompt 若调整，必须记录版本、原因、失败样本和重跑结果。

---

## 3. 冻结测试用例

### Gate 0

| ID | 层级 | 目标 | fixture/替身 | 通过条件 |
|---|---|---|---|---|
| **A8V3-G01** | Doc | 工作计划与验收计划同步 | 文档互链 | 19/20→19/7884.68、测试层、停止条件一致 |

### Slice A：ignore 与 OCR

| ID | 层级 | 目标 | 执行方式 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S01** | Git | 恢复既有 ignore 并忽略 OCR/API 临时产物 | `git check-ignore -v` + `git status --short` | uploads、tmp、api-test-output、ocr-results 全命中正确规则；稳定文档/脚本仍可跟踪 |
| **A8V3-S02** | Unit | parse 成功写 OCR JSON | Mockito + real ObjectMapper + `@TempDir` | 文件位于配置根/{date}；含 timestamp/fileId/provider/fallback/raw/parsed；连续写不覆盖 |
| **A8V3-S03** | Unit | 非法 JSON 写 error OCR 且保留业务错误 | mock `extractFirstJsonObject` 抛 3001 | 文件含 raw/errorCode/errorMessage/provider；主流程仍抛 3001；写盘失败不改业务错误 |

### Slice B：fixture 与 Dedup

| ID | 层级 | 目标 | fixture/替身 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S04** | Algorithm | fixture 自身一致性 | 4 页 JSON fixture | 页完整数 6/3/5/6；完整输入 20；唯一名 19；金额和 7884.68；逐项字段与 §1.2 一致 |
| **A8V3-S05** | Algorithm | real DedupEngine 处理重复和标题边界 | A8V3-S04 fixture | 完整输入 20→merged 19/dropped 1；标题行不能覆盖完整值；唯一不完整行 warning；无 NPE；逐项金额收益正确 |

### Slice C：构建与回归

| ID | 层级 | 目标 | 命令 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S06** | BUSINESS/COVERAGE | 全量回归 | `mvn clean verify` | BUILD SUCCESS；全部测试 PASS；JaCoCo 不低于项目门槛；无新增跳过 |

### Slice D：真实四图 E2E

| ID | 层级 | 目标 | 数据边界 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S07** | PRODUCTION | 顺序 upload 四张正确样图 | `uploads/samples` 四文件 + real filesystem/MySQL | 4/4 HTTP 正常、`code=0`、4 个非空新 fileId；请求体中 fileId 与 upload 响应一致 |
| **A8V3-S08** | PRODUCTION | 顺序 parse 并逐页对账 | real provider + real MySQL | 4/4 `code=0`；完整行数期望 6/3/5/6（仅标题不计）；名称/金额/收益逐项一致；聚合唯一数 19、总额 7884.68±0.01 |
| **A8V3-S09** | PRODUCTION | OCR 真实写盘 | real parse 输出 | 至少 4 个与本轮 fileId 对应的 JSON；路径在仓库根目标目录；字段完整且文件不覆盖 |
| **A8V3-S10** | READ_SQL | chat_history 审计 | real MySQL 查询 | 每个 fileId 至少 1 user + 1 assistant；assistant 的 used_provider 非空；fallback_triggered 可解释；content 对应本轮 raw |

### Slice E：确认与 Dedup 链路

| ID | 层级 | 目标 | 数据边界 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S11** | Integration | 经 real SnapShotConfirmService 触发 real DedupEngine | H2/测试事务 + 四页 fixture | `dedupReport input=20/merged=19/dropped=1`；assetRawInserted=19；category 汇总合计 7884.68；三表镜像一致；测试回滚 |
| **A8V3-S12** | PRODUCTION-OPTIONAL | endpoint 级 confirm 补证 | 隔离 MySQL 测试数据；执行前批准 | `/api/snapshot/confirm` 返回同等 Dedup 指标；查询镜像一致；按批准方案清理/撤销；不得污染现有用户数据 |

### Slice F：报告与提交

| ID | 层级 | 目标 | 执行方式 | 通过条件 |
|---|---|---|---|---|
| **A8V3-S13** | DELIVERY | 证据、checklist、git hygiene | 报告 + `git diff --check/status` + secret scan | 失败与修复均留档；1a.8.6 状态真实；无 key、原图、OCR raw、临时 JSON/fileId 入仓；commit 可回溯 |

---

## 4. 实际执行命令

### 4.1 定向与全量测试

```bat
cd fincontrol-backend
mvn -Dtest=ScreenshotServiceTest,DedupEngineTest,SnapShotConfirmServiceTest test
mvn clean verify
```

### 4.2 ignore 验证

```bat
git check-ignore -v fincontrol-backend/uploads/samples/phase1a2-alipay-fund-list-20260715-2355-1.jpg
git check-ignore -v docs/test-records/ocr-results/2026-07-18/probe.json
git check-ignore -v docs/test-records/automated-smoke/1a8/api-test-output/probe.json
git status --short
```

### 4.3 真实四图

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\1a8\01-real-four-page-e2e.ps1
```

脚本必须：顺序 upload→parse、使用对象序列化 JSON、在每页失败时停止、将原始 API 输出写到 ignored `api-test-output/`，并输出无密钥的汇总 log。

### 4.4 SQL 审计

```sql
SELECT id, conversation_id, role, used_provider, fallback_triggered,
       CHAR_LENGTH(content) AS content_len, created_at
FROM chat_history
WHERE conversation_id IN (<本轮 4 个 conversationId>)
ORDER BY id;
```

### 4.5 提交前

```bat
git diff --check
git status --short
git diff --stat
```

密钥检查只输出路径/命中位置，不把本地 secret value 写入报告。

---

## 5. 五段式验收

| 段 | 必须证据 | PASS 条件 | 当前状态 |
|---|---|---|---|
| **BUSINESS** | A8V3-S02~S06、S11 | OCR 行为、fixture、Dedup、confirm integration 全通过 | ⏳ NOT RUN |
| **CONTRACT** | 现有 Screenshot/Snapshot Controller 回归 | 不改变既有请求/响应字段；回归通过 | ⏳ NOT RUN |
| **READ_SQL** | A8V3-S10 + prompt_versions 查询 | 本轮 4 对 chat_history 完整；provider 审计可见；生效 prompt 版本可证 | ⏳ NOT RUN |
| **PRODUCTION** | A8V3-S07~S10 | 正确四图真实 parse；19 项逐字段一致；总额 7884.68；OCR 4+ | ⏳ NOT RUN |
| **COVERAGE** | A8V3-S06 JaCoCo | 不低于项目既有门槛（目标 ≥60%） | ⏳ NOT RUN |

`A8V3-S12` 是需批准的 production confirm 补证，不替代必需的 A8V3-S11，也不阻塞在 H2/测试事务中证明 SnapShotConfirmService 真实触发 DedupEngine。

---

## 6. 硬性 PASS 判据

只有同时满足以下全部条件，才可把 1a.8.6 标为 ✅：

1. 正确 4 张 `uploads/samples` 图片，不是 1a.7 历史截图。
2. 4/4 upload 与 4/4 parse 成功。
3. 四页完整记录按 6/3/5/6 对账；标题边界不误算。
4. 20 条完整输入经 Dedup 得到 19 个唯一标的。
5. 19 个名称全部命中，amount/profit 逐项一致。
6. 金额合计 `7884.68 ± 0.01`。
7. OCR 至少 4 个文件，目录、字段、provider/fallback 均正确。
8. chat_history 对本轮四个 conversation 审计完整。
9. SnapShotConfirmService/DedupEngine integration 及三表镜像通过。
10. `mvn clean verify` 与覆盖率通过。
11. 报告诚实记录所有失败、prompt 变更和复验；无秘密/临时产物入仓。

`code=0`、mock fixture PASS、旧图片 PASS、单张图片 PASS 均不能单独满足本判据。

---

## 7. 失败处理与停止条件

| 失败类型 | 必须动作 | 禁止动作 |
|---|---|---|
| upload/fileId 为空 | 保存 request/response；修脚本序列化；从 A8V3-S07 重跑 | 手工填假 fileId 后宣称脚本通过 |
| provider HTTP/限流 | 保存 provider/错误码/耗时；按既有路由重试或 fallback | 换 mock provider |
| 非 JSON/0 funds | 保存 OCR error log 与 chat_history；定位 prompt/client | 只看 HTTP 200 |
| 漏基金/错金额/错收益 | 自动差异列表；保留 raw；最小 prompt 修订并登记版本 | 降低 fund_count 或金额断言 |
| 标题覆盖完整行/NPE | 修 Dedup 完整性规则并补回归 | 调换页面顺序掩盖 bug |
| category 冲突 | 显式报告；用户确认/fixture 映射后再 confirm | 静默跨类合并 |
| MySQL confirm 污染风险 | 停在 H2；申请隔离数据写入批准 | 直接覆盖 user 1 既有快照 |
| 全量测试/coverage 失败 | 修复并重跑全量 | 只跑定向测试后提交 |

任何失败记录不得删除；修复后在 §8 追加新行。

---

## 8. 实际结果记录

| 时间 | 测试 ID | 实际结果 | 根因/证据 | 后续 |
|---|---|---|---|---|
| 2026-07-18 17:22 前后 | A8V3-S07/S08 前置尝试 | ❌ FAIL BEFORE BUSINESS | 4 个 request 均为 `fileId=""`，4 个 response 均 `code=1001`；chat_history 无新行；OCR 空 | 修复脚本 JSON/fileId 传递后重新顺序 upload→parse |
| 2026-07-18 17:41 | A8V3-G01 | ✅ PASS | 工作计划与本验收计划已冻结 19 标的、20→19、测试层与停止条件 | 开始 Slice A |
| 待执行 | A8V3-S01~S13 | ⏳ NOT RUN | — | 按 Gate 顺序回填，不预填 PASS |

---

## 9. 修订记录

| 时间 | 修订 | 原因 |
|---|---|---|
| 2026-07-18 早前 | v2 以 17 基金 + 余额宝 = 18 为口径 | 用户后续提供的完整清单证明实际为 18 基金 + 余额宝 = 19 |
| 2026-07-18 17:41 | 升级 v3；更正真实执行状态；冻结 A8V3-G01/S01~S13 | 复核发现 parse 请求 fileId 为空、OCR/SQL 无证据、ignore/path 有缺陷；必须先计划与验收再编码 |
