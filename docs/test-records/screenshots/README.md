# docs / test-records / screenshots（团队规范）

> **入仓版团队规范**——约束 `fincontrol-backend/uploads/samples/` 的使用方式、命名、隐私脱敏 SOP。
>
> 实际样本图**不入仓**（隐私原因），仅本规则文档跟随仓库走，方便所有 contributor 共用一份规约。
>
> **本地副本**：`fincontrol-backend/uploads/samples/README.md`（与本文内容相同，跟随 uploads/ gitignore 被排除）。

---

## 1. 用途

本规范约束的是 `fincontrol-backend/uploads/samples/` 这一**本地开发测试样本文件夹**。所有真实支付宝 / 真实基金 App 的截图，按本文命名 + 脱敏 SOP 放置于此，用于：

- `1a.5 POST /api/screenshot/parse` **路径 A**（真实图 → ParsedAsset）回归测试
- `1a.6 POST /api/screenshot/reparse` — 同一图重跑视觉模型
- Phase 1a.7 端到端冒烟前必做的「真实场景 ground truth」

**隔离边界**：

```
fincontrol-backend/uploads/
├── samples/      ← 本地开发自管的真实图（gitignore）
│   └── README.md ← 本规则的本地副本
└── screenshots/  ← 运行时上传目录（FileStorageService.store() 自动写）
```

**`screenshots/` 不放真实测试图**——它只承接业务上传，需要保持「业务原生数据」语义。

---

## 2. 不入仓

`fincontrol-backend/.gitignore` 已包含：

```gitignore
# 上传文件（运行时生成）
uploads/
*.png
*.jpg
*.jpeg
*.webp
```

——本目录**永远**不会被 git 跟踪。**真实支付宝截图含个人敏感信息**（资金、姓名、卡尾号），绝对不能进仓库；各人 App 截图视觉差异大，不应进测试 fixtures。

> 如需把某张样本图**固化进测试 fixtures**（如长期回归测试 case），请改放到：
>
> ```
> fincontrol-backend/src/test/resources/fixtures/screenshots/{phase}-{scenario}.png
> ```
>
> 并改 `ScreenshotServiceTest` 用 `@Value("classpath:fixtures/screenshots/...")` 注入。

---

## 3. 命名规范（脚本自动取上传时间）

**格式**：

```
{phase}-{vendor}-{scenario}-{yyyyMMdd-HHmm}-{seq}.{ext}
```

| 字段 | 含义 | 示例 |
|------|------|------|
| `{phase}` | 验证的 Phase（小写） | `phase1a2` / `phase1a3` |
| `{vendor}` | 截图来自哪个 App | `alipay` / `qwb`（蚂蚁）/ `cgb`（蛋卷）/ `manual` |
| `{scenario}` | 场景描述 | `dashboard` / `fund-list` / `fund-detail` / `error-empty` / `unsorted` |
| `{yyyyMMdd-HHmm}` | **脚本自动取的上传时间**（24h 制） | `20260715-2230` |
| `{seq}` | 同分钟内多张图自动从 1 起递增 | `1` / `2` / `3` |
| `{ext}` | png / jpg / webp | |

**完整示例**：

```
phase1a2-alipay-fund-list-20260715-2230-1.png       ← 第 1 张同分钟内的
phase1a2-alipay-fund-list-20260715-2230-2.png       ← 第 2 张
phase1a2-alipay-error-empty-20260715-2235-1.png
phase1a4-qwb-fund-list-20260801-1430-1.jpg
```

**为什么 `HHmm` 不是 12h 制**：脚本内 `Get-Date -Format "yyyyMMdd-HHmm"`（PowerShell 默认 24h 制）。晚上 10:00 = `2200`、凌晨 2:00 = `0200`，**无歧义**。

---

## 4. 隐私脱敏 SOP（**放入前必做**）

| 敏感字段 | 脱敏方式 |
|---------|---------|
| **真实姓名** | 用贴纸 / 模糊笔刷覆盖 |
| **资金账户尾号**（卡号、基金账户 ID）| 用黑条遮盖 |
| **精确余额数字**（涉及个人资产的）| 可保留——非金融实操，但**如有顾虑请用黑条模糊 4 位以上** |
| **手机号 / 邮箱 / 微信号** | 用贴纸覆盖 |
| **交易订单号 / 单号** | 用黑条覆盖 |
| **地理位置 / 街道门牌** | 用贴纸覆盖 |

> ⚠️ 即便如此，仍**不建议真的实拍**入仓——您和平台分担风险，最稳的是**合成 / 演示数据**。

---

## 5. 使用流程（PowerShell watcher + curl 三件套）

### 5.1 启动 watcher session

新开一个 cmd 窗口，**长期运行**直到您跑完测试：

```cmd
cd C:\path\to\Fincontrol\fincontrol-backend\uploads\samples
powershell -ExecutionPolicy Bypass -File .\Start-TestSampleSession.ps1 -Phase phase1a2 -Vendor alipay -Scenario fund-list
```

**您将看到**：

```
[w1] TestSampleSession STARTED
  Phase    : phase1a2
  Vendor   : alipay
  Scenario : fund-list
  WatchDir : C:\...\uploads\samples
  Drop your screenshot here. Auto-rename on write-stable.
  Ctrl+C or .\Stop-TestSampleSession.ps1 to stop.
```

- `Scenario` 不传时**默认 `unsorted`**——可后续用 `Classify-Sample.ps1` 重命名
- PowerShell 5.1+ 即可跑

### 5.2 粘图（任何方式：Ctrl+V / 拖拽 / 保存）

watcher 检测到 `Created` 事件 → 等文件 size 稳定 2 次 → 复制 + 删除原文件 → 输出：

```
[22:30:45] [w1] fund-snap.png -> phase1a2-alipay-fund-list-20260715-2230-1.png  (245.3 KB)
```

### 5.3 跑 curl 三件套验证

**注意：上传时用 watcher 输出的最终路径**：

```cmd
rem Step 1 · 上传
curl -sS -X POST http://localhost:8080/api/screenshot/upload ^
  -H "X-User-Id: 1" ^
  -F "file=@C:\path\to\Fincontrol\fincontrol-backend\uploads\samples\phase1a2-alipay-fund-list-20260715-2230-1.png"

rem Step 2 · parse（记下 fileId）
curl -sS -X POST http://localhost:8080/api/screenshot/parse ^
  -H "Content-Type: application/json" ^
  -H "X-User-Id: 1" ^
  -d "{\"fileId\":\"<上一步的fileId>\",\"userId\":1}"

rem Step 3 · 看解析日志
curl -sS "http://localhost:8080/api/parse-logs?limit=10" ^
  -H "X-User-Id: 1"
```

**预期 1a.2 成功路径 A 信号**：

- `parse` 返回 `code:0` + 完整 ParsedAsset（含 6 类 + 基金明细 + matchedFunds 数组）
- `reparse` 返回 `code:0` + 同样结果
- `parse-logs` 该条记录 `status: imported`、`fundCount > 0`

### 5.4 结束 watcher

新开另一个 cmd：

```cmd
cd C:\path\to\Fincontrol\fincontrol-backend\uploads\samples
powershell -ExecutionPolicy Bypass -File .\Stop-TestSampleSession.ps1
```

**或**直接回到跑 watcher 的 cmd 窗口，按 `Ctrl+C`。两者效果一样：watcher 退出、`.session.json` 删除。

### 5.5 手动（不用 watcher）—— 命令行一次性

如果 watcher 启动太重 / 不想保持进程，**也可以不跑 watcher**——直接自己用资源管理器把图改名粘进去：

```cmd
copy /Y "%USERPROFILE%\Desktop\fund-snap.png" "C:\...\uploads\samples\phase1a2-alipay-fund-list-20260715-2230-1.png"
```

**注意 12h/24h**：自己手动写文件名时务必用 24h（`2230` 不是 `10:30PM`）。

---

## 6. 维护约定

- **添加样本图** → 必须经过 §4 隐私 SOP 脱敏
- **视觉模型升级时**（minimax 出新版 / 切换到 gpt-4o 等）→ 重跑 §5.3 三件套，确认旧图仍能识别
- **3 个月前的样本** → 归档到 `samples-archive/YYYY-MM/` 或直接删除
- **`unsorted` 文件** → 长期累积到 ≥3 张时，建议跑 `Classify-Sample.ps1` 二次整理

---

## 7. 与 1a.2 验收的关系

| # | 1a.2 checklist | 本规范是否相关 |
|---|----|------|
| 1a.4 | POST /api/screenshot/upload | ✓ §5.3 Step 1 |
| 1a.5 | POST /api/screenshot/parse | ✓ §5.3 Step 2（**核心路径 A 验证**）|
| 1a.6 | POST /api/screenshot/reparse | ✓ §5.3 Step 2 复用 |
| 1a.23 | GET /api/parse-logs | ✓ §5.3 Step 3 |
| P0-1.4 | 失败时也写 chat_history | ✓ 对照上面 verify chat_history 写入 |
| P0-4.4 | reparse 基于已有 conversation | ✓ §5.3 reparse 路径验证 |

---

## 8. 相关文档

- `fincontrol-backend/uploads/samples/README.md` — 本规则的**本地副本**（gitignore 内）
- `docs/phase-1/testing-guide-1a2.md` — 1a.2 端到端测试教程
- `docs/phase-1/acceptance-criteria.md` — Phase 1 47 项 + 18 项 P0 验收全集
- `docs/phase-0/api-contract.md §2.1-2.2` — parse / reparse API 契约
- `docs/test-records/README.md` — Phase 0 测试记录总目录
