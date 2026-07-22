# Phase 1b.2 验收计划（首页 + 全局状态联动）

> **状态**：📝 待审阅（开工前）
> **编写日期**：2026-07-22
> **配套文档**：
> - [Phase 1b.2 工作计划](../../../phase-1/work-plans/1b/2026-07-22_phase1b2-work-plan.md)
> - [Phase 1b 验收清单](../../../phase-1/checklists/phase-1b.md)
> - [Phase 0 决策文档](../../../phase-0/decisions.md)（决策 4 v2 / 20 / 21）
>
> **重要说明**：本文件为**开工前的验收计划**（DoD 基线）。实施过程中**不修改**，实际验收结果、需求变更、已知问题等将在完工后的 `acceptance-report.md` 中独立记录。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段编号 | 1b.2 |
| 里程碑 | 首页 + 全局状态联动 + 累计收益率简化算法落地 |
| 完成日期 | _____（待完工后填）|
| 关联 checklist | 1b.6, 1b.7, 1b.8, 1b.9, 1b.23 |
| 关联决策 | 决策 4 v2 / 20 / 21 |

---

## 1. 验收结果（基线 — 待填）

> 本节由验收人在完工后填写，每个 checklist 项 + 附加项的"通过/未通过" + 证据。

### 1.1 checklist 必达项（5 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| 1b.6 | 首页三卡片（余额类 + 六大类总值 + 累计收益率）| ☐ 通过 / ☐ 未通过 | （截图：`/test/1b/2026-07-22-联调记录.md` 引用）|
| 1b.7 | 六大类环形图（Recharts）| ☐ 通过 / ☐ 未通过 | （截图：DevTools 截图）|
| 1b.8 | 六大类明细表格（含 profit 列）| ☐ 通过 / ☐ 未通过 | （截图：表格展开后）|
| 1b.9 | 最近操作时间线 | ☐ 通过 / ☐ 未通过 | （截图：≥ 3 时间线条目）|
| 1b.23 | 全局状态联动 | ☐ 通过 / ☐ 未通过 | （DevTools Network 截图：useEffect 触发 4 个 fetch 并行）|

### 1.2 附加验收（必达 6 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| A1 | 后端 `AssetQueryService.getCumulativeReturn()` 真实算法实现 | ☐ 通过 / ☐ 未通过 | （`mvn test` 输出：241 → 242）|
| A2 | DTO 加 `algorithm: "phase1_simple"` 字段 | ☐ 通过 / ☐ 未通过 | （Swagger UI 截图）|
| A3 | 累计收益率端到端联调（用 0716 真实数据）| ☐ 通过 / ☐ 未通过 | （`/test/1b/2026-07-22-联调记录.md` 完整命令序列 + 响应）|
| A4 | `/test/1b/2026-07-22-联调记录.md` 文件存在 | ☐ 通过 / ☐ 未通过 | （文件 + 含"存放位置说明"段）|
| A5 | 根目录 README 1b.2 状态更新 | ☐ 通过 / ☐ 未通过 | （README 行 30 显示 `✅ 已完成`）|
| A6 | checklist 1b.2 项勾选 | ☐ 通过 / ☐ 未通过 | （`checklists/phase-1b.md` 1b.6–1b.9, 1b.23 全部 [x]）|

---

## 2. 验收环境

### 2.1 软件版本要求

| 工具 | 版本要求 | 验证命令 |
|------|----------|----------|
| Node.js | ≥ 18.0 | `node -v` |
| npm | ≥ 9.0 | `npm -v` |
| Java | 17 LTS | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL | 8.0 | （任意，1a.7 真实数据库）|
| 浏览器 | Chrome 120+ | 手动 |

### 2.2 后端 + 前端状态

- [ ] 后端 `mvn spring-boot:run` 在 :8080
- [ ] 前端 `npm run dev` 在 :5173
- [ ] 集成联调（前端 → Vite proxy → :8080）OK

---

## 3. 详细验证步骤

### 3.1 checklist 1b.6 — 首页三卡片

**步骤**：
1. 浏览器访问 `http://localhost:5173/`
2. 首页加载完成（约 1s）
3. 顶部应展示 **3 张卡片**横排：
   - **余额类卡片**：显示 ¥xxx.xx（来自 `GET /asset/balance`）
   - **六大类总值卡片**：显示 ¥xxx.xx（来自 `GET /snapshot/latest?includeDetail=true` 的 `sixCategoriesTotal`）
   - **累计收益率卡片**（决策 4 v2）：显示 `+x.xx%` 或 `-x.xx%`（来自 `GET /asset/cumulative-return`）；底部含 `algorithm-tag: phase1_simple`

**通过标准**：
- 3 张卡片全部渲染，数字非空
- 累计收益率卡片有 `phase1_simple` 标签
- 颜色按值正负切换（红/绿，但这里用 var(--color-success/error)）

### 3.2 checklist 1b.7 — 六大类环形图（Recharts）

**步骤**：
1. 三卡片下方应展示 **Recharts PieChart**（全宽）
2. 左侧饼图 + 右侧"实际比例 vs 目标比例"对比表
3. Hover 每个扇区显示 tooltip：类别名 + 金额 + 占比 %

**通过标准**：
- 饼图渲染 6 个扇区（货币类 / 固收类 / 商品类 / A股权益类 / 海外权益类 / 港股大中华类）
- 比例表数值与后端 API 一致（容许 ±0.01）

### 3.3 checklist 1b.8 — 六大类明细表格

**步骤**：
1. 环形图下方应有可折叠区域（`<details>` 或按钮）
2. 点击展开后展示明细表，列：
   - 基金名称 / 金额 / 占比 / 目标比例 / 偏差 / 持有收益
3. 数字与后端 `GET /snapshot/latest?includeDetail=true` 返回的 `categories[].funds[]` 一致

**通过标准**：
- 默认折叠
- 展开后 ≥ 18 行（按 1a.10 真实数据）
- "持有收益"列含 `holding_profit`（决策 3 落地）

### 3.4 checklist 1b.9 — 最近操作时间线

**步骤**：
1. 明细表下方有时间线区域
2. 至少展示 3 条记录（mock 或真实数据均可）
3. 每条：HH:MM:SS + 简介（如 "解析 18 只基金"）

**通过标准**：
- 时间线条目 ≥ 3
- 时间格式正确（HH:MM:SS）
- 简介不空字符串

### 3.5 checklist 1b.23 — 全局状态联动

**步骤**：
1. DevTools → Network 标签
2. 加载首页触发：
   - `GET /api/asset/balance`
   - `GET /api/snapshot/latest?includeDetail=true`
   - `GET /api/asset/operations/recent?limit=5`
   - `GET /api/asset/cumulative-return`
3. 确认 4 个请求均 200
4. /data 页 confirm 入库后（1b.3 实现，1b.2 用手动测试：在 DevTools console 执行 `useAssetSnapshotStore.getState().fetchLatest()`），首页三卡片自动刷新

**通过标准**：
- 4 个并行请求都成功 200
- 主页加载一次触发 4 个 fetch
- 手动触发 `fetchLatest()` 后三卡片数字更新

### 3.6 附加验收 A1 — 后端算法 + Unit Test

```bash
cd fincontrol-backend
mvn test -Dtest=AssetQueryServiceTest -q
```

**预期输出**（新增 3 个用例）：`getCumulativeReturn_positiveCumProf`、`getCumulativeReturn_negativeCumProf`、`getCumulativeReturn_zeroAmountRatios_zero` 全部 PASS。

**通过标准**：从 241 测试增长到 ≥ 244（新增 3 个），无回归。

### 3.7 附加验收 A2 — DTO 加 `algorithm` 字段

```bash
# 浏览器访问 http://localhost:8080/swagger-ui/index.html#/Asset
# 或直接 curl：
curl http://localhost:8080/v3/api-docs | jq '.components.schemas.CumulativeReturnResponse'
```

**预期输出**：
```json
{
  "available": true,    // 新增：算法可用
  "algorithm": "phase1_simple",
  "totalCumulativeProfit": ...,
  "totalAmount": ...,
  "returnRate": ...,
  "message": null
}
```

**通过标准**：`algorithm` 字段存在 + 值为 `phase1_simple`。

### 3.8 附加验收 A3 — 端到端联调

**完整脚本**（保存在 `/test/1b/2026-07-22-联调记录.md`）：
1. `mvn spring-boot:run` 启动后端（in background）
2. `npm run dev` 启动前端（in background）
3. **上传 4 张截图**（0720 标号，对应 0716 数据）：
   ```bash
   for i in 1 2 3 4; do
     curl -X POST -H "X-User-Id: 1" \
       -F "file=@fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-$i.jpg" \
       http://localhost:8080/api/screenshot/upload
   done
   ```
4. **触发 4 次解析**（注入 `dataTime=2026-07-16`）：
   ```bash
   for fileId in $fileIds; do
     curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
       -d "{\"fileId\":\"$fileId\",\"userId\":1,\"dataTime\":\"2026-07-16\"}" \
       http://localhost:8080/api/screenshot/parse
   done
   ```
5. **confirm**（用解析返回的 conversationId 列表）：
   ```bash
   curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
     -d "{...}" http://localhost:8080/api/snapshot/confirm
   ```
6. **验证累计收益率**：
   ```bash
   curl http://localhost:8080/api/asset/cumulative-return | jq
   ```
7. **浏览器截图** `http://localhost:5173/`，保存到 `/test/1b/screenshots-home.png`

**预期算法值**（基于 1a.10 真实数据估算，口径 A = 全口径含余额类）：
- 已知总额 7884.68（含余额类，总资产视角）
- 余额类明细：余额宝 320.85 + 余额（合并显示）
- Σcumulative_profit ≈ 1.89(余额宝) + 其他基金持仓收益 ≈ 估算 100 ~ 500 元
- Σamount = 7884.68（全口径 / 含余额类）
- 累计收益率 ≈ 2.0% ~ 7.0%（口径 A 后 1a.10 真实数据范围；分子分母都含余额类）
- **精确值**：以跑通后实际值为准，记录在联调记录
- **口径变更说明**（2026-07-22 修订）：原计划用口径 B（不含余额类）已被用户拍板为口径 A（全口径）。分母不再减去 320.85 元。决策 4 v2 SQL 同步移除 `AND category != '余额类'`。


**通过标准**：
- 4 张图都解析成功（code=0）
- confirm 写入 19 fund（与 1a.10 一致）
- API 返回 `algorithm="phase1_simple"` + 百分比在合理范围
- 浏览器三卡片显示数字与 API 一致

### 3.9 附加验收 A4 — `/test/1b/2026-07-22-联调记录.md` 文件存在

文件结构（决策 20 规范）：
```markdown
# 1b.2 累计收益率端到端联调（2026-07-22）

## 存放位置说明
- 原始 4 张截图：`fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-{1,2,3,4}.jpg`
- 标号 0720｜真实截图数据日期 0716｜1b.2 联调联试用

## 何时何地去向说明
- 创建日期：2026-07-22
- 用于：1b.2 累计收益率简化算法落地验证（决策 4 v2）
- 归档：归档后保留 30 天可删（按决策 20 /test/ 规范）

## 联调全过程
[4 张图上传命令]
[4 张图解析命令（含 dataTime=2026-07-16）]
[confirm 命令]
[累计收益率 API 响应]
[实际算法百分比 vs 期望范围]

## 截图引用
- 浏览器首页截图：./screenshots-home.png
```

**通过标准**：文件存在 + 4 个段落齐全。

### 3.10 附加验收 A5 — README 1b.2 行更新

**通过标准**：行 30 = `| [x] **1b.2** 首页 + Recharts 环形图 + 累计收益率 v2 | ✅ 已完成 (2026-07-22)`

### 3.11 附加验收 A6 — checklist 1b.2 项勾选

**通过标准**：`docs/phase-1/checklists/phase-1b.md` 5 项 `[x]`：
- 1b.6 ✅
- 1b.7 ✅
- 1b.8 ✅
- 1b.9 ✅
- 1b.23 ✅

---

## 4. 自动化测试

### 4.1 后端测试

```bash
cd fincontrol-backend
mvn test -q
```

**预期**：241 → ≥ 244 测试 PASS（新增 3 个 `getCumulativeReturn_*`）

### 4.2 前端测试

```bash
cd fincontrol-frontend
npm test -- --run
```

**预期**：现有 16/16 PASS **不变**（1b.2 修改 store 是向后兼容增量；如需扩展 store 测试可加）

### 4.3 集成联调（前 1b.1 已有）

前置 1b.1 联调脚本可复用（位于 `fincontrol-backend/uploads/samples/Start-TestSampleSession.ps1`）。

---

## 5. 验证步骤汇总表

| 步骤 | 工具 | 期望输出 |
|------|------|----------|
| Step 1 | mvn test | ≥ 244 PASS |
| Step 2 | curl upload + parse + confirm | 19 fund 写入 |
| Step 3 | curl /api/asset/cumulative-return | `algorithm=phase1_simple` + 百分比 |
| Step 4 | 浏览器访问 `/` | 3 卡片 + 环形图 + 表格 + 时间线 |
| Step 5 | DevTools Network | 4 个并行 fetch 200 |
| Step 6 | 检查 `/test/1b/2026-07-22-联调记录.md` | 4 段齐全 |
| Step 7 | 检查 README / checklist | 5/5 项更新 |

---

## 6. 已知风险与缓解

| # | 风险 | 缓解 |
|---|------|------|
| R1 | minimax API 限流（1a.10 已遇过，30-300s 推理时间）| 失败时改用 0715 4 张图（phase1a2 数据） |
| R2 | 路径 A vs 路径 B：1b.2 测试用路径 A（4×单图）即与 1a.10 一致 | 已知 4/4 通过 |
| R3 | 前端 store 修改破坏 1b.1 测试 | 保留旧字段，新增字段不删除 |
| R4 | Recharts v.s. 1b.1 的 echarts 残留 | 1b.1 已 npm uninstall echarts；1b.2 不用 |
| R5 | cumulative_return 旧版 `{available:false}` 已在前端 useAssetSnapshotStore 假设 available=true → 1b.2 后端改后可能需要前端加 fallback | 在前端 store 加 fallback 逻辑（available=false 时默认 0%） |

---

## 7. 退出条件

- [ ] 5 项 checklist 必达项全部通过
- [ ] 6 项附加验收全部通过
- [ ] `mvn test` ≥ 244 PASS
- [ ] `/test/1b/2026-07-22-联调记录.md` 文件存在
- [ ] **无 Console error / warning**（前端）
- [ ] **后端 log 无 ERROR 级日志**（除已知的 1a.10 caveat）

---

## 8. 验收签字

- 验收人：刘博丞
- 验收日期：____
- 签字：____

---

## 附录 A：手动验证命令清单

```bash
# === 启动 ===
cd fincontrol-backend
mvn spring-boot:run > log/backend.log 2>&1 &
echo "Backend PID: $!"

cd fincontrol-frontend
npm run dev > log/frontend.log 2>&1 &
echo "Frontend PID: $!"

# === 后端健康检查 ===
sleep 30
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}

# === 上传 4 张真实截图 ===
mkdir -p test/1b
for i in 1 2 3 4; do
  resp=$(curl -s -X POST -H "X-User-Id: 1" \
    -F "file=@fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-$i.jpg" \
    http://localhost:8080/api/screenshot/upload)
  fileId=$(echo "$resp" | jq -r '.data.fileId')
  echo "图 $i fileId: $fileId" | tee -a test/1b/2026-07-22-联调记录.md
  eval "fileId$i='$fileId'"
done

# === 触发解析（4 次，含 dataTime=2026-07-16 覆盖 AI 提取的日期）===
for i in 1 2 3 4; do
  eval "fid=\$fileId$i"
  resp=$(curl -s -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
    -d "{\"fileId\":\"$fid\",\"userId\":1,\"dataTime\":\"2026-07-16\"}" \
    http://localhost:8080/api/screenshot/parse)
  convId=$(echo "$resp" | jq -r '.data.conversationId')
  echo "图 $i conversationId: $convId" | tee -a test/1b/2026-07-22-联调记录.md
  eval "convId$i='$convId'"
done

# === confirm（将 4 个 conversation 合并入库）===
# 构造请求体（简化：用 4 个 conversationId 各传入一个 parsedAssets）
# 注意：实际 parsedAssets 字段较复杂；如不确定用纯 integration 测试覆盖

# === 验证累计收益率（核心验收项）===
curl -s http://localhost:8080/api/asset/cumulative-return | jq
echo | tee -a test/1b/2026-07-22-联调记录.md

# === 浏览器手动验证 ===
echo "Browser open http://localhost:5173/ and screenshot" | tee -a test/1b/2026-07-22-联调记录.md
```

---

## 附录 B：验收截图清单

完工后应提供（如截图无法提供，至少文字描述）：

1. ✅ 首页 3 卡片截图（含累计收益率百分比）
2. ✅ 环形图截图（6 个扇区可识别）
3. ✅ 明细表展开截图（含 profit 列）
4. ✅ 时间线截图（≥ 3 条）
5. ✅ DevTools Network 截图（4 个并行 fetch 200）
6. ✅ Swagger UI 中 `CumulativeReturnResponse` schema 截图
7. ✅ `/test/1b/2026-07-22-联调记录.md` 文件内容截屏或 cat 输出
8. ✅ `mvn test` 输出（≥ 244 PASS）
9. ✅ `npm test` 输出（16/16 PASS）

---

*文档生成时间：2026-07-22*
*配套工作计划：[work-plan.md](../../../phase-1/work-plans/1b/2026-07-22_phase1b2-work-plan.md)*
