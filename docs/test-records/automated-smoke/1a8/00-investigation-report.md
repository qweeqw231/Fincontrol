# Phase 1a.8 Step 0 调查报告 — "1 fund per image" 之谜

**调查日期**：2026-07-18
**调查人**：刘博丞
**结论**：**不是 minimax 模型能力问题，是 smoke 脚本的显示 bug。**

---

## 调查方法

1. 翻 1a.7 smoke 测试的原始 API 响应文件：
   - `docs/test-records/automated-smoke/1a7/api-test-output/03_s02_parse_*.json`
2. 直接读 JSON 内容，看 `categories[].funds[]` 实际有多少只基金

---

## 关键发现：smoke log "1 funds" 是 `grep -c` 行数（不是匹配数）

### 现象
- smoke log 报：`parse 2355-1.jpg → convId=...（1 funds）`
- 实际 API 响应：6 只基金（余额宝 + 长城短债债券A + 鹏华纯债债券D + 国泰黄金ETF联接C + 天弘纳斯达克100 + 摩根纳斯达克100）

### 根因
smoke 脚本 `03-smoke-1-screenshot.sh` 中：
```bash
fc=$(grep -c '"fundName"' "$outfile" 2>/dev/null || echo 0)
mark_ok "  parse $f → convId=$cid（$fc funds）"
```

`grep -c` 数的是**匹配的行数**，不是**匹配的总次数**。
API 响应是**单行 JSON**（整段 JSON 写在 1 行），所以 `"fundName"` 出现了 6 次但全在同一行 → `grep -c` 返回 **1**。

### 正确做法
```bash
# 改用 grep -o + wc -l 数总次数
fc=$(grep -o '"fundName"' "$outfile" | wc -l)
```

### 4 张图实际数据

| 截图 | 实际基金数 | smoke log 显示 | 差异 |
|---|---|---|---|
| 2355-1.jpg | **6** | 1 | 真实！6 只 |
| 2355-2.jpg | (没存 response 文件，docker compose 之前 wipe) | 1 | 未知 |
| 2355-3.jpg | **5** | 1 | 真实！5 只 |
| 2356-1.jpg | (502 minimax 限流) | error | 真实！限流了 |

---

## minimax 限流问题仍然存在（需 1a.8 解决）

虽然数据正确，但 **minimax 限流是真的**：
- 4 张图中 1 张（2356-1.jpg）被限流返 502
- reparse 返 504 超时
- 75% 解析成功率（3/4）需要 fallback 提升到 100%

---

## 1a.8 实际工程重点（调整后）

### 不需要做的
- ❌ 不需要调 prompt（minimax 已经返回正确数据）
- ❌ 不需要修 `extractFirstJsonObject`（它工作正常）
- ❌ 不需要排查 JSON 抽取 bug（已经验证 6/5 只基金都返回）

### 必须做的
- ✅ **smoke 脚本显示 bug**：`grep -c` → `grep -o | wc -l`（1 行修改）
- ✅ **minimax 限流 → 豆包 fallback**（Step 1-3 主体工程）
- ✅ **AiRouter 完整版**（retry + circuit breaker + cache + 监控埋点）
- ✅ **chat fallback**：minimax → DeepSeek

### Step 0 后续行动
1. ✅ Step 0 完成（已写报告）
2. ⏭️ Step 1：修 smoke 显示 bug + `VisionModelClient` 加 `apiStyle` 枚举
3. ⏭️ Step 2：`TextAiClient` 加 DeepSeek fallback
4. ⏭️ Step 3：`AiRouter` 实现
5. ⏭️ Step 4：端到端 smoke 验证 4/4 vision + 5/5 chat

---

## 经验教训

> 1. **`grep -c` 数行不数匹配**——这是常见坑，特别是 JSON 单行输出
> 2. **看 raw data 而不是看 log 显示**——log 可能是错的，但 data 是真的
> 3. **怀疑一切**——用户的自问"为什么只识别 1 只"是合理的，质量保证就应该这样

> Phase 1a 实际上**已经过 4 段验收**（业务层 / 契约 / 真实 SQL / 覆盖率），只是**显示 bug**让人误以为有问题。
> 1a.8 主要解决**minimax 限流**问题（这是真的），并**修显示 bug**作为副产物。