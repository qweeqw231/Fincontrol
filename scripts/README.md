# scripts/ — 工作区级运维脚本

> 本目录收纳**工作区根级别**的运维脚本（不是某个 polyrepo 成员内部的脚本）。
> 各子目录组织规则：

```
scripts/
├── README.md           # 本文件（你正在看）
├── smoke/              # 端到端 smoke 包装（按测试阶段 + 测试类型组织）
│   ├── README.md
│   ├── 1a7/            # 阶段 1a.7 的 smoke（稳定，跟 commit 走）
│   │   ├── README.md
│   │   ├── run-1a7.bat      # Windows cmd wrapper
│   │   └── run-1a7.sh       # Git Bash wrapper
│   └── docker/         # Docker 模式 smoke（实验性，跨阶段可复用）
│       ├── README.md
│       └── *.bat
└── git/                # Git 工具（绕过 cmd.exe 路径含空格的限制）
    └── commit-and-push.sh
```

## 使用原则

1. **smoke 包装** 永远调 `fincontrol-backend/scripts/<phase>/<step>.sh`，不在自己内部实现业务逻辑
2. **新测试阶段** 加在 `smoke/<new-phase>/`，例如未来有 `smoke/1b1/`、`smoke/2a1/`
3. **新测试类型** 加在对应阶段的子目录，例如 `smoke/1a7/load/`、`smoke/1a7/security/`
4. **debug / 一次性脚本** → 放 `.tmp/`（gitignored），**不提交**
5. **绝对路径禁止** 写在脚本里：用 `%~dp0`（bat）或 `BASH_SOURCE`（sh）推导

## 不要做的事

- ❌ 把临时调试脚本放到根目录
- ❌ 在 polyrepo 成员目录里创建工作区级的工具脚本
- ❌ 写死 Windows 绝对路径（`C:\Users\xxx\...`）到脚本