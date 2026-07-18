# scripts/smoke/1a7/ — Phase 1a.7 端到端冒烟

## 用法

### Windows（双击）
```
双击 run-1a7.bat
```
或 cmd：
```cmd
cd C:\path\to\Fincontrol\scripts\smoke\1a7
run-1a7.bat
```

### Git Bash / WSL
```bash
cd /c/path/to/Fincontrol/scripts/smoke/1a7
./run-1a7.sh
```

## 步骤（6 步）

| Step | 脚本 | 验证内容 |
|---|---|---|
| 1 | `01-up-mysql.sh` | 启 MySQL（Docker 或 本地 fallback）|
| 2 | `02-up-backend.sh` | 启 Spring Boot 后端 |
| 3 | `03-smoke-1-screenshot.sh` | 截图上传 + 解析 + 确认入库 |
| 4 | `04-smoke-2-chat.sh` | chat 多轮对话 |
| 5 | `05-coverage.sh` | mvn test + JaCoCo + Swagger |
| 6 | `99-cleanup.sh` | 停后端（保留 MySQL）|

## 输出位置

- 日志：`docs/test-records/manual-tests/2026-07-17_phase1a7-smoke.log`
- API 输出：`docs/test-records/manual-tests/api-test-output/`
- Backend log：`fincontrol-backend/backend.log`

## 相关文档

- 1a.7 验收报告：`docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md`
- 1a.7 工作计划：`docs/phase-1/work-plans/2026-07-17_phase1a7-work-plan.md`