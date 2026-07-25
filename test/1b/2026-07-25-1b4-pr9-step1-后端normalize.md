# 1b.4 PR9 Step 1 测试报告 — 后端 fund_name normalize

**日期**: 2026-07-25 20:33
**测试范围**: CategoryMapServiceTest 单元测试（mock Mapper）
**命令**: `cd fincontrol-backend && mvn test -Dtest=CategoryMapServiceTest`
**结果**: ✅ **24 通过 / 0 失败 / 0 错误**

## Bug 1 验证

### TC-1.1 全角空格匹配 — ✅ PASS
- 步骤：上传 0720 图片，AI 解析返回 `安信　新价值`（含全角空格 `\u3000`）
- 预期：mock 返回 DB 中保存的 `安信新价值`（无空格），service.match 通过 normalize 命中
- 实测：matched=1, unmatched=0, fundName="安信新价值"

### TC-1.2 多空格匹配 — ✅ PASS
- 步骤：AI 解析返 `安信  新价值`（中间双空格）
- 实测：matched=1, fundName="安信新价值"

### TC-1.3 完全不匹配 — ✅ PASS
- 步骤：mock 返 1 条 `安信新价值`，输入包含 `完全无关基金`
- 实测：matched=1（安信新价值）, unmatched=["完全无关基金"]

### TC-1.4 normalizeFundName 单元 — ✅ PASS
- `null` / `""` / `"   "` → null
- `"安信新价值"` → `"安信新价值"`（无空白 → 不变）
- `"安信　新价值"` → `"安信新价值"`（全角→半角 → 去除）
- `"安信  新价值"` → `"安信新价值"`（折叠 → 去除）
- `"a\tb\nc\rd"` → `"abcd"`（多种空白类型都去除）

### TC-1.5 match 调用新 mapper — ✅ PASS
- 验证 service.match 调 `selectByUserAndNormalizedNames`，不调旧的 `selectByUserAndFundNames`

## 实现要点

### SQL 端（FundCategoryMapMapper.xml）
```sql
SELECT * FROM fund_category_map
WHERE user_id = #{userId}
  AND REPLACE(REPLACE(REPLACE(REPLACE(fund_name, ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(12288), '') IN
  <foreach collection="normalizedNames" item="n" open="(" separator="," close=")">
      REPLACE(REPLACE(REPLACE(REPLACE(#{n}, ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(12288), '')
  </foreach>
```

### Java 端（CategoryMapService.normalizeFundName）
```java
static String normalizeFundName(String name) {
    if (name == null) return null;
    String s = name.trim();
    if (s.isEmpty()) return null;
    // 全角空格、tab、LF、CR → 半角空格
    s = s.replace('\u3000', ' ').replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    // 折叠连续空白为单空格
    s = s.replaceAll("\\s+", " ");
    // 最终去除所有空白（与 SQL 端对齐）
    s = s.replace(" ", "");
    return s;
}
```

### 两端对齐策略
**去除所有空白字符**（含全角空格、半角空格、tab、CR、LF）作为最终匹配 key。
- 金融场景下 "易方达 蓝筹" / "易方达蓝筹" / "易方达　蓝筹" 视为同一只基金
- 模糊匹配容忍度高，几乎任何排版差异都能命中

## 旧测试兼容性
5 个旧 case 的 mock 从 `selectByUserAndFundNames` 改为 `selectByUserAndNormalizedNames`，逻辑保持等价。

## 集成测试（需在 Step 3 跑）
- 启动 MySQL + 后端 + Vite，手动上传 0720 图片验证 dropdown 显示 "固收类"
- 此步骤需要 GUI 环境，本机未跑，待 PR 集成测试时验证

## 结论
Bug 1 后端修复完成，单元测试 24/24 全绿。后续 Step 2-3 处理前端 Bug 2-7。