# Phase 1a.8 v3 真实四图闭环修复工作计划

**状态**：`IN_PROGRESS` 〔2026-07-18 复核 Gate 0 已冻结；真实四图执行尚未通过〕  
**计划日期**：2026-07-18  
**代码基线**：`68056ee57b5de23d8690ae9f6c75190a6b2753d2`  
**配套验收计划**：[`2026-07-18_phase1a8-v2-acceptance-plan.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v2-acceptance-plan.md)  
**历史 v2 对照报告**：[`2026-07-18_phase1a8-v2-real-data-check.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v2-real-data-check.md)  
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)  
**API 契约**：[`api-contract.md §2 / §3`](../../phase-0/api-contract.md)

> 本文件保留原文件名以维持既有链接，但内容已按 2026-07-18 17:41 的复核结论升级为 **v3 真实四图闭环计划**。历史 v2 的“18 行”“测错图”“仅看 code=0”口径不再作为当前验收依据；其失败证据保留在历史对照报告中。

---

## 0. Gate 0：本轮复核结论与冻结事实

### 0.1 已核验的真实状态

1. 后端健康检查为 HTTP 200，当前运行基线可继续测试。
2. 正确的 4 张用户样图位于 `fincontrol-backend/uploads/samples/`：
   - `phase1a2-alipay-fund-list-20260715-2355-1.jpg`
   - `phase1a2-alipay-fund-list-20260715-2355-2.jpg`
   - `phase1a2-alipay-fund-list-20260715-2355-3.jpg`
   - `phase1a2-alipay-fund-list-20260715-2356-1.jpg`
3. `ScreenshotService` 已有 OCR 写盘初版，之前只验证了 `compile/test-compile`，尚未完成行为测试与真实写盘验证。
4. 最近生成的 4 个 parse 请求体实际均为 `{"fileId":"","userId":1}`，响应均为 `code=1001 / fileId 必填`。
5. MySQL `chat_history` 没有本轮四图的新记录；根目录 OCR 目录为空。因此“parse 仍在等待”更正为“parse 未进入业务层并已失败”。
6. `.gitignore` 当前未提交改动误删了 `**/uploads/`、`**/tmp/`、`api-test-output/` 三条既有规则，同时没有加入 OCR 忽略规则，必须先修复。
7. OCR 路径当前依赖 JVM 启动 CWD；从 `fincontrol-backend` 启动时可能写入错误的 `fincontrol-backend/docs/...`，必须改为可配置且稳定的仓库根路径。
8. `DedupEngine` 已在正确入口生效：
   `POST /api/snapshot/confirm` → `SnapShotConfirmService.confirm()` → `DedupEngine.deduplicate()`。
   单图 `ScreenshotService.parse()` 不负责多图去重，本轮不破坏这条职责边界。
9. `application-local.yml` 为 ignored/untracked，本地 API 密钥不得进入 commit、报告或 smoke 输出。

### 0.2 正确 ground truth：19 个唯一标的

正确口径为 **18 只基金 + 1 余额宝 = 19 个唯一标的**，总额 **7,884.68 元**，不是旧计划中的 18。

| 大类 | 数量 | 标的 |
|---|---:|---|
| 货币类 | 1 | 中加货币E 796.32 / +2.32 |
| 固收类 | 3 | 长城短债债券A 454.58 / +4.58；鹏华纯债债券D 435.82 / +1.62；安信新价值灵活配置混合A 298.45 / -1.55 |
| 商品类 | 3 | 国泰黄金ETF联接A 923.16 / -129.84；国泰黄金ETF联接C 564.86 / -45.25；华安黄金ETF联接C 156.48 / -26.27 |
| A 股权益类 | 5 | 诺安中证A100指数A 1267.84 / +46.84；国泰海通中证500指数增强C 289.87 / -0.13；广发价值回报混合C 113.84 / -6.16；易方达机器人ETF联接C 108.20 / +8.20；诺安中证A100指数C 105.57 / +5.57 |
| 海外权益类 | 4 | 天弘纳斯达克100指数(QDII)A 633.32 / +51.32；天弘纳斯达克100指数(QDII)C 308.82 / +33.82；摩根纳斯达克100指数(QDII)A 545.48 / +35.48；招商纳斯达克100ETF联接(QDII)C 184.69 / +24.69 |
| 港股/大中华类 | 2 | 易方达恒生科技ETF联接(QDII)C 255.42 / -8.58；华安香港精选股票(QDII) 121.11 / +1.11 |
| 余额类 | 1 | 余额宝 320.85 / +1.89 |
| **总计** | **19** | **7,884.68 元** |

### 0.3 四页边界与去重口径

| 页 | 完整持仓行 | 仅标题边界行 | 完整行数 |
|---|---|---|---:|
| 2355-1 | 天弘纳指A、国泰黄金C、摩根纳指A、长城短债A、鹏华纯债D、余额宝 | 天弘纳指C | 6 |
| 2355-2 | 诺安A100A、国泰黄金A、中加货币E；页头显示总额 7884.68 | 无 | 3 |
| 2355-3 | 华安黄金C、华安香港、广发价值C、易方达机器人C、诺安A100C | 无 | 5 |
| 2356-1 | 天弘纳指C、安信新价值、国泰海通500、易方达恒生、招商纳指C、华安黄金C | 华安香港 | 6 |

冻结规则：

- 四页共有 **20 条完整持仓行**。
- `华安黄金ETF联接C` 是完整跨页重复；Dedup 后应减少 1 条。
- `天弘纳指C` 在第一页仅标题、第四页完整；`华安香港` 在第三页完整、第四页仅标题。
- 仅标题且缺少金额/收益的行不是完整持仓，不能新增唯一标的，也不能覆盖另一页的完整记录。
- 最终硬门槛：**20 条完整输入 → 19 个唯一标的，金额合计 7884.68**。

---

## 1. 目标、范围与非目标

### 1.1 目标

1. 修复 OCR 日志目录、写盘稳定性与测试覆盖。
2. 建立机器可读的四页真实 ground-truth fixture。
3. 顺序执行 4 张真实图片 upload + parse，逐项核对名称、金额、收益。
4. 通过 `SnapShotConfirmService` 真实触发 DedupEngine，证明 20 条完整行合并为 19 个唯一标的。
5. 以数据正确性而非 `code=0` 作为 1a.8.6 PASS 判据。
6. 产出可回溯的失败/修复/复验记录，并同步 checklist。

### 1.2 范围内

- `.gitignore` 修复与临时测试产物治理。
- OCR success/error 日志，包含 timestamp、fileId、provider、fallback、raw、parsed/error。
- OCR 输出路径可配置、文件名防覆盖。
- `ScreenshotService` 日志行为单测。
- 四页 ground-truth fixture 与自动比较器。
- DedupEngine 对完整/不完整跨页记录的安全合并。
- `mvn clean verify` 与 JaCoCo。
- 真实 MySQL + 真实 vision provider 的四页顺序 E2E。
- H2/测试事务中的 `SnapShotConfirmService` 真实链路验收；如需 production MySQL confirm 证据，使用隔离测试数据并在执行前申请批准。
- 1a.8 文档、报告、checklist、commit/push 收尾。

### 1.3 非目标

- 不在 v3 强制 AI 输出固定六大类；v2 prompt 仍允许语义合理的自由分类标签。
- 不实现 Phase 1b UI 确认面板。
- 不在本轮扩展 DeepSeek text fallback。
- 不提交用户原图、OCR raw、API 密钥或临时 API 响应。
- 不以修改测试名称、替换真实 provider 为 mock、降低断言等方式制造 PASS。

---

## 2. 测试层级与替身边界

| 层级 | 数据/替身 | 目的 | 是否为 PASS 必需 |
|---|---|---|---|
| Unit | Mockito + `@TempDir` | OCR success/error 写盘、路径与字段 | 是 |
| Algorithm | 机器可读 4 页 fixture + real DedupEngine | 20→19、标题行保护、总额与逐项字段 | 是 |
| Integration | H2/测试事务 + real SnapShotConfirmService/DedupEngine | 确认链路与三表写入/回滚 | 是 |
| Production E2E | 真实 4 张 JPG + 真实 MySQL + 真实 vision provider | upload/parse/OCR/chat_history/真实识别 | 是 |
| Production confirm | 隔离测试用户或可清理数据 + MySQL | 补充 endpoint 级证据 | 条件执行，需批准 |

真实图片 E2E 与 deterministic fixture 是两类证据：fixture 不能证明模型真实识别；真实模型 `code=0` 也不能证明 19 项数据正确。两者必须分别通过。

---

## 3. 垂直切片与测试 ID

| Gate / Slice | 实施内容 | 主要文件 | 对应验收 ID | 完成条件 |
|---|---|---|---|---|
| Gate 0 | 复核事实、冻结 19 标的与测试边界 | 本计划 + 验收计划 | A8V3-G01 | 计划互链、ID/fixture/real-mock 边界明确 |
| Slice A | 恢复 ignore 规则；修 OCR 路径、唯一文件名、success/error 写盘 | `.gitignore`、`ScreenshotService.java`、配置、测试 | A8V3-S01~S03 | 定向单测通过；目标目录正确 |
| Slice B | 建 4 页 fixture；补 Dedup 20→19 与不完整标题保护 | `src/test/resources`、`DedupEngine.java/Test` | A8V3-S04~S05 | 19 项逐字段、总额、drop 均通过 |
| Slice C | 全量构建与覆盖率 | `pom.xml`/现有测试 | A8V3-S06 | `mvn clean verify` PASS；覆盖率达标 |
| Slice D | 顺序 upload/parse；OCR、SQL、逐页自动对账 | `scripts/1a8/`、ignored API output | A8V3-S07~S10 | 4/4 code=0 且 19 项真实数据全对 |
| Slice E | 经 SnapShotConfirmService 触发 Dedup；验证镜像 | Service integration test；可选 MySQL endpoint | A8V3-S11~S12 | 20→19、7884.68、三表一致 |
| Slice F | 报告、checklist、git hygiene、commit/push | manual report、`phase-1a.md` | A8V3-S13 | 证据完整，无秘密/临时产物入仓 |

---

## 4. 实施细则

### 4.1 `.gitignore` 与产物

必须恢复：

```gitignore
**/uploads/
**/tmp/
docs/test-records/automated-smoke/**/api-test-output/
```

并新增：

```gitignore
/docs/test-records/ocr-results/
```

自动化 API JSON 只放 `automated-smoke/1a8/api-test-output/`；最终可提交证据为不含密钥/原始 OCR 的 smoke log 与人工报告。

### 4.2 OCR 写盘

- 输出根路径由配置决定，默认稳定指向仓库根 `docs/test-records/ocr-results`。
- 文件名至少包含毫秒时间戳、fileId、usedProvider；同一次重跑不得覆盖。
- 成功：保存 raw + parsed + provider/fallback + timestamp。
- JSON 抽取失败：保存 raw + errorCode/errorMessage + provider/fallback。
- 写盘失败只告警，不改变主业务响应；但 A8V3-S02/S03 必须能观测并验证。

### 4.3 Dedup 完整性规则

- 完整记录：fundName、amount、profit、categoryName 均可用于确认。
- 同名记录两者都完整：维持后页优先。
- 一完整一不完整：完整记录优先，不允许标题行覆盖完整值。
- 唯一但不完整：不进入最终资产，产生 `DATA_INCOMPLETE` warning。
- 同名完整记录且 category 冲突：保持现有硬失败，不能静默跨类合并。

### 4.4 真实 E2E

- 使用 PowerShell 对象 + `ConvertTo-Json` 构造 parse body，禁止 CMD 多层 JSON 转义。
- 四页顺序执行，避免并发限流与日志相互覆盖。
- 每页保存 request/response 到 ignored `api-test-output/`。
- 每次立即核对 HTTP、业务 code、OCR 文件和 chat_history；任一失败即停止后续 confirm。

---

## 5. 验证命令

```bat
cd fincontrol-backend
mvn clean verify
```

```powershell
# 实际脚本名称在 Slice D 落盘后执行
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\1a8\01-real-four-page-e2e.ps1
```

```bat
mysql -uroot -p --default-character-set=utf8mb4 fincontrol -e "SELECT id,conversation_id,role,used_provider,fallback_triggered,created_at FROM chat_history ORDER BY id DESC LIMIT 12;"
```

提交前：

```bat
git diff --check
git status --short
git diff --stat
git grep -n -E "(ark-|sk-)" -- ":(exclude)fincontrol-backend/src/main/resources/application-local.yml"
```

---

## 6. Gate、停止条件与 PASS 定义

### 6.1 Gate 顺序

1. Gate 0：计划和验收 ID 冻结。
2. Gate 1：ignore/OCR 单测通过。
3. Gate 2：fixture/Dedup 20→19 通过。
4. Gate 3：`mvn clean verify` 通过。
5. Gate 4：真实四页 19 项逐字段通过。
6. Gate 5：SnapShotConfirmService 合并与镜像通过。
7. Gate 6：报告/checklist/commit/push。

### 6.2 停止条件

出现下列任一项，不得宣称 1a.8.6 PASS：

- 任一真实 parse 非 `code=0`。
- 唯一标的不是 19。
- 任一基金名称、金额或收益与 fixture 不一致。
- 合计与 7884.68 的误差大于 0.01。
- 标题行覆盖完整记录或 Dedup 抛未记录 NPE。
- OCR 少于 4 个对应日志或 provider 审计未落库。
- `mvn clean verify` / coverage 失败。
- 只验证单测、未跑真实四图，或只看 `code=0` 未做数据对账。

若真实 provider 仍漏项，应保存失败证据、定位 OCR raw、做最小 prompt 版本修订后从 Gate 4 重跑，不得删除失败记录。

---

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| minimax 限流/慢响应 | 串行执行；记录耗时；只在明确路由条件下 fallback |
| v2 自由分类导致同基金跨页 category 冲突 | 原始 OCR 报告分类差异；确认前由 fixture/用户确认映射；不静默吞冲突 |
| 标题边界被模型输出为 null 行 | Dedup 完整记录优先 + `DATA_INCOMPLETE` warning |
| OCR 相对路径随 CWD 漂移 | 配置化绝对解析 + `@TempDir` 单测 |
| 同秒写盘覆盖 | 毫秒时间戳/唯一后缀 |
| 真实 MySQL confirm 污染用户数据 | 先 H2/事务；production confirm 使用隔离数据且执行前申请批准 |
| 本地密钥误入仓 | ignored local config + 提交前 secret/path 检查 |
| 旧文档仍含 18 口径 | 本计划与配套验收计划定义唯一当前口径；最终报告同步相关 1a.8 文档 |

---

## 8. 提交检查点

建议一个聚焦提交：

```text
fix(1a.8): close real four-page OCR and 20-to-19 dedup
```

提交必须包含：

- 工作计划/验收计划修订；
- `.gitignore`、OCR 实现与测试；
- ground-truth fixture、Dedup 修复与测试；
- 稳定 E2E 脚本；
- 真实验收报告与 checklist 状态。

提交不得包含：

- `application-local.yml` 或任何 API key；
- `uploads/` 用户图片；
- `ocr-results/` raw；
- `api-test-output/` 临时 JSON；
- 根目录随机 `fileId` 或 `.tmp/` 调试文件。

push 属网络操作，执行时单独申请批准；若失败，报告本地 commit hash 与重试命令。

---

## 9. 修订记录

| 时间 | 修订 | 原因 |
|---|---|---|
| 2026-07-18 早前 | v2：不约束大类、尝试真实 OCR | 实际使用了错误的 1a.7 历史图片，且只以 code=0 判断 |
| 2026-07-18 17:22 | OCR 初版、创建目录、四图 upload 尝试 | parse 请求 fileId 为空，未进入业务层；OCR/SQL 无证据 |
| 2026-07-18 17:41 | 升级为本 v3 真实四图闭环计划；冻结 19 标的、20→19、测试边界与停止条件 | 用户明确 4 页内容、19 个唯一标的和 7884.68；复核工作区、SQL、OCR 后纠正进度 |
