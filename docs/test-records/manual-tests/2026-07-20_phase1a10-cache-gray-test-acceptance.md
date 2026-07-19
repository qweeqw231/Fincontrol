# Phase 1a.10 缓存验证 + 20260716 灰测 验收文件（2026-07-20）

> **状态**：📋 **验收文件落盘，等待 ACT 模式执行**
> **配套**：[`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md)（1a.10 真实 E2E）/ [`2026-07-19_phase1a-acceptance.md`](2026-07-19_phase1a-acceptance.md)（1a 整体验收）
> **报告类型**：缓存验证 + 新数据泛化灰测（2 in 1）
> **执行日期**：2026-07-20（待 ACT 模式执行）

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.0（落盘） |
| 编写日期 | 2026-07-20 00:40（Asia/Shanghai） |
| 配套工作 | [`2026-07-19_phase1a10-real-e2e.md §9.8`](2026-07-19_phase1a10-real-e2e.md)（本验收执行结果将写入该章节） |
| 测试模式 | ① 缓存机制验证（20260715 数据，3 步）+ ② 新数据灰测（20260716 用户上传，路径 A + 路径 B） |
| 外部 ground truth | DeepSeek 网页端人工识别（用户独立工具） |

---

## 1. 背景与目标

### 1.1 触发问题

用户提问："**我们的操作排除从缓存加载数据的意外路径了吗**"

诚实回答：**部分排除，但需要严格验证**。

### 1.2 现有缓存层盘点

| # | 缓存层 | 位置 | 范围 | 持久性 | 我们的测试是否命中？ |
|---|---|---|---|---|---|
| 1 | `AiRouter.visionCache` (Caffeine) | JVM heap, 24h TTL, max 1024 | AI 视觉响应 | ❌ JVM 关闭即清 | 首次跑 = MISS（fresh JVM），但**同 JVM 重跑同样 4 张图会 HIT** |
| 2 | `fund_category_map` MySQL 表 | MySQL 持久化 | fund_name → category | ✅ | 19 条已存在；新 confirm **UPDATE last_seen_at**，不 INSERT 新行 |
| 3 | `prompt_versions` MySQL 表 | MySQL 持久化 | prompt 模板（v2.7.1） | ✅ | 加载稳定，无变化 |
| 4 | `CategoryMasterService.activeAliasIndex` | JVM heap | 类别 alias 内存索引 | ❌ | 重启清空 |
| 5 | Spring/HTTP keep-alive | Tomcat | HTTP 连接复用 | ❌ | 每次 curl 新建/复用连接 |
| 6 | MyBatis L1/L2 cache | MyBatis | 取决于配置 | ❌ | 项目无 @Cache 注解 |

### 1.3 三个核心问题

| # | 问题 | 风险 | 验证方法 |
|---|---|---|---|
| Q1 | 同 JVM 重跑 4 张图，路径 A 会被 Caffeine cache 命中吗？ | 缓存命中 → 0 真实模型调用 → 测试结果不是真实 E2E | 跑 2 轮路径 A，对比延迟 + log HIT/MISS 计数 |
| Q2 | 路径 A 已 cache 后，路径 B 还会 cache MISS 吗？ | 路径 A 缓存 key = 4 个 (单图 hash)；路径 B 缓存 key = 1 个 (4 图合并 hash) | 跑 1 轮路径 A + 1 轮路径 B，抓 log 验证 |
| Q3 | 新数据（用户上传 20260716）能否被系统正确识别？ | 模型可能因训练数据偏向金融场景；新数据可能遇到 OCR/parsing 边缘 case | 跑 路径 A（4×单图）+ 路径 B（1×parse-batch），与 DeepSeek 网页端人工识别对比 |

---

## 2. 测试范围

### 2.1 缓存机制验证（基于 20260715 数据，3 步实验）

| 实验 | 内容 | 预期 | 验证目标 |
|---|---|---|---|
| **1A** | 启动后端（fresh JVM，cache 空）→ 跑 1 轮路径 A 4 张图 | log 显示 **4 次 "vision cache MISS"**；延迟各 5-10s | 验证新 JVM cache 必空 |
| **1B** | 立即跑第 2 轮路径 A 4 张图（同 fileId） | log 显示 **4 次 "vision cache HIT"**；延迟各 < 0.5s | 验证 cache 真的在工作 |
| **1C** | 跑 1 轮路径 B parse-batch 4 张图（同 4 张图） | log 显示 **1 次 "vision cache MISS"**（新 key 空间）| 验证路径 A/B cache key 隔离 |
| **1D** | 修改 `application.yml` `cache-max-size: 0` → 重启 → 重复 1A+1B | 1A: 4 MISS；1B: **4 MISS（无 HIT）** | 验证 cache 可关闭 |

### 2.2 20260716 灰测（新数据，路径 A + 路径 B）

| 步骤 | 内容 | 预期 |
|---|---|---|
| **2.1** | 启动后端 → 启动 `Start-TestSampleSession.ps1` watcher（FileSystemWatcher 模式，phase=phase1a10, vendor=alipay, scenario=fund-list） | watcher 后台运行，等待新文件 |
| **2.2** | 用户将 4 张 20260716 支付宝基金列表截图手动放入 `uploads/samples/`（Ctrl+V / 拖拽 / 保存） | watcher 自动重命名为 `phase1a10-alipay-fund-list-20260716-HHmm-N.jpg` |
| **2.3** | 关闭 watcher（`Stop-TestSampleSession.ps1`） | `.session.json` 清理 |
| **2.4** | 跑路径 A（4×单图）：4 次 upload + 4 次 parse | 4 次 code=0，每张图 5-15s 延迟 |
| **2.5** | 跑路径 B（1×parse-batch 4 张图） | 1 次 code=0，5-60s 延迟（间歇性可能更长） |
| **2.6** | 用户用 DeepSeek 网页端独立识别 4 张 20260716 图 | 人工 ground truth JSON |
| **2.7** | 统一对比：AI 自动 vs DeepSeek 人工 | 见 §5 验收标准 |

---

## 3. 测试设计

### 3.1 缓存验证实验 1A/1B/1C/1D

**实验 1A — fresh JVM 路径 A 首轮**：
```bash
# 启动
powershell scripts/1a10/00-start-backend.ps1
# 跑 1 轮 4 张图
powershell scripts/1a10/02-single-e2e.ps1
# 抓 log
Get-Content logs/1a10-backend.out | Select-String "vision cache (HIT|MISS)"
```
**预期 log 计数**：MISS × 4，HIT × 0

**实验 1B — 路径 A 二轮（同 fileId）**：
```bash
# 立即再跑 1 轮
powershell scripts/1a10/02-single-e2e.ps1
# 抓 log
```
**预期 log 计数**：MISS × 0（累计），HIT × 4（累计）

**实验 1C — 路径 B（同样 4 张图）**：
```bash
powershell .tmp/check-batch-e2e.ps1
# 抓 log
```
**预期 log 计数**：MISS × 1（路径 B 新 key），HIT × 4（路径 A 二轮累计）

**实验 1D — 关闭 cache**：
```yaml
# application.yml
fincontrol:
  ai:
    router:
      cache-max-size: 0  # 关闭
      cache-ttl-hours: 0
```
```bash
# 重启 + 重复 1A+1B
powershell scripts/1a10/00-start-backend.ps1
powershell scripts/1a10/02-single-e2e.ps1  # 第 1 轮
powershell scripts/1a10/02-single-e2e.ps1  # 第 2 轮
```
**预期**：每张图都 MISS × 2（无 HIT），证明 cache 真的被关闭

### 3.2 20260716 灰测（路径 A + 路径 B）

**步骤 2.1 — 启动 watcher**：
```bash
cd fincontrol-backend/uploads/samples
powershell -ExecutionPolicy Bypass -File Start-TestSampleSession.ps1 `
  -Phase phase1a10 -Vendor alipay -Scenario fund-list
```

**步骤 2.2 — 用户上传 4 张图**：
- 用户在 IDE 文件浏览器 / 系统资源管理器中
- 把 4 张 `2026-07-16` 支付宝基金列表截图 Ctrl+V / 拖入 `uploads/samples/`
- watcher 检测到新文件稳定后（400ms+2 次 size 一致），自动重命名为 `phase1a10-alipay-fund-list-20260716-HHmm-1.jpg` 等

**步骤 2.3 — 关闭 watcher**：
```bash
powershell -ExecutionPolicy Bypass -File Stop-TestSampleSession.ps1
```

**步骤 2.4 — 跑路径 A**：
```bash
powershell scripts/1a10/02-single-e2e.ps1
```
（脚本需要小改：默认读取 `phase1a2-alipay-fund-list-20260715-*`，需要适配 20260716 文件名）

**步骤 2.5 — 跑路径 B**：
```bash
powershell .tmp/check-batch-e2e.ps1
```

**步骤 2.6 — DeepSeek 人工识别**：
- 用户打开 DeepSeek 网页端 (https://chat.deepseek.com/)
- 上传 4 张 20260716 图
- 用 prompt: "请解析以下 4 张支付宝基金列表截图，按图顺序输出每张图的基金明细（基金名、金额、所属大类）"
- 拿到 4 段 JSON

**步骤 2.7 — 统一对比**：
- 我写对比脚本（`diff-ai-vs-deepseek.js`）逐基金对账：
  - 基金名：exact / 包含子串算一致
  - 金额：精确相等（±0.01）
  - 类别：归一化后一致
- 输出 PASS / FAIL 矩阵

### 3.3 DeepSeek 人工 ground truth 提取流程

**简化 prompt 模板**（用户使用时复制）：
```
请逐张解析以下 4 张支付宝基金列表截图，按图顺序输出 JSON：
[
  {
    "page": 1,
    "topAsset": "<顶部总资产数字，若无则 null>",
    "categories": [
      {
        "name": "<大类名>",
        "funds": [
          {"name": "<基金名>", "amount": <金额数字>}
        ]
      }
    ]
  }
]
```

**注意**：人工识别可能与 AI 自动识别有微小差异（OCR 误差、模型版本差异），§5 验收标准用宽松匹配。

---

## 4. 验收标准（5 段式 + 灰测 PASS 条件）

### 4.1 缓存验证 3 步（5 段式）

| 段 | 1A | 1B | 1C | 1D |
|---|---|---|---|---|
| **BUSINESS** | 4 张图 code=0 | 4 张图 code=0 | 1 次 code=0 | 8 张图全 code=0 |
| **CONTRACT** | 4 张图响应结构符合 ParsedAsset DTO | 同 1A | 路径 B 响应符合 BatchParseResponse DTO | 同 1A |
| **READ_SQL** | 不写 MySQL（仅 vision） | 不写 | 不写 | 不写 |
| **PRODUCTION** | log: 4 MISS 0 HIT | log: 0 MISS 4 HIT | log: 0 MISS 1 MISS（路径 B） | log: 8 MISS 0 HIT（双轮） |
| **COVERAGE** | N/A（仅 vision） | N/A | N/A | N/A |

### 4.2 20260716 灰测（PASS 条件）

**AI 自动 vs DeepSeek 人工对比矩阵**：

| 维度 | 严格 | 宽松 | 备注 |
|---|---|---|---|
| 基金名 | 100% 完全一致 | ≥ 80% 包含子串一致 | 模型可能缩写（"A" / "C"） |
| 金额 | 100% 精确（±0.01） | ≥ 80% 误差 ±1.00 | 余额小数位可能不同 |
| 类别归一化 | 100% canonical 7 大类 | ≥ 80% 归一化后一致 | 余额类 / 货币类 容易混淆 |
| 顶部总资产 | 100% 一致 | N/A（仅参考） | P1/P3/P4 多为 null |
| 路径 A 一致率 | 100% | 路径 A 4 张图全部识别完整 | 19 基金 / 7 大类结构 |
| 路径 B 一致率 | ≥ 80% | 路径 B 1 次 4 图批量识别 | 与路径 A 结果对账 |

**灰测 PASS 条件**（任一不满足则 FAIL）：
1. ✅ 路径 A 4 张图全部 code=0 + ≥ 80% 字段与人工一致
2. ✅ 路径 B 1 次 code=0 + ≥ 80% 字段与人工一致
3. ✅ 路径 A 和路径 B 之间 ≥ 90% 字段一致（验证同图多路径一致性）
4. ✅ 写入 MySQL: ≥ 10 fund_category_map 行（结构合理），asset_raw + asset_snapshot 都成功
5. ❌ **不要求** 100% 一致（用户已声明"不知道解析结果"）

### 4.3 风险与回滚

| 风险 | 概率 | 影响 | 回滚方案 |
|---|---|---|---|
| 缓存污染导致测试无效 | 中（实验 1B/1C 验证） | 测试结果不可信 | 1D 关闭 cache 重测 |
| minimax 4 图 batch timeout | 高（已知） | 路径 B FAIL | 重试 1-2 次 / 接受 PRODUCTION_BLOCKED |
| DeepSeek 人工识别偏差 | 中 | 对比矩阵 FAIL | 阈值从 80% 降到 60% |
| 20260716 文件 OCR 边缘 case | 中 | 灰测 FAIL | 调整 prompt / 重传更清晰图 |
| 用户上传慢（>10 min）| 低 | watcher 长时间运行 | 关 watcher 重启 |

---

## 5. 执行步骤（4 阶段 × 估算耗时）

| 阶段 | 内容 | 估算 | 依赖 |
|---|---|---|---|
| **0. 落盘本文件 + commit + push** | 写 `2026-07-20_phase1a10-cache-gray-test-acceptance.md`，commit `docs(1a.10): 写 缓存验证+灰测 验收文件`，push | 5 min | 用户确认 |
| **1. 缓存验证 3 步（实验 1A+1B+1C+1D）** | 启动后端 → 1A → 1B → 1C → 改 application.yml → 1D | 30-40 min | 后端启动 |
| **2. 20260716 灰测** | 启动 watcher → 用户上传 4 张图 → 跑路径 A → 路径 B → DeepSeek 人工 → 统一对比 | 30-40 min | 用户上传图片 + DeepSeek 识别 |
| **3. 写 §9.8 报告 + commit + push** | 在 `1a.10-real-e2e.md` 增补 §9.8，commit `docs(1a.10): 写 1a.10-real-e2e.md §9.8 缓存验证 + 20260716 灰测报告` | 10 min | 阶段 1+2 全部完成 |

**总耗时**：约 75-90 分钟

---

## 6. 关联文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 1a.10 真实 E2E | [`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md) | 本次实验结果将写入 §9.8 |
| 1a 整体验收 | [`2026-07-19_phase1a-acceptance.md`](2026-07-19_phase1a-acceptance.md) | 阶段 0 验收基线 |
| 1a.10 验收计划 | [`2026-07-19_phase1a10-acceptance-plan.md`](2026-07-19_phase1a10-acceptance-plan.md) | 5 段式定义 |
| Start-TestSampleSession.ps1 | [`../../../fincontrol-backend/uploads/samples/Start-TestSampleSession.ps1`](../../../fincontrol-backend/uploads/samples/Start-TestSampleSession.ps1) | FileSystemWatcher 自动改名 |
| AiRouter.java | [`../../../fincontrol-backend/src/main/java/com/fincontrol/ai/AiRouter.java`](../../../fincontrol-backend/src/main/java/com/fincontrol/ai/AiRouter.java) | visionCache 实现 |
| application.yml | [`../../../fincontrol-backend/src/main/resources/application.yml`](../../../fincontrol-backend/src/main/resources/application.yml) | cache 配置 |

---

## 7. 文件状态

- ✅ **验收文件已落盘**（本文）
- ⏳ **等用户 toggle 到 ACT 模式 + 确认开始**
- ⏳ **等用户上传 4 张 20260716 图**
- ⏳ **等 DeepSeek 网页端人工识别完成**

**下一步**：commit + push 验收文件 → 等 ACT 模式开始执行
