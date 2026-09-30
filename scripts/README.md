# scripts/ — 工作区级运维脚本

> 本目录收纳**工作区根级别**的运维脚本（不是某个 polyrepo 成员内部的脚本）。
> 各子目录组织规则：

```
scripts/
├── README.md           # 本文件（你正在看）
├── desktop/            # 即开即用层（决策 35 / 36）：桌面快捷方式 + 全栈启停
│   ├── create-desktop-shortcut.ps1   # 创建 启动 / 关闭 两个快捷方式
│   ├── launch-fincontrol.ps1         # MySQL 检查 → (dist 缺失才 build) → 后端 → 开浏览器
│   └── stop-fincontrol.ps1           # 优雅关闭后端 + 清理残留 Vite
├── 1b/                 # 1b 阶段运维（决策 24）
│   ├── restart-backend.ps1           # 后端重启 SOP（-SkipRebuild 复用 jar 快速启动）
│   └── stop-backend.ps1
├── smoke/              # 端到端 smoke 包装（按测试阶段 + 测试类型组织）
│   ├── README.md
│   ├── 1a7/            # 阶段 1a.7 的 smoke（稳定，跟 commit 走）
│   │   ├── README.md
│   │   ├── run-1a7.bat      # Windows cmd wrapper
│   │   └── run-1a7.sh       # Git Bash wrapper
│   └── docker/         # Docker 模式 smoke（实验性，跨阶段可复用）
│       ├── README.md
│       └── *.bat
├── import-data/        # 外部数据导入（决策 36 配套；Phase 3 可视化数据源）
├── check-decision-refs.cjs  # 文档防腐：校验全仓「决策 N」引用是否均已登记主表
└── git/                # Git 工具（绕过 cmd.exe 路径含空格的限制）
    └── commit-and-push.sh
```

## 运行形态（决策 36，2026-09-30）

日常使用为**单进程模式**：后端 8080 同时托管前端构建产物（`fincontrol-frontend/dist`），
「开」= `FinControl.lnk`，「关」= 页面右上角按钮或 `FinControl Stop.lnk`。
前端开发模式（`npm run dev`，5173 + Vite proxy）与之并存，互不影响。
详见 `docs/phase-1/decisions/decision-36-single-process-runtime.md`。

## 使用原则

1. **smoke 包装** 永远调 `fincontrol-backend/scripts/<phase>/<step>.sh`，不在自己内部实现业务逻辑
2. **新测试阶段** 加在 `smoke/<new-phase>/`，例如未来有 `smoke/1b1/`、`smoke/2a1/`
3. **新测试类型** 加在对应阶段的子目录，例如 `smoke/1a7/load/`、`smoke/1a7/security/`
4. **debug / 一次性脚本** → 放 `.tmp/`（gitignored），**不提交**
5. **绝对路径禁止** 写在脚本里：用 `%~dp0`（bat）或 `BASH_SOURCE`（sh）推导
6. **`.ps1` 必须为纯 ASCII**（PowerShell 5.1 UTF-8 兼容）。注释与输出一律用英文；
   中文说明写在文档里，不要写进脚本源码

## 工具路径解析（决策 36 的例外说明）

原则 5 针对**路径推导**，但**外部工具链的定位**是另一回事：桌面快捷方式启动的是
干净的 `powershell.exe`，其 PATH 来自注册表（机器级 PATH 中 `%MAVEN_HOME%\bin` 是字面量条目），
`mvn` / `npm` 未必能解析。因此 `desktop/` 与 `1b/` 下的脚本按

```
环境变量（MAVEN_HOME） → 已知安装路径 → PATH(Get-Command)
```

的顺序解析 `mvn.cmd` / `npm.cmd`（`java.exe` 沿用既有做法直接指向 `jdk-17`），
即 **PATH 是最后一档兜底**，前面两档保证快捷方式在 PATH 解析异常时也能启动。
这是**有意为之的兜底**；若换机器，只需改候选列表中的路径，不必重写脚本。

## 不要做的事

- ❌ 把临时调试脚本放到根目录
- ❌ 在 polyrepo 成员目录里创建工作区级的工具脚本
- ❌ 写死 Windows 绝对路径（`C:\Users\xxx\...`）到脚本 —— 例外见上节
- ❌ 在 `.ps1` 里写中文（PowerShell 5.1 会因 UTF-8 解析问题报错，历史上专门修过一轮）