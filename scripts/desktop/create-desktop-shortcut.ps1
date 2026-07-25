# 1b.4 PR8 / 决策 35：创建桌面快捷方式 FinControl.lnk
#
# 幂等：检测到 .lnk 已存在则跳过
# 图标：使用 fincontrol-frontend/public/brand/logo.png
# 目标：调 scripts/desktop/launch-fincontrol.ps1

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

$DesktopPath = [Environment]::GetFolderPath('Desktop')
$lnkPath = Join-Path $DesktopPath 'FinControl.lnk'
$logoPath = Join-Path $ProjectRoot 'fincontrol-frontend\public\brand\logo.png'
$targetScript = Join-Path $ProjectRoot 'scripts\desktop\launch-fincontrol.ps1'

if (-not (Test-Path -LiteralPath $logoPath)) {
    Write-Host "[create-shortcut] ✗ logo.png 不存在：$logoPath" -ForegroundColor Red
    Write-Host "[create-shortcut] 请先跑 Step 0 复制品牌资源" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path -LiteralPath $targetScript)) {
    Write-Host "[create-shortcut] ✗ launch-fincontrol.ps1 不存在：$targetScript" -ForegroundColor Red
    exit 1
}

if (Test-Path -LiteralPath $lnkPath) {
    Write-Host "[create-shortcut] 桌面 FinControl.lnk 已存在；跳过" -ForegroundColor Cyan
    Write-Host "[create-shortcut]   路径：$lnkPath" -ForegroundColor Gray
    Write-Host "[create-shortcut]   如需重新创建，请先手动删除" -ForegroundColor Gray
    exit 0
}

Write-Host "[create-shortcut] 创建桌面快捷方式..." -ForegroundColor Cyan
Write-Host "[create-shortcut]   桌面路径：$lnkPath" -ForegroundColor Gray
Write-Host "[create-shortcut]   目标脚本：$targetScript" -ForegroundColor Gray
Write-Host "[create-shortcut]   图标：$logoPath" -ForegroundColor Gray

try {
    $WshShell = New-Object -ComObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($lnkPath)
    $Shortcut.TargetPath = 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'
    $Shortcut.Arguments = "-ExecutionPolicy Bypass -File `"$targetScript`""
    $Shortcut.WorkingDirectory = $ProjectRoot
    $Shortcut.IconLocation = $logoPath
    $Shortcut.Description = 'FinControl - 个人资产配置控制（点击启动）'
    $Shortcut.WindowStyle = 7  # 最小化窗口
    $Shortcut.Save()

    Write-Host "[create-shortcut] ✓ 桌面快捷方式创建成功" -ForegroundColor Green
    Write-Host ""
    Write-Host "现在你可以：" -ForegroundColor Cyan
    Write-Host "  1. 双击桌面的 FinControl.lnk 启动系统" -ForegroundColor White
    Write-Host "  2. 浏览器自动打开 http://localhost:5173/" -ForegroundColor White
    Write-Host "  3. 完成截图入库后，点首页右上角 ⏻ 按钮关闭服务" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "[create-shortcut] ✗ 创建失败：$_" -ForegroundColor Red
    exit 1
}
