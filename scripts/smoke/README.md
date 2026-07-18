# scripts/smoke/ — 端到端 smoke 包装

按 **测试阶段 + 测试类型** 二级目录组织：

```
smoke/
├── 1a7/        # Phase 1a.7 的 smoke
│   ├── run-1a7.bat           # Windows cmd 主入口
│   └── run-1a7.sh            # Git Bash 主入口
└── docker/     # 跨阶段可复用的 Docker 验证脚本
    ├── run-test-docker-mysql.bat
    ├── run-test-backend-docker-mysql.bat
    ├── run-test-smoke2-docker.bat
    └── run-test-docker-compose-up.bat
```

## 命名约定

- **`run-*.bat/.sh`**：可直接执行的主入口，**对应一个完整的子阶段冒烟**（如 `run-1a7.bat` 跑完 1a.7 全部 6 步）
- **`run-test-*.bat`**：实验性 / 单一目标验证脚本（如 `run-test-docker-mysql.bat` 只验证 Docker MySQL 路径）

## 新增阶段的步骤

1. 在 `smoke/` 下建目录：`smoke/<phase>/`
2. 创建 `smoke/<phase>/run-<phase>.bat`（+ 可选 `.sh` Git Bash 版）
3. 内部按 Step 1~N 调用 `fincontrol-backend/scripts/<phase>/<step>.sh`
4. 写 `smoke/<phase>/README.md` 说明这一阶段的 smoke 用法